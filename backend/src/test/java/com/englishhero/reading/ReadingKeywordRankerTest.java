package com.englishhero.reading;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReadingKeywordRankerTest {
 static final Instant NOW=Instant.parse("2026-11-08T00:00:00Z");
 ReadingToolsService.Keyword word(String id,String lemma,String...forms){return new ReadingToolsService.Keyword(id,lemma,null,List.of(),"",forms.length==0?List.of(lemma):List.of(forms));}
 @Test void filtersFunctionAndProficientWordsButKeepsUnlearnedAndDueWords(){
  var candidates=List.of(word("1","can"),word("2","brain"),word("3","visual"),word("4","university"));
  var states=Map.of("2",new ReadingKeywordRanker.Learning(true,2,true,NOW.minusSeconds(1)),"3",new ReadingKeywordRanker.Learning(false,2,true,NOW.minusSeconds(1)));
  assertThat(ReadingKeywordRanker.select(candidates,states,ArticleContent.normalize("We can study brain visual university."),"Brain",NOW)).extracting(ReadingToolsService.Keyword::lemma).containsExactly("visual","university");
 }
 @Test void prefersTitleAndRepetitionWithinLearningPriorityAndRetainsExactForms(){
  var candidates=List.of(word("1","area"),word("2","brain","brain","brains"),word("3","visual"),word("4","library"));
  var states=Map.of("2",new ReadingKeywordRanker.Learning(false,2,true,NOW.plusSeconds(100)));
  var result=ReadingKeywordRanker.select(candidates,states,ArticleContent.normalize("Area brain brains visual visual library."),"The visual library",NOW);
  assertThat(result).extracting(ReadingToolsService.Keyword::lemma).containsExactly("visual","library","area","brain");
  assertThat(result.getLast().forms()).containsExactly("brain","brains");
 }
 @Test void lengthBasedCapNeverFillsWithExcludedWordsAndStableTiesFollowTextOrder(){
  var candidates=new ArrayList<ReadingToolsService.Keyword>();for(int i=0;i<30;i++)candidates.add(word(""+i,"word"+i));
  assertThat(ReadingKeywordRanker.select(candidates,Map.of(),ArticleContent.normalize("Text. ".repeat(400)),"Title",NOW)).hasSize(10);
  assertThat(ReadingKeywordRanker.select(candidates,Map.of(),ArticleContent.normalize("Text. ".repeat(700)),"Title",NOW)).hasSize(15);
  assertThat(ReadingKeywordRanker.select(candidates,Map.of(),ArticleContent.normalize("Text. ".repeat(1100)),"Title",NOW)).hasSize(20).extracting(ReadingToolsService.Keyword::entryId).containsExactlyElementsOf(candidates.subList(0,20).stream().map(ReadingToolsService.Keyword::entryId).toList());
  assertThat(ReadingKeywordRanker.select(List.of(word("1","the")),Map.of(),ArticleContent.normalize("The text."),"The",NOW)).isEmpty();
 }
}
