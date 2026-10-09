package com.gyanwire.projects;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SimplePdf {

    private SimplePdf() {
    }

    public static byte[] fromText(String text) {
        String safe = text == null ? "" : text.replace("\r", "");
        List<String> lines = new ArrayList<>();
        for (String raw : safe.split("\n")) {
            String line = raw.length() > 90 ? raw.substring(0, 90) : raw;
            lines.add(escape(line));
            if (lines.size() == 40) {
                break;
            }
        }
        StringBuilder stream = new StringBuilder("BT /F1 11 Tf 72 760 Td 14 TL\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                stream.append('(').append(lines.get(i)).append(") Tj\n");
            } else {
                stream.append("T* (").append(lines.get(i)).append(") Tj\n");
            }
        }
        stream.append("ET");
        byte[] content = stream.toString().getBytes(StandardCharsets.US_ASCII);
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        int[] offsets = new int[6];
        offsets[1] = pdf.length();
        pdf.append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
        offsets[2] = pdf.length();
        pdf.append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n");
        offsets[3] = pdf.length();
        pdf.append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R ");
        pdf.append("/Resources << /Font << /F1 5 0 R >> >> >> endobj\n");
        offsets[4] = pdf.length();
        pdf.append("4 0 obj << /Length ").append(content.length).append(" >> stream\n");
        pdf.append(new String(content, StandardCharsets.US_ASCII)).append("\nendstream endobj\n");
        offsets[5] = pdf.length();
        pdf.append("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");
        int xref = pdf.length();
        pdf.append("xref\n0 6\n");
        pdf.append("0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String escape(String line) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '(' || c == ')' || c == '\\') {
                out.append('\\');
            }
            if (c >= 32 && c < 127) {
                out.append(c);
            } else if (c == '\t') {
                out.append(' ');
            }
        }
        return out.toString();
    }
}
