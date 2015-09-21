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

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * A message for a general MPC message that is exchanged between peers.
 * 
 * @author Lisa Barisic, ETH Zurich
 */
@XmlRootElement
public class MessageBase implements Serializable {
    private static final long serialVersionUID = 7307382590667505236L;

    protected boolean isDummyMessage = false;

    protected boolean isGoodbyeMessage = false;

    protected boolean isHelloMessage = false;

    protected boolean isVerificationSuccessful = true;

    protected String message = null;

    protected int metricCount = 0;
    protected int roundNumber = 0;
    protected String senderID = null;
    protected int senderIndex = 0;
    protected int timeSlotCount = 0;
    protected boolean wasGoodbyeReceived = true; // Distinguish between goodbyes
                                                 // received and sent

    public MessageBase() { /* jaxb needs this */
    }

    /**
     * Creates a new instance of a message.
     * 
     * @param roundNumber
     *            Current round number
     * @param senderID
     *            Sender's ID
     * @param senderIndex
     *            Sender's number or index
     */
    public MessageBase(final int roundNumber, final String senderID,
            final int senderIndex) {
        this(senderID, senderIndex);
        this.roundNumber = roundNumber;
    }

    /**
     * Creates a new instance of a message.
     * 
     * @param senderID
     *            Sender's ID
     * @param senderIndex
     *            Sender's number or index
     */
    public MessageBase(final String senderID, final int senderIndex) {
        this.senderID = senderID;
        this.senderIndex = senderIndex;
    }

    public String getMessage() {
        return this.message;
    }

    public int getMetricCount() {
        return this.metricCount;
    }

    public int getRoundNumber() {
        return this.roundNumber;
    }

    public String getSenderID() {
        return this.senderID;
    }

    public int getSenderIndex() {
        return this.senderIndex;
    }

    public int getTimeSlotCount() {
        return this.timeSlotCount;
    }

    /**
     * Dummy messages are used to notify observers even though the connection
     * counterpart is offline.
     * 
     * @return <true> if this is a dummy message.
     */
    public boolean isDummyMessage() {
        return this.isDummyMessage;
    }

    public boolean isGoodbyeMessage() {
        return this.isGoodbyeMessage;
    }

    public boolean isHelloMessage() {
        return this.isHelloMessage;
    }

    public boolean isVerificationSuccessful() {
        return this.isVerificationSuccessful;
    }

    public boolean isWasGoodbyeReceived() {
        return this.wasGoodbyeReceived;
    }

    public void setDummyMessage(final boolean isDummyMessage) {
        this.isDummyMessage = isDummyMessage;
    }

    public void setGoodbyeMessage(final boolean isGoodbyeMessage) {
        this.isGoodbyeMessage = isGoodbyeMessage;
    }

    public void setHelloMessage(final boolean isHelloMessage) {
        this.isHelloMessage = isHelloMessage;
    }

    /**
     * Dummy messages are used to notify observers even though the connection
     * counterpart is offline.
     * 
     * @param isDummyMessage
     *            <true> if this is a dummy message.
     */
    public void setIsDummyMessage(final boolean isDummyMessage) {
        this.isDummyMessage = isDummyMessage;
    }

    public void setIsGoodbyeMessage(final boolean isGoodbyeMessage) {
        this.isGoodbyeMessage = isGoodbyeMessage;
    }

    public void setIsHelloMessage(final boolean isHelloMessage) {
        this.isHelloMessage = isHelloMessage;
    }

    public void setIsVerificationSuccessful(
            final boolean isVerificationSuccessful) {
        this.isVerificationSuccessful = isVerificationSuccessful;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public void setMetricCount(final int metricCount) {
        this.metricCount = metricCount;
    }

    public void setRoundNumber(final int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public void setSenderID(final String senderID) {
        this.senderID = senderID;
    }

    public void setSenderIndex(final int senderIndex) {
        this.senderIndex = senderIndex;
    }

    public void setTimeSlotCount(final int timeSlotCount) {
        this.timeSlotCount = timeSlotCount;
    }

    public void setVerificationSuccessful(final boolean isVerificationSuccessful) {
        this.isVerificationSuccessful = isVerificationSuccessful;
    }

    public void setWasGoodbyeReceived(final boolean wasGoodbyeReceived) {
        this.wasGoodbyeReceived = wasGoodbyeReceived;
    }

    public boolean wasGoodbyeReceived() {
        return this.wasGoodbyeReceived;
    }
}
