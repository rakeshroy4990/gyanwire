package com.gyanwire.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gyanwire.research.engine.PointersService;
import com.gyanwire.research.rank.HybridRanker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class EvalRunner {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PointersService pointers = new PointersService();

    public ObjectNode run(Path goldenDir) throws IOException {
        long started = System.nanoTime();
        double pointerNdcg = 0;
        double hybridNdcg = 0;
        double precision = 0;
        double trusted = 0;
        int queries = 0;
        ArrayNode industries = mapper.createArrayNode();
        try (Stream<Path> files = Files.list(goldenDir)) {
            List<Path> json = files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
            for (Path file : json) {
                JsonNode root = mapper.readTree(file.toFile());
                String industry = root.path("industry").asText();
                double indNdcg = 0;
                int indQueries = 0;
                for (JsonNode query : root.path("queries")) {
                    List<Map<String, Object>> pages = score(industry, query);
                    List<String> pointerOrder = urls(pages);
                    List<Map<String, Object>> fused = HybridRanker.fuse(
                            pages, query.path("query").asText(""), HybridRanker.halfLifeDays(industry));
                    List<String> hybridOrder = urls(fused);
                    var good = EvalMetrics.setOf(textList(query.path("good")));
                    pointerNdcg += EvalMetrics.ndcgAt(pointerOrder, good, 5);
                    hybridNdcg += EvalMetrics.ndcgAt(hybridOrder, good, 5);
                    precision += EvalMetrics.precisionAt(pointerOrder, good, 5);
                    trusted += EvalMetrics.trustedShare(pointerOrder, 5);
                    queries++;
                    indQueries++;
                    indNdcg += EvalMetrics.ndcgAt(pointerOrder, good, 5);
                }
                ObjectNode row = mapper.createObjectNode();
                row.put("industry", industry);
                row.put("queries", indQueries);
                row.put("ndcgAt5", round(indQueries == 0 ? 0 : indNdcg / indQueries));
                industries.add(row);
            }
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        ObjectNode report = mapper.createObjectNode();
        report.put("date", LocalDate.now().toString());
        report.put("queries", queries);
        report.put("ndcgAt5", round(queries == 0 ? 0 : pointerNdcg / queries));
        report.put("hybridNdcgAt5", round(queries == 0 ? 0 : hybridNdcg / queries));
        report.put("precisionAt5", round(queries == 0 ? 0 : precision / queries));
        report.put("trustedDomainShare", round(queries == 0 ? 0 : trusted / queries));
        report.put("latencyMs", latencyMs);
        report.put("llmCalls", 0);
        report.set("industries", industries);
        return report;
    }

    public Path writeReport(Path goldenDir, Path reportsDir) throws IOException {
        Files.createDirectories(reportsDir);
        ObjectNode report = run(goldenDir);
        Path out = reportsDir.resolve("baseline-" + report.path("date").asText() + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
        return out;
    }

    private List<Map<String, Object>> score(String industry, JsonNode query) {
        List<Map<String, Object>> pages = new ArrayList<>();
        int index = 0;
        for (JsonNode candidate : query.path("candidates")) {
            Map<String, Object> page = new HashMap<>();
            candidate.fields().forEachRemaining(e -> page.put(e.getKey(), e.getValue().isNumber()
                    ? e.getValue().numberValue()
                    : e.getValue().asText()));
            Map<String, Object> scored = pointers.score(
                    page,
                    List.of(industry),
                    query.path("thoughts").asText(""),
                    query.path("query").asText(""),
                    index++);
            page.put("score", scored.get("score"));
            page.put("why", scored.get("why"));
            pages.add(page);
        }
        pages.sort(Comparator.comparingInt((Map<String, Object> p) -> ((Number) p.get("score")).intValue()).reversed());
        return pages;
    }

    private static List<String> urls(List<Map<String, Object>> pages) {
        List<String> urls = new ArrayList<>();
        for (Map<String, Object> page : pages) {
            urls.add(String.valueOf(page.get("url")));
        }
        return urls;
    }

    private static List<String> textList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                out.add(item.asText());
            }
        }
        return out;
    }

    public static Path repoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (cwd.getFileName() != null && cwd.getFileName().toString().equals("gyanwire-server")) {
            return cwd.getParent();
        }
        return cwd;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
