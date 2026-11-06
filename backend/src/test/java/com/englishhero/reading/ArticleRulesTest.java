package com.englishhero.reading;
import com.englishhero.web.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ArticleRulesTest {
    static ReadingDtos.DraftInput draft(String title,String body,String source,String url) {
        return new ReadingDtos.DraftInput("1",title,"每天一点进步","A short introduction.","B1",body,"Lingua Loop编辑组",source,url,"原创学习练习");
    }
    @Test void enforcesLengthsSourceAndUnicodeWithoutExecutingMarkup() {
        ArticleRules.validate(draft("a".repeat(160),"A simple sentence.","ORIGINAL",null));
        assertThatThrownBy(()->ArticleRules.validate(draft("a".repeat(161),"Text.","ORIGINAL",null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->ArticleRules.validate(draft("Title","a".repeat(2001),"ORIGINAL",null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->ArticleRules.validate(draft("Title","Text.","THIRD_PARTY","javascript:alert(1)"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->ArticleRules.validate(draft("Title","bad\ud800text","ORIGINAL",null))).isInstanceOf(BusinessException.class);
        var content=ArticleContent.normalize("<script>alert(1)</script>\r\n\r\nEvery small step matters.");
        assertThat(content.paragraphs()).hasSize(2);
        assertThat(content.paragraphs().getFirst().sentences().getFirst().text()).contains("<script>");
    }
    @Test void stableNormalizationAndWordCountsUseExplicitEnglishRules() {
        var one=ArticleContent.normalize("  Every small step matters.\r\n\r\nWe can’t stop learning.  ");
        var two=ArticleContent.normalize("Every small step matters.\n\nWe can’t stop learning.");
        assertThat(one).isEqualTo(two);
        assertThat(one.paragraphs().getFirst().sentences().getFirst().key()).isEqualTo("p0001-s0001");
        assertThat(ArticleContent.wordCount(one)).isEqualTo(8);
    }
}
