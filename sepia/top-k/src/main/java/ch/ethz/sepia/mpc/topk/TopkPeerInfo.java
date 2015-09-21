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

/**
 * stores information about a topk (privacy) peer
 *
 * @author Dilip Many
 *
 */
public class TopkPeerInfo {

	private String ID;
	private int index;

	/** contains the initial shares */
	private boolean isInitialSharesReceived = false;
	private long[][] initialKeyShares = null; // [S][H]
	private long[][] initialValueShares = null; // [S][H]

	/**
	 * Creates a new topk info object
	 */
	public TopkPeerInfo(String ID, int index) {
		this.ID = ID;
		this.index = index;
	}

	public String getID() {
		return ID;
	}

	public void setID(String iD) {
		ID = iD;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
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

	public boolean isInitialSharesReceived() {
		return isInitialSharesReceived;
	}

	public void setInitialSharesReceived(boolean isInitialSharesReceived) {
		this.isInitialSharesReceived = isInitialSharesReceived;
	}
}
