package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NegotiationEvaluationProcessLoggerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesOneCompactJsonObjectPerLine() throws Exception {
        Path log = temporaryDirectory.resolve("process.jsonl");
        try (NegotiationEvaluationProcessLogger logger = new NegotiationEvaluationProcessLogger(OBJECT_MAPPER, log)) {
            logger.write(Map.of("stage", "generate", "request", Map.of("text", "first")));
            logger.write(Map.of("stage", "validate_and_fill", "request", Map.of("text", "second")));
        }

        List<String> lines = Files.readAllLines(log);
        assertEquals(2, lines.size());
        assertFalse(lines.stream().anyMatch(String::isBlank));
        assertEquals(
                "generate", OBJECT_MAPPER.readTree(lines.get(0)).get("stage").asText());
        assertEquals(
                "validate_and_fill",
                OBJECT_MAPPER.readTree(lines.get(1)).get("stage").asText());
    }
}
