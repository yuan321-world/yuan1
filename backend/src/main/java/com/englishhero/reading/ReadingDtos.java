package com.englishhero.reading;
import java.time.Instant;
import java.util.List;
public final class ReadingDtos {
    private ReadingDtos(){}
    public record CategoryDto(String id,String code,String name){}
    public record DraftInput(String categoryId,String title,String titleZh,String summary,String difficulty,String body,String author,String sourceType,String sourceUrl,String usageNote){}
    public record DraftUpdate(String categoryId,String title,String titleZh,String summary,String difficulty,String body,String author,String sourceType,String sourceUrl,String usageNote,Integer expectedLockVersion){
        public DraftInput input(){return new DraftInput(categoryId,title,titleZh,summary,difficulty,body,author,sourceType,sourceUrl,usageNote);}
    }
    public record ArticleVersionDto(String id,String articleId,int versionNo,String title,String titleZh,String summary,CategoryDto category,String difficulty,ArticleContent.Document content,int wordCount,int estimatedMinutes,String author,String sourceType,String sourceUrl,String usageNote){}
    public record AdminArticleDto(String id,String status,String draftVersionId,String publishedVersionId,int lockVersion,ArticleVersionDto draft){}
    public record PageDto<T>(List<T> items,int page,int pageSize,long total){}
    public record LockInput(Integer expectedLockVersion){}
    public record ProgressUpdate(String sentenceKey,Integer offset,Boolean completed,Integer expectedLockVersion){}
    public record ReadingHistoryDto(ArticleSummaryDto article,ProgressDto progress){}
    public record ProgressDto(String id,String articleId,String articleVersionId,String sentenceKey,int offset,String status,int lockVersion,Instant updatedAt,Instant completedAt){}
    public record ReadingDetailDto(ArticleVersionDto version,ProgressDto progress){}
    public record StartResult(ArticleVersionDto version,ProgressDto progress,boolean created){}
    public record ArticleSummaryDto(String id,String versionId,String title,String titleZh,String summary,CategoryDto category,String difficulty,int wordCount,int estimatedMinutes,String author,String sourceType,String sourceUrl,String usageNote,ProgressDto progress){}
}
