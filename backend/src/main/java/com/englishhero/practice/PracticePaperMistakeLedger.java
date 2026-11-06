package com.englishhero.practice;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class PracticePaperMistakeLedger {
 private final PracticePaperMapper papers;private final PracticePaperContentService content;private final PracticePaperMistakeMapper mistakes;private final PracticeAttemptMapper attempts;
 public PracticePaperMistakeLedger(PracticePaperMapper papers,PracticePaperContentService content,PracticePaperMistakeMapper mistakes,PracticeAttemptMapper attempts){this.papers=papers;this.content=content;this.mistakes=mistakes;this.attempts=attempts;}
 public void recordReview(PracticeAttemptMapper.Row review,java.util.List<com.englishhero.reading.quiz.QuizMapper.Question> questions,Instant at){
  var origin=attempts.paperSource(review.id());if(origin==null)throw new IllegalStateException("Paper review source required");
  var source=papers.run(review.user(),origin.attemptId(),false);var component=papers.components(source.version()).stream().filter(c->c.slot().equals(origin.slot())&&c.setVersionId().equals(""+review.setVersion())).findFirst().orElseThrow(()->new IllegalStateException("Frozen review component mismatch"));
  var v=content.version(component.setVersionId());var full=content.questions(v);var saved=attempts.answers(review.id());
  for(var answer:saved){int index=java.util.stream.IntStream.range(0,full.size()).filter(i->full.get(i).id()==answer.question()).findFirst().orElseThrow();var q=full.get(index);
   mistakes.assess(new PracticePaperMistakeMapper.Assessment(review.user(),v.setId(),v.id(),v.quizVersionId(),q.id(),origin.slot(),PracticePaperMistakeRules.originalNumber(origin.slot(),index),source.id(),null,review.id(),answer.draft(),Objects.equals(answer.draft(),q.answer()),at));
  }
 }
 public void recordPaper(PracticePaperMapper.Run run,List<PracticePaperDtos.ResponseInput> responses,Instant at){
  for(var c:papers.components(run.version())){
   var v=content.version(c.setVersionId());if(PracticeSubjectiveRules.isSubjective(v.readingType()))continue;
   var response=responses.stream().filter(r->r.slot().equals(c.slot())).findFirst().orElseThrow();var qs=content.questions(v);
   for(int i=0;i<qs.size();i++){var q=qs.get(i);String chosen=response.choices().get(""+q.id());
    mistakes.assess(new PracticePaperMistakeMapper.Assessment(run.user(),v.setId(),v.id(),v.quizVersionId(),q.id(),c.slot(),PracticePaperMistakeRules.originalNumber(c.slot(),i),run.id(),run.id(),null,chosen,Objects.equals(chosen,q.answer()),at));
   }
  }
 }
}
