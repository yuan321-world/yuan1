package com.englishhero.reading;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.englishhero.vocabulary.learning.WordbookDataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

class ExamWordbooksIT extends LearningHttpITSupport {
    @Autowired WordbookDataLoader loader;

    @Test void importsEightBooksOnceWithCoreSubsetsAndFrozenShuffleOrder() throws Exception {
        loader.loadExamBooks();
        var ids = jdbc.queryForList("SELECT id FROM vocabulary_wordbook WHERE data_version='ecdict-exam-bc015ed-v1' ORDER BY id", Long.class);
        assertThat(ids).hasSize(8);
        loader.loadExamBooks();
        assertThat(jdbc.queryForList("SELECT id FROM vocabulary_wordbook WHERE data_version='ecdict-exam-bc015ed-v1' ORDER BY id", Long.class)).isEqualTo(ids);

        for (String exam : List.of("KY", "CET6", "CET4", "GK")) {
            var shuffled = words(exam + "_SHUFFLE");
            var core = words(exam + "_CORE");
            assertThat(shuffled).hasSizeGreaterThan(1000).doesNotHaveDuplicates();
            assertThat(shuffled).isNotEqualTo(shuffled.stream().sorted().toList());
            assertThat(core).hasSize(1000).doesNotHaveDuplicates();
            assertThat(shuffled).containsAll(core);
        }
        var user = actor(false);
        var catalog = json.readTree(call(user, "GET", ROOT + "/wordbooks", null).body());
        assertThat(catalog.size()).isGreaterThanOrEqualTo(8);
        assertThat(catalog.values()).noneMatch(book -> book.path("title").asText().equals("大学英语四级·参考词表"));
        String book = jdbc.queryForObject("SELECT id FROM vocabulary_wordbook WHERE code='KY_SHUFFLE'", String.class);
        assertThat(call(user, "PUT", ROOT + "/source", Map.of("kind", "BOOK", "bookId", book)).statusCode()).isEqualTo(200);
        var result = call(user, "POST", ROOT + "/sessions", Map.of("requestId", UUID.randomUUID().toString(), "kind", "NEW", "mode", "RECOGNITION"));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(json.readTree(result.body()).path("current").path("lemma").asText()).isEqualTo(words("KY_SHUFFLE").getFirst());
    }

    @Test void aWordLearnedInCoreCountsInShuffleAndIsNotAssignedAsNewAgain() throws Exception {
        loader.loadExamBooks();
        var user = actor(false);
        String core = jdbc.queryForObject("SELECT id FROM vocabulary_wordbook WHERE code='CET4_CORE'", String.class);
        String shuffle = jdbc.queryForObject("SELECT id FROM vocabulary_wordbook WHERE code='CET4_SHUFFLE'", String.class);
        assertThat(call(user, "PUT", ROOT + "/settings", Map.of("newGoal", 50, "reviewGoal", 50)).statusCode()).isEqualTo(200);
        assertThat(call(user, "PUT", ROOT + "/source", Map.of("kind", "BOOK", "bookId", core)).statusCode()).isEqualTo(200);
        var start = Map.of("requestId", UUID.randomUUID().toString(), "kind", "NEW", "mode", "RECOGNITION");
        var first = json.readTree(call(user, "POST", ROOT + "/sessions", start).body());
        String session = ROOT + "/sessions/" + first.path("id").asText();
        String item = first.path("current").path("id").asText();
        String lemma = first.path("current").path("lemma").asText();
        var feedback = call(user, "POST", session + "/feedback", Map.of(
                "requestId", UUID.randomUUID().toString(), "expectedVersion", first.path("version").asInt(),
                "itemId", item, "action", "KNOWN"));
        assertThat(feedback.statusCode()).isEqualTo(200);
        int version = json.readTree(feedback.body()).path("version").asInt();
        assertThat(call(user, "POST", session + "/state", Map.of(
                "requestId", UUID.randomUUID().toString(), "expectedVersion", version, "status", "PAUSED")).statusCode()).isEqualTo(200);
        assertThat(call(user, "PUT", ROOT + "/source", Map.of("kind", "BOOK", "bookId", shuffle)).statusCode()).isEqualTo(200);

        var catalog = json.readTree(call(user, "GET", ROOT + "/wordbooks", null).body());
        assertThat(catalog.values().stream().filter(book -> shuffle.equals(book.path("id").asText())).toList())
                .singleElement().satisfies(book -> assertThat(book.path("learned").asInt()).isEqualTo(1));
        var response = call(user, "POST", ROOT + "/sessions",
                Map.of("requestId", UUID.randomUUID().toString(), "kind", "NEW", "mode", "RECOGNITION"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForList("""
                SELECT d.lemma FROM vocabulary_learning_item i
                JOIN vocabulary_learning_word w ON w.id=i.learning_word_id
                JOIN dictionary_entry d ON d.id=w.entry_id
                WHERE i.session_id=?
                """, String.class, json.readTree(response.body()).path("id").asText()))
                .isNotEmpty().doesNotContain(lemma);
    }

    private List<String> words(String code) {
        return jdbc.queryForList("""
            SELECT d.lemma FROM vocabulary_wordbook b
            JOIN vocabulary_wordbook_entry e ON e.book_id=b.id
            JOIN dictionary_entry d ON d.id=e.entry_id
            WHERE b.code=? AND b.data_version='ecdict-exam-bc015ed-v1' ORDER BY e.sort_order
            """, String.class, code);
    }
}
