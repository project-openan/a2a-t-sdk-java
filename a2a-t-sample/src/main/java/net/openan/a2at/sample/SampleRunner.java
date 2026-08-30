package net.openan.a2at.sample;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Unified launcher for every sample in this module.
 *
 * <p>One command runs any sample end to end — start the server, wait until its HTTP endpoint is up, run the client,
 * then stop the server. The server output is captured to a temp log file; the client output streams to the console.
 *
 * <p>Usage from the repository root:
 *
 * <pre>
 * java -cp @a2a-t-sample/target/sample.args net.openan.a2at.sample.SampleRunner <sample>
 * </pre>
 *
 * <p>Each sample is discovered by convention from {@code src/main/resources/sample/} — a directory containing both
 * {@code client/} and {@code server/} subdirectories is a runnable sample. The corresponding main classes are resolved
 * as {@code net.openan.a2at.sample.<java_package>.client.ClientSampleMain} and {@code ...server.ServerSampleMain},
 * where {@code <java_package>} is the directory name with dashes replaced by underscores. Adding a new sample needs no
 * registration: drop the package and the resource directory, and the runner picks it up.
 *
 * <p>Environment variables: {@code A2AT_SAMPLE_MAX_ARTIFACTS} (passed through to the client) and
 * {@code A2AT_SAMPLE_LIST} (set to any value to only list samples).
 *
 * @since 2026-08
 */
public final class SampleRunner {

    private static final Path SAMPLES_ROOT = Path.of("a2a-t-sample", "src", "main", "resources", "sample");

    private static final Path ARGS_FILE = Path.of("a2a-t-sample", "target", "sample.args");

    private static final long SERVER_STARTUP_TIMEOUT_SECONDS = 120;

    private SampleRunner() {}

    /**
     * Runner entry point.
     *
     * @param args single sample name, or nothing to list the available samples
     */
    public static void main(String[] args) throws Exception {
        List<String> samples = discoverSamples();
        if (samples.isEmpty()) {
            System.err.println("No samples found under " + SAMPLES_ROOT);
            System.exit(2);
        }
        if (args.length == 0 || "list".equalsIgnoreCase(args[0]) || System.getenv("A2AT_SAMPLE_LIST") != null) {
            System.out.println(
                    "Available samples (run with: java @a2a-t-sample/target/sample.args net.openan.a2at.sample.SampleRunner <name>):");
            samples.forEach(name -> System.out.println("  - " + name));
            return;
        }

        String sample = args[0].toLowerCase(Locale.ROOT);
        if (!samples.contains(sample)) {
            System.err.println("Unknown sample '" + sample + "'. Available samples:");
            samples.forEach(name -> System.err.println("  - " + name));
            System.exit(2);
        }

        System.exit(runSample(sample));
    }

    private static int runSample(String sample) throws Exception {
        String javaPackage = "net.openan.a2at.sample." + sample.replace('-', '_');
        String serverMain = javaPackage + ".server.ServerSampleMain";
        String clientMain = javaPackage + ".client.ClientSampleMain";
        String serverEnv = SAMPLES_ROOT
                .resolve(sample)
                .resolve("server")
                .resolve("server.env")
                .toString();
        String clientEnv = SAMPLES_ROOT
                .resolve(sample)
                .resolve("client")
                .resolve("client.env")
                .toString();
        if (!Files.exists(Path.of(serverEnv)) || !Files.exists(Path.of(clientEnv))) {
            System.err.println("Sample '" + sample + "' is missing its env files:");
            System.err.println("  " + serverEnv);
            System.err.println("  " + clientEnv);
            return 2;
        }

        List<String> classpath = readArgsClasspath();

        int serverPort = resolveServerPort(clientEnv);
        if (isPortOpen(serverPort)) {
            System.err.println("[runner] port " + serverPort + " is already in use — a previous server for sample '"
                    + sample + "' may still be running.");
            System.err.println("[runner] stop it first (e.g. stop the lingering java process listening on the port), "
                    + "or rerun after 'mvn clean' fails to delete jars.");
            return 2;
        }

        Path serverLog = Path.of("a2a-t-sample", "target", "sample-logs").resolve(sample + "-server.log");
        Files.createDirectories(serverLog.getParent());
        System.out.println(
                "[runner] starting server for sample '" + sample + "' (log: " + serverLog.toAbsolutePath() + ")");
        Process server = new ProcessBuilder(javaCommand(classpath, serverMain, serverEnv))
                .redirectErrorStream(true)
                .start();
        // Kill the spawned processes when this runner is interrupted (Ctrl+C), so no orphaned
        // server keeps running and locking jars under target/.
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            server.destroyForcibly();
                            System.out.println("[runner] shutdown: server stopped");
                        },
                        "sample-runner-shutdown"));
        Thread serverLogPumper = new Thread(() -> pumpServerLog(server, serverLog), "sample-runner-server-log");
        serverLogPumper.setDaemon(true);
        serverLogPumper.start();
        if (!waitForServer(serverPort, SERVER_STARTUP_TIMEOUT_SECONDS)) {
            System.err.println(
                    "[runner] server did not start within " + SERVER_STARTUP_TIMEOUT_SECONDS + "s; server log tail:");
            tailLog(serverLog, 30);
            server.destroyForcibly();
            return 2;
        }
        System.out.println("[runner] server is up on port " + serverPort + ", starting client");

        Path clientLog = Path.of("a2a-t-sample", "target", "sample-logs").resolve(sample + "-client.log");
        Process client = new ProcessBuilder(javaCommand(classpath, clientMain, clientEnv))
                .redirectErrorStream(true)
                .start();
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            client.destroyForcibly();
                            System.out.println("[runner] shutdown: client stopped");
                        },
                        "sample-runner-shutdown-client"));
        Thread clientLogPumper = new Thread(() -> pumpLog(client, clientLog, "[client] "), "sample-runner-client-log");
        clientLogPumper.setDaemon(true);
        clientLogPumper.start();
        int clientExit = client.waitFor();
        server.destroyForcibly();
        server.waitFor();

        System.out.println("[runner] client exit code: " + clientExit);
        if (clientExit != 0) {
            System.out.println("[runner] server log tail:");
            tailLog(serverLog, 30);
        }
        return clientExit;
    }

    /** Pumps the server process output to the console (prefixed) and to the log file concurrently. */
    private static void pumpServerLog(Process server, Path serverLog) {
        pumpLog(server, serverLog, "[server] ");
    }

    /**
     * Pumps one child process output to the console (prefixed) and to the log file concurrently.
     *
     * <p>Both the reader and the log file use UTF-8; the child JVMs are launched with {@code stdout.encoding=UTF-8} so
     * their console streams emit UTF-8 bytes even on Windows where the default console encoding is the ANSI code page
     * (GBK).
     */
    private static void pumpLog(Process process, Path logFile, String consolePrefix) {
        try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                java.io.BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(consolePrefix + line);
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException exception) {
            System.out.println("[runner] log pump ended: " + exception.getMessage());
        }
    }

    private static List<String> readArgsClasspath() throws IOException {
        List<String> lines = Files.readAllLines(ARGS_FILE, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-cp") && lines.indexOf(line) < lines.size() - 1) {
                // classpath value is the next non-empty line
                int index = lines.indexOf(line) + 1;
                while (index < lines.size() && lines.get(index).trim().isEmpty()) {
                    index++;
                }
                return List.of("-cp", lines.get(index).trim());
            }
        }
        throw new IllegalStateException("No -cp entry found in " + ARGS_FILE);
    }

    private static List<String> javaCommand(List<String> classpath, String mainClass, String envPath) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        // Force UTF-8 console streams on the child JVM: on Windows the default console encoding is
        // the ANSI code page (GBK), which would garble Chinese when the runner decodes UTF-8.
        // Unknown -D properties are ignored, so both the JDK 19+ and legacy spellings are safe.
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");
        command.add("-Dsun.stdout.encoding=UTF-8");
        command.add("-Dsun.stderr.encoding=UTF-8");
        command.addAll(classpath);
        command.add(mainClass);
        command.add(envPath);
        return command;
    }

    private static List<String> discoverSamples() {
        if (!Files.isDirectory(SAMPLES_ROOT)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(SAMPLES_ROOT)) {
            return dirs.filter(Files::isDirectory)
                    .filter(dir -> Files.isDirectory(dir.resolve("client")) && Files.isDirectory(dir.resolve("server")))
                    .map(dir -> dir.getFileName().toString().toLowerCase(Locale.ROOT))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan samples under " + SAMPLES_ROOT, exception);
        }
    }

    private static int resolveServerPort(String clientEnv) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(clientEnv), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return 26336;
        }
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("A2AT_SAMPLE_PORT=")) {
                try {
                    return Integer.parseInt(
                            line.substring("A2AT_SAMPLE_PORT=".length()).trim());
                } catch (NumberFormatException ignored) {
                    // fall through to default
                }
            }
        }
        return 26336;
    }

    private static boolean isPortOpen(int port) {
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create("http://127.0.0.1:" + port + "/").toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setRequestMethod("GET");
            connection.getResponseCode();
            connection.disconnect();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean waitForServer(int port, long timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection connection = (HttpURLConnection)
                        URI.create("http://127.0.0.1:" + port + "/").toURL().openConnection();
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
                connection.setRequestMethod("GET");
                int code = connection.getResponseCode();
                connection.disconnect();
                if (code >= 200 && code < 500) {
                    return true;
                }
            } catch (IOException ignored) {
                // not up yet
            }
            Thread.sleep(1000L);
        }
        return false;
    }

    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        return Path.of(
                        javaHome,
                        "bin",
                        System.getProperty("os.name", "")
                                        .toLowerCase(Locale.ROOT)
                                        .contains("win")
                                ? "java.exe"
                                : "java")
                .toString();
    }

    private static void tailLog(Path log, int lineCount) {
        try (BufferedReader reader = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > lineCount) {
                    lines.remove(0);
                }
            }
            lines.forEach(System.out::println);
        } catch (IOException exception) {
            System.out.println("[runner] failed to read log " + log + ": " + exception.getMessage());
        }
    }
}
