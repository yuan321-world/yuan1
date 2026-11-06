package com.englishhero.reading;
import com.englishhero.vocabulary.learning.WordbookDataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
class CET4ReferenceDataTest {
 @Test void pinnedReferenceDataIsCompleteAndVerified()throws Exception{
  byte[] manifest=new ClassPathResource("vocabulary/ecdict-cet4-local/manifest.json").getContentAsByteArray();
  byte[] entries=new ClassPathResource("vocabulary/ecdict-cet4-local/entries.json").getContentAsByteArray();
  var data=WordbookDataLoader.verify(manifest,entries);assertThat(data.manifest().originalCount()).isEqualTo(3849);assertThat(data.entries().entries()).hasSize(3846);assertThat(data.manifest().excluded()).hasSize(3);assertThat(data.manifest().missingPhoneticCount()).isEqualTo(10);
  assertThatThrownBy(()->WordbookDataLoader.verify(manifest,new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
 }
}
