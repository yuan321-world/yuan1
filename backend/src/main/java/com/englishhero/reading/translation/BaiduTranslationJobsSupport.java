package com.englishhero.reading.translation;
import java.util.Set;
final class BaiduTranslationJobsSupport {
 private BaiduTranslationJobsSupport(){}
 static boolean unsent(String code){return code!=null&&Set.of("BAIDU_CHARACTER_QUOTA","QUEUE_EXPIRED","ACCOUNT_DISABLED","BAIDU_NOT_CONFIGURED","BAIDU_INPUT_INVALID","BAIDU_52003","BAIDU_54000","BAIDU_54001","BAIDU_54003","BAIDU_54004","BAIDU_54005","BAIDU_58000","BAIDU_58001","BAIDU_58002","BAIDU_90107").contains(code);}
}
