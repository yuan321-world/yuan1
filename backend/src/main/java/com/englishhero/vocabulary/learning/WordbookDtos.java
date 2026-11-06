package com.englishhero.vocabulary.learning;
import java.util.List;
public final class WordbookDtos {
 private WordbookDtos(){}
 public record Excluded(String word,String reason){}
 public record Manifest(int schemaVersion,String code,String dataVersion,String title,String sourceUrl,String notice,int originalCount,int availableCount,int missingPhoneticCount,List<Excluded> excluded,String entriesSha256){}
 public record Book(String id,String title,String dataVersion,String sourceUrl,String notice,int originalCount,int availableCount,int missingPhoneticCount,List<Excluded> excluded,String status,int learned){}
 public record Source(String kind,String bookId,String title,boolean available,String reason,Integer wordCount){}
 public record Selection(String kind,String bookId){}
}
