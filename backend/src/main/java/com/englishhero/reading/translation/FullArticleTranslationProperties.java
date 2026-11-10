package com.englishhero.reading.translation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "english-hero.reading.translation.full")
public class FullArticleTranslationProperties {
    private boolean enabled;
    private boolean seedPublished;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isSeedPublished() { return seedPublished; }
    public void setSeedPublished(boolean seedPublished) { this.seedPublished = seedPublished; }
}
