package TDSL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Log {

    private AtomicInteger size = new AtomicInteger(0); // used as version clock
    private ArrayList<Object> log = new ArrayList<>();
    private LockQueue lock = new LockQueue();
    static final boolean DBG_LOG = false;
    private static final long singletonMask = 0x4000000000000000L;
    private static final long versionNegMask = singletonMask;

    private AtomicLong versionAndFlags = new AtomicLong();

    protected long getVersion() {
        return (versionAndFlags.get() & (~versionNegMask));
    }

    protected void setVersion(long version) {
        long l = versionAndFlags.get();
//		assert ((l & lockMask) != 0);
        l &= versionNegMask;
        l |= (version & (~versionNegMask));
        versionAndFlags.set(l);
    }

    protected boolean isSingleton() {
        long l = versionAndFlags.get();
        return (l & singletonMask) != 0;
    }

    protected boolean tryLock() {
        if(lock.tryLock())
        {
            LocalStorage localStorage = TX.lStorage.get();
            if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
            {
                if(!localStorage.logLockedSet.contains(this))
                {
                    localStorage.innerLogLockedSet.add(this);
                }
            }
            else
            {
                localStorage.logLockedSet.add(this);
            }
            return true;
        }
        else return false;
    }

    private void handleLockRecord()
    {
        LocalStorage localStorage = TX.lStorage.get();
        HashMap<Log, LocalLog> logMap;
        LocalLog localLog;
        if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
        {
            HashMap<Log, LocalLog> pLogMap = localStorage.logMap;
            logMap = localStorage.innerLogMap;
            if(!logMap.containsKey(this))
                this.getLen();
                localLog = logMap.get(this);
            if(!pLogMap.get(this).isLockedbyMe) {
                localLog.isLockedbyMe = true;
                logMap.put(this,localLog);
            }

        }
        else {
            logMap = localStorage.logMap;
            if(logMap.containsKey(this))
            {
                localLog = logMap.get(this);
            }
            else
            {
                localLog = new LocalLog(this.getLen());
            }
            localLog.isLockedbyMe = true;
            logMap.put(this,localLog);
        }
    }

    protected void setSingleton(boolean value) {
        long l = versionAndFlags.get();
//		assert ((l & lockMask) != 0);
        if (value) {
            l |= singletonMask;
            versionAndFlags.set(l);
            return;
        }
        l &= (~singletonMask);
        versionAndFlags.set(l);
    }


    public Object read(int idx)
    {// No Nesting support yet
        LocalStorage localStorage = TX.lStorage.get();
        if(!localStorage.TX)
        {//Singleton
            lock.lock();
            Object ret = log.get(idx); // Throws unchecked index out of bounds
            lock.unlock();
            return ret;

        }
        else
        {//TX
            Integer version = getLen();
            if(idx < version)
            {
                return log.get(idx);
            }
            else
            {
                if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
                {
//                    System.out.println("closed nesting");
//                    System.out.println("version = " + version + " idx = " + idx);
                    LocalLog localLog = localStorage.logMap.get(this);
                    if(localLog != null)
                    {
//                        System.out.println("localLog.size() = " + localLog.size());
                        if(idx < version + localLog.size())
                        {
                            System.out.println(localLog.size() + version );
                            System.out.println("reading from L1");
                            return  localLog.get(idx);
                        }
                        /*System.out.println(localLog.size() + version );
                        if(localLog.size() + version > idx)
                            return  localLog.consume(idx);*/
                    }
                    LocalLog innerLocalLog = localStorage.innerLogMap.get(this);

                    return innerLocalLog.get(idx);
                }
                LocalLog localLog = localStorage.logMap.get(this);
                return localLog.get(idx);
            }
            //TODO: handle closed nesting here (read by hierarchy)
        }
    }

    public boolean hasNext(int idx)
    {
        if(isEmpty()) return false;
        LocalStorage localStorage = TX.lStorage.get();
        if(!localStorage.TX){
            return (idx < size.get()-1);
        }
        else
        {//TX
            handleVersion(localStorage);
            int ver = getLen();
            if(idx < ver)
            {
                return true;
            }
            else
            {
                if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
                {
                    return localStorage.innerLogMap.get(this).hasNext(idx);
                }
                return localStorage.logMap.get(this).hasNext(idx);
            }

        }
    }

    public boolean isEmpty()
    {
        if(size.get() == 0)
        {
            LocalStorage localStorage = TX.lStorage.get();
            if(!localStorage.TX) {
                return true;
            }
            else
            {
                //TX
                handleVersion(localStorage);
                if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
                {
                    if(localStorage.innerLogVersionMap.containsKey(this)) {
                        if(localStorage.logVersionMap.containsKey(this)) {
                            return (localStorage.logMap.get(this).isEmpty() && localStorage.innerLogMap.get(this).isEmpty());
                        }
                        else // only inner Log exists
                            return (localStorage.innerLogMap.get(this).isEmpty() );
                    }
                }
                if(localStorage.logVersionMap.containsKey(this)) {
                    return (localStorage.logMap.get(this).isEmpty());
                }
                return true;
            }
        }
        return false;
    }

    public void append(Object object)
    {
        LocalStorage localStorage = TX.lStorage.get();
        if(!localStorage.TX)
        {//Singleton
            lock.lock();
            Object ret = log.add(object); // Throws unchecked index out of bounds
            size.incrementAndGet();
            lock.unlock();
        }
        else
        {//TX
            handleVersion(localStorage);
            if (!tryLock()) { // if queue is locked by another thread
                if(!(TX.CLOSED_NESTING && localStorage.TXnum > 1)) {
                    localStorage.TX = false;
                    if (DBG_LOG) System.out.println("couldn't acquire lock @ " + Thread.currentThread().getName());
                }
                else
                {
                    if (DBG_LOG) System.out.println("couldn't acquire lock @ " + Thread.currentThread().getName());
                    localStorage.innerTX = false;
//                localStorage.TX = false; // todo: check if necessary
                }
                TXLibExceptions excep = new TXLibExceptions();
                throw excep.new AbortException();
            }
            getLen();
//            handleLockRecord();
            LocalLog localLog;

            if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
            {
                localLog = localStorage.innerLogMap.get(this);
                localLog.append(object);
                localStorage.innerLogMap.put(this, localLog);
            }
            else {
                localLog = localStorage.logMap.get(this);
                localLog.append(object);
                localStorage.logMap.put(this, localLog);
            }

        }
    }


    private Integer getLen()
    {// Also validates
        LocalStorage localStorage = TX.lStorage.get();
        if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
        {//Nested
            if (localStorage.innerLogVersionMap.containsKey(this)) {
                //Inner exists
                Integer ver = localStorage.innerLogVersionMap.get(this);
                /*if (size.consume() > ver && !localStorage.innerLogMap.consume(this).opaque()) {
                    localStorage.innerTX = false;
                    TXLibExceptions excep = new TXLibExceptions();
                    throw excep.new AbortException();
                }*/

                return ver;
            }
            else
            {
                if (localStorage.logVersionMap.containsKey(this)) {
                    // No inner, ext exists
                    Integer ver = localStorage.logVersionMap.get(this);
                    /*if (size.consume() > ver && !localStorage.logMap.consume(this).opaque()) {
                        localStorage.innerTX = false;
                        TXLibExceptions excep = new TXLibExceptions();
                        throw excep.new AbortException();
                    }*/
                    localStorage.innerLogVersionMap.put(this, ver);
                    localStorage.innerLogMap.put(this, new LocalLog(ver + localStorage.logMap.get(this).size() ));
                    return ver;
                }
                else
                {
                    Integer ver = size.get();
                    localStorage.innerLogVersionMap.put(this, ver);
                    localStorage.innerLogMap.put(this, new LocalLog(ver));
                    return ver;
                }
            }
        }
        else {
            if (localStorage.logVersionMap.containsKey(this)) {
                Integer ver = localStorage.logVersionMap.get(this);
                /*if (size.consume() > ver && !localStorage.logMap.consume(this).opaque()) {
                    localStorage.TX = false;
                    TXLibExceptions excep = new TXLibExceptions();
                    throw excep.new AbortException();
                }*/
                return ver;
            } else {
                Integer ver = size.get();
                localStorage.logVersionMap.put(this, ver);
                localStorage.logMap.put(this, new LocalLog(ver));
                return ver;
            }
        }
    }

    public void lock()
    {
        if(!this.lock.isHeldByCurrent())
            this.lock.lock();
    }

    public void unlock()
    {
        this.lock.unlock();
    }

    public boolean verify()
    {
        LocalStorage localStorage = TX.lStorage.get();
        if(!localStorage.logVersionMap.containsKey(this)) return true;
        Integer ver = localStorage.logVersionMap.get(this);
        if(size.get() == ver ) // Log unmodified
            return true;
        else
        {// size > ver, Log was modified
            LocalLog localLog = localStorage.logMap.get(this);
            if(localLog.readOnly())
            {
                return localLog.opaque();
            }
            else
                return false;
        }
//        return (size.consume() == ver || localStorage.logMap.consume(this).opaque());
    }

    public boolean verifyNested()
    {
        LocalStorage localStorage = TX.lStorage.get();
        Integer ver = localStorage.innerLogVersionMap.get(this);
        if(size.get() == ver ) // Log unmodified
            return true;
        else
        {// size > ver, Log was modified
//            System.out.println("size > ver");
            LocalLog localLog = localStorage.innerLogMap.get(this);
            if(localLog.readOnly())
            {
//                return localLog.opaque();
                boolean temp = localLog.opaque();
                if(!temp)
                {
                    System.out.println("inner about to abort");
                }
                return temp;
            }
            else
                return false;
        }
//        return !(size.consume() > ver && !localStorage.innerLogMap.consume(this).opaque());
    }

    public void commit()
    {
        LocalStorage localStorage = TX.lStorage.get();
        assert this.lock.isHeldByCurrent(): "very very bad";
        this.log.addAll(localStorage.logMap.get(this).localLog);
        /*for(Object obj: localStorage.logMap.consume(this).localLog) {
            this.log.add(obj);
        }*/
        size.set(log.size());
    }

    public boolean verifyAfterInnerAbort(LocalStorage localStorage)
    {
        LocalLog localLog = localStorage.logMap.get(this);
        if(localLog != null) {
            if (this.verify())
            { // ext is ok with changes
                localLog.version = size.get();
                localStorage.logMap.replace(this,localLog);
                return true;
            }
            else
                return false; // TODO: must abort all, parent can't serialize.
        }
        else
            return true; // no problem to restart init, nothing to be done
    }

    public void updateVersionAfterInnerCommit(LocalStorage localStorage)
    {
        //TODO: is this necessary? I think the update upon abort suffices.
    }

    public ArrayList getLog()
    {
        ArrayList list = new ArrayList();
        for(Object item: log) list.add(item);
        return list;
//        return (ArrayList)Collections.unmodifiableList(log);
    }

    private void handleVersion(LocalStorage localStorage)
    {
        long readVersion = localStorage.readVersion;
        if (TX.CLOSED_NESTING && localStorage.TXnum > 1) {
            readVersion = localStorage.innerReadVersion;
        }

        if (readVersion < getVersion()) {
            if (TX.CLOSED_NESTING && localStorage.TXnum > 1) {
                localStorage.innerTX = false;
            }
            else
                localStorage.TX = false;
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();
        }
        if ((localStorage.readVersion == getVersion()) && (isSingleton())) {
            TX.incrementAndGetVersion();
            localStorage.TX = false;
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();
        }
    }

}
