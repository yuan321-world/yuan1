package com.englishhero.reading;

import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Explicit real test-database UI verification; excluded from default *IT discovery. */
class VocabularyBrowserVerification extends LearningHttpITSupport {
    final Map<HttpClient, Map<String,String>> accounts = new HashMap<>();

    @Override HttpClient actor(boolean admin) throws Exception {
        String name="read_"+UUID.randomUUID().toString().replace("-", "").substring(0,16);
        String password=UUID.randomUUID()+"aA!";
        users.add(name);
        identities.register(name,password,"Vocabulary Browser Test");
        var client=anonymous();
        assertThat(call(client,"POST","/auth/login",Map.of("username",name,"password",password)).statusCode()).isEqualTo(200);
        accounts.put(client,Map.of("username",name,"password",password));
        return client;
    }

    @Test void productionVocabularyThroughRealBrowser() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path fixture=root.resolve(".tool-cache/vocabulary-full/verification-fixture.json");
        Path log=root.resolve(".tool-cache/vocabulary-full/verification-browser.log");
        Files.createDirectories(fixture.getParent());
        try {
            var user=actor(false); var other=actor(false);
            word(user,"agenda"); word(user,"summary");
            Files.writeString(fixture,json.writeValueAsString(Map.of("port",port,"user",accounts.get(user),"other",accounts.get(other))));
            var process=new ProcessBuilder("node",root.resolve("frontend/tests/real/vocabulary-browser.cjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean finished=process.waitFor(180,TimeUnit.SECONDS);
            if(!finished){process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);throw new AssertionError("Vocabulary browser exceeded 180 seconds; inspect ignored verification log");}
            assertThat(process.exitValue()).withFailMessage("Vocabulary browser failed; inspect .tool-cache/vocabulary-full/verification-browser.log (no credentials printed)").isZero();
        } finally {
            Files.deleteIfExists(fixture);
            accounts.clear();
            // Only records owned by this test's generated accounts; superclass clears words/accounts.
            for(String name:users) for(long id:jdbc.queryForList("SELECT id FROM identity_user WHERE username=?",Long.class,name)) {
                for(String table:List.of("vocabulary_phrase_event","vocabulary_phrase_command","vocabulary_phrase_round")) jdbc.update("DELETE FROM "+table+" WHERE user_id=?",id);
                jdbc.update("DELETE s FROM vocabulary_phrase_source s JOIN vocabulary_phrase p ON p.id=s.phrase_id WHERE p.user_id=?",id);
                jdbc.update("DELETE FROM vocabulary_phrase WHERE user_id=?",id);
            }
        }
    }
}
