package com.gyanwire.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The only production HTTP call to /chat/completions.
 * Tiered models omit temperature. Chat Completions names the effort field reasoning_effort.
 */
@Service
public class OpenAiClient {

    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final LlmClient.LlmTransport transport;
    private final Runnable pause;

    @Autowired
    public OpenAiClient(
            @Value("${app.llm.api-key:}") String apiKey,
            @Value("${app.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            ObjectMapper mapper
    ) {
        this(apiKey, baseUrl, mapper, jdk(Duration.ofSeconds(45)), OpenAiClient::jitter);
    }

    OpenAiClient(String apiKey, String baseUrl, ObjectMapper mapper, LlmClient.LlmTransport transport, Runnable pause) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "https://api.openai.com/v1" : baseUrl.replaceAll("/$", "");
        this.mapper = mapper;
        this.transport = transport;
        this.pause = pause == null ? () -> { } : pause;
    }

    LlmClient.LlmTransport transport() {
        return transport;
    }

    public boolean configured() {
        return !apiKey.isBlank() && !apiKey.contains("your-key");
    }

    public Exchange complete(String model, String system, String user, String reasoningEffort, Integer maxOutputTokens) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        if (reasoningEffort == null || reasoningEffort.isBlank()) {
            payload.put("temperature", 0.2);
        } else {
            payload.put("reasoning_effort", reasoningEffort);
        }
        payload.put("response_format", Map.of("type", "json_object"));
        if (maxOutputTokens != null && maxOutputTokens > 0) {
            payload.put("max_completion_tokens", maxOutputTokens);
        }
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system == null ? "" : system),
                Map.of("role", "user", "content", user == null ? "" : user)
        ));
        try {
            String json = mapper.writeValueAsString(payload);
            LlmClient.LlmHttpResult http = postWithRetry(json);
            return parse(http);
        } catch (Exception ex) {
            return new Exchange(null, 0, null, null, null, false, snippet(ex.getMessage()), null);
        }
    }

    private LlmClient.LlmHttpResult postWithRetry(String json) throws Exception {
        URI uri = URI.create(baseUrl + "/chat/completions");
        LlmClient.LlmHttpResult first = transport.post(uri, json, apiKey);
        if (first != null && (first.status() == 429 || first.status() >= 500)) {
            pause.run();
            LlmClient.LlmHttpResult second = transport.post(uri, json, apiKey);
            return second == null ? first : second;
        }
        return first;
    }

    private Exchange parse(LlmClient.LlmHttpResult http) {
        if (http == null) {
            return new Exchange(null, 0, null, null, null, false, "empty http result", null);
        }
        if (http.status() >= 400 || http.body() == null) {
            return new Exchange(null, http.status(), null, null, null, false, snippet(http.body()), null);
        }
        try {
            JsonNode root = mapper.readTree(http.body());
            Integer in = intOrNull(root.path("usage").path("prompt_tokens"));
            Integer out = intOrNull(root.path("usage").path("completion_tokens"));
            Integer cached = intOrNull(root.path("usage").path("prompt_tokens_details").path("cached_tokens"));
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return new Exchange(null, http.status(), in, cached, out, false, "empty completion", null);
            }
            return new Exchange(mapper.readTree(content), http.status(), in, cached, out, true, null, content);
        } catch (Exception ex) {
            return new Exchange(null, http.status(), null, null, null, false, snippet(ex.getMessage()), null);
        }
    }

    static LlmClient.LlmTransport jdk(Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        Duration limit = timeout == null ? Duration.ofSeconds(45) : timeout;
        return (uri, jsonBody, key) -> {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(limit)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return new LlmClient.LlmHttpResult(res.statusCode(), res.body());
        };
    }

    private static void jitter() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(20, 80));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }

    private static String snippet(String raw) {
        if (raw == null || raw.isBlank()) {
            return "request failed";
        }
        String trimmed = raw.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240);
    }

    public record Exchange(
            JsonNode json,
            int status,
            Integer inputTokens,
            Integer cachedInputTokens,
            Integer outputTokens,
            boolean ok,
            String error,
            String text
    ) {
    }
}
