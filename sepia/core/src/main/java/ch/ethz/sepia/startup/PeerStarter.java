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

package ch.ethz.sepia.startup;

import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.ExceptionEvent;
import ch.ethz.sepia.events.FinalResultEvent;
import ch.ethz.sepia.events.GoodbyeEvent;
import ch.ethz.sepia.mpc.PeerBase;
import ch.ethz.sepia.mpc.PeerFactory;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;

/**
 * Starts input and privacy peers.
 */
public class PeerStarter extends Observable implements Observer, Runnable {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(PeerStarter.class));

    protected ConnectionManager connectionManager = null;
    protected String errorMessage = null;
    protected boolean isInputPeer;
    protected int minInputPeers = 0;
    protected int minPrivacyPeers = 0;
    protected Stopper mpcStopper = null;
    protected String myID = null;
    protected int numberOfItems = 0;
    protected int numberOfTimeSlots = 0;
    protected PeerBase peer = null;
    private final String peerName;
    protected Stopper stopper = null;
    private boolean streaming = false;
    protected int timeout = 0;
    protected int timeSlotsToDo = 0;
    private long timestampStartRound;

    /**
     * Superclass of all peers.
     * 
     * @param stopper
     *            Can be used to stop a running thread.
     * @param isInputPeer
     *            true if it is an input peer, false for privacy peers
     */
    public PeerStarter(final String peerName, final Stopper stopper,
            final boolean isInputPeer, final Stopper mpcStopper) {
        this.peerName = peerName;
        this.stopper = stopper;
        this.mpcStopper = mpcStopper;
        this.timestampStartRound = System.currentTimeMillis();
        this.isInputPeer = isInputPeer;

        initProperties();
    }

    protected boolean checkStopped() {
        if (this.stopper.isStopped()) {
            logger.info("Processing stopped by stopper!");
            stopProcessing();
            return true;
        }
        return false;
    }

    protected synchronized void createMPCinstance() throws Exception {
        // not interested in the peer number
        this.peer = PeerFactory.getPeerInstance(this.peerName,
                this.isInputPeer, 0, this.connectionManager, this.mpcStopper);
        this.peer.initialize();
    }

    /**
     * Reads the properties and sets internal values accordingly.
     */
    public void initProperties() {
        final Properties properties = Configuration.getInstance(this.peerName)
                .getProperties();
        this.timeout = Integer.valueOf(properties.getProperty(
                Configuration.PROP_TIMEOUT, Configuration.DEFAULT_TIMEOUT));
        this.minInputPeers = Integer.valueOf(properties
                .getProperty(Configuration.PROP_MIN_INPUTPEERS));
        this.minPrivacyPeers = Integer.valueOf(properties
                .getProperty(Configuration.PROP_MIN_PRIVACYPEERS));
        this.myID = properties.getProperty(Configuration.PROP_MY_PEER_ID);
        this.numberOfTimeSlots = Integer.valueOf(properties
                .getProperty(Configuration.PROP_NUMBER_OF_TIME_SLOTS));
        this.timeSlotsToDo = this.numberOfTimeSlots;
        this.numberOfItems = Integer.valueOf(properties
                .getProperty(Configuration.PROP_NUMBER_OF_ITEMS));

        // Output the properties that were set
        logger.info("The following properties were set:");
        logger.info("minInputPeers: " + this.minInputPeers);
        logger.info("minPrivacyPeers: " + this.minPrivacyPeers);
        logger.info("timeout: " + this.timeout);
        logger.info("my peer ID: " + this.myID);

        if (this.timeSlotsToDo < 0) {
            this.streaming = true;
        }

    }

    /**
     * Sets changed and then notify your observers
     * 
     * @param event
     *            The event to send to the observers
     */
    protected synchronized void notify(final String comment, final Object event) {
        logger.info(comment + ": Notifying observers");
        setChanged();
        notifyObservers(event);
    }

    private void processExceptionEvent(final ExceptionEvent exceptionEvent,
            final Observable observable) throws Exception {
        logger.error("Received Exception Event: " + exceptionEvent.getMessage());
        logger.error("Exception occurred at: "
                + Utils.getStackTrace(exceptionEvent.getException()));

        notify("Exception event received from "
                + observable.getClass().getName(), exceptionEvent);

        Configuration.getInstance(this.myID).getStopListener()
                .stop(null, "Error event: " + observable.getClass().getName());

        stopProcessing();
    }

    private void processMpcEvent(final Object object,
            final Observable observable) throws Exception {
        FinalResultEvent finalResultEvent;

        if (object instanceof FinalResultEvent) {
            logger.info("Received FinalResultEvent ");
            finalResultEvent = (FinalResultEvent) object;
            /*
             * The round may be successful if enough peer's verification
             * succeeded, but if a peer is disqualified he will get zeros as
             * result for this round
             */
            logger.info("Round was successful: "
                    + finalResultEvent.isWholeRoundSuccessful());
            logger.info("Verification of my inputs was successful: "
                    + finalResultEvent.isVerificationSuccessful());

            this.timeSlotsToDo--;

            // Output various connection and running time statistics
            // ConnectionManager.logStatistics();
            // ConnectionManager.newStatisticsRound();
            if (!this.isInputPeer) {
                PrimitivesEnabledProtocol.logStatistics();
                // PrimitivesEnabledProtocol.newStatisticsRound();
            }

            // Output timing statistics
            final long currentTs = System.currentTimeMillis();
            logger.trace("--> Running time of round (including connection discovery!): "
                    + (currentTs - this.timestampStartRound)
                    / 1000.0
                    + " seconds.");
            this.timestampStartRound = currentTs;

            logger.trace("Time slots to do: " + this.timeSlotsToDo);

            if (!this.streaming) {
                if (this.timeSlotsToDo <= 0) {
                    logger.info("Secret Sharing done for all time slots");

                    // We're done!
                    stopProcessing();
                }
            }

            // Notify our own observer and tell them the result
            notify("Analyzer result", finalResultEvent);
        } else if (object instanceof GoodbyeEvent) {
            logger.warn("Received goodbye event");
            stopProcessing();
            notify("Bye bye", object);
        } else {
            logger.warn("Unexpected Event type: " + object.getClass());
        }
    }

    /**
     * Creates the peer instance and the connection manager. Then, connections
     * between peers are established, and the protocol is started.
     */
    @Override
    public synchronized void run() {
        try {
            this.connectionManager = Configuration.getInstance(this.myID)
                    .getConnectionManager();

            // Start MPC primitive and add this to the observer list
            logger.info("Starting MPC");
            createMPCinstance();
            startMPC();

        } catch (final Exception e) {
            final String message = "Unexpected error in run(): "
                    + Utils.getStackTrace(e)
                    + " -> Notify observers and then clean up";
            logger.error(message);

            // Notify my observers...
            sendExceptionEvent(e, message);
            stopProcessing();
        }
    }

    protected void sendExceptionEvent(final Exception exception,
            final String message) {
        ExceptionEvent event;

        event = new ExceptionEvent(this, exception, message);
        notify(message, event);
    }

    /**
     * Start a MPC primitive and let it exchange data with the privacy peers.
     * This is added to the mpc's observer list to receive the final results.
     */
    protected synchronized void startMPC() throws Exception {
        this.peer.addObserver(this);
        this.peer.runProtocol();
    }

    /**
     * Closes all connections.
     */
    protected void stopConnectionManagers() {
        /* nop */
    }

    protected void stopProcessing() {
        logger.info("Shutting down SEPIA");
        this.mpcStopper.stop();
        this.stopper.stop();
        stopConnectionManagers();
        this.peer = null;

        // That's all folks!
    }

    @Override
    public synchronized void update(final Observable observable,
            final Object object) {
        ExceptionEvent exceptionEvent;

        logger.info("Received notification from observable...("
                + observable.getClass().getName() + ")");

        if (checkStopped()) {
            return;
        }

        try {
            if (object instanceof ExceptionEvent) {
                processExceptionEvent((ExceptionEvent) object, observable);

            } else if (observable instanceof PeerBase) {
                processMpcEvent(object, observable);

            } else {
                logger.warn("Unexpected notifier: " + observable.getClass());
            }
        } catch (final Exception e) {
            logger.error("Error when processing event: "
                    + Utils.getStackTrace(e) + "("
                    + observable.getClass().getName() + ")");
            exceptionEvent = new ExceptionEvent(this, new Exception(
                    "Error when processing event: " + e.getMessage()));
            notify(exceptionEvent.getMessage(), exceptionEvent);
            Configuration.getInstance(this.myID).getStopListener()
                    .stop(e, "Error event: " + observable.getClass().getName());
            stopProcessing();
        }
    }
}
