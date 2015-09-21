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

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.PrivacyViolationException;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import ch.ethz.sepia.services.Stopper;

/**
 * common functionalities for all topk protocol classes
 *
 * @author Dilip Many
 *
 */
public abstract class TopkProtocol extends PrimitivesEnabledProtocol implements Runnable {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(TopkProtocol.class));

	/** holds the message to be sent over the connection */
	protected TopkMessage messageToSend;
	/** hold the last received message */
	protected TopkMessage messageReceived;
	/** defines the string which precedes a topk protocol message */
	protected static String TOPK_MESSAGE = "TOPK_MESSAGE";


	/**
	 * creates a new protocol instance
	 *
	 * @param threadNumber	This peer's thread number (for identification when notifying observers)
	 * @param topkPeer	(Privacy) Peer who started the protocol
	 * @param otherPeerID   the other peer's ID
	 * @param stopper		Can be used to stop a running protocol thread
	 */
	public TopkProtocol(int threadNumber, TopkBase topkPeer, String otherPeerID, int otherPeerIndex, Stopper stopper) {
		super(threadNumber, topkPeer.getConnectionManager(), topkPeer.getMyPeerID(), otherPeerID, topkPeer.getMyPeerIndex(), otherPeerIndex, stopper);

		initializeProtocolPrimitives(topkPeer);
	}


	/**
	 * Sends a topk message over the connection.
	 * @throws PrivacyViolationException
	 */
	protected void sendMessage() throws PrivacyViolationException {
		logger.info("Sending topk message (to " + otherPeerID + ")...");
		connectionManager.send(otherPeerID, TOPK_MESSAGE);
		connectionManager.send(otherPeerID, messageToSend);
	}


	/**
	 * Receives a topk message over the connection.
	 * (the received message is stored in the messageReceived variable)
	 * @throws PrivacyViolationException
	 */
	protected void receiveMessage() throws PrivacyViolationException {
		logger.info("Waiting for topk message to arrive ( from " + otherPeerID + ")...");
		String messageType = (String) connectionManager.receive(otherPeerID);
		messageReceived = (TopkMessage) connectionManager.receive(otherPeerID);

		// If the input peer has disconnected, null is returned
		if(messageType==null || messageReceived==null) {
			/*
			 * Even though the input peer has left, we need to notify our observers in order
			 * not to block protocol execution. Use a dummy message.
			 */
			messageReceived = new TopkMessage(otherPeerID, otherPeerIndex);
			messageReceived.setIsDummyMessage(true);

			logger.info("No connection to "+otherPeerID+". Notifying Observers with DUMMY message... ");
			notify(messageReceived);
		} else if (TOPK_MESSAGE.equals(messageType)) {
			logger.info("Received " + messageType + " message type from "+otherPeerID+". Notifying Observers... ");
			notify(messageReceived);
		} else {
			logger.warn("Received unexpected message type (expected: " + TOPK_MESSAGE + ", received: " + messageType);
		}
	}

	/**
	 * Checks whether the protocol was stopped.
	 * @return true if the protocol was stopped, false otherwise.
	 */
	protected boolean wasIStopped() {
		// Leave if someone stopped you
		if (stopper.isStopped()) {
			logger.info("Protocol thread handling "+otherPeerID+" was stopped, returning...");
			return true;
		}
		return false;
	}
}
