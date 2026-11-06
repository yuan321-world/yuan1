package com.englishhero.reading.translation;
import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
class BaiduTranslationClientTest {
 @Test void signsOriginalUtf8AndSendsOnlyFormBodyAndParsesAllSegments()throws Exception{
  var form=new AtomicReference<String>();var json=new ObjectMapper();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/translate",e->{assertThat(e.getRequestURI().getQuery()).isNull();assertThat(e.getRequestMethod()).isEqualTo("POST");form.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] b=json.writeValueAsBytes(Map.of("from","en","to","zh","trans_result",List.of(Map.of("src","Hello & world","dst","你好，世界"),Map.of("src","Read together","dst","一起阅读"))));e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();
  try{var config=new BaiduTranslationProperties();config.setAppId("synthetic-app");config.setSecretKey("synthetic-secret");var client=new BaiduTranslationClient(config,json,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/translate"));var result=client.translate("Hello & world Read together");assertThat(result.translation()).isEqualTo("你好，世界\n一起阅读");var fields=new HashMap<String,String>();for(String field:form.get().split("&")){String[] pair=field.split("=",2);fields.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}assertThat(fields.get("q")).isEqualTo("Hello & world Read together");assertThat(fields.get("sign")).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(("synthetic-app"+fields.get("q")+fields.get("salt")+"synthetic-secret").getBytes(StandardCharsets.UTF_8))));assertThat(fields.keySet()).containsExactlyInAnyOrder("q","appid","salt","sign","from","to");assertThat(form.get()).doesNotContain("synthetic-secret");}finally{server.stop(0);}
 }
 @Test void missingConfigurationNeverSendsAndCountsSourceCharacters(){var config=new BaiduTranslationProperties();assertThatThrownBy(()->new BaiduTranslationClient(config,new ObjectMapper()).translate("two words")).isInstanceOfSatisfying(BaiduTranslationClient.Failure.class,e->assertThat(e.definitelyUnsent).isTrue());assertThat(config.characters("a b!")).isEqualTo(4);assertThat(config.characters("a".repeat(1000))).isEqualTo(1000);}
}
