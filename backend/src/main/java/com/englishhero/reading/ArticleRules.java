package com.englishhero.reading;
import com.englishhero.web.BusinessException;
import static com.englishhero.reading.ArticleContent.text;
public final class ArticleRules {
    private ArticleRules(){}
    public static long id(String value,String field){try{if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException();return Long.parseLong(value);}catch(Exception e){throw BusinessException.invalid(field,"请选择有效记录");}}
    public static ReadingDtos.DraftInput validate(ReadingDtos.DraftInput input){
        if(input==null)throw BusinessException.invalid("title","请填写文章");
        id(input.categoryId(),"categoryId");
        String title=text(input.title(),"title",160,true),zh=text(input.titleZh(),"titleZh",160,true),summary=text(input.summary(),"summary",500,true);
        if(!java.util.Set.of("B1","B2").contains(input.difficulty()==null?"":input.difficulty()))throw BusinessException.invalid("difficulty","请选择B1或B2");
        if(!java.util.Set.of("ORIGINAL","THIRD_PARTY").contains(input.sourceType()==null?"":input.sourceType()))throw BusinessException.invalid("sourceType","请选择有效来源类型");
        String author=text(input.author(),"author",160,true),usage=text(input.usageNote(),"usageNote",1000,true),url=text(input.sourceUrl(),"sourceUrl",2048,false);
        if(input.sourceType().equals("THIRD_PARTY")&&url.isBlank())throw BusinessException.invalid("sourceUrl","请填写真实来源链接");
        if(!url.isBlank()){try{var u=java.net.URI.create(url);if(!java.util.Set.of("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException();}catch(Exception e){throw BusinessException.invalid("sourceUrl","来源链接须为有效http或https网址");}}
        String body=text(input.body(),"body",20000,true);ArticleContent.normalize(body);
        return new ReadingDtos.DraftInput(input.categoryId(),title,zh,summary,input.difficulty(),body,author,input.sourceType(),url.isBlank()?null:url,usage);
    }
    public static int page(int page){if(page<1||page>100000)throw BusinessException.invalid("page","页码超出范围");return page;}
    public static int pageSize(int size){if(size<1||size>50)throw BusinessException.invalid("pageSize","每页数量须为1—50");return size;}
    public static void lock(Integer value){if(value==null||value<0)throw BusinessException.invalid("expectedLockVersion","请重新加载文章");}
}
