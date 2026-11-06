package com.englishhero.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishhero.practice.PracticeDtos.DraftInput;
import com.englishhero.practice.PracticeDtos.DraftState;
import com.englishhero.practice.PracticeDtos.Metadata;
import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.json.JsonMapper;

class PracticeMatchRulesTest {
    @Test
    void acceptsTenFullStatementsAndRepeatedParagraphAnswers() {
        String statement = "A complete statement that retains its full wording. ".repeat(5).strip();
        var questions = java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> new QuestionInput("match-" + (36 + i), "MATCH", statement, List.of(),
                i < 3 ? "A" : "Z", "Explanation " + i, i))
            .toList();
        var draft = new DraftInput("1", "2", "CET4", "MATCH", metadata(), questions, 0, List.of());

        var normalized = PracticeDraftRules.normalize(draft);

        assertThat(normalized.readingType()).isEqualTo("MATCH");
        assertThat(normalized.questions()).hasSize(10);
        assertThat(normalized.questions()).allSatisfy(q -> {
            assertThat(q.type()).isEqualTo("MATCH");
            assertThat(q.stem()).isEqualTo(statement);
            assertThat(q.options()).isEmpty();
        });
        assertThat(normalized.questions()).extracting(QuestionInput::correctOption)
            .containsExactly("A", "A", "A", "Z", "Z", "Z", "Z", "Z", "Z", "Z");
    }

    @Test
    void acceptsOnlyAThroughZForNumberedMatchStatements() {
        var invalidQuestions = matchQuestions();
        invalidQuestions.set(0, new QuestionInput("match-36", "MATCH", "A statement", List.of(), "AA", "Explanation", 0));
        assertThatThrownBy(() -> PracticeDraftRules.normalize(
            new DraftInput("1", "2", "CET4", "MATCH", metadata(), invalidQuestions, 0)))
            .isInstanceOf(com.englishhero.web.BusinessException.class);

        var draftQuestions = matchQuestions();
        draftQuestions.set(9, new QuestionInput("match-45", "MATCH", "A statement", List.of(), null, "", 9));
        assertThat(PracticeDraftRules.normalize(
            new DraftInput("1", "2", "CET4", "MATCH", metadata(), draftQuestions, 0)).questions().getLast().correctOption())
            .isNull();
    }

    @Test
    void validatesStoredParagraphLabelsAndAllowsRepeatedParagraphReferences() {
        String body = "[A] One paragraph.\n\n[B] Another paragraph.\n\n[C] Final paragraph.";
        assertThat(PracticeMatchRules.paragraphIssue(body)).isNull();
        assertThat(PracticeMatchRules.paragraphLetters(body)).containsExactly("A", "B", "C");
        assertThat(PracticeMatchRules.paragraphIssue("[A] First.\n\n[C] Skipped."))
            .isNotNull();
        assertThat(PracticeMatchRules.paragraphIssue("[A] First.\n\n[A] Duplicate."))
            .isNotNull();
        assertThat(PracticeMatchRules.paragraphIssue("[A] First.\n\n[B] "))
            .isNotNull();
        assertThat(PracticeMatchRules.paragraphIssue("[A] First.\n[B] Missing blank separator."))
            .isNotNull();
        assertThat(PracticeMatchRules.referenceIssue(List.of("A", "D"), List.of("A", "B", "C")))
            .isNotNull();
        assertThat(PracticeMatchRules.referenceIssue(List.of("A", "A", "C"), List.of("A", "B", "C")))
            .isNull();
    }

    @Test
    void checksMatchStatementsWithoutDetailedOptionsAndRejectsMissingParagraphReferences() {
        var checks = new PracticePublicationChecks(JsonMapper.builder().build(),
            Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC));
        var question = new QuestionInput("match-36", "MATCH", "A full statement.", List.of(), "A", "Why A fits", 0);
        assertThat(checks.question(question, "MATCH", List.of())).isEmpty();
        assertThat(checks.question(new QuestionInput("match-36", "MATCH", "", List.of(), null, "", 0), "MATCH", List.of()))
            .extracting(PracticeDtos.Check::path)
            .contains("questions[match-36].stem", "questions[match-36].correctOption", "questions[match-36].explanation");
        var state = new DraftState(metadata(), java.util.Map.of(), null, "MATCH", List.of());
        assertThat(checks.source(1, state, List.of(question), "MATCH", "[A] A passage."))
            .isEmpty();
        assertThat(checks.source(1, state, List.of(new QuestionInput("match-36", "MATCH", "A statement", List.of(), "B", "Why", 0)),
            "MATCH", "[A] A passage."))
            .extracting(PracticeDtos.Check::path)
            .contains("questions[match-36].correctOption");
        assertThat(checks.source(1, state, List.of(question), "MATCH", "[A] First.\n[B] Missing separator."))
            .extracting(PracticeDtos.Check::path)
            .contains("body");
    }

    @Test
    void preservesOlderConstructorsAndDetectsOmittedChoiceAndFlagFields() throws Exception {
        var choice = new PracticeDtos.Choice("91", "A");
        assertThat(choice.choiceProvided()).isTrue();
        assertThat(choice.flagged()).isNull();
        assertThat(new PracticeDtos.Choice("91", "A", true).flagged()).isTrue();
        assertThat(new PracticeDtos.Answer("91", "A", null, null, null, null).flagged()).isNull();
        assertThat(new PracticeAttemptMapper.Saved(91, "A", null, null).flagged()).isFalse();
        assertThat(new PracticeDtos.Mistake("91", "2", "3", "4", "5", "PENDING", 1,
            "Statement", "A", "Explanation", "Passage").readingType()).isNull();
        var mapper = JsonMapper.builder().build();
        var choiceOnly = mapper.readValue("{\"questionId\":\"91\",\"choice\":\"A\"}", PracticeDtos.Choice.class);
        assertThat(choiceOnly.choiceProvided()).isTrue();
        assertThat(choiceOnly.flagged()).isNull();
        var flagOnly = mapper.readValue("{\"questionId\":\"91\",\"flagged\":true}", PracticeDtos.Choice.class);
        assertThat(flagOnly.choiceProvided()).isFalse();
        assertThat(flagOnly.flagged()).isTrue();
        var clear = mapper.readValue("{\"questionId\":\"91\",\"choice\":null}", PracticeDtos.Choice.class);
        assertThat(clear.choiceProvided()).isTrue();
    }

    private static Metadata metadata() {
        return new Metadata("Local match passage", "本地匹配练习", "ORIGINAL", "education", null, null,
            null, "", "", "Original fixture for local practice");
    }

    private static java.util.ArrayList<QuestionInput> matchQuestions() {
        return new java.util.ArrayList<>(java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> new QuestionInput("match-" + (36 + i), "MATCH", "Statement " + (36 + i), List.of(),
                "A", "Explanation", i)).toList());
    }

}
