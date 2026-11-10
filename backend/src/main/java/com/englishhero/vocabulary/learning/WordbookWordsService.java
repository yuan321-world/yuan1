package com.englishhero.vocabulary.learning;

import java.time.Instant;
import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.englishhero.identity.*;
import com.englishhero.reading.ArticleRules;
import com.englishhero.web.BusinessException;
import com.englishhero.dictionary.DictionaryDtos.Sense;

/** Browsing never allocates a learning round or changes the selected source. */
@Service
public class WordbookWordsService {
 public record Counts(int total,int unlearned,int reviewing,int proficient){}
 public record Word(String entryId,String lemma,String meaning,String state,Instant dueAt,boolean reviewActive,String collectionId,boolean collected){}
 public record Words(String title,Counts counts,List<Word> items,int page,int pageSize,long total){}
 public record ProficiencyResult(Word word,Counts counts){}
 private final LearningMapper db;
 private final WordbookMapper books;
 private final IdentityService identities;
 public WordbookWordsService(LearningMapper db,WordbookMapper books,IdentityService identities){this.db=db;this.books=books;this.identities=identities;}
 private String title(long user,String book){
  if("personal".equals(book))return "我的阅读生词";
  long id=ArticleRules.id(book,"bookId");
  return books.books(user,null).stream().filter(b->b.id().equals(Long.toString(id))).findFirst()
   .orElseThrow(()->new BusinessException(404,"WORD_BOOK_UNAVAILABLE","词书不存在或已下架")).title();
 }
 private static String stateSql(){return "CASE WHEN w.proficient=TRUE THEN 'PROFICIENT' WHEN w.stage>0 THEN 'REVIEW' ELSE 'NEW' END";}
 private String from(boolean personal){return " FROM dictionary_entry d LEFT JOIN vocabulary_learning_word w ON w.entry_id=d.id AND w.user_id=? LEFT JOIN user_word u ON u.entry_id=d.id AND u.user_id=?"+(personal?" WHERE w.personal_enrolled=TRUE":" JOIN vocabulary_wordbook_entry be ON be.entry_id=d.id WHERE be.book_id=?");}
 private ArrayList<Object> args(long user,String book){var args=new ArrayList<Object>();args.add(user);args.add(user);if(!book.equals("personal"))args.add(Long.parseLong(book));return args;}
 private Word word(ResultSet r)throws SQLException{
  var senses=List.of(db.decode(r.getString("senses"),Sense[].class));
  String meaning=senses.stream().filter(s->s.definitionZh()!=null&&!s.definitionZh().isBlank()).map(s->Objects.toString(s.partOfSpeech(),"")+" "+s.definitionZh()).limit(2).reduce((a,b)->a+"；"+b).orElse("").strip();
  return new Word(r.getString("id"),r.getString("lemma"),meaning,r.getString("state"),r.getTimestamp("due_at")==null?null:r.getTimestamp("due_at").toInstant(),r.getBoolean("review_active"),r.getString("collection_id"),r.getBoolean("collected"));
 }
 private static String columns(){return "SELECT d.id,d.lemma,d.senses,"+stateSql()+" state,w.due_at,w.active review_active,u.id collection_id,u.active collected";}
 @Transactional(readOnly=true)
 public Words list(SessionPrincipal actor,String book,String query,String state,boolean collected,String sort,int page,int size){
  identities.current(actor);long user=actor.userId();String title=title(user,book);ArticleRules.page(page);ArticleRules.pageSize(size);
  if(!Set.of("ALL","NEW","REVIEW","PROFICIENT").contains(state))throw BusinessException.invalid("status","请选择有效学习状态");
  if(!Set.of("BOOK","ALPHA").contains(sort))throw BusinessException.invalid("sort","请选择有效排序");
  String q=Objects.toString(query,"").strip();if(q.length()>80)throw BusinessException.invalid("q","搜索内容不能超过80字");
  boolean personal=book.equals("personal");String base=from(personal);var args=args(user,book);
  var counts=db.jdbc().queryForObject("SELECT COUNT(*),COALESCE(SUM("+stateSql()+"='NEW'),0),COALESCE(SUM("+stateSql()+"='REVIEW'),0),COALESCE(SUM("+stateSql()+"='PROFICIENT'),0)"+base,
   (r,n)->new Counts(r.getInt(1),r.getInt(2),r.getInt(3),r.getInt(4)),args.toArray());
  String filter="";
  if(!state.equals("ALL")){filter+=" AND "+stateSql()+"=?";args.add(state);}
  if(collected)filter+=" AND u.active=TRUE";
  if(!q.isEmpty()){
   // Literal search: user '%' and '_' are not wildcard operators.
   String pattern="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
   filter+=" AND (LOWER(d.lemma) LIKE ? ESCAPE '!' OR LOWER(CAST(JSON_EXTRACT(d.senses,'$[*].definitionZh') AS CHAR)) LIKE ? ESCAPE '!')";args.add(pattern);args.add(pattern);
  }
  long total=db.count("SELECT COUNT(*)"+base+filter,args.toArray());
  String order=sort.equals("ALPHA")?"d.lemma,d.id":personal?"w.id,d.id":"be.sort_order,d.id";
  args.add(size);args.add((page-1)*size);
  var rows=db.jdbc().query(columns()+base+filter+" ORDER BY "+order+" LIMIT ? OFFSET ?",(r,n)->word(r),args.toArray());
  return new Words(title,counts,rows,page,size,total);
 }
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public ProficiencyResult proficiency(SessionPrincipal actor,String book,String entry,LearningDtos.ProficiencyInput input){
  identities.current(actor);long user=actor.userId();db.lock(user);title(user,book);long id=ArticleRules.id(entry,"entryId");
  if(input==null||input.proficient()==null)throw BusinessException.invalid("proficient","请选择熟练状态");
  boolean personal=book.equals("personal");long present=personal?db.count("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=? AND entry_id=? AND personal_enrolled=TRUE",user,id):db.count("SELECT COUNT(*) FROM vocabulary_wordbook_entry WHERE book_id=? AND entry_id=?",book,id);
  if(present==0)throw new BusinessException(404,"LEARNING_NOT_FOUND","该词不在此词书中");
  if(input.proficient())db.jdbc().update("INSERT INTO vocabulary_learning_word(user_id,entry_id,personal_enrolled,proficient) VALUES(?,?,FALSE,TRUE) ON DUPLICATE KEY UPDATE proficient=TRUE,revision=revision+1",user,id);
  else db.jdbc().update("UPDATE vocabulary_learning_word SET proficient=FALSE,revision=revision+1 WHERE user_id=? AND entry_id=?",user,id);
  var args=args(user,book);args.add(id);
  var word=db.jdbc().queryForObject(columns()+from(personal)+" AND d.id=?",(r,n)->word(r),args.toArray());
  var counts=list(actor,book,"","ALL",false,"BOOK",1,1).counts();
  return new ProficiencyResult(word,counts);
 }
}
