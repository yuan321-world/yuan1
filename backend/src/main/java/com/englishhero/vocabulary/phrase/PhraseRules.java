package com.englishhero.vocabulary.phrase;
import java.text.Normalizer;
import java.util.Locale;
import com.englishhero.web.BusinessException;
import static com.englishhero.vocabulary.phrase.PhraseDtos.*;
public final class PhraseRules {
 private PhraseRules(){}
 public static String canonical(String text){String value=Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKC).strip().replaceAll("\\s+"," ").replace('’','\'').toLowerCase(Locale.ROOT);if(value.length()>160||!value.matches("[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){1,19}"))throw BusinessException.invalid("text","请输入2至20个英文单词组成的词组，不含句子标点");return value;}
 public static String meaning(String text){String v=text==null?"":text.strip();if(v.isEmpty()||v.length()>500||!v.matches("(?s).*\\p{IsHan}.*"))throw BusinessException.invalid("meaningZh","请填写500字以内的个人中文释义");return v;}
 public static String note(String text){String v=text==null?"":text.strip();if(v.length()>2000)throw BusinessException.invalid("note","备注最多2000字");return v;}
 public static Item assist(Item i){return new Item(i.phraseId(),i.text(),i.meaningZh(),false,i.failures()+1,true,true,i.draft(),false,i.ruleVersion());}
 public static Item pass(Item i){return new Item(i.phraseId(),i.text(),i.meaningZh(),!i.assisted(),i.failures(),false,true,"",true,i.ruleVersion());}
}
