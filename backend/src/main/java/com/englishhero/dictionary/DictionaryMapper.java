package com.englishhero.dictionary;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.dictionary.DictionaryDtos.*;
@Repository
public class DictionaryMapper {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public DictionaryMapper(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public String manifest(){return String.join(":",jdbc.queryForList("SELECT metadata_value FROM system_metadata WHERE metadata_key IN ('dictionary_manifest_hash','dictionary_ecdict_hash','dictionary_wordbook_cet4_hash') OR metadata_key LIKE 'dictionary_ecdict_search_%_hash' ORDER BY metadata_key",String.class));}
    public record Surface(String text,PublicEntry entry){}
    public List<String> relatedIds(String phrase){
        var words=List.of(phrase.toLowerCase(Locale.ROOT).replace('’','\'').trim().split("\\s+"));
        String params=String.join(",",Collections.nCopies(words.size(),"?"));
        var args=new ArrayList<String>(words);args.addAll(words);
        return jdbc.queryForList("SELECT id FROM dictionary_entry WHERE lemma IN ("+params+") UNION SELECT entry_id AS id FROM dictionary_form WHERE form IN ("+params+")",String.class,args.toArray());
    }
    public List<Surface> searchForms(String prefix){
        return jdbc.query("SELECT f.form,e.* FROM dictionary_form f JOIN dictionary_entry e ON e.id=f.entry_id WHERE f.form LIKE ? AND f.form<>e.lemma ORDER BY CASE WHEN f.form=? THEN 0 ELSE 1 END,CASE WHEN e.lemma=? THEN 0 ELSE 1 END,f.form,e.lemma LIMIT 3",(r,n)->new Surface(r.getString("form"),new PublicEntry(r.getString("id"),r.getString("lemma"),r.getString("phonetic"),List.of(json.readValue(r.getString("senses"),Sense[].class)),r.getString("source_note"))),prefix+"%",prefix,prefix);
    }
    public List<PublicEntry> lemmas(List<String> candidates){if(candidates.isEmpty())return List.of();if(candidates.size()>16)throw new IllegalArgumentException("Too many morphology candidates");return jdbc.query("SELECT * FROM dictionary_entry WHERE lemma IN ("+String.join(",",Collections.nCopies(candidates.size(),"?"))+") ORDER BY id",(r,n)->new PublicEntry(r.getString("id"),r.getString("lemma"),r.getString("phonetic"),List.of(json.readValue(r.getString("senses"),Sense[].class)),r.getString("source_note")),candidates.toArray());}
    public List<PublicEntry> lookup(String word){return jdbc.query("SELECT DISTINCT e.* FROM dictionary_entry e LEFT JOIN dictionary_form f ON f.entry_id=e.id WHERE e.lemma=? OR f.form=? ORDER BY CASE WHEN e.lemma=? THEN 0 ELSE 1 END,e.id",(r,n)->new PublicEntry(r.getString("id"),r.getString("lemma"),r.getString("phonetic"),List.of(json.readValue(r.getString("senses"),Sense[].class)),r.getString("source_note")),word,word,word);}
    public PublicEntry entry(long id){return jdbc.query("SELECT * FROM dictionary_entry WHERE id=?",(r,n)->new PublicEntry(r.getString("id"),r.getString("lemma"),r.getString("phonetic"),List.of(json.readValue(r.getString("senses"),Sense[].class)),r.getString("source_note")),id).stream().findFirst().orElse(null);}
    public List<String> forms(String entryId){return jdbc.queryForList("SELECT form FROM dictionary_form WHERE entry_id=? ORDER BY form",String.class,entryId);}
    public List<PublicEntry> search(String query) {
        var found = new LinkedHashMap<String, PublicEntry>();
        org.springframework.jdbc.core.RowMapper<PublicEntry> row = (r,n) -> new PublicEntry(
                r.getString("id"), r.getString("lemma"), r.getString("phonetic"),
                List.of(json.readValue(r.getString("senses"), Sense[].class)), r.getString("source_note"));
        for (var entry : lemmas(List.of(query))) found.put(entry.entryId(), entry);
        var forms = jdbc.query("SELECT e.* FROM dictionary_form f JOIN dictionary_entry e ON e.id=f.entry_id WHERE f.form=? ORDER BY e.lemma LIMIT 10", row, query);
        for (var entry : forms) found.putIfAbsent(entry.entryId(), entry);
        var prefix = jdbc.query("SELECT * FROM dictionary_entry WHERE lemma LIKE ? ORDER BY lemma LIMIT 10", row, query + "%");
        for (var entry : prefix) found.putIfAbsent(entry.entryId(), entry);
        return found.values().stream().limit(10).toList();
    }
}
