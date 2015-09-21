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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;



/**
 * This interface defines the methods that need to be provided by data classes for the TopK protocol.
 * @author Dilip Many
 */

interface TopKData {

	/**
	 * @return the inputFile
	 */
	public File getInputFile();

	/**
	 * @param inputFile the inputFile to set
	 */
	public void setInputFile(File inputFile);

	/**
	 * Opens the input file.
	 * @throws FileNotFoundException
	 */
	public void openFile() throws Exception;

	/**
	 * Closes the input file.
	 * @throws IOException
	 */
	public void closeInputFile() throws IOException;

	/**
	 * Reads the next record from the file.
	 * @throws IOException
	 */
	public void readNextTimeslot() throws Exception;

	/**
	 * Returns true if the distribution is an IPv4 address distribution.
	 * The type of distribution is inferred from the file name.
	 * @return true if the distribution is an IP address distribution
	 */
	public boolean isIPv4Distribution();

	/**
	 * Returns true if the distribution is a port distribution.
	 * The type of distribution is inferred from the file name.
	 * @return true if the distribution is a port distribution
	 */
	public boolean isPortDistribution();

	/**
	 * @return the distribution
	 */
	public HashMap<Integer, Integer> getDistribution();
}
