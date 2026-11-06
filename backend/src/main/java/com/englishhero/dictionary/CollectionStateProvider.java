package com.englishhero.dictionary;
import java.util.*;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.dictionary.DictionaryDtos.CollectionState;
public interface CollectionStateProvider { Map<String,CollectionState> states(SessionPrincipal actor,List<String> entryIds); }
