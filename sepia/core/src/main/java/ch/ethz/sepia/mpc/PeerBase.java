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

package ch.ethz.sepia.mpc;

import java.io.LineNumberReader;
import java.util.Observable;
import java.util.Random;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.ExceptionEvent;
import ch.ethz.sepia.events.GoodbyeEvent;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;

/**
 * A base class for input and privacy peers.
 *
 * @author Lisa Barisic, ETH Zurich
 */
public abstract class PeerBase extends Observable {
    private static final int DEFAULT_SLEEPTIME = 1500;

    /** To say you're about to send a list holding disqualifications */
    public static final String DISQUALIFICATION_DELIVERY = "DISQUALIFICATION_DELIVERY";

    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(PeerBase.class));

    /** Minumum number of peers needed if result shall be reconstructed */
    public static final int MIN_PEERS_FOR_RECONSTRUCTION = 3;

    /** To say you're about to send your peer ID (hopefully unique :) */
    public static final String PEER_ID = "PEER_ID";

    /** Pseudo random generators */
    public static final String[] PRG_LIST = { "SHA1PRNG" };

    /** To say you're about to send the final result */
    public static final String RESULT_DELIVERY = "RESULT_DELIVERY";

    /** Say you're about to send a share */
    public static final String SHARE_DELIVERY = "SHARE_DELIVERY";

    /** To say you're about to send the skipInputVerification flag */
    public static final String SKIP_INPUT_VERIFICATION_FLAG = "SKIP_INPUT_VERIFICATION_FLAG";

    protected ConnectionManager connectionManager = null;
    protected int currentTimeSlot = 0;
    protected int finalResultsToDo = 0;
    protected int goodbyesReceivedToDo = 0;

    protected LineNumberReader lineNumberReader = null;
    protected int minInputPeers = 0;

    protected int minPrivacyPeers = 0;
    protected String myPeerID; // Unique ID
    protected int myPeerIndex = 0;
    protected String myPeerName = null;
    protected Stopper protocolStopper = null;
    // protected SecureRandom random = null;
    protected Random random = null;
    protected String randomAlgorithm = null;
    protected Stopper stopper = null;

    protected int verificationsToDo = 0;

    /**
     * Creates a new instance of a general MPC peer.
     *
     * @param peerName
     *            the name of the peer
     * @param myPeerIndex
     *            The peer's number/index
     * @param stopper
     *            Stopper with which this thread can be stopped
     */
    public PeerBase(final String peerName, final int myPeerIndex,
            final ConnectionManager cm, final Stopper stopper) {

        logger.info("PeerBase with peerName := " + peerName + " instantiated!");

        this.myPeerIndex = myPeerIndex;
        this.connectionManager = cm;
        this.stopper = stopper;
        this.myPeerName = peerName;
        logger.info("myPeerName(PeerBase): " + this.myPeerName);
    }

    protected synchronized boolean checkStopped() {
        if (this.stopper.isStopped()) {
            logger.warn("I was stopped... stop processing");
            stopProcessing();
            try {
                cleanUp();
            } catch (final Exception e) {
                // Ignore, we've been stopped anyway...
                logger.error("Exception occured in Thread.sleep(): "
                        + Utils.getStackTrace(e));
            }
            return true;
        }
        return false;
    }

    protected abstract void cleanUp() throws Exception;

    /**
     * Returns the connection manager.
     *
     * @return the connection manager
     */
    public ConnectionManager getConnectionManager() {
        return this.connectionManager;
    }

    public synchronized int getCurrentTimeSlot() {
        return this.currentTimeSlot;
    }

    public synchronized String getMyPeerID() {
        return this.myPeerID;
    }

    public synchronized int getMyPeerIndex() {
        return this.myPeerIndex;
    }

    public String getPeerName() {
        return this.myPeerName;
    }

    /**
     * Initialize the MPC primitive
     */
    public abstract void initialize() throws Exception;

    /**
     * Initializes a new round.
     */
    protected abstract void initializeNewRound();

    protected abstract void initProperties() throws Exception;

    /**
     * Processes the event when a MpcMessage was received from an observable.
     *
     * @param messageBase
     *            The message received
     */
    protected synchronized void processMpcMessage(final MessageBase messageBase)
            throws Exception {
        if (messageBase.isHelloMessage()) {
            logger.info("Received Hello message");

        } else if (messageBase.isGoodbyeMessage()) {
            if (messageBase.wasGoodbyeReceived()) {
                logger.info("Received goodbye message");
                this.goodbyesReceivedToDo--;
                logger.info("Goodbyes to do: " + this.goodbyesReceivedToDo);
                if (this.goodbyesReceivedToDo <= 0) {
                    logger.info("Sending goodbye to observers");
                    sendNotification(new GoodbyeEvent(this));
                }
            }
        }
    }

    /**
     * Run the MPC protocol(s) over the given connection(s).
     */
    public abstract void runProtocol() throws Exception;

    /**
     * Notifes observers about an exceptional event and stops processing.
     *
     * @param exceptionEvent
     *            Exception event
     */
    protected synchronized void sendExceptionEvent(
            final ExceptionEvent exceptionEvent) {
        sendNotification(exceptionEvent);
        stopProcessing();
    }

    /**
     * Notifes observers about an exceptional event and stops processing.
     *
     * @param source
     *            Observable
     * @param exception
     *            Exception to be sent
     * @param message
     *            Additional message
     */
    protected synchronized void sendExceptionEvent(final Object source,
            final Exception exception, final String message) {
        sendExceptionEvent(new ExceptionEvent(source, exception, message));
    }

    /**
     * Notifes observers about an exceptional event and stops processing.
     *
     * @param source
     *            Observable
     * @param message
     *            Exception message
     */
    protected synchronized void sendExceptionEvent(final Object source,
            final String message) {
        sendExceptionEvent(source, new Exception(message), "");
    }

    /**
     * Sets changed and then notify your observers
     *
     * @param event
     *            The event to send to the observers
     */
    protected synchronized void sendNotification(final Object event) {
        logger.info("Notifying observers");
        setChanged();
        notifyObservers(event);
    }

    public synchronized void setMyPeerID(final String myPeerID) {
        this.myPeerID = myPeerID;
    }

    protected synchronized void stopProcessing() {
        logger.info("Stopping mpc entropy protocols");
        this.protocolStopper.stop();
    }

    /**
     * Wait until next time slot bulk is ready (if any)
     *
     * @return true if next time slot bulk is ready for processing, false if i
     *         should stop processing...
     */
    public final boolean waitForNextTimeSlotData(
            final int expectedTimeSlotRoundNumber) {
        while (this.currentTimeSlot != expectedTimeSlotRoundNumber) {
            logger.info("thread \"" + Thread.currentThread().getName()
                    + "\": Wait until time slot round "
                    + expectedTimeSlotRoundNumber + " is ready");

            try {
                Thread.sleep(DEFAULT_SLEEPTIME);
            } catch (final Exception e) {
                // wake up...
                logger.error("Exception occured in Thread.sleep(): "
                        + Utils.getStackTrace(e));
            }

            // Leave if someone stopped you
            if (this.protocolStopper.isStopped()) {
                logger.info("I was stopped");
                return false;
            }
        }
        return true;
    }
}
