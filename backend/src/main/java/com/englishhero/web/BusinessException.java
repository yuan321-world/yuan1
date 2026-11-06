package com.englishhero.web;
import java.util.Map;
public class BusinessException extends RuntimeException {
    public final int status; public final String code; public final Map<String,String> fields;
    public BusinessException(int status,String code,String message){this(status,code,message,Map.of());}
    public BusinessException(int status,String code,String message,Map<String,String> fields){super(message);this.status=status;this.code=code;this.fields=Map.copyOf(fields);}
    public static BusinessException invalid(String field,String message){return new BusinessException(400,"VALIDATION_ERROR","请检查填写内容",Map.of(field,message));}
    public static BusinessException missing(){return new BusinessException(404,"ARTICLE_NOT_FOUND","文章不存在或暂不可访问");}
    public static BusinessException conflict(){return new BusinessException(409,"ARTICLE_VERSION_CONFLICT","文章已被更新，请重新加载后编辑");}
}
