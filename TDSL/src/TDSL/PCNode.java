package TDSL;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PCNode<T> {
    public T getVal() {
        return val;
    }

    public void setVal(T val) {
        this.val = val;
    }

    enum State {
        BOTTOM, // Empty
        LOCKED, // In process of being produced or consumed
        READY, // Produced, not consumed
    }
    final static  HashMap<State, Integer> stateHashMap;

    static {
        stateHashMap = new HashMap<>();
        stateHashMap.put(State.BOTTOM,0);
        stateHashMap.put(State.LOCKED,1);
        stateHashMap.put(State.READY,2);
    }

    public void setState(State state) {
        this.state.set(stateHashMap.get(state));
    }

    AtomicInteger state;
    private T val;

    public PCNode()
    {
        state = new AtomicInteger(stateHashMap.get(State.BOTTOM));
        setVal(null);
    }

    public boolean CompareAndSet(State state1, State state2)
    {
        return state.compareAndSet(stateHashMap.get(state1),stateHashMap.get(state2));
    }

    public T get()
    {
        return getVal();
    }

}
