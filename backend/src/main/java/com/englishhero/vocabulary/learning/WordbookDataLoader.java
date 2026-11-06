package com.englishhero.vocabulary.learning;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.dictionary.*;
import com.englishhero.dictionary.DictionaryDtos.Dataset;
import static com.englishhero.vocabulary.learning.WordbookDtos.*;

@Component @Order(30)
public class WordbookDataLoader implements ApplicationRunner {
 private static final String EXAM_BUNDLE_SHA = "3bf70254cd188b7ed61712faf0657328f42849e927871bb7375c1014ea1c6017";
 private static final String EXAM_SOURCE = "https://github.com/skywind3000/ECDICT/tree/bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b";
 public record ExamBook(String code, String title, String notice, List<Excluded> excluded, List<String> words) {}
 public record ExamBundle(Dataset dataset, List<ExamBook> books) {}

 private final JdbcTemplate jdbc;private final ObjectMapper json;private final TransactionTemplate tx;private final boolean enabled, examsEnabled;
 public WordbookDataLoader(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager,@Value("${english-hero.vocabulary.cet4-reference.enabled:false}")boolean enabled,@Value("${english-hero.vocabulary.exam-wordbooks.enabled:false}")boolean examsEnabled){this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);this.enabled=enabled;this.examsEnabled=examsEnabled;}
 public record Verified(Manifest manifest,Dataset entries){}
 public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
 public static Verified verify(byte[] manifest,byte[] entries){
  try{var json=new ObjectMapper();var m=json.readValue(manifest,Manifest.class);if(m==null||m.schemaVersion()!=1||!"CET4_REFERENCE".equals(m.code())||!"ecdict-cet4-bc015ed-v1".equals(m.dataVersion())||!"3cc6d01e6268806c6a9a34203a022242129c745a7229dde982d28a8ed729a0d7".equals(m.entriesSha256())||!hash(entries).equals(m.entriesSha256())||m.originalCount()!=3849||m.availableCount()!=3846||m.missingPhoneticCount()!=10||m.excluded()==null||m.excluded().size()!=3)throw new IllegalArgumentException("Invalid pinned reference manifest");
   var data=json.readValue(entries,Dataset.class);EcdictDataLoader.validate(data);if(!data.dataVersion().equals(m.dataVersion())||data.entries().size()!=m.availableCount()||data.entries().stream().filter(e->e.phonetic()==null||e.phonetic().isBlank()).count()!=10)throw new IllegalArgumentException("Reference coverage differs");
   return new Verified(m,data);
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid reference data",e);}
 }
 @Override public void run(ApplicationArguments args)throws Exception{if(enabled)load(new ClassPathResource("vocabulary/ecdict-cet4-local/manifest.json").getContentAsByteArray(),new ClassPathResource("vocabulary/ecdict-cet4-local/entries.json").getContentAsByteArray());if(examsEnabled)loadExamBooks();}
 public void load(byte[] manifest,byte[] entries){var verified=verify(manifest,entries);var m=verified.manifest();var data=verified.entries();String manifestHash=hash(manifest);
  tx.executeWithoutResult(status->{
   lockCatalog();
   var prior=jdbc.queryForList("SELECT manifest_hash FROM vocabulary_wordbook WHERE code=? AND data_version=?",String.class,m.code(),m.dataVersion());if(!prior.isEmpty()){if(!prior.getFirst().equals(manifestHash))throw new IllegalStateException("Wordbook content changed without version update");return;}
   var ids = importDictionary(data);
   jdbc.update("INSERT INTO vocabulary_wordbook(code,data_version,title,source_url,notice,manifest_hash,original_count,available_count,missing_phonetic_count,excluded_json,status) VALUES(?,?,?,?,?,?,?,?,?,?,'UNLISTED')",m.code(),m.dataVersion(),m.title(),m.sourceUrl(),m.notice(),manifestHash,m.originalCount(),m.availableCount(),m.missingPhoneticCount(),json.writeValueAsString(m.excluded()));long book=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
   var ordered=new ArrayList<>(data.entries());ordered.sort(Comparator.comparing(e->e.lemma()));var rows=new ArrayList<long[]>();for(int i=0;i<ordered.size();i++)rows.add(new long[]{book,ids.get(ordered.get(i).lemma()),i});
   insertOrder(rows);
   jdbc.update("INSERT INTO system_metadata(metadata_key,metadata_value) VALUES('dictionary_wordbook_cet4_hash',?) ON DUPLICATE KEY UPDATE metadata_value=VALUES(metadata_value)",m.entriesSha256());
  });
 }

 private void lockCatalog() {
  jdbc.update("INSERT IGNORE INTO system_metadata(metadata_key,metadata_value) VALUES('dictionary_manifest_lock','1')");jdbc.queryForObject("SELECT metadata_value FROM system_metadata WHERE metadata_key='dictionary_manifest_lock' FOR UPDATE",String.class);
 }

 private Map<String,Long> importDictionary(Dataset data) {
   jdbc.batchUpdate("INSERT IGNORE INTO dictionary_entry(lemma,phonetic,senses,source_note) VALUES(?,?,?,?)",data.entries(),500,(s,e)->{s.setString(1,e.lemma());s.setString(2,e.phonetic());s.setString(3,json.writeValueAsString(e.senses()));s.setString(4,data.sourceNote());});
   var ids=new HashMap<String,Long>();jdbc.query("SELECT id,lemma FROM dictionary_entry",r->{ids.put(r.getString("lemma"),r.getLong("id"));});
   record Form(String word,long entry){}var forms=new ArrayList<Form>();for(var e:data.entries())for(String form:e.forms())forms.add(new Form(form,ids.get(e.lemma())));
   jdbc.batchUpdate("INSERT IGNORE INTO dictionary_form(form,entry_id) VALUES(?,?)",forms,500,(s,f)->{s.setString(1,f.word());s.setLong(2,f.entry());});
  return ids;
 }

 private void insertOrder(List<long[]> rows) {
  jdbc.batchUpdate("INSERT INTO vocabulary_wordbook_entry(book_id,entry_id,sort_order) VALUES(?,?,?)",rows,500,(s,row)->{for(int i=0;i<3;i++)s.setLong(i+1,row[i]);});
 }

 public void loadExamBooks() throws java.io.IOException {
  var resource = new ClassPathResource("vocabulary/ecdict-exams-local/bundle.json");
  byte[] bytes = resource.getContentAsByteArray();
  if (!hash(bytes).equals(EXAM_BUNDLE_SHA)) throw new IllegalArgumentException("Unexpected exam wordbook bundle hash");
  var bundle = json.readValue(bytes, ExamBundle.class);
  EcdictDataLoader.validate(bundle.dataset());
  tx.executeWithoutResult(status -> {
   lockCatalog();
   String version = bundle.dataset().dataVersion();
   var installed = jdbc.queryForList("SELECT code FROM vocabulary_wordbook WHERE data_version=? AND manifest_hash=?", String.class, version, EXAM_BUNDLE_SHA);
   if (installed.containsAll(bundle.books().stream().map(ExamBook::code).toList())) return;
   var ids = importDictionary(bundle.dataset());
   var missingPhonetic = new HashSet<String>();
   for (var entry : bundle.dataset().entries()) {
    if (entry.phonetic() == null || entry.phonetic().isBlank()) missingPhonetic.add(entry.lemma());
   }
   for (var book : bundle.books()) {
    if (installed.contains(book.code())) continue;
    int available = book.words().size();
    int missing = (int) book.words().stream().filter(missingPhonetic::contains).count();
    jdbc.update("""
      INSERT INTO vocabulary_wordbook(code,data_version,title,source_url,notice,manifest_hash,original_count,available_count,missing_phonetic_count,excluded_json)
      VALUES(?,?,?,?,?,?,?,?,?,?)
      """, book.code(), version, book.title(), EXAM_SOURCE, book.notice(), EXAM_BUNDLE_SHA,
      available + book.excluded().size(), available, missing, json.writeValueAsString(book.excluded()));
    long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    var rows = new ArrayList<long[]>();
    for (int index = 0; index < available; index++) {
     rows.add(new long[]{id, ids.get(book.words().get(index)), index});
    }
    insertOrder(rows);
   }
  });
 }
}
