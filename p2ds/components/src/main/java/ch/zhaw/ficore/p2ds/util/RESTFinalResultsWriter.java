package ch.zhaw.ficore.p2ds.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.FinalResultsWriter;
import ch.zhaw.ficore.p2ds.group.json.DataSets;

public class RESTFinalResultsWriter implements FinalResultsWriter {

    private static final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(RESTFinalResultsWriter.class));

    private int maxSize = 1;
    private final String peerName;

    public Queue<String> queue = new LinkedList<String>();

    private final String url;

    public RESTFinalResultsWriter(final String url, final String peerName) {
        this.url = url;
        this.peerName = peerName;
    }

    public void setMaxSize(final int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public synchronized void write(final String arg0) {
        LOGGER.info(this.peerName + " ::~ " + arg0);
        this.queue.add(arg0);

        if (this.queue.size() >= this.maxSize) {
            DataSets ds = new DataSets();
            ds.setData(new ArrayList<String>());
            while (!this.queue.isEmpty()) {
                ds.getData().add(this.queue.poll());
            }
            try {
                RESTHelper.postRequestJSON(this.url,
                        RESTHelper.toJSON(DataSets.class, ds));
            } catch (Exception ex) {
                LOGGER.catching(ex);
                LOGGER.error("Could not send data!");
            }
        }
    }
}
