package com.gyanwire.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.persistence.postgres.model.LlmCallEntity;
import com.gyanwire.persistence.postgres.repository.LlmCallRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LlmClient {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final ObjectMapper mapper;
    private final LlmCallRepository calls;
    private final LlmTransport transport;

    @Autowired
    public LlmClient(
            @Value("${app.llm.api-key:}") String apiKey,
            @Value("${app.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.llm.model:gpt-4o-mini}") String model,
            ObjectMapper mapper,
            LlmCallRepository calls
    ) {
        this(apiKey, baseUrl, model, mapper, calls, new JdkLlmTransport());
    }

    LlmClient(
            String apiKey,
            String baseUrl,
            String model,
            ObjectMapper mapper,
            LlmCallRepository calls,
            LlmTransport transport
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "https://api.openai.com/v1" : baseUrl.replaceAll("/$", "");
        this.model = model;
        this.mapper = mapper;
        this.calls = calls;
        this.transport = transport;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.contains("your-key");
    }

    public JsonNode complete(UUID userId, String feature, String promptVersion, String system, String user) {
        if (!isConfigured()) {
            return null;
        }
        long started = System.nanoTime();
        Integer tokensIn = null;
        Integer tokensOut = null;
        boolean ok = false;
        JsonNode parsed = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)
            ));
            URI uri = URI.create(baseUrl + "/chat/completions");
            LlmHttpResult result = transport.post(uri, mapper.writeValueAsString(payload), apiKey);
            if (result != null && result.status() < 400 && result.body() != null) {
                JsonNode root = mapper.readTree(result.body());
                tokensIn = intOrNull(root.path("usage").path("prompt_tokens"));
                tokensOut = intOrNull(root.path("usage").path("completion_tokens"));
                String content = root.path("choices").path(0).path("message").path("content").asText("");
                if (!content.isBlank()) {
                    parsed = mapper.readTree(content);
                    ok = true;
                }
            }
        } catch (Exception ignored) {
            parsed = null;
            ok = false;
        }
        log(userId, feature, promptVersion, tokensIn, tokensOut, latencyMs(started), ok);
        return ok ? parsed : null;
    }

    private void log(
            UUID userId,
            String feature,
            String promptVersion,
            Integer tokensIn,
            Integer tokensOut,
            int latencyMs,
            boolean ok
    ) {
        try {
            LlmCallEntity row = new LlmCallEntity();
            row.setId(UUID.randomUUID());
            row.setUserId(userId);
            row.setFeature(feature);
            row.setPromptVersion(promptVersion);
            row.setTokensIn(tokensIn);
            row.setTokensOut(tokensOut);
            row.setLatencyMs(latencyMs);
            row.setOk(ok);
            row.setCreatedAt(Instant.now());
            calls.save(row);
        } catch (Exception ignored) {
            // A log write must not change the completion result.
        }
    }

    private static int latencyMs(long startedNanos) {
        long ms = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (ms < 0) {
            return 0;
        }
        if (ms > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) ms;
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }

    @FunctionalInterface
    interface LlmTransport {
        LlmHttpResult post(URI uri, String jsonBody, String apiKey) throws Exception;
    }

    record LlmHttpResult(int status, String body) {
    }

    static final class JdkLlmTransport implements LlmTransport {
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        @Override
        public LlmHttpResult post(URI uri, String jsonBody, String apiKey) throws Exception {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return new LlmHttpResult(res.statusCode(), res.body());
        }
    }
}
