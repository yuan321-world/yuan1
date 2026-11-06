package com.englishhero.vocabulary.phrase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PhraseRulesTest {
 @Test void canonicalDuplicates(){assertEquals(PhraseRules.canonical("  Look  forward TO "),PhraseRules.canonical("look forward to"));}
 @Test void rejectsSentenceAndSingleWord(){assertThrows(RuntimeException.class,()->PhraseRules.canonical("word"));assertThrows(RuntimeException.class,()->PhraseRules.canonical("this is a sentence."));}
 @Test void assistedRetryRequiresAnotherIndependentPass(){var i=new PhraseDtos.Item("1","look forward to","期待",false,0,false,false,"",false,PhraseSchedule.RULE);var hint=PhraseRules.assist(i);assertFalse(PhraseRules.pass(hint).passed());assertTrue(PhraseRules.pass(PhraseRules.pass(hint)).passed());assertEquals(1,PhraseRules.pass(hint).failures());}
}
