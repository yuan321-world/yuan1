package com.englishhero.practice.review;
import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;import tools.jackson.databind.ObjectMapper;
class DeepSeekReviewClientTest {
 final ObjectMapper json=new ObjectMapper();HttpServer server;AtomicInteger calls=new AtomicInteger();AtomicReference<String> request=new AtomicReference<>();AtomicReference<String> bearer=new AtomicReference<>();String response;int status=200;
 final String result="""
 {"score":12,"summaryZh":"表达清楚","summaryEn":"Clear.","dimensions":[{"name":"内容","level":"GOOD"},{"name":"结构","level":"GOOD"},{"name":"语言","level":"GOOD"}],"annotations":[],"omissions":[],"revisedText":"A quiet room helps students."}
 """;
 @BeforeEach void setup()throws Exception{response=json.writeValueAsString(Map.of("model","deepseek-flash","choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",result))),"usage",Map.of("prompt_tokens",3000,"completion_tokens",2000)));server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/chat/completions",e->{calls.incrementAndGet();request.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));bearer.set(e.getRequestHeaders().getFirst("Authorization"));byte[] bytes=response.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();}
 @AfterEach void stop(){server.stop(0);}
 DeepSeekReviewClient client(){var p=new PracticeReviewProperties();p.setEnabled(true);p.setApiKey("synthetic-only-not-a-key");return new DeepSeekReviewClient(p,json,new PracticeReviewResultValidator(json),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"));}
 PracticeReviewDtos.Source source(){return new PracticeReviewDtos.Source("SUBJECTIVE","1","WRITING","7","Write an essay.","Explain why.","A quiet room helps students.");}
 @Test void sendsStrictJsonWithoutToolsOrAccountDetails(){var value=client().review(source());assertThat(value.result().score()).isEqualTo(12);assertThat(value.inputTokens()).isEqualTo(3000);var body=json.readTree(request.get());assertThat(body.at("/thinking/type").asText()).isEqualTo("disabled");assertThat(body.at("/response_format/type").asText()).isEqualTo("json_object");assertThat(body.path("max_tokens").asInt()).isEqualTo(8000);assertThat(body.has("tools")).isFalse();assertThat(bearer.get()).isEqualTo("Bearer synthetic-only-not-a-key");assertThat(calls.get()).isEqualTo(1);}
 @Test void providerLimitDoesNotAutomaticallyRetry(){status=429;response="{}";assertThatThrownBy(()->client().review(source())).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,e->assertThat(e.code).isEqualTo("PROVIDER_LIMIT"));assertThat(calls.get()).isEqualTo(1);}
 @Test void invalidFeedbackRetainsTrustedUsageForSettlement(){
  response=json.writeValueAsString(Map.of("model","deepseek-flash","choices",List.of(Map.of("finish_reason","stop","message",Map.of("content","{}"))),"usage",Map.of("prompt_tokens",3000,"completion_tokens",2000)));
  assertThatThrownBy(()->client().review(source())).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,e->{assertThat(e.code).isEqualTo("INVALID_OUTPUT");assertThat(e).extracting("inputTokens","outputTokens").containsExactly(3000L,2000L);});
 }
 @Test void rejectedAuthenticationCanReleaseButServerFailureRemainsUnknown(){
  status=401;assertThatThrownBy(()->client().review(source())).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,e->assertThat(e).extracting("definitelyUnsent").isEqualTo(true));
  status=500;assertThatThrownBy(()->client().review(source())).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,e->assertThat(e).extracting("definitelyUnsent").isEqualTo(false));
 }
 @Test void malformedOrUnpricedUsageNeverReleasesAnUnknownReserve(){
  for(var usage:List.of(Map.of("prompt_tokens",-1,"completion_tokens",10),Map.of("prompt_tokens",5,"completion_tokens",8001),Map.of("prompt_tokens",new java.math.BigInteger("18446744073709551617"),"completion_tokens",10))){
   response=json.writeValueAsString(Map.of("model","deepseek-flash","choices",List.of(Map.of("finish_reason","length","message",Map.of("content","{}"))),"usage",usage));
   assertThatThrownBy(()->client().review(source())).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,e->{assertThat(e.inputTokens).isNull();assertThat(e.outputTokens).isNull();assertThat(e.definitelyUnsent).isFalse();});
  }
  response=json.writeValueAsString(Map.of("model","other-model","usage",Map.of("prompt_tokens",5,"completion_tokens",10)));
  assertThatThrownBy(()->client().review(source())).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,e->assertThat(e.inputTokens).isNull());
 }
 @Test void otherProviderErrorsAndTruncationRemainSafeFailures(){var codes=Map.of(401,"PROVIDER_AUTH",402,"PROVIDER_BALANCE",500,"PROVIDER_UNAVAILABLE");for(var entry:codes.entrySet()){status=entry.getKey();assertThatThrownBy(()->client().review(source())).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,e->assertThat(e.code).isEqualTo(entry.getValue()));}status=200;response=json.writeValueAsString(Map.of("model","deepseek-flash","choices",List.of(Map.of("finish_reason","length","message",Map.of("content",result)))));assertThatThrownBy(()->client().review(source())).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,e->assertThat(e.code).isEqualTo("INVALID_OUTPUT"));assertThat(calls.get()).isEqualTo(4);}
 @Test void emptyAndOversizedResponsesNeverBecomeFeedback(){response="{}";assertThatThrownBy(()->client().review(source())).isInstanceOf(DeepSeekReviewClient.Failure.class);response="x".repeat(140000);assertThatThrownBy(()->client().review(source())).isInstanceOf(DeepSeekReviewClient.Failure.class);assertThat(calls.get()).isEqualTo(2);}
 @Test void budgetAlwaysRoundsUpAndUnknownUsageKeepsReservation(){assertThat(PracticeReviewBudget.cost(3000L,2000L)).isEqualTo(3300);assertThat(PracticeReviewBudget.cost(1L,0L)).isEqualTo(1);assertThat(PracticeReviewBudget.cost(null,2L)).isEqualTo(50000);assertThat(PracticeReviewBudget.cost(1L,9000L)).isEqualTo(50000);}
 @Test void rejectsEscapedPayloadBeyondBudgetBoundBeforeSending(){var source=new PracticeReviewDtos.Source("SUBJECTIVE","1","WRITING","7","Write.","","A"+"\u0001".repeat(19999));assertThatThrownBy(()->client().review(source)).isInstanceOf(DeepSeekReviewClient.Failure.class);assertThat(calls.get()).isZero();}
 @Test void twentyOriginalCalibrationSamplesUseLocalStubOnly()throws Exception{
  var samples=json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of("../content/ai-review/original-calibration.json"))).get("samples");assertThat(samples.size()).isEqualTo(20);
  for(var sample:samples){assertThat(sample.path("synthetic").asBoolean()).isTrue();var s=new PracticeReviewDtos.Source("SUBJECTIVE","1",sample.get("slot").asText(),"7",sample.get("prompt").asText(),"",sample.get("text").asText());assertThat(client().review(s).result().score()).isEqualTo(12);var body=json.readTree(request.get());assertThat(body.at("/messages/1/content").asText()).doesNotContain("attemptId","setVersionId");}assertThat(calls.get()).isEqualTo(20);
 }
}
