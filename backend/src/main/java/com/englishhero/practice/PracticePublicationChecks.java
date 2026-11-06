package com.englishhero.practice;

import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.practice.PracticeDtos.*;

@Component
public class PracticePublicationChecks {
    private final ObjectMapper json;
    private final Clock clock;

    public PracticePublicationChecks(ObjectMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
    }

    public String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String questionHash(QuestionInput q) {
        return hash(new QuestionInput(q.key(), q.type(), q.stem(), q.options(), q.correctOption(), q.explanation(), 0));
    }

    public String questionHash(QuestionInput q, String readingType, List<String> wordBank) {
        String legacy = questionHash(q);
        return "CLOZE".equals(readingType) ? hash(List.of(legacy, wordBank == null ? List.of() : wordBank)) : legacy;
    }

    public String sourceHash(long articleVersion, Metadata metadata, List<QuestionInput> questions) {
        return hash(List.of(articleVersion, metadata, questions.stream().map(this::questionHash).toList()));
    }

    public String sourceHash(long articleVersion, Metadata metadata, List<QuestionInput> questions,
            String readingType, List<String> wordBank) {
        if (!"CLOZE".equals(readingType)) return sourceHash(articleVersion, metadata, questions);
        return hash(List.of(articleVersion, metadata,
            questions.stream().map(q -> questionHash(q, readingType, wordBank)).toList(),
            wordBank == null ? List.of() : wordBank));
    }

    public String sourceHash(long articleVersion, Metadata metadata, List<QuestionInput> questions,String type,List<String> bank,ListeningData listening){
        String base=sourceHash(articleVersion,metadata,questions,type,bank);
        return listening==null?base:hash(List.of(base,Objects.toString(listening.audioAssetId(),""),listening.groups()));
    }

    public List<Check> question(QuestionInput q) {
        var issues = new ArrayList<Check>();
        String path = "questions[" + q.key() + "].";
        if (q.stem().isBlank()) issues.add(new Check(path + "stem", q.key(), "缺题干"));
        if (q.options().stream().anyMatch(String::isBlank)) issues.add(new Check(path + "options", q.key(), "缺选项"));
        if (q.options().stream().map(o -> o.strip().toLowerCase(Locale.ROOT)).distinct().count() != 4)
            issues.add(new Check(path + "options", q.key(), "重复选项"));
        if (q.correctOption() == null) issues.add(new Check(path + "correctOption", q.key(), "未设置答案"));
        if (q.explanation().isBlank()) issues.add(new Check(path + "explanation", q.key(), "缺解析"));
        return issues;
    }

    public List<Check> question(QuestionInput q, String readingType, List<String> wordBank) {
        if ("CLOZE".equals(readingType)) return clozeQuestion(q);
        if ("MATCH".equals(readingType)) return matchQuestion(q);
        return question(q);
    }

    private List<Check> clozeQuestion(QuestionInput q) {
        var issues = new ArrayList<Check>();
        String path = "questions[" + q.key() + "].";
        if (!q.type().equals("CLOZE") || !q.stem().matches("2[6-9]|3[0-5]"))
            issues.add(new Check(path + "stem", q.key(), "选词填空题号无效"));
        if (!q.options().isEmpty()) issues.add(new Check(path + "options", q.key(), "共享词库不应复制到每个空位"));
        if (q.correctOption() == null || !q.correctOption().matches("[A-O]"))
            issues.add(new Check(path + "correctOption", q.key(), "答案须为A–O"));
        if (q.explanation().isBlank()) issues.add(new Check(path + "explanation", q.key(), "缺解析"));
        return issues;
    }

    private List<Check> matchQuestion(QuestionInput q) {
        var issues = new ArrayList<Check>();
        String path = "questions[" + q.key() + "].";
        if (!"MATCH".equals(q.type())) issues.add(new Check(path + "type", q.key(), "题型须为长篇匹配"));
        if (q.stem().isBlank()) issues.add(new Check(path + "stem", q.key(), "缺完整陈述"));
        if (!q.options().isEmpty()) issues.add(new Check(path + "options", q.key(), "长篇匹配不使用选项"));
        if (q.correctOption() == null || !q.correctOption().matches("[A-Z]"))
            issues.add(new Check(path + "correctOption", q.key(), "答案须为A–Z"));
        if (q.explanation().isBlank()) issues.add(new Check(path + "explanation", q.key(), "缺解析"));
        return issues;
    }

    public List<Check> metadata(Metadata metadata) {
        var issues = new ArrayList<Check>();
        if (metadata.title().isBlank()) issues.add(new Check("metadata.title", null, "填写英文标题"));
        if (metadata.titleZh().isBlank()) issues.add(new Check("metadata.titleZh", null, "填写中文标题"));
        if (metadata.usageNote().isBlank()) issues.add(new Check("metadata.usageNote", null, "填写来源与使用说明"));
        if (metadata.sourceKind().equals("PAST")) {
            if (metadata.sourceName().isBlank()) issues.add(new Check("metadata.sourceName", null, "真题必须填写来源名称"));
            if (metadata.year() == null || metadata.year() < 2000 || metadata.year() > Year.now(clock).getValue())
                issues.add(new Check("metadata.year", null, "填写2000至当前年之间的真实考试年份"));
            if (metadata.month() == null || metadata.month() < 1 || metadata.month() > 12)
                issues.add(new Check("metadata.month", null, "填写1–12之间的真实考试月份"));
            if (metadata.paperNo() == null || metadata.paperNo() < 1 || metadata.paperNo() > 9)
                issues.add(new Check("metadata.paperNo", null, "填写1–9之间的真实套次"));
        }
        return issues;
    }

    public List<Check> all(long articleVersion, DraftState state, List<QuestionInput> questions) {
        var issues = new ArrayList<>(metadata(state.metadata()));
        for (var question : questions) {
            issues.addAll(question(question));
            if (!Objects.equals(state.reviews().get(question.key()), questionHash(question)))
                issues.add(new Check("questions[" + question.key() + "].review", question.key(), "未校对"));
        }
        if (!Objects.equals(state.sourceReview(), sourceHash(articleVersion, state.metadata(), questions)))
            issues.add(new Check("sourceReview", null, "尚未确认文章与来源校对"));
        return issues;
    }

    public List<Check> source(long articleVersion, DraftState state, List<QuestionInput> questions,
            String readingType, String body) {
        var issues = new ArrayList<>(metadata(state.metadata()));
        if ("CLOZE".equals(readingType)) {
            String bankIssue = wordBankIssue(state.wordBank());
            if (bankIssue != null) issues.add(new Check("wordBank", null, bankIssue));
            String markerIssue = PracticeClozeRules.markerIssue(body);
            if (markerIssue != null) issues.add(new Check("body", null, markerIssue));
        } else if ("MATCH".equals(readingType)) {
            String markerIssue = PracticeMatchRules.paragraphIssue(body);
            if (markerIssue != null) issues.add(new Check("body", null, markerIssue));
            else {
                var answers = questions.stream().map(QuestionInput::correctOption).toList();
                String referenceIssue = PracticeMatchRules.referenceIssue(answers, PracticeMatchRules.paragraphLetters(body));
                if (referenceIssue != null) {
                    for (var question : questions) {
                        if (question.correctOption() != null && !PracticeMatchRules.paragraphLetters(body).contains(question.correctOption()))
                            issues.add(new Check("questions[" + question.key() + "].correctOption", question.key(), referenceIssue));
                    }
                }
            }
        }
        return issues;
    }

    public List<Check> all(long articleVersion, DraftState state, List<QuestionInput> questions,
            String readingType, String body) {
        if ("LISTENING".equals(readingType)) {
            var issues=new ArrayList<>(metadata(state.metadata()));
            if(questions.isEmpty())issues.add(new Check("questions",null,"整套听力至少需要一道题"));
            if(state.listening()==null||state.listening().groups().isEmpty())issues.add(new Check("listening.groups",null,"请设置套内题目分组"));
            if(body==null||body.isBlank())issues.add(new Check("body",null,"请填写配套原文"));
            for(var q:questions){issues.addAll(question(q));if(!Objects.equals(state.reviews().get(q.key()),questionHash(q)))issues.add(new Check("questions["+q.key()+"].review",q.key(),"未校对"));}
            if(!Objects.equals(state.sourceReview(),sourceHash(articleVersion,state.metadata(),questions,readingType,state.wordBank(),state.listening())))issues.add(new Check("sourceReview",null,"尚未确认原文与来源校对"));
            return issues;
        }
        if ("CLOZE".equals(readingType)) {
            var issues = new ArrayList<>(source(articleVersion, state, questions, readingType, body));
            var usedAnswers = new HashSet<String>();
            for (var question : questions) {
                issues.addAll(question(question, readingType, state.wordBank()));
                if (question.correctOption() != null && !usedAnswers.add(question.correctOption()))
                    issues.add(new Check("questions[" + question.key() + "].correctOption", question.key(), "10个空位答案不能重复"));
                if (!Objects.equals(state.reviews().get(question.key()), questionHash(question, readingType, state.wordBank())))
                    issues.add(new Check("questions[" + question.key() + "].review", question.key(), "未校对"));
            }
            if (!Objects.equals(state.sourceReview(), sourceHash(articleVersion, state.metadata(), questions, readingType, state.wordBank())))
                issues.add(new Check("sourceReview", null, "尚未确认文章与来源校对"));
            return issues;
        }
        if ("MATCH".equals(readingType)) {
            var issues = new ArrayList<>(source(articleVersion, state, questions, readingType, body));
            for (var question : questions) {
                issues.addAll(question(question, readingType, state.wordBank()));
                if (!Objects.equals(state.reviews().get(question.key()), questionHash(question, readingType, state.wordBank())))
                    issues.add(new Check("questions[" + question.key() + "].review", question.key(), "未校对"));
            }
            if (!Objects.equals(state.sourceReview(), sourceHash(articleVersion, state.metadata(), questions, readingType, state.wordBank())))
                issues.add(new Check("sourceReview", null, "尚未确认文章与来源校对"));
            return issues;
        }
        return all(articleVersion, state, questions);
    }

    public String wordBankIssue(List<String> words) {
        if (words == null || words.size() != 15) return "共享词库必须包含15个词";
        var seen = new HashSet<String>();
        for (String word : words) {
            if (word == null || word.isBlank()) return "词库不能包含空词";
            if (!seen.add(word.strip().toLowerCase(Locale.ROOT))) return "词库中的词不能重复";
        }
        return null;
    }
}
