package com.englishhero.practice;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PracticePaperMistakeRulesTest {
 @Test void missingSlotIsAValidationError(){
  assertThatThrownBy(()->PracticePaperMistakeRules.originalNumber(null,0))
   .isInstanceOfSatisfying(com.englishhero.web.BusinessException.class,error->{
    assertThat(error.status).isEqualTo(400);
    assertThat(error.code).isEqualTo("VALIDATION_ERROR");
    assertThat(error.fields).containsKey("slot");
   });
 }
 @Test void preservesOriginalNumbersAndRejectsSubjectiveOrOutOfRange(){
  assertThat(PracticePaperMistakeRules.originalNumber("DETAILED_2",0)).isEqualTo(51);
  assertThat(PracticePaperMistakeRules.originalNumber("DETAILED_2",4)).isEqualTo(55);
  assertThat(PracticePaperMistakeRules.originalNumber("LISTENING",24)).isEqualTo(25);
  assertThat(PracticePaperMistakeRules.originalNumber("CLOZE",0)).isEqualTo(26);
  assertThat(PracticePaperMistakeRules.originalNumber("MATCH",0)).isEqualTo(36);
  assertThatThrownBy(()->PracticePaperMistakeRules.originalNumber("WRITING",0)).isInstanceOf(com.englishhero.web.BusinessException.class);
  assertThatThrownBy(()->PracticePaperMistakeRules.originalNumber("DETAILED_1",5)).isInstanceOf(com.englishhero.web.BusinessException.class);
 }
 @Test void keepsSharedContextForListeningClozeAndMatching(){
  for(String type:new String[]{"LISTENING","CLOZE","MATCH"})assertThat(PracticePaperMistakeRules.fullReview(type)).isTrue();
  assertThat(PracticePaperMistakeRules.fullReview("DETAILED")).isFalse();
 }
}
