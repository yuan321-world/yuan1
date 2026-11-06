package com.englishhero.wall;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WallLayoutIT extends WallHttpITSupport {
 @Test void simultaneousPreferredPositionAndRetryNeverOverlap()throws Exception{
  var a=actor(false);var b=actor(false);var first=message();var second=message();
  var executor=Executors.newFixedThreadPool(2);try{var fa=executor.submit(()->call(a,"POST","/wall/messages",first));var fb=executor.submit(()->call(b,"POST","/wall/messages",second));var ra=fa.get(20,TimeUnit.SECONDS);var rb=fb.get(20,TimeUnit.SECONDS);assertThat(ra.statusCode()).isEqualTo(200);assertThat(rb.statusCode()).isEqualTo(200);var pa=json.readTree(ra.body()).get("position");var pb=json.readTree(rb.body()).get("position");int x=pa.get("x").asInt(),y=pa.get("y").asInt(),bx=pb.get("x").asInt(),by=pb.get("y").asInt();assertThat(x+280+30<=bx||x>=bx+280+30||y+420+30<=by||y>=by+420+30).isTrue();assertThat(call(a,"POST","/wall/messages",first).body()).isEqualTo(ra.body());}finally{executor.shutdownNow();}
 }
}
