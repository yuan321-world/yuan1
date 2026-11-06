package com.englishhero.practice;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PracticeResponsePrivacyTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void permitsPrivateFieldWordsInsideOrdinaryPromptText() {
        var response = json.readTree("""
            {"prompt":{"prompt":"The statement is for your reference. Take notes."}}
            """);
        assertThatCode(() -> PracticeContentBundleIT.assertNoPrivateFields(response)).doesNotThrowAnyException();
    }

    @Test
    void rejectsActualPrivateFieldsIncludingNestedObjectsAndArrays() {
        for (String response : new String[] {
            "{\"reference\":\"answer\"}",
            "{\"prompt\":{\"notes\":\"private\"}}",
            "{\"questions\":[{\"correctOption\":\"A\"}]}",
            "{\"questions\":[{\"explanation\":\"private\"}]}"
        }) {
            assertThatThrownBy(() -> PracticeContentBundleIT.assertNoPrivateFields(json.readTree(response)))
                .isInstanceOf(AssertionError.class);
        }
    }
}
