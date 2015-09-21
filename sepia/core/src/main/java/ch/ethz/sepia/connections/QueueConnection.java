package ch.ethz.sepia.connections;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class QueueConnection implements Connection {
    final PrivacyPeerAddress ppa;
    final BlockingQueue<byte[]> queue;

    public QueueConnection(final PrivacyPeerAddress ppa) {
        this.ppa = ppa;
        this.queue = new LinkedBlockingQueue<byte[]>();
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean isConnected() {
        // TODO Auto-generated method stub
        return true;
    }

    public void putMessage(final byte[] message) throws InterruptedException {
        queue.put(message);
    }

    @Override
    public byte[] read() throws IOException, InterruptedException {
        return queue.take();
    }

    @Override
    public void write(final byte[] bytes) throws IOException {
        // TODO Auto-generated method stub

    }

}
