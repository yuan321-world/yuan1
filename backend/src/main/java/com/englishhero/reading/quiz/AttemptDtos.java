package com.englishhero.reading.quiz;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.englishhero.reading.quiz.QuizDtos.*;
public final class AttemptDtos {
 private AttemptDtos(){}
 public record StartInput(String quizVersionId,String clientRequestId){}
 public record ChoiceInput(String choice,Integer expectedLockVersion){}
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record SavedAnswer(String questionId,String draftChoice,String submittedChoice,Boolean correct,String correctOption,String explanation,Instant submittedAt){}
 public record Summary(String id,String quizId,String quizVersionId,String articleId,String articleVersionId,int attemptNo,String status,Integer correctCount,int total,Instant startedAt,Instant completedAt){}
 public record Attempt(String id,String quizId,String quizVersionId,String articleId,String articleVersionId,int attemptNo,String status,Integer correctCount,int total,int lockVersion,Instant startedAt,Instant completedAt,PublicQuiz quiz,List<SavedAnswer> answers){}
 public record State(PublicQuiz published,Attempt current,Summary first,Summary latest){}
}
