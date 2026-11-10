package com.englishhero.reading.translation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FullArticleTranslationChunksTest {
    @Test
    void chunksStayWithinBaiduCodePointLimitAndReconstructTheSource() {
        String source = "A 😀 sentence. ".repeat(90);

        var chunks = FullArticleTranslationChunks.split(source);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.text().codePointCount(0, chunk.text().length())).isLessThanOrEqualTo(1000));
        assertThat(chunks.stream().map(chunk -> chunk.separatorBefore() + chunk.text())
                .collect(java.util.stream.Collectors.joining())).isEqualTo(source);
    }

    @Test
    void prefersSentenceBoundaryBeforeWhitespaceFallback() {
        String source = "a".repeat(970) + ". " + "b".repeat(80);

        var chunks = FullArticleTranslationChunks.split(source);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().text()).endsWith(".");
        assertThat(chunks.get(1).separatorBefore()).isEqualTo(" ");
        assertThat(chunks.get(1).text()).startsWith("b");
    }

    @Test
    void hardSplitKeepsSurrogatePairsWhole() {
        String source = "😀".repeat(1001);

        var chunks = FullArticleTranslationChunks.split(source);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.text().codePointCount(0, chunk.text().length())).isLessThanOrEqualTo(1000);
            assertThat(chunk.text()).doesNotMatch("(?s).*[\\uD800-\\uDBFF]$");
            assertThat(chunk.text()).doesNotMatch("(?s)^[\\uDC00-\\uDFFF].*");
        });
        assertThat(chunks.stream().map(chunk -> chunk.separatorBefore() + chunk.text())
                .collect(java.util.stream.Collectors.joining())).isEqualTo(source);
    }
}
