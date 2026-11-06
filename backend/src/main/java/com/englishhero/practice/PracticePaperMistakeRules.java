package com.englishhero.practice;
import java.util.*;
import com.englishhero.web.BusinessException;
final class PracticePaperMistakeRules {
 private static final Map<String,Integer> START=Map.of("LISTENING",1,"CLOZE",26,"MATCH",36,"DETAILED_1",46,"DETAILED_2",51);
 static int originalNumber(String slot,int index){Integer start=slot==null?null:START.get(slot);if(start==null||index<0||index>=PracticePaperRules.count(slot))throw BusinessException.invalid("slot","请选择有效客观题部分与题号");return start+index;}
 static boolean fullReview(String type){return Set.of("LISTENING","CLOZE","MATCH").contains(type);}
}
