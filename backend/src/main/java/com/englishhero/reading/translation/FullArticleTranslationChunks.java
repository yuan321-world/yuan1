package com.englishhero.reading.translation;

import java.util.ArrayList;
import java.util.List;

/** Baidu accepts up to 1,000 Unicode code points per request. */
public final class FullArticleTranslationChunks {
    private static final int MAX_CODE_POINTS = 1000;
    private static final int MIN_PREFERRED_BOUNDARY = 720;

    public record Chunk(String text, String separatorBefore) {}

    private FullArticleTranslationChunks() {}

    public static List<Chunk> split(String source) {
        if (source == null || source.isEmpty()) return List.of();
        var result = new ArrayList<Chunk>();
        int start = 0;
        String separatorBefore = "";
        while (source.codePointCount(start, source.length()) > MAX_CODE_POINTS) {
            int max = source.offsetByCodePoints(start, MAX_CODE_POINTS);
            int min = source.offsetByCodePoints(start, MIN_PREFERRED_BOUNDARY);
            int sentenceEnd = preferredSentenceEnd(source, min, max);
            if (sentenceEnd > start) {
                result.add(new Chunk(source.substring(start, sentenceEnd), separatorBefore));
                int next = sentenceEnd;
                while (next < source.length() && Character.isWhitespace(source.codePointAt(next))) {
                    next += Character.charCount(source.codePointAt(next));
                }
                separatorBefore = source.substring(sentenceEnd, next);
                start = next;
                continue;
            }

            int whitespace = preferredWhitespace(source, min, max);
            if (whitespace > start) {
                result.add(new Chunk(source.substring(start, whitespace), separatorBefore));
                int next = whitespace;
                while (next < source.length() && Character.isWhitespace(source.codePointAt(next))) {
                    next += Character.charCount(source.codePointAt(next));
                }
                separatorBefore = source.substring(whitespace, next);
                start = next;
                continue;
            }

            result.add(new Chunk(source.substring(start, max), separatorBefore));
            separatorBefore = "";
            start = max;
        }
        result.add(new Chunk(source.substring(start), separatorBefore));
        return List.copyOf(result);
    }

    private static int preferredSentenceEnd(String source, int min, int max) {
        int result = -1;
        for (int i = min; i < max;) {
            int point = source.codePointAt(i);
            int after = i + Character.charCount(point);
            if (point == '.' || point == '!' || point == '?' || point == ';' || point == ':') result = after;
            i = after;
        }
        return result;
    }

    private static int preferredWhitespace(String source, int min, int max) {
        int result = -1;
        for (int i = min; i < max;) {
            int point = source.codePointAt(i);
            if (Character.isWhitespace(point)) result = i;
            i += Character.charCount(point);
        }
        return result;
    }
}
