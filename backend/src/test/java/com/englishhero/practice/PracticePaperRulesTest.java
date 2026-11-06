package com.englishhero.practice;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PracticePaperRulesTest {
 List<PracticePaperRules.Component> complete(){
  var result=new ArrayList<PracticePaperRules.Component>();int i=0;
  for(var s:PracticePaperRules.SLOTS)result.add(new PracticePaperRules.Component(s,""+(++i),"CET4","PAST",2024,6,PracticePaperRules.type(s),PracticePaperRules.count(s)));
  return result;
 }
 @Test void requiresExactlySevenDistinctCorrectComponents(){
  assertThat(PracticePaperRules.validateComponents("CET4",2024,6,complete())).hasSize(7);
  var missing=complete();missing.removeFirst();assertThatThrownBy(()->PracticePaperRules.validateComponents("CET4",2024,6,missing)).isInstanceOf(RuntimeException.class);
  var duplicate=complete();duplicate.set(1,duplicate.getFirst());assertThatThrownBy(()->PracticePaperRules.validateComponents("CET4",2024,6,duplicate)).isInstanceOf(RuntimeException.class);
  var wrong=complete();wrong.set(1,new PracticePaperRules.Component("LISTENING","2","CET4","PAST",2024,6,"LISTENING",24));assertThatThrownBy(()->PracticePaperRules.validateComponents("CET4",2024,6,wrong)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->PracticePaperRules.validateComponents("CET4",2025,6,complete())).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->PracticePaperRules.validateComponents("CET6",2024,6,complete())).isInstanceOf(RuntimeException.class);
 }
 @Test void answersRejectForeignQuestionsInvalidLettersAndDuplicateCloze(){
  var ids=List.of("11","12");
  assertThat(PracticePaperRules.choices("CLOZE",Map.of("11","A"),ids,List.of())).containsEntry("11","A");
  assertThatThrownBy(()->PracticePaperRules.choices("CLOZE",Map.of("11","A","12","A"),ids,List.of())).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->PracticePaperRules.choices("DETAILED",Map.of("999","B"),ids,List.of())).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->PracticePaperRules.choices("LISTENING",Map.of("11","O"),ids,List.of())).isInstanceOf(RuntimeException.class);
  assertThat(PracticePaperRules.choices("MATCH",Map.of("11","B","12","B"),ids,List.of("A","B"))).hasSize(2);
  assertThatThrownBy(()->PracticePaperRules.choices("MATCH",Map.of("11","C"),ids,List.of("A","B"))).isInstanceOf(RuntimeException.class);
 }
}
