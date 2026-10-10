package com.gyanwire.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.LlmClient;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EvalModelBakeoffMain {

    private EvalModelBakeoffMain() {
    }

    public static void main(String[] args) throws Exception {
        String apiKey = env("LLM_API_KEY");
        String baseUrl = envOr("LLM_BASE_URL", "https://api.openai.com/v1");
        String lowModel = envOr("LLM_MODEL", "gpt-4o-mini");
        String configuredHigh = env("LLM_MODEL_HIGH");
        String highModel = configuredHigh.isBlank() || configuredHigh.equals(lowModel) ? "gpt-5.4" : configuredHigh;
        ObjectMapper mapper = new ObjectMapper();
        LlmClient low = LlmClient.pinned(apiKey, baseUrl, lowModel, mapper);
        LlmClient high = LlmClient.pinned(apiKey, baseUrl, highModel, mapper);
        var report = ModelBakeoff.compare(low, high, mapper);
        Path reports = EvalRunner.repoRoot().resolve("eval/reports");
        Files.createDirectories(reports);
        Path out = reports.resolve("model-bakeoff-" + report.path("date").asText() + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("Wrote " + out);
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private static String envOr(String key, String fallback) {
        String value = env(key);
        return value.isBlank() ? fallback : value;
    }
}
