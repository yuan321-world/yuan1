package com.englishhero.reading;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/** Explicit real-browser check: -Dit.test=PracticeMatchBrowserCheck. Not part of ordinary backend-only IT discovery. */
class PracticeMatchBrowserCheck extends PracticeHttpITSupport {
 @org.springframework.beans.factory.annotation.Autowired com.englishhero.identity.token.AuthProperties authProperties;
 @org.junit.jupiter.api.BeforeEach void recoverOnlyThisChecksAbandonedFixtures(){users.addAll(jdbc.queryForList("SELECT username FROM identity_user WHERE username LIKE 'read!_ma!_%' ESCAPE '!' OR username LIKE 'read!_mu!_%' ESCAPE '!'",String.class));cleanupPractice();cleanup();users.clear();}
 @Test void realFrontendUsesTestDatabaseAndActualAuthentication()throws Exception{
  authProperties.setAllowedOrigins(List.of("http://127.0.0.1:4174","http://127.0.0.1:5188"));
  String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12),adminName="read_ma_"+suffix,userName="read_mu_"+suffix,adminPassword=UUID.randomUUID()+"aA!",userPassword=UUID.randomUUID()+"aA!";
  users.add(adminName);users.add(userName);identities.bootstrap(adminName,adminPassword,"Practice Browser Admin");identities.register(userName,userPassword,"Practice Browser Learner");var admin=anonymous();success(call(admin,"POST","/auth/login",Map.of("username",adminName,"password",adminPassword)));var a=article(admin);
  Path frontend=Path.of("../frontend").toAbsolutePath().normalize(),out=Path.of("../.tool-cache/practice-match-implementation").toAbsolutePath().normalize();Files.createDirectories(out);
  var viteBuilder=new ProcessBuilder("node","node_modules/vite/bin/vite.js","--host","127.0.0.1","--port","5188").directory(frontend.toFile()).redirectErrorStream(true).redirectOutput(out.resolve("real-vite.log").toFile());viteBuilder.environment().put("E2E_API_TARGET","http://127.0.0.1:"+port);Process vite=viteBuilder.start();
  try{
   boolean ready=false;for(int i=0;i<50;i++){try{var response=java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).connectTimeout(java.time.Duration.ofSeconds(1)).build().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:5188")).timeout(java.time.Duration.ofSeconds(2)).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding());if(response.statusCode()==200){ready=true;break;}}catch(Exception ignored){}Thread.sleep(100);}assertThat(ready).as("controlled real-test Vite ready").isTrue();
   var runner=new ProcessBuilder("node","node_modules/@playwright/test/cli.js","test","practice-match-real.spec.ts","--project=desktop","--workers=1","--reporter=line").directory(frontend.toFile()).redirectErrorStream(true).redirectOutput(out.resolve("real-browser-private.log").toFile());var env=runner.environment();env.put("E2E_EXTERNAL_SERVER","1");env.put("E2E_PORT","5188");env.put("E2E_PRACTICE_REAL","1");env.put("PRACTICE_ADMIN_NAME",adminName);env.put("PRACTICE_ADMIN_PASSWORD",adminPassword);env.put("PRACTICE_USER_NAME",userName);env.put("PRACTICE_USER_PASSWORD",userPassword);env.put("PRACTICE_ARTICLE_ID",a.get("id").asText());env.put("PRACTICE_QA_DIR",out.toString());Process child=runner.start();boolean ended=child.waitFor(240,TimeUnit.SECONDS);if(!ended)child.destroyForcibly();String report=Files.readString(out.resolve("real-browser-private.log")).replace(adminPassword,"[redacted]").replace(userPassword,"[redacted]");Files.writeString(out.resolve("real-browser.log"),report);Files.delete(out.resolve("real-browser-private.log"));assertThat(ended).as("real UI check completed within 240s").isTrue();assertThat(child.exitValue()).as("real UI flow; see sanitized .tool-cache/practice-match-implementation/real-browser.log").isZero();
  }finally{vite.destroy();vite.waitFor(5,TimeUnit.SECONDS);if(vite.isAlive())vite.destroyForcibly();}
 }
}
