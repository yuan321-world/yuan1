package com.englishhero.practice;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
class PracticeAudioTest {
 Path directory;
 @org.junit.jupiter.api.BeforeEach void setup() throws Exception {var base=Path.of(System.getProperty("user.dir"),"target","audio-unit");java.nio.file.Files.createDirectories(base);directory=java.nio.file.Files.createTempDirectory(base,"case-");}
 @org.junit.jupiter.api.AfterEach void clean() throws Exception {try(var files=java.nio.file.Files.list(directory)){for(var file:files.toList())java.nio.file.Files.deleteIfExists(file);}java.nio.file.Files.deleteIfExists(directory);}
 @Test void acceptsMp3ButRejectsForgedEmptyAndOversizedAudio() throws Exception {
  var storage=new PracticeAudioStorage(directory,32);
  byte[] mp3=new byte[]{(byte)0xff,(byte)0xfb,(byte)0x90,0,0,0,0,0};
  var stored=storage.store(new MockMultipartFile("file","../../voice.mp3","audio/mpeg",mp3));
  assertThat(stored.size()).isEqualTo(mp3.length);
  assertThat(storage.path(stored.key())).startsWith(directory);
  assertThat(stored.key()).doesNotContain("voice","..");
  assertThatThrownBy(()->storage.store(new MockMultipartFile("file","fake.mp3","audio/mpeg",new byte[]{1,2,3}))).isInstanceOf(com.englishhero.web.BusinessException.class);
  assertThatThrownBy(()->storage.store(new MockMultipartFile("file","empty.mp3","audio/mpeg",new byte[0]))).isInstanceOf(com.englishhero.web.BusinessException.class);
  assertThatThrownBy(()->storage.store(new MockMultipartFile("file","big.mp3","audio/mpeg",new byte[33]))).isInstanceOf(com.englishhero.web.BusinessException.class);
  assertThatThrownBy(()->storage.path("../escape")).isInstanceOf(com.englishhero.web.BusinessException.class);
 }
 @Test void validatesRangesIncludingSuffixAndUnsatisfiableRequests(){
  assertThat(PracticeAudioController.range("bytes=0-99",1000)).containsExactly(0,99);
  assertThat(PracticeAudioController.range("bytes=-10",1000)).containsExactly(990,999);
  assertThat(PracticeAudioController.range("bytes=995-",1000)).containsExactly(995,999);
  assertThatThrownBy(()->PracticeAudioController.range("bytes=1000-",1000)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->PracticeAudioController.range("bytes=0-1,3-4",1000)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
}
