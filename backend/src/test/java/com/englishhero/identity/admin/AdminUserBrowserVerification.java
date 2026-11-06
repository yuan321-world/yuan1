package com.englishhero.identity.admin;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AdminUserBrowserVerification extends AdminUserHttpITSupport {
 @Test void realBrowserDisablesRestoresAndKeepsAudit()throws Exception{
 var user=actor(false);var admin=actor(true);String message=create(user);long id=jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,accounts.get(user).get("username"));Path root=Path.of("..").toAbsolutePath().normalize(),fixture=root.resolve(".tool-cache/admin-users/browser-fixture.json");Files.createDirectories(fixture.getParent());Files.writeString(fixture,json.writeValueAsString(Map.of("port",port,"user",accounts.get(user),"admin",accounts.get(admin),"userId",Long.toString(id),"message",message)));
 try{var process=new ProcessBuilder("node",root.resolve("frontend/tests/real/admin-users-browser.cjs").toString()).directory(root.toFile()).redirectErrorStream(true).redirectOutput(root.resolve(".tool-cache/admin-users/real-browser.log").toFile()).start();boolean completed=process.waitFor(150,TimeUnit.SECONDS);if(!completed){process.destroyForcibly();throw new AssertionError("Real user management browser timed out");}assertThat(process.exitValue()).withFailMessage("Inspect ignored real-browser.log; credentials are not printed").isZero();}finally{Files.deleteIfExists(fixture);}
 }
}
