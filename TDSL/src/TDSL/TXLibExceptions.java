package TDSL;

public class TXLibExceptions {

    @SuppressWarnings("serial")
    public class QueueIsEmptyException extends Exception {

    }

    @SuppressWarnings("serial")
    public class AbortException extends RuntimeException {

    }

    @SuppressWarnings("serial")
    public class NestedAbortException extends AbortException {

    }
}
