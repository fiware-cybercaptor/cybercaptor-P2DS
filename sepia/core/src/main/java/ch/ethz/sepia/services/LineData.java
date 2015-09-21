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

package ch.ethz.sepia.services;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This abstract class reads records from a file.
 * Each data record is stored in a separate line.
 * @author Dilip Many
 */
public abstract class LineData {
    private static final Logger logger = LogManager.getLogger(LineData.class);
    
	protected File inputFile;
	protected LineNumberReader lineNumberReader = null;


	/**
	 * @return the inputFile
	 */
	public File getInputFile() {
		return inputFile;
	}


	/**
	 * @param inputFile
	 *            the inputFile to set
	 */
	public void setInputFile(File inputFile) {
		this.inputFile = inputFile;
	}


	/**
	 * Opens the input file.
	 * (Any initialization required before reading the data shall be done here.)
	 * @throws Exception 
	 */
	public void openFile() throws Exception {
		lineNumberReader = Services.initializeLineNumberReader(inputFile);
	}


	/**
	 * Closes the input file.
	 * @throws IOException
	 */
	public void closeInputFile() throws IOException {
	}


	/**
	 * Reads all records from the file.
	 * @throws IOException 
	 */
	public void readNextTimeslot() throws Exception {
		if(lineNumberReader == null) {
			openFile();
		}

		logger.info("Reading input file: "+inputFile.getName());
		String nextLine;
		try {
			lineNumberReader = Services.initializeLineNumberReader(inputFile);
			nextLine = lineNumberReader.readLine();
			while (nextLine != null) {
				parseAndStoreTuple(nextLine);
				nextLine = lineNumberReader.readLine();
			}
		} catch (Exception e) {
			logger.error("Error when reading from file \"" + inputFile.getName() + "\":" +Utils.getStackTrace(e));
		}
		logger.info("Read "+lineNumberReader.getLineNumber()+" lines");
	}


	protected abstract void parseAndStoreTuple(String line);
}
