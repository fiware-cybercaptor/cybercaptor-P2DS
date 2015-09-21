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

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

import ch.ethz.sepia.mpc.MessageBase;

/**
 * message used to exchange data among (privacy) peers in the ADDITIVE protocol
 * 
 * @author Dilip Many
 * 
 */
@XmlRootElement
public class AdditiveMessage extends MessageBase implements Serializable {
    private static final long serialVersionUID = 3461683923455914692L;

    /** contains the final results */
    private long[] finalResults = null;
    /** contains the initial shares */
    private long[] initialShares = null;

    /** indicates if the message contains the final results */
    private boolean isFinalResultMessage = false;
    /**
     * Message Type Flags
     */
    /** indicates if the message contains the initial shares */
    private boolean isInitialSharesMessage = false;

    public AdditiveMessage() { /* jaxb needs this */
    }

    /**
     * creates a new ADDITIVE protocol message with the specified sender id and
     * index
     * 
     * @param senderID
     *            the senders id
     * @param senderIndex
     *            the senders index
     */
    public AdditiveMessage(final String senderID, final int senderIndex) {
        super(senderID, senderIndex);
    }

    public long[] getFinalResults() {
        return this.finalResults;
    }

    /**
     * @return the initial shares
     */
    public long[] getInitialShares() {
        return this.initialShares;
    }

    /**
     * @return the final results
     */
    public long[] getResults() {
        return this.finalResults;
    }

    public boolean isFinalResultMessage() {
        return this.isFinalResultMessage;
    }

    public boolean isInitialSharesMessage() {
        return this.isInitialSharesMessage;
    }

    public void setFinalResultMessage(final boolean isFinalResultMessage) {
        this.isFinalResultMessage = isFinalResultMessage;
    }

    public void setFinalResults(final long[] finalResults) {
        this.finalResults = finalResults;
    }

    public void setInitialShares(final long[] initialShares) {
        this.initialShares = initialShares;
    }

    public void setInitialSharesMessage(final boolean isInitialSharesMessage) {
        this.isInitialSharesMessage = isInitialSharesMessage;
    }

    public void setIsFinalResultMessage(final boolean isFinalResultMessage) {
        this.isFinalResultMessage = isFinalResultMessage;
    }

    public void setIsInitialSharesMessage(final boolean isInitialSharesMessage) {
        this.isInitialSharesMessage = isInitialSharesMessage;
    }

    /**
     * @param finalResults
     *            the finalResults to set
     */
    public void setResults(final long[] finalResults) {
        this.finalResults = finalResults;
    }

    /**
     * sets the initial shares
     * 
     * @param shares
     *            the initial shares to set
     */
    public void setShares(final long[] shares) {
        this.initialShares = shares;
    }

    /**
     * Returns a String representation of this message.
     * 
     * @return a String representation of this message
     */
    @Override
    public String toString() {
        return "[isGoodBye=" + isGoodbyeMessage() + ";isHello="
                + isHelloMessage() + ";isInitialShares="
                + isInitialSharesMessage() + ";isFinalResult="
                + isFinalResultMessage() + "]";

    }
}
