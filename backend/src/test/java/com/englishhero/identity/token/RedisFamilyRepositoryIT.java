package com.englishhero.identity.token;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;
class RedisFamilyRepositoryIT {
    @Test void rotationPreservesDeadlineAndNamespaceAndRememberBoundary() {
        var connection=TokenTestSupport.connection();var redis=new StringRedisTemplate(connection);
        var p=TokenTestSupport.auth();var repo=new RedisFamilyRepository(redis,p);var codec=new RefreshTokenCodec(p);
        var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var credential=codec.create();
        var family=LoginFamily.start(credential.sid(),42,1,true,now);String digest=codec.digest(credential.secret());
        try {
            repo.create(family,digest);
            assertThat(repo.rotate(family.sid(),digest,"b".repeat(64),42,1,now.plusSeconds(604799)).status()).isEqualTo(RedisFamilyRepository.RotationStatus.SUCCESS);
            assertThat(repo.find(family.sid()).absoluteExpiresAt()).isEqualTo(family.absoluteExpiresAt());
            assertThat(redis.getExpire(repo.familyKey(family.sid()))).isBetween(604790L,604800L);
            assertThatThrownBy(()->repo.validateAndTouch(family.sid(),42,1,now.plusSeconds(604800))).isInstanceOf(com.englishhero.identity.IdentityException.class);
            var isolated=TokenTestSupport.auth();isolated.setNamespace("eh:test:auth:v2");
            assertThatThrownBy(()->new RedisFamilyRepository(redis,isolated).find(family.sid())).isInstanceOf(com.englishhero.identity.IdentityException.class);
        } finally {redis.delete(repo.familyKey(family.sid()));redis.delete(repo.usedKey(family.sid(),digest));connection.destroy();}
    }
    @Test void atomicRotationReplayAndIdleBoundaries() throws Exception {
        var connection=TokenTestSupport.connection();var redis=new StringRedisTemplate(connection);
        var repo=new RedisFamilyRepository(redis,TokenTestSupport.auth());
        var codec=new RefreshTokenCodec(TokenTestSupport.auth());var credential=codec.create();
        var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var family=LoginFamily.start(credential.sid(),42,1,false,now);
        var digest=codec.digest(credential.secret());
        try {
            repo.create(family,digest);
            assertThat(repo.rotate(family.sid(),"0".repeat(64),"1".repeat(64),42,1,now).status()).isEqualTo(RedisFamilyRepository.RotationStatus.INVALID);
            assertThat(repo.validateAndTouch(family.sid(),42,1,now).absoluteExpiresAt()).isEqualTo(family.absoluteExpiresAt());
            try(var pool=Executors.newFixedThreadPool(2)) {
                var a=pool.submit(()->repo.rotate(family.sid(),digest,"2".repeat(64),42,1,now));
                var b=pool.submit(()->repo.rotate(family.sid(),digest,"3".repeat(64),42,1,now));
                assertThat(List.of(a.get().status(),b.get().status())).containsExactlyInAnyOrder(RedisFamilyRepository.RotationStatus.SUCCESS,RedisFamilyRepository.RotationStatus.REPLAY);
            }
            assertThatThrownBy(()->repo.validateAndTouch(family.sid(),42,1,now)).isInstanceOf(com.englishhero.identity.IdentityException.class);
            var second=LoginFamily.start(codec.create().sid(),42,1,false,now);
            repo.create(second,digest);
            assertThatThrownBy(()->repo.validateAndTouch(second.sid(),42,1,now.plusSeconds(7200))).isInstanceOf(com.englishhero.identity.IdentityException.class);
            redis.delete(repo.familyKey(second.sid()));
        } finally {redis.delete(repo.familyKey(family.sid()));redis.delete(repo.usedKey(family.sid(),digest));connection.destroy();}
    }
}
