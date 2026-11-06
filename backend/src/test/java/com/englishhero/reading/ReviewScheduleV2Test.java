package com.englishhero.reading;
import com.englishhero.vocabulary.learning.ReviewSchedule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReviewScheduleV2Test {
 @Test void sevenStagesAndLegacyHighStagesCannotDowngrade(){var now=Instant.parse("2026-11-02T00:00:00Z");int[] days={1,3,7,15,30,60,90,90};for(int stage=0;stage<=7;stage++){var next=ReviewSchedule.next(stage,false,now,"fixed-v2-seven");assertThat(next.stage()).isEqualTo(Math.min(stage+1,7));assertThat(next.dueAt()).isEqualTo(now.atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().plusDays(days[stage]).atTime(5,0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant());}assertThat(ReviewSchedule.next(7,false,now,"fixed-v1-binary").stage()).isEqualTo(7);assertThat(ReviewSchedule.next(7,true,now,"fixed-v2-seven").stage()).isEqualTo(1);}
}
