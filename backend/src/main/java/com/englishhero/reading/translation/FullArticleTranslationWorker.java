package com.englishhero.reading.translation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FullArticleTranslationWorker {
    private final FullArticleTranslationService service;
    private final FullArticleTranslationProperties properties;
    private final BaiduTranslationProperties baidu;
    private final AtomicBoolean working = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "reading-full-translation");
        thread.setDaemon(true);
        return thread;
    });

    public FullArticleTranslationWorker(FullArticleTranslationService service,
            FullArticleTranslationProperties properties, BaiduTranslationProperties baidu) {
        this.service = service;
        this.properties = properties;
        this.baidu = baidu;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (properties.isEnabled() && properties.isSeedPublished() && baidu.ready()) service.seedPublished();
    }

    @Scheduled(fixedDelay = 1200)
    public void tick() {
        if (!properties.isEnabled() || !baidu.ready() || !working.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                service.workOnce();
            } catch (RuntimeException ignored) {
                // Queue state is recoverable; never log source text, credentials, or upstream responses.
            } finally {
                working.set(false);
            }
        });
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
