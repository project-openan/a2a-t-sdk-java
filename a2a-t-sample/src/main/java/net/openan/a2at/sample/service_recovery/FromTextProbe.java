package net.openan.a2at.sample.service_recovery;

import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;

/**
 * One-shot diagnostic entry: runs {@code generateNotificationPromptFromText} alone with the
 * realistic natural-language input and prints every intermediate artifact.
 *
 * <p>Run from the repository root:
 * <pre>
 * java @a2a-t-sample/target/sample.args \
 *   net.openan.a2at.sample.service_recovery.FromTextProbe \
 *   a2a-t-sample/src/main/resources/sample/service-recovery/client/client.env
 * </pre>
 *
 * @since 2026-08
 */
public final class FromTextProbe {

    private FromTextProbe() {
    }

    /**
     * Probe entry point.
     *
     * @param args args[0] is the sample client env path
     */
    public static void main(String[] args) throws Exception {
        // Route all probe output to a UTF-8 file so the console codepage cannot garble it.
        java.io.PrintStream out = new java.io.PrintStream(
                java.nio.file.Files.newOutputStream(java.nio.file.Path.of(
                        "a2a-t-sample", "target", "sample-logs", "from-text-probe-output.txt")),
                true,
                java.nio.charset.StandardCharsets.UTF_8);
        System.setOut(out);
        System.setErr(out);
        run(args);
        out.close();
    }

    private static void run(String[] args) {
        // Install the logging LLM wrapper so the raw LLM request/response is visible in the probe output.
        net.openan.a2at.sample.service_recovery.shared.mock.SampleMockLlmInstaller.installLlmLogger(false, "probe");

        Path envPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("a2a-t-sample", "src", "main", "resources", "sample", "service-recovery", "client",
                        "client.env");

        String input = ServiceRecoverySampleInputs.naturalLanguageInput();

        System.out.println("=== [probe] input (" + input.length() + " chars) ===");
        System.out.println(input);
        System.out.println();

        A2ATClient client = new A2ATClient(envPath);
        String templateUri = ServiceRecoverySampleInputs.TEMPLATE_URI;

        System.out.println("=== [probe] calling generateNotificationPromptFromText ===");
        long start = System.currentTimeMillis();
        try {
            MetadataContent content = client.generateNotificationPromptFromText(input, templateUri);
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("=== [probe] SUCCESS in " + elapsed + " ms ===");
            System.out.println("--- templateUri ---");
            System.out.println(content.templateUri());
            System.out.println("--- extensionUri ---");
            System.out.println(content.extensionUri());
            System.out.println("--- promptText ---");
            System.out.println(content.promptText());
            System.out.println("--- metadata map ---");
            for (Map.Entry<String, Object> entry : content.buildMetadataContent().entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + summarize(String.valueOf(entry.getValue())));
            }
        } catch (RuntimeException error) {
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("=== [probe] FAILED in " + elapsed + " ms ===");
            System.out.println(error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private static String summarize(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80) + "... (" + value.length() + " chars)";
    }
}
