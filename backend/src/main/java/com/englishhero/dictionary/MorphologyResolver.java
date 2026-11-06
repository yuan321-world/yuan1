package com.englishhero.dictionary;

import java.util.*;
import com.englishhero.dictionary.DictionaryDtos.PublicEntry;

/** Conservative fallback: hypotheses are never definitions and must round-trip through a real lemma. */
public final class MorphologyResolver {
    private MorphologyResolver() {}
    private static final Map<String,Set<String>> IRREGULAR = irregular();
    private static final Map<String,Map<String,Set<String>>> PARADIGMS = paradigms();
    private static Map<String,Map<String,Set<String>>> paradigms() {
        try(var input=new org.springframework.core.io.ClassPathResource("dictionary/ecdict-local/paradigms.json").getInputStream()) {
            return new tools.jackson.databind.ObjectMapper().readValue(input,new tools.jackson.core.type.TypeReference<Map<String,Map<String,Set<String>>>>(){});
        } catch(java.io.IOException failure) { throw new IllegalStateException("Packaged morphology constraints unavailable",failure); }
    }
    private static boolean allows(String lemma,String category,String surface) {
        var known=PARADIGMS.getOrDefault(lemma,Map.of()).get(category);
        return known==null||known.isEmpty()||known.contains(surface);
    }
    private static final Set<String> IRREGULAR_PAST = Set.of("be","go","do","have","see","make","take","write","speak","think","buy","bring","run","eat","leave","feel","keep","hold","find","come","become","draw","meet","pay","sell","send","stand","understand","choose","sing","swim","break","cut","put","set","let","read");
    private static final Set<String> IRREGULAR_PLURAL = Set.of("child","man","woman","foot","tooth","mouse");
    private static Map<String,Set<String>> irregular() {
        var result=new LinkedHashMap<String,Set<String>>();
        for(String group:List.of("be:is,am,are,was,were,been,being","go:went,gone","do:does,did,done","have:has,had","see:saw,seen","make:made","take:took,taken","write:wrote,written","speak:spoke,spoken","think:thought","buy:bought","bring:brought","run:ran","eat:ate,eaten","leave:left","feel:felt","keep:kept","hold:held","find:found","come:came","become:became","draw:drew,drawn","meet:met","pay:paid","sell:sold","send:sent","stand:stood","understand:understood","choose:chose,chosen","sing:sang,sung","swim:swam,swum","break:broke,broken","child:children","man:men","woman:women","foot:feet","tooth:teeth","mouse:mice","good:better,best","well:better,best","bad:worse,worst")) {
            String[] pair=group.split(":"); for(String form:pair[1].split(",")) result.computeIfAbsent(form,k->new LinkedHashSet<>()).add(pair[0]);
        }
        return result;
    }
    public static List<String> candidates(String surface) {
        if(surface==null||surface.length()>64||!surface.matches("[a-z]+(?:'s)?"))return List.of();
        var result=new LinkedHashSet<String>(IRREGULAR.getOrDefault(surface,Set.of()));
        if(surface.endsWith("'s"))result.add(surface.substring(0,surface.length()-2));
        for(String ending:List.of("s","es","ies","ed","ied","ing","er","est")) {
            if(!surface.endsWith(ending)||surface.length()<=ending.length()+1)continue;
            String stem=surface.substring(0,surface.length()-ending.length());result.add(stem);
            if(List.of("ed","ing","er","est").contains(ending)) { result.add(stem+"e"); if(stem.length()>2&&stem.charAt(stem.length()-1)==stem.charAt(stem.length()-2))result.add(stem.substring(0,stem.length()-1)); }
            if(ending.equals("ies")||ending.equals("ied"))result.add(stem+"y");
            if(stem.endsWith("i")&&(ending.equals("er")||ending.equals("est")))result.add(stem.substring(0,stem.length()-1)+"y");
        }
        result.remove(surface);return result.stream().filter(s->s.length()>1&&s.matches("[a-z]+")).limit(16).toList();
    }
    public static boolean matches(String surface,PublicEntry entry) {
        String lemma=entry.lemma();if(IRREGULAR.getOrDefault(surface,Set.of()).contains(lemma))return true;
        boolean noun=pos(entry,"n"),verb=pos(entry,"v|vt|vi"),adjective=pos(entry,"adj|a");
        if(noun&&(surface.equals(lemma+"'s")||allows(lemma,"plural",surface)&&!IRREGULAR_PLURAL.contains(lemma)&&surface.equals(plural(lemma))))return true;
        if(verb&&(allows(lemma,"third",surface)&&surface.equals(plural(lemma))||allows(lemma,"past",surface)&&!IRREGULAR_PAST.contains(lemma)&&surface.equals(past(lemma))||allows(lemma,"ing",surface)&&surface.equals(ing(lemma))))return true;
        if(adjective&&!Set.of("good","well","bad").contains(lemma))for(String ending:List.of("er","est")) {
            String base=consonantY(lemma)?lemma.substring(0,lemma.length()-1)+"i":lemma.endsWith("e")?lemma.substring(0,lemma.length()-1):lemma;
            if(allows(lemma,"comparison",surface)&&surface.equals((doubleFinal(lemma)?lemma+lemma.charAt(lemma.length()-1):base)+ending))return true;
        }
        return false;
    }
    private static boolean pos(PublicEntry e,String labels){return e.senses().stream().anyMatch(s->java.util.regex.Pattern.compile("(?:^|[^a-z])(?:"+labels+")(?:\\.|\\b)").matcher(s.partOfSpeech().toLowerCase(Locale.ROOT)).find());}
    private static boolean consonantY(String s){return s.length()>1&&s.endsWith("y")&&"aeiou".indexOf(s.charAt(s.length()-2))<0;}
    private static String plural(String s){return consonantY(s)?s.substring(0,s.length()-1)+"ies":s.matches(".*(?:s|x|z|ch|sh)$")?s+"es":s+"s";}
    private static String past(String s){return consonantY(s)?s.substring(0,s.length()-1)+"ied":s.endsWith("e")?s+"d":(doubleFinal(s)?s+s.charAt(s.length()-1):s)+"ed";}
    private static String ing(String s){return s.endsWith("ie")?s.substring(0,s.length()-2)+"ying":s.endsWith("e")&&!s.endsWith("ee")&&!s.endsWith("ye")&&!s.endsWith("oe")?s.substring(0,s.length()-1)+"ing":(doubleFinal(s)?s+s.charAt(s.length()-1):s)+"ing";}
    private static boolean doubleFinal(String s){return s.length()>2&&s.length()<=6&&s.matches(".*[^aeiou][aeiou][^aeiouwxy]$")&&java.util.regex.Pattern.compile("[aeiou]+").matcher(s).results().count()==1;}
}
