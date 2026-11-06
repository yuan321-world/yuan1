package com.englishhero.practice;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.englishhero.practice.review.*;
import tools.jackson.databind.ObjectMapper;
class PracticeReviewRulesTest {
 final ObjectMapper json=new ObjectMapper();
 String translation(String kind,String source,String omissions,String summary){return json.writeValueAsString(java.util.Map.of("score",8,"summaryZh",summary,"summaryEn","The meaning needs correction.","dimensions",java.util.List.of(java.util.Map.of("name","信息传达","level","FAIR"),java.util.Map.of("name","语法准确","level","GOOD"),java.util.Map.of("name","表达自然","level","GOOD")),"annotations",java.util.List.of(java.util.Map.of("kind",kind,"severity","ERROR","quote","are required to join","occurrence",1,"replacement","join voluntarily","reasonZh","自愿不是强制","reasonEn","Voluntary is not compulsory.","sourceQuote",source)),"omissions",json.readTree(omissions),"revisedText","Residents join voluntarily."));}
 @Test void translationQualityChecksRejectInventedSentenceCountsAndDuplicateMeaningOmissions(){
  var validator=new PracticeReviewResultValidator(json);var source=new PracticeReviewDtos.Source("SUBJECTIVE","1","TRANSLATION","1","居民自愿参加。志愿者讲故事。","","Residents are required to join.");
  String duplicate="""
   [{"sourceQuote":"居民自愿参加","reasonZh":"信息遗漏","reasonEn":"Missing information.","suggestion":"Residents participate voluntarily."}]
   """;
  var grounded=validator.validate(source,translation("meaning","居民自愿参加","[]","后三句全部缺失"));assertThat(grounded.summaryZh()).isEqualTo("本次反馈列出 1 项需修正表达、0 项待补充信息，请结合下方原文定位核对。");assertThat(grounded.annotations()).hasSize(1);assertThat(grounded.score()).isEqualTo(8);
  assertThat(validator.validate(source,translation("meaning","居民自愿参加","[]","存在误译").replace("The meaning needs correction.","Three sentences are missing.")).summaryEn()).isEqualTo("Corrections listed: 1; missing-information items listed: 0. Check the source-linked details below.");
  assertThatThrownBy(()->validator.validate(source,translation("meaning","居民自愿参加",duplicate,"自愿被误译为强制"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->validator.validate(source,translation("meaning","","[]","自愿被误译为强制"))).isInstanceOf(IllegalArgumentException.class);
  assertThat(validator.validate(source,translation("meaning","居民自愿参加","[]","自愿被误译为强制")).annotations()).hasSize(1);
 }
 @Test void validatesScoresAndActualQuotesInsteadOfTrustingJson(){
  var validator=new PracticeReviewResultValidator(new ObjectMapper());
  String valid="""
   {"score":11,"summaryZh":"主谓一致需修改","summaryEn":"Check agreement.","dimensions":[{"name":"内容","level":"GOOD"},{"name":"结构","level":"GOOD"},{"name":"语言","level":"IMPROVE"}],"annotations":[{"kind":"grammar","severity":"ERROR","quote":"Students finds","occurrence":1,"replacement":"Students find","reasonZh":"复数主语","reasonEn":"Plural subject."}],"omissions":[],"revisedText":"Students find it useful."}
   """;
  assertThat(validator.validate("Students finds it useful.","Write an essay.",valid).score()).isEqualTo(11);
  assertThatThrownBy(()->validator.validate("Other text","Prompt",valid)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->validator.validate("Students finds it useful.","Prompt",valid.replace("\"score\":11","\"score\":16"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->validator.validate("Students finds it useful.","Prompt","{}")).isInstanceOf(IllegalArgumentException.class);
 }
}
