package TDSL;

public class LocalQueue {

    protected boolean firstDeq = true; // is this the first time of a dequeue?
    protected QNode nodeToDeq = null; // the node to dequeue
    protected boolean isLockedByMe = false; // is queue (not local queue) locked by me
    protected int lockedByWhom = -1;
    protected QNode head = null; // TODO: revisit with Hagar (this was private before)
    protected QNode tail = null;
    private int size;

    protected enum DequeState{
        // This aids TXEnd of a nested TX @ commit phase
        NEVER, EXTERNAL, PARENT_TX, NESTED_TX
    }

    protected DequeState dequeState = DequeState.NEVER;


    protected void enqueue(Object val) {

        QNode node = new QNode();
        node.val = val;
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

    protected Object dequeue() throws TXLibExceptions.QueueIsEmptyException {

        if (head == null) {
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
        return ret;
    }

    protected boolean isEmpty() {

        return (size <= 0);
    }

    //This method merges localQueue of nested TX into localQueue of parent TX
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
//todo: null where??
        if (dequeState == DequeState.NEVER || dequeState == DequeState.EXTERNAL) return; // TODO: should be checked before call
        if (dequeState == DequeState.PARENT_TX){ // TODO: should be checked before call
            if (upToNode == null) {
                if (TX.DEBUG_MODE_QUEUE) {
                    System.out.println("Queue dequeueNodes - upToNode is null");
                }
                return;
            }

            if (TX.DEBUG_MODE_QUEUE) {
                System.out.println("Queue dequeueNodes - inner");
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
    }

}
