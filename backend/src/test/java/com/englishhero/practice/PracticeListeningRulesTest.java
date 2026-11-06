package com.englishhero.practice;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
import static com.englishhero.practice.PracticeDtos.*;
class PracticeListeningRulesTest {
 @Test void wholeSetGroupsMustCoverEveryQuestionExactlyInOrder(){
  var questions=List.of(new QuestionInput("q1","LISTENING","1. Which option?",List.of("One","Two","Three","Four"),"A","Explanation",0),new QuestionInput("q2","LISTENING","2. Which option?",List.of("One","Two","Three","Four"),"B","Explanation",1));
  var good=new ListeningData("1",List.of(new ListeningGroup("news","News",List.of("q1","q2"))),false);
  assertThat(PracticeListeningRules.normalize(good,questions).groups()).hasSize(1);
  assertThatThrownBy(()->PracticeListeningRules.normalize(new ListeningData("1",List.of(new ListeningGroup("news","News",List.of("q2","q1"))),false),questions)).isInstanceOf(com.englishhero.web.BusinessException.class);
  assertThatThrownBy(()->PracticeListeningRules.normalize(new ListeningData("1",List.of(new ListeningGroup("news","News",List.of("q1","q1"))),false),questions)).isInstanceOf(com.englishhero.web.BusinessException.class);
 }
 @Test void playbackMustBeFiniteAndBounded(){
  assertThatThrownBy(()->PracticeListeningRules.playback(new PlaybackState(Double.NaN,1))).isInstanceOf(com.englishhero.web.BusinessException.class);
  assertThatThrownBy(()->PracticeListeningRules.playback(new PlaybackState(10,3))).isInstanceOf(com.englishhero.web.BusinessException.class);
  assertThat(PracticeListeningRules.playback(new PlaybackState(30,1.5)).positionSeconds()).isEqualTo(30);
 }
}
