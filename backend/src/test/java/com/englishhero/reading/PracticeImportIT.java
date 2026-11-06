package com.englishhero.reading;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PracticeImportIT extends PracticeHttpITSupport {
 @Test void validationIsReadOnlyAndImportAlwaysCreatesUnreviewedDraft()throws Exception{
  var admin=actor(true);var a=article(admin);var input=draftSet(a,0,List.of(question("q1","Imported original")));input.remove("expectedLockVersion");input.put("requestId",UUID.randomUUID().toString());long before=jdbc.queryForObject("SELECT COUNT(*) FROM practice_set",Long.class);
  var validation=success(call(admin,"POST","/admin/practice/import/validate",input));assertThat(validation.get("valid").asBoolean()).isTrue();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_set",Long.class)).isEqualTo(before);
  var set=success(call(admin,"POST","/admin/practice/import",input));assertThat(set.get("status").asText()).isEqualTo("DRAFT");assertThat(set.get("revision").asInt()).isZero();assertThat(set.get("reviewedQuestionKeys").size()).isZero();assertThat(set.get("sourceReviewed").asBoolean()).isFalse();
  assertThat(success(call(admin,"POST","/admin/practice/import",input)).get("id")).isEqualTo(set.get("id"));input.put("questions",List.of(question("q1","Changed duplicate")));assertThat(call(admin,"POST","/admin/practice/import",input).statusCode()).isEqualTo(409);input.put("requestId",UUID.randomUUID().toString());assertThat(call(admin,"POST","/admin/practice/import",input).statusCode()).isEqualTo(409);assertThat(success(call(admin,"GET","/admin/practice/sets/"+set.get("id").asText(),null)).toString()).contains("Imported original").doesNotContain("Changed duplicate");
 }
 @Test void unsupportedTypesReviewClaimsAndOversizedJsonAreRejected()throws Exception{
  var admin=actor(true);var a=article(admin);var input=draftSet(a,0,List.of(question("q1","Original")));input.remove("expectedLockVersion");input.put("requestId",UUID.randomUUID().toString());input.put("examCode","CET6");assertThat(success(call(admin,"POST","/admin/practice/import/validate",input)).get("valid").asBoolean()).isFalse();input.put("examCode","CET4");input.put("reviewedQuestionKeys",List.of("q1"));assertThat(call(admin,"POST","/admin/practice/import",input).statusCode()).isEqualTo(400);input.remove("reviewedQuestionKeys");input.put("questions",List.of(question("q1","a".repeat(270000))));assertThat(call(admin,"POST","/admin/practice/import",input).statusCode()).isEqualTo(413);
 }
}
