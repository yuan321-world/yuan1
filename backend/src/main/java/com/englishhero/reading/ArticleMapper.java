package com.englishhero.reading;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import static com.englishhero.reading.ReadingDtos.*;

/** Parameterized JDBC within the same Spring transaction manager as identity/MyBatis. */
@Repository
public class ArticleMapper {
    private final JdbcTemplate jdbc;
    public ArticleMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record ArticleRow(long id,long categoryId,String status,Long draftVersionId,Long publishedVersionId,int lockVersion,long createdBy,Instant publishedAt){}
    public record VersionRow(long id,long articleId,int versionNo,long categoryId,String title,String titleZh,String summary,String difficulty,String contentJson,int wordCount,int estimatedMinutes,String author,String sourceType,String sourceUrl,String usageNote){}
    private static final RowMapper<ArticleRow> ARTICLE=(r,n)->new ArticleRow(r.getLong("id"),r.getLong("category_id"),r.getString("status"),(Long)r.getObject("draft_version_id"),(Long)r.getObject("published_version_id"),r.getInt("lock_version"),r.getLong("created_by"),r.getTimestamp("published_at")==null?null:r.getTimestamp("published_at").toInstant());
    private static final RowMapper<VersionRow> VERSION=(r,n)->new VersionRow(r.getLong("id"),r.getLong("article_id"),r.getInt("version_no"),r.getLong("category_id"),r.getString("title"),r.getString("title_zh"),r.getString("summary"),r.getString("difficulty"),r.getString("content_json"),r.getInt("word_count"),r.getInt("estimated_minutes"),r.getString("author"),r.getString("source_type"),r.getString("source_url"),r.getString("usage_note"));
    public List<CategoryDto> categories(){return jdbc.query("SELECT id,code,name FROM reading_category ORDER BY sort_order,id",(r,n)->new CategoryDto(r.getString(1),r.getString(2),r.getString(3)));}
    public CategoryDto category(long id){return categories().stream().filter(c->c.id().equals(Long.toString(id))).findFirst().orElse(null);}
    public ArticleRow article(long id,boolean lock){return jdbc.query("SELECT * FROM reading_article WHERE id=?"+(lock?" FOR UPDATE":""),ARTICLE,id).stream().findFirst().orElse(null);}
    public VersionRow version(long articleId,long versionId){return jdbc.query("SELECT * FROM reading_article_version WHERE article_id=? AND id=?",VERSION,articleId,versionId).stream().findFirst().orElse(null);}
    public VersionRow versionCurrent(long articleId,long versionId){return jdbc.query("SELECT * FROM reading_article_version WHERE article_id=? AND id=? FOR SHARE",VERSION,articleId,versionId).stream().findFirst().orElse(null);}
    public List<ArticleRow> adminList(String status,Long category,String keyword,int page,int size){
        var args=new ArrayList<Object>();String where=filter(status,category,keyword,args);
        args.add(size);args.add((page-1)*size);
        return jdbc.query("SELECT a.* FROM reading_article a JOIN reading_article_version v ON v.id=a.draft_version_id "+where+" ORDER BY a.updated_at DESC,a.id DESC LIMIT ? OFFSET ?",ARTICLE,args.toArray());
    }
    public long adminCount(String status,Long category,String keyword){var args=new ArrayList<Object>();String where=filter(status,category,keyword,args);return jdbc.queryForObject("SELECT COUNT(*) FROM reading_article a JOIN reading_article_version v ON v.id=a.draft_version_id "+where,Long.class,args.toArray());}
    private String filter(String status,Long category,String keyword,List<Object> args){
        String s=" WHERE a.content_usage='READING'";
        if(status!=null&&!status.isBlank()){s+=" AND a.status=?";args.add(status);}
        if(category!=null){s+=" AND v.category_id=?";args.add(category);}
        if(keyword!=null&&!keyword.isBlank()){s+=" AND v.title LIKE ? ESCAPE '!'";args.add("%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        return s;
    }
    public boolean practiceOnly(long id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT content_usage='PRACTICE' FROM reading_article WHERE id=?",Boolean.class,id));}
    public long insertPracticeMaterial(long category,long actor){return insert("INSERT INTO reading_article(category_id,created_by,updated_by,content_usage) VALUES(?,?,?,'PRACTICE')",category,actor,actor);}
    public long insertArticle(long category,long actor){return insert("INSERT INTO reading_article(category_id,created_by,updated_by) VALUES(?,?,?)",category,actor,actor);}
    public long insertVersion(long article,int version,long actor,DraftInput d,String content,String hash,int words){
        return insert("INSERT INTO reading_article_version(article_id,version_no,category_id,title,title_zh,summary,difficulty,content_json,content_hash,word_count,estimated_minutes,author,source_type,source_url,usage_note,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            article,version,Long.parseLong(d.categoryId()),d.title(),d.titleZh(),d.summary(),d.difficulty(),content,hash,words,Math.max(1,(words+199)/200),d.author(),d.sourceType(),d.sourceUrl(),d.usageNote(),actor);
    }
    public void initialDraft(long article,long version){jdbc.update("UPDATE reading_article SET draft_version_id=? WHERE id=?",version,article);}
    public int saveDraft(long article,long version,int expected,long actor){return jdbc.update("UPDATE reading_article SET draft_version_id=?,lock_version=lock_version+1,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND lock_version=?",version,actor,article,expected);}
    public int publish(long article,long version,long category,int expected,long actor){return jdbc.update("UPDATE reading_article SET published_version_id=?,category_id=?,status='PUBLISHED',published_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6),updated_by=?,lock_version=lock_version+1 WHERE id=? AND lock_version=?",version,category,actor,article,expected);}
    public int unpublish(long article,int expected,long actor){return jdbc.update("UPDATE reading_article SET status='UNLISTED',updated_at=UTC_TIMESTAMP(6),updated_by=?,lock_version=lock_version+1 WHERE id=? AND lock_version=?",actor,article,expected);}
    private long insert(String sql,Object...args){
        var keys=new GeneratedKeyHolder();jdbc.update(connection->{var p=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
