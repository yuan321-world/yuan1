package com.englishhero.practice.review;
import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
class DeepSeekGrammarClientTest {
 @Test void grammarUsesSeparatePromptAndRejectsInventedQuotesWhileKeepingKnownCost()throws Exception{
  var json=new ObjectMapper();var sent=new AtomicReference<String>();var quote=new AtomicReference<>("Quiet spaces");var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/chat/completions",e->{sent.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));var analysis=Map.of("scopeNote","完整句子。","mainClause",List.of(Map.of("quote",quote.get(),"role","主语","explanation","名词短语。")),"clauses",List.of(),"clauseNote","没有从句。","grammarPoints",List.of(Map.of("quote","help people read","explanation","不带to的不定式。")));byte[] response=json.writeValueAsBytes(Map.of("model","deepseek-flash","choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",json.writeValueAsString(analysis)))),"usage",Map.of("prompt_tokens",500,"completion_tokens",300)));e.sendResponseHeaders(200,response.length);e.getResponseBody().write(response);e.close();});server.start();
  try{var config=new PracticeReviewProperties();config.setEnabled(true);config.setApiKey("synthetic-only");var client=new DeepSeekReviewClient(config,json,new PracticeReviewResultValidator(json),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"));var result=client.analyze("Quiet spaces help people read.");assertThat(json.readTree(result.translation()).path("mainClause").size()).isEqualTo(1);var body=json.readTree(sent.get());assertThat(body.path("max_tokens").asInt()).isEqualTo(3072);assertThat(body.at("/messages/0/content").asText()).contains("grammar tutor","fragments/phrases");assertThat(body.at("/messages/1/content").asText()).isEqualTo("Quiet spaces help people read.");quote.set("An invented subject");assertThatThrownBy(()->client.analyze("Quiet spaces help people read.")).isInstanceOfSatisfying(DeepSeekReviewClient.Failure.class,f->{assertThat(f.code).isEqualTo("INVALID_OUTPUT");assertThat(f.inputTokens).isEqualTo(500);assertThat(f.outputTokens).isEqualTo(300);assertThat(f.definitelyUnsent).isFalse();});}
  finally{server.stop(0);}
 }
}
