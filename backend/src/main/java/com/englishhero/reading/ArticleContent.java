package com.englishhero.reading;
import java.text.*;
import java.util.*;
import java.util.regex.Pattern;
import com.englishhero.web.BusinessException;
public final class ArticleContent {
    private ArticleContent(){}
    public record Sentence(String key,String text){}
    public record Paragraph(String key,List<Sentence> sentences){}
    public record Document(int schemaVersion,List<Paragraph> paragraphs){}
    public static final Pattern WORD=Pattern.compile("[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*");
    public static String text(String raw,String field,int max,boolean required) {
        if(raw==null)raw="";
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(Character.isHighSurrogate(c)){if(i+1>=raw.length()||!Character.isLowSurrogate(raw.charAt(++i)))throw BusinessException.invalid(field,"内容包含无效字符");}
            else if(Character.isLowSurrogate(c)||(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw BusinessException.invalid(field,"内容包含无效字符");
        }
        String value=Normalizer.normalize(raw.replace("\r\n","\n").replace('\r','\n'),Normalizer.Form.NFC).strip();
        if(required&&value.isBlank())throw BusinessException.invalid(field,"请填写此项");
        if(value.codePointCount(0,value.length())>max)throw BusinessException.invalid(field,"最多填写"+max+"个字符");
        return value;
    }
    public static Document normalize(String raw){
        String value=text(raw,"body",20000,true);var paragraphs=new ArrayList<Paragraph>();
        for(String paragraph:value.split("\\n[\\t ]*\\n+")){
            String p=paragraph.strip();if(p.isEmpty())continue;String pk="p%04d".formatted(paragraphs.size()+1);
            var breaker=BreakIterator.getSentenceInstance(Locale.ENGLISH);breaker.setText(p);var sentences=new ArrayList<Sentence>();
            int start=breaker.first();for(int end=breaker.next();end!=BreakIterator.DONE;start=end,end=breaker.next()){
                String s=p.substring(start,end).strip();if(s.isEmpty())continue;
                if(s.codePointCount(0,s.length())>2000)throw BusinessException.invalid("body","单句最多2000个字符，请拆分长句");
                sentences.add(new Sentence(pk+"-s%04d".formatted(sentences.size()+1),s));
            }
            paragraphs.add(new Paragraph(pk,List.copyOf(sentences)));
        }
        return new Document(1,List.copyOf(paragraphs));
    }
    public static int wordCount(Document document){return document.paragraphs().stream().flatMap(p->p.sentences().stream()).mapToInt(s->(int)WORD.matcher(s.text()).results().count()).sum();}
}
