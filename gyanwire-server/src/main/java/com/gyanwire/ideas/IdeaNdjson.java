package com.gyanwire.ideas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.research.ResearchException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One JSON object per line, flushed before the next line is written.
 * The open line goes out before ideas are built so the client is not
 * held until the whole payload exists.
 */
public final class IdeaNdjson {

    private IdeaNdjson() {
    }

    public static void write(OutputStream output, ObjectMapper mapper, Supplier<Map<String, Object>> load)
            throws IOException {
        write(output, mapper, load, null);
    }

    public static void write(
            OutputStream output,
            ObjectMapper mapper,
            Supplier<Map<String, Object>> load,
            Runnable flush
    ) throws IOException {
        try {
            writeRecord(output, mapper, openLine(), flush);
            Map<String, Object> data = new LinkedHashMap<>(load.get());
            List<Map<String, Object>> ideas = ideas(data);
            Map<String, Object> meta = new LinkedHashMap<>(data);
            meta.remove("ideas");
            meta.put("type", "meta");
            meta.put("success", true);
            writeRecord(output, mapper, meta, flush);
            for (Map<String, Object> idea : ideas) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("type", "item");
                line.put("item", idea);
                writeRecord(output, mapper, line, flush);
            }
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("type", "done");
            done.put("success", true);
            done.put("returned", ideas.size());
            writeRecord(output, mapper, done, flush);
        } catch (Exception ex) {
            writeRecord(output, mapper, errorLine(ex), flush);
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

    static List<Map<String, Object>> ideas(Map<String, Object> data) {
        Object ideas = data == null ? null : data.get("ideas");
        if (!(ideas instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> idea = new LinkedHashMap<>();
            map.forEach((key, value) -> idea.put(String.valueOf(key), value));
            rows.add(idea);
        }
        return rows;
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
        } else if (ex instanceof ResearchException research) {
            error.put("errorCode", research.getErrorCode());
            error.put("status", research.getStatus());
        } else {
            error.put("status", 500);
        }
        return error;
    }
}
