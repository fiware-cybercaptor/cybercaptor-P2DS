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

package ch.ethz.sepia.mpc.protocolPrimitives;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.connections.PrivacyPeerConnectionManager;
import ch.ethz.sepia.connections.PrivacyViolationException;
import ch.ethz.sepia.mpc.ProtocolBase;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.IOperation;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.LessThan;
import ch.ethz.sepia.services.Stopper;

/**
 * Abstract protocol class for concrete privacy peer to privacy peer protocol
 * classes that want to use the operations from
 * MpcShamirSharingProtocolPrimitives.
 * <p>
 * Before using the operations through this class the reference to the privacy
 * peer that created this protocol instance and the information about the
 * privacy peer at the other end (of the communication link) has to be set. use:
 * {@link #initializeProtocolPrimitives(PrimitivesEnabledPeer, PeerInfo)}
 * 
 * @author Dilip Many
 * 
 */
public abstract class PrimitivesEnabledProtocol extends ProtocolBase {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(PrimitivesEnabledProtocol.class));

    private static long numberOfFinishedRounds;
    /**
     * info object to hold information about peer at other end of the connection
     */
    // protected PeerInfo otherPeerInfo = null;
    /**
     * defines the string which precedes a Shamir Sharing Protocol Primitives
     * message
     */
    protected static String SHAMIR_SHARING_PROTOCOL_PRIMITIVES_MESSAGE = "SSPP_MSG";
    private static long thisRoundComputationTime, thisRoundCommunicationTime;

    /*
     * Variables used for running time statistics. During doOperations(), one
     * thread stops the elapsed time during computation and communication. Since
     * all threads run in parallel and have roughly equal loads, this can be
     * used as an approximation of the overall times for computation and
     * communication.
     */
    private static long totalComputationTime, totalCommunicationTime;

    /**
     * Logs running time statistics.
     */
    public static void logStatistics() {
        logger.info("PrimitivesEnabledProtocol statistics [seconds]:");
        logger.info("--- Total      : Computation: " + totalComputationTime
                / 1000.0 + ", Communication: " + totalCommunicationTime
                / 1000.0);
        logger.info("--- This round : Computation: " + thisRoundComputationTime
                / 1000.0 + ", Communication: " + thisRoundCommunicationTime
                / 1000.0);
        logger.info("--- Avg. round : Computation: " + totalComputationTime
                / 1000.0 / (numberOfFinishedRounds + 1) + ",  Communication: "
                + totalCommunicationTime / 1000.0
                / (numberOfFinishedRounds + 1) + " (" + 100
                * (double) totalCommunicationTime
                / (totalCommunicationTime + totalComputationTime) + "%)");
    }

    /**
     * Resets the current round statistics.
     */
    public static void newStatisticsRound() {
        thisRoundComputationTime = 0;
        thisRoundCommunicationTime = 0;
        numberOfFinishedRounds++;
    }

    /** hold the last received message */
    protected PrimitivesMessage messageReceived;
    /** holds the message to be sent over the connection */
    protected PrimitivesMessage messageToSend;

    /**
     * the primitives used by the privacy peer that created this protocol
     * instance
     */
    private Primitives primitives = null;

    /** the privacy peer that created this protocol instance */
    private PrimitivesEnabledPeer primitivesEnabledPeer = null;

    /**
     * Calls
     * {@link ProtocolBase#ProtocolBase(int, ConnectionManager, String, String, int, int, Stopper)}
     * .
     */
    public PrimitivesEnabledProtocol(final int threadNumber,
            final ConnectionManager cm, final String myPeerID,
            final String otherPeerID, final int myPeerIndex,
            final int otherPeerIndex, final Stopper stopper) {
        super(threadNumber, cm, myPeerID, otherPeerID, myPeerIndex,
                otherPeerIndex, stopper);
    }

    /**
     * Executes the scheduled operations, including the synchronization of
     * intermediate share values with the other privacy peers.
     * 
     * @return true, if operation execution was successful
     * @throws PrimitivesException
     * @throws BrokenBarrierException
     * @throws InterruptedException
     * @throws PrivacyViolationException
     */
    public boolean doOperations() throws PrimitivesException,
            InterruptedException, BrokenBarrierException,
            PrivacyViolationException {
        // check if necessary initializations were done correctly
        if (this.primitivesEnabledPeer == null || this.otherPeerID == null) {
            final String errorMessage = "protocol instance not initialized with initializeProtocolPrimitives!";
            logger.error(errorMessage);
            throw new PrimitivesException(errorMessage);
        }
        if (this.otherPeerIndex < 0) {
            final String errorMessage = "protocol instance not initialized correctly: otherPeerInfo.getPeerIndex() = "
                    + this.otherPeerIndex;
            logger.error(errorMessage);
            throw new PrimitivesException(errorMessage);
        }
        if (this.primitives == null) {
            this.primitives = this.primitivesEnabledPeer.getPrimitives();
            if (this.primitives == null) {
                final String errorMessage = "privacy peers protocol primitives instance is NULL!";
                logger.error(errorMessage);
                throw new PrimitivesException(errorMessage);
            }
        }

        generateRandomNumbersIfNeeded();

        final int currentOperationSetNumber = this.primitivesEnabledPeer
                .getCurrentOperationSetNumber();
        // get the ids of the operations that shall be done
        final int[] ids = this.primitivesEnabledPeer.getOperationIDs();
        logger.info("thread " + Thread.currentThread().getId() + " is doing "
                + ids.length + " operations (operationSet="
                + currentOperationSetNumber + ")");

        long start = 0, stop = 0;
        final boolean amITakingTime = this.primitivesEnabledPeer
                .getBarrierPP2PPProtocolThreads().await() == 0; // choose one
                                                                // thread to
                                                                // keep the time

        if (amITakingTime) {
            start = System.currentTimeMillis();
        }
        // process initial data
        this.primitives.processReceivedData();
        if (amITakingTime) {
            stop = System.currentTimeMillis();
            final long duration = stop - start;
            thisRoundComputationTime += duration;
            totalComputationTime += duration;
        }
        // send, receive and process data till operations are done
        int roundCounter = 1;
        while (!this.primitives.areOperationsCompleted()) {
            logger.info("thread " + Thread.currentThread().getId()
                    + " is doing round " + roundCounter + " of operationSet="
                    + currentOperationSetNumber);
            if (amITakingTime) {
                start = System.currentTimeMillis();
            }
            // data ready; send it; receive data from other peers
            sendReceiveOperationData(this.otherPeerIndex);
            if (amITakingTime) {
                stop = System.currentTimeMillis();
                final long duration = stop - start;
                thisRoundCommunicationTime += duration;
                totalCommunicationTime += duration;
            }

            if (amITakingTime) {
                start = System.currentTimeMillis();
            }
            // process received data
            this.primitives.processReceivedData();
            if (amITakingTime) {
                stop = System.currentTimeMillis();
                final long duration = stop - start;
                thisRoundComputationTime += duration;
                totalComputationTime += duration;
            }
            roundCounter++;
        }
        // wait till all local threads completed the operation
        this.primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await();
        logger.info("thread " + Thread.currentThread().getId() + " completed "
                + ids.length + " operations (operationSet="
                + currentOperationSetNumber + ")");
        return true;
    }

    /**
     * Checks whether currently scheduled operations require bitwise shared
     * random numbers and generates them in one batch, if needed.
     * 
     * @throws PrimitivesException
     * @throws BrokenBarrierException
     * @throws InterruptedException
     * @throws PrivacyViolationException
     */
    private void generateRandomNumbersIfNeeded() throws PrimitivesException,
            InterruptedException, BrokenBarrierException,
            PrivacyViolationException {
        int randomNumbersNeeded = 0;
        final int bitsPerElement = this.primitives.getBitsCount();
        final List<IOperation> ops = this.primitives.getOperations();
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i) instanceof LessThan) {
                randomNumbersNeeded += ((LessThan) ops.get(i))
                        .getRandomNumbersNeeded(this.primitives);
            }
        }

        if (randomNumbersNeeded == 0) {
            return; // No random numbers needed. Proceed as always.
        }

        if (this.primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await() == 0) {
            logger.info("thread " + Thread.currentThread().getId()
                    + ": Automatically batch-generating " + randomNumbersNeeded
                    + " bitwise-shared random numbers!");

            // Backup old operations and schedule random number generation
            this.primitives.pushOperations();
            this.primitives.initialize(1);
            this.primitives.batchGenerateBitwiseRandomNumbers(0,
                    new long[] { randomNumbersNeeded });
        }
        this.primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await();

        // Perform random number generation
        doOperations();

        if (this.primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await() == 0) {
            final long[] preGeneratedRandomNumbers = this.primitives
                    .getResult(0);

            // Restore old operations and add the random numbers
            int bitIndex = 0;
            this.primitives.popOperations();
            final List<IOperation> ppOps = this.primitives.getOperations();
            for (int op = 0; op < ppOps.size(); op++) {
                if (ppOps.get(op) instanceof LessThan) {
                    // set random bits
                    final LessThan lt = (LessThan) ppOps.get(op);
                    final int bitsNeeded = lt
                            .getRandomNumbersNeeded(this.primitives)
                            * bitsPerElement;
                    final long[] bits = new long[bitsNeeded];
                    System.arraycopy(preGeneratedRandomNumbers, bitIndex, bits,
                            0, bitsNeeded);
                    lt.setRandomNumberBitShares(bits);
                    bitIndex += bitsNeeded;
                }
            }
        }
        this.primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await();

    }

    /**
     * initializes the protocol instance
     * 
     * @param primitivesEnabledPeer
     *            the privacy peer that created this protocol instance
     * @param peerInfo
     *            the information about the privacy peer at the other end of the
     *            communication link
     */
    public void initializeProtocolPrimitives(
            final PrimitivesEnabledPeer primitivesEnabledPeer) {
        this.primitivesEnabledPeer = primitivesEnabledPeer;
    }

    /**
     * Receives a Shamir Sharing Protocol Primitives message over the
     * connection. (the received message is stored in the messageReceived
     * variable)
     * 
     * @throws PrivacyViolationException
     */
    protected synchronized void receiveOperationData()
            throws PrivacyViolationException {
        logger.info("Waiting for Shamir Sharing Protocol Primitives message to arrive (from "
                + this.otherPeerID + ")");
        this.messageReceived = (PrimitivesMessage) this.connectionManager
                .receive(this.otherPeerID);

        /*
         * Here we need to deal with the possibility of a crashed remote privacy
         * peer. In case the remote privacy peer is offline, we get
         * <code>null</code> messages.
         */
        if (this.messageReceived == null) {
            // The other guy is down. Generate a dummy message in order not to
            // stop the protocol execution.
            this.messageReceived = new PrimitivesMessage(this.otherPeerID,
                    this.otherPeerIndex);
            this.messageReceived.setIsDummyMessage(true);
            logger.warn("Received empty message from " + this.otherPeerID
                    + ". Using a DUMMY message instead.");
        }

        if (this.messageReceived instanceof PrimitivesMessage) {
            logger.info("Received PrimitivesMessage message from "
                    + this.otherPeerID);

            if (this.messageReceived != null) {
                this.primitivesEnabledPeer
                        .processShamirSharingProtocolPrimitivesMessage(this.messageReceived);
            }
        } else {
            logger.warn("Received unexpected message type (expected: "
                    + SHAMIR_SHARING_PROTOCOL_PRIMITIVES_MESSAGE
                    + ", received: "
                    + this.messageReceived.getClass().toString());
        }
    }

    /**
     * Sends a Shamir Sharing Protocol Primitives message over the connection.
     * 
     * @throws PrivacyViolationException
     */
    protected synchronized void sendOperationData()
            throws PrivacyViolationException {
        logger.info("Sending Shamir Sharing Protocol Primitives message (to "
                + this.otherPeerID + ")");
        this.connectionManager.send(this.otherPeerID, this.messageToSend);
    }

    /**
     * sends the data of the running operations and receives a message
     * 
     * @param recipientPrivacyPeerIndex
     *            peer index of recipient
     * @throws PrimitivesException
     * @throws PrivacyViolationException
     */
    private void sendReceiveOperationData(final int recipientPrivacyPeerIndex)
            throws PrimitivesException, PrivacyViolationException {
        this.messageToSend = new PrimitivesMessage(this.myPeerID,
                this.myPeerIndex);
        this.messageToSend.setOperationsData(this.primitives
                .getDataToSend(recipientPrivacyPeerIndex));
        if (this.messageToSend.getOperationsData().length < 1) {
            final String errorMessage = "no operations data to send for privacy peer "
                    + recipientPrivacyPeerIndex + "!";
            throw new PrimitivesException(errorMessage);
        }

        logger.info("Thread " + Thread.currentThread().getId()
                + ": Send/receive Shamir Sharing Protocol Primitives message");
        if (PrivacyPeerConnectionManager.sendingFirst(this.myPeerID,
                this.otherPeerID)) {
            sendOperationData();
            receiveOperationData();
        } else {
            receiveOperationData();
            sendOperationData();
        }
    }

}
