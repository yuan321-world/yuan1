package com.englishhero.wall;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import static com.englishhero.wall.WallDtos.*;
@Repository
public class WallMapper {
 private final JdbcTemplate jdbc;
 public WallMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public JdbcTemplate sql(){return jdbc;}
 public record Message(long id,long userId,String kind,String category,String color,String title,String body,boolean authorAdmin,String status,boolean edited,long version,int x,int y,Instant createdAt,String displayName){}
 public Message row(ResultSet r,int n)throws SQLException{return new Message(r.getLong("id"),r.getLong("user_id"),r.getString("kind"),r.getString("category"),r.getString("color"),r.getString("title"),r.getString("body"),r.getBoolean("author_admin"),r.getString("status"),r.getBoolean("edited_by_admin"),r.getLong("version"),r.getInt("position_x"),r.getInt("position_y"),r.getTimestamp("created_at").toInstant(),r.getString("display_name"));}
 public Message message(long id,boolean lock){if(lock)jdbc.queryForList("SELECT id FROM wall_message WHERE id=? FOR UPDATE",Long.class,id);return jdbc.query("SELECT m.*,u.display_name FROM wall_message m JOIN identity_user u ON u.id=m.user_id WHERE m.id=?",this::row,id).stream().findFirst().orElse(null);}
 public List<Message> list(long before,int size,String category,String keyword,Long owner,boolean privateList){
  String sql="SELECT m.*,u.display_name FROM wall_message m JOIN identity_user u ON u.id=m.user_id WHERE m.id<? AND m.status"+(privateList?" IN ('VISIBLE','HIDDEN')":"='VISIBLE'");var args=new ArrayList<Object>();args.add(before);
  if(owner!=null){sql+=" AND m.user_id=?";args.add(owner);}if(category!=null&&!category.isBlank()){sql+=" AND m.category=?";args.add(category);}if(keyword!=null&&!keyword.isBlank()){sql+=" AND (LOCATE(?,m.body)>0 OR LOCATE(?,m.title)>0 OR LOCATE(?,u.display_name)>0)";args.add(keyword);args.add(keyword);args.add(keyword);}sql+=" ORDER BY m.id DESC LIMIT ?";args.add(size+1);return jdbc.query(sql,this::row,args.toArray());
 }
 public void lockLayout(){jdbc.queryForObject("SELECT revision FROM wall_layout_state WHERE id=1 FOR UPDATE",Long.class);}
 public List<WallLayout.Rectangle> occupied(Long exclude){return jdbc.query("SELECT position_x,position_y FROM wall_message WHERE status='VISIBLE'"+(exclude==null?"":" AND id<>?")+" FOR UPDATE",(r,n)->new WallLayout.Rectangle(r.getInt(1),r.getInt(2),WallLayout.WIDTH,WallLayout.HEIGHT),exclude==null?new Object[]{}:new Object[]{exclude});}
 public long insert(long user,MessageInput p,boolean admin,Position position,Instant now){return insertKey("INSERT INTO wall_message(user_id,kind,category,color,title,body,author_admin,position_x,position_y,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",user,p.kind(),p.category(),p.color(),p.title(),p.text(),admin,position.x(),position.y(),Timestamp.from(now),Timestamp.from(now));}
 public long insertKey(String sql,Object... args){var key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 public MessageDto dto(Message m,Long viewer){long likes=jdbc.queryForObject("SELECT COUNT(*) FROM wall_like WHERE message_id=?",Long.class,m.id);boolean liked=viewer!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM wall_like WHERE message_id=? AND user_id=?",Long.class,m.id,viewer)>0;long comments=jdbc.queryForObject("SELECT COUNT(*) FROM wall_comment WHERE message_id=? AND status='VISIBLE'",Long.class,m.id);String name=m.displayName==null||m.displayName.isBlank()?"学习者":m.displayName;return new MessageDto(Long.toString(m.id),m.version,m.kind,m.category,m.color,m.body,m.title,new Author(name,m.authorAdmin),m.createdAt,m.edited,new Position(m.x,m.y,WallLayout.WIDTH,WallLayout.HEIGHT),likes,liked,comments,viewer!=null&&viewer==m.userId);}
 public String authorReason(long id){return jdbc.queryForList("SELECT author_reason FROM wall_moderation_log WHERE message_id=? AND comment_id IS NULL AND action<>'KEEP' ORDER BY id DESC LIMIT 1",String.class,id).stream().findFirst().orElse("");}
}
