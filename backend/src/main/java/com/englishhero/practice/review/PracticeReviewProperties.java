package com.englishhero.practice.review;
import org.springframework.stereotype.Component;import org.springframework.boot.context.properties.ConfigurationProperties;
@Component @ConfigurationProperties(prefix="english-hero.practice.ai")
public class PracticeReviewProperties {
 private boolean enabled=false,workerEnabled=true;private String apiKey="",model="deepseek-flash";
 private int userDailyLimit=10;private long dailyBudgetMicrousd=1_000_000,dailyBudgetMicrocny=1_000_000;
 public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}
 public boolean isWorkerEnabled(){return workerEnabled;}public void setWorkerEnabled(boolean v){workerEnabled=v;}
 public String getApiKey(){return apiKey;}public void setApiKey(String v){apiKey=v;}
 public String getModel(){return model;}public void setModel(String v){model=v;}
 public int getUserDailyLimit(){return userDailyLimit;}public void setUserDailyLimit(int v){userDailyLimit=v;}
 // Keep the existing conservative USD ledger; official Flash CNY tariffs are 20/3
 // times USD tariffs (input and output). Floor the yuan ceiling before reserving.
 // This is tariff parity, not a live foreign-exchange conversion. A lower legacy cap still applies.
 public long getDailyBudgetMicrousd(){long cny=Math.max(0,dailyBudgetMicrocny);long equivalent=cny/20*3+(cny%20)*3/20;return Math.min(Math.max(0,dailyBudgetMicrousd),equivalent);}
 public void setDailyBudgetMicrousd(long v){dailyBudgetMicrousd=v;}
 public long getDailyBudgetMicrocny(){return dailyBudgetMicrocny;}public void setDailyBudgetMicrocny(long v){dailyBudgetMicrocny=v;}
 public boolean ready(){return enabled&&apiKey!=null&&!apiKey.isBlank()&&"deepseek-flash".equals(model);}
}
