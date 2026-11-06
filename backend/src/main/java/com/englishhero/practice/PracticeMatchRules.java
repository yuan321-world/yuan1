package com.englishhero.practice;

import com.englishhero.reading.ArticleContent;
import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
import com.englishhero.web.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validation rules for paragraph-labelled CET4 matching passages. */
public final class PracticeMatchRules {
    private static final Pattern PARAGRAPH = Pattern.compile("^\\[([A-Z])\\][\\t ]+(.+)$", Pattern.DOTALL);
    private static final Pattern EXTRA_MARKER = Pattern.compile("(?m)^\\[[A-Z]\\][\\t ]+");

    private PracticeMatchRules() {}

    public static List<QuestionInput> normalizeQuestions(List<QuestionInput> input) {
        if (input == null || input.size() != 10)
            throw BusinessException.invalid("questions", "长篇匹配需要36至45共10条陈述");
        var keys = new HashSet<String>();
        var result = new ArrayList<QuestionInput>(10);
        for (int i = 0; i < input.size(); i++) {
            var q = input.get(i);
            String field = "questions[" + i + "]";
            if (q == null || q.key() == null || !q.key().matches("[A-Za-z0-9_-]{1,64}") || !keys.add(q.key()))
                throw BusinessException.invalid("questions", "题目标识须唯一且有效");
            if (!"MATCH".equals(q.type()))
                throw BusinessException.invalid(field + ".type", "长篇匹配题型须为MATCH");
            if (q.options() == null || !q.options().isEmpty())
                throw BusinessException.invalid(field + ".options", "长篇匹配不使用选项");
            String answer = q.correctOption();
            if (answer != null && !answer.isBlank() && !answer.matches("[A-Z]"))
                throw BusinessException.invalid(field + ".correctOption", "答案须为A–Z或未选择");
            result.add(new QuestionInput(q.key(), "MATCH", ArticleContent.text(q.stem(), field + ".stem", 2000, false),
                List.of(), answer == null || answer.isBlank() ? null : answer,
                ArticleContent.text(q.explanation(), field + ".explanation", 4000, false), i));
        }
        return List.copyOf(result);
    }

    /** Returns a displayable issue, or null when the body consists of A–Z paragraphs in order. */
    public static String paragraphIssue(String body) {
        if (body == null || body.isBlank()) return "原文须包含从[A]开始连续编号的1–26个段落";
        String normalized = ArticleContent.text(body, "body", 20000, true);
        String[] paragraphs = normalized.split("\\n[\\t ]*\\n+", -1);
        if (paragraphs.length < 1 || paragraphs.length > 26) return "原文段落数量须为1–26";
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].strip();
            Matcher marker = PARAGRAPH.matcher(paragraph);
            String expected = Character.toString('A' + i);
            if (!marker.matches() || !expected.equals(marker.group(1)) || marker.group(2).isBlank())
                return "原文段落须从[A]开始连续、唯一且内容非空";
            String remainder = marker.group(2);
            if (EXTRA_MARKER.matcher(remainder).find())
                return "每个原文段落只能包含一个段落字母标记；段落之间须留空行";
        }
        return null;
    }

    public static List<String> paragraphLetters(String body) {
        if (paragraphIssue(body) != null) return List.of();
        String[] paragraphs = ArticleContent.text(body, "body", 20000, true).split("\\n[\\t ]*\\n+", -1);
        return java.util.stream.IntStream.range(0, paragraphs.length)
            .mapToObj(i -> Character.toString('A' + i)).toList();
    }

    /** Repeated letters are legal; each non-empty answer still has to be a single A–Z letter. */
    public static void validateRepeatedAnswers(Collection<String> choices) {
        for (String choice : choices) {
            if (choice != null && !choice.isBlank() && !choice.matches("[A-Z]"))
                throw BusinessException.invalid("answers", "长篇匹配答案须为A–Z或未选择");
        }
    }

    public static String referenceIssue(Collection<String> answers, Collection<String> paragraphLetters) {
        Set<String> available = Set.copyOf(paragraphLetters);
        for (String answer : answers) {
            if (answer != null && !answer.isBlank() && !available.contains(answer))
                return "答案必须引用当前原文中实际存在的段落";
        }
        return null;
    }
}
