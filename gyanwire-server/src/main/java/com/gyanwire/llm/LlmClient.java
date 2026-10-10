package com.gyanwire.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.persistence.postgres.model.LlmCallEntity;
import com.gyanwire.persistence.postgres.repository.LlmCallRepository;
import com.gyanwire.usage.UsageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
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
    private final ModelRouter router;
    private final ObjectMapper mapper;
    private final LlmCallRepository calls;
    private final LlmTransport transport;
    private final HighModelGate highModelGate;

    @Autowired
    public LlmClient(
            @Value("${app.llm.api-key:}") String apiKey,
            @Value("${app.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.llm.model:gpt-4o-mini}") String model,
            @Value("${app.llm.model-high:gpt-4o-mini}") String highModel,
            ObjectMapper mapper,
            LlmCallRepository calls,
            OpenAiClient openAi,
            ObjectProvider<UsageService> usage
    ) {
        this(apiKey, baseUrl, model, highModel, mapper, calls, openAi.transport(), userId -> {
            UsageService service = usage == null ? null : usage.getIfAvailable();
            return service != null && service.allowsHighModel(userId);
        });
    }

    LlmClient(
            String apiKey,
            String baseUrl,
            String model,
            ObjectMapper mapper,
            LlmCallRepository calls,
            LlmTransport transport
    ) {
        this(apiKey, baseUrl, model, model, mapper, calls, transport, null);
    }

    LlmClient(
            String apiKey,
            String baseUrl,
            String lowModel,
            String highModel,
            ObjectMapper mapper,
            LlmCallRepository calls,
            LlmTransport transport,
            HighModelGate highModelGate
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "https://api.openai.com/v1" : baseUrl.replaceAll("/$", "");
        this.router = new ModelRouter(lowModel, highModel);
        this.mapper = mapper;
        this.calls = calls;
        this.transport = transport;
        this.highModelGate = highModelGate;
    }

    /** One pinned model, no call log. Used by the idea/outline bakeoff. */
    public static LlmClient pinned(String apiKey, String baseUrl, String model, ObjectMapper mapper) {
        return new LlmClient(
                apiKey,
                baseUrl,
                model,
                model,
                mapper,
                null,
                OpenAiClient.jdk(Duration.ofSeconds(120)),
                null
        );
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.contains("your-key");
    }

    public JsonNode complete(UUID userId, String feature, String promptVersion, String system, String user) {
        CallResult result = completeCall(userId, feature, promptVersion, system, user);
        return result.ok() ? result.parsed() : null;
    }

    public CallResult completeCall(UUID userId, String feature, String promptVersion, String system, String user) {
        boolean highTier = highModelGate != null && highModelGate.allowed(userId);
        String chosen = router.modelFor(feature, highTier);
        if (!isConfigured()) {
            return new CallResult(null, chosen, 0, null, null, 0, false, "llm not configured");
        }
        long started = System.nanoTime();
        Integer tokensIn = null;
        Integer tokensOut = null;
        boolean ok = false;
        JsonNode parsed = null;
        int status = 0;
        String error = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", chosen);
            payload.put("temperature", 0.2);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)
            ));
            URI uri = URI.create(baseUrl + "/chat/completions");
            LlmHttpResult result = transport.post(uri, mapper.writeValueAsString(payload), apiKey);
            if (result != null) {
                status = result.status();
                if (result.status() < 400 && result.body() != null) {
                    JsonNode root = mapper.readTree(result.body());
                    tokensIn = intOrNull(root.path("usage").path("prompt_tokens"));
                    tokensOut = intOrNull(root.path("usage").path("completion_tokens"));
                    String content = root.path("choices").path(0).path("message").path("content").asText("");
                    if (!content.isBlank()) {
                        parsed = mapper.readTree(content);
                        ok = true;
                    } else {
                        error = "empty completion";
                    }
                } else {
                    error = snippet(result.body());
                }
            } else {
                error = "empty http result";
            }
        } catch (Exception ex) {
            parsed = null;
            ok = false;
            error = snippet(ex.getMessage());
        }
        int latency = latencyMs(started);
        log(userId, feature, promptVersion, chosen, tokensIn, tokensOut, latency, ok);
        return new CallResult(ok ? parsed : null, chosen, status, tokensIn, tokensOut, latency, ok, ok ? null : error);
    }

    private void log(
            UUID userId,
            String feature,
            String promptVersion,
            String model,
            Integer tokensIn,
            Integer tokensOut,
            int latencyMs,
            boolean ok
    ) {
        if (calls == null) {
            return;
        }
        try {
            LlmCallEntity row = new LlmCallEntity();
            row.setId(UUID.randomUUID());
            row.setUserId(userId);
            row.setFeature(feature);
            row.setPromptVersion(promptVersion);
            row.setModel(model);
            row.setTokensIn(tokensIn);
            row.setCachedInputTokens(0);
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

    private static String snippet(String raw) {
        if (raw == null || raw.isBlank()) {
            return "request failed";
        }
        String trimmed = raw.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240);
    }

    @FunctionalInterface
    interface HighModelGate {
        boolean allowed(UUID userId);
    }

    public record CallResult(
            JsonNode parsed,
            String model,
            int status,
            Integer tokensIn,
            Integer tokensOut,
            int latencyMs,
            boolean ok,
            String error
    ) {
    }
}
