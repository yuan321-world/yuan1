package com.englishhero.reading;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.englishhero.reading.ReadingDtos.*;
@Repository
public class ReadingMapper {
    private final JdbcTemplate jdbc;
    public ReadingMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    static ProgressDto progressRow(ResultSet r,int n)throws SQLException{return new ProgressDto(r.getString("id"),r.getString("article_id"),r.getString("article_version_id"),r.getString("sentence_key"),r.getInt("position_offset"),r.getString("status"),r.getInt("lock_version"),r.getTimestamp("updated_at").toInstant(),r.getTimestamp("completed_at")==null?null:r.getTimestamp("completed_at").toInstant());}
    public ProgressDto progress(long actor,long version){return jdbc.query("SELECT * FROM reading_progress WHERE user_id=? AND article_version_id=?",ReadingMapper::progressRow,actor,version).stream().findFirst().orElse(null);}
    public ProgressDto progressCurrent(long actor,long version){return jdbc.query("SELECT * FROM reading_progress WHERE user_id=? AND article_version_id=? FOR UPDATE",ReadingMapper::progressRow,actor,version).stream().findFirst().orElse(null);}
    public void start(long actor,long article,long version,String sentence){jdbc.update("INSERT INTO reading_progress(user_id,article_id,article_version_id,sentence_key) VALUES(?,?,?,?)",actor,article,version,sentence);}
    public int save(long actor,long version,ProgressUpdate input){return jdbc.update("UPDATE reading_progress SET sentence_key=?,position_offset=?,completed_at=CASE WHEN completed_at IS NULL AND ? THEN UTC_TIMESTAMP(6) ELSE completed_at END,status=CASE WHEN status='COMPLETED' OR ? THEN 'COMPLETED' ELSE 'READING' END,updated_at=UTC_TIMESTAMP(6),lock_version=lock_version+1 WHERE user_id=? AND article_version_id=? AND lock_version=?",input.sentenceKey(),input.offset(),input.completed(),input.completed(),actor,version,input.expectedLockVersion());}
    public List<ProgressDto> history(long actor,int page,int size){return jdbc.query("SELECT * FROM reading_progress WHERE user_id=? ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",ReadingMapper::progressRow,actor,size,(page-1)*size);}
    public long historyCount(long actor){return jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress WHERE user_id=?",Long.class,actor);}
    private String historyFilter(long actor,Long category,String keyword,String difficulty,List<Object> args){
        String where=" WHERE p.user_id=?";args.add(actor);
        if(category!=null){where+=" AND v.category_id=?";args.add(category);}
        if(difficulty!=null&&!difficulty.isBlank()){where+=" AND v.difficulty=?";args.add(difficulty);}
        if(!keyword.isBlank()){
            where+=" AND (v.title LIKE ? ESCAPE '!' OR v.title_zh LIKE ? ESCAPE '!' OR v.summary LIKE ? ESCAPE '!')";
            String term="%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            args.add(term);args.add(term);args.add(term);
        }
        return where;
    }
    public List<ProgressDto> history(long actor,Long category,String keyword,String difficulty,String sort,int page,int size){
        var args=new ArrayList<Object>();String where=historyFilter(actor,category,keyword,difficulty,args);
        String order=switch(sort){case "short"->"v.word_count ASC,";case "long"->"v.word_count DESC,";case "newest"->"a.published_at DESC,";default->"";};
        args.add(size);args.add((page-1)*size);
        return jdbc.query("SELECT p.* FROM reading_progress p JOIN reading_article_version v ON v.id=p.article_version_id JOIN reading_article a ON a.id=p.article_id"+where+" ORDER BY "+order+"p.updated_at DESC,p.id DESC LIMIT ? OFFSET ?",ReadingMapper::progressRow,args.toArray());
    }
    public long historyCount(long actor,Long category,String keyword,String difficulty){
        var args=new ArrayList<Object>();String where=historyFilter(actor,category,keyword,difficulty,args);
        return jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress p JOIN reading_article_version v ON v.id=p.article_version_id"+where,Long.class,args.toArray());
    }
    public List<Long> publishedIds(){return jdbc.queryForList("SELECT id FROM reading_article WHERE status='PUBLISHED' AND content_usage='READING' ORDER BY id",Long.class);}
    public List<Long> relatedIds(long id,long category){return jdbc.queryForList("SELECT id FROM reading_article WHERE status='PUBLISHED' AND content_usage='READING' AND id<>? ORDER BY (category_id=?) DESC,published_at DESC,id DESC LIMIT 3",Long.class,id,category);}
    public Long neighborId(long id,boolean previous){return jdbc.queryForList("SELECT id FROM reading_article WHERE status='PUBLISHED' AND content_usage='READING' AND id"+(previous?"<? ORDER BY id DESC":" >? ORDER BY id ASC")+" LIMIT 1",Long.class,id).stream().findFirst().orElse(null);}
    private String filter(Long category,String keyword,String difficulty,Long owner,List<Object> args){
        String where=" WHERE a.status='PUBLISHED' AND a.content_usage='READING'";
        if(category!=null){where+=" AND a.category_id=?";args.add(category);}
        if(difficulty!=null&&!difficulty.isBlank()){where+=" AND v.difficulty=?";args.add(difficulty);}
        if(!keyword.isBlank()){where+=" AND (v.title LIKE ? ESCAPE '!' OR v.title_zh LIKE ? ESCAPE '!' OR v.summary LIKE ? ESCAPE '!')";String term="%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.add(term);args.add(term);args.add(term);}
        if(owner!=null){where+=" AND EXISTS (SELECT 1 FROM user_article_favorite f WHERE f.user_id=? AND f.article_id=a.id AND f.active=TRUE)";args.add(owner);}return where;
    }
    public List<Long> published(Long category,String keyword,int page,int size){return published(category,keyword,null,"newest",page,size,null);}
    public List<Long> published(Long category,String keyword,String difficulty,String sort,int page,int size,Long owner){var args=new ArrayList<Object>();String filter=filter(category,keyword,difficulty,owner,args);args.add(size);args.add((page-1)*size);String order=switch(sort){case "short"->"v.word_count ASC,";case "long"->"v.word_count DESC,";default->"";};return jdbc.queryForList("SELECT a.id FROM reading_article a JOIN reading_article_version v ON a.published_version_id=v.id "+filter+" ORDER BY "+order+"a.published_at DESC,a.id DESC LIMIT ? OFFSET ?",Long.class,args.toArray());}
    public long count(Long category,String keyword){return count(category,keyword,null,null);}
    public long count(Long category,String keyword,String difficulty,Long owner){var args=new ArrayList<Object>();String filter=filter(category,keyword,difficulty,owner,args);return jdbc.queryForObject("SELECT COUNT(*) FROM reading_article a JOIN reading_article_version v ON a.published_version_id=v.id "+filter,Long.class,args.toArray());}
}
