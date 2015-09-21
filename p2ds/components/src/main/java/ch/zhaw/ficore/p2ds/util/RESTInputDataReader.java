package ch.zhaw.ficore.p2ds.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.InputDataReader;

public class RESTInputDataReader implements InputDataReader {
    private static final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(RESTInputDataReader.class));

    private final BlockingQueue<String> dataQueue = new LinkedBlockingQueue<String>();

    @Override
    public String read() {
        LOGGER.info("waiting for data");
        String data = null;
        try {
            data = this.dataQueue.take();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            LOGGER.catching(e);
        }
        LOGGER.info(data);
        return data;
    }

    public void write(final String csvData) throws InterruptedException {
        this.dataQueue.put(csvData);
    }
}
