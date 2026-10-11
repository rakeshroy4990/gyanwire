package com.gyanwire.news;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One JSON object per line, flushed before the next line is written.
 * The open line goes out before the feed is loaded so the client is not
 * held until the whole payload exists.
 */
public final class NewsNdjson {

    private NewsNdjson() {
    }

    public static void writeDefault(OutputStream output, ObjectMapper mapper, Supplier<Map<String, Object>> load)
            throws IOException {
        writeDefault(output, mapper, load, null);
    }

    public static void writeDefault(
            OutputStream output,
            ObjectMapper mapper,
            Supplier<Map<String, Object>> load,
            Runnable flush
    ) throws IOException {
        try {
            writeRecord(output, mapper, openLine(), flush);
            Map<String, Object> data = new LinkedHashMap<>(load.get());
            data.put("intent", "products");
            data.put("engine", "gyanwire");
            data.put("usedLlm", false);
            writeFeed(output, mapper, data, flush);
        } catch (Exception ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("type", "error");
            error.put("success", false);
            error.put("message", ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Could not load product news."
                    : ex.getMessage());
            writeLine(output, mapper, error);
            if (flush != null) {
                flush.run();
            }
        }
    }

    public static void writeFeed(OutputStream output, ObjectMapper mapper, Map<String, Object> data) throws IOException {
        writeFeed(output, mapper, data, null);
    }

    public static void writeFeed(
            OutputStream output,
            ObjectMapper mapper,
            Map<String, Object> data,
            Runnable flush
    ) throws IOException {
        List<Map<String, Object>> items = items(data);
        Map<String, Object> meta = new LinkedHashMap<>(data);
        meta.remove("results");
        meta.remove("items");
        meta.put("type", "meta");
        meta.put("success", true);
        writeRecord(output, mapper, meta, flush);
        for (Map<String, Object> item : items) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("type", "item");
            line.put("item", item);
            writeRecord(output, mapper, line, flush);
        }
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("type", "done");
        done.put("success", true);
        done.put("returned", items.size());
        writeRecord(output, mapper, done, flush);
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

    public static void writeLine(OutputStream output, ObjectMapper mapper, Map<String, Object> line) throws IOException {
        output.write(mapper.writeValueAsBytes(line));
        output.write('\n');
        output.flush();
    }

    public static Map<String, Object> openLine() {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "open");
        line.put("intent", "products");
        line.put("engine", "gyanwire");
        line.put("usedLlm", false);
        return line;
    }

    public static List<Map<String, Object>> items(Map<String, Object> data) {
        Object results = data == null ? null : data.get("results");
        if (!(results instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            map.forEach((key, value) -> item.put(String.valueOf(key), value));
            items.add(item);
        }
        return items;
    }
}
