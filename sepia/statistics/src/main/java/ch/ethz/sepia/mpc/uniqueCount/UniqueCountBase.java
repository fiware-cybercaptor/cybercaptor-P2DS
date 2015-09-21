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

package ch.ethz.sepia.mpc.uniqueCount;

import java.util.Collections;
import java.util.Observable;
import java.util.Properties;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.ExceptionEvent;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledPeer;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;
import ch.ethz.sepia.startup.ConfigFile;


/**
 * This abstract class contains the functionality common to peers and
 * privacy peers of the MPC UniqueCount protocol.
 *
 * @author Dilip Many
 *
 */
public abstract class UniqueCountBase extends PrimitivesEnabledPeer {
    private static final Logger logger = LogManager.getLogger(UniqueCountBase.class);
    
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
	/** the degree of the polynomials to use*/
	protected int degreeT = -1;
	/** contains the final results */
	protected boolean skipInputVerification;	
	protected long[] finalResults = null;
	
	/** prefix of all uniquecount protocol properties */
	public static final String PROP_UNIQUECOUNT_FOOBAR = "mpc.uniquecount.foobar";
	
	/**
	 * Creates a new MPC uniquecount peer instance
	 * 
	 * @param myPeerIndex	This peer's number/index
	 * @param stopper		Stopper (can be used to stop this thread)
	 * @param cm the connection manager
	 */
	public UniqueCountBase(int myPeerIndex, ConnectionManager cm, Stopper stopper) {
		super(myPeerIndex, cm, stopper);
		protocolStopper = new Stopper();
	}

	/**
	 * Init the properties.
	 */
	protected synchronized void initProperties() throws Exception {
		Properties properties = ConfigFile.getInstance().getProperties();
		
        inputFolder = properties.getProperty(ConfigFile.PROP_INPUT_DIR, ConfigFile.DEFAULT_INPUT_DIR);
        outputFolder = properties.getProperty(ConfigFile.PROP_OUTPUT_DIR, ConfigFile.DEFAULT_OUTPUT_DIR);
    	inputTimeout = Integer.valueOf(properties.getProperty(ConfigFile.PROP_INPUT_TIMEOUT, ConfigFile.DEFAULT_INPUT_TIMEOUT));
		
		randomAlgorithm = properties.getProperty(ConfigFile.PROP_PRG, ConfigFile.DEFAULT_PRG);
		random = new Random();

		timeSlotCount = Integer.valueOf(properties.getProperty(ConfigFile.PROP_NUMBER_OF_TIME_SLOTS));
		numberOfItems = Integer.valueOf(properties.getProperty(ConfigFile.PROP_NUMBER_OF_ITEMS));
		minInputPeers = Integer.valueOf(properties.getProperty(ConfigFile.PROP_MIN_INPUTPEERS));
		minPrivacyPeers = Integer.valueOf(properties.getProperty(ConfigFile.PROP_MIN_PRIVACYPEERS));
		setMyPeerID(properties.getProperty(ConfigFile.PROP_MY_PEER_ID));
		shamirSharesFieldOrder = Long.valueOf(properties.getProperty(ConfigFile.PROP_FIELD, ConfigFile.DEFAULT_FIELD));
		degreeT = Integer.valueOf(properties.getProperty(ConfigFile.PROP_DEGREE, "-1"));

        myAlphaIndex = Collections.binarySearch(connectionManager.getConfiguredPrivacyPeerIDs(), getMyPeerID());
        
        skipInputVerification = Boolean.valueOf(properties.getProperty(ConfigFile.PROP_SKIP_INPUT_VERIFICATION));
        
		// output properties to log
		logger.info("The following properties were set:");
		logger.info("time slot count: " + timeSlotCount);
		logger.info("number of items per time slot: " + numberOfItems);
		logger.info("random algorithm: " + randomAlgorithm);
		logger.info("minInputPeers: " + minInputPeers);
		logger.info("minPrivacyPeers: " + minPrivacyPeers);
		logger.info("Shamir shares field order: " + shamirSharesFieldOrder);
		logger.info("Shamir shares polynomial degree: " + degreeT);
		logger.info("myID: " + getMyPeerID());
		logger.info("my alpha index: " + myAlphaIndex);
		logger.info("Skip input verification: " + skipInputVerification);
	}


	/**
	 * Process message received by an observable.
	 * 
	 * @param observable	Observable who sent the notification
	 * @param object		The object that was sent by the observable
	 */
	protected abstract void notificationReceived(Observable observable, Object object) throws Exception;


	/**
	 * Invoked when an observable that we're observing is notifying its
	 * observers
	 * 
	 * @param observable	Observable who sent the notification
	 * @param object		The object that was sent by the observable
	 */
	public void update(Observable observable, Object object) {
		ExceptionEvent exceptionEvent;
		String errorMessage;
		
		if(object==null) {
			logger.error("Received a null message from observable: " + observable.getClass().getName());
			return;
		}
		
		logger.info("Received notification from observable: " + observable.getClass().getName() + " (object is of type: " + object.getClass().getName() + ")");

		try {
			/* !!! WATCH OUT FOR ORDER (e.g. UniqueCountMessage will go for
			 * !!! MpcMessage, too, since it is subclassing it!) -> always check
			 * !!! subclasses first
			 */
			if (object instanceof UniqueCountMessage) {
				notificationReceived(observable, object);

			} else if (object instanceof ExceptionEvent) {
				exceptionEvent = (ExceptionEvent) object;
				logger.error("Received Exception Event" + exceptionEvent.getMessage());
				sendExceptionEvent(exceptionEvent);

			} else {
				errorMessage = "Unexpected message type: " + object.getClass().getName();
				logger.error(errorMessage);
				sendExceptionEvent(this, errorMessage);
			}

		} catch (Exception e) {
			errorMessage = "Error when processing event: " + Utils.getStackTrace(e);
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
	
	public boolean skipInputVerification() {
		return skipInputVerification;
	}

}
