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

import java.util.Collections;
import java.util.List;
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
 * peers of the MPC ADDITIVE protocol.
 * 
 * @author Dilip Many
 * 
 */
public abstract class AdditiveBase extends PrimitivesEnabledPeer {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(AdditiveBase.class));

    public static final String OUTPUT_FILE_PREFIX = "sepia_putput";

    /** the degree of the polynomials to use */
    protected int degreeT = -1;
    /** contains the final results */
    protected long[] finalResults = null;
    protected String inputFolder;

    protected int inputTimeout;
    protected long maxElement;
    /** the alpha index of this (privacy) peer */
    protected int myAlphaIndex = 0;
    /** number of items per time slot */
    protected int numberOfItems = 0;
    protected String outputFolder;
    /** the size of the field to use for the Shamir shares computations */
    protected long shamirSharesFieldOrder = 0;

    protected boolean skipInputVerification;
    /** number of time slots */
    protected int timeSlotCount = 1;

    /**
     * Creates a new MPC ADDITIVE peer instance
     * 
     * @param myPeerIndex
     *            This peer's number/index
     * @param stopper
     *            Stopper (can be used to stop this thread)
     * @param cm
     *            the connection manager
     */
    public AdditiveBase(final String peerName, final int myPeerIndex,
            final ConnectionManager cm, final Stopper stopper) {
        super(peerName, myPeerIndex, cm, stopper);
        this.protocolStopper = new Stopper();
        logger.info("myPeerName(Constructor): " + this.myPeerName);
    }

    /**
     * Does some cleaning up. Stops all started threads.
     */
    @Override
    protected synchronized void cleanUp() throws Exception {
        // Stop all started threads
        stopProcessing();
    }

    /**
     * Init the properties.
     */
    @Override
    protected synchronized void initProperties() throws Exception {
        logger.info("myPeerName(initProperties): " + this.myPeerName);

        Properties properties = Configuration.getInstance(this.myPeerName)
                .getProperties();

        this.inputFolder = properties.getProperty(Configuration.PROP_INPUT_DIR,
                Configuration.DEFAULT_INPUT_DIR);
        this.outputFolder = properties
                .getProperty(Configuration.PROP_OUTPUT_DIR,
                        Configuration.DEFAULT_OUTPUT_DIR);
        this.inputTimeout = Integer.valueOf(properties.getProperty(
                Configuration.PROP_INPUT_TIMEOUT,
                Configuration.DEFAULT_INPUT_TIMEOUT));

        this.randomAlgorithm = properties.getProperty(Configuration.PROP_PRG,
                Configuration.DEFAULT_PRG);
        this.random = new Random();

        this.timeSlotCount = Integer.valueOf(properties
                .getProperty(Configuration.PROP_NUMBER_OF_TIME_SLOTS));
        this.numberOfItems = Integer.valueOf(properties
                .getProperty(Configuration.PROP_NUMBER_OF_ITEMS));
        this.minInputPeers = Integer.valueOf(properties
                .getProperty(Configuration.PROP_MIN_INPUTPEERS));
        this.minPrivacyPeers = Integer.valueOf(properties
                .getProperty(Configuration.PROP_MIN_PRIVACYPEERS));
        setMyPeerID(properties.getProperty(Configuration.PROP_MY_PEER_ID));
        this.shamirSharesFieldOrder = Long.valueOf(properties.getProperty(
                Configuration.PROP_FIELD, Configuration.DEFAULT_FIELD));
        this.degreeT = Integer.valueOf(properties.getProperty(
                Configuration.PROP_DEGREE, "-1"));

        this.connectionManager = Configuration.getInstance(this.myPeerName)
                .getConnectionManager();

        logger.info(this.connectionManager.toString());

        List<String> strList = this.connectionManager
                .getConfiguredPrivacyPeerIDs();
        logger.info("ConfiguredPrivacyPeerIDs");
        for (String str : strList) {
            logger.info("ConfiguredPrivacyPeerID: " + str);
        }

        this.myAlphaIndex = Collections.binarySearch(strList, getMyPeerID());

        // specific to addition
        this.skipInputVerification = Boolean.valueOf(properties
                .getProperty(Configuration.PROP_SKIP_INPUT_VERIFICATION));
        if (!this.skipInputVerification) {
            this.maxElement = Long.valueOf(properties.getProperty(
                    Configuration.PROP_MAXELEMENT,
                    Long.toString(this.shamirSharesFieldOrder - 1)));
        }

        // output properties to log
        logger.info("The following properties were set:");
        logger.info("time slot count: " + this.timeSlotCount);
        logger.info("number of items per time slot: " + this.numberOfItems);
        logger.info("random algorithm: " + this.randomAlgorithm);
        logger.info("minInputPeers: " + this.minInputPeers);
        logger.info("minPrivacyPeers: " + this.minPrivacyPeers);
        logger.info("Shamir shares field order: " + this.shamirSharesFieldOrder);
        logger.info("Shamir shares polynomial degree: " + this.degreeT);
        logger.info("myID: " + getMyPeerID());
        logger.info("my alpha index: " + this.myAlphaIndex);
        logger.info("Skip input verification: " + this.skipInputVerification);
        if (!this.skipInputVerification) {
            logger.info("Maximum value accepted: " + this.maxElement);
        }

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
    @Override
    public void update(final Observable observable, final Object object) {
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
             * !!! WATCH OUT FOR ORDER (e.g. ADDITIVEMessage will go for !!!
             * MpcMessage, too, since it is subclassing it!) -> always check !!!
             * subclasses first
             */
            if (object instanceof AdditiveMessage) {
                notificationReceived(observable, object);

            } else if (object instanceof ExceptionEvent) {
                exceptionEvent = (ExceptionEvent) object;
                logger.error("Received Exception Event"
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
}
