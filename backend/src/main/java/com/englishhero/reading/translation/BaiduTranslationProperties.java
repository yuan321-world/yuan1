package com.englishhero.reading.translation;
import org.springframework.stereotype.Component;import org.springframework.boot.context.properties.ConfigurationProperties;
@Component @ConfigurationProperties(prefix="english-hero.reading.translation.baidu")
public class BaiduTranslationProperties {
 public static final String MODEL="baidu-general";
 private String appId="",secretKey="";private long monthlyCharacterLimit=1_000_000;
 public String getAppId(){return appId;}public void setAppId(String v){appId=v;}
 public String getSecretKey(){return secretKey;}public void setSecretKey(String v){secretKey=v;}
 public long getMonthlyCharacterLimit(){return monthlyCharacterLimit;}public void setMonthlyCharacterLimit(long v){monthlyCharacterLimit=v;}
 public boolean ready(){return appId!=null&&!appId.isBlank()&&secretKey!=null&&!secretKey.isBlank();}
 public int characters(String text){return text.codePointCount(0,text.length());}
}
