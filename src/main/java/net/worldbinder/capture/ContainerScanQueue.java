package net.worldbinder.capture;

import java.util.*;

/** One outstanding interaction. Full, associated content is the only success event. Time uses client ticks. */
public final class ContainerScanQueue<T> {
    public enum Phase { IDLE, OPENING, WAITING, VERIFIED, CLOSING, COOLDOWN, SUSPENDED }
    public enum Action { NONE, OPEN, CLOSE }
    private final LinkedHashMap<String,T> pending=new LinkedHashMap<>();
    private final Set<String> visited=new HashSet<>();
    private Phase phase=Phase.IDLE;
    private T current;private String key;
    private int menu=-1,attempts;
    private long deadline;
    private boolean retry;
    private int confirmed,failed,retries;
    public Phase phase(){return phase;} public T current(){return current;} public int menu(){return menu;}
    public int confirmed(){return confirmed;} public int failed(){return failed;} public int pending(){return pending.size()+(current==null?0:1);}
    public int retries(){return retries;}
    public boolean offer(String key,T target){
        if(pending.size()>=64||visited.contains(key)||Objects.equals(this.key,key)||pending.containsKey(key))return false;
        pending.put(key,target);return true;
    }
    public Action tick(long now,int timeout,int maxAttempts){
        if(phase==Phase.SUSPENDED)return Action.NONE;
        if(phase==Phase.VERIFIED){phase=Phase.CLOSING;deadline=now+10;retry=false;return Action.CLOSE;}
        if((phase==Phase.OPENING||phase==Phase.WAITING)&&now>=deadline){
            if(attempts<maxAttempts){retry=true;if(menu>=0){phase=Phase.CLOSING;deadline=now+10;return Action.CLOSE;}phase=Phase.COOLDOWN;deadline=now+10;}
            else {failed++;visited.add(key);if(menu<0){phase=Phase.SUSPENDED;return Action.NONE;}retry=false;phase=Phase.CLOSING;deadline=now+10;return Action.CLOSE;}
        }
        if(phase==Phase.CLOSING&&now>=deadline){phase=Phase.SUSPENDED;return Action.NONE;}
        if(phase==Phase.COOLDOWN&&now>=deadline){
            if(retry){attempts++;retries++;menu=-1;phase=Phase.OPENING;deadline=now+timeout;return Action.OPEN;}
            current=null;key=null;phase=Phase.IDLE;
        }
        if(phase==Phase.IDLE&&!pending.isEmpty()){
            var entry=pending.entrySet().iterator().next();key=entry.getKey();current=entry.getValue();pending.remove(key);
            attempts=1;menu=-1;phase=Phase.OPENING;deadline=now+timeout;return Action.OPEN;
        }
        return Action.NONE;
    }
    public boolean opened(int id,long now,int timeout){
        if(!(phase==Phase.OPENING||phase==Phase.COOLDOWN&&retry&&menu<0)||id<0)return false;menu=id;phase=Phase.WAITING;deadline=now+timeout;return true;
    }
    public boolean received(int id){
        if(phase!=Phase.WAITING||id!=menu)return false;phase=Phase.VERIFIED;confirmed++;visited.add(key);return true;
    }
    public void closed(int id,long now){
        if(phase==Phase.CLOSING&&menu==id){phase=Phase.COOLDOWN;deadline=now+10;}
        else suspend();
    }
    public void suspend(){phase=Phase.SUSPENDED;pending.clear();current=null;key=null;menu=-1;}
    public void resume(){if(phase==Phase.SUSPENDED){phase=Phase.IDLE;current=null;key=null;menu=-1;}}
    public void rescan(){pending.clear();visited.clear();confirmed=0;failed=0;retries=0;current=null;key=null;menu=-1;phase=Phase.IDLE;}
}
