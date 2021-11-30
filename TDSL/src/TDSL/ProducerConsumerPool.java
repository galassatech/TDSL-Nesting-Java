package TDSL;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ProducerConsumerPool<T> {

    private class Pool
    {
        Integer size;
        ArrayList<PCNode> pool;
        AtomicInteger index;
        AtomicInteger wraparound_index;
        final static int ITERATION_LIMIT = 100000;

        Pool(int size)
        {
            this.size = size;
            pool = new ArrayList<PCNode>(size);
            index = new AtomicInteger(0);
            wraparound_index = new AtomicInteger(1);
            for(int i = 0 ; i < size; i++)
            {
                pool.add(new PCNode<T>());
            }

        }

        protected PCNode<T> getEmptyNode()
        {
            for(int i = 0 ; i >-1 /*< ITERATION_LIMIT */; i++ )
                for(PCNode node : pool)
                {
                    if(node.CompareAndSet(PCNode.State.BOTTOM, PCNode.State.LOCKED))
                        return node;
                }
            return null;
        }

        protected PCNode<T> getReadyNode()
        {
            for(int i = 0 ; i > -1 /* <ITERATION_LIMIT */ ;i++ )
                for(PCNode node : pool)
                {
                    if(node.CompareAndSet(PCNode.State.READY, PCNode.State.LOCKED))
                        return node;
                }
            return null;
        }



    }

    Integer size;
    final Integer max_size;
    Pool pool;
    // TODO: Add atomic index , handle wraparound

    public ProducerConsumerPool(int K)
    {
        max_size = K;
        pool = new Pool(K);
    }

    public boolean produce(T p)
    {
        LocalStorage localStorage = TX.lStorage.get();
        if (!localStorage.TX) {
            //Singleton
            PCNode<T> node = pool.getEmptyNode();
//            System.out.println("hi");
            if (node != null) {
                node.setVal(p);
                node.setState(PCNode.State.READY);
                return true;
            }
        }
        else {
            //TX
            /*PCNode key = getTXNodeToProduce(localStorage);

            if(localStorage.consumed.isEmpty() > 0 /*localStorage.producedAfterConsuming.isEmpty() )
            { // >0 means that there is some loceked node with content that we don't need anymore
                PCNode node = new PCNode();
                node.setVal(p);
                node.setState(PCNode.State.LOCKED);
                for(PCNode c: localStorage.consumed)
                {
                    if(!localStorage.producedAfterConsuming.containsKey(c))
                    {
                        localStorage.producedAfterConsuming.put(c,node);
                        break;
                    }
                }
                return true;
            }*/
            PCNode<T> node = pool.getEmptyNode();
            if (node != null) {
                node.setVal(p);
                localStorage.produced.add(node);
                return true;
            }
            else
            {
                localStorage.TX = false;
                TXLibExceptions excep = new TXLibExceptions();
                throw excep.new AbortException();
            }
        }
        return false;
    }


    public void consume(Consumer f) throws Exception {
        /*
         * IMPORTANT NOTE - Inside TX we first try to "replace" consumed nodes,
         * otherwise correctness of long transactions (as irrelevant as those are) in limited isEmpty pools is compromised
         * and system can lock down
         * */
        LocalStorage localStorage = TX.lStorage.get();
        if(!localStorage.TX)
        {//Singleton
            PCNode<T> node = pool.getReadyNode();
            //node is LOCKED
            if (node != null) {
                f.consume(node.getVal());
                node.setState(PCNode.State.BOTTOM);
            } else {
                throw new IllegalStateException();
            }
        }
        else
        {//TX
            PCNode<T> node = getReadyNodeForTX(localStorage);
            /*if(node.getVal() == null) {
                f.consume(node);
            }*/
            /*if(node == null)
            {
                localStorage.TX = false;
                TXLibExceptions excep = new TXLibExceptions();
                throw excep.new AbortException();
            }*/
            f.consume(node.getVal());
        }

    }

    private PCNode<T> getReadyNodeForTX(LocalStorage localStorage) {
        if(localStorage.produced == null)
        {
            return pool.getReadyNode();
        }
        else
        {
            if(localStorage.produced.size()>0) {
                PCNode<T> node = localStorage.produced.remove(0);
                node.setState(PCNode.State.BOTTOM);
                return node;
            }
            else
            {
                PCNode<T> node = pool.getReadyNode();
                if(node == null)
                {
                    localStorage.TX = false;
                    TXLibExceptions excep = new TXLibExceptions();
                    throw excep.new AbortException();
                }
                localStorage.consumed.add(node);
                return node;
            }
        }
    }

    //TODO: Add inner sets
//    private int getIndex()
//    {// provide index to work with
//
//    }

}
