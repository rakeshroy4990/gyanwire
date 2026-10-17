package com.gyanwire.actionplan.research;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cached multi-source summary for scripting a video from a news story.
 */
public record VideoResearchPack(
        String urlHash,
        String status,
        Map<String, Object> summary,
        String error,
        Instant updatedAt
) {
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";

    public boolean ready() {
        return STATUS_READY.equals(status) && summary != null && !summary.isEmpty();
    }

    public boolean running() {
        return STATUS_RUNNING.equals(status);
    }

    public boolean failed() {
        return STATUS_FAILED.equals(status);
    }

    public Map<String, Object> toApiMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status == null ? STATUS_FAILED : status);
        if (summary != null) {
            out.put("summary", summary);
        }
        if (error != null && !error.isBlank()) {
            out.put("error", error);
        }
        return out;
    }
}
