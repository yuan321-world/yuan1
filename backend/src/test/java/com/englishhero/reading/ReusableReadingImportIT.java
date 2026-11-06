package com.englishhero.reading;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReusableReadingImportIT extends ReadingHttpITSupport {
 @Test void sixReviewedExternalTextsValidateAndImportOnlyAsIdempotentDrafts()throws Exception{
  var payload=json.readValue(Files.readString(Path.of("../content/reading/reusable-20261030/articles.import.json")),Map.class);
  var admin=actor(true);long before=jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class),published=jdbc.queryForObject("SELECT COUNT(*) FROM reading_article WHERE status='PUBLISHED'",Long.class);
  var validation=call(admin,"POST","/admin/articles/import/validate",payload);assertThat(validation.statusCode()).isEqualTo(200);assertThat(json.readTree(validation.body()).path("valid").asBoolean()).isTrue();assertThat(json.readTree(validation.body()).path("itemCount").asInt()).isEqualTo(6);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before);
  String request=UUID.randomUUID().toString(),path="/admin/articles/import/confirm?clientImportId="+request;
  var response=call(admin,"POST",path,payload);assertThat(response.statusCode()).isIn(200,201);var ids=json.readTree(response.body()).path("articleIds");assertThat(ids.size()).isEqualTo(6);
  var source=json.valueToTree(payload).path("articles");int i=0;
  for(var id:ids){var draft=json.readTree(call(admin,"GET","/admin/articles/"+id.asText(),null).body());assertThat(draft.path("status").asText()).isEqualTo("DRAFT");assertThat(draft.path("publishedVersionId").isNull()).isTrue();assertThat(draft.at("/draft/sourceType").asText()).isEqualTo("THIRD_PARTY");assertThat(draft.at("/draft/sourceUrl").asText()).isEqualTo(source.get(i).path("sourceUrl").asText());assertThat(draft.at("/draft/title").asText()).isEqualTo(source.get(i).path("title").asText());assertThat(draft.at("/draft/wordCount").asInt()).isGreaterThan(100);i++;}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before+6);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article WHERE status='PUBLISHED'",Long.class)).isEqualTo(published);
  assertThat(json.readTree(call(admin,"POST",path,payload).body())).isEqualTo(json.readTree(response.body()));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before+6);
 }
}
