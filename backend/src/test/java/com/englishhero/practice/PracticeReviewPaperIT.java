package com.englishhero.practice;
import static org.assertj.core.api.Assertions.*;
import java.util.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.web.server.LocalServerPort;import org.springframework.test.context.*;import org.springframework.jdbc.core.JdbcTemplate;import tools.jackson.databind.*;import com.englishhero.identity.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"english-hero.practice.ai.enabled=true","english-hero.practice.ai.api-key=stub-test-only","english-hero.practice.ai.worker-enabled=false"})
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
class PracticeReviewPaperIT {
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired IdentityService identities;@Autowired ObjectMapper json;
 final PracticePaperMistakeIT p=new PracticePaperMistakeIT();
 @DynamicPropertySource static void isolated(DynamicPropertyRegistry r){PracticePaperIT.isolated(r);}
 @BeforeEach void prepare()throws Exception{p.port=port;p.jdbc=jdbc;p.identities=identities;p.json=json;p.prepare();}
 @AfterEach void clean(){for(String table:List.of("practice_subjective_review_request","practice_subjective_review"))jdbc.update("DELETE FROM "+table+" WHERE user_id IN (SELECT id FROM identity_user WHERE username LIKE 'paper!_%' ESCAPE '!')");p.clean();}
 @Test void paperAndSingleSourcesCannotBeConfusedAndEachSlotUsesItsOwnFrozenText()throws Exception{
  var a=p.start();String id=a.get("id").asText();var input=new HashMap<String,Object>(Map.of("requestId",UUID.randomUUID().toString(),"sourceType","PAPER","attemptId",id,"slot","WRITING","consentVersion","2026-10-26"));
  assertThat(p.h.call(p.learner,"POST","/practice/subjective-reviews",input).statusCode()).isEqualTo(400);
  var done=p.h.success(p.h.call(p.learner,"POST","/practice/paper-attempts/"+id+"/submit",p.submission(a,false)));
  assertThat(p.h.call(p.other,"POST","/practice/subjective-reviews",input).statusCode()).isEqualTo(404);
  var result=p.h.call(p.learner,"POST","/practice/subjective-reviews",input);assertThat(result.statusCode()).isEqualTo(202);var review=json.readTree(result.body());String rid=review.get("id").asText();
  assertThat(review.get("sourceType").asText()).isEqualTo("PAPER");assertThat(review.get("submittedText").asText()).isEqualTo(done.at("/components/0/text").asText());
  jdbc.update("UPDATE practice_subjective_review SET status='FAILED',error_code='INVALID_OUTPUT',completed_at=UTC_TIMESTAMP(6) WHERE id=?",rid);
  var translation=new HashMap<>(input);translation.put("requestId",UUID.randomUUID().toString());translation.put("slot","TRANSLATION");assertThat(p.h.call(p.learner,"POST","/practice/subjective-reviews",translation).statusCode()).isEqualTo(202);
  var single=new HashMap<>(input);single.put("requestId",UUID.randomUUID().toString());single.put("sourceType","SUBJECTIVE");assertThat(p.h.call(p.learner,"POST","/practice/subjective-reviews",single).statusCode()).isEqualTo(404);
  assertThat(p.h.success(p.h.call(p.learner,"GET","/practice/paper-attempts/"+id,null)).toString()).isEqualTo(done.toString());
 }
}
