package com.englishhero.identity.token;

import com.englishhero.identity.SessionPrincipal;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class JwtService {
    private final AuthProperties p; private final Clock clock;
    private final RSAPrivateKey privateKey; private final RSAPublicKey publicKey;
    public JwtService(AuthProperties p,Clock clock) {
        this.p=p;this.clock=clock;p.validate();
        try {
            var factory=KeyFactory.getInstance("RSA");
            privateKey=(RSAPrivateKey)factory.generatePrivate(new PKCS8EncodedKeySpec(pem(p.getPrivateKey())));
            publicKey=(RSAPublicKey)factory.generatePublic(new X509EncodedKeySpec(pem(p.getPublicKey())));
            if(publicKey.getModulus().bitLength()<2048 || !privateKey.getModulus().equals(publicKey.getModulus())) throw new GeneralSecurityException();
        } catch(Exception e){throw new IllegalStateException("Auth RSA key pair is invalid.");}
    }
    private byte[] pem(String path)throws Exception {return Base64.getDecoder().decode(Files.readString(Path.of(path)).replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));}
    public record IssuedAccess(String value,long expiresIn) { @Override public String toString(){return "IssuedAccess[redacted]";} }
    public IssuedAccess issue(SessionPrincipal principal,LoginFamily family) {
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        long ttl=Math.min(600,Duration.between(now,family.absoluteExpiresAt()).getSeconds());
        if(ttl<=0)throw TokenErrors.invalid();
        var claims=new JWTClaimsSet.Builder().issuer(p.getIssuer()).audience(p.getAudience()).subject(Long.toString(principal.userId()))
            .claim("sid",family.sid()).claim("sv",principal.securityVersion()).issueTime(Date.from(now)).notBeforeTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttl))).jwtID(UUID.randomUUID().toString()).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("at+jwt")).keyID(p.getKid()).build(),claims);
        try {jwt.sign(new RSASSASigner(privateKey));return new IssuedAccess(jwt.serialize(),ttl);}
        catch(JOSEException e){throw TokenErrors.unavailable();}
    }
    public VerifiedAccess verifySignatureAndClaims(String value) {
        if(value==null || value.length()>4096)throw TokenErrors.invalid();
        try {
            var jwt=SignedJWT.parse(value);var h=jwt.getHeader();
            if(!JWSAlgorithm.RS256.equals(h.getAlgorithm()) || !new JOSEObjectType("at+jwt").equals(h.getType())
                    || !p.getKid().equals(h.getKeyID()) || h.getJWKURL()!=null || h.getJWK()!=null || h.getX509CertURL()!=null
                    || h.getCriticalParams()!=null || !jwt.verify(new RSASSAVerifier(publicKey)))throw TokenErrors.invalid();
            var c=jwt.getJWTClaimsSet();
            if(!p.getIssuer().equals(c.getIssuer()) || !List.of(p.getAudience()).equals(c.getAudience()))throw TokenErrors.invalid();
            String sub=c.getSubject(),sid=c.getStringClaim("sid"); Object sv=c.getClaim("sv");
            if(sub==null || !sub.matches("[1-9][0-9]{0,18}") || sid==null || !sid.matches("[a-f0-9]{32}")
                || !(sv instanceof Integer || sv instanceof Long) || ((Number)sv).longValue()<0 || ((Number)sv).longValue()>Integer.MAX_VALUE
                || c.getJWTID()==null || !c.getJWTID().matches("[a-f0-9-]{36}"))throw TokenErrors.invalid();
            // Nimbus parses NumericDate as Date: reject fractional/non-number input as well.
            var raw=jwt.getPayload().toJSONObject();
            for(String name:List.of("iat","nbf","exp")) if(!(raw.get(name) instanceof Long || raw.get(name) instanceof Integer))throw TokenErrors.invalid();
            Instant iat=c.getIssueTime().toInstant(),nbf=c.getNotBeforeTime().toInstant(),exp=c.getExpirationTime().toInstant();
            if(iat.isAfter(clock.instant().plusSeconds(30)) || nbf.isAfter(clock.instant()) || !nbf.equals(iat)
                || !exp.isAfter(iat) || Duration.between(iat,exp).getSeconds()>600)throw TokenErrors.invalid();
            return new VerifiedAccess(Long.parseLong(sub),sid,((Number)sv).intValue(),exp);
        } catch(Exception e){throw TokenErrors.invalid();}
    }
}
