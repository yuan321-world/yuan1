package com.englishhero.reading;

import java.time.Instant;
import java.util.*;

/** Read-only learning relevance ranking; the full source intersection remains available separately. */
public final class ReadingKeywordRanker {
    public record Learning(boolean proficient, int stage, boolean active, Instant dueAt) {}
    private static final Set<String> FUNCTION_WORDS = Set.of((
        "a an the this that these those i me my mine myself we us our ours ourselves " +
        "you your yours yourself yourselves he him his himself she her hers herself it its itself " +
        "they them their theirs themselves who whom whose which what whoever whichever whatever " +
        "am is are was were be been being have has had having do does did doing " +
        "can could may might must shall should will would ought " +
        "and or but nor so yet if then than as because although though while whether " +
        "of to in on at by for from with without into onto upon over under between among through " +
        "about against during before after since until within throughout " +
        "not no yes all any some each every either neither both such there here where when why how"
    ).split(" "));
    private ReadingKeywordRanker() {}

    public static List<ReadingToolsService.Keyword> select(List<ReadingToolsService.Keyword> candidates,
            Map<String, Learning> learning, ArticleContent.Document document, String title, Instant now) {
        var frequency = new HashMap<String, Integer>();
        for (var paragraph : document.paragraphs()) for (var sentence : paragraph.sentences()) {
            var matcher = ArticleContent.WORD.matcher(sentence.text());
            while (matcher.find()) frequency.merge(normalize(matcher.group()), 1, Integer::sum);
        }
        var titleWords = new HashSet<String>();
        var matcher = ArticleContent.WORD.matcher(Objects.toString(title, ""));
        while (matcher.find()) titleWords.add(normalize(matcher.group()));
        int wordCount = ArticleContent.wordCount(document);
        int limit = wordCount <= 500 ? 10 : wordCount <= 1000 ? 15 : 20;
        // Stable sorting preserves article order for equal relevance.
        return candidates.stream()
            .filter(word -> !FUNCTION_WORDS.contains(normalize(word.lemma())))
            .filter(word -> !learning.containsKey(word.entryId()) || !learning.get(word.entryId()).proficient())
            .sorted(Comparator.<ReadingToolsService.Keyword>comparingInt(word -> priority(learning.get(word.entryId()), now)).reversed()
                .thenComparing(Comparator.comparingInt((ReadingToolsService.Keyword word) -> relevance(word, frequency, titleWords)).reversed()))
            .limit(limit).toList();
    }
    private static String normalize(String text) { return Objects.toString(text, "").strip().toLowerCase(Locale.ROOT).replace('’', '\'').replace('‐', '-').replace('‑', '-'); }
    private static int priority(Learning word, Instant now) {
        if (word == null || word.stage() == 0) return 2;
        return word.active() && word.dueAt() != null && !word.dueAt().isAfter(now) ? 2 : 1;
    }
    private static int relevance(ReadingToolsService.Keyword word, Map<String, Integer> frequency, Set<String> titleWords) {
        var forms = new HashSet<String>();
        forms.add(normalize(word.lemma()));
        word.forms().forEach(form -> forms.add(normalize(form)));
        int occurrences = forms.stream().mapToInt(form -> frequency.getOrDefault(form, 0)).sum();
        return (forms.stream().anyMatch(titleWords::contains) ? 4 : 0) + Math.min(5, Math.max(0, occurrences - 1));
    }
}
