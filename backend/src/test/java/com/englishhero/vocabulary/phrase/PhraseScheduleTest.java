package com.englishhero.vocabulary.phrase;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PhraseScheduleTest {
 @Test void firstCompletionAndAllSevenIntervals(){Instant now=Instant.parse("2026-11-02T08:00:00Z");int stage=0;for(int days:new int[]{1,3,7,15,30,60,90,90}){var next=PhraseSchedule.next(stage,false,now,PhraseSchedule.RULE);assertEquals(now.plus(Duration.ofDays(days)),next.dueAt());stage=next.stage();}assertEquals(7,stage);}
 @Test void failureResetsToOneDayAndLegacyMissingRuleIsSupported(){var now=Instant.parse("2026-11-02T08:00:00Z");var next=PhraseSchedule.next(7,true,now,null);assertEquals(1,next.stage());assertEquals(now.plus(Duration.ofDays(1)),next.dueAt());assertEquals(PhraseSchedule.RULE,next.ruleVersion());}
}
