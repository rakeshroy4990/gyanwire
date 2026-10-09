package com.gyanwire.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.persistence.postgres.model.LlmCallEntity;
import com.gyanwire.persistence.postgres.repository.LlmCallRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void successWritesOneCallWithTokens() throws Exception {
        LlmCallRepository calls = mock(LlmCallRepository.class);
        when(calls.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicReference<String> body = new AtomicReference<>();
        LlmClient client = client(calls, (uri, jsonBody, apiKey) -> {
            body.set(jsonBody);
            assertThat(uri.toString()).endsWith("/chat/completions");
            String response = """
                    {"usage":{"prompt_tokens":11,"completion_tokens":4},
                     "choices":[{"message":{"content":"{\\"query\\":\\"q\\"}"}}]}
                    """;
            return new LlmClient.LlmHttpResult(200, response);
        });

        JsonNode parsed = client.complete(null, "query-sharpen", "query-sharpen.v0", "system", "notes");

        assertThat(parsed.path("query").asText()).isEqualTo("q");
        JsonNode sent = mapper.readTree(body.get());
        assertThat(sent.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(sent.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(sent.path("model").asText()).isEqualTo("gpt-4o-mini");
        LlmCallEntity row = captured(calls);
        assertThat(row.isOk()).isTrue();
        assertThat(row.getFeature()).isEqualTo("query-sharpen");
        assertThat(row.getPromptVersion()).isEqualTo("query-sharpen.v0");
        assertThat(row.getTokensIn()).isEqualTo(11);
        assertThat(row.getTokensOut()).isEqualTo(4);
        assertThat(row.getLatencyMs()).isNotNegative();
    }

    @Test
    void httpErrorLogsFailureAndReturnsNull() {
        LlmCallRepository calls = mock(LlmCallRepository.class);
        when(calls.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LlmClient client = client(calls, (uri, jsonBody, apiKey) -> new LlmClient.LlmHttpResult(429, "{\"error\":\"no\"}"));

        JsonNode parsed = client.complete(null, "blend-rank", "blend-rank.v0", "system", "user");

        assertThat(parsed).isNull();
        LlmCallEntity row = captured(calls);
        assertThat(row.isOk()).isFalse();
        assertThat(row.getPromptVersion()).isEqualTo("blend-rank.v0");
    }

    @Test
    void logFailureDoesNotDropASuccessfulCompletion() {
        LlmCallRepository calls = mock(LlmCallRepository.class);
        when(calls.save(any())).thenThrow(new RuntimeException("db down"));
        LlmClient client = client(calls, (uri, jsonBody, apiKey) -> new LlmClient.LlmHttpResult(
                200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}"
        ));

        JsonNode parsed = client.complete(null, "query-sharpen", "query-sharpen.v0", "system", "user");

        assertThat(parsed.path("ok").asBoolean()).isTrue();
    }

    @Test
    void unconfiguredClientDoesNotCallTransport() {
        LlmCallRepository calls = mock(LlmCallRepository.class);
        LlmClient client = new LlmClient(
                "",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                mapper,
                calls,
                (uri, jsonBody, apiKey) -> {
                    throw new AssertionError("transport called");
                }
        );

        assertThat(client.complete(null, "query-sharpen", "query-sharpen.v0", "s", "u")).isNull();
    }

    private LlmClient client(LlmCallRepository calls, LlmClient.LlmTransport transport) {
        return new LlmClient("sk-test", "https://api.openai.com/v1", "gpt-4o-mini", mapper, calls, transport);
    }

    private static LlmCallEntity captured(LlmCallRepository calls) {
        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(calls).save(captor.capture());
        return captor.getValue();
    }
}
