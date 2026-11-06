package com.englishhero.reading;

import com.englishhero.vocabulary.learning.ReviewSchedule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReviewScheduleTest {
 @Test void fixedIntervalsAndFailuresNeverAdvanceTwice() {
  var now=Instant.parse("2026-10-19T15:59:59Z");int[] days={1,3,7,14,30,30};
  for(int stage=0;stage<=5;stage++){
   var result=ReviewSchedule.next(stage,false,now);
   assertThat(result.stage()).isEqualTo(Math.min(stage+1,5));
   assertThat(result.dueAt()).isEqualTo(now.atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().plusDays(days[stage]).atTime(5,0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
   assertThat(result.ruleVersion()).isEqualTo("fixed-v1-binary");
   assertThat(ReviewSchedule.next(stage,true,now).stage()).isEqualTo(1);
   assertThat(ReviewSchedule.next(stage,true,now).dueAt()).isEqualTo(Instant.parse("2026-10-19T21:00:00Z"));
  }
 }
 @Test void firstReviewUsesNextCalendarDayAtFiveAcrossMidnight() {
  for (String time : new String[]{"2026-11-04T16:01:00Z","2026-11-04T20:59:00Z","2026-11-04T21:00:00Z","2026-11-05T15:59:00Z"}) {
   assertThat(ReviewSchedule.next(0,false,Instant.parse(time),"fixed-v2-seven").dueAt())
    .isEqualTo(Instant.parse("2026-11-05T21:00:00Z"));
   assertThat(ReviewSchedule.next(5,true,Instant.parse(time),"fixed-v2-seven").dueAt())
    .isEqualTo(Instant.parse("2026-11-05T21:00:00Z"));
  }
 }}
