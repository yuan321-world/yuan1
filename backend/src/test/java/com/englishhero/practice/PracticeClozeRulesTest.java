package com.englishhero.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishhero.practice.PracticeDtos.DraftInput;
import com.englishhero.practice.PracticeDtos.DraftState;
import com.englishhero.practice.PracticeDtos.Metadata;
import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeClozeRulesTest {
    @Test
    void acceptsTheTenNumberedClozeHolesAndTheFullAnswerRange() {
        var questions = java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> new QuestionInput("hole-" + (26 + i), "CLOZE", "" + (26 + i), List.of(),
                "ABCDEFGHIJKLMNO".substring(i, i + 1), "Explanation " + i, i))
            .toList();
        var draft = new DraftInput("1", "2", "CET4", "CLOZE", metadata(), questions, 0);

        assertThatCode(() -> PracticeDraftRules.normalize(draft)).doesNotThrowAnyException();
        var normalized = PracticeDraftRules.normalize(draft);
        assertThat(normalized.questions()).hasSize(10);
        assertThat(normalized.questions()).extracting(QuestionInput::stem)
            .containsExactly("26", "27", "28", "29", "30", "31", "32", "33", "34", "35");
        assertThat(normalized.questions().getLast().correctOption()).isEqualTo("J");
    }

    @Test
    void permitsIncompleteDraftAnswersButRejectsInvalidClozeStructure() {
        var questions = clozeQuestions();
        questions.set(8, new QuestionInput("hole-34", "CLOZE", "34", List.of(), null, "", 8));
        var normalized = PracticeDraftRules.normalize(new DraftInput("1", "2", "CET4", "CLOZE", metadata(), questions, 0));
        assertThat(normalized.questions().get(8).correctOption()).isNull();
        assertThat(normalized.questions().get(8).explanation()).isEmpty();

        assertThatThrownBy(() -> PracticeDraftRules.normalize(new DraftInput("1", "2", "CET4", "CLOZE", metadata(), questions.subList(0, 9), 0)))
            .isInstanceOf(com.englishhero.web.BusinessException.class);
        var badStem = new ArrayList<>(clozeQuestions());
        badStem.set(4, new QuestionInput("hole-30", "CLOZE", "31", List.of(), "E", "Explanation", 4));
        assertThatThrownBy(() -> PracticeDraftRules.normalize(new DraftInput("1", "2", "CET4", "CLOZE", metadata(), badStem, 0)))
            .isInstanceOf(com.englishhero.web.BusinessException.class);
    }

    @Test
    void enforcesSharedBankUniquenessAndExactPassageMarkers() {
        assertThat(PracticeClozeRules.normalizeWordBank(List.of("  adapt ", "brief")))
            .containsExactly("adapt", "brief");
        assertThatThrownBy(() -> PracticeClozeRules.normalizeWordBank(List.of("Learn", "learn")))
            .isInstanceOf(com.englishhero.web.BusinessException.class);

        String body = markers();
        assertThat(PracticeClozeRules.markerIssue(body)).isNull();
        assertThat(PracticeClozeRules.markerIssue(body.replace("{{35}}", ""))).isNotNull();
        assertThat(PracticeClozeRules.markerIssue(body + " {{36}}")).isNotNull();
        assertThat(PracticeClozeRules.markerIssue(body.replace("{{35}}", "{{34}}"))).isNotNull();
    }

    @Test
    void bankAndBodyChangesInvalidateClozeReviewHashesWithoutChangingDetailedHashes() {
        var checks = new PracticePublicationChecks(JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC));
        var questions = clozeQuestions();
        var q = questions.getFirst();
        var bankA = java.util.stream.IntStream.range(0, 15).mapToObj(i -> "word-" + i).toList();
        var bankB = new ArrayList<>(bankA);
        bankB.set(0, "changed");

        assertThat(checks.questionHash(q, "CLOZE", bankA)).isNotEqualTo(checks.questionHash(q, "CLOZE", bankB));
        assertThat(checks.sourceHash(2, metadata(), questions, "CLOZE", bankA))
            .isNotEqualTo(checks.sourceHash(2, metadata(), questions, "CLOZE", bankB));
        assertThat(checks.questionHash(q, "DETAILED", bankA)).isEqualTo(checks.questionHash(q));
        assertThat(checks.sourceHash(2, metadata(), questions, "DETAILED", bankA))
            .isEqualTo(checks.sourceHash(2, metadata(), questions));
    }

    @Test
    void rejectsReusingOneChoiceInTheMergedAnswerState() {
        assertThatThrownBy(() -> PracticeClozeRules.validateUniqueChoices(List.of("A", "B", "A")))
            .isInstanceOf(com.englishhero.web.BusinessException.class);
        assertThatCode(() -> PracticeClozeRules.validateUniqueChoices(java.util.Arrays.asList("A", null, "B")))
            .doesNotThrowAnyException();
    }

    private Metadata metadata() {
        return new Metadata("A passage", "一篇文章", "PAST", "education", 2024, 6, 1,
            "Test source", "https://example.test", "Local study only");
    }

    private ArrayList<QuestionInput> clozeQuestions() {
        return new ArrayList<>(java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> new QuestionInput("hole-" + (26 + i), "CLOZE", "" + (26 + i), List.of(),
                "ABCDEFGHIJKLMNO".substring(i, i + 1), "Explanation " + i, i))
            .toList());
    }

    private String markers() {
        return java.util.stream.IntStream.rangeClosed(26, 35).mapToObj(i -> "{{" + i + "}}")
            .reduce((a, b) -> a + " text " + b).orElseThrow();
    }
}
