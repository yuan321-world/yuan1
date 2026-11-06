package com.englishhero.wall;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WallRulesTest {
 @Test void countsWholeUnicodeCharactersAndRejectsMalformedText(){assertThat(WallRules.text("😀".repeat(1000),"text",1000,true)).hasSize(2000);assertThatThrownBy(()->WallRules.text("😀".repeat(1001),"text",1000,true)).isInstanceOf(com.englishhero.web.BusinessException.class);assertThatThrownBy(()->WallRules.text("\uD800","text",1000,true)).isInstanceOf(com.englishhero.web.BusinessException.class);assertThatThrownBy(()->WallRules.text("x\u0000y","text",1000,true)).isInstanceOf(com.englishhero.web.BusinessException.class);}
 @Test void requestIdsAndBoundsAreStrict(){String id=UUID.randomUUID().toString();assertThat(WallRules.uuid(id)).isEqualTo(id);assertThatThrownBy(()->WallRules.uuid("1-1-1-1-1")).isInstanceOf(com.englishhero.web.BusinessException.class);assertThatThrownBy(()->WallRules.id("9223372036854775808","id")).isInstanceOf(com.englishhero.web.BusinessException.class);assertThatThrownBy(()->WallRules.size(51)).isInstanceOf(com.englishhero.web.BusinessException.class);}
}
