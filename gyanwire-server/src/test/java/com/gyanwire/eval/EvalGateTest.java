package com.gyanwire.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EvalGateTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void goldenSetScoresAndDoesNotRegressPastThreePoints() throws Exception {
        Path root = EvalRunner.repoRoot();
        JsonNode report = new EvalRunner().run(root.resolve("eval/golden"));
        assertThat(report.path("queries").asInt()).isEqualTo(70);
        assertThat(report.path("ndcgAt5").asDouble()).isGreaterThan(0.5);
        assertThat(report.path("hybridNdcgAt5").asDouble())
                .isGreaterThanOrEqualTo(report.path("ndcgAt5").asDouble() - 0.001);

        Path reports = root.resolve("eval/reports");
        if (!Files.isDirectory(reports)) {
            return;
        }
        Path baseline;
        try (Stream<Path> files = Files.list(reports)) {
            baseline = files
                    .filter(p -> p.getFileName().toString().startsWith("baseline-") && p.getFileName().toString().endsWith(".json"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElse(null);
        }
        if (baseline == null) {
            return;
        }
        JsonNode committed = mapper.readTree(baseline.toFile());
        double drop = committed.path("ndcgAt5").asDouble() - report.path("ndcgAt5").asDouble();
        assertThat(drop)
                .as("nDCG@5 dropped %.3f versus %s", drop, baseline.getFileName())
                .isLessThanOrEqualTo(0.03);
    }

    @Test
    void ndcgRewardsHigherRanks() {
        var good = EvalMetrics.setOf(java.util.List.of("a", "b"));
        double high = EvalMetrics.ndcgAt(java.util.List.of("a", "b", "c"), good, 5);
        double low = EvalMetrics.ndcgAt(java.util.List.of("c", "z", "a"), good, 5);
        assertThat(high).isGreaterThan(low);
        assertThat(EvalMetrics.precisionAt(java.util.List.of("a", "b", "c", "d", "e"), good, 5)).isEqualTo(0.4);
    }
}
