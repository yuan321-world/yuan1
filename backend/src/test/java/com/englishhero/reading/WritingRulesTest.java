package com.englishhero.reading;
import com.englishhero.reading.writing.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WritingRulesTest {
 @Test void keepsPlainTextAndBoundsListsAndOrder(){
  var i=new WritingDtos.Input(" <b>Instead of</b> ","释义",List.of("phrase","phrase"),"结构","Example.","例句",List.of("写作"),0,null);
  assertThat(WritingRules.content(i).expression()).isEqualTo("<b>Instead of</b>");assertThat(WritingRules.content(i).keyPhrases()).hasSize(1);
  assertThatThrownBy(()->WritingRules.content(new WritingDtos.Input("a","b",Collections.nCopies(9,"a"),"a","b","c",List.of("d"),0,null))).isInstanceOf(com.englishhero.web.BusinessException.class);
  assertThatThrownBy(()->WritingRules.content(new WritingDtos.Input("a","b",List.of("a"),"a","b","c",List.of("d"),-1,null))).isInstanceOf(com.englishhero.web.BusinessException.class);
 }
}
