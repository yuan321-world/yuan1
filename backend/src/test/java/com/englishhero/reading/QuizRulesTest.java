package com.englishhero.reading;

import com.englishhero.reading.quiz.QuizRules;
import com.englishhero.reading.quiz.QuizDtos.*;
import com.englishhero.web.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class QuizRulesTest {
 QuestionInput q(String key,List<String> options,String answer,int order){return new QuestionInput(key,"DETAIL"," What helps? ",options,answer," Context from the article. ",order);}
 @Test void validatesAndSortsWithoutChangingStableKeys(){var valid=List.of("One","Two","Three","Four");var result=QuizRules.questions(new Input(List.of(q("later",valid,"B",5),q("first",valid,"A",1)),0));assertThat(result).extracting(QuestionInput::key).containsExactly("first","later");assertThat(result.getFirst().stem()).isEqualTo("What helps?");}
 @Test void rejectsDuplicateKeysOptionsAndInvalidCorrectChoice(){var valid=List.of("One","Two","Three","Four");assertThatThrownBy(()->QuizRules.questions(new Input(List.of(q("a",valid,"A",0),q("a",valid,"A",1)),0))).isInstanceOf(BusinessException.class);assertThatThrownBy(()->QuizRules.questions(new Input(List.of(q("a",List.of("One","One","Three","Four"),"A",0)),0))).isInstanceOf(BusinessException.class);assertThatThrownBy(()->QuizRules.questions(new Input(List.of(q("a",valid,"E",0)),0))).isInstanceOf(BusinessException.class);}
 @Test void rejectsUnboundedSetsAndMissingExplanation(){var valid=List.of("One","Two","Three","Four");assertThatThrownBy(()->QuizRules.questions(new Input(Collections.nCopies(11,q("a",valid,"A",0)),0))).isInstanceOf(BusinessException.class);var empty=new QuestionInput("a","DETAIL","Stem",valid,"A","",0);assertThatThrownBy(()->QuizRules.questions(new Input(List.of(empty),0))).isInstanceOf(BusinessException.class);}
}
