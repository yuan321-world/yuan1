package com.englishhero.practice;
import java.time.Instant;
import java.util.*;
import com.englishhero.reading.ReadingDtos.ArticleVersionDto;
import com.englishhero.reading.quiz.QuizDtos.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
public final class PracticeDtos {
 public record ListeningGroup(String key,String title,List<String> questionKeys){}
 public record ListeningData(String audioAssetId,List<ListeningGroup> groups,Boolean audioReviewed){}
 public record PlaybackState(double positionSeconds,double rate){}

 private PracticeDtos(){}
 public record Metadata(String title,String titleZh,String sourceKind,String topic,Integer year,Integer month,Integer paperNo,String sourceName,String sourceUrl,String usageNote){}
 public record DraftInput(String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,Integer expectedLockVersion,List<String> wordBank,ListeningData listening){
  public DraftInput(String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,Integer expectedLockVersion,List<String> wordBank){this(articleId,articleVersionId,examCode,readingType,metadata,questions,expectedLockVersion,wordBank,null);}
  public DraftInput(String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,Integer expectedLockVersion){this(articleId,articleVersionId,examCode,readingType,metadata,questions,expectedLockVersion,null);}
 }
 public record MaterialInput(String requestId,String body,Metadata metadata,List<QuestionInput> questions,Integer expectedLockVersion,String readingType,List<String> wordBank,ListeningData listening){
  public MaterialInput(String requestId,String body,Metadata metadata,List<QuestionInput> questions,Integer expectedLockVersion,String readingType,List<String> wordBank){this(requestId,body,metadata,questions,expectedLockVersion,readingType,wordBank,null);}
  public MaterialInput(String requestId,String body,Metadata metadata,List<QuestionInput> questions,Integer expectedLockVersion){this(requestId,body,metadata,questions,expectedLockVersion,null,null);}
 }
 public record DraftState(Metadata metadata,Map<String,String> reviews,String sourceReview,String readingType,List<String> wordBank,ListeningData listening){
  public DraftState(Metadata metadata,Map<String,String> reviews,String sourceReview,String readingType,List<String> wordBank){this(metadata,reviews,sourceReview,readingType,wordBank,null);}
  public DraftState(Metadata metadata,Map<String,String> reviews,String sourceReview){this(metadata,reviews,sourceReview,"DETAILED",List.of());}
 }
 public record Check(String path,String questionKey,String message){}
 public record ReviewInput(Integer expectedLockVersion,String kind,String questionKey,Boolean reviewed){}
 public record AdminSet(String id,String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,List<String> reviewedQuestionKeys,boolean sourceReviewed,String status,int revision,int lockVersion,ArticleVersionDto article,List<Check> checks,boolean materialOnly,List<String> wordBank,ListeningData listening,@JsonInclude(JsonInclude.Include.NON_NULL) PracticeSubjectiveDtos.Content subjective){
  public AdminSet(String id,String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,List<String> reviewedQuestionKeys,boolean sourceReviewed,String status,int revision,int lockVersion,ArticleVersionDto article,List<Check> checks,boolean materialOnly,List<String> wordBank,ListeningData listening){this(id,articleId,articleVersionId,examCode,readingType,metadata,questions,reviewedQuestionKeys,sourceReviewed,status,revision,lockVersion,article,checks,materialOnly,wordBank,listening,null);}
  public AdminSet(String id,String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,List<String> reviewedQuestionKeys,boolean sourceReviewed,String status,int revision,int lockVersion,ArticleVersionDto article,List<Check> checks,boolean materialOnly,List<String> wordBank){this(id,articleId,articleVersionId,examCode,readingType,metadata,questions,reviewedQuestionKeys,sourceReviewed,status,revision,lockVersion,article,checks,materialOnly,wordBank,null);}
  public AdminSet(String id,String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,List<String> reviewedQuestionKeys,boolean sourceReviewed,String status,int revision,int lockVersion,ArticleVersionDto article,List<Check> checks,boolean materialOnly){this(id,articleId,articleVersionId,examCode,readingType,metadata,questions,reviewedQuestionKeys,sourceReviewed,status,revision,lockVersion,article,checks,materialOnly,List.of());}
 }
 public record PublishedVersion(int revision,Instant publishedAt,Metadata metadata,ArticleVersionDto article,List<QuestionInput> questions,String readingType,List<String> wordBank,ListeningData listening,@JsonInclude(JsonInclude.Include.NON_NULL) PracticeSubjectiveDtos.Content subjective){
  public PublishedVersion(int revision,Instant publishedAt,Metadata metadata,ArticleVersionDto article,List<QuestionInput> questions,String readingType,List<String> wordBank,ListeningData listening){this(revision,publishedAt,metadata,article,questions,readingType,wordBank,listening,null);}
  public PublishedVersion(int revision,Instant publishedAt,Metadata metadata,ArticleVersionDto article,List<QuestionInput> questions,String readingType,List<String> wordBank){this(revision,publishedAt,metadata,article,questions,readingType,wordBank,null);}
  public PublishedVersion(int revision,Instant publishedAt,Metadata metadata,ArticleVersionDto article,List<QuestionInput> questions){this(revision,publishedAt,metadata,article,questions,"DETAILED",List.of());}
 }
 @JsonInclude(JsonInclude.Include.NON_NULL) public record SetSummary(String id,String setVersionId,Metadata metadata,String examCode,String readingType,int questionCount,Instant publishedAt,String personalState,String currentAttemptId){}
 @JsonInclude(JsonInclude.Include.NON_NULL) public record SetDetail(String id,String setVersionId,Metadata metadata,String examCode,String readingType,int questionCount,Instant publishedAt,String personalState,String currentAttemptId,ArticleVersionDto article,List<String> wordBank,PracticeSubjectiveDtos.Prompt subjective){
  public SetDetail(String id,String setVersionId,Metadata metadata,String examCode,String readingType,int questionCount,Instant publishedAt,String personalState,String currentAttemptId,ArticleVersionDto article,List<String> wordBank){this(id,setVersionId,metadata,examCode,readingType,questionCount,publishedAt,personalState,currentAttemptId,article,wordBank,null);}
  public SetDetail(String id,String setVersionId,Metadata metadata,String examCode,String readingType,int questionCount,Instant publishedAt,String personalState,String currentAttemptId,ArticleVersionDto article){this(id,setVersionId,metadata,examCode,readingType,questionCount,publishedAt,personalState,currentAttemptId,article,List.of());}
 }
 public record StartInput(String setVersionId,String requestId){}
 public static final class Choice {
  private final String questionId;
  private final String choice;
  private final Boolean flagged;
  private final boolean choiceProvided;
  public Choice(String questionId,String choice){this(questionId,choice,null,true);}
  public Choice(String questionId,String choice,Boolean flagged){this(questionId,choice,flagged,true);}
  private Choice(String questionId,String choice,Boolean flagged,boolean choiceProvided){this.questionId=questionId;this.choice=choice;this.flagged=flagged;this.choiceProvided=choiceProvided;}
  @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
  public static Choice fromJson(Map<String,Object> value){
   Object rawChoice=value.get("choice");Object rawFlag=value.get("flagged");
   if(rawFlag!=null&&!(rawFlag instanceof Boolean))throw new IllegalArgumentException("flagged must be a boolean");
   return new Choice((String)value.get("questionId"),rawChoice==null?null:rawChoice.toString(),(Boolean)rawFlag,value.containsKey("choice"));
  }
  public String questionId(){return questionId;}
  public String choice(){return choice;}
  public Boolean flagged(){return flagged;}
  public boolean choiceProvided(){return choiceProvided;}
 }
 public record DraftAnswersInput(Integer expectedVersion,List<Choice> answers,String currentQuestionId,PlaybackState playback){
  public DraftAnswersInput(Integer expectedVersion,List<Choice> answers,String currentQuestionId){this(expectedVersion,answers,currentQuestionId,null);}}
 public record SubmitInput(Integer expectedVersion,String requestId,Boolean confirmUnanswered){}
 public record RetryInput(String requestId){}
 @JsonInclude(JsonInclude.Include.NON_NULL) public record Answer(String questionId,String draftChoice,String submittedChoice,Boolean correct,String correctOption,String explanation,Boolean flagged){
  public Answer(String questionId,String draftChoice,String submittedChoice,Boolean correct,String correctOption,String explanation){this(questionId,draftChoice,submittedChoice,correct,correctOption,explanation,null);}
 }
 public record PaperReviewSource(String attemptId,String slot,Map<String,Integer> originalNumbers){}
 @JsonInclude(JsonInclude.Include.NON_NULL) public record Attempt(String id,String setId,String setVersionId,String quizVersionId,String kind,String sourceAttemptId,String status,int version,String currentQuestionId,int total,ArticleVersionDto article,List<PublicQuestion> questions,List<Answer> answers,Integer correctCount,Instant startedAt,Instant completedAt,String title,String readingType,List<String> wordBank,ListeningData listening,PlaybackState playback,PaperReviewSource reviewSource){
  public Attempt(String id,String setId,String setVersionId,String quizVersionId,String kind,String sourceAttemptId,String status,int version,String currentQuestionId,int total,ArticleVersionDto article,List<PublicQuestion> questions,List<Answer> answers,Integer correctCount,Instant startedAt,Instant completedAt,String title,String readingType,List<String> wordBank,ListeningData listening,PlaybackState playback){this(id,setId,setVersionId,quizVersionId,kind,sourceAttemptId,status,version,currentQuestionId,total,article,questions,answers,correctCount,startedAt,completedAt,title,readingType,wordBank,listening,playback,null);}

  public Attempt(String id,String setId,String setVersionId,String quizVersionId,String kind,String sourceAttemptId,String status,int version,String currentQuestionId,int total,ArticleVersionDto article,List<PublicQuestion> questions,List<Answer> answers,Integer correctCount,Instant startedAt,Instant completedAt,String title,String readingType,List<String> wordBank){this(id,setId,setVersionId,quizVersionId,kind,sourceAttemptId,status,version,currentQuestionId,total,article,questions,answers,correctCount,startedAt,completedAt,title,readingType,wordBank,null,null);}
  public Attempt(String id,String setId,String setVersionId,String quizVersionId,String kind,String sourceAttemptId,String status,int version,String currentQuestionId,int total,ArticleVersionDto article,List<PublicQuestion> questions,List<Answer> answers,Integer correctCount,Instant startedAt,Instant completedAt,String title){this(id,setId,setVersionId,quizVersionId,kind,sourceAttemptId,status,version,currentQuestionId,total,article,questions,answers,correctCount,startedAt,completedAt,title,"DETAILED",List.of());}
 }
 @JsonInclude(JsonInclude.Include.NON_NULL) public record Mistake(String questionId,String setId,String setVersionId,String sourceAttemptId,String latestAttemptId,String status,int wrongCount,String stem,String correctOption,String explanation,String title,String readingType){
  public Mistake(String questionId,String setId,String setVersionId,String sourceAttemptId,String latestAttemptId,String status,int wrongCount,String stem,String correctOption,String explanation,String title){this(questionId,setId,setVersionId,sourceAttemptId,latestAttemptId,status,wrongCount,stem,correctOption,explanation,title,null);}
 }
 public record ImportInput(String requestId,String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,List<String> wordBank,ListeningData listening){
  public ImportInput(String requestId,String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions,List<String> wordBank){this(requestId,articleId,articleVersionId,examCode,readingType,metadata,questions,wordBank,null);}
  public ImportInput(String requestId,String articleId,String articleVersionId,String examCode,String readingType,Metadata metadata,List<QuestionInput> questions){this(requestId,articleId,articleVersionId,examCode,readingType,metadata,questions,null);}
  public DraftInput draft(){return new DraftInput(articleId,articleVersionId,examCode,readingType,metadata,questions,0,wordBank,listening);}
 }
 public record ImportValidation(boolean valid,Map<String,String> fieldErrors,int itemCount){}
 public record Query(String exam,String readingType,String source,String topic,String state,String status,Integer year,Integer month,Integer paperNo,String q,String sort,int page,int pageSize,String section){
  public Query(String exam,String readingType,String source,String topic,String state,String status,Integer year,Integer month,Integer paperNo,String q,String sort,int page,int pageSize){this(exam,readingType,source,topic,state,status,year,month,paperNo,q,sort,page,pageSize,null);}}
}
