package TDSL;

import java.util.HashSet;

public class LocalPCPool {
    HashSet<PCNode> lockedNodes;
    HashSet<PCNode> ProducedNodes;
    HashSet<PCNode> ConsumedNodes;

    public LocalPCPool()
    {
        lockedNodes = new HashSet<>();
        ProducedNodes = new HashSet<>();
        ConsumedNodes = new HashSet<>();
    }

}
