package ch.ethz.sepia.connections;

import java.io.IOError;

public interface OutputStrategy {
    public void send(final Object message) throws IOError, InterruptedException;
}
