package com.gyanwire.actionplan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.config.ClientDisconnect;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.research.ResearchException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One JSON object per line, flushed before the next line is written.
 * The open line goes out before the plan is built so the client can
 * navigate as soon as a skeleton plan id arrives.
 */
public final class ActionPlanNdjson {

    @FunctionalInterface
    public interface Producer {
        void produce(ActionPlanSink sink) throws Exception;
    }

    private ActionPlanNdjson() {
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
        ActionPlanSink sink = new ActionPlanSink() {
            @Override
            public void status(String stage) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("type", "status");
                line.put("stage", stage == null ? "" : stage);
                writeChecked(output, mapper, line, flush);
            }

            @Override
            public void plan(Map<String, Object> plan) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("type", "plan");
                line.put("success", true);
                line.put("plan", plan == null ? Map.of() : plan);
                writeChecked(output, mapper, line, flush);
            }
        };
        try {
            writeRecord(output, mapper, openLine(), flush);
            producer.produce(sink);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("type", "done");
            done.put("success", true);
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

    public static void writeLine(OutputStream output, ObjectMapper mapper, Map<String, Object> line) throws IOException {
        output.write(mapper.writeValueAsBytes(line));
        output.write('\n');
        output.flush();
    }

    public static Map<String, Object> openLine() {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "open");
        line.put("intent", "action_plan");
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
                ? "Could not build the plan."
                : ex.getMessage());
        if (ex instanceof PlanLimitException limit) {
            error.put("errorCode", limit.getErrorCode());
            error.put("status", 402);
            if (limit.getData() != null && !limit.getData().isEmpty()) {
                error.put("data", limit.getData());
            }
        } else if (ex instanceof ActionPlanException plan) {
            error.put("errorCode", plan.getErrorCode());
            error.put("status", plan.getStatus());
            if (plan.getData() != null && !plan.getData().isEmpty()) {
                error.put("data", plan.getData());
            }
        } else if (ex instanceof ResearchException research) {
            error.put("errorCode", research.getErrorCode());
            error.put("status", research.getStatus());
        } else if (ex instanceof UncheckedIOException) {
            error.put("status", 500);
            error.put("message", "Could not build the plan.");
        } else {
            error.put("status", 500);
        }
        return error;
    }
}
