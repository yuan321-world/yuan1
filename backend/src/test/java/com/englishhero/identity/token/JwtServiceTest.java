package com.englishhero.identity.token;
import java.time.*;
import java.util.*;
import com.englishhero.identity.SessionPrincipal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class JwtServiceTest {
    final Instant now=Instant.parse("2026-10-15T00:00:00Z");
    @Test void signedTokensClipLifetimeAndRejectTampering() {
        var service=new JwtService(TokenTestSupport.auth(),Clock.fixed(now,ZoneOffset.UTC));
        var family=LoginFamily.start("a".repeat(32),42,3,false,now);
        var issued=service.issue(new SessionPrincipal(42,"user",3,List.of("USER")),family);
        assertThat(issued.expiresIn()).isEqualTo(600);
        assertThat(service.verifySignatureAndClaims(issued.value()).userId()).isEqualTo(42);
        assertThat(issued.toString()).doesNotContain(issued.value());
        assertThatThrownBy(()->service.verifySignatureAndClaims(issued.value()+"bad")).isInstanceOf(RuntimeException.class);
        var near=new JwtService(TokenTestSupport.auth(),Clock.fixed(family.absoluteExpiresAt().minusSeconds(15),ZoneOffset.UTC));
        assertThat(near.issue(new SessionPrincipal(42,"user",3,List.of()),family).expiresIn()).isEqualTo(15);
    }
    @Test void signedButInvalidAlgorithmIssuerAudienceKidTypesAndFutureTimeAreRejected() throws Exception {
        var p=TokenTestSupport.auth();var service=new JwtService(p,Clock.fixed(now,ZoneOffset.UTC));
        var f=LoginFamily.start("a".repeat(32),42,3,false,now);
        String valid=service.issue(new SessionPrincipal(42,"user",3,List.of()),f).value();
        var parsed=com.nimbusds.jwt.SignedJWT.parse(valid);
        var pem=java.nio.file.Files.readString(java.nio.file.Path.of(p.getPrivateKey())).replaceAll("-----[^-]+-----","").replaceAll("\\s","");
        var key=(java.security.interfaces.RSAPrivateKey)java.security.KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        var signer=new com.nimbusds.jose.crypto.RSASSASigner(key);
        for(var mutation:List.<Map<String,Object>>of(Map.of("iss","wrong"),Map.of("aud","wrong"),Map.of("sub","-1"),Map.of("sv","3"),Map.of("sv",3.5),Map.of("sid","../bad"),Map.of("nbf",now.plusSeconds(1).getEpochSecond()),Map.of("iat","invalid"),Map.of("exp",now.plusSeconds(900).getEpochSecond()))) {
            var claims=new HashMap<String,Object>(parsed.getJWTClaimsSet().toJSONObject());claims.putAll(mutation);
            var bad=new com.nimbusds.jose.JWSObject(parsed.getHeader(),new com.nimbusds.jose.Payload(claims));bad.sign(signer);
            assertThatThrownBy(()->service.verifySignatureAndClaims(bad.serialize())).isInstanceOf(com.englishhero.identity.IdentityException.class);
        }
        for(var header:List.of(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.PS256).keyID(p.getKid()).type(new com.nimbusds.jose.JOSEObjectType("at+jwt")).build(),
                new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID("unknown").type(new com.nimbusds.jose.JOSEObjectType("at+jwt")).build(),
                new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID(p.getKid()).jwkURL(java.net.URI.create("https://evil.invalid/key")).build())) {
            var bad=new com.nimbusds.jwt.SignedJWT(header,parsed.getJWTClaimsSet());bad.sign(signer);
            assertThatThrownBy(()->service.verifySignatureAndClaims(bad.serialize())).isInstanceOf(com.englishhero.identity.IdentityException.class);
        }
    }
}
