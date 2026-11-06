package com.englishhero.dictionary;

import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.reading.ArticleContent;
import static com.englishhero.dictionary.DictionaryDtos.*;

/** Explicit opt-in offline import. Existing definitions/IDs and private vocabulary are never replaced. */
@Component @Order(20)
public class EcdictDataLoader implements ApplicationRunner {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final TransactionTemplate tx;private final boolean enabled;
    public EcdictDataLoader(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager,@Value("${english-hero.dictionary.ecdict.enabled:false}") boolean enabled){this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);this.enabled=enabled;}
    @Override public void run(ApplicationArguments args)throws Exception {if(enabled)try(var input=new ClassPathResource("dictionary/ecdict-local/entries.json").getInputStream()){load(input.readAllBytes());}}
    public void load(byte[] bytes) {
        load(bytes,"dictionary_ecdict");
    }
    void load(byte[] bytes,String metadata) {
        Dataset data=json.readValue(bytes,Dataset.class);validate(data);final String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        tx.executeWithoutResult(status->{
            jdbc.update("INSERT IGNORE INTO system_metadata(metadata_key,metadata_value) VALUES('dictionary_manifest_lock','1')");
            jdbc.queryForObject("SELECT metadata_value FROM system_metadata WHERE metadata_key='dictionary_manifest_lock' FOR UPDATE",String.class);
            var version=jdbc.queryForList("SELECT metadata_value FROM system_metadata WHERE metadata_key=?",String.class,metadata+"_version");
            var prior=jdbc.queryForList("SELECT metadata_value FROM system_metadata WHERE metadata_key=?",String.class,metadata+"_hash");
            if(!version.isEmpty()&&version.getFirst().equals(data.dataVersion())) { if(prior.size()!=1||!prior.getFirst().equals(hash))throw new IllegalStateException("ECDICT content changed without version update"); return; }
            jdbc.batchUpdate("INSERT IGNORE INTO dictionary_entry(lemma,phonetic,senses,source_note) VALUES(?,?,?,?)",data.entries(),500,(statement,entry)->{
                statement.setString(1,entry.lemma());statement.setString(2,entry.phonetic());statement.setString(3,json.writeValueAsString(entry.senses()));statement.setString(4,data.sourceNote());
            });
            var ids=new HashMap<String,Long>();jdbc.query("SELECT id,lemma FROM dictionary_entry",(org.springframework.jdbc.core.RowCallbackHandler)row->ids.put(row.getString("lemma"),row.getLong("id")));
            record Form(String word,long id){}
            var forms=new ArrayList<Form>();for(var entry:data.entries())for(String form:entry.forms())forms.add(new Form(form,ids.get(entry.lemma())));
            jdbc.batchUpdate("INSERT IGNORE INTO dictionary_form(form,entry_id) VALUES(?,?)",forms,500,(statement,form)->{statement.setString(1,form.word());statement.setLong(2,form.id());});
            for(var item:Map.of(metadata+"_version",data.dataVersion(),metadata+"_hash",hash).entrySet())jdbc.update("INSERT INTO system_metadata(metadata_key,metadata_value) VALUES(?,?) ON DUPLICATE KEY UPDATE metadata_value=?",item.getKey(),item.getValue(),item.getValue());
        });
    }
    public static void validate(Dataset data){
        if(data==null||data.schemaVersion()!=1||data.entries()==null||data.entries().isEmpty()||data.entries().size()>10000||!data.dataVersion().startsWith("ecdict-"))throw new IllegalArgumentException("Invalid ECDICT dataset");
        ArticleContent.text(data.dataVersion(),"dataVersion",64,true);ArticleContent.text(data.sourceNote(),"sourceNote",500,true);
        var seen=new HashSet<String>();for(var e:data.entries()) {
            if(e==null||!e.lemma().equals(DictionaryRules.normalize(e.lemma()))||!seen.add(e.lemma())||e.forms()==null||e.forms().size()>64||e.senses()==null||e.senses().isEmpty()||e.senses().size()>16)throw new IllegalArgumentException("Invalid ECDICT entry");
            if(e.phonetic()!=null)ArticleContent.text(e.phonetic(),"phonetic",128,false);
            var forms=new HashSet<String>();for(String form:e.forms())if(!forms.add(DictionaryRules.normalize(form)))throw new IllegalArgumentException("Duplicate ECDICT form");
            for(var sense:e.senses()){if(sense==null)throw new IllegalArgumentException("Invalid ECDICT sense");ArticleContent.text(sense.partOfSpeech(),"partOfSpeech",32,true);ArticleContent.text(sense.definitionZh(),"definitionZh",300,true);ArticleContent.text(sense.definitionEn(),"definitionEn",500,false);}
        }
    }
}
