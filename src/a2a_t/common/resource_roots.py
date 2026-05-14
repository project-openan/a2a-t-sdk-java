from __future__ import annotations

import sysconfig
from pathlib import Path


def resolve_prompt_resource_root(*, module_file: str | Path, source_parent_depth: int) -> Path:
    """Resolve packaged prompt resources for source checkouts and installed wheels."""
    module_path = Path(module_file).resolve()
    source_root = module_path.parents[source_parent_depth] / "package_data" / "prompt_resources"
    if source_root.exists():
        return source_root
    return Path(sysconfig.get_path("data")).resolve() / "prompt_resources"
