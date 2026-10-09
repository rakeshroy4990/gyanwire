package com.gyanwire.research.engine;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;

public final class PageText {

    private PageText() {
    }

    public static String visibleText(Document doc) {
        if (doc == null) {
            return "";
        }
        doc.select("script, style, noscript, template").remove();
        doc.filter(new NodeFilter() {
            @Override
            public FilterResult head(Node node, int depth) {
                if (node instanceof org.jsoup.nodes.Comment) {
                    return FilterResult.REMOVE;
                }
                if (node instanceof Element element && hidden(element)) {
                    return FilterResult.REMOVE;
                }
                if (node instanceof TextNode textNode) {
                    textNode.text(stripZeroWidth(textNode.text()));
                }
                return FilterResult.CONTINUE;
            }
        });
        String text = doc.body() == null ? doc.text() : doc.body().text();
        return stripZeroWidth(text).replaceAll("\\s+", " ").trim();
    }

    public static String forModel(String text) {
        String body = text == null ? "" : text;
        return "<page_content>\n" + body + "\n</page_content>";
    }

    private static boolean hidden(Element element) {
        if (element.hasAttr("hidden")) {
            return true;
        }
        if ("true".equalsIgnoreCase(element.attr("aria-hidden"))) {
            return true;
        }
        String style = element.attr("style").toLowerCase().replace(" ", "");
        return style.contains("display:none") || style.contains("visibility:hidden") || style.contains("fontsize:0");
    }

    static String stripZeroWidth(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (cp != 0x200B && cp != 0x200C && cp != 0x200D && cp != 0xFEFF && cp != 0x2060) {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }
}
