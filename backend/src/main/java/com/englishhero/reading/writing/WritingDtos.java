package com.englishhero.reading.writing;
import java.util.List;
public final class WritingDtos {
 private WritingDtos(){}
 public record Content(String expression,String meaningZh,List<String> keyPhrases,String structure,String exampleEn,String exampleZh,List<String> scenarios,Integer sortOrder){}
 public record Input(String expression,String meaningZh,List<String> keyPhrases,String structure,String exampleEn,String exampleZh,List<String> scenarios,Integer sortOrder,Integer expectedLockVersion){}
 public record AdminMaterial(String id,String articleId,String articleVersionId,Content draft,Content published,String status,int revision,int lockVersion){}
 public record PublishedMaterial(String id,String articleId,String articleVersionId,Content content,int revision){}
 public record FavoriteInput(Integer materialRevision){}
 public record ImitationInput(String text,Integer materialRevision,Integer expectedLockVersion){}
 public record SavedImitation(String text,int materialRevision,Content snapshot,int lockVersion){}
 public record Personal(String materialId,String articleId,String articleVersionId,String expression,boolean available,boolean favorite,PublishedMaterial material,SavedImitation imitation){}
}
