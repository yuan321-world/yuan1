package com.englishhero.identity.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "english-hero.email")
public class EmailProperties {
    private boolean enabled;
    private String hmacSecret = "";
    private String from = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret == null ? "" : hmacSecret; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from == null ? "" : from; }
}
