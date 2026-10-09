package com.gyanwire.research.engine;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

/**
 * Parse and format article publish dates for findings across all industries.
 */
public final class PublishedDates {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private PublishedDates() {}

    public static Instant parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {}
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {}
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {}
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH)).toInstant();
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    public static Instant fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        String[] selectors = {
                "meta[property=article:published_time]",
                "meta[name=article:published_time]",
                "meta[property=og:article:published_time]",
                "meta[name=pubdate]",
                "meta[name=publish-date]",
                "meta[name=date]",
                "meta[itemprop=datePublished]",
                "time[datetime]"
        };
        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el == null) {
                continue;
            }
            String raw = el.hasAttr("content") ? el.attr("content") : el.attr("datetime");
            if (raw == null || raw.isBlank()) {
                raw = el.text();
            }
            Instant parsed = parse(raw);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    public static void apply(Map<String, Object> row, Instant published) {
        if (row == null || published == null) {
            return;
        }
        row.put("publishedAt", published.toString());
        row.put("publishedLabel", DISPLAY.format(published.atZone(ZoneOffset.UTC)));
        long age = ChronoUnit.DAYS.between(published, Instant.now());
        row.put("ageDays", Math.max(0, (int) age));
    }

    public static String label(Object publishedAt) {
        if (publishedAt == null) {
            return "";
        }
        Instant instant = parse(String.valueOf(publishedAt));
        if (instant == null) {
            return "";
        }
        return DISPLAY.format(instant.atZone(ZoneOffset.UTC));
    }
}
