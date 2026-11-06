package com.englishhero.dictionary;
import com.englishhero.web.BusinessException;
import java.util.Locale;
public final class DictionaryRules {
    private DictionaryRules(){}
    public static String normalizeSearch(String query){
        if(query==null||query.length()>160)throw BusinessException.invalid("q","请输入80字符以内的英文单词或词组");
        String text=query.trim().replaceAll("\\s+"," ");
        if(text.length()>80||!text.matches("[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*(?: [A-Za-z]+(?:['’‐‑-][A-Za-z]+)*){0,9}"))throw BusinessException.invalid("q","请输入英文单词或词组，不含句子标点");
        return text.toLowerCase(Locale.ROOT).replace('’','\'').replace('‐','-').replace('‑','-');
    }
    public static String normalize(String word){
        if(word==null||word.length()>64||!word.matches("[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*"))throw BusinessException.invalid("word","请选择一个完整英文单词（最多64个字符）");
        return word.toLowerCase(Locale.ROOT).replace('’','\'').replace('‐','-').replace('‑','-');
    }
}
