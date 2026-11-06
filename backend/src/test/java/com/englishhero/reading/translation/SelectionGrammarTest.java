package com.englishhero.reading.translation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
class SelectionGrammarTest {
 private final ObjectMapper json=new ObjectMapper();
 private String valid(){return """
 {"scopeNote":"完整简单句。","mainClause":[{"quote":"Quiet spaces","role":"主语","explanation":"名词短语。"}],"clauses":[],"clauseNote":"无从句。","grammarPoints":[{"quote":"help people read","explanation":"help后接不带to的不定式。"}]}
 """;}
 @Test void acceptsBoundedGrammarAndChecksEnglishQuotes(){assertThat(SelectionGrammar.validate("Quiet spaces help people read.",valid(),json).path("mainClause").size()).isEqualTo(1);assertThatThrownBy(()->SelectionGrammar.validate("Other source text.",valid(),json)).isInstanceOf(IllegalArgumentException.class);}
 @Test void rejectsMissingSectionsAndUnboundedExplanations(){assertThatThrownBy(()->SelectionGrammar.validate("Quiet spaces help people read.","{}",json)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->SelectionGrammar.validate("Quiet spaces help people read.",valid().replace("无从句。","字".repeat(1001)),json)).isInstanceOf(IllegalArgumentException.class);}
}
