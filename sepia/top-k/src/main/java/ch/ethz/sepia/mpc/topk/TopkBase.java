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

package ch.ethz.sepia.mpc.topk;

import java.util.Collections;
import java.util.HashMap;
import java.util.Observable;
import java.util.Properties;
import java.util.Random;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.ExceptionEvent;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledPeer;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;
import ch.ethz.sepia.startup.Configuration;

/**
 * This abstract class contains the functionality common to peers and privacy
 * peers of the MPC Topk protocol.
 *
 * @author Dilip Many
 *
 */
public abstract class TopkBase extends PrimitivesEnabledPeer {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(TopkBase.class));

    protected String inputFolder;
    protected String outputFolder;
    protected int inputTimeout;

    /** number of time slots */
    protected int timeSlotCount = 1;
    /** number of items per time slot */
    protected int numberOfItems = 0;
    /** the alpha index of this (privacy) peer */
    protected int myAlphaIndex = 0;
    /** the size of the field to use for the Shamir shares computations */
    protected long shamirSharesFieldOrder = 0;
    /** the degree of the polynomials to use */
    protected int degreeT = -1;

    public static final String PROP_TOPK_S = "mpc.topk.s"; // the size of hash
                                                           // arrays
    public static final String PROP_TOPK_H = "mpc.topk.h"; // the number of hash
                                                           // arrays
    public static final String PROP_TOPK_K = "mpc.topk.k"; // the k in top-k

    /** contains the final results */
    protected HashMap<Long, Long> finalResults = null;

    /** The size of hash arrays */
    protected int H;
    /** The number of hash arrays */
    protected int S;
    /** The k in top-k */
    protected int K;

    /**
     * Creates a new MPC topk peer instance
     *
     * @param myPeerIndex
     *            This peer's number/index
     * @param stopper
     *            Stopper (can be used to stop this thread)
     * @param cm
     *            the connection manager
     */
    public TopkBase(int myPeerIndex, ConnectionManager cm, Stopper stopper) {
        super(makePeerName(myPeerIndex), myPeerIndex, cm, stopper);
        // TODO: Shouldn't this be protocolStopper = stopper?
        protocolStopper = new Stopper();
    }

    protected static String makePeerName(int myPeerIndex) {
        return "Top-k-" + myPeerIndex;
    }

    /**
     * Init the properties.
     */
    protected synchronized void initProperties() throws Exception {
        // TODO: What is the right "peerName" here?
        Properties properties = Configuration.getInstance(makePeerName(getMyPeerIndex())).getProperties();

        inputFolder = properties.getProperty(Configuration.PROP_INPUT_DIR,
                Configuration.DEFAULT_INPUT_DIR);
        outputFolder = properties.getProperty(Configuration.PROP_OUTPUT_DIR,
                Configuration.DEFAULT_OUTPUT_DIR);
        inputTimeout = Integer.valueOf(properties.getProperty(
                Configuration.PROP_INPUT_TIMEOUT,
                Configuration.DEFAULT_INPUT_TIMEOUT));

        randomAlgorithm = properties.getProperty(Configuration.PROP_PRG,
                Configuration.DEFAULT_PRG);
        random = new Random();

        timeSlotCount = Integer.valueOf(properties
                .getProperty(Configuration.PROP_NUMBER_OF_TIME_SLOTS));
        numberOfItems = Integer.valueOf(properties
                .getProperty(Configuration.PROP_NUMBER_OF_ITEMS));
        minInputPeers = Integer.valueOf(properties
                .getProperty(Configuration.PROP_MIN_INPUTPEERS));
        minPrivacyPeers = Integer.valueOf(properties
                .getProperty(Configuration.PROP_MIN_PRIVACYPEERS));
        setMyPeerID(properties.getProperty(Configuration.PROP_MY_PEER_ID));
        shamirSharesFieldOrder = Long.valueOf(properties.getProperty(
                Configuration.PROP_FIELD, Configuration.DEFAULT_FIELD));
        degreeT = Integer.valueOf(properties.getProperty(
                Configuration.PROP_DEGREE, "-1"));

        myAlphaIndex = Collections.binarySearch(
                connectionManager.getConfiguredPrivacyPeerIDs(), getMyPeerID());

        /*
         * Properties specific to the top-k protocol.
         */
        K = Integer.valueOf(properties.getProperty(PROP_TOPK_K));
        S = Integer.valueOf(properties.getProperty(PROP_TOPK_S));
        H = Integer.valueOf(properties.getProperty(PROP_TOPK_H));

        // output properties to log
        logger.info("The following properties were set:");
        logger.info("time slot count: " + timeSlotCount);
        logger.info("number of items per time slot: "
                + numberOfItems);
        logger.info("random algorithm: " + randomAlgorithm);
        logger.info("minInputPeers: " + minInputPeers);
        logger.info("minPrivacyPeers: " + minPrivacyPeers);
        logger.info("Shamir shares field order: "
                + shamirSharesFieldOrder);
        logger.info("Shamir shares polynomial degree: " + degreeT);
        logger.info("myID: " + getMyPeerID());
        logger.info("my alpha index: " + myAlphaIndex);

        logger.info("PPTKS: K=" + K + ", S=" + S + ", H=" + H);
    }

    /**
     * Process message received by an observable.
     *
     * @param observable
     *            Observable who sent the notification
     * @param object
     *            The object that was sent by the observable
     */
    protected abstract void notificationReceived(Observable observable,
            Object object) throws Exception;

    /**
     * Invoked when an observable that we're observing is notifying its
     * observers
     *
     * @param observable
     *            Observable who sent the notification
     * @param object
     *            The object that was sent by the observable
     */
    public void update(Observable observable, Object object) {
        ExceptionEvent exceptionEvent;
        String errorMessage;

        if (object == null) {
            logger.error("Received a null message from observable: "
                    + observable.getClass().getName());
            return;
        }

        logger.info("Received notification from observable: "
                + observable.getClass().getName() + " (object is of type: "
                + object.getClass().getName() + ")");

        try {
            /*
             * !!! WATCH OUT FOR ORDER (e.g. TopkMessage will go for !!!
             * MpcMessage, too, since it is subclassing it!) -> always check !!!
             * subclasses first
             */
            if (object instanceof TopkMessage) {
                notificationReceived(observable, object);

            } else if (object instanceof ExceptionEvent) {
                exceptionEvent = (ExceptionEvent) object;
                logger.error("Received Exception Event..."
                        + exceptionEvent.getMessage());
                sendExceptionEvent(exceptionEvent);

            } else {
                errorMessage = "Unexpected message type: "
                        + object.getClass().getName();
                logger.error(errorMessage);
                sendExceptionEvent(this, errorMessage);
            }

        } catch (Exception e) {
            errorMessage = "Error when processing event: "
                    + Utils.getStackTrace(e);
            logger.error(errorMessage);
            sendExceptionEvent(this, e, errorMessage);
        }
    }

    /**
     * Does some cleaning up.
     */
    protected synchronized void cleanUp() throws Exception {
        // Stop all started threads
        stopProcessing();
    }

    /**
     * Generates a string representation of an IPv4 address stored in an
     * integer.
     *
     * @param i
     *            the integer
     * @return the IPv4 address as a string
     */
    public static String renderIPV4Address(long i) {
        return ((i >> 24) & 0xFF) + "." + ((i >> 16) & 0xFF) + "."
                + ((i >> 8) & 0xFF) + "." + (i & 0xFF);
    }
}
