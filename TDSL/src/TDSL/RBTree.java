package TDSL;

import java.util.HashSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public class RBTree {
    private TreeMap<Integer, Object> rbTree = new TreeMap();
//    private ConcurrentSkipListMap<Integer, Object> rbTree = new ConcurrentSkipListMap(); TODO: consider
    private LockQueue lock = new LockQueue();
    private static final long singletonMask = 0x4000000000000000L;
    //	private static final long versionMask = lockMask | singletonMask;
    private static final long versionNegMask = singletonMask;
    //TODO: add vc
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

    private void lock() {
        lock.lock();
    }

    protected boolean tryLock() {
        return lock.tryLock();
    }

    protected void unlock() {
        lock.unlock();
    }

    protected void putAll(TreeMap<Integer, ? > tree)
    {
        rbTree.putAll(tree);
    }

    protected void removeAll(HashSet<Integer> removeMap) {
        for(Integer key: removeMap)
            rbTree.remove(key);
    }

    public Object get(Integer key) throws TXLibExceptions.AbortException {
        //TODO: check VC, handle singleton
        LocalStorage localStorage = TX.lStorage.get();

        if (!localStorage.TX) {

            if (TX.DEBUG_MODE_TREE) {
                System.out.println("Tree consume - singleton @ " + Thread.currentThread().getName());
            }

            lock();
            Object ret = rbTree.get(key);
            setVersion(TX.getVersion());
            setSingleton(true);
            unlock();
            return ret;

        }
        long readVersion = checkVersion(localStorage);
        tryLock_ext(localStorage);
        handleLockRecord(localStorage);
        if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
        { // nested: check if existing in some Ltree or in (Gtree & not deleted)
            LocalRBTree n_tree = localStorage.innerRBTreeMap.get(this);
            if(n_tree == null)
            {
                n_tree = new LocalRBTree();
            }
            localStorage.innerRBTreeMap.put(this,n_tree);
            Object val = n_tree.putMap.get(key);
            if(val != null)
                return val;
            if(n_tree.removeMap.contains(key))
                return null;
            return getFromAncestorTree(key,localStorage);


            /* pessimistic consume
            if(n_tree != null)
            {
                Object val = n_tree.putMap.consume(key);
                if(val != null)
                    return val;
                if(n_tree.removeMap.contains(key))
                    return null;
            }
            else
            {
                LocalRBTree p_tree = localStorage.RBTreeMap.consume(this);
                if(p_tree != null)
                {
                    Object val = p_tree.putMap.consume(key);
                    if(val != null)
                        return val;
                    if(p_tree.removeMap.contains(key))
                        return null;
                }
                else
                {
                    //todo: create innerTree? perhaps not (tree is locked, nothing to update)
                    return rbTree.consume(key);
                }
            }
            pessimistic consume*/
        }
        else
        {// not nested or flat nesting
            LocalRBTree p_tree = localStorage.RBTreeMap.get(this);
            if(p_tree == null)
            {
                p_tree = new LocalRBTree();
            }
            Object val = p_tree.putMap.get(key);
            if(val != null)
                return val;
            if(p_tree.removeMap.contains(key))
                return null;
            return rbTree.get(key);
            /*Pessimistic
            *
            LocalRBTree p_tree = localStorage.RBTreeMap.consume(this);
            if(p_tree != null)
            {
                Object val = p_tree.putMap.consume(key);
                if(val != null)
                    return val;
                if(p_tree.removeMap.contains(key))
                    return null;
            }
            else
            {
                return rbTree.consume(key);
            }
            *
            * Pessimistic */

        }
    }


    public Object remove(Integer key)
    {
        LocalStorage localStorage = TX.lStorage.get();

        if (!localStorage.TX) {

            if (TX.DEBUG_MODE_TREE) {
                System.out.println("Tree remove - singleton @ " + Thread.currentThread().getName());
            }

            lock();
            Object ret = rbTree.remove(key);
            setVersion(TX.getVersion());
            setSingleton(true);
            unlock();
            return ret;

        }
        long readVersion = checkVersion(localStorage);
        tryLock_ext(localStorage);
        handleLockRecord(localStorage);
        if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
        { // nested: check if existing in some Ltree or in (Gtree & not deleted)
            LocalRBTree n_tree = localStorage.innerRBTreeMap.get(this);
            if(n_tree != null)
            {
                n_tree.removeMap.add(key);
                Object val = n_tree.putMap.remove(key);
                localStorage.innerRBTreeMap.put(this,n_tree);
                if(val != null) {
                    return val;
                }
                else {
                    return getFromAncestorTree(key, localStorage);
                }
            }
            else
            {
                n_tree = new LocalRBTree();
                n_tree.removeMap.add(key);
                localStorage.innerRBTreeMap.put(this, n_tree);
                return getFromAncestorTree(key, localStorage);
            }
        }
        else
        {// not nested or flat nesting
            LocalRBTree p_tree = localStorage.innerRBTreeMap.get(this);
            if(p_tree != null)
            {
                Object val = p_tree.putMap.remove(key);
                if(val != null)
                    return val;
                val = p_tree.removeMap.add(key);
                localStorage.RBTreeMap.put(this,p_tree);
                return rbTree.get(key);
            }
            else
            {
                p_tree = new LocalRBTree();
                p_tree.removeMap.add(key);
                localStorage.RBTreeMap.put(this,p_tree);
                return rbTree.get(key);
            }
        }
    }

    public boolean contains(Integer key)
    {
        LocalStorage localStorage = TX.lStorage.get();

        if (!localStorage.TX) {

            if (TX.DEBUG_MODE_TREE) {
                System.out.println("Tree contains - singleton @ " + Thread.currentThread().getName());
            }

            lock();
            boolean ret = rbTree.containsKey(key);
            setVersion(TX.getVersion());
            setSingleton(true);
            unlock();
            return ret;

        }
        long readVersion = checkVersion(localStorage);
        tryLock_ext(localStorage);
        handleLockRecord(localStorage);
        //TODO: hierarchically check if is in putMap (and then !in rmvMap)
        if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
        {
            LocalRBTree n_tree = localStorage.innerRBTreeMap.get(this);
            if(n_tree != null) {
                if (n_tree.removeMap.contains(key))
                    return false;
                else {
                    if (n_tree.putMap.containsKey(key))
                        return true;
                }
            }
        }
        LocalRBTree p_tree = localStorage.innerRBTreeMap.get(this);
        if(p_tree != null)
        {
            if(p_tree.removeMap.contains(key))
                return false;
            if(p_tree.putMap.containsKey(key))
                return true;
        }
        return rbTree.containsKey(key);
    }

    public boolean put(Integer key, Object val)
    {
        LocalStorage localStorage = TX.lStorage.get();

        if (!localStorage.TX) {

            if (TX.DEBUG_MODE_TREE) {
                System.out.println("Tree put - singleton @ " + Thread.currentThread().getName());
            }

            lock();
            rbTree.put(key,val);
            setVersion(TX.getVersion());
            setSingleton(true);
            unlock();
            return true;
        }
        long readVersion = checkVersion(localStorage);
        tryLock_ext(localStorage);
        handleLockRecord(localStorage);
        if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
        {
            LocalRBTree n_tree = localStorage.innerRBTreeMap.get(this);
            n_tree = putLocalRBTree(key, val, n_tree);
            localStorage.innerRBTreeMap.put(this, n_tree);
            if(!localStorage.RBTreeMap.containsKey(this)) // locked by inner

            return true;
        }
        LocalRBTree p_tree = localStorage.RBTreeMap.get(this);
        p_tree = putLocalRBTree(key, val, p_tree);
        localStorage.RBTreeMap.put(this, p_tree);
        return true;
    }

    private LocalRBTree putLocalRBTree(Integer key, Object val, LocalRBTree localRBTree) {
        if(localRBTree == null)
        {
            localRBTree = new LocalRBTree();
        }
        if(localRBTree.removeMap.contains(key))
        {
            localRBTree.removeMap.remove(key);
        }
        localRBTree.putMap.put(key,val);
        return localRBTree;
    }



    protected void tryLock_ext(LocalStorage localStorage) throws TXLibExceptions.AbortException {
        if (!tryLock()) { // if tree is locked by another thread
            if(!(TX.CLOSED_NESTING && localStorage.TXnum > 1)) {
                localStorage.TX = false;
                if (TX.DEBUG_MODE_TREE) System.out.println("couldn't acquire lock @ " + Thread.currentThread().getName());
            }
            else
            {
                if (TX.DEBUG_MODE_TREE) System.out.println("couldn't acquire lock @ " + Thread.currentThread().getName());
                localStorage.innerTX = false;
            }
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();
        }
    }

    protected Object getFromAncestorTree(Integer key, LocalStorage localStorage) {
        assert (TX.CLOSED_NESTING && localStorage.TXnum > 1):"Shouldn't be here";
        LocalRBTree p_tree = localStorage.RBTreeMap.get(this);
        if(p_tree != null) {
            Object val = p_tree.putMap.get(key);
            if(val != null)
                return val;
        }
        return rbTree.get(key);
    }


    private long checkVersion (LocalStorage localStorage)
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
        return readVersion;
    }

    private void handleLockRecord(LocalStorage localStorage)
    {
        if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
        {
            LocalRBTree n_tree = localStorage.innerRBTreeMap.get(this);
            if(n_tree != null) // this method had been called, no need to continue
                return;
            else
            {
                LocalRBTree p_tree = localStorage.RBTreeMap.get(this);
                n_tree = new LocalRBTree();
                if (p_tree == null) // already locked by ongoing transaction
                    n_tree.lockedSet = true;
                localStorage.innerRBTreeMap.put(this,n_tree);
            }
        }
        else
        {
            LocalRBTree p_tree = localStorage.RBTreeMap.get(this);
            if(p_tree != null) // this method had been called, no need to continue
                return;
            else
            {
                p_tree = new LocalRBTree();
                p_tree.lockedSet = true;
                localStorage.RBTreeMap.put(this,p_tree);
            }
        }
    }

}
