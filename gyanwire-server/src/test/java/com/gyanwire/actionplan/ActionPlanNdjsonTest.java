package com.gyanwire.actionplan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.config.PlanLimitException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActionPlanNdjsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void skeletonPlanIsFlushedBeforeProducerFinishes() throws Exception {
        FlushLog log = new FlushLog();
        AtomicInteger flushesAfterSkeleton = new AtomicInteger(-1);

        ActionPlanNdjson.write(log, mapper, sink -> {
            sink.plan(Map.of(
                    "planId", "plan-1",
                    "buildStatus", "building",
                    "kind", "action_plan"));
            flushesAfterSkeleton.set(log.flushedAt.size());
            sink.status("brief");
            sink.plan(Map.of(
                    "planId", "plan-1",
                    "buildStatus", "ready",
                    "kind", "action_plan"));
        });

        assertThat(flushesAfterSkeleton.get()).isEqualTo(2);
        String[] lines = log.text().split("\n");
        assertThat(mapper.readTree(lines[0]).get("type").asText()).isEqualTo("open");
        assertThat(mapper.readTree(lines[0]).get("intent").asText()).isEqualTo("action_plan");
        assertThat(mapper.readTree(lines[1]).get("type").asText()).isEqualTo("plan");
        assertThat(mapper.readTree(lines[1]).get("plan").get("buildStatus").asText()).isEqualTo("building");
        assertThat(mapper.readTree(lines[2]).get("type").asText()).isEqualTo("status");
        assertThat(mapper.readTree(lines[3]).get("plan").get("buildStatus").asText()).isEqualTo("ready");
        assertThat(mapper.readTree(lines[4]).get("type").asText()).isEqualTo("done");
    }

    @Test
    void limitFailureIsItsOwnLineAfterOpen() throws Exception {
        FlushLog log = new FlushLog();
        ActionPlanNdjson.write(log, mapper, sink -> {
            throw new PlanLimitException(
                    "Daily action plan limit reached.",
                    "LIMIT_REACHED",
                    Map.of("upgradeUrl", "/pricing"));
        });

        String[] lines = log.text().split("\n");
        assertThat(mapper.readTree(lines[0]).get("type").asText()).isEqualTo("open");
        assertThat(mapper.readTree(lines[1]).get("type").asText()).isEqualTo("error");
        assertThat(mapper.readTree(lines[1]).get("status").asInt()).isEqualTo(402);
        assertThat(mapper.readTree(lines[1]).get("errorCode").asText()).isEqualTo("LIMIT_REACHED");
    }

    private static final class FlushLog extends OutputStream {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final List<Integer> flushedAt = new ArrayList<>();

        @Override
        public void write(int b) {
            buf.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buf.write(b, off, len);
        }

        @Override
        public void flush() {
            flushedAt.add(buf.size());
        }

        String text() {
            return buf.toString(StandardCharsets.UTF_8);
        }
    }
}
