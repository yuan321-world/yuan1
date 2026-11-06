package com.englishhero.reading;
import java.util.*;
public final class ArticleImportDtos {
    private ArticleImportDtos(){}
    public record ImportPayload(Integer schemaVersion,List<ReadingDtos.DraftInput> articles){}
    public record ImportValidation(boolean valid,Map<String,String> fieldErrors,int itemCount){}
    public record ImportResult(String batchId,List<String> articleIds){}
}
