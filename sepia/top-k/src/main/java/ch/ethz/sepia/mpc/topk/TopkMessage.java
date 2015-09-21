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

import java.io.Serializable;
import java.util.HashMap;

import ch.ethz.sepia.mpc.MessageBase;

/**
 * message used to exchange data among (privacy) peers in the topk protocol
 *
 * @author Dilip Many
 *
 */
public class TopkMessage extends MessageBase implements Serializable {
	private static final long serialVersionUID = 3461683923455914692L;

	/**
	 * Message Type Flags
	 */
	/** indicates if the message contains the initial shares */
	private boolean isInitialSharesMessage = false;
	/** indicates if the message contains the final results */
	private boolean isFinalResultMessage = false;

	/** contains the initial shares */
	private long[][] initialKeyShares = null; // [S][H]
	private long[][] initialValueShares = null; // [S][H]

	/** contains the final results */
	private HashMap<Long, Long> finalResults = null;

	private boolean keysAreIpAddresses = false;

	/**
	 * creates a new topk protocol message with the specified sender id and
	 * index
	 *
	 * @param senderID
	 *            the senders id
	 * @param senderIndex
	 *            the senders index
	 */
	public TopkMessage(String senderID, int senderIndex) {
		super(senderID, senderIndex);
	}

	public boolean isInitialSharesMessage() {
		return isInitialSharesMessage;
	}

	public void setIsInitialSharesMessage(boolean isInitialSharesMessage) {
		this.isInitialSharesMessage = isInitialSharesMessage;
	}

	public boolean isFinalResultMessage() {
		return isFinalResultMessage;
	}

	public void setIsFinalResultMessage(boolean isFinalResultMessage) {
		this.isFinalResultMessage = isFinalResultMessage;
	}

	public HashMap<Long, Long> getFinalResults() {
		return finalResults;
	}

	public void setFinalResults(HashMap<Long, Long> finalResults) {
		this.finalResults = finalResults;
	}

	public long[][] getInitialKeyShares() {
		return initialKeyShares;
	}

	public void setInitialKeyShares(long[][] initialKeyShares) {
		this.initialKeyShares = initialKeyShares;
	}

	public long[][] getInitialValueShares() {
		return initialValueShares;
	}

	public void setInitialValueShares(long[][] initialValueShares) {
		this.initialValueShares = initialValueShares;
	}

	public boolean areKeysIpAddresses() {
		return keysAreIpAddresses;
	}

	public void setKeysAreIpAddresses(boolean keysAreIpAddresses) {
		this.keysAreIpAddresses = keysAreIpAddresses;
	}

}
