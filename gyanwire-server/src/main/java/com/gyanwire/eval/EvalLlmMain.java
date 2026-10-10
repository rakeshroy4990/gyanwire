package com.gyanwire.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gyanwire.llm.PlanWeekRules;
import com.gyanwire.llm.QuerySharpener;
import com.gyanwire.llm.RerankGate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EvalLlmMain {

    private EvalLlmMain() {
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path root = EvalRunner.repoRoot();
        var golden = mapper.readTree(root.resolve("eval/llm/golden.json").toFile());
        int schemaOk = 0;
        int schemaTotal = 0;
        for (var sample : golden.path("schemaSamples")) {
            schemaTotal++;
            if (sample.path("passes").asBoolean()) {
                schemaOk++;
            }
        }
        double schemaRate = schemaTotal == 0 ? 0 : (double) schemaOk / schemaTotal;
        double overlap = 0;
        int queries = 0;
        for (var query : golden.path("queries")) {
            List<String> pointer = text(query.path("pointer"));
            List<String> optimized = text(query.path("optimized"));
            overlap += top3(pointer, optimized);
            queries++;
        }
        double meanOverlap = queries == 0 ? 0 : overlap / queries;
        boolean messyOk = QuerySharpener.messy("UPI") && !QuerySharpener.messy("India UPI merchant subsidy");
        List<Map<String, Object>> wide = List.of(Map.of("score", 90), Map.of("score", 70));
        List<Map<String, Object>> close = List.of(Map.of("score", 80), Map.of("score", 76));
        boolean gateOk = !RerankGate.shouldRerank(wide) && RerankGate.shouldRerank(close);
        ObjectNode weeks = twelveWeeks(mapper);
        boolean weeksOk = PlanWeekRules.valid(weeks, Set.of("notes", "sheet"), 100);
        boolean pass = schemaRate >= 0.95 && meanOverlap >= 0.8 && messyOk && gateOk && weeksOk;
        ObjectNode report = mapper.createObjectNode();
        report.put("date", java.time.LocalDate.now().toString());
        report.put("queries", queries);
        report.put("schemaPassRate", Math.round(schemaRate * 1000.0) / 1000.0);
        report.put("top3Overlap", Math.round(meanOverlap * 1000.0) / 1000.0);
        report.put("messyGate", messyOk && gateOk);
        report.put("weekRules", weeksOk);
        report.put("pass", pass);
        Path out = root.resolve("eval/reports/llm-eval-" + report.path("date").asText() + ".json");
        Files.createDirectories(out.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        if (!pass) {
            System.exit(1);
        }
    }

    private static ObjectNode twelveWeeks(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode weeks = root.putArray("weeks");
        for (int i = 1; i <= 12; i++) {
            ObjectNode week = weeks.addObject();
            week.put("title", "Week " + i + " goal");
            week.put("toolId", i <= 6 ? "notes" : "sheet");
            week.put("costInr", 50);
            week.putArray("steps").add("Do the step");
        }
        return root;
    }

    private static double top3(List<String> pointer, List<String> optimized) {
        Set<String> good = new HashSet<>(pointer.subList(0, Math.min(3, pointer.size())));
        int hit = 0;
        int limit = Math.min(3, optimized.size());
        for (int i = 0; i < limit; i++) {
            if (good.contains(optimized.get(i))) {
                hit++;
            }
        }
        return limit == 0 ? 0 : (double) hit / 3.0;
    }

    private static List<String> text(com.fasterxml.jackson.databind.JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (var item : node) {
                out.add(item.asText());
            }
        }
        return out;
    }
}
