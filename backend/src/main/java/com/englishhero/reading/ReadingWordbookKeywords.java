package com.englishhero.reading;

import java.util.*;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.dictionary.*;
import com.englishhero.vocabulary.learning.WordbookMapper;

/** Matches only words actually present in the article; never enrolls or allocates study words. */
@Component
public class ReadingWordbookKeywords {
 public record Selection(String title,String kind,List<ReadingToolsService.Keyword> keywords){}
 private final JdbcTemplate jdbc;private final WordbookMapper books;private final ObjectMapper json;private final Clock clock;
 public ReadingWordbookKeywords(JdbcTemplate jdbc,WordbookMapper books,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.books=books;this.json=json;this.clock=clock;}
 public Selection select(long user,ReadingDtos.ArticleVersionDto version){
  var document=version.content();
  var source=books.selected(user);boolean personal=source.kind().equals("PERSONAL");
  var surfaces=new LinkedHashSet<String>();
  for(var paragraph:document.paragraphs())for(var sentence:paragraph.sentences()){
   var matcher=ArticleContent.WORD.matcher(sentence.text());while(matcher.find())if(matcher.group().length()<=64)surfaces.add(DictionaryRules.normalize(matcher.group()));
  }
  if(surfaces.isEmpty())return new Selection(source.title(),source.kind(),List.of());
  var entries=new LinkedHashMap<String,DictionaryDtos.PublicEntry>();var matches=new HashMap<String,Set<String>>();
  var tokens=new ArrayList<>(surfaces);Object member=personal?user:source.bookId();
  String membership=personal?"EXISTS (SELECT 1 FROM vocabulary_learning_word w WHERE w.entry_id=d.id AND w.user_id=? AND w.personal_enrolled=TRUE)":"EXISTS (SELECT 1 FROM vocabulary_wordbook_entry b WHERE b.entry_id=d.id AND b.book_id=?)";
  for(int start=0;start<tokens.size();start+=200){
   var batch=tokens.subList(start,Math.min(start+200,tokens.size()));String placeholders=String.join(",",Collections.nCopies(batch.size(),"?"));var args=new ArrayList<Object>();args.add(member);args.addAll(batch);args.add(member);args.addAll(batch);
   String columns="d.id,d.lemma,d.phonetic,d.senses,d.source_note,";
   String sql="SELECT "+columns+"LOWER(d.lemma) surface FROM dictionary_entry d WHERE "+membership+" AND LOWER(d.lemma) IN ("+placeholders+") UNION ALL SELECT "+columns+"LOWER(f.form) surface FROM dictionary_form f JOIN dictionary_entry d ON d.id=f.entry_id WHERE "+membership+" AND LOWER(f.form) IN ("+placeholders+")";
   jdbc.query(sql,(r,n)->{String id=r.getString("id");entries.putIfAbsent(id,new DictionaryDtos.PublicEntry(id,r.getString("lemma"),r.getString("phonetic"),List.of(json.readValue(r.getString("senses"),DictionaryDtos.Sense[].class)),r.getString("source_note")));matches.computeIfAbsent(id,key->new TreeSet<>()).add(r.getString("surface"));return id;},args.toArray());
  }
  var positions=new HashMap<String,Integer>();for(int i=0;i<tokens.size();i++)positions.put(tokens.get(i),i);
  var result=entries.values().stream().sorted(Comparator.comparingInt(e->matches.get(e.entryId()).stream().mapToInt(s->positions.getOrDefault(s,Integer.MAX_VALUE)).min().orElse(Integer.MAX_VALUE)))
   .map(e->new ReadingToolsService.Keyword(e.entryId(),e.lemma(),e.phonetic(),e.senses(),e.sourceNote(),List.copyOf(matches.get(e.entryId())))).toList();
  var learning=new HashMap<String,ReadingKeywordRanker.Learning>();
  var ids=result.stream().map(ReadingToolsService.Keyword::entryId).toList();
  for(int start=0;start<ids.size();start+=200){
   var batch=ids.subList(start,Math.min(start+200,ids.size()));var args=new ArrayList<Object>();args.add(user);args.addAll(batch);
   jdbc.query("SELECT entry_id,proficient,stage,active,due_at FROM vocabulary_learning_word WHERE user_id=? AND entry_id IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+")",(r,n)->{learning.put(r.getString("entry_id"),new ReadingKeywordRanker.Learning(r.getBoolean("proficient"),r.getInt("stage"),r.getBoolean("active"),r.getTimestamp("due_at")==null?null:r.getTimestamp("due_at").toInstant()));return 0;},args.toArray());
  }
  return new Selection(source.title(),source.kind(),ReadingKeywordRanker.select(result,learning,document,version.title(),clock.instant()));
 }
}
