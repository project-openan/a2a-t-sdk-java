package net.openan.a2at.sdk.prompt.taskrendering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import org.junit.jupiter.api.Test;

/**
 * Contract tests that verify real template resources from the classpath are rendered correctly:
 * scaffolding text (Requirement:/要求：) must be removed from rendered output.
 */
class RealTemplateRenderingContractTest {

    private final TaskPromptRenderer taskRenderer = new TaskPromptRenderer();
    private final DropBlankSlotSectionRenderer dropRenderer = new DropBlankSlotSectionRenderer();

    @Test
    void energySavingEnUsTemplateCollapsesScaffolding() {
        String template = loadTemplate(
                "prompt_resources/templates/Task-T/network-layer/energy-saving/v1/en-US/template.md");
        String rendered = taskRenderer.render(template, Map.of(
                "operation_type", "Create",
                "task_description", "Energy saving plan.",
                "task_object", "Area: Songshanhu",
                "task_target", "Maximize energy saving.",
                "task_context", "NR; full period.",
                "expected_output", "Report."));

        assertFalse(rendered.contains("Requirement:"));
        assertTrue(rendered.contains("Create"));
        assertTrue(rendered.contains("Energy saving plan."));
        assertTrue(rendered.contains("Maximize energy saving."));
    }

    @Test
    void energySavingZhCnTemplateCollapsesScaffolding() {
        String template = loadTemplate(
                "prompt_resources/templates/Task-T/network-layer/energy-saving/v1/zh-CN/template.md");
        String rendered = taskRenderer.render(template, Map.of(
                "操作类型", "创建",
                "任务描述", "节能方案。",
                "任务对象", "区域：松山湖",
                "任务目标", "最大化节能。",
                "任务上下文", "NR；全时段。",
                "预期输出", "报告。"));

        assertFalse(rendered.contains("要求："));
        assertTrue(rendered.contains("创建"));
        assertTrue(rendered.contains("节能方案。"));
        assertTrue(rendered.contains("最大化节能。"));
    }

    @Test
    void authorizationEnUsTemplateCollapsesScaffolding() {
        String template = loadTemplate(
                "prompt_resources/templates/Authorization-T/authorization-policy-management/v1/en-US/template.md");
        String rendered = taskRenderer.render(template, Map.of(
                "authorization_policy_operation_type", "add authorization policy",
                "network_operation_authorization_policy_list", "Policy list content."));

        assertFalse(rendered.contains("Requirement:"));
        assertTrue(rendered.contains("add authorization policy"));
        assertTrue(rendered.contains("Policy list content."));
    }

    @Test
    void authorizationZhCnTemplateCollapsesScaffolding() {
        String template = loadTemplate(
                "prompt_resources/templates/Authorization-T/authorization-policy-management/v1/zh-CN/template.md");
        String rendered = taskRenderer.render(template, Map.of(
                "授权策略的操作类型", "新增授权策略",
                "动网操作的授权策略列表", "策略列表内容。"));

        assertFalse(rendered.contains("要求："));
        assertTrue(rendered.contains("新增授权策略"));
        assertTrue(rendered.contains("策略列表内容。"));
    }

    @Test
    void subscribeIncidentZhCnTemplateCollapsesScaffolding() {
        String template = loadTemplate(
                "prompt_resources/templates/Notification-T/network-layer/subscribe-incident/v1/zh-CN/template.md");
        String rendered = taskRenderer.render(template, Map.of(
                "通知主题", "Incident",
                "订阅条件", "严重",
                "上报通知数据格式", "DataPart"));

        assertFalse(rendered.contains("要求："));
        assertTrue(rendered.contains("Incident"));
        assertTrue(rendered.contains("DataPart"));
    }

    @Test
    void serviceRecoveryZhCnTemplateCollapsesScaffolding() {
        String template = loadTemplate(
                "prompt_resources/templates/Notification-T/network-layer/service-recovery/v1/zh-CN/template.md");
        String rendered = taskRenderer.render(template, Map.of(
                "订阅条件", "子网：xx",
                "上报通知数据格式", "数据格式内容。"));

        assertFalse(rendered.contains("要求："));
        assertTrue(rendered.contains("数据格式内容。"));
    }

    @Test
    void negotiationZhCnTemplateDropsBlankSlotSections() {
        String template = loadTemplate(
                "prompt_resources/templates/Negotiation-T/information-negotiation/propose/v1/zh-CN/template.md");
        String rendered = dropRenderer.render(template, Map.of(
                "所需信息项", "1. 信息项"));

        assertFalse(rendered.contains("要求："));
        assertTrue(rendered.contains("信息项"));
    }

    private static String loadTemplate(String classpathResource) {
        try (java.io.InputStream stream = RealTemplateRenderingContractTest.class
                .getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (stream == null) {
                throw new AssertionError("Template not found on classpath: " + classpathResource);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read template: " + classpathResource, e);
        }
    }
}