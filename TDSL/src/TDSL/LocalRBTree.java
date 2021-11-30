package TDSL;

import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

public class LocalRBTree {
    TreeMap<Integer,Object> putMap = new TreeMap();
    HashSet<Integer> removeMap = new HashSet();
    boolean lockedSet = false;
    // TODO: should be in localstorage - this is cheating (FIX: after moving to new dir):

    public void mergeTrees( LocalRBTree src)
    { // digest a child tree.
        this.putMap.putAll(src.putMap);
        for(Integer key:src.removeMap)
        {
            putMap.remove(key);
            this.removeMap.add(key);
        }
        this.lockedSet = true;
    }

}
