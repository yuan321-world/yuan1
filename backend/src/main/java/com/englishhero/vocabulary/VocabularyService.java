package com.englishhero.vocabulary;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englishhero.identity.*;
import com.englishhero.reading.*;
import com.englishhero.dictionary.*;
import com.englishhero.dictionary.DictionaryDtos.CollectionState;
import com.englishhero.web.BusinessException;
import static com.englishhero.vocabulary.VocabularyDtos.*;
import static com.englishhero.reading.ReadingDtos.*;
@Service
public class VocabularyService implements CollectionStateProvider {
    public record ArticleWord(WordDto word,SourceDto source){}
    private final VocabularyMapper words;private final ReadingMapper reading;private final AdminArticleService versions;private final DictionaryProvider dictionary;private final IdentityService identities;private final com.englishhero.vocabulary.learning.LearningMapper learning;private final WordDifficultyService difficulty;
    public VocabularyService(VocabularyMapper words,ReadingMapper reading,AdminArticleService versions,DictionaryProvider dictionary,IdentityService identities,com.englishhero.vocabulary.learning.LearningMapper learning,WordDifficultyService difficulty){this.words=words;this.reading=reading;this.versions=versions;this.dictionary=dictionary;this.identities=identities;this.learning=learning;this.difficulty=difficulty;}
    @Transactional public CollectResult collectFromLearning(SessionPrincipal actor,LearningCollectionInput input){
        identities.current(actor);if(input==null)throw BusinessException.invalid("entryId","请选择学习中的词条");
        long sid=ArticleRules.id(input.sessionId(),"sessionId"),entry=ArticleRules.id(input.entryId(),"entryId");words.lockOwner(actor.userId());
        var session=learning.session(actor.userId(),sid);if(session==null)throw new BusinessException(404,"LEARNING_NOT_FOUND","学习记录不存在或不属于当前账号");
        var item=learning.items(actor.userId(),sid).stream().filter(i->i.snapshot().entry().entryId().equals(Long.toString(entry))).findFirst().orElseThrow(()->new BusinessException(404,"LEARNING_NOT_FOUND","当前词条不属于此学习记录"));
        Long word=words.wordIdCurrent(actor.userId(),entry);boolean created=word==null;
        if(created){words.create(actor.userId(),entry);word=words.wordIdCurrent(actor.userId(),entry);}else words.active(actor.userId(),word,true);
        var source=item.snapshot().source();if(source!=null)words.source(word,Long.parseLong(source.articleId()),Long.parseLong(source.articleVersionId()),source.sentenceKey(),source.startOffset(),source.endOffset(),source.surfaceForm(),source.sentence());
        var row=words.jdbc().queryForMap("SELECT source_key,source_title FROM vocabulary_learning_session WHERE user_id=? AND id=?",actor.userId(),sid);
        String key=Objects.toString(row.get("source_key"),"PERSONAL"),title=Objects.toString(row.get("source_title"),"学习中收藏"),kind=key.startsWith("BOOK:")?"BOOK":"LEARNING";
        words.jdbc().update("INSERT INTO user_word_learning_origin(word_id,session_id,source_key,source_kind,title) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",word,sid,key,kind,title);
        return new CollectResult(words.wordCurrent(actor.userId(),word),created);
    }
    @Transactional public CollectResult collect(SessionPrincipal actor,CollectInput input){
        identities.current(actor);if(input==null)throw BusinessException.invalid("entryId","请选择词条");
        long vid=ArticleRules.id(input.articleVersionId(),"articleVersionId"),entry=ArticleRules.id(input.entryId(),"entryId");
        var progress=reading.progress(actor.userId(),vid);if(progress==null)throw new BusinessException(404,"READING_NOT_FOUND","请先开始阅读此文章");
        var version=versions.version(Long.parseLong(progress.articleId()),vid);
        var sentence=version.content().paragraphs().stream().flatMap(p->p.sentences().stream()).filter(s->s.key().equals(input.sentenceKey())).findFirst().orElseThrow(()->BusinessException.invalid("sentenceKey","请重新选择当前文章中的单词"));
        Integer start=input.startOffset(),end=input.endOffset();String text=sentence.text();
        if(start==null||end==null||start<0||end<=start||end>text.length())throw BusinessException.invalid("startOffset","选词位置无效，请重新选择");
        boolean whole=ArticleContent.WORD.matcher(text).results().anyMatch(m->m.start()==start&&m.end()==end);
        if(!whole)throw BusinessException.invalid("startOffset","请选择一个完整英文单词");
        String surface=text.substring(start,end),normalized=DictionaryRules.normalize(surface);
        java.util.List<com.englishhero.dictionary.DictionaryDtos.PublicEntry> candidates;
        try{candidates=dictionary.lookup(normalized);}catch(RuntimeException e){throw new BusinessException(503,"DICTIONARY_UNAVAILABLE","词典暂时不可用，请稍后重试");}
        if(candidates.stream().noneMatch(e->e.entryId().equals(input.entryId())))throw BusinessException.invalid("entryId","词条与选中单词不匹配");
        words.lockOwner(actor.userId());Long id=words.wordIdCurrent(actor.userId(),entry);boolean created=id==null;
        if(created){words.create(actor.userId(),entry);id=words.wordIdCurrent(actor.userId(),entry);}else words.active(actor.userId(),id,true);
        words.source(id,Long.parseLong(progress.articleId()),vid,sentence.key(),start,end,surface,text);
        return new CollectResult(words.wordCurrent(actor.userId(),id),created);
    }
    @Transactional public CollectResult collectFromDictionary(SessionPrincipal actor, DictionaryCollectionInput input) {
        identities.current(actor);
        if (input == null) throw BusinessException.invalid("entryId", "请选择词条");
        String word = DictionaryRules.normalize(input.word());
        long entry = ArticleRules.id(input.entryId(), "entryId");
        if (dictionary.lookup(word).stream().noneMatch(candidate -> candidate.entryId().equals(input.entryId())))
            throw BusinessException.invalid("entryId", "词条与查询内容不匹配");
        words.lockOwner(actor.userId());
        Long id = words.wordIdCurrent(actor.userId(), entry);
        boolean created = id == null;
        if (created) {
            words.create(actor.userId(), entry);
            id = words.wordIdCurrent(actor.userId(), entry);
        } else {
            words.active(actor.userId(), id, true);
        }
        words.jdbc().update("INSERT INTO user_word_learning_origin(word_id,source_key,source_kind,title) VALUES(?,'DICTIONARY','DICTIONARY','查词收藏') ON DUPLICATE KEY UPDATE id=id", id);
        return new CollectResult(words.wordCurrent(actor.userId(), id), created);
    }
    @Transactional(readOnly=true) public PageDto<WordDto> list(SessionPrincipal actor,int page,int size){identities.current(actor);ArticleRules.page(page);ArticleRules.pageSize(size);return new PageDto<>(words.list(actor.userId(),page,size),page,size,words.count(actor.userId()));}
    @Transactional(readOnly=true) public WordDetail detail(SessionPrincipal actor,String id,int page,int size){identities.current(actor);ArticleRules.page(page);ArticleRules.pageSize(size);long word=ArticleRules.id(id,"wordId");var owned=owned(actor,word);return new WordDetail(owned,new PageDto<>(words.sources(word,page,size),page,size,words.sourceCount(word)),words.origins(word),owned.learning()==null?null:difficulty.state(actor,owned.learning().id()));}
    @Transactional(readOnly=true) public PageDto<WordDto> search(SessionPrincipal actor,int page,int size,String query,String status){identities.current(actor);ArticleRules.page(page);ArticleRules.pageSize(size);query=Objects.toString(query,"").trim();status=Objects.toString(status,"ALL");if(query.length()>80)throw BusinessException.invalid("q","搜索内容最多80个字符");if(!Set.of("ALL","UNJOINED","LEARNING","PROFICIENT","DUE","DIFFICULT").contains(status))throw BusinessException.invalid("status","请选择有效学习状态");var ids=words.matching(actor.userId(),query,status,page,size);var rows=ids.stream().map(id->words.word(actor.userId(),id)).toList();return new PageDto<>(rows,page,size,words.matchingCount(actor.userId(),query,status));}
    @Transactional public void cancel(SessionPrincipal actor,String id){identities.current(actor);long word=ArticleRules.id(id,"wordId");words.lockOwner(actor.userId());if(words.wordCurrent(actor.userId(),word)==null)throw new BusinessException(404,"WORD_NOT_FOUND","收藏记录不存在");words.active(actor.userId(),word,false);}
    private WordDto owned(SessionPrincipal actor,long id){var owned=words.word(actor.userId(),id);if(owned==null)throw new BusinessException(404,"WORD_NOT_FOUND","收藏记录不存在");return owned;}
    @Transactional(readOnly=true) public PageDto<ArticleWord> forVersion(SessionPrincipal actor,String articleId,String versionId,int page,int size){
        identities.current(actor);long aid=ArticleRules.id(articleId,"articleId"),vid=ArticleRules.id(versionId,"versionId");ArticleRules.page(page);ArticleRules.pageSize(size);
        var progress=reading.progress(actor.userId(),vid);if(progress==null||!progress.articleId().equals(Long.toString(aid)))throw new BusinessException(404,"READING_NOT_FOUND","尚无此文章的本人阅读记录");
        var items=words.forVersion(actor.userId(),vid,page,size).stream().map(w->new ArticleWord(w,words.versionSource(Long.parseLong(w.id()),vid))).toList();
        return new PageDto<>(items,page,size,words.versionCount(actor.userId(),vid));
    }
    @Override public Map<String,CollectionState> states(SessionPrincipal actor,List<String> entries){
        var result=new HashMap<String,CollectionState>();for(String entry:entries){Long id=words.wordId(actor.userId(),Long.parseLong(entry));if(id!=null){var word=words.word(actor.userId(),id);result.put(entry,new CollectionState(word.id(),word.active()));}}return result;
    }
}
