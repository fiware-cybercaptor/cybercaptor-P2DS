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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Vector;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.FinalResultEvent;
import ch.ethz.sepia.mpc.CountingBarrier;
import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.VectorData;
import ch.ethz.sepia.mpc.protocolPrimitives.Primitives;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.startup.Configuration;

/**
 * A MPC privacy peer with the computation capabilities for the ADDITIVE
 * protocol
 * 
 * @author Dilip Many
 * 
 */
public class AdditivePrivacyPeer extends AdditiveBase {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(AdditivePrivacyPeer.class));

    /** number of initial shares that the privacy peer yet has to receive */
    private int initialSharesToReceive = 0;
    private boolean isRoundSuccessful = true;

    /** summed up shares */
    private long[] itemSumShares = null;
    /** number of input peers connected to this one */
    private int numberOfInputPeers = 0;
    /** vector of information objects for the connected peers */
    private Vector<AdditivePeerInfo> peerInfos = null;
    /** barrier to synchronize the peerProtocolThreads threads */
    private CountingBarrier peerProtocolBarrier = null;

    /** vector of protocols (between this privacy peer and the peers) */
    private Vector<AdditiveProtocolPrivacyPeerToPeer> peerProtocolThreads = null;
    /** barrier to synchronize the ppToPPProtocolThreads threads */
    private CountingBarrier ppProtocolBarrier = null;
    /** vector of protocols (between this privacy peer and other privacy peers) */
    private Vector<AdditiveProtocolPrivacyPeerToPP> ppToPPProtocolThreads = null;
    /** vector of information objects for the connected privacy peers */
    private Vector<AdditivePeerInfo> privacyPeerInfos = null;

    /**
     * creates a new MPC ADDITIVE privacy peer
     * 
     * @param myPeerIndex
     *            This peer's number/index
     * @param stopper
     *            Stopper (can be used to stop this thread)
     * @param cm
     *            the connection manager
     * @throws Exception
     */
    public AdditivePrivacyPeer(final String peerName, final int myPeerIndex,
            final ConnectionManager cm, final Stopper stopper) throws Exception {
        super(peerName, myPeerIndex, cm, stopper);

        this.peerInfos = new Vector<AdditivePeerInfo>();
        this.privacyPeerInfos = new Vector<AdditivePeerInfo>();
        this.peerProtocolThreads = new Vector<AdditiveProtocolPrivacyPeerToPeer>();
        this.ppToPPProtocolThreads = new Vector<AdditiveProtocolPrivacyPeerToPP>();
    }

    /**
     * Add up all the shares received (of non-disqualified users).
     */
    public void addShares() {
        this.itemSumShares = new long[this.numberOfItems];
        ShamirSharing ss = this.primitives.getMpcShamirSharing();
        for (AdditivePeerInfo peerInfo : this.peerInfos) {
            if (peerInfo.isVerificationSuccessful()) {
                logger.info("Adding share of: " + peerInfo.getID());
                this.itemSumShares = ss.vectorAdd(this.itemSumShares,
                        peerInfo.getInitialShares());
            } else {
                logger.warn("Peer is disqualified: " + peerInfo.getID()
                        + " -> Not adding share");
            }
        }
    }

    /**
     * Create and start the threads. Attach one input peer id to each of them.
     * 
     * @param inputPeerIDs
     *            the ids of the input peers
     */
    private void createProtocolThreadsForInputPeers(
            final List<String> inputPeerIDs) {
        this.peerProtocolThreads.clear();
        this.peerInfos.clear();
        int currentID = 0;
        for (String ipId : inputPeerIDs) {
            logger.info("Create a thread for input peer " + ipId);
            AdditiveProtocolPrivacyPeerToPeer pp2p = new AdditiveProtocolPrivacyPeerToPeer(
                    currentID, this, ipId, currentID, this.stopper);
            pp2p.addObserver(this);
            Thread thread = new Thread(pp2p,
                    "ADDITIVE Peer protocol connected with " + ipId);
            this.peerProtocolThreads.add(pp2p);
            this.peerInfos
                    .add(currentID, new AdditivePeerInfo(ipId, currentID));
            thread.start();
            currentID++;
        }
    }

    /**
     * Create and start the threads. Attach one privacy peer id to each of them.
     * 
     * @param privacyPeerIDs
     *            the ids of the privacy peers
     * @param ppIndexMap
     *            a map mapping privacy peer IDs to indices
     */
    private void createProtocolThreadsForPrivacyPeers(
            final List<String> privacyPeerIDs,
            final Map<String, Integer> ppIndexMap) {
        this.ppToPPProtocolThreads.clear();
        this.privacyPeerInfos.clear();
        int currentID = 0;
        for (String ppId : privacyPeerIDs) {
            logger.info("Create a thread for privacy peer " + ppId);
            int otherPPindex = ppIndexMap.get(ppId);
            AdditiveProtocolPrivacyPeerToPP pp2pp = new AdditiveProtocolPrivacyPeerToPP(
                    currentID, this, ppId, otherPPindex, this.stopper);
            pp2pp.setMyPeerIndex(this.myAlphaIndex);
            pp2pp.addObserver(this);
            Thread thread = new Thread(pp2pp,
                    "ADDITIVE PP-to-PP protocol connected with " + ppId);
            this.ppToPPProtocolThreads.add(pp2pp);
            this.privacyPeerInfos.add(currentID, new AdditivePeerInfo(ppId,
                    otherPPindex));
            thread.start();
            currentID++;
        }
    }

    /**
     * lets protocol thread report to privacy peer that it sent the final result
     * and starts new round if there are more time slots (data) to process
     */
    public synchronized void finalResultIsSent() {
        this.finalResultsToDo--;
        logger.info("thread " + Thread.currentThread().getId()
                + " called finalResultIsSent; finalResultsToDo="
                + this.finalResultsToDo);
        if (this.finalResultsToDo <= 0) {
            // report final result to observers
            logger.info("Sent all final results. Notifying observers");
            VectorData dummy = new VectorData(); // dummy data to avoid null
                                                 // pointer exception in
                                                 // Peers::processMpcEvent
            FinalResultEvent finalResultEvent;
            finalResultEvent = new FinalResultEvent(this, this.myAlphaIndex,
                    getMyPeerID(), getMyPeerID(), dummy);
            finalResultEvent.setVerificationSuccessful(true);
            sendNotification(finalResultEvent);
            // check if there are more time slots to process
            logger.info("TimeSlotCount (privacy-peer) is: "
                    + this.timeSlotCount);
            if (this.timeSlotCount < 0
                    || this.currentTimeSlot < this.timeSlotCount) {
                this.currentTimeSlot++;
                logger.info("thread " + Thread.currentThread().getId()
                        + " increased currentTimeSlot to "
                        + this.currentTimeSlot + ", will init new round now");
                initializeNewRound();
            } else {
                logger.info("No more data available... Stopping protocol threads");
                this.protocolStopper.stop();
                Configuration.getInstance(this.myPeerID).getStopListener()
                        .stop();
            }
        }
    }

    /**
     * @return the final result
     */
    public long[] getFinalResult() {
        return this.finalResults;
    }

    /**
     * Generates a consistent mapping from active privacy peer IDs to privacy
     * peer indices.
     * 
     * @param connectedPrivacyPeerIDs
     *            all connected PPs, without myself.
     * @return the index map
     */
    private Map<String, Integer> getIndexMap(
            final List<String> connectedPrivacyPeerIDs) {
        List<String> allPPsorted = new ArrayList<String>();
        allPPsorted.addAll(connectedPrivacyPeerIDs);
        allPPsorted.add(getMyPeerID());

        Collections.sort(allPPsorted);
        HashMap<String, Integer> indexMap = new HashMap<String, Integer>();
        for (int index = 0; index < allPPsorted.size(); index++) {
            indexMap.put(allPPsorted.get(index), index);
        }
        return indexMap;
    }

    public long[] getItemSumShares() {
        return this.itemSumShares;
    }

    /**
     * returns the number of peers connected to this one
     */
    public int getNumberOfInputPeers() {
        return this.numberOfInputPeers;
    }

    /**
     * @return the numberOfItems per time slot
     */
    public int getNumberOfItems() {
        return this.numberOfItems;
    }

    /**
     * Returns the peer info for the INPUT PEER with the given user number,
     * which corresponds to the index of this privacy peer's elements in the
     * list (null if user not in list)
     * 
     * @param peerNumber
     *            the peer's number in the list
     * 
     * @return The input peers info instance (null if not found)
     */
    private synchronized AdditivePeerInfo getPeerInfoByIndex(
            final int peerNumber) {
        return this.peerInfos.elementAt(peerNumber);
    }

    /**
     * Returns the peer info for the PEER with the given peer ID.
     * 
     * @param peerID
     *            The peer's ID
     * 
     * @return The peers info instance (null if not found)
     */
    private synchronized AdditivePeerInfo getPeerInfoByPeerID(
            final String peerID) {
        for (AdditivePeerInfo peerInfo : this.peerInfos) {
            if (peerInfo.getID() == null) {
                logger.warn("There is a peerInfo without a peerID! "
                        + peerInfo.getIndex());
            } else if (peerInfo.getID().equals(peerID)) {
                return peerInfo;
            }
        }
        return null;
    }

    public Vector<AdditiveProtocolPrivacyPeerToPP> getPpToPPProtocolThreads() {
        return this.ppToPPProtocolThreads;
    }

    /**
     * Returns the peer info for the PRIVACY PEER with the given user number,
     * which corresponds to the index of this privacy peer's elements in the
     * list (null if user not in list)
     * 
     * @param privacyPeerNumber
     *            The privacy peer's number in the list
     * 
     * @return The privacy peers info instance (null if not found)
     */
    public synchronized AdditivePeerInfo getPrivacyPeerInfoByIndex(
            final int privacyPeerNumber) {
        return this.privacyPeerInfos.elementAt(privacyPeerNumber);
    }

    public Vector<AdditivePeerInfo> getPrivacyPeerInfos() {
        return this.privacyPeerInfos;
    }

    /**
     * returns the number of time slots
     */
    public int getTimeSlotCount() {
        return this.timeSlotCount;
    }

    /**
     * Initializes the privacy peer
     */
    @Override
    public void initialize() throws Exception {
        initProperties();

        this.currentTimeSlot = 1;
    }

    /**
     * Initializes a new round of computation.
     */
    @Override
    public void initializeNewRound() {
        PrimitivesEnabledProtocol.newStatisticsRound();

        // Get all the active privacy peer IDs. Note that these are not
        // necessarily all PPs configured in the config file.
        List<String> privacyPeerIDs = this.connectionManager
                .getActivePeers(true);
        logger.info("Size of privacyPeerIDs := " + privacyPeerIDs.size());
        List<String> inputPeerIDs = this.connectionManager
                .getActivePeers(false);
        Map<String, Integer> ppIndexMap = getIndexMap(privacyPeerIDs);
        this.myAlphaIndex = ppIndexMap.get(this.myPeerID);

        this.numberOfPrivacyPeers = privacyPeerIDs.size() + 1; // Count myself
        this.numberOfInputPeers = inputPeerIDs.size();
        this.peerProtocolBarrier = new CountingBarrier(this.numberOfInputPeers);
        this.ppProtocolBarrier = new CountingBarrier(
                this.numberOfPrivacyPeers - 1);
        clearPP2PPBarrier();

        // init counters
        this.initialSharesToReceive = this.numberOfInputPeers;
        this.finalResultsToDo = this.numberOfInputPeers;
        this.finalResults = null;
        this.isRoundSuccessful = true;

        this.primitives = new Primitives(this.randomAlgorithm,
                this.shamirSharesFieldOrder, this.degreeT,
                this.numberOfPrivacyPeers, this.myAlphaIndex,
                this.numberOfPrivacyPeers - 1);
        createProtocolThreadsForInputPeers(inputPeerIDs);
        createProtocolThreadsForPrivacyPeers(privacyPeerIDs, ppIndexMap);
    }

    public boolean isRoundSuccessful() {
        return this.isRoundSuccessful;
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
    public void notificationReceived(final Observable observable,
            final Object object) throws Exception {
        if (object instanceof AdditiveMessage) {
            AdditiveMessage msg = (AdditiveMessage) object;
            // We are awaiting a message with initial shares
            if (msg.isDummyMessage()) {
                // Counterpart is offline. Simulate an initial shares message.
                msg.setIsInitialSharesMessage(true);
            }

            if (msg.isInitialSharesMessage()) {
                logger.info("Received shares from peer: " + msg.getSenderID());
                AdditivePeerInfo peerInfo = getPeerInfoByPeerID(msg
                        .getSenderID());
                peerInfo.setInitialShares(msg.getInitialShares());

                this.initialSharesToReceive--;
                if (this.initialSharesToReceive <= 0) {
                    logger.info("Received all initial shares from peers");
                    startNextPPProtocolStep();
                }

            } else {
                String errorMessage = "Didn't receive initial shares";
                errorMessage += "\nisGoodBye: " + msg.isGoodbyeMessage();
                errorMessage += "\nisHello: " + msg.isHelloMessage();
                errorMessage += "\nisInitialShares: "
                        + msg.isInitialSharesMessage();
                errorMessage += "\nisFinalResult: "
                        + msg.isFinalResultMessage();
                logger.error(errorMessage);
                sendExceptionEvent(this, errorMessage);
            }
        } else {
            throw new Exception("Received unexpected message type (expected: "
                    + AdditiveMessage.class.getName() + ", received: "
                    + object.getClass().getName());
        }
    }

    /**
     * gets the results of the less-than operations of the weights threshold
     * check, and reconstructs the result
     */
    public void processNormBoundCheckResult() {
        this.isRoundSuccessful = true;
        int numberOfDishonestPeers = 0;
        int operationIndex = 0;
        for (int peerIndex = 0; peerIndex < this.numberOfInputPeers; peerIndex++) {
            for (int itemIndex = 0; itemIndex < this.numberOfItems; itemIndex++) {
                operationIndex = peerIndex * this.numberOfItems + itemIndex;
                if (this.primitives.getResult(operationIndex)[0] != 1) {
                    getPeerInfoByIndex(peerIndex).setVerificationSuccessful(
                            false);
                    numberOfDishonestPeers++;
                    logger.warn("Input peer "
                            + getPeerInfoByIndex(peerIndex).getID()
                            + " was disqualified! It shared a value bigger than "
                            + this.maxElement + "!");
                    break;
                } else {
                    getPeerInfoByIndex(peerIndex).setVerificationSuccessful(
                            true);
                }
            }
        }

        int numberOfHonestPeers = this.numberOfInputPeers
                - numberOfDishonestPeers;
        if (numberOfHonestPeers < this.numberOfInputPeers) {
            logger.error("Not enough honest peers (" + numberOfHonestPeers
                    + " < " + this.numberOfInputPeers
                    + ")! Sending verification results.");
            this.isRoundSuccessful = false;
        }
        logger.info("Processed the norm bound check results");
    }

    /**
     * Run the MPC protocol(s) over the given connection(s).
     */
    @Override
    public synchronized void runProtocol() {
        // All we need to do here is starting the first round
        initializeNewRound();
    }

    /**
     * retrieves and stores the final result
     */
    public void setFinalResult() {
        logger.info("Thread " + Thread.currentThread().getId()
                + " called setFinalResult");
        this.finalResults = new long[this.operationIDs.length];
        for (int i = 0; i < this.operationIDs.length; i++) {
            this.finalResults[i] = this.primitives
                    .getResult(this.operationIDs[i])[0];
        }
        logger.info("Thread " + Thread.currentThread().getId()
                + " starts next pp-peer protocol step");
        startNextPeerProtocolStep();
    }

    public boolean skipInputVerification() {
        return this.skipInputVerification;
    }

    /**
     * starts the reconstruction of the final result
     */
    public void startFinalResultReconstruction() {
        int nrOfitems = this.itemSumShares.length;
        initializeNewOperationSet(nrOfitems);
        this.operationIDs = new int[nrOfitems];
        long[] data = null;
        for (int i = 0; i < nrOfitems; i++) {
            // create reconstruction operation for result of product operation
            this.operationIDs[i] = i;
            data = new long[1];
            data[0] = this.itemSumShares[i];
            if (!this.primitives.reconstruct(this.operationIDs[i], data)) {
                logger.error("reconstruct operation arguments are invalid: id="
                        + this.operationIDs[i] + ", data=" + data[0]);
            }
        }
        logger.info("Started the final result reconstruction; ("
                + this.operationIDs.length
                + " reconstruction operations are in progress)");
    }

    /**
     * starts the less-thans of the max. element check
     */
    public void startLessThans() {

        // create less-than operation set
        initializeNewOperationSet(this.numberOfInputPeers * this.numberOfItems);
        this.operationIDs = new int[this.numberOfInputPeers
                * this.numberOfItems];
        int operationIndex = 0;
        int dataSize = 5;
        long[] data = null;
        long isLessThanHalfOfFieldSize = this.maxElement <= this.shamirSharesFieldOrder / 2 ? 1
                : 0;
        for (int peerIndex = 0; peerIndex < this.numberOfInputPeers; peerIndex++) {
            for (int itemIndex = 0; itemIndex < this.numberOfItems; itemIndex++) {
                this.operationIDs[operationIndex] = operationIndex;
                data = new long[dataSize];
                data[0] = getPeerInfoByIndex(peerIndex).getInitialShares()[itemIndex];
                data[1] = this.maxElement;
                data[2] = -1;
                data[3] = isLessThanHalfOfFieldSize;
                data[4] = -1;
                if (!this.primitives.lessThan(operationIndex, data)) {
                    Services.printVector(
                            "less-than operation arguments are invalid: id="
                                    + peerIndex + "; data=", data, logger);
                }
                operationIndex++;
            }
        }
        logger.info("thread " + Thread.currentThread().getId()
                + " started the less-thans of the max. element check; ("
                + this.operationIDs.length
                + " less-than operations are in progress)");
    }

    /**
     * Starts the next PeerProtocol step.
     * 
     * @throws InterruptedException
     */
    public void startNextPeerProtocolStep() {
        logger.info("PeerProtocol Opening the barrier. PeerProtocol Threads can start the next step.");
        try {
            this.peerProtocolBarrier.openBarrier();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * Starts the next PPProtocol step.
     * 
     * @throws InterruptedException
     */
    public void startNextPPProtocolStep() throws InterruptedException {
        logger.info("PPProtocol Opening the barrier. PPProtocol Threads can start the next step.");
        this.ppProtocolBarrier.openBarrier();
    }

    /**
     * gets the results of the less-than operations of the norm bound check, and
     * reconstructs the result
     */
    public void startNormBoundCheckResultReconstruction() {
        long[] result = new long[this.operationIDs.length];
        for (int i = 0; i < this.operationIDs.length; i++) {
            // get less-than operation result
            result[i] = this.primitives.getResult(this.operationIDs[i])[0];
        }

        initializeNewOperationSet(result.length);
        this.operationIDs = new int[result.length];
        long[] data = null;
        for (int i = 0; i < result.length; i++) {
            // create reconstruction operation for result of less-than operation
            this.operationIDs[i] = i;
            data = new long[1];
            data[0] = result[i];
            if (!this.primitives.reconstruct(this.operationIDs[i], data)) {
                logger.error("reconstruct operation arguments are invalid: id="
                        + this.operationIDs[i] + ", data=" + data[0]);
            }
        }
        logger.info("thread " + Thread.currentThread().getId()
                + " started the norm bound check result reconstruction; ("
                + this.operationIDs.length
                + " reconstruction operations are in progress)");
    }

    /**
     * Wait until the privacy peer is ready for the next PeerProtocol step.
     * 
     * @throws InterruptedException
     */
    public void waitForNextPeerProtocolStep() {
        logger.info("PeerProtocol Barrier: Thread nr. "
                + (this.peerProtocolBarrier.getNumberOfWaitingThreads() + 1)
                + " arrived.");
        try {
            this.peerProtocolBarrier.block();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * Wait until the privacy peer is ready for the next PPProtocol step.
     * 
     * @throws InterruptedException
     */
    public void waitForNextPPProtocolStep() {
        logger.info("PPProtocol Barrier: Thread nr. "
                + (this.ppProtocolBarrier.getNumberOfWaitingThreads() + 1)
                + " arrived.");
        try {
            this.ppProtocolBarrier.block();
        } catch (InterruptedException e) {
            // ignore
        }
    }

}
