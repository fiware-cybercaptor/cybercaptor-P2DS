// Copyright 2010-2012 Martin Burkhart (martibur@ethz.ch)
//
// This file is part of SEPIA. SEPIA is free software: you can redistribute
// it and/or modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation, either version 3
// of the License, or (at your option) any later version.
//
// SEPIA is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.

package ch.ethz.sepia.mpc.additive;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Vector;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.FinalResultEvent;
import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.VectorData;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import ch.ethz.sepia.services.DirectoryPoller;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.startup.Configuration;

/**
 * A MPC peer providing the private input data for the ADDITIVE protocol
 * 
 * @author Dilip Many
 * 
 */
public class AdditivePeer extends AdditiveBase {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(AdditivePeer.class));

    protected VectorData data;
    /**
     * array containing my initial shares; dimensions:
     * [numberOfPrivacyPeers][numberOfItems]
     */
    protected long[][] initialShares = null;

    /** indicates if the initial shares were generated yet */
    protected boolean initialSharesGenerated = false;
    /** MpcShamirSharing instance to use basic operations on Shamir shares */
    protected ShamirSharing mpcShamirSharing = null;

    /** vector of protocols (between this peer and the privacy peers) */
    protected Vector<AdditiveProtocolPeer> peerProtocolThreads = null;
    protected DirectoryPoller poller;

    /**
     * constructs a new ADDITIVE peer object
     * 
     * @param myPeerIndex
     *            This peer's number/index
     * @param stopper
     *            Stopper (can be used to stop this thread)
     * @param cm
     *            the connection manager
     * @throws Exception
     */
    public AdditivePeer(final String peerName, final int myPeerIndex,
            final ConnectionManager cm, final Stopper stopper) throws Exception {
        super(peerName, myPeerIndex, cm, stopper);
        this.peerProtocolThreads = new Vector<AdditiveProtocolPeer>();
        this.mpcShamirSharing = new ShamirSharing();
        this.stopper = stopper;
    }

    /**
     * Create and start the threads. Attach one privacy peer id to each of them.
     * 
     * @param privacyPeerIDs
     *            the ids of the privacy peers
     */
    protected void createProtocolThreadsForPrivacyPeers(
            final List<String> privacyPeerIDs) {
        this.peerProtocolThreads.clear();
        int currentID = 0;
        for (String ppId : privacyPeerIDs) {
            logger.info("Create a thread for privacy peer " + ppId);
            AdditiveProtocolPeer AdditiveProtocolPeer = new AdditiveProtocolPeer(
                    currentID, this, ppId, currentID, this.stopper);
            AdditiveProtocolPeer.addObserver(this);
            Thread thread = new Thread(AdditiveProtocolPeer,
                    "ADDITIVE Peer protocol with user number " + currentID);
            this.peerProtocolThreads.add(AdditiveProtocolPeer);
            thread.start();
            currentID++;
        }
    }

    /**
     * Generates shares for each secret input.
     */
    public synchronized void generateInitialShares() {
        if (!this.initialSharesGenerated) {
            this.initialSharesGenerated = true;
            logger.info("Generating initial shares");
            this.initialShares = this.mpcShamirSharing.generateShares(this.data
                    .getInput());
            logger.info("DONE generating initial shares");
        }
    }

    /**
     * Returns the initial shares for the privacy peer.
     * 
     * @param privacyPeerIndex
     *            index of privacy peer for which to return the initial shares
     */
    protected long[] getInitialSharesForPrivacyPeer(final int privacyPeerIndex) {
        return this.initialShares[privacyPeerIndex];
    }

    /**
     * Initializes the peer
     */
    @Override
    public void initialize() throws Exception {
        initProperties();

        this.mpcShamirSharing.setRandomAlgorithm(this.randomAlgorithm);
        this.mpcShamirSharing.setFieldSize(this.shamirSharesFieldOrder);
        if (this.degreeT > 0) {
            this.mpcShamirSharing.setDegreeT(this.degreeT);
        }

        this.currentTimeSlot = 1;

        // Create output folder if it does not exist
        File folder = new File(this.outputFolder);
        if (!folder.exists()) {
            folder.mkdir();
        }

        // Init the input directory poller
        this.poller = new DirectoryPoller(this.stopper, new File(
                this.inputFolder));
        this.poller.setTimeout(this.inputTimeout);

    }

    /**
     * Initializes and starts a new round of computation. It first
     * (re-)established connections and then creates and runs the protocol
     * threads for the new round.
     */
    @Override
    protected void initializeNewRound() {
        PrimitivesEnabledProtocol.newStatisticsRound();

        List<String> privacyPeerIDs = this.connectionManager
                .getActivePeers(true);
        Collections.sort(privacyPeerIDs);
        this.numberOfPrivacyPeers = privacyPeerIDs.size();
        this.mpcShamirSharing
                .setNumberOfPrivacyPeers(this.numberOfPrivacyPeers);
        this.mpcShamirSharing.init();
        clearPP2PPBarrier();

        // Init state variables
        this.initialSharesGenerated = false;
        this.initialShares = null;
        this.finalResults = null;
        this.finalResultsToDo = this.numberOfPrivacyPeers;

        readDataFromConfiguration(); // TODO: munt
        createProtocolThreadsForPrivacyPeers(privacyPeerIDs);
    }

    /**
     * Process message received by an observable.
     * 
     * @param observable
     *            Observable who sent the notification
     * @param object
     *            The object that was sent by the observable
     */
    @Override
    protected void notificationReceived(final Observable observable,
            final Object object) throws Exception {
        if (object instanceof AdditiveMessage) {
            // We are awaiting a final results message
            AdditiveMessage additiveMessage = (AdditiveMessage) object;

            if (additiveMessage.isDummyMessage()) {
                // Simulate a final results message in order not to stop
                // protocol execution
                additiveMessage.setIsFinalResultMessage(true);
            }

            if (additiveMessage.isFinalResultMessage()) {
                logger.info("Received a final result message from a privacy peer");
                this.finalResultsToDo--;

                if (this.finalResults == null
                        && additiveMessage.getResults() != null) {
                    this.finalResults = additiveMessage.getResults();
                    this.data.setOutput(this.finalResults);
                }

                if (this.finalResultsToDo <= 0) {
                    // notify observers about final result
                    logger.info("Received all final results. Notifying observers");
                    VectorData dummy = new VectorData(); // dummy data to avoid
                                                         // null pointer
                                                         // exception in
                                                         // Peers::processMpcEvent
                    FinalResultEvent finalResultEvent = new FinalResultEvent(
                            this, this.myAlphaIndex, getMyPeerID(),
                            additiveMessage.getSenderID(), dummy);
                    finalResultEvent.setVerificationSuccessful(true);
                    sendNotification(finalResultEvent);

                    // check for disqualification
                    if (this.data.getOutput().length != this.numberOfItems) {
                        logger.warn("Computation FAILED: too many input peers were disqualified!"); // therefore
                                                                                                    // received
                                                                                                    // result
                                                                                                    // is
                                                                                                    // input
                                                                                                    // verification
                                                                                                    // result
                                                                                                    // (size
                                                                                                    // =
                                                                                                    // numberOfInputPeers
                                                                                                    // *
                                                                                                    // numberOfItems)
                    }

                    writeDataToConfiguration();

                    // check if there are more time slots to process
                    if ((this.currentTimeSlot < this.timeSlotCount || this.timeSlotCount < 0)
                            && !Configuration.getInstance(this.myPeerID)
                                    .getGlobalStopper().isStopped()) {
                        this.currentTimeSlot++;
                        initializeNewRound();
                    } else {
                        logger.info("No more data available... Stopping protocol threads");
                        this.protocolStopper.stop();
                        Configuration.getInstance(this.myPeerID)
                                .getStopListener().stop();
                    }
                }
            } else {
                String errorMessage = "Didn't receive final result; last message is "
                        + additiveMessage;
                logger.error(errorMessage);
                sendExceptionEvent(this, errorMessage);
            }
        } else {
            throw new Exception("Received unexpected message type (expected: "
                    + AdditiveMessage.class.getName() + ", received: "
                    + object.getClass().getName());
        }
    }

    protected void readDataFromConfiguration() {
        logger.info("readDataFromConfiguration");
        Configuration cfg = Configuration.getInstance(this.myPeerID);
        String line = cfg.getInputDataReader().read();
        logger.info(this.myPeerID + "  " + this.currentTimeSlot + " " + line
                + " <= " + cfg);
        String parts[] = line.split(";");
        this.data = new VectorData();
        long[] input = new long[parts.length];
        long[] output = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            input[i] = Long.parseLong(parts[i]);
        }
        this.data.setInput(input);
        this.data.setOutput(output);
    }

    /**
     * Run the MPC protocol(s) over the given connection(s).
     */
    @Override
    public void runProtocol() throws Exception {
        // All we need to do here is starting the first round
        initializeNewRound();
    }

    protected void writeDataToConfiguration() {
        logger.info("finalResults are here");
        long[] output = this.data.getOutput();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < output.length - 1; i++) {
            sb.append(output[i]);
            sb.append(";");
        }
        if (output.length > 0) {
            sb.append(output[output.length - 1]);
        }
        logger.info(sb.toString());
        Configuration.getInstance(this.myPeerID).getFinalResultsWriter()
                .write(sb.toString());
    }

}
