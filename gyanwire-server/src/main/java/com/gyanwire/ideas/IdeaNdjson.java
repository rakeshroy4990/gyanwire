package com.gyanwire.ideas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.ActionPlanException;
import com.gyanwire.config.ClientDisconnect;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.research.ResearchException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One JSON object per line, flushed before the next line is written.
 * The open line goes out before ideas are built so the client is not
 * held until the whole payload exists. Later lines leave as the
 * producer emits status, meta, and item events.
 */
public final class IdeaNdjson {

    @FunctionalInterface
    public interface Producer {
        void produce(IdeaSink sink) throws Exception;
    }

    private IdeaNdjson() {
    }

    public static void write(OutputStream output, ObjectMapper mapper, Producer producer) throws IOException {
        write(output, mapper, producer, null);
    }

    public static void write(
            OutputStream output,
            ObjectMapper mapper,
            Producer producer,
            Runnable flush
    ) throws IOException {
        AtomicInteger returned = new AtomicInteger();
        IdeaSink sink = new IdeaSink() {
            @Override
            public void status(String stage) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("type", "status");
                line.put("stage", stage == null ? "" : stage);
                writeChecked(output, mapper, line, flush);
            }

            @Override
            public void meta(Map<String, Object> frame) {
                Map<String, Object> line = new LinkedHashMap<>();
                if (frame != null) {
                    frame.forEach((key, value) -> {
                        if (!"ideas".equals(key) && !"type".equals(key)) {
                            line.put(key, value);
                        }
                    });
                }
                line.put("type", "meta");
                line.put("success", true);
                writeChecked(output, mapper, line, flush);
            }

            @Override
            public void item(Map<String, Object> row) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("type", "item");
                line.put("item", row);
                writeChecked(output, mapper, line, flush);
                returned.incrementAndGet();
            }

            @Override
            public void research(Map<String, Object> frame) {
                Map<String, Object> line = new LinkedHashMap<>();
                if (frame != null) {
                    frame.forEach((key, value) -> {
                        if (!"type".equals(key)) {
                            line.put(key, value);
                        }
                    });
                }
                line.put("type", "research");
                writeChecked(output, mapper, line, flush);
            }
        };
        try {
            writeRecord(output, mapper, openLine(), flush);
            producer.produce(sink);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("type", "done");
            done.put("success", true);
            done.put("returned", returned.get());
            writeRecord(output, mapper, done, flush);
        } catch (Exception ex) {
            if (ClientDisconnect.isGone(ex)) {
                return;
            }
            try {
                writeRecord(output, mapper, errorLine(ex), flush);
            } catch (Exception writeEx) {
                if (ClientDisconnect.isGone(writeEx)) {
                    return;
                }
                if (writeEx instanceof IOException io) {
                    throw io;
                }
                if (writeEx instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IOException(writeEx);
            }
        }
    }

    /**
     * Compatibility helper for tests that still supply one finished map.
     */
    public static void write(
            OutputStream output,
            ObjectMapper mapper,
            java.util.function.Supplier<Map<String, Object>> load,
            Runnable flush
    ) throws IOException {
        write(output, mapper, (IdeaSink sink) -> emitMap(sink, load.get()), flush);
    }

    public static void write(
            OutputStream output,
            ObjectMapper mapper,
            java.util.function.Supplier<Map<String, Object>> load
    ) throws IOException {
        write(output, mapper, load, null);
    }

    public static void emitMap(IdeaSink sink, Map<String, Object> data) {
        if (sink == null || data == null) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>(data);
        @SuppressWarnings("unchecked")
        Object ideasObj = meta.remove("ideas");
        sink.meta(meta);
        if (ideasObj instanceof Iterable<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> map) {
                    Map<String, Object> idea = new LinkedHashMap<>();
                    map.forEach((key, value) -> idea.put(String.valueOf(key), value));
                    sink.item(idea);
                }
            }
        }
    }

    public static void writeLine(OutputStream output, ObjectMapper mapper, Map<String, Object> line) throws IOException {
        output.write(mapper.writeValueAsBytes(line));
        output.write('\n');
        output.flush();
    }

    public static Map<String, Object> openLine() {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "open");
        line.put("intent", "ideas");
        return line;
    }

    private static void writeChecked(
            OutputStream output,
            ObjectMapper mapper,
            Map<String, Object> line,
            Runnable flush
    ) {
        try {
            writeRecord(output, mapper, line, flush);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void writeRecord(
            OutputStream output,
            ObjectMapper mapper,
            Map<String, Object> line,
            Runnable flush
    ) throws IOException {
        writeLine(output, mapper, line);
        if (flush != null) {
            flush.run();
        }
    }

    private static Map<String, Object> errorLine(Exception ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "error");
        error.put("success", false);
        error.put("message", ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Could not build ideas."
                : ex.getMessage());
        if (ex instanceof PlanLimitException limit) {
            error.put("errorCode", limit.getErrorCode());
            error.put("status", 402);
        } else if (ex instanceof ActionPlanException plan) {
            error.put("errorCode", plan.getErrorCode());
            error.put("status", plan.getStatus());
            if (plan.getData() != null && !plan.getData().isEmpty()) {
                error.put("data", plan.getData());
            }
        } else if (ex instanceof ResearchException research) {
            error.put("errorCode", research.getErrorCode());
            error.put("status", research.getStatus());
        } else if (ex instanceof UncheckedIOException io && io.getCause() != null) {
            error.put("status", 500);
            error.put("message", "Could not build ideas.");
        } else {
            error.put("status", 500);
        }
        return error;
    }
}
