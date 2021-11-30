package TDSL;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Queue {
    //	private static final long lockMask = 0x2000000000000000L;
    private static final long singletonMask = 0x4000000000000000L;
    //	private static final long versionMask = lockMask | singletonMask;
    private static final long versionNegMask = singletonMask;
    private LockQueue qLock = new LockQueue();
    private QNode head;
    private QNode tail;
    private int size;
    //	// bit 61 is lock
    // bit 62 is singleton
    // 0 is false, 1 is true
    // we are missing a bit because this is signed
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
        qLock.lock();
    }

    protected boolean tryLock() {
        return qLock.tryLock();
    }

    protected void unlock() {
        qLock.unlock();
    }

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// another implementation of queueLock:
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// 	
//	private void lock() {
//		if(TX.lStorage.getTX() == true){
//			// locking only in singleton
//			if(TX.lStorage.getQueueMap().consume(this).isLockedByMe == true){
//				assert ((flags.consume() & lockMask) != 0);
//				assert(false); 
//				return;
//			}
//		}
//		long l, locked;
//		do {
//			l = flags.consume();
//			while ((l & lockMask) != 0) {
//				// wait till lock is free
//				l = flags.consume();
//			}
//			locked = l | lockMask;
//		} while (flags.compareAndSet(l, locked) == false);
//		if(TX.lStorage.getTX() == true){
//			HashMap<Queue, LocalQueue> qMap = TX.lStorage.getQueueMap();
//			LocalQueue lQueue = qMap.consume(this);
//			if(lQueue == null){
//				lQueue = new LocalQueue();
//			}
//			lQueue.isLockedByMe = true;
//			qMap.put(this, lQueue);
//			TX.lStorage.setQueueMap(qMap);
//			assert ((flags.consume() & lockMask) != 0);
//		}
//	}

//	protected boolean tryLock() {
//		if(TX.lStorage.getTX() == true){
//			HashMap<Queue, LocalQueue> qMap = TX.lStorage.getQueueMap();
//			LocalQueue lQueue = qMap.consume(this);
//			if(lQueue != null){
//				if(TX.lStorage.getQueueMap().consume(this).isLockedByMe == true){
//					if(TX.DEBUG_MODE_QUEUE){
//						System.out.println("queue try lock - I have the lock");
//					}
//					assert ((flags.consume() & lockMask) != 0);
//					return true;
//				}
//			}
//		}
//		long l = flags.consume();
//		if ((l & lockMask) != 0) {
//			return false;
//		}
//		long locked = l | lockMask;
//		if(TX.DEBUG_MODE_QUEUE){
//			System.out.println("queue try lock - this is the locked " + locked);
//		}
//		if(flags.compareAndSet(l, locked)==true){
//			if(TX.lStorage.getTX() == true){
//				HashMap<Queue, LocalQueue> qMap = TX.lStorage.getQueueMap();
//				LocalQueue lQueue = qMap.consume(this);
//				if(lQueue == null){
//					lQueue = new LocalQueue();
//				}
//				lQueue.isLockedByMe = true;
//				qMap.put(this, lQueue);
//				TX.lStorage.setQueueMap(qMap);
//			}
//			if(TX.DEBUG_MODE_QUEUE){
//				System.out.println("queue try lock - managed to lock");
//			}
//			assert ((flags.consume() & lockMask) != 0);
//			return true;
//		}
//		return false;
//	}
//
//	protected void unlock() {
//		long l = flags.consume();
//		assert ((l & lockMask) != 0);
//		if(TX.lStorage.getTX() == true){
//			assert(TX.lStorage.getQueueMap().consume(this).isLockedByMe == true);
//		}
//		long unlocked = l & (~lockMask);
//		assert (flags.compareAndSet(l, unlocked) == true);
//		if(TX.lStorage.getTX() == true){
//			HashMap<Queue, LocalQueue> qMap = TX.lStorage.getQueueMap();
//			LocalQueue lQueue = qMap.consume(this);
//			if(lQueue == null){
//				lQueue = new LocalQueue();
//			}
//			lQueue.isLockedByMe = false;
//			qMap.put(this, lQueue);
//			TX.lStorage.setQueueMap(qMap);
//		}
//	}

    protected void enqueueNodes(LocalQueue lQueue) {
        assert (lQueue != null);
        if (TX.DEBUG_MODE_QUEUE) {
            System.out.println("Queue enqueueNodes");
        }
        try {
            while (!lQueue.isEmpty()) {
                if (TX.DEBUG_MODE_QUEUE) {
                    System.out.println("Queue enqueueNodes - lQueue is not empty");
                }
                QNode node = new QNode();
                node.val = lQueue.dequeue();
                if (TX.DEBUG_MODE_QUEUE) {
                    System.out.println("Queue enqueueNodes - lQueue node val is " + node.val);
                }
                node.next = null;
                node.prev = tail;
                size++;
                if (tail == null) {
                    tail = node;
                    head = node;
                } else {
                    tail.next = node;
                    tail = node;
                }
            }
        } catch (TXLibExceptions.QueueIsEmptyException e) {
            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue enqueueNodes - local queue is empty");
            }
        }

    }

    protected void dequeueNodes(QNode upToNode) {

        if (upToNode == null) {
            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue dequeueNodes - upToNode is null");
            }
            return;
        }

        if (TX.DEBUG_MODE_QUEUE) {
            System.out.println("Queue dequeueNodes");
        }

        QNode curr = head;
        while (curr != null && curr != upToNode.next) {
            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue dequeueNodes - dequeueing");
            }
            size--;
            curr = curr.next;
        }
        head = curr;
        if (curr == null) {
            tail = null;
            assert (size == 0);
        }
    }

    public void enqueue(Object val) throws TXLibExceptions.AbortException {

        LocalStorage localStorage = TX.lStorage.get();

        // SINGLETON
        if (!localStorage.TX) {

            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue enqueue - singleton @ " + Thread.currentThread().getName());
            }

            QNode node = new QNode();
            node.val = val;
            node.next = null;
            node.prev = tail;

            lock();
            size++;
            if (tail == null) {
                tail = node;
                head = node;

            } else {
                tail.next = node;
                tail = node;
            }

            setVersion(TX.getVersion());
            setSingleton(true);

            unlock();
            return;
        }

        // TX

        if (TX.DEBUG_MODE_QUEUE) {
            System.out.println("Queue enqueue - in TX");
        }

        //TODO: handle (Closed) nestedTX
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

        HashMap<Queue, LocalQueue> qMap;
        if(TX.CLOSED_NESTING && localStorage.TXnum >1 )
            qMap = localStorage.innerQueueMap;
        else
            qMap = localStorage.queueMap;
        LocalQueue lQueue = qMap.get(this);
        if (lQueue == null) {
            lQueue = new LocalQueue();
        }
        lQueue.enqueue(val);
        qMap.put(this, lQueue);

    }

    public Object dequeue() throws TXLibExceptions.QueueIsEmptyException, TXLibExceptions.AbortException {

        LocalStorage localStorage = TX.lStorage.get();

        // SINGLETON
        if (!localStorage.TX) {

            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue dequeue - singleton @ " + Thread.currentThread().getName());
            }

            lock();
            if (head == null) {
                unlock();
                TXLibExceptions excep = new TXLibExceptions();
                throw excep.new QueueIsEmptyException();
            }
            QNode temp = head;
            Object ret = temp.val;
            head = head.next;
            if (head == null) {
                tail = null;
            } else {
                head.prev = null;
            }
            size--;
            setVersion(TX.getVersion());
            setSingleton(true);
            unlock();
            return ret;

        }

        // TX

        if (TX.DEBUG_MODE_QUEUE) {
            System.out.println("Queue dequeue - in TX");
        }

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

        /*if (localStorage.readVersion < getVersion()) {
            localStorage.TX = false;
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();
        }*/
        if ((localStorage.readVersion == getVersion()) && (isSingleton())) {
            TX.incrementAndGetVersion();
            localStorage.TX = false;
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();
        }

        if (!tryLock()) { // if queue is locked by another thread
            if(!(TX.CLOSED_NESTING && localStorage.TXnum > 1)) { // flat or parent
                localStorage.TX = false; // todo: tx?
                if (TX.DEBUG_MODE_QUEUE) System.out.println("couldn't acquire lock @ " + Thread.currentThread().getName());
            }
            else
            {
                if (TX.DEBUG_MODE_QUEUE) System.out.println("couldn't acquire lock @ " + Thread.currentThread().getName());
                localStorage.innerTX = false;
//                localStorage.TX = false; // todo: check if necessary
            }
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();

        }

        // now we have the lock

        // handling inner TX
        if(TX.CLOSED_NESTING && localStorage.TXnum > 1)
        {
            // TODO: call method that finds the right nodeToDeq - this should be called EVERY TIME
            // when the "original" queue is (speculatively) empty - start dequeueing from loclQueue
            // when localQueue is empty - start dequeueing from innerLocal
            HashMap<Queue, LocalQueue> qMap = localStorage.innerQueueMap;
            LocalQueue lQueue = qMap.get(this);
            if (lQueue == null) {
                lQueue = new LocalQueue();
                qMap.put(this, lQueue);
            }

//            lQueue.isLockedByMe = true;
            manageLock(localStorage,lQueue);


            getNodeToDeq(lQueue, localStorage);
            /*
            if (lQueue.firstDeq) { //understand where to start dequeueing from
                if (TX.DEBUG_MODE_QUEUE) {
                    System.out.println("Queue dequeue - first dequeue");
                }
                // if this is the first dequeue then try to dequeue the tail
                lQueue.firstDeq = false;
                HashMap<Queue, LocalQueue> qMap_ext = localStorage.queueMap;
                LocalQueue lQueue_ext = qMap_ext.consume(this);
//                lQueue.dequeState = LocalQueue.DequeState.PARENT_TX;
                if (lQueue_ext == null) {
                    // parent tx never accessed this queue
                    lQueue.nodeToDeq = head;
                    lQueue.dequeState = LocalQueue.DequeState.EXTERNAL;
                    if(lQueue.nodeToDeq == null)
                    { //try using local (nested) queue
//                        lQueue.nodeToDeq = lQueue.head;
                        lQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
                        qMap.put(this, lQueue);
                        return lQueue.dequeue();
                    }

                }else
                {//Parent TX did access this queue
                    if(lQueue_ext.firstDeq == true)
                    { // parent TX never dequeued
                        lQueue.nodeToDeq = head;
                        lQueue.dequeState = LocalQueue.DequeState.EXTERNAL;
                        if(lQueue.nodeToDeq == null){
                            lQueue.nodeToDeq = lQueue_ext.head;
                            if(lQueue.nodeToDeq == null)
                            {
                                lQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
                                qMap.put(this, lQueue);
                                return lQueue.dequeue();
                            }
                            lQueue.dequeState = LocalQueue.DequeState.PARENT_TX;
                        }
                    }else
                    {
//                        if(lQueue_ext.nodeToDeq.next)
                        lQueue.nodeToDeq = lQueue_ext.nodeToDeq.next;
                        lQueue.dequeState = LocalQueue.DequeState.EXTERNAL;
                        if(lQueue.nodeToDeq == null){
                            lQueue.nodeToDeq = lQueue_ext.head;
                            if(lQueue.nodeToDeq == null)
                            {
                                lQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
                                qMap.put(this, lQueue);
                                return lQueue.dequeue();
                            }
                            lQueue.dequeState = LocalQueue.DequeState.PARENT_TX;
                        }
                    }
                }
//                lQueue.nodeToDeq = head;
//                lQueue.dequeState = LocalQueue.DequeState.EXTERNAL;
//                if (lQueue.nodeToDeq == null) {
//                    //try grabbing parent TX lQueues head
//                    HashMap<Queue, LocalQueue> qMap_ext = localStorage.queueMap;
//                    LocalQueue lQueue_ext = qMap_ext.consume(this);
//                    lQueue.dequeState = LocalQueue.DequeState.PARENT_TX;
//                    if (lQueue_ext != null) {
//                        lQueue.nodeToDeq = lQueue_ext.head;
//                        // TODO: should there be "next" here as well??
//                        lQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
//                    }
//                }
            } else if (lQueue.nodeToDeq != null) { //not first deQ, there are nodes to be deQed
                lQueue.nodeToDeq = lQueue.nodeToDeq.next;
            } else
            { // not first deq, nodeToDeq is null - check where I am in hierarchy
                assert lQueue.dequeState != LocalQueue.DequeState.NEVER: "impossible";
                while(lQueue.nodeToDeq == null) {
                    switch (lQueue.dequeState) {
                        case EXTERNAL: {
                            lQueue.dequeState = LocalQueue.DequeState.PARENT_TX;
                            HashMap<Queue, LocalQueue> qMap_ext = localStorage.queueMap;
                            LocalQueue lQueue_ext = qMap_ext.consume(this);
                            if (lQueue_ext == null) break;
                            lQueue.nodeToDeq = lQueue_ext.head;
                            break;
                        } // go to
                        case PARENT_TX: {
                            lQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
                            return lQueue.dequeue();
                        }
                        default:
                            lQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
                            break; // todo: queue is empty - but abort should happen a little later (make default)
                    }
                }
            }

            if (lQueue.nodeToDeq != null) { // dequeue from the queue or parent TX queue
                if (TX.DEBUG_MODE_QUEUE) System.out.println("Trying to deq from higher Queue");
                Object ret = lQueue.nodeToDeq.val;
                lQueue.isLockedByMe = true;
                if(lQueue.lockedByWhom == -1)
                    lQueue.lockedByWhom = TX.lStorage.consume().TXnum;
                qMap.put(this, lQueue);
                return ret;
            }
            */

            //By now - we either got an empty queue exception or we have a valid node to dequeue
            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue dequeue - nodeToDeq isn't null");
            }

            // there is no node in queue, then try the localQueue
/*            lQueue.isLockedByMe = true; TODO: GAL
            if(lQueue.lockedByWhom == -1)
                lQueue.lockedByWhom = TX.lStorage.consume().TXnum;*/
            qMap.put(this, lQueue);

            if(lQueue.dequeState == LocalQueue.DequeState.EXTERNAL || lQueue.dequeState == LocalQueue.DequeState.PARENT_TX)
                return lQueue.nodeToDeq.val;
            return lQueue.dequeue(); // can throw an exception
        }
        //

        HashMap<Queue, LocalQueue> qMap = localStorage.queueMap;
        LocalQueue lQueue = qMap.get(this);
        // Handle locking records

        //
        if (lQueue == null) {
            lQueue = new LocalQueue();
        }

        if (lQueue.firstDeq) {
            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue dequeue - first dequeue");
            }
            // if this is the first dequeue then try to dequeue the tail
            lQueue.firstDeq = false;
            lQueue.nodeToDeq = head;
        } else if (lQueue.nodeToDeq != null) {
            lQueue.nodeToDeq = lQueue.nodeToDeq.next;
        }

        if (lQueue.nodeToDeq != null) { // dequeue from the queue

            Object ret = lQueue.nodeToDeq.val;
            manageLock(localStorage,lQueue);
            /*lQueue.isLockedByMe = true;
            if(lQueue.lockedByWhom == -1)
                lQueue.lockedByWhom = TX.lStorage.consume().TXnum;*/
            qMap.put(this, lQueue);

            return ret;
        }

        if (TX.DEBUG_MODE_QUEUE) {
            System.out.println("Queue dequeue - nodeToDeq is null");
        }

        // there is no node in queue, then try the localQueue
        manageLock(localStorage,lQueue);
        /*lQueue.isLockedByMe = true;
        if(lQueue.lockedByWhom == -1)
            lQueue.lockedByWhom = TX.lStorage.consume().TXnum;*/
        qMap.put(this, lQueue);
        return lQueue.dequeue(); // can throw an exception

    }

    private void getNodeToDeq(LocalQueue localQueue, LocalStorage localStorage) throws TXLibExceptions.QueueIsEmptyException{
        //TODO: delete everything, code like waterfall (no return at all)
        //TODO(2): add dbg souts
        if(localQueue.dequeState == LocalQueue.DequeState.NEVER)
        { // no need to look @ nodeToDeq.next
            assert localQueue.firstDeq: "localQueue.firstDeq is False but DeqState is NEVER";
            localQueue.firstDeq = false;
            HashMap<Queue, LocalQueue> qMap_ext = localStorage.queueMap;
            LocalQueue lQueue_ext = qMap_ext.get(this);
            if(lQueue_ext == null)
            {// parent TX never accessed this Queue - grab this Queue's head
                localQueue.nodeToDeq = this.head;
                localQueue.dequeState = LocalQueue.DequeState.EXTERNAL;
            }
            else
            {//parent TX did access this Queue
                if(lQueue_ext.nodeToDeq != null)
                {
                    if(lQueue_ext.nodeToDeq.next != null)
                    {
                        localQueue.nodeToDeq = lQueue_ext.nodeToDeq.next;
                        localQueue.dequeState = LocalQueue.DequeState.EXTERNAL;
                    }
                    else
                    {
                        localQueue.nodeToDeq = lQueue_ext.head;
                        localQueue.dequeState = LocalQueue.DequeState.PARENT_TX;
                    }
                }
                else
                {
                    localQueue.nodeToDeq = lQueue_ext.head;
                    localQueue.dequeState = LocalQueue.DequeState.PARENT_TX;
                }
            }
            if(localQueue.nodeToDeq != null) {
                return;
            }
            else{
                TXLibExceptions e = new TXLibExceptions();
                throw e.new QueueIsEmptyException();
            }
        }

        if(localQueue.dequeState == LocalQueue.DequeState.EXTERNAL)
        {
		if(localQueue.nodeToDeq == null) {
                TXLibExceptions e = new TXLibExceptions();
                throw e.new QueueIsEmptyException();
            }
            if(localQueue.nodeToDeq.next == null)
            {// out of elements in this Queue, try going to parent TX
                HashMap<Queue, LocalQueue> qMap_ext = localStorage.queueMap;
                LocalQueue lQueue_ext = qMap_ext.get(this);
                if(lQueue_ext == null)
                {// parent TX never accessed this Queue - goto localQueue
                    localQueue.nodeToDeq = localQueue.head;
                    localQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
                    if(localQueue.nodeToDeq == null)
                    {
                        TXLibExceptions e = new TXLibExceptions();
                        throw e.new QueueIsEmptyException();
                    }
                }
                else
                {// parent tx had accessed this Queue
                    localQueue.nodeToDeq = lQueue_ext.head;
                    if(localQueue.nodeToDeq != null)
                    {
                        localQueue.dequeState = LocalQueue.DequeState.PARENT_TX;
                        return;
                    }
                    else{
                        if(localQueue.head == null)
                        {
                            TXLibExceptions e = new TXLibExceptions();
                            throw e.new QueueIsEmptyException();
                        }
                        localQueue.nodeToDeq = localQueue.head;
                        localQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
                        return;
                    }
                }
            }
            else { // OK to proceed with this Queue
                localQueue.nodeToDeq = localQueue.nodeToDeq.next;
                return;
            }

        }

        if(localQueue.dequeState == LocalQueue.DequeState.PARENT_TX)
        {//This Queue is (speculatively) empty, parent tx lQueue exists
            if(localQueue.nodeToDeq.next == null)
            {//parent tx lQueue is (speculatively) empty - try deq'ing from nested lQueue
                localQueue.nodeToDeq = localQueue.head;
                localQueue.dequeState = LocalQueue.DequeState.NESTED_TX;
                if(localQueue.nodeToDeq == null)
                {
                    TXLibExceptions e = new TXLibExceptions();
                    throw e.new QueueIsEmptyException();
                }
                return;
            }
            else{//keep using parent tx's Queue
                localQueue.nodeToDeq = localQueue.nodeToDeq.next;
                return;
            }
        }

        if(localQueue.dequeState == LocalQueue.DequeState.NESTED_TX){
            //dequeueing from nested TX local Queue
            localQueue.nodeToDeq = localQueue.head;
            if(localQueue.nodeToDeq == null)
            {
                TXLibExceptions e = new TXLibExceptions();
                throw e.new QueueIsEmptyException();
            }
        }
    }

    public boolean isEmpty() throws TXLibExceptions.AbortException {

        LocalStorage localStorage = TX.lStorage.get();

        // SINGLETON
        if (!localStorage.TX) {
            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue isEmpty - singleton");
            }
            int ret = size;
            lock();
            setVersion(TX.getVersion());
            setSingleton(true);
            unlock();
            return (ret <= 0);
        }

        // TX
        if (TX.DEBUG_MODE_QUEUE) {
            System.out.println("Queue isEmpty - in TX");
        }

        if (TX.DEBUG_MODE_QUEUE) {
            System.out.println("Queue isEmpty - now not locked by me");
        }

        if (localStorage.readVersion < getVersion()) {
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
        //TODO: handle nesting
        if (!tryLock()) { // if queue is locked by another thread

            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue isEmpty - couldn't lock");
            }

            localStorage.TX = false;
            TXLibExceptions excep = new TXLibExceptions();
            throw excep.new AbortException();

        }

        // now we have the lock
        if (size > 0) {
            return false;
        }

        // check lQueue
        HashMap<Queue, LocalQueue> qMap = localStorage.queueMap;
        LocalQueue lQueue = qMap.get(this);
        if (lQueue == null) {
            lQueue = new LocalQueue();
        }
        qMap.put(this, lQueue);

        return lQueue.isEmpty();

    }

    public int getSize(){
        return size;
    }

    public void manageLock(LocalStorage localStorage,LocalQueue child)
    {
        LocalQueue parent;
        parent = localStorage.queueMap.get(this);
        if(null == parent){
            child.lockedByWhom = localStorage.TXnum;
        }
        else
        {
            if(parent.isLockedByMe)
            {
                child.lockedByWhom = parent.lockedByWhom;
            }
            else
            {
                child.lockedByWhom = localStorage.TXnum;
            }
        }
        child.isLockedByMe = true;
    }

}
