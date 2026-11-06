package com.englishhero.identity.token;

import java.nio.file.*;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("english-hero.auth")
public class AuthProperties {
    private String namespace, privateKey, publicKey, hmacSecret, kid, issuer, audience;
    private List<String> allowedOrigins = List.of();
    private boolean secure;
    public void validate() {
        if (namespace == null || !namespace.matches("eh:(dev|test|prod):auth:v[1-9][0-9]*")
                || privateKey == null || publicKey == null || !Files.isRegularFile(Path.of(privateKey))
                || !Files.isRegularFile(Path.of(publicKey)) || hmacSecret == null || hmacSecret.length() < 43
                || kid == null || !kid.matches("[a-zA-Z0-9_-]{1,64}") || issuer == null || issuer.isBlank()
                || audience == null || audience.isBlank() || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("Auth requires isolated namespace, stable keys, HMAC secret and allowed origins; see auth setup.");
        }
        for (var origin : allowedOrigins) {
            var uri = java.net.URI.create(origin);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getScheme().equals("https") || (!secure && uri.getScheme().equals("http")))
                    || !(uri.getPath().isEmpty())) throw new IllegalStateException("Invalid auth origin.");
        }
    }
    public String getNamespace(){return namespace;} public void setNamespace(String v){namespace=v;}
    public String getPrivateKey(){return privateKey;} public void setPrivateKey(String v){privateKey=v;}
    public String getPublicKey(){return publicKey;} public void setPublicKey(String v){publicKey=v;}
    public String getHmacSecret(){return hmacSecret;} public void setHmacSecret(String v){hmacSecret=v;}
    public String getKid(){return kid;} public void setKid(String v){kid=v;}
    public String getIssuer(){return issuer;} public void setIssuer(String v){issuer=v;}
    public String getAudience(){return audience;} public void setAudience(String v){audience=v;}
    public List<String> getAllowedOrigins(){return allowedOrigins;} public void setAllowedOrigins(List<String> v){allowedOrigins=v;}
    public boolean isSecure(){return secure;} public void setSecure(boolean v){secure=v;}
}
