package com.gyanwire.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NewsNdjsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void eachRecordIsFlushedBeforeTheNextIsWritten() throws Exception {
        FlushLog log = new FlushLog();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", "Top searched product news");
        data.put("freshness", "last 7 days");
        data.put("message", "Two signals");
        data.put("results", List.of(
                Map.of("title", "First launch", "url", "https://a.example/1"),
                Map.of("title", "Second launch", "url", "https://a.example/2")
        ));
        data.put("items", data.get("results"));

        NewsNdjson.writeFeed(log, mapper, data);

        assertThat(log.flushedAt).hasSize(4);
        String body = log.text();
        String[] lines = body.split("\n");
        assertThat(lines).hasSize(4);
        int end = 0;
        for (int i = 0; i < lines.length; i++) {
            end += lines[i].getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
            assertThat(log.flushedAt.get(i)).isEqualTo(end);
            JsonNode node = mapper.readTree(lines[i]);
            assertThat(node.has("results")).isFalse();
            assertThat(node.has("items")).isFalse();
        }
        assertThat(mapper.readTree(lines[0]).get("type").asText()).isEqualTo("meta");
        assertThat(mapper.readTree(lines[1]).get("type").asText()).isEqualTo("item");
        assertThat(mapper.readTree(lines[1]).get("item").get("title").asText()).isEqualTo("First launch");
        assertThat(mapper.readTree(lines[2]).get("item").get("title").asText()).isEqualTo("Second launch");
        assertThat(mapper.readTree(lines[3]).get("type").asText()).isEqualTo("done");
        assertThat(mapper.readTree(lines[3]).get("returned").asInt()).isEqualTo(2);
    }

    @Test
    void openLineIsFlushedBeforeTheFeedIsLoaded() throws Exception {
        FlushLog log = new FlushLog();
        AtomicInteger flushesBeforeLoad = new AtomicInteger(-1);

        NewsNdjson.writeDefault(log, mapper, () -> {
            flushesBeforeLoad.set(log.flushedAt.size());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("results", List.of(Map.of("title", "Only", "url", "https://a.example/only")));
            data.put("freshness", "last 7 days");
            data.put("label", "Top searched product news");
            return data;
        });

        assertThat(flushesBeforeLoad.get()).isEqualTo(1);
        String[] lines = log.text().split("\n");
        assertThat(lines).hasSize(4);
        assertThat(mapper.readTree(lines[0]).get("type").asText()).isEqualTo("open");
        assertThat(mapper.readTree(lines[1]).get("type").asText()).isEqualTo("meta");
        assertThat(mapper.readTree(lines[1]).get("intent").asText()).isEqualTo("products");
        assertThat(mapper.readTree(lines[1]).get("usedLlm").asBoolean()).isFalse();
        assertThat(mapper.readTree(lines[2]).get("type").asText()).isEqualTo("item");
        assertThat(mapper.readTree(lines[3]).get("type").asText()).isEqualTo("done");
    }

    @Test
    void loadFailureIsItsOwnLineAfterOpen() throws Exception {
        FlushLog log = new FlushLog();
        NewsNdjson.writeDefault(log, mapper, () -> {
            throw new IllegalStateException("database offline");
        });
        String[] lines = log.text().split("\n");
        assertThat(lines).hasSize(2);
        assertThat(mapper.readTree(lines[0]).get("type").asText()).isEqualTo("open");
        assertThat(mapper.readTree(lines[1]).get("type").asText()).isEqualTo("error");
        assertThat(mapper.readTree(lines[1]).get("message").asText()).isEqualTo("database offline");
        assertThat(log.flushedAt).hasSize(2);
    }

    @Test
    void clientDisconnectDuringFeedIsSwallowed() throws Exception {
        DropAfterOpen drop = new DropAfterOpen();
        NewsNdjson.writeDefault(drop, mapper, () -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("results", List.of(Map.of("title", "Only", "url", "https://a.example/only")));
            data.put("label", "AI");
            return data;
        });
        assertThat(drop.text()).contains("\"type\":\"open\"");
        assertThat(drop.text()).doesNotContain("\"type\":\"error\"");
    }

    private static final class DropAfterOpen extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int lines;

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                lines++;
                if (lines > 1) {
                    throw new IOException("Broken pipe");
                }
            }
            bytes.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) {
                write(b[i]);
            }
        }

        String text() {
            return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
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
            return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
