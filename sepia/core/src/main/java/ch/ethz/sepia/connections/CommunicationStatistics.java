package ch.ethz.sepia.connections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommunicationStatistics {
    private static final Logger logger = LogManager
            .getLogger(CommunicationStatistics.class);

    private long numberOfFinishedRounds;
    private long thisRoundBytesReceived;
    private long thisRoundBytesSent;
    private long thisRoundMessagesReceived;
    private long thisRoundMessagesSent;
    private long thisRoundUncompressedBytesReceived;
    private long thisRoundUncompressedBytesSent;
    private long totalBytesReceived;
    private long totalBytesSent;
    private long totalMessagesReceived;
    private long totalMessagesSent;
    private long totalUncompressedBytesReceived;
    private long totalUncompressedBytesSent;

    public CommunicationStatistics() {
         numberOfFinishedRounds = 0;
         thisRoundBytesReceived = 0;
         thisRoundBytesSent = 0;
         thisRoundMessagesReceived = 0;
         thisRoundMessagesSent = 0;
         thisRoundUncompressedBytesReceived = 0;
         thisRoundUncompressedBytesSent = 0;
         totalBytesReceived = 0;
         totalBytesSent = 0;
         totalMessagesReceived = 0;
         totalMessagesSent = 0;
         totalUncompressedBytesReceived = 0;
         totalUncompressedBytesSent = 0;
    }

    public void incMessagesReceived(final int length) {
        thisRoundMessagesReceived++;
        totalMessagesReceived++;
        thisRoundBytesReceived += length;
        totalBytesReceived += length;
    }

    public void incMessagesSent(final int length) {
        thisRoundMessagesSent++;
        totalMessagesSent++;
        thisRoundBytesSent += length;
        totalBytesSent += length;
    }

    public void incUncompressedBytesReceived(final int totalOut) {
        thisRoundUncompressedBytesReceived += totalOut;
        totalUncompressedBytesReceived += totalOut;
    }

    public void incUncompressedBytesSent(final int totalIn) {
        thisRoundUncompressedBytesSent += totalIn;
        totalUncompressedBytesSent += totalIn;
    }

    /**
     * Logs the connection statistics (messages, bytes).
     */
    public void logStatistics() {
        logger.info("ConnectionManager statistics:");
        logger.info("--- Total      : MR=" + totalMessagesReceived + ", MS="
                + totalMessagesSent + ", BR=" + totalBytesReceived + ", UBR="
                + totalUncompressedBytesReceived + ", BS=" + totalBytesSent
                + ", UBS=" + totalUncompressedBytesSent);
        logger.info("--- This round : MR=" + thisRoundMessagesReceived
                + ", MS=" + thisRoundMessagesSent + ", BR="
                + thisRoundBytesReceived + ", UBR="
                + thisRoundUncompressedBytesReceived + ", BS="
                + thisRoundBytesSent + ", UBS="
                + thisRoundUncompressedBytesSent);
        logger.info("--- Avg. round : MR=" + totalMessagesReceived
                / (numberOfFinishedRounds + 1) + ", MS=" + totalMessagesSent
                / (numberOfFinishedRounds + 1) + ", BR=" + totalBytesReceived
                / (numberOfFinishedRounds + 1) + ", UBR="
                + totalUncompressedBytesReceived / (numberOfFinishedRounds + 1)
                + ", BS=" + totalBytesSent / (numberOfFinishedRounds + 1)
                + ", UBS=" + totalUncompressedBytesSent
                / (numberOfFinishedRounds + 1));
    }

    /**
     * Resets the current round statistics.
     */
    public void newStatisticsRound() {
        thisRoundMessagesReceived = 0;
        thisRoundMessagesSent = 0;
        thisRoundBytesReceived = 0;
        thisRoundUncompressedBytesReceived = 0;
        thisRoundBytesSent = 0;
        thisRoundUncompressedBytesSent = 0;
        numberOfFinishedRounds++;
    }

}
