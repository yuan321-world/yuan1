package com.englishhero.reading;
import com.englishhero.reading.checkin.CheckinService;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CheckinDateTest {
 @Test void shanghaiMidnightAndMondayBoundariesAreIndependentOfHostTimezone(){
  assertThat(CheckinService.date(Instant.parse("2026-10-18T15:59:59Z"))).isEqualTo(LocalDate.of(2026,10,18));
  var monday=CheckinService.date(Instant.parse("2026-10-18T16:00:00Z"));assertThat(monday).isEqualTo(LocalDate.of(2026,10,19));
  assertThat(CheckinService.monday(monday)).isEqualTo(monday);assertThat(CheckinService.monday(LocalDate.of(2026,10,18))).isEqualTo(LocalDate.of(2026,10,12));
 }
}
