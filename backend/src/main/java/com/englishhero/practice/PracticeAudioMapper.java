package com.englishhero.practice;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PracticeAudioMapper {
    final JdbcTemplate jdbc;
    private final PracticeSetMapper sets;
    public PracticeAudioMapper(JdbcTemplate jdbc,PracticeSetMapper sets){this.jdbc=jdbc;this.sets=sets;}
    public record Asset(long id,long owner,String key,String name,long size,String sha256){}
    public long insert(long owner,String name,PracticeAudioStorage.StoredAudio stored){return sets.insert("INSERT INTO practice_audio_asset(created_by,storage_key,original_name,byte_size,sha256) VALUES(?,?,?,?,?)",owner,stored.key(),name,stored.size(),stored.sha256());}
    public Asset get(long id){return jdbc.query("SELECT * FROM practice_audio_asset WHERE id=?",(r,n)->new Asset(r.getLong("id"),r.getLong("created_by"),r.getString("storage_key"),r.getString("original_name"),r.getLong("byte_size"),r.getString("sha256")),id).stream().findFirst().orElse(null);}
    public String version(long version){return jdbc.queryForObject("SELECT listening_json FROM practice_set_version WHERE id=?",String.class,version);}
}
