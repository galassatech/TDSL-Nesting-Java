package TDSL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

public class LocalStorage {

	protected int TXnum = 0; // for nesting

	protected long readVersion = 0L;
	protected long writeVersion = 0L; // for debug
	protected boolean TX = false;
	protected boolean readOnly = true;
	protected HashMap<Queue, LocalQueue> queueMap = new HashMap<>();
	protected HashMap<LNode, WriteElement> writeSet = new HashMap<>();
	protected HashSet<LNode> readSet = new HashSet<>();
	protected HashMap<LinkedList, ArrayList<LNode>> indexAdd = new HashMap<>();
	protected HashMap<LinkedList, ArrayList<LNode>> indexRemove = new HashMap<>();
	protected HashMap<RBTree, LocalRBTree> RBTreeMap = new HashMap<>();
	protected ArrayList<PCNode> produced = new ArrayList<>();
	protected HashSet<PCNode> consumed = new HashSet<>();
	protected HashMap<PCNode,PCNode> producedAfterConsuming = new HashMap<>();
	protected HashMap<Log,Integer> logVersionMap = new HashMap<>();
	protected HashSet<Log> logLockedSet = new HashSet<>();
	protected HashMap<Log,LocalLog> logMap = new HashMap<>();



	// with ArrayList all nodes will be added to the list
	// (no compression needed)
	// later, when we add the nodes to the index,
	// the latest node that was added to this list
	// will be the last update to the node in the index

	protected void printIndexAdd() { // for debug
		System.out.println(Thread.currentThread().getName() + " printIndexAdd");
		for (Entry<LinkedList, ArrayList<LNode>> e : indexAdd.entrySet()) {
			ArrayList<LNode> a = e.getValue();
			for (LNode l : a) {
				System.out.println(Thread.currentThread().getName() + " " + l + " " + l.key);
			}
		}
	}

	protected void printIndexRemove() { // for debug
		System.out.println(Thread.currentThread().getName() + " printIndexRemove");
		for (Entry<LinkedList, ArrayList<LNode>> e : indexRemove.entrySet()) {
			ArrayList<LNode> a = e.getValue();
			for (LNode l : a) {
				System.out.println(Thread.currentThread().getName() + " " + l + " " + l.key);
			}
		}
	}

	protected void printWriteSet() { // for debug
		System.out.println(Thread.currentThread().getName() + " printWriteSet");
		for (Entry<LNode, WriteElement> e : writeSet.entrySet()) {
			LNode n = e.getKey();
			WriteElement w = e.getValue();
			if (w.deleted) {
				System.out.println(Thread.currentThread().getName() + " " + n + " " + n.key + " delete");
				continue;
			}
			System.out.print(Thread.currentThread().getName() + " " + n + " " + n.key);
			if (w.next != null) {
				System.out.print(" next is " + w.next + " " + w.next.key);
			}
			System.out.println();
		}
	}

	protected void printReadSet() { // for debug
		System.out.println(Thread.currentThread().getName() + " printReadSet");
		for (LNode n : readSet) {
			System.out.println(Thread.currentThread().getName() + " " + n + " " + n.key);
		}
	}

	protected void putIntoWriteSet(LNode node, LNode next, Object val, boolean deleted) {
		WriteElement we = new WriteElement();
		we.next = next;
		we.deleted = deleted;
		we.val = val;
		writeSet.put(node, we);
	}

	protected void addToIndexAdd(LinkedList list, LNode node) {
		ArrayList<LNode> nodes = indexAdd.get(list);
		if (nodes == null) {
			nodes = new ArrayList<>();
		}
		nodes.add(node);
		indexAdd.put(list, nodes);
	}

	protected void addToIndexRemove(LinkedList list, LNode node) {
		ArrayList<LNode> nodes = indexRemove.get(list);
		if (nodes == null) {
			nodes = new ArrayList<>();
		}
		nodes.add(node);
		indexRemove.put(list, nodes);
	}

	protected boolean abortAll = false;
	protected long innerReadVersion = -1L;
	protected boolean innerTX = false;
	protected HashMap<Queue, LocalQueue> innerQueueMap = new HashMap<>();
	protected HashMap<LNode, WriteElement> innerWriteSet = new HashMap<>();
	protected HashSet<LNode> innerReadSet = new HashSet<>();
	protected HashMap<LinkedList, ArrayList<LNode>> innerIndexAdd = new HashMap<>();
	protected HashMap<LinkedList, ArrayList<LNode>> innerIndexRemove = new HashMap<>();
	protected HashMap<RBTree, LocalRBTree> innerRBTreeMap = new HashMap<>();
	protected HashMap<Log, LocalLog> innerLogMap = new HashMap<>();
	protected HashSet<Log> innerLogLockedSet = new HashSet<>();
	protected HashMap<Log, Integer> innerLogVersionMap = new HashMap<>();

	protected void printInnerWriteSet() { // for debug
		System.out.println(Thread.currentThread().getName() + " printInnerWriteSet");
		for (Entry<LNode, WriteElement> e : innerWriteSet.entrySet()) {
			LNode n = e.getKey();
			WriteElement w = e.getValue();
			if (w.deleted) {
				System.out.println(Thread.currentThread().getName() + " " + n + " " + n.key + " delete");
				continue;
			}
			System.out.print(Thread.currentThread().getName() + " " + n + " " + n.key);
			if (w.next != null) {
				System.out.print(" next is " + w.next + " " + w.next.key);
			}
			System.out.println();
		}
	}

	protected void printInnerReadSet() { // for debug
		System.out.println(Thread.currentThread().getName() + " printInnerReadSet");
		for (LNode n : innerReadSet) {
			System.out.println(Thread.currentThread().getName() + " " + n + " " + n.key);
		}
	}

	protected void printInnerIndexAdd() { // for debug
		System.out.println(Thread.currentThread().getName() + " printInnerIndexAdd");
		for (Entry<LinkedList, ArrayList<LNode>> e : innerIndexAdd.entrySet()) {
			ArrayList<LNode> a = e.getValue();
			for (LNode l : a) {
				System.out.println(Thread.currentThread().getName() + " " + l + " " + l.key);
			}
		}
	}

	protected void printInnerIndexRemove() { // for debug
		System.out.println(Thread.currentThread().getName() + " printInnerIndexRemove");
		for (Entry<LinkedList, ArrayList<LNode>> e : innerIndexRemove.entrySet()) {
			ArrayList<LNode> a = e.getValue();
			for (LNode l : a) {
				System.out.println(Thread.currentThread().getName() + " " + l + " " + l.key);
			}
		}
	}

	protected void putIntoInnerWriteSet(LNode node, LNode next, Object val, boolean deleted) {
		WriteElement we = new WriteElement();
		we.next = next;
		we.deleted = deleted;
		we.val = val;
		innerWriteSet.put(node, we);
	}

	protected void addToInnerIndexAdd(LinkedList list, LNode node) {
		ArrayList<LNode> nodes = innerIndexAdd.get(list);
		if (nodes == null) {
			nodes = new ArrayList<>();
		}
		nodes.add(node);
		innerIndexAdd.put(list, nodes);
	}

	protected void addToInnerIndexRemove(LinkedList list, LNode node) {
		ArrayList<LNode> nodes = innerIndexRemove.get(list);
		if (nodes == null) {
			nodes = new ArrayList<>();
		}
		nodes.add(node);
		innerIndexRemove.put(list, nodes);
	}

}
