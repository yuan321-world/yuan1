package com.englishhero.reading.translation;

import java.time.Instant;
import java.time.LocalDate;
import com.englishhero.reading.EditorialReadingCatalog;

public final class FullArticleTranslationDtos {
    private FullArticleTranslationDtos() {}

    public record Response(String status, int completed, int total, boolean configured,
                           EditorialReadingCatalog.Translation translation, String error) {}

    public record ChunkRow(String paragraphKey, int chunkIndex, String separatorBefore,
                           String status, String translatedText, String errorCode,
                           Instant retryAfter) {}

    public record PublishedVersion(long articleId, long versionId) {}

    public record ChunkJob(long id, long versionId, String contentHash, String paragraphKey,
                           int chunkIndex, String text, int attemptNo, int rateLimitAttempts,
                           LocalDate quotaMonth, String leaseToken, Instant leaseUntil) {}
}
