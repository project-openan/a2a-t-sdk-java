#!/usr/bin/env python3
# Copyright (c) 2026 Huawei Technologies Co., Ltd.
# All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#
#    Licensed under the Apache License, Version 2.0 (the "License"); you may
#    not use this file except in compliance with the License. You may obtain
#    a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#    License for the specific language governing permissions and limitations
#    under the License.

"""将 smoke 报告 JSON 确定性渲染为 markdown 逐例原始输入输出记录（transcript）。

用法:
    python authz_smoke_transcript.py <report.json> [--out OUT] [--scenarios P] [--param-schema P]

输入:
    report.json   — demo 冒烟运行的 JSON 报告（结构: meta / summary / scenarios[]）。
    scenarios.json — 冒烟题集，提供每例原始输入（from_text 的 text /
                     from_data_with_schema 的 data+input.schema）与场景级 validate_schema。
    param-schema.json — suite 级默认提参 schema。

输出:
    markdown 逐例两段式记录（客户端生成 / 服务端校验提参），文末附差分对判读汇总表。
    格式按 D16 定稿：data 场景打印原始 data 与 input.schema 两份 JSON；客户端报错打印原始
    错误 JSON；服务端拒绝打印原始 errors 数组 JSON；默认 schema 亦全文打印。键名泄漏扫描
    （D13）对挂变异 schema 的用例生效。

仅依赖标准库；所有读写均为 utf-8。
"""

from __future__ import annotations

import argparse
import json
import os
import sys

# 变异例 label 后缀（基线 label = 去掉后缀）
VARIANT_SUFFIXES = ("-varname", "-varfields", "-varflat", "-varreq", "-varsch", "-dual")

# 默认 schema 中会被变异重命名、用于键名泄漏扫描的键（operationType 恒定不改键）
DEFAULT_SCAN_KEYS = ("policyList", "policyId", "scene", "actionType", "operationName", "validityPeriod")

# 冒烟集固定差分对（基线, 变异后缀）
DIFF_PAIRS = (
    ("c1-nl-add-01", "-varname"),
    ("c3-nl-mod-06", "-varfields"),
    ("b2-nl-format-01", "-varreq"),
)

UNCOLLECTED = "（未采集）"


def _reconfigure_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:
            pass


def load_json(path: str):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def jprint(obj) -> str:
    return json.dumps(obj, ensure_ascii=False, indent=2)


def split_variant_suffix(label: str):
    for suffix in VARIANT_SUFFIXES:
        if label.endswith(suffix):
            return label[: -len(suffix)], suffix
    return label, None


def collect_dict_keys(node, acc: set) -> None:
    """递归收集任意嵌套 dict/list 结构中被用作 dict 键名（key）的字符串。"""
    if isinstance(node, dict):
        for key, value in node.items():
            if isinstance(key, str):
                acc.add(key)
            collect_dict_keys(value, acc)
    elif isinstance(node, list):
        for item in node:
            collect_dict_keys(item, acc)


def collect_schema_property_names(schema, acc: set) -> None:
    """递归收集 JSON Schema 中声明过的属性名（properties 键）。"""
    if not isinstance(schema, dict):
        return
    properties = schema.get("properties")
    if isinstance(properties, dict):
        for key, sub in properties.items():
            if isinstance(key, str):
                acc.add(key)
            collect_schema_property_names(sub, acc)
    for singleton in ("items", "additionalProperties", "contains", "propertyNames", "not"):
        sub = schema.get(singleton)
        if isinstance(sub, dict):
            collect_schema_property_names(sub, acc)
    for combine in ("allOf", "oneOf", "anyOf"):
        for sub in schema.get(combine, []) or []:
            collect_schema_property_names(sub, acc)


def leakage(actual_params, validate_schema):
    """返回 actual_params 中出现的、未在当前变异 schema 中声明的默认 schema 键。"""
    if actual_params is None or not isinstance(validate_schema, dict):
        return []
    declared: set = set()
    collect_schema_property_names(validate_schema, declared)
    used: set = set()
    collect_dict_keys(actual_params, used)
    return [key for key in DEFAULT_SCAN_KEYS if key in used and key not in declared]


def status_text(match) -> str:
    if match is None:
        return "—"
    return "✅" if match else "❌"


def diff_verdict(base_match, variant_match) -> str:
    if base_match and variant_match:
        return "健康"
    if base_match and not variant_match:
        return "schema 映射问题"
    if not base_match and variant_match:
        return "内容理解问题"
    return "公共依赖问题"


def compact_slot_errors(errors) -> str:
    if not errors:
        return "[]"
    parts = ["%s:%s" % (e.get("slot_name"), e.get("code")) for e in errors]
    return "[" + ", ".join(parts) + "]"


def mismatch_note(entry) -> str:
    assertions = entry.get("assertions") or {}
    actual = entry.get("actual_outcome")
    errs = compact_slot_errors(entry.get("actual_slot_errors") or [])
    expected_client = entry.get("expected_client") or {}
    expected_server = entry.get("expected_server") or {}
    if assertions.get("client_prompt") is None:
        return "客户端阶段结果与期望不符（actual_outcome=%s，slot_errors=%s，期望 outcome=%s）" % (
            actual,
            errs,
            expected_client.get("outcome"),
        )
    if assertions.get("server_outcome") is False:
        return "服务端结果与期望不符（actual_outcome=%s，slot_errors=%s，期望 outcome=%s）" % (
            actual,
            errs,
            expected_server.get("outcome"),
        )
    if assertions.get("server_params") is False:
        return "服务端提参结果与期望 params 不符（见上方提参结果与 expected.params 对照）"
    return "actual_outcome=%s，slot_errors=%s" % (actual, errs)


def render_client_input(scenario_def, entry_kind) -> list:
    lines = []
    inp = scenario_def.get("input") or {}
    if entry_kind == "from_data_with_schema":
        lines.append("**原始输入**（from_data_with_schema）")
        lines.append("")
        lines.append("data:")
        lines.append("```json")
        lines.append(jprint(inp.get("data")))
        lines.append("```")
        lines.append("input.schema:")
        lines.append("```json")
        lines.append(jprint(inp.get("schema")))
        lines.append("```")
    else:
        lines.append("**原始输入**（from_text）")
        lines.append("")
        lines.append("> %s" % (inp.get("text") or ""))
    lines.append("")
    return lines


def render_scenario(entry, scenario_def, param_schema) -> list:
    lines = []
    label = entry.get("label", "?")
    match = bool(entry.get("match"))
    mark = "✅ match" if match else "❌ MISMATCH"
    lines.append("## %s  —  %s" % (label, mark))

    baseline, suffix = split_variant_suffix(label)
    if suffix:
        lines.append("[【差分对】配对基线：%s]" % baseline)
    lines.append("")

    entry_kind = (scenario_def or {}).get("entry") or entry.get("entry") or "from_text"
    prompt_text = entry.get("prompt_text")
    client_failed = prompt_text is None

    lines.append("### 客户端（生成）")
    lines.append("")
    lines.extend(render_client_input(scenario_def or {}, entry_kind))

    if client_failed:
        error = entry.get("error") or {}
        fail_obj = {
            "code": error.get("code") or entry.get("actual_outcome"),
            "message": error.get("message"),
            "slot_errors": entry.get("actual_slot_errors") or [],
        }
        lines.append("**生成结果：失败**")
        lines.append("")
        lines.append("```json")
        lines.append(jprint(fail_obj))
        lines.append("```")
    else:
        lines.append("**生成结果：成功（渲染后 prompt）**")
        lines.append("")
        lines.append("```")
        lines.append(prompt_text)
        lines.append("```")
    lines.append("")
    lines.append("**提参理由（LLM reasoning）**：%s" % (entry.get("client_reasoning") or UNCOLLECTED))
    lines.append("")

    lines.append("### 服务端（校验 + 提参）")
    lines.append("")
    if client_failed:
        lines.append("（客户端生成失败路径，服务端未被调用。）")
        lines.append("")
    else:
        validate_schema = (scenario_def or {}).get("validate_schema")
        if validate_schema:
            schema_label = "本用例变异 schema"
            schema_to_print = validate_schema
        else:
            schema_label = "suite 默认 param-schema.json"
            schema_to_print = param_schema
        lines.append("**validateAndDataFilling 输入 schema（%s）**：" % schema_label)
        lines.append("")
        lines.append("```json")
        lines.append(jprint(schema_to_print))
        lines.append("```")
        lines.append("")
        lines.append("（输入 prompt 即上方客户端渲染结果，此处不重复。）")
        lines.append("")
        outcome = entry.get("actual_outcome")
        lines.append("**校验与提参结果**：`%s`" % outcome)
        lines.append("")
        if outcome == "success":
            lines.append("**提参结果**：")
            lines.append("")
            lines.append("```json")
            lines.append(jprint(entry.get("actual_params")))
            lines.append("```")
        else:
            lines.append("```json")
            lines.append(jprint(entry.get("actual_slot_errors") or []))
            lines.append("```")
        lines.append("")
        lines.append("**校验理由（LLM reasoning）**：%s" % (entry.get("server_reasoning") or UNCOLLECTED))
        lines.append("")
        if validate_schema is None:
            leak_line = "**键名泄漏扫描**：无泄漏"
        else:
            leaks = leakage(entry.get("actual_params"), validate_schema)
            leak_line = "**键名泄漏扫描**：无泄漏" if not leaks else (
                "**键名泄漏扫描**：❌ 泄漏——actual_params 出现 schema 外键：" + ", ".join(leaks)
            )
        lines.append(leak_line)
        lines.append("")

    assertions = entry.get("assertions") or {}
    client_prompt = assertions.get("client_prompt")
    lines.append("**判定**：match=%s（client_prompt=%s）" % (match, repr(client_prompt)))
    if not match:
        lines.append("")
        lines.append("> ❌ mismatch 说明：%s" % mismatch_note(entry))
    return lines


def render_report(report, scenarios_index, param_schema, report_path) -> str:
    lines = []
    meta = report.get("meta") or {}
    summary = report.get("summary") or {}
    scenario_entries = report.get("scenarios") or []
    total = summary.get("total", len(scenario_entries))
    match_count = summary.get("match")
    if match_count is None:
        match_count = sum(1 for c in scenario_entries if c.get("match"))
    scenarios_resource = meta.get("scenariosResource", "scenarios.json")

    lines.append("# Authorization-T Demo 冒烟测试原始输入输出记录")
    lines.append("")
    lines.append(
        "- 位置：`%s`；题集：`%s`；LLM：蓝区统一网关（key 不落档）；参数：`-Dauthz.reasoning=true`；"
        "结果：**%s/%s match**" % (report_path, scenarios_resource, match_count, total)
    )
    lines.append("- 分区：预期成功在前（index 0-7）、预期拒绝在中（index 8-12）、客户端拦截在末尾（index 13-14）")
    lines.append("- 每例两段：客户端（输入→生成结果→理由）与服务端（schema 输入→校验提参结果→理由）；服务端段不重复渲染后 prompt")
    lines.append("")

    for entry in scenario_entries:
        scenario_def = scenarios_index.get(entry.get("label"))
        lines.append("---")
        lines.append("")
        lines.extend(render_scenario(entry, scenario_def, param_schema))
        lines.append("")

    lines.append("---")
    lines.append("")
    lines.append("## 差分对判读汇总")
    lines.append("")
    lines.append("| 差分对 | 基线半 | 变异半 | 判读 |")
    lines.append("|---|---|---|---|")
    match_by_label = {c.get("label"): bool(c.get("match")) for c in scenario_entries}
    for base, suffix in DIFF_PAIRS:
        variant = base + suffix
        bm = match_by_label.get(base)
        vm = match_by_label.get(variant)
        verdict = "—" if bm is None or vm is None else diff_verdict(bm, vm)
        lines.append("| %s/%s | %s | %s | %s |" % (base, suffix, status_text(bm), status_text(vm), verdict))

    return "\n".join(lines) + "\n"


def default_paths(args):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    scenarios = args.scenarios or os.path.normpath(
        os.path.join(script_dir, "..", "src", "main", "resources", "sample", "authz-policy", "scenarios.json")
    )
    param_schema = args.param_schema or os.path.normpath(
        os.path.join(script_dir, "..", "src", "main", "resources", "sample", "authz-policy", "param-schema.json")
    )
    out = args.out or os.path.join(os.path.dirname(scenarios), "smoke-io-transcript.md")
    return scenarios, param_schema, out


def main(argv=None) -> int:
    _reconfigure_stdio()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", help="smoke 运行报告的 JSON 路径")
    parser.add_argument("--out", "-o", help="输出 .md 路径（默认：scenarios.json 同目录 smoke-io-transcript.md）")
    parser.add_argument("--scenarios", help="冒烟题集 scenarios.json 路径（默认相对仓库布局）")
    parser.add_argument("--param-schema", dest="param_schema", help="默认 param-schema.json 路径（默认相对仓库布局）")
    args = parser.parse_args(argv)

    scenarios_path, param_schema_path, out_path = default_paths(args)

    report = load_json(args.report)
    scenarios_raw = load_json(scenarios_path)
    param_schema = load_json(param_schema_path)

    scenarios_index = {}
    for scenario in scenarios_raw.get("scenarios", []):
        label = scenario.get("label")
        if label is not None:
            scenarios_index[label] = scenario

    transcript = render_report(report, scenarios_index, param_schema, args.report)

    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(transcript)
    print("wrote %s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())