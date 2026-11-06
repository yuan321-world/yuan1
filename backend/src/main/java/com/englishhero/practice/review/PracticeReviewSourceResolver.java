package com.englishhero.practice.review;
import org.springframework.stereotype.Component;
import com.englishhero.identity.SessionPrincipal;import com.englishhero.practice.*;import com.englishhero.web.BusinessException;
import static com.englishhero.practice.review.PracticeReviewDtos.*;
@Component
public class PracticeReviewSourceResolver {
 private final PracticeSubjectiveService single;private final PracticePaperAttemptService paper;private final PracticePaperMapper papers;
 public PracticeReviewSourceResolver(PracticeSubjectiveService single,PracticePaperAttemptService paper,PracticePaperMapper papers){this.single=single;this.paper=paper;this.papers=papers;}
 public Source resolve(SessionPrincipal actor,String type,String id,String slot){
  if(!"WRITING".equals(slot)&&!"TRANSLATION".equals(slot))throw BusinessException.invalid("slot","请选择写作或翻译");
  Source s;
  if("SUBJECTIVE".equals(type)){var a=single.get(actor,id);done(a.status());if(!slot.equals(a.readingType()))throw BusinessException.invalid("slot","题型不匹配");s=new Source(type,a.id(),slot,a.setVersionId(),a.prompt().prompt(),a.prompt().requirements(),a.answerText());}
  else if("PAPER".equals(type)){var a=paper.get(actor,id);done(a.status());var c=a.components().stream().filter(x->slot.equals(x.slot())).findFirst().orElseThrow(()->BusinessException.invalid("slot","部分不存在"));s=new Source(type,a.id(),slot,papers.components(Long.parseLong(a.paperVersionId())).stream().filter(x->slot.equals(x.slot())).findFirst().orElseThrow().setVersionId(),c.prompt().prompt(),c.prompt().requirements(),c.text());}
  else throw BusinessException.invalid("sourceType","请选择有效练习来源");
  if(s.text()==null||s.text().isBlank()||s.text().length()>20000||!s.text().matches("(?s).*[A-Za-z].*"))throw BusinessException.invalid("attemptId","请先提交非空英文作答");return s;
 }
 private void done(String status){if(!"COMPLETED".equals(status))throw BusinessException.invalid("attemptId","请先提交作答");}
}
