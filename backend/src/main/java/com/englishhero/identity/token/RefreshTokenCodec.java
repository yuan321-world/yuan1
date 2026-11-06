package com.englishhero.identity.token;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public final class RefreshTokenCodec {
    private final byte[] key;
    private final SecureRandom random=new SecureRandom();
    public RefreshTokenCodec(AuthProperties p) { key=Base64.getDecoder().decode(p.getHmacSecret()); if(key.length<32) throw new IllegalStateException("Refresh HMAC key too short."); }
    public record RefreshCredential(String sid,String secret) {
        public String value(){return sid+"."+secret;}
        @Override public String toString(){return "RefreshCredential[redacted]";}
    }
    private byte[] bytes(int size){var value=new byte[size];random.nextBytes(value);return value;}
    public RefreshCredential create() {return new RefreshCredential(HexFormat.of().formatHex(bytes(16)),Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(32)));}
    public RefreshCredential next(String sid) {return new RefreshCredential(sid,create().secret());}
    public RefreshCredential parse(String value) {
        if(value==null || !value.matches("[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}")) throw TokenErrors.invalid();
        String secret=value.substring(33);
        if(!Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(secret)).equals(secret)) throw TokenErrors.invalid();
        return new RefreshCredential(value.substring(0,32),secret);
    }
    public String digest(String secret) {
        try {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}
        catch(GeneralSecurityException e){throw new IllegalStateException("Refresh digest unavailable.");}
    }
}
