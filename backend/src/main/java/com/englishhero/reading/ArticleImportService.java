package com.englishhero.reading;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.web.BusinessException;
import static com.englishhero.reading.ArticleImportDtos.*;

@Service
public class ArticleImportService {
    private final AdminArticleService admin;private final ArticleMapper articles;private final JdbcTemplate jdbc;private final ObjectMapper json;private final TransactionTemplate transaction;
    public ArticleImportService(AdminArticleService admin,ArticleMapper articles,JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager){this.admin=admin;this.articles=articles;this.jdbc=jdbc;this.json=json;this.transaction=new TransactionTemplate(manager);this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    public ImportValidation validate(SessionPrincipal actor,ImportPayload payload){admin.requireAdmin(actor);var errors=errors(payload);return new ImportValidation(errors.isEmpty(),errors,payload==null||payload.articles()==null?0:payload.articles().size());}
    private Map<String,String> errors(ImportPayload payload){
        var errors=new LinkedHashMap<String,String>();
        if(payload==null||!Objects.equals(payload.schemaVersion(),1)){errors.put("schemaVersion","仅支持schemaVersion=1");return errors;}
        if(payload.articles()==null||payload.articles().isEmpty()||payload.articles().size()>20){errors.put("articles","每批须包含1—20篇文章");return errors;}
        for(int i=0;i<payload.articles().size();i++){
            try{var d=ArticleRules.validate(payload.articles().get(i));if(articles.category(Long.parseLong(d.categoryId()))==null)throw BusinessException.invalid("categoryId","请选择有效分类");}
            catch(BusinessException failure){for(var field:failure.fields.entrySet()){if(errors.size()>=100)break;errors.put("articles["+i+"]."+field.getKey(),field.getValue());}}
        }
        return errors;
    }
    public ImportResult importDrafts(SessionPrincipal actor,String clientImportId,ImportPayload payload){
        admin.requireAdmin(actor);
        if(clientImportId==null||!clientImportId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw BusinessException.invalid("clientImportId","请重新选择文件后导入");
        String clientId=UUID.fromString(clientImportId).toString();var errors=errors(payload);if(!errors.isEmpty())throw new BusinessException(400,"VALIDATION_ERROR","请修正导入文件中的问题",errors);
        var normalized=new ImportPayload(1,payload.articles().stream().map(ArticleRules::validate).toList());String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(normalized).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}
        try{
            return transaction.execute(status->{
                var existing=existing(clientId,actor.userId(),hash);if(existing!=null)return existing;
                var keys=new GeneratedKeyHolder();jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO reading_import_batch(client_import_id,created_by,payload_hash,article_ids) VALUES(?,?,?,'[]')",java.sql.Statement.RETURN_GENERATED_KEYS);statement.setString(1,clientId);statement.setLong(2,actor.userId());statement.setString(3,hash);return statement;},keys);
                long batch=Objects.requireNonNull(keys.getKey()).longValue();var ids=new ArrayList<String>();
                for(var input:normalized.articles())ids.add(admin.create(actor,input).id());
                jdbc.update("UPDATE reading_import_batch SET article_ids=? WHERE id=?",json.writeValueAsString(ids),batch);
                return new ImportResult(Long.toString(batch),List.copyOf(ids));
            });
        }catch(DuplicateKeyException duplicate){
            // The failed transaction has ended. Read a concurrent winner in a fresh transaction.
            var result=transaction.execute(status->existing(clientId,actor.userId(),hash));if(result==null)throw duplicate;return result;
        }
    }
    private ImportResult existing(String clientId,long actor,String hash){
        return jdbc.query("SELECT * FROM reading_import_batch WHERE client_import_id=?",(r,n)->{
            if(r.getLong("created_by")!=actor||!hash.equals(r.getString("payload_hash")))throw new BusinessException(409,"IMPORT_CONFLICT","导入请求已被使用，请重新选择文件后操作");
            return new ImportResult(r.getString("id"),List.of(json.readValue(r.getString("article_ids"),String[].class)));
        },clientId).stream().findFirst().orElse(null);
    }
}
