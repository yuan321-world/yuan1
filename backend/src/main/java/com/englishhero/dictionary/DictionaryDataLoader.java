package com.englishhero.dictionary;

import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.reading.ArticleContent;
import static com.englishhero.dictionary.DictionaryDtos.*;

/** Only packaged, reviewed data is loaded. No upload or network dictionary importer. */
@Component @org.springframework.core.annotation.Order(10)
public class DictionaryDataLoader implements ApplicationRunner {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final TransactionTemplate tx;
    public DictionaryDataLoader(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager){this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);}
    @Override public void run(ApplicationArguments args)throws Exception{
        try(var input=new ClassPathResource("dictionary/entries.v1.json").getInputStream()){load(input.readAllBytes());}
    }
    public void load(byte[] bytes){
        Dataset data=json.readValue(bytes,Dataset.class);validate(data);
        final String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        tx.executeWithoutResult(status->{
            jdbc.update("INSERT IGNORE INTO system_metadata(metadata_key,metadata_value) VALUES('dictionary_manifest_lock','1')");
            jdbc.queryForObject("SELECT metadata_value FROM system_metadata WHERE metadata_key='dictionary_manifest_lock' FOR UPDATE",String.class);
            var versions=jdbc.queryForList("SELECT metadata_value FROM system_metadata WHERE metadata_key='dictionary_manifest_version'",String.class);
            var hashes=jdbc.queryForList("SELECT metadata_value FROM system_metadata WHERE metadata_key='dictionary_manifest_hash'",String.class);
            if(!versions.isEmpty()&&versions.getFirst().equals(data.dataVersion())){
                if(hashes.size()!=1||!hashes.getFirst().equals(hash))throw new IllegalStateException("Dictionary content changed without a new dataVersion");
                return;
            }
            for(var entry:data.entries()){
                jdbc.update("INSERT INTO dictionary_entry(lemma,phonetic,senses,source_note) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE phonetic=?,senses=?,source_note=?",entry.lemma(),entry.phonetic(),json.writeValueAsString(entry.senses()),data.sourceNote(),entry.phonetic(),json.writeValueAsString(entry.senses()),data.sourceNote());
                long id=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma=?",Long.class,entry.lemma());
                // Keep forms of previously reviewed versions: existing source references stay meaningful.
                for(String form:entry.forms())jdbc.update("INSERT IGNORE INTO dictionary_form(form,entry_id) VALUES(?,?)",DictionaryRules.normalize(form),id);
            }
            for(var item:Map.of("dictionary_manifest_version",data.dataVersion(),"dictionary_manifest_hash",hash).entrySet())jdbc.update("INSERT INTO system_metadata(metadata_key,metadata_value) VALUES(?,?) ON DUPLICATE KEY UPDATE metadata_value=?",item.getKey(),item.getValue(),item.getValue());
        });
    }
    public static void validate(Dataset data){
        if(data==null||data.schemaVersion()!=1||data.entries()==null||data.entries().isEmpty()||data.entries().size()>10000)throw new IllegalArgumentException("Invalid dictionary schema or entry count");
        ArticleContent.text(data.dataVersion(),"dataVersion",64,true);ArticleContent.text(data.sourceNote(),"sourceNote",500,true);
        var lemmas=new HashSet<String>();
        for(var e:data.entries()){
            if(e==null||!DictionaryRules.normalize(e.lemma()).equals(e.lemma())||!lemmas.add(e.lemma())||e.forms()==null||e.forms().size()>64||e.senses()==null||e.senses().isEmpty()||e.senses().size()>16)throw new IllegalArgumentException("Invalid or duplicate dictionary entry");
            if(e.phonetic()!=null)ArticleContent.text(e.phonetic(),"phonetic",128,false);
            var forms=new HashSet<String>();for(String form:e.forms())if(!forms.add(DictionaryRules.normalize(form)))throw new IllegalArgumentException("Duplicate dictionary form");
            for(var sense:e.senses()){
                if(sense==null)throw new IllegalArgumentException("Invalid dictionary sense");
                ArticleContent.text(sense.partOfSpeech(),"partOfSpeech",32,true);ArticleContent.text(sense.definitionZh(),"definitionZh",300,true);ArticleContent.text(sense.definitionEn(),"definitionEn",500,true);
            }
        }
    }
}
