package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Set;
import com.englishhero.vocabulary.learning.VocabularyStatsService;
import static org.assertj.core.api.Assertions.*;
class VocabularyStreakTest {
 @Test void yesterdayStreakSurvivesNewDayUntilTodaysFirstCompletion(){var today=LocalDate.parse("2026-11-02");var days=Set.of(today.minusDays(3),today.minusDays(2),today.minusDays(1));assertThat(VocabularyStatsService.streak(days,today)).isEqualTo(3);assertThat(VocabularyStatsService.streak(days,today.plusDays(1))).isZero();}
 @Test void emptyAndGappedHistoryDoesNotFabricateDays(){var today=LocalDate.parse("2026-11-02");assertThat(VocabularyStatsService.streak(Set.of(),today)).isZero();assertThat(VocabularyStatsService.longest(Set.of())).isZero();var days=Set.of(today,today.minusDays(1),today.minusDays(3));assertThat(VocabularyStatsService.streak(days,today)).isEqualTo(2);assertThat(VocabularyStatsService.longest(days)).isEqualTo(2);}
}
