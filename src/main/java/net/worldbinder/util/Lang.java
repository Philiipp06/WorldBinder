package net.worldbinder.util;

import net.minecraft.network.chat.Component;

public final class Lang {
    /** Bundled catalogs. English is the fallback for every other Minecraft language. */
    public static final String[] LANGUAGES={"en_us","de_de","fr_fr"};
    private static final java.util.Map<String,java.util.Map<String,String>> FALLBACKS=new java.util.HashMap<>();
    private static final java.util.Set<String> MISSING=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final String STORED="\u001eworldbinder:";
    static {
        for(String language:LANGUAGES){
            try(var input=Lang.class.getResourceAsStream("/assets/worldbinder/lang/"+language+".json")){
                if(input==null)throw new IllegalStateException("Missing bundled language "+language);
                var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                java.util.Map<String,String> values=new java.util.HashMap<>();json.entrySet().forEach(entry->values.put(entry.getKey(),entry.getValue().getAsString()));FALLBACKS.put(language,values);
            }catch(java.io.IOException error){throw new ExceptionInInitializerError(error);}
        }
    }
    private Lang() {
    }

    public static Component text(String key, Object... args) {
        Object[] supported = java.util.Arrays.stream(args).map(value -> value instanceof net.worldbinder.validation.Finding finding?finding.render():value instanceof String text?resolve(text):value).map(value -> value instanceof Component || value instanceof Number
                || value instanceof Boolean || value instanceof String ? value : String.valueOf(value)).toArray();
        String fallback=template(language(),key);
        return Component.translatableWithFallback(key, fallback, supported);
    }

    public static String string(String key, Object... args) {
        return text(key, args).getString();
    }
    public static String language(){
        try{var client=net.minecraft.client.Minecraft.getInstance();if(client!=null&&client.options!=null)return bundle(client.options.languageCode);}catch(RuntimeException ignored){}
        return "en_us";
    }
    private static String template(String language,String key){
        var values=FALLBACKS.get(bundle(language));String value=values.get(key);
        if(value==null){if(MISSING.add(key))net.worldbinder.WorldBinder.LOGGER.warn("Missing translation key: {}",key);return values.getOrDefault("worldbinder.language.unknown","?");}
        return value;
    }
    /** Bundled catalog for a Minecraft language code: German and French variants share their catalog, all others use English. */
    public static String bundle(String languageCode){
        if(languageCode==null)return "en_us";
        if(languageCode.startsWith("de"))return "de_de";
        if(languageCode.startsWith("fr"))return "fr_fr";
        return "en_us";
    }
    public static String in(String language,String key,Object... args){
        var matcher=java.util.regex.Pattern.compile("%(?:(\\d+)\\$)?([%s])").matcher(template(language,key));var output=new StringBuilder();int cursor=0;
        while(matcher.find()){
            String replacement;
            if(matcher.group(2).equals("%"))replacement="%";
            else{int index=matcher.group(1)==null?cursor++:Integer.parseInt(matcher.group(1))-1;
                if(index>=args.length)replacement=template(language,"worldbinder.language.unknown");else{Object value=args[index];replacement=value instanceof net.worldbinder.validation.Finding finding?finding.render(language):value instanceof String text?resolve(text,language):String.valueOf(value);}}
            matcher.appendReplacement(output,java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);return output.toString();
    }
    public static String stored(String key,Object... args){return STORED+net.worldbinder.WorldBinder.GSON.toJson(net.worldbinder.validation.Finding.of(key,args));}
    public static String resolve(String value){return resolve(value,language());}
    public static String resolve(String value,String language){
        if(value==null)return "";if(!value.startsWith(STORED))return value;
        try{return net.worldbinder.WorldBinder.GSON.fromJson(value.substring(STORED.length()),net.worldbinder.validation.Finding.class).render(language);}
        catch(RuntimeException error){return template(language,"worldbinder.language.unknown");}
    }
}
