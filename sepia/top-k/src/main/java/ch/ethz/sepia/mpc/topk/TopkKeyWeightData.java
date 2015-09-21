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

import java.util.HashMap;

import ch.ethz.sepia.services.LineData;

/**
 * This class reads key weight tuples from CSV.
 * The distribution is stored in a HashMap.
 * @author Dilip Many
 */

public class TopkKeyWeightData extends LineData implements TopKData {
	/** character separating columns/fields in the input alert data file */
	private static final String FIELD_SEPARATOR = ";";

	private HashMap<Integer, Integer> distribution;



	/**
	 * Opens the input file.
	 * @throws Exception
	 */
	public void openFile() throws Exception {
		super.openFile();
		distribution = new HashMap<Integer, Integer>();
	}


	protected void parseAndStoreTuple(String line) throws NumberFormatException {
		String[] fields = line.split(FIELD_SEPARATOR);
		distribution.put(Integer.valueOf(fields[0]), Integer.valueOf(fields[1]));
	}


	/**
	 * Returns true if the distribution is an IPv4 address distribution.
	 * The type of distribution is inferred from the file name.
	 * @return true if the distribution is an IP address distribution
	 */
	public boolean isIPv4Distribution() {
		return false;
	}


	/**
	 * Returns true if the distribution is a port distribution.
	 * The type of distribution is inferred from the file name.
	 * @return true if the distribution is a port distribution
	 */
	public boolean isPortDistribution() {
		return false;
	}


	/**
	 * @return the distribution
	 */
	public HashMap<Integer, Integer> getDistribution() {
		return distribution;
	}
}
