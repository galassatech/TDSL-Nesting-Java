package TDSL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

public class TX {

	public static final boolean DEBUG_MODE_LL = false;
	public static final boolean DEBUG_MODE_QUEUE = false;
	public static final boolean DEBUG_MODE_TREE = false;
	public static final boolean DEBUG_MODE_TX = false;
	public static final boolean DEBUG_MODE_VERSION = false;
	public static final boolean FLAT_NESTING = false;
	public static final boolean CLOSED_NESTING = true;

	protected static ThreadLocal<LocalStorage> lStorage = ThreadLocal.withInitial(LocalStorage::new);

	private static AtomicLong GVC = new AtomicLong();

	protected static long getVersion() {
		return GVC.get();
	}

	protected static long incrementAndGetVersion() {
		return GVC.incrementAndGet();
	}

	public static void TXbegin() {

		if (DEBUG_MODE_TX) {
			System.out.println("TXbegin");
		}

		LocalStorage localStorage = lStorage.get();

		if (FLAT_NESTING || CLOSED_NESTING) {
			localStorage.TXnum++;
			if (CLOSED_NESTING && localStorage.TXnum > 1) { // nested
				localStorage.innerTX = true;
				if (localStorage.innerReadVersion >= localStorage.readVersion) {
					// this is after inner abort
					localStorage.innerReadVersion = getVersion();
					return;
				}
				localStorage.innerReadVersion = localStorage.readVersion;
				return;
			}
			if (localStorage.TXnum > 1) {
				// flat nesting, don't consume version again
				return;
			}
		}

		localStorage.TX = true;
		localStorage.readVersion = getVersion();

	}

	public static boolean TXend() throws TXLibExceptions.AbortException {

		if (DEBUG_MODE_TX) {
			System.out.println("TXend");
		}

		boolean abort = false;

		LocalStorage localStorage = lStorage.get();

		if (!localStorage.TX) {
			if (DEBUG_MODE_TX) {
				System.out.println("TXend - abort the TX");
			}
			abort = true;
		}

		if (CLOSED_NESTING && localStorage.abortAll) {
			abort = true;
		}

		if (FLAT_NESTING && localStorage.TXnum != 1) {
			localStorage.TXnum--;
			if (abort) {
				TXLibExceptions excep = new TXLibExceptions();
				if(TX.DEBUG_MODE_QUEUE) System.out.println(Thread.currentThread().getName() + " aborted");
				throw excep.new AbortException();
			}
			return true; // don't continue to perform TXend actions
		}

		if (CLOSED_NESTING && localStorage.TXnum != 1) {
			localStorage.TXnum--;
			if(TX.DEBUG_MODE_QUEUE) {
				System.out.println("isEmpty 1 = " + localStorage.queueMap.entrySet().size());
				System.out.println("isEmpty 2 = " + localStorage.innerQueueMap.entrySet().size());
			}

			if (!localStorage.innerTX) { // abort inner TX
				// TODO release queue locks even if aborted - Maybe done...
				for (Entry<Queue, LocalQueue> entry : localStorage.innerQueueMap.entrySet()) {
					Queue queue = entry.getKey();
					LocalQueue lQueue = entry.getValue();
					if (lQueue.isLockedByMe) assert(lQueue.isLockedByMe && lQueue.lockedByWhom > -1);
					if (lQueue.isLockedByMe && lQueue.lockedByWhom == localStorage.TXnum+1) {
						queue.unlock();
						lQueue.isLockedByMe = false;
					}
				}
				for (Entry<RBTree, LocalRBTree> entry : localStorage.innerRBTreeMap.entrySet())
				{
					if(entry.getValue().lockedSet == true)
						entry.getKey().unlock();
				}
				for (Log log : localStorage.innerLogLockedSet)
				{
					log.unlock();
				}
				clearAfterInnerAbort(localStorage);
				TXLibExceptions excep = new TXLibExceptions();
				throw excep.new AbortException();
			}
			if (localStorage.readVersion < localStorage.innerReadVersion) {
				// inner was aborted before, then validate
				HashSet<LNode> readSet = localStorage.readSet;
				for (LNode node : readSet) {
					if (node.isLocked()) {
						// someone else holds the lock
						abort = true;
						break;
					} else if (node.getVersion() > localStorage.readVersion) {
						abort = true;
						break;
					} else if (node.isSameVersionAndSingleton(localStorage.readVersion)) {
						incrementAndGetVersion(); // increment GVC
						node.setSingleton(false);
						if (DEBUG_MODE_VERSION) {
							System.out.println("singleton - increment GVC");
						}
						abort = true;
						break;
					}
				}
				if (abort) {
					// TODO release queue locks even if aborted
					for (Entry<Queue, LocalQueue> entry : localStorage.innerQueueMap.entrySet()) {
						Queue queue = entry.getKey();
						LocalQueue lQueue = entry.getValue();
                        if (lQueue.isLockedByMe) assert(lQueue.isLockedByMe && lQueue.lockedByWhom > -1): "me? " + lQueue.isLockedByMe +"\twhom? " + lQueue.lockedByWhom ;
						if (lQueue.isLockedByMe && lQueue.lockedByWhom == localStorage.TXnum+1) {
							queue.unlock();
							lQueue.isLockedByMe = false;
						}
					}
					for (Entry<RBTree, LocalRBTree> entry : localStorage.innerRBTreeMap.entrySet())
					{
						if(entry.getValue().lockedSet == true)
							entry.getKey().unlock();
					}

					for (Log log : localStorage.innerLogLockedSet)
					{
						log.unlock();
					}

					localStorage.abortAll = true;
					localStorage.innerReadVersion = 0L;
					clearAfterInnerAbort(localStorage);
					/*localStorage.innerQueueMap.clear();
					localStorage.innerRBTreeMap.clear();
					localStorage.innerWriteSet.clear();
					localStorage.innerReadSet.clear();
					localStorage.innerIndexAdd.clear();
					localStorage.innerIndexRemove.clear();
					localStorage.innerLogVersionMap.clear();
					localStorage.innerLogMap.clear();
					localStorage.innerTX = false;*/
					TXLibExceptions excep = new TXLibExceptions();
					throw excep.new NestedAbortException();
					// TODO catch abortExepction
					// if it is nested then rethrow
					// else continue
				}
				// jump to new readVersion
				localStorage.readVersion = localStorage.innerReadVersion;
			}

			for(Log l : localStorage.innerLogVersionMap.keySet())
			{
				if(!l.verifyNested())
				{
					abort = true;
				}
			}

			if(abort)
			{
				for(Log l : localStorage.innerLogVersionMap.keySet())
				{
					if(!l.verifyAfterInnerAbort(localStorage))
					{
						localStorage.abortAll = true;
					}
				}
				clearAfterInnerAbort(localStorage);
				if (localStorage.abortAll)
				{
					TXLibExceptions excep = new TXLibExceptions();
					throw excep.new NestedAbortException();
				}
				else {
					TXLibExceptions excep = new TXLibExceptions();
					throw excep.new AbortException();
				}
			}

			// commit inner
//			System.out.println("inner committing");
			// TODO handle queueMap
			localStorage.writeSet.putAll(localStorage.innerWriteSet);
			localStorage.readSet.addAll(localStorage.innerReadSet);
			// TODO not sure if all the index changes by inner are new
			for (Entry<LinkedList, ArrayList<LNode>> entry : localStorage.innerIndexAdd.entrySet()) {
				LinkedList ll = entry.getKey();
				if (localStorage.indexAdd.containsKey(ll)) {
					ArrayList<LNode> nodes = localStorage.indexAdd.get(ll);
					nodes.addAll(entry.getValue());
					localStorage.indexAdd.put(ll, nodes);
				} else {
					localStorage.indexAdd.put(ll, entry.getValue());
				}
			}
			for (Entry<LinkedList, ArrayList<LNode>> entry : localStorage.innerIndexRemove.entrySet()) {
				LinkedList ll = entry.getKey();
				if (localStorage.indexRemove.containsKey(ll)) {
					ArrayList<LNode> nodes = localStorage.indexRemove.get(ll);
					nodes.addAll(entry.getValue());
					localStorage.indexRemove.put(ll, nodes);
				} else {
					localStorage.indexRemove.put(ll, entry.getValue());
				}
			}
			// TODO : Merge Queues (add test before?) (MUST Have proper handshake for nodeToDeq)
			// TODO: if inner Queue had dequeued - update nodeToDeq and innerQueue content accordingly
			// TODO: if inner Queue had enqueuec - enque to relevant innerQueue
//			assert localStorage.innerQueueMap.isEmpty() <= 1 : "bad";
			for(Entry<Queue, LocalQueue> entry : localStorage.innerQueueMap.entrySet()){
				LocalQueue lq = entry.getValue();
				Queue q = entry.getKey();
				boolean queueWasAccessed = localStorage.queueMap.containsKey(q);
				LocalQueue ext_lq = (queueWasAccessed)? localStorage.queueMap.get(q):  new LocalQueue();
				//todo: pass nodeToDeQ correctly by innerQueue state
				//todo: handle firstDequeue
				if(lq.dequeState == LocalQueue.DequeState.NEVER){
//					System.out.println("never");
					ext_lq.enqueueNodes(lq);
				}
				else {
					ext_lq.firstDeq = false;
					if (lq.dequeState == LocalQueue.DequeState.EXTERNAL){
						if (TX.DEBUG_MODE_QUEUE) System.out.println("ext - Q");
						ext_lq.nodeToDeq = lq.nodeToDeq;
					}
					else if (lq.dequeState == LocalQueue.DequeState.PARENT_TX)
					{
						if (TX.DEBUG_MODE_QUEUE) System.out.println("parent");
						ext_lq.dequeueNodes(lq.nodeToDeq); //todo: should the be some prev/next?
					}
					else { // LocalQueue.DequeState.NESTED_TX
						if (TX.DEBUG_MODE_QUEUE) System.out.println("inner Q");
						ext_lq.dequeueNodes(ext_lq.head);

					}
					ext_lq.enqueueNodes(lq);
				}
				if(lq.isLockedByMe && !ext_lq.isLockedByMe )
                {
                    ext_lq.isLockedByMe = true;
                    ext_lq.lockedByWhom = localStorage.TXnum;
                }
				localStorage.queueMap.put(q, ext_lq);

			}

			HashMap<RBTree,LocalRBTree> parentMap =  localStorage.RBTreeMap;
			HashMap<RBTree,LocalRBTree> childMap =  localStorage.innerRBTreeMap;
			for (Entry<RBTree, LocalRBTree> entry : localStorage.innerRBTreeMap.entrySet())
			{
				LocalRBTree ptree = parentMap.get(entry.getKey());
				LocalRBTree ltree = childMap.get(entry.getKey());
				if(ptree == null)
				{
					ptree = new LocalRBTree();
				}
				ptree.mergeTrees(ltree);
				/*ptree.putMap.putAll(ltree.putMap);
				for(Integer key : ltree.removeMap)
				{
					ptree.putMap.remove(key);
					ptree.removeMap.add(key);
				}*/
				parentMap.put(entry.getKey(),ptree);
			}

			// merge Logs

			for(Entry<Log, LocalLog> entry : localStorage.innerLogMap.entrySet())
			{
				LocalLog parentLog;
				LocalLog innerLog = entry.getValue();
				Log key = entry.getKey();
				if(localStorage.logMap.containsKey(key))
				{
					parentLog = localStorage.logMap.get(key);
				}
				else {
					parentLog = new LocalLog(innerLog.version);
					localStorage.logVersionMap.put(key, localStorage.innerLogVersionMap.get(key));
				}
				parentLog.migrate(innerLog);
				localStorage.logMap.put(key, parentLog);
			}

			localStorage.logLockedSet.addAll(localStorage.innerLogLockedSet);

			clearAfterInnerAbort(localStorage);

			/*localStorage.innerQueueMap.clear();
			localStorage.innerRBTreeMap.clear();
			localStorage.innerReadSet.clear();
			localStorage.innerWriteSet.clear();
			localStorage.innerIndexAdd.clear();
			localStorage.innerIndexRemove.clear();
			localStorage.innerLogMap.clear();
			localStorage.innerLogVersionMap.clear();
			localStorage.innerTX = false;*/
			localStorage.innerReadVersion = 0L;
			return true;
		}

		// locking write set

		HashMap<LNode, WriteElement> writeSet = localStorage.writeSet;

		HashSet<LNode> lockedLNodes = new HashSet<>();

		if (!abort) {
			for (Entry<LNode, WriteElement> entry : writeSet.entrySet()) {
				LNode node = entry.getKey();
				if (!node.tryLock()) {
					abort = true;
					break;
				}
				lockedLNodes.add(node);
			}
		}
		
		// locking queues
		
		HashMap<Queue, LocalQueue> qMap = localStorage.queueMap;

		if (!abort) {
			for (Entry<Queue, LocalQueue> entry : qMap.entrySet()) {
				Queue queue = entry.getKey();
				if (!queue.tryLock()) { // if queue is locked by another thread
					abort = true;
					break;
				}
				LocalQueue lQueue = entry.getValue();
				lQueue.isLockedByMe = true;
				qMap.put(queue,lQueue);
			}
		}

/*		// locking Trees

		HashMap<RBTree, LocalRBTree> treeMap = localStorage.RBTreeMap;

		if (!abort) {
			for (Entry<RBTree, LocalRBTree> entry : treeMap.entrySet()) {
				RBTree tree = entry.getKey();
				if (!tree.tryLock()) { // if tree is locked by another thread
					abort = true;
					break;
				}
			}
		}*/


		//(Note) RBTrees are already locked (pessimistic DS).

		// No need to lock PCNodes

		//locking Logs
		if(!abort) {
			for (Log log : localStorage.logVersionMap.keySet()) {
				if (!log.tryLock()) { // if queue is locked by another thread
					abort = true;
					break;
				}
//			log.lock();

			}
		}
		// validate read set

		HashSet<LNode> readSet = localStorage.readSet;

		if (!abort) {
			for (LNode node : readSet) {
				if (!lockedLNodes.contains(node) && node.isLocked()) {
					// someone else holds the lock
					abort = true;
					break;
				} else if (node.getVersion() > localStorage.readVersion) {
					abort = true;
					break;
				} else if (node.isSameVersionAndSingleton(localStorage.readVersion)) {
					incrementAndGetVersion(); // increment GVC
					node.setSingleton(false);
					if (DEBUG_MODE_VERSION) {
						System.out.println("singleton - increment GVC");
					}
					abort = true;
					break;
				}
			}
		}

		// validate queue

		if (!abort) {
			for (Entry<Queue, LocalQueue> entry : qMap.entrySet()) {
				Queue queue = entry.getKey();
				if (queue.getVersion() > localStorage.readVersion) {
					abort = true;
					break;
				} else if (queue.getVersion() == localStorage.readVersion && queue.isSingleton()) {
					incrementAndGetVersion(); // increment GVC
					abort = true;
					break;
				}
			}
		}

		HashMap<RBTree, LocalRBTree> treeMap = localStorage.RBTreeMap;

		if(!abort)
		{
			for(Entry<RBTree, LocalRBTree> entry: treeMap.entrySet())
			{
				RBTree tree = entry.getKey();
				if (tree.getVersion() > localStorage.readVersion) {
					abort = true;
					break;
				} else if (tree.getVersion() == localStorage.readVersion && tree.isSingleton()) {
					incrementAndGetVersion(); // increment GVC
					abort = true;
					break;
				}
			}
		}

		// No need to validate PCPool

		// Validating Logs
		if(!abort) {
			for (Log log : localStorage.logVersionMap.keySet()) {
				if (!log.verify()) {
					abort = true;
//				System.out.println("verification failed");
				}
			}
		}

		// increment GVC

		long writeVersion = 0;

		if (!abort && !localStorage.readOnly) {
			writeVersion = incrementAndGetVersion();
			assert (writeVersion > localStorage.readVersion);
			localStorage.writeVersion = writeVersion;
		}

		// commit
		
		if (!abort && !localStorage.readOnly) {
			// LinkedList
			for (Entry<LNode, WriteElement> entry : writeSet.entrySet()) {
				LNode node = entry.getKey();
				WriteElement we = entry.getValue();
				node.next = we.next;
				node.val = we.val; // when node val changed because of put
				if (we.deleted) {
					node.setDeleted(true);
					node.val = null; // for index
				}
				node.setVersion(writeVersion);
				node.setSingleton(false);
			}
		}

		if (!abort) {
			// Queue
			for (Entry<Queue, LocalQueue> entry : qMap.entrySet()) {
				Queue queue = entry.getKey();
				LocalQueue lQueue = entry.getValue();
				queue.dequeueNodes(lQueue.nodeToDeq);
				queue.enqueueNodes(lQueue);
				if (TX.DEBUG_MODE_QUEUE) {
					System.out.println("commit queue before set version");
				}
				queue.setVersion(writeVersion);
				queue.setSingleton(false);
			}
		}
		if (!abort) {
			//RBT
			for (Entry<RBTree, LocalRBTree> entry : treeMap.entrySet()) {
				//TODO: when in separate lib - add readOnly flag
				RBTree tree = entry.getKey();
				LocalRBTree lTree = entry.getValue();
				if (!(lTree.removeMap.isEmpty() && lTree.putMap.isEmpty())) {
					tree.putAll(lTree.putMap);
					tree.removeAll(lTree.removeMap);
					tree.setVersion(writeVersion);
				}
				tree.setSingleton(false);
			}
		}
		if (!abort) {
			//PCPool
			for(PCNode node:localStorage.produced)
			{
				node.setState(PCNode.State.READY);
			}
			for(PCNode node:localStorage.consumed) {
				node.setState(PCNode.State.BOTTOM);
			}
		}
		else
		{
			//PCPool
			for(PCNode node:localStorage.produced)
			{
				node.setState(PCNode.State.BOTTOM);
			}
			for(PCNode node:localStorage.consumed) {
				node.setState(PCNode.State.READY);
			}
		}

		if(!abort)
		{
			//Log
			for(Log log: localStorage.logMap.keySet())
			{
				log.commit();
				log.setVersion(writeVersion);
			}
		}


		// release locks, even if abort

		lockedLNodes.forEach(LNode::unlock);

		for (Entry<Queue, LocalQueue> entry : qMap.entrySet()) {
			Queue queue = entry.getKey();
			LocalQueue lQueue = entry.getValue();
			if (lQueue.isLockedByMe || !abort) {
//				System.out.println(Thread.currentThread().getName() + " unlocking; abort = " + abort);
				queue.unlock();
				lQueue.isLockedByMe = false;
			}
		}

		for (RBTree tree: treeMap.keySet())
		{
			assert(treeMap.get(tree).lockedSet == true): "very bad";
			tree.unlock();
		}

		/*for(Log log: localStorage.logVersionMap.keySet())
		{
			if(localStorage.logMap.get(log).isLockedbyMe) {
				log.unlock();
				LocalLog localLog = localStorage.logMap.get(log);
				localLog.isLockedbyMe = false;
				localStorage.logMap.put(log,localLog);

			}
		}*/

		for(Log log : localStorage.logLockedSet)
		{
			log.unlock();
		}

		// update index

		if (!abort && !localStorage.readOnly) {
			// adding to index
			HashMap<LinkedList, ArrayList<LNode>> indexMap = localStorage.indexAdd;
			for (Entry<LinkedList, ArrayList<LNode>> entry : indexMap.entrySet()) {
				LinkedList list = entry.getKey();
				ArrayList<LNode> nodes = entry.getValue();
				nodes.forEach(node -> list.index.add(node));
			}
			// removing from index
			indexMap = localStorage.indexRemove;
			for (Entry<LinkedList, ArrayList<LNode>> entry : indexMap.entrySet()) {
				LinkedList list = entry.getKey();
				ArrayList<LNode> nodes = entry.getValue();
				nodes.forEach(node -> list.index.remove(node));
			}
		}

		// cleanup
		localStorage.innerQueueMap.clear();
		localStorage.innerRBTreeMap.clear();
		localStorage.queueMap.clear();
		localStorage.RBTreeMap.clear();
		localStorage.writeSet.clear();
		localStorage.readSet.clear();
		localStorage.indexAdd.clear();
		localStorage.indexRemove.clear();
		localStorage.consumed.clear();
		localStorage.produced.clear();
		localStorage.logMap.clear();
		localStorage.logVersionMap.clear();
		localStorage.innerLogMap.clear();
		localStorage.innerLogVersionMap.clear();
		localStorage.innerLogLockedSet.clear();
		localStorage.logLockedSet.clear();
		localStorage.TX = false;
		localStorage.readOnly = true;
		localStorage.abortAll = false;

		if (FLAT_NESTING || CLOSED_NESTING) {
			localStorage.TXnum--;
		}

		if (DEBUG_MODE_TX) {
			if (abort) {
				System.out.println("TXend - aborted");
			}
			System.out.println("TXend - is done");
		}

		if (abort) {
//			System.out.println(Thread.currentThread().getName() + " ext aborted");
			TXLibExceptions excep = new TXLibExceptions();
			throw excep.new AbortException();
			// TODO also catch nested abort
		}

		return true;

	}

	private static void clearAfterInnerAbort(LocalStorage localStorage) {

		localStorage.innerLogLockedSet.clear();
		localStorage.innerQueueMap.clear();
		localStorage.innerRBTreeMap.clear();
		localStorage.innerWriteSet.clear();
		localStorage.innerReadSet.clear();
		localStorage.innerIndexAdd.clear();
		localStorage.innerIndexRemove.clear();
		localStorage.innerLogVersionMap.clear();
		localStorage.innerLogMap.clear();
		localStorage.innerTX = false;
	}

	public static void TXbeginNested()
	{

	}

	public static void TXlock()
	{

	}

	public static void TXvalidate()
	{

	}

	public static void TXendNested()
	{

	}

}
