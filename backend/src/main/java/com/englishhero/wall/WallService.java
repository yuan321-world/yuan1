package com.englishhero.wall;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englishhero.identity.SessionPrincipal;
import static com.englishhero.wall.WallDtos.*;
@Service
public class WallService {
 private final WallMapper mapper;private final WallCommandService commands;
 public WallService(WallMapper mapper,WallCommandService commands){this.mapper=mapper;this.commands=commands;}
 public MessageInput validate(MessageInput p){if(p==null)throw WallRules.missing();String kind=WallRules.oneOf(p.kind(),"kind","MESSAGE","ANNOUNCEMENT");String category=kind.equals("ANNOUNCEMENT")?"ANNOUNCEMENT":WallRules.oneOf(p.category(),"category","STUDY","CHECKIN","ENCOURAGE");return new MessageInput(WallRules.uuid(p.requestId()),kind,category,WallRules.oneOf(p.color(),"color","yellow","pink","blue","green","purple","orange"),WallRules.text(p.text(),"text",1000,true),WallRules.text(p.title(),"title",80,kind.equals("ANNOUNCEMENT")),p.preferredPosition());}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public MessageDto create(SessionPrincipal actor,MessageInput input){
  var p=validate(input);long uid=commands.actor(actor);if(p.kind().equals("ANNOUNCEMENT"))commands.requireAdmin(actor);
  return commands.execute(actor,p.requestId(),"CREATE_MESSAGE",p,MessageDto.class,()->{commands.quota(uid,"MESSAGE");mapper.lockLayout();int x=p.preferredPosition()==null?0:WallLayout.coordinate(p.preferredPosition().x(),"preferredPosition.x"),y=p.preferredPosition()==null?0:WallLayout.coordinate(p.preferredPosition().y(),"preferredPosition.y");Position pos=WallLayout.allocate(x,y,mapper.occupied(null));long id=mapper.insert(uid,p,commands.admin(actor),pos,commands.clock.instant());return mapper.dto(mapper.message(id,true),uid);});
 }
 @Transactional(readOnly=true) public Page<MessageDto> list(SessionPrincipal actor,String category,String keyword,String cursor,int size,boolean mine){if(actor!=null)commands.actor(actor);if(mine)commands.actor(actor);validateQuery(category,keyword,size);var rows=mapper.list(WallRules.cursor(cursor),size,category,keyword,mine?actor.userId():null,false);boolean more=rows.size()>size;var items=rows.stream().limit(size).map(m->mapper.dto(m,actor==null?null:actor.userId())).toList();return new Page<>(items,more?items.getLast().id():null,more);}
 @Transactional(readOnly=true) public Page<MineDto> mine(SessionPrincipal actor,String cursor,int size){long uid=commands.actor(actor);WallRules.size(size);var rows=mapper.list(WallRules.cursor(cursor),size,null,null,uid,true);boolean more=rows.size()>size;var items=rows.stream().limit(size).map(m->new MineDto(mapper.dto(m,uid),m.status(),mapper.authorReason(m.id()))).toList();return new Page<>(items,more?items.getLast().message().id():null,more);}
 @Transactional(readOnly=true) public MessageDto detail(SessionPrincipal actor,String id){if(actor!=null)commands.actor(actor);return mapper.dto(visible(WallRules.id(id,"id"),false),actor==null?null:actor.userId());}
 public WallMapper.Message visible(long id,boolean lock){var p=mapper.message(id,lock);if(p==null||!p.status().equals("VISIBLE"))throw WallRules.missing();return p;}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<?,?> deleteOwn(SessionPrincipal actor,String id,Command input){long uid=commands.actor(actor),target=WallRules.id(id,"id");if(input==null)throw WallRules.conflict();long version=WallRules.version(input.expectedVersion());return commands.execute(actor,input.requestId(),"DELETE_MESSAGE:"+id,input,Map.class,()->{mapper.lockLayout();var p=mapper.message(target,true);if(p==null||p.userId()!=uid||p.status().equals("DELETED"))throw WallRules.missing();if(p.version()!=version)throw WallRules.conflict();mapper.sql().update("UPDATE wall_message SET status='DELETED',version=version+1,updated_at=? WHERE id=?",Timestamp.from(commands.clock.instant()),target);return Map.of("id",id,"deleted",true);});}
 private void validateQuery(String category,String keyword,int size){WallRules.size(size);WallRules.text(keyword,"keyword",100,false);if(category!=null&&!category.isBlank())WallRules.oneOf(category,"category","STUDY","CHECKIN","ENCOURAGE","ANNOUNCEMENT");}
}
