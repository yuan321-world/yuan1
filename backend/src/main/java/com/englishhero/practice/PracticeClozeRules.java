package com.englishhero.practice;

import com.englishhero.reading.ArticleContent;
import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
import com.englishhero.web.BusinessException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PracticeClozeRules {
    private static final Pattern MARKER = Pattern.compile("\\{\\{([^{}]*)}}", Pattern.UNICODE_CHARACTER_CLASS);

    private PracticeClozeRules() {}

    public static List<String> normalizeWordBank(List<String> input) {
        if (input == null) return List.of();
        if (input.size() > 15) throw BusinessException.invalid("wordBank", "词库最多15个词");
        var seen = new HashSet<String>();
        var result = new ArrayList<String>(input.size());
        for (int i = 0; i < input.size(); i++) {
            String word = ArticleContent.text(input.get(i), "wordBank[" + i + "]", 100, false);
            if (!word.isBlank() && !seen.add(word.toLowerCase(Locale.ROOT)))
                throw BusinessException.invalid("wordBank", "词库中的词不能重复");
            result.add(word);
        }
        return List.copyOf(result);
    }

    public static List<QuestionInput> normalizeQuestions(List<QuestionInput> input) {
        if (input == null || input.size() != 10)
            throw BusinessException.invalid("questions", "选词填空需要26至35共10个空位");
        var keys = new HashSet<String>();
        var result = new ArrayList<QuestionInput>(10);
        for (int i = 0; i < 10; i++) {
            var q = input.get(i);
            String field = "questions[" + i + "]";
            if (q == null || q.key() == null || !q.key().matches("[A-Za-z0-9_-]{1,64}") || !keys.add(q.key()))
                throw BusinessException.invalid("questions", "题目标识须唯一且有效");
            String expectedStem = Integer.toString(26 + i);
            if (!"CLOZE".equals(q.type()) || !expectedStem.equals(q.stem()))
                throw BusinessException.invalid(field + ".stem", "选词填空题型与题号须按26至35排列");
            if (q.options() == null || !q.options().isEmpty())
                throw BusinessException.invalid(field + ".options", "共享词库不应复制到每个空位");
            String answer = q.correctOption();
            if (answer != null && !answer.isBlank() && !answer.matches("[A-O]"))
                throw BusinessException.invalid(field + ".correctOption", "答案须为A–O或未选择");
            result.add(new QuestionInput(q.key(), "CLOZE", expectedStem, List.of(),
                answer == null || answer.isBlank() ? null : answer,
                ArticleContent.text(q.explanation(), field + ".explanation", 4000, false), i));
        }
        return List.copyOf(result);
    }

    public static String markerIssue(String body) {
        if (body == null) return "原文必须包含26至35各一个填空标记";
        var counts = new int[10];
        Matcher matcher = MARKER.matcher(body);
        int markers = 0;
        while (matcher.find()) {
            markers++;
            String value = matcher.group(1);
            if (!value.matches("2[6-9]|3[0-5]")) return "原文只能包含26至35各一个填空标记";
            counts[Integer.parseInt(value) - 26]++;
        }
        String remainder = matcher.replaceAll("");
        if (remainder.contains("{{") || remainder.contains("}}") || markers != 10)
            return "原文必须包含26至35各一个填空标记，不能有额外标记";
        for (int count : counts) if (count != 1) return "原文必须包含26至35各一个填空标记，不能有额外标记";
        return null;
    }

    public static void validateUniqueChoices(Collection<String> choices) {
        var used = new HashSet<String>();
        for (String choice : choices) {
            if (choice != null && !choice.isBlank() && !used.add(choice))
                throw BusinessException.invalid("answers", "同一个词最多只能填入一个空位");
        }
    }
}
