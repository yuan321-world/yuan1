package com.englishhero.reading.quiz;

import java.util.List;
public final class QuizDtos {
 private QuizDtos(){}
 public record QuestionInput(String key,String type,String stem,List<String> options,String correctOption,String explanation,Integer sortOrder){}
 public record Input(List<QuestionInput> questions,Integer expectedLockVersion){}
 public record PublicQuestion(String id,String key,String type,String stem,List<String> options){}
 public record PublicQuiz(String id,String versionId,String articleId,String articleVersionId,List<PublicQuestion> questions){}
 public record AdminQuiz(String id,String articleId,String articleVersionId,List<QuestionInput> draft,PublicQuiz published,String status,int revision,int lockVersion){}
 public record GuestStart(String quizVersionId){}
 public record GuestSession(String token,int expiresIn){}
 public record GuestSubmit(String token,String questionId,String choice){}
 public record Feedback(String questionId,String choice,boolean correct,String correctOption,String explanation,boolean completed,int correctCount,int total){}
}
