package com.englishhero.practice.review;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class PracticeReviewBudgetCurrencyTest {
 @Test void defaultsToOneYuanUsingConservativeFlashRateParity(){
  var config=new PracticeReviewProperties();
  // Official Flash RMB input/output tariffs are 20/3 times the USD tariffs.
  // Existing USD accounting rounds up, therefore 150000 microUSD <= 1000000 microCNY.
  assertThat(config.getDailyBudgetMicrousd()).isEqualTo(150000);
  assertThat(PracticeReviewBudget.cost(3000L,2000L)*20).isGreaterThanOrEqualTo((2*3000L+8*2000L)*3);
 }
 @Test void roundsTheYuanCapDownAndPreservesALowerLegacyCap(){var c=new PracticeReviewProperties();c.setDailyBudgetMicrocny(1000001);assertThat(c.getDailyBudgetMicrousd()).isEqualTo(150000);c.setDailyBudgetMicrousd(100000);assertThat(c.getDailyBudgetMicrousd()).isEqualTo(100000);c.setDailyBudgetMicrocny(-1);assertThat(c.getDailyBudgetMicrousd()).isZero();}
}
