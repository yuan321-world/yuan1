package com.englishhero.identity.admin;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AdminUserRulesTest {
 @Test void validatesUnicodeAndStrictIdentifiers(){assertThat(AdminUserRules.text(" 😀 ","reason",1,true)).isEqualTo("😀");assertThatThrownBy(()->AdminUserRules.text("\ud800","reason",200,true)).isInstanceOf(com.englishhero.web.BusinessException.class);assertThatThrownBy(()->AdminUserRules.text("a".repeat(201),"reason",200,true)).isInstanceOf(com.englishhero.web.BusinessException.class);assertThatThrownBy(()->AdminUserRules.id("01","id")).isInstanceOf(com.englishhero.web.BusinessException.class);assertThatThrownBy(()->AdminUserRules.id("9223372036854775808","id")).isInstanceOf(com.englishhero.web.BusinessException.class);}
}
