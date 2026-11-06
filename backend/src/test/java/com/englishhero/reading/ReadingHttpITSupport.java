package com.englishhero.reading;
import com.englishhero.identity.*;
import com.englishhero.identity.token.TokenTestSupport;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
abstract class ReadingHttpITSupport {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired tools.jackson.databind.ObjectMapper json;
    final List<String> users=new ArrayList<>();
    final Map<HttpClient,String> access=new java.util.concurrent.ConcurrentHashMap<>();
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry){
        TokenTestSupport.register(registry);
        var yaml=new YamlPropertiesFactoryBean();yaml.setResources(new FileSystemResource("./config/application-test.yml"));var p=Objects.requireNonNull(yaml.getObject());
        assertThat(p.getProperty("spring.datasource.url")).matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        for(String key:List.of("url","username","password"))registry.add("spring.datasource."+key,()->p.getProperty("spring.datasource."+key));
    }
    HttpClient anonymous(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
    HttpClient actor(boolean admin)throws Exception{
        String name="read_"+UUID.randomUUID().toString().replace("-","").substring(0,16);users.add(name);
        String password=UUID.randomUUID()+"aA!";
        if(admin)identities.bootstrap(name,password,"Reading Test Admin");else identities.register(name,password,"Reading Test User");
        var c=anonymous();assertThat(call(c,"POST","/auth/login",Map.of("username",name,"password",password)).statusCode()).isEqualTo(200);return c;
    }
    HttpResponse<String> call(HttpClient c,String method,String path,Object body)throws Exception {
        String csrf=null;
        if(!method.equals("GET")){var r=call(c,"GET","/auth/csrf",null);csrf=json.readTree(r.body()).get("token").asText();}
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Origin","http://127.0.0.1:4174");
        if(access.containsKey(c))request.header("Authorization","Bearer "+access.get(c));
        if(csrf!=null)request.header("X-CSRF-TOKEN",csrf);
        if(body!=null)request.header("Content-Type","application/json");
        var result=c.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        if(path.equals("/auth/login")&&result.statusCode()==200)access.put(c,json.readTree(result.body()).get("accessToken").asText());
        return result;
    }
    ReadingDtos.DraftInput draft(String title){return ArticleRulesTest.draft(title,"Small steps can change a day. We learn by trying again.","ORIGINAL",null);}
    @AfterEach void cleanup(){
        for(String name:users){
            var ids=jdbc.queryForList("SELECT id FROM identity_user WHERE username=?",Long.class,name);
            for(Long uid:ids){
                boolean hasCheckins=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reading_daily_checkin'",Boolean.class));
                if(hasCheckins){jdbc.update("DELETE d FROM reading_daily_checkin d JOIN reading_article_qualification q ON q.id=d.qualification_id WHERE q.user_id=? OR q.article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);jdbc.update("DELETE FROM reading_article_qualification WHERE user_id=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);}

                boolean hasArticles=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reading_article'",Boolean.class));
                if(hasArticles){
                    boolean hasAttempts=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='user_reading_attempt'",Boolean.class));
                    if(hasAttempts){String owned="user_id=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)";jdbc.update("DELETE r FROM user_reading_attempt_request r JOIN user_reading_attempt a ON a.id=r.attempt_id WHERE a.user_id=? OR a.article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);jdbc.update("DELETE s FROM user_reading_answer s JOIN user_reading_attempt a ON a.id=s.attempt_id WHERE a.user_id=? OR a.article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);jdbc.update("DELETE FROM user_reading_attempt WHERE "+owned,uid,uid);}
                    boolean hasQuiz=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reading_quiz'",Boolean.class));
                    if(hasQuiz){jdbc.update("DELETE q FROM reading_question q JOIN reading_quiz_version v ON v.id=q.quiz_version_id JOIN reading_quiz r ON r.id=v.quiz_id WHERE r.created_by=? OR r.article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);jdbc.update("UPDATE reading_quiz SET status='UNLISTED',published_version_id=NULL WHERE created_by=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);jdbc.update("DELETE v FROM reading_quiz_version v JOIN reading_quiz r ON r.id=v.quiz_id WHERE r.created_by=? OR r.article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);jdbc.update("DELETE FROM reading_quiz WHERE created_by=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);}
                    boolean hasPersonalWriting=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='user_writing_favorite'",Boolean.class));
                    if(hasPersonalWriting){for(String table:List.of("user_writing_favorite","user_writing_imitation"))jdbc.update("DELETE FROM "+table+" WHERE user_id=? OR material_id IN (SELECT id FROM reading_writing_material WHERE created_by=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?))",uid,uid,uid);}
                    boolean hasWriting=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reading_writing_material'",Boolean.class));
                    if(hasWriting)jdbc.update("DELETE FROM reading_writing_material WHERE created_by=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);
                    boolean hasFavorites=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='user_article_favorite'",Boolean.class));
                    if(hasFavorites)jdbc.update("DELETE FROM user_article_favorite WHERE user_id=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);
                    boolean hasWords=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='user_word'",Boolean.class));
                    if(hasWords){jdbc.update("DELETE s FROM user_word_source s JOIN user_word w ON w.id=s.word_id WHERE w.user_id=? OR s.article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);jdbc.update("DELETE FROM user_word WHERE user_id=?",uid);}
                    boolean hasProgress=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reading_progress'",Boolean.class));
                    if(hasProgress)jdbc.update("DELETE FROM reading_progress WHERE user_id=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);
                    jdbc.update("UPDATE reading_article SET draft_version_id=NULL,published_version_id=NULL WHERE created_by=?",uid);jdbc.update("DELETE v FROM reading_article_version v JOIN reading_article a ON a.id=v.article_id WHERE a.created_by=?",uid);jdbc.update("DELETE FROM reading_article WHERE created_by=?",uid);}
                boolean hasImports=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reading_import_batch'",Boolean.class));
                if(hasImports)jdbc.update("DELETE FROM reading_import_batch WHERE created_by=?",uid);
                jdbc.update("DELETE FROM identity_user WHERE id=?",uid);
            }
        }
    }
}
