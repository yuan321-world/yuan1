package com.englishhero.wall;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Repository;
@Repository
public class WallModerationMapper {
 private final WallMapper mapper;
 public WallModerationMapper(WallMapper mapper){this.mapper=mapper;}
 public List<Map<String,Object>> reports(long mid,Long cid){return mapper.sql().queryForList("SELECT CAST(id AS CHAR) AS id,CAST(message_id AS CHAR) AS messageId,CAST(comment_id AS CHAR) AS commentId,target_type AS targetType,reason,detail,status,created_at AS createdAt FROM wall_report WHERE message_id=? AND "+(cid==null?"comment_id IS NULL":"comment_id=?")+" ORDER BY id DESC",cid==null?new Object[]{mid}:new Object[]{mid,cid});}
 public List<Map<String,Object>> history(long mid){return mapper.sql().queryForList("SELECT CAST(id AS CHAR) AS id,CAST(comment_id AS CHAR) AS commentId,action,before_text AS beforeText,after_text AS afterText,author_reason AS authorReason,internal_note AS internalNote,created_at AS createdAt FROM wall_moderation_log WHERE message_id=? ORDER BY id DESC LIMIT 50",mid);}
 public long log(long actor,long mid,Long cid,String action,String before,String after,String reason,String internal,Instant now){return mapper.insertKey("INSERT INTO wall_moderation_log(actor_id,message_id,comment_id,action,before_text,after_text,author_reason,internal_note,created_at) VALUES(?,?,?,?,?,?,?,?,?)",actor,mid,cid,action,before,after,reason,internal,Timestamp.from(now));}
 public void notify(long recipient,long log,long mid,Long cid,String action,String reason,String excerpt,Instant now){mapper.sql().update("INSERT INTO wall_notification(recipient_id,moderation_id,message_id,comment_id,action,author_reason,excerpt,created_at) VALUES(?,?,?,?,?,?,?,?)",recipient,log,mid,cid,action,reason,excerpt,Timestamp.from(now));}
}
