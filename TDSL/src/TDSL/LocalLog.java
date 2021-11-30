package TDSL;

import java.util.ArrayList;
import java.util.Iterator;

public class LocalLog {
    ArrayList<Object> localLog;
    boolean reachedEnd = false;
    int version;
    boolean isLockedbyMe = false;

    public LocalLog(int ver) {
        localLog = new ArrayList<>();
        version = ver;
    }

    public Object get(int idx)
    {
        return localLog.get(idx - version /*- 1*/ );
    }

    public void append(Object obj)
    {
        localLog.add(obj);
    }

    public boolean isEmpty()
    {
        reachedEnd = true;
        return localLog.isEmpty();
    }

    public boolean hasNext(int idx)
    {
        if(localLog.size() == 0)
        {
            reachedEnd = true;
            return false;
        }
        return (idx - version  < localLog.size()); // TODO: another case of reaching end
    }

    protected Iterator getIterator()
    {
        return localLog.iterator();
    }

    public boolean readOnly() {
        return localLog.isEmpty();
    }

    public boolean opaque()
    {
        return this.readOnly() && !reachedEnd;// TODO: revise
    }

//    public boolean verifyTx(int ver, int size)
//    {
//        if(this.readOnly() && )
//        return true;
//    }

    public int size() {
        return localLog.size();
    }

    public void migrate(LocalLog log)
    {
        this.localLog.addAll(log.localLog);
        if(isLockedbyMe) return;
        else
            this.isLockedbyMe = log.isLockedbyMe;

    }
}
