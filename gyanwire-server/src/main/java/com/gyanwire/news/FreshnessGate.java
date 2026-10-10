package com.gyanwire.news;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class FreshnessGate {

    public record Result(List<NewsRow> items, String freshness) {
    }

    private FreshnessGate() {
    }

    public static boolean shouldSkip(Instant newestFirstSeen, Instant now, Duration interval) {
        if (newestFirstSeen == null || now == null || interval == null || interval.isZero() || interval.isNegative()) {
            return false;
        }
        return !newestFirstSeen.isBefore(now.minus(interval));
    }

    public static Result select(List<NewsRow> rows, int limit, int[] windows, Instant now) {
        int[] steps = windows == null || windows.length == 0 ? new int[]{14, 30, 60} : windows;
        List<NewsRow> dated = new ArrayList<>();
        if (rows != null) {
            for (NewsRow row : rows) {
                if (row == null || row.dateEstimated() || row.publishedAt() == null) {
                    continue;
                }
                dated.add(row);
            }
        }
        List<NewsRow> chosen = List.of();
        int used = 0;
        for (int i = 0; i < steps.length; i++) {
            chosen = within(dated, steps[i], now);
            used = i;
            if (chosen.size() >= limit || i == steps.length - 1) {
                break;
            }
        }
        return new Result(chosen, label(used, steps));
    }

    private static List<NewsRow> within(List<NewsRow> rows, int days, Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(Math.max(days, 1)));
        List<NewsRow> out = new ArrayList<>();
        for (NewsRow row : rows) {
            if (!row.publishedAt().isBefore(cutoff)) {
                out.add(row);
            }
        }
        return out;
    }

    static String label(int step, int[] windows) {
        if (step <= 0) {
            return "fresh";
        }
        int days = windows[Math.min(step, windows.length - 1)];
        if (days <= 30) {
            return "last 30 days";
        }
        return "older — few recent items";
    }
}
