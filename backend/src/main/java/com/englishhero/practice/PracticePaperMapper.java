package com.englishhero.practice;

import java.util.*;
import java.time.Instant;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.practice.PracticePaperDtos.*;

@Repository
public class PracticePaperMapper {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public PracticePaperMapper(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 private AdminInput decode(String s){return json.readValue(s,AdminInput.class);}
 public record Paper(long id,AdminInput draft,boolean reviewed,String status,int revision,int lock,Long published){}
 public record Version(long id,long paper,int revision,AdminInput metadata){}
 public record Run(long id,long user,long paper,long version,String status,int lock,String slot,String question,Instant started,Instant completed){}
 private final RowMapper<Paper> papers=(r,n)->new Paper(r.getLong("id"),decode(r.getString("draft_json")),r.getBoolean("source_reviewed"),r.getString("status"),r.getInt("revision"),r.getInt("lock_version"),r.getObject("published_version_id",Long.class));
 private final RowMapper<Version> versions=(r,n)->new Version(r.getLong("id"),r.getLong("paper_id"),r.getInt("version_no"),decode(r.getString("metadata_json")));
 private final RowMapper<Run> runs=(r,n)->new Run(r.getLong("id"),r.getLong("user_id"),r.getLong("paper_id"),r.getLong("paper_version_id"),r.getString("status"),r.getInt("lock_version"),r.getString("current_slot"),r.getString("current_question_id"),r.getTimestamp("started_at").toInstant(),r.getTimestamp("completed_at")==null?null:r.getTimestamp("completed_at").toInstant());
 public long insert(String sql,Object...args){var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
 public Paper paper(long id,boolean lock){return jdbc.query("SELECT * FROM practice_paper WHERE id=?"+(lock?" FOR UPDATE":""),papers,id).stream().findFirst().orElseThrow(PracticeAdminService::missing);}
 public Version version(long id){return jdbc.query("SELECT * FROM practice_paper_version WHERE id=?",versions,id).stream().findFirst().orElseThrow(PracticeAdminService::missing);}
 public List<Paper> papers(){return jdbc.query("SELECT * FROM practice_paper ORDER BY id DESC",papers);}
 // Catalog reads only published snapshots, then batches the selected page's components.
 public List<Version> publishedCatalogVersions(){return jdbc.query("SELECT v.* FROM practice_paper p JOIN practice_paper_version v ON v.id=p.published_version_id WHERE p.status='PUBLISHED' ORDER BY p.id DESC",versions);}
 public record CatalogComponent(long paperVersion,ComponentInput input,ComponentView detail,boolean available){}
 public List<CatalogComponent> catalogComponents(List<Long> versionIds){
  if(versionIds.isEmpty())return List.of();
  String placeholders=String.join(",",Collections.nCopies(versionIds.size(),"?"));
  String sql="SELECT c.paper_version_id,c.slot,c.set_version_id,v.metadata_json,v.reading_type,s.status,CASE WHEN v.reading_type IN ('WRITING','TRANSLATION') THEN 1 ELSE (SELECT COUNT(*) FROM reading_question q WHERE q.quiz_version_id=v.quiz_version_id) END question_count FROM practice_paper_component c JOIN practice_set_version v ON v.id=c.set_version_id JOIN practice_set s ON s.id=v.set_id WHERE c.paper_version_id IN ("+placeholders+") ORDER BY c.paper_version_id,c.sort_order";
  return jdbc.query(sql,(r,n)->{var m=json.readValue(r.getString("metadata_json"),PracticeDtos.Metadata.class);var input=new ComponentInput(r.getString("slot"),r.getString("set_version_id"));return new CatalogComponent(r.getLong("paper_version_id"),input,new ComponentView(input.slot(),input.setVersionId(),m.titleZh().isBlank()?m.title():m.titleZh(),r.getString("reading_type"),r.getInt("question_count")),"PUBLISHED".equals(r.getString("status")));},versionIds.toArray());
 }
 public Map<Long,String> catalogActiveIds(long user,List<Long> paperIds){
  if(paperIds.isEmpty())return Map.of();
  var args=new ArrayList<Object>();args.add(user);args.addAll(paperIds);
  String placeholders=String.join(",",Collections.nCopies(paperIds.size(),"?"));
  var result=new HashMap<Long,String>();jdbc.query("SELECT paper_id,id FROM practice_paper_attempt WHERE user_id=? AND status='IN_PROGRESS' AND paper_id IN ("+placeholders+")",r->{result.put(r.getLong("paper_id"),r.getString("id"));},args.toArray());return result;
 }
 public List<Version> versions(long id){return jdbc.query("SELECT * FROM practice_paper_version WHERE paper_id=? ORDER BY version_no DESC",versions,id);}
 public List<ComponentInput> components(long version){return jdbc.query("SELECT slot,set_version_id FROM practice_paper_component WHERE paper_version_id=? ORDER BY sort_order",(r,n)->new ComponentInput(r.getString(1),r.getString(2)),version);}
 public void draft(Paper p,AdminInput d,long actor){jdbc.update("UPDATE practice_paper SET draft_json=?,source_reviewed=FALSE,lock_version=lock_version+1,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",json.writeValueAsString(d),actor,p.id());}
 public void review(Paper p,long actor){jdbc.update("UPDATE practice_paper SET source_reviewed=TRUE,lock_version=lock_version+1,updated_by=? WHERE id=?",actor,p.id());}
 public void publish(Paper p,long version,long actor){jdbc.update("UPDATE practice_paper SET published_version_id=?,status='PUBLISHED',revision=revision+1,lock_version=lock_version+1,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",version,actor,p.id());}
 public void unpublish(Paper p,long actor){jdbc.update("UPDATE practice_paper SET status='UNLISTED',lock_version=lock_version+1,updated_by=? WHERE id=?",actor,p.id());}
 public Run run(long user,long id,boolean lock){return jdbc.query("SELECT * FROM practice_paper_attempt WHERE user_id=? AND id=?"+(lock?" FOR UPDATE":""),runs,user,id).stream().findFirst().orElseThrow(PracticeAdminService::missing);}
 public Run active(long user,long paper){return jdbc.query("SELECT * FROM practice_paper_attempt WHERE user_id=? AND paper_id=? AND status='IN_PROGRESS' FOR UPDATE",runs,user,paper).stream().findFirst().orElse(null);}
 public String activeId(long user,long paper){return jdbc.query("SELECT id FROM practice_paper_attempt WHERE user_id=? AND paper_id=? AND status='IN_PROGRESS'",(r,n)->r.getString(1),user,paper).stream().findFirst().orElse(null);}
 public List<ResponseInput> responses(long id){return jdbc.query("SELECT response_json FROM practice_paper_response WHERE attempt_id=? ORDER BY FIELD(slot,'WRITING','LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2','TRANSLATION')",(r,n)->json.readValue(r.getString(1),ResponseInput.class),id);}
 public void initialize(long attempt,long version,ResponseInput response){jdbc.update("INSERT INTO practice_paper_response(attempt_id,paper_version_id,slot,response_json) VALUES(?,?,?,?)",attempt,version,response.slot(),json.writeValueAsString(response));}
 public void responses(Run r,List<ResponseInput> values,Map<String,Object> results){for(var v:values)jdbc.update("UPDATE practice_paper_response SET response_json=?,result_json=? WHERE attempt_id=? AND slot=?",json.writeValueAsString(v),results==null?null:json.writeValueAsString(results.get(v.slot())),r.id(),v.slot());}
 public void advance(Run r,String slot,String question,boolean done){jdbc.update("UPDATE practice_paper_attempt SET current_slot=?,current_question_id=?,lock_version=lock_version+1,status=?,completed_at="+(done?"UTC_TIMESTAMP(6)":"NULL")+" WHERE id=?",slot,question,done?"COMPLETED":"IN_PROGRESS",r.id());}
 public Long replay(long user,String op,String request,String hash){var rows=jdbc.queryForList("SELECT attempt_id,request_hash FROM practice_paper_request WHERE user_id=? AND operation=? AND request_id=?",user,op,request);if(rows.isEmpty())return null;if(!hash.equals(rows.getFirst().get("request_hash")))throw PracticeAttemptService.conflict();return ((Number)rows.getFirst().get("attempt_id")).longValue();}
 public void remember(long user,String op,String request,String hash,long id){jdbc.update("INSERT INTO practice_paper_request(user_id,operation,request_id,request_hash,attempt_id) VALUES(?,?,?,?,?)",user,op,request,hash,id);}
 public List<Run> history(long user){return jdbc.query("SELECT * FROM practice_paper_attempt WHERE user_id=? ORDER BY started_at DESC,id DESC",runs,user);}
}

