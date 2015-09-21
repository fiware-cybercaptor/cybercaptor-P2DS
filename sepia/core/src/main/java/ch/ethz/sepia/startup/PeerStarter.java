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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.connections.InputPeerConnectionManager;
import ch.ethz.sepia.connections.PrivacyPeerAddress;
import ch.ethz.sepia.connections.PrivacyPeerConnectionManager;
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
	private static final Logger logger = LogManager
			.getLogger(PeerStarter.class);

	protected ConnectionManager connectionManager = null;
	protected String errorMessage = null;
	protected boolean isInputPeer;
	protected int minInputPeers = 0;
	protected int minPrivacyPeers = 0;
	protected Stopper mpcStopper = null;
	protected String myID = null;
	protected int myServerPort;
	protected int numberOfItems = 0;
	protected int numberOfTimeSlots = 0;
	protected String outputFileName;
	protected PeerBase peer = null;
	private final String peerName;
	protected List<PrivacyPeerAddress> privacyPeers = null;
	protected Stopper stopper = null;
	protected int timeout = 0;
	protected int timeSlotsToDo = 0;
	private long timestampStartRound;
	protected boolean useCompression = false;

	/**
	 * Superclass of all peers.
	 * 
	 * @param stopper
	 *            Can be used to stop a running thread.
	 * @param isInputPeer
	 *            true if it is an input peer, false for privacy peers
	 */
	public PeerStarter(final String peerName, final Stopper stopper,
			final boolean isInputPeer) {
		this.peerName = peerName;
		this.stopper = stopper;
		this.mpcStopper = new Stopper();
		this.outputFileName = null;
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

	public int getNumberOfTimeSlots() {
		return this.numberOfTimeSlots;
	}

	/**
	 * Loads and initializes the keystore. Creates an SSLContext, which has the
	 * local certificates associated for client authentication.
	 * 
	 * @note You can add a keystore with trusted certificates via the VM
	 *       parameter "-Djavax.net.ssl.trustStore".
	 * @return an the SSLContext for client authentication.
	 */
	private SSLContext initKeyStore() {
		KeyStore keyStore;
		SSLContext sslContext = null;

		Properties properties = Configuration.getInstance(this.peerName)
				.getProperties();

		try {
			keyStore = KeyStore.getInstance("JKS");
			String keystoreName = properties
					.getProperty(Configuration.PROP_KEY_STORE);
			keyStore.load(new FileInputStream(keystoreName), properties
					.getProperty(Configuration.PROP_KEY_STORE_PASSWORD)
					.toCharArray());
			logger.info("Loaded keystore from file: " + keystoreName);

			KeyManagerFactory keyManagerFactory = KeyManagerFactory
					.getInstance("SunX509");
			keyManagerFactory.init(keyStore,
					properties.getProperty(Configuration.PROP_KEY_PASSWORD)
							.toCharArray());
			logger.info("KeyStore class: " + keyStore.getClass());
			logger.info("KeyStore Type/Provider/Size: " + keyStore.getType()
					+ "/" + keyStore.getProvider() + "/" + keyStore.size());

			// Init the SSLContext with the certificates from the local keystore
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
		} catch (KeyStoreException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (NoSuchAlgorithmException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (CertificateException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (FileNotFoundException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (IOException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (UnrecoverableKeyException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (KeyManagementException e) {
			logger.error(Utils.getStackTrace(e));
		}
		return sslContext;
	}

	/**
	 * Reads the properties and sets internal values accordingly.
	 */
	public void initProperties() {
		Properties properties = Configuration.getInstance(this.peerName)
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
		this.useCompression = Boolean.parseBoolean(properties
				.getProperty(Configuration.PROP_CONNECTION_USE_COMPRESSION));

		// Parse the privacy peer config entry
		this.privacyPeers = new ArrayList<PrivacyPeerAddress>();
		String ppString = properties
				.getProperty(Configuration.PROP_ACTIVE_PRIVACY_PEERS);
		if (ppString != null) {
			String[] pps = ppString.split(";");
			for (String pp : pps) {
				String[] parts = pp.split(":");
				PrivacyPeerAddress ppa = new PrivacyPeerAddress(parts[0],
						parts[1], Integer.parseInt(parts[2]));
				this.privacyPeers.add(ppa);

				if (this.myID.equals(ppa.id)) {
					// note down my server port
					this.myServerPort = ppa.serverPort;
				}
			}

			/*
			 * All must have the same order to ensure that each privacy peer
			 * gets shares for the same evaluation point from all the input
			 * peers
			 */
			Collections.sort(this.privacyPeers);
		} else {
			logger.error("No privacy peers configured!");
		}

		// Output the properties that were set
		logger.info("The following properties were set:");
		logger.info("minInputPeers: " + this.minInputPeers);
		logger.info("minPrivacyPeers: " + this.minPrivacyPeers);
		logger.info("timeout: " + this.timeout);
		logger.info("use compression: " + this.useCompression);
		logger.info("my peer ID: " + this.myID);

		StringBuilder ppsb = new StringBuilder("Configured privacy peers:");
		boolean rest = false;
		for (PrivacyPeerAddress ppa : this.privacyPeers) {
			if (rest) {
				ppsb.append(", ");
			}
			ppsb.append(ppa.toString());
			rest = true;
		}
		logger.info(ppsb);
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
			ConnectionManager.logStatistics();
			ConnectionManager.newStatisticsRound();
			if (!this.isInputPeer) {
				PrimitivesEnabledProtocol.logStatistics();
				// PrimitivesEnabledProtocol.newStatisticsRound();
			}

			// Output timing statistics
			long currentTs = System.currentTimeMillis();
			logger.trace("--> Running time of round (including connection discovery!): "
					+ (currentTs - this.timestampStartRound)
					/ 1000.0
					+ " seconds.");
			this.timestampStartRound = currentTs;

			logger.trace("Time slots to do: " + this.timeSlotsToDo);

			if (this.timeSlotsToDo <= 0) {
				logger.info("Secret Sharing done for all time slots");

				// We're done!
				stopProcessing();
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
			SSLContext sslContext = initKeyStore();
			if (this.isInputPeer) {
				this.connectionManager = new InputPeerConnectionManager(
						this.myID, this.privacyPeers, sslContext);
				this.connectionManager
						.setEnableCompression(this.useCompression);
				// Other input peers don't connect to us
				this.connectionManager.setMinInputPeers(0);
				this.connectionManager.setMinPrivacyPeers(this.minPrivacyPeers);
				this.connectionManager.setActiveTimeout(this.timeout);
			} else {
				this.connectionManager = new PrivacyPeerConnectionManager(
						this.myID, this.myServerPort, this.privacyPeers,
						sslContext);
				this.connectionManager
						.setEnableCompression(this.useCompression);
				this.connectionManager.setMinInputPeers(this.minInputPeers);
				this.connectionManager.setActiveTimeout(this.timeout);
				/*
				 * For privacy peers, the expected number of privacy peer
				 * connections is minPrivacyPeers-1, because they are a privacy
				 * peer themselves.
				 */
				this.connectionManager
						.setMinPrivacyPeers(this.minPrivacyPeers - 1);
				((PrivacyPeerConnectionManager) this.connectionManager).start();
			}

			// Start MPC primitive and add this to the observer list
			logger.info("Starting MPC");
			createMPCinstance();
			startMPC();

		} catch (Exception e) {
			String message = "Unexpected error in run(): "
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
		if (this.connectionManager != null) {
			if (!this.isInputPeer) {
				((PrivacyPeerConnectionManager) this.connectionManager).stop();
			}
			this.connectionManager.closeConnections();
		}
	}

	protected void stopProcessing() {
		logger.info("Shutting down SEPIA");
		this.mpcStopper.setIsStopped(true);
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
		} catch (Exception e) {
			logger.error("Error when processing event: "
					+ Utils.getStackTrace(e) + "("
					+ observable.getClass().getName() + ")");
			exceptionEvent = new ExceptionEvent(this, new Exception(
					"Error when processing event: " + e.getMessage()));
			notify(exceptionEvent.getMessage(), exceptionEvent);
			stopProcessing();
		}
	}
}
