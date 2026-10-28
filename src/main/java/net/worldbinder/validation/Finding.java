package net.worldbinder.validation;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.*;
import com.google.gson.TypeAdapter;
import java.io.IOException;
import java.util.*;
import net.worldbinder.util.Lang;

/** Deferred language with structured nested causes; a legacy report string remains readable as supplied. */
@JsonAdapter(Finding.Adapter.class)
public record Finding(String key,List<Object> arguments,String legacy) {
    public static Finding of(String key,Object... arguments){
        return new Finding(key,Arrays.stream(arguments).map(value->value instanceof Finding?value:String.valueOf(value)).toList(),null);
    }
    public static Object detail(Throwable error){
        while((error instanceof java.util.concurrent.CompletionException||error instanceof java.util.concurrent.ExecutionException)&&error.getCause()!=null)error=error.getCause();
        return error instanceof FindingException found?found.finding():error instanceof FindingIOException found?found.finding():error instanceof FindingStateException found?found.finding():error instanceof FindingUncheckedIOException found?found.finding():String.valueOf(error.getMessage());
    }
    public String render(){return render(Lang.language());}
    public String render(String language){return legacy!=null?legacy:Lang.in(language,key,arguments.toArray());}
    @Override public String toString(){return render();}
    public static final class Adapter extends TypeAdapter<Finding>{
        @Override public void write(JsonWriter writer,Finding value)throws IOException{
            if(value==null){writer.nullValue();return;}if(value.legacy()!=null){writer.value(value.legacy());return;}
            writer.beginObject().name("key").value(value.key()).name("arguments").beginArray();
            for(Object argument:value.arguments())if(argument instanceof Finding nested)write(writer,nested);else writer.value(String.valueOf(argument));
            writer.endArray().endObject();
        }
        @Override public Finding read(JsonReader reader)throws IOException{
            if(reader.peek()==JsonToken.NULL){reader.nextNull();return new Finding(null,List.of(),"");}
            if(reader.peek()==JsonToken.STRING)return new Finding(null,List.of(),reader.nextString());
            reader.beginObject();String key=null,legacy=null;List<Object> arguments=new ArrayList<>();
            while(reader.hasNext())switch(reader.nextName()){
                case "key"->key=reader.nextString();
                case "legacy"->legacy=reader.nextString();
                case "arguments"->{reader.beginArray();while(reader.hasNext()){if(reader.peek()==JsonToken.BEGIN_OBJECT)arguments.add(read(reader));else if(reader.peek()==JsonToken.NULL){reader.nextNull();arguments.add("");}else arguments.add(reader.nextString());}reader.endArray();}
                default->reader.skipValue();
            }
            reader.endObject();return new Finding(key,List.copyOf(arguments),legacy);
        }
    }
}
