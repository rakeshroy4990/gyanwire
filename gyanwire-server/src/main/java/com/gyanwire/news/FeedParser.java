package com.gyanwire.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;

public final class FeedParser {

    public record Item(String title, String link, String publishedRaw, String snippet) {
    }

    private FeedParser() {
    }

    public static List<Item> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        List<Item> items = new ArrayList<>();
        for (Element item : doc.select("item")) {
            items.add(new Item(text(item, "title"), linkOf(item), firstText(item, "pubDate", "date"), snippet(item)));
        }
        if (!items.isEmpty()) {
            return items;
        }
        for (Element entry : doc.select("entry")) {
            items.add(new Item(text(entry, "title"), atomLink(entry), firstText(entry, "published", "updated"), snippet(entry)));
        }
        return items;
    }

    private static String text(Element parent, String tag) {
        Element el = parent.selectFirst(tag);
        return el == null ? "" : el.text();
    }

    private static String firstText(Element parent, String... tags) {
        for (String tag : tags) {
            Element el = parent.selectFirst(tag);
            if (el != null && !el.text().isBlank()) {
                return el.text();
            }
        }
        return "";
    }

    private static String linkOf(Element item) {
        Element link = item.selectFirst("link");
        if (link == null) {
            return "";
        }
        String href = link.attr("href");
        if (!href.isBlank()) {
            return href.trim();
        }
        return link.text().trim();
    }

    private static String atomLink(Element entry) {
        Element alternate = entry.selectFirst("link[rel=alternate]");
        Element link = alternate != null ? alternate : entry.selectFirst("link");
        if (link == null) {
            return "";
        }
        String href = link.attr("href");
        return href.isBlank() ? link.text().trim() : href.trim();
    }

    private static String snippet(Element item) {
        String raw = firstText(item, "description", "summary", "content");
        if (raw.isBlank()) {
            return "";
        }
        String plain = Jsoup.parse(raw).text().replaceAll("\\s+", " ").trim();
        return plain.length() > 400 ? plain.substring(0, 400) : plain;
    }
}
