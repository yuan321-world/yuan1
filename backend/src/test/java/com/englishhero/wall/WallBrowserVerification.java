package com.englishhero.wall;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
// Explicit UI verification: -Dit.test=WallBrowserVerification (not part of default backend IT discovery).
class WallBrowserVerification extends WallHttpITSupport {
 @Test void realAccountsThroughBrowserPersistModerationAndNotifications()throws Exception{
  var user=actor(false);var other=actor(false);var admin=actor(true);String neighbor=create(other);
  Path root=Path.of("..").toAbsolutePath().normalize(),fixture=root.resolve(".tool-cache/wall-implementation/browser-fixture.json"),log=root.resolve(".tool-cache/wall-implementation/real-browser.log");
  Files.createDirectories(fixture.getParent());Files.writeString(fixture,json.writeValueAsString(Map.of("port",port,"user",accounts.get(user),"other",accounts.get(other),"admin",accounts.get(admin),"neighbor",neighbor)));
  try{var p=new ProcessBuilder("node",root.resolve("frontend/tests/real/wall-browser.cjs").toString()).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();boolean finished=p.waitFor(180,TimeUnit.SECONDS);if(!finished){p.destroyForcibly();throw new AssertionError("Real wall browser exceeded 180 seconds; inspect ignored log");}assertThat(p.exitValue()).withFailMessage("Real browser failed; inspect .tool-cache/wall-implementation/real-browser.log (credentials are not printed)").isZero();}finally{Files.deleteIfExists(fixture);}
 }
}
