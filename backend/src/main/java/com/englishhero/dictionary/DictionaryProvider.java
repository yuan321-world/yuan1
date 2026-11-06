package com.englishhero.dictionary;
import java.util.List;
import com.englishhero.dictionary.DictionaryDtos.PublicEntry;
public interface DictionaryProvider { List<PublicEntry> lookup(String normalizedWord); }
