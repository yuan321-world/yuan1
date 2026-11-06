package com.englishhero.wall;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.englishhero.wall.WallDtos.*;
@Repository
public class WallInteractionMapper {
 private final WallMapper mapper;
 public WallInteractionMapper(WallMapper mapper){this.mapper=mapper;}
 public record Comment(long id,long messageId,long userId,String body,String status,long version,Instant createdAt,String displayName,boolean admin){}
 private Comment row(ResultSet r,int n)throws SQLException{return new Comment(r.getLong("id"),r.getLong("message_id"),r.getLong("user_id"),r.getString("body"),r.getString("status"),r.getLong("version"),r.getTimestamp("created_at").toInstant(),r.getString("display_name"),r.getBoolean("admin"));}
 private String select(){return "SELECT c.*,u.display_name,EXISTS(SELECT 1 FROM identity_user_role r WHERE r.user_id=c.user_id AND r.role='ADMIN') AS admin FROM wall_comment c JOIN identity_user u ON u.id=c.user_id";}
 public Comment comment(long id,boolean lock){if(lock)mapper.sql().queryForList("SELECT id FROM wall_comment WHERE id=? FOR UPDATE",Long.class,id);return mapper.sql().query(select()+" WHERE c.id=?",this::row,id).stream().findFirst().orElse(null);}
 public List<Comment> list(long parent,long cursor,int size,boolean admin){return mapper.sql().query(select()+" WHERE c.message_id=? AND c.id<?"+(admin?"":" AND c.status='VISIBLE'")+" ORDER BY c.id DESC LIMIT ?",this::row,parent,cursor,size+1);}
 public CommentDto dto(Comment c,Long actor){return new CommentDto(Long.toString(c.id),Long.toString(c.messageId),c.version,c.body,new Author(c.displayName==null||c.displayName.isBlank()?"学习者":c.displayName,c.admin),c.createdAt,actor!=null&&actor==c.userId);}
}
