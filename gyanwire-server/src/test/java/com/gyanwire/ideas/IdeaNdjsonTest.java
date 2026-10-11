package com.gyanwire.ideas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.config.PlanLimitException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdeaNdjsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void eachIdeaIsFlushedBeforeTheNextIsWritten() throws Exception {
        FlushLog log = new FlushLog();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", "run-1");
        data.put("cached", false);
        data.put("pipeline", "idea-pipeline.v2");
        data.put("ideas", List.of(
                Map.of("id", "1", "title", "First idea"),
                Map.of("id", "2", "title", "Second idea")
        ));

        IdeaNdjson.write(log, mapper, () -> data);

        assertThat(log.flushedAt).hasSize(5);
        String[] lines = log.text().split("\n");
        assertThat(lines).hasSize(5);
        int end = 0;
        for (int i = 0; i < lines.length; i++) {
            end += lines[i].getBytes(StandardCharsets.UTF_8).length + 1;
            assertThat(log.flushedAt.get(i)).isEqualTo(end);
            assertThat(mapper.readTree(lines[i]).has("ideas")).isFalse();
        }
        assertThat(mapper.readTree(lines[0]).get("type").asText()).isEqualTo("open");
        assertThat(mapper.readTree(lines[1]).get("type").asText()).isEqualTo("meta");
        assertThat(mapper.readTree(lines[1]).get("runId").asText()).isEqualTo("run-1");
        assertThat(mapper.readTree(lines[2]).get("item").get("title").asText()).isEqualTo("First idea");
        assertThat(mapper.readTree(lines[3]).get("item").get("title").asText()).isEqualTo("Second idea");
        assertThat(mapper.readTree(lines[4]).get("returned").asInt()).isEqualTo(2);
    }

    @Test
    void openLineIsFlushedBeforeIdeasAreBuilt() throws Exception {
        FlushLog log = new FlushLog();
        AtomicInteger flushesBeforeLoad = new AtomicInteger(-1);

        IdeaNdjson.write(log, mapper, () -> {
            flushesBeforeLoad.set(log.flushedAt.size());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ideas", List.of(Map.of("title", "Only")));
            data.put("cached", false);
            return data;
        });

        assertThat(flushesBeforeLoad.get()).isEqualTo(1);
        String[] lines = log.text().split("\n");
        assertThat(mapper.readTree(lines[0]).get("type").asText()).isEqualTo("open");
        assertThat(mapper.readTree(lines[0]).get("intent").asText()).isEqualTo("ideas");
        assertThat(mapper.readTree(lines[2]).get("type").asText()).isEqualTo("item");
    }

    @Test
    void limitFailureIsItsOwnLineAfterOpen() throws Exception {
        FlushLog log = new FlushLog();
        IdeaNdjson.write(log, mapper, () -> {
            throw new PlanLimitException("Daily idea limit reached.", "LIMIT_REACHED", Map.of("upgradeUrl", "/pricing"));
        });
        String[] lines = log.text().split("\n");
        assertThat(lines).hasSize(2);
        assertThat(mapper.readTree(lines[0]).get("type").asText()).isEqualTo("open");
        assertThat(mapper.readTree(lines[1]).get("type").asText()).isEqualTo("error");
        assertThat(mapper.readTree(lines[1]).get("errorCode").asText()).isEqualTo("LIMIT_REACHED");
        assertThat(mapper.readTree(lines[1]).get("status").asInt()).isEqualTo(402);
        assertThat(log.flushedAt).hasSize(2);
    }

    private static final class FlushLog extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final List<Integer> flushedAt = new ArrayList<>();

        @Override
        public void write(int b) {
            bytes.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            bytes.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            flushedAt.add(bytes.size());
        }

        String text() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
