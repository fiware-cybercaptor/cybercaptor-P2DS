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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

/**
 * This class holds input data organized in vectors, e.g., a sequence of volume metric values or
 * a histogram. It is, for instance, used by the addition, entropy, and unique count protocol.
 *
 * @author Martin Burkhart
 *
 */
public class VectorData implements Serializable{
    private static final String CSV_SEPARATOR = ";";

	private static final XLogger logger = new XLogger(LoggerFactory.getLogger(VectorData.class));

	private static final long serialVersionUID = -8392988851788249594L;

	/** If the result of the computation is not a vector it is stored in this field.
	 * E.g., when entropy or unique count is computed, the distribution is summarized
	 * in a single output value)*/
	protected Double aggregatedOutput;

	/** stores the number of vector elements. */
	protected int elementCount;
	/** holds the input data as read from the file. */
	protected long[] input;

	/** holds the output data, i.e., the result of the aggregation. */
	protected long[] output;

	/**
	 * Default constructor.
	 */
	public VectorData() {
	}

	/**
	 * @return the aggregatedResult
	 */
	public Double getAggregatedOutput() {
		return aggregatedOutput;
	}


	/**
	 * @return the elementCount
	 */
	public int getElementCount() {
		return elementCount;
	}

	// ----------------------
	// Getters and setters
	// ----------------------

	/**
	 * @return the input
	 */
	public long[] getInput() {
		return input;
	}

	/**
	 * @return the output
	 */
	public long[] getOutput() {
		return output;
	}

	/**
	 * Reads the vector data from a file and stores it in the input array.
	 * The file is expected to contain a single row with comma-separated values.
	 *
	 * @param file			file to read from
	 * @throws IOException
	 */
	public void readDataFromFile(final File file) throws IOException {
		logger.info("Reading input file: "+file.getName());

        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
		String line = bufferedReader.readLine();
		line = line.replace(",", CSV_SEPARATOR);
        final String[] elements = line.split(CSV_SEPARATOR);

        elementCount = elements.length;
        input = new long[elementCount];
        output = new long[elementCount];

        for (int i = 0; i < elementCount; i++) {
            input[i] = Long.valueOf(elements[i].trim());
        }

		logger.info("Read "+elementCount+" elements.");
		bufferedReader.close();
	}

	/**
	 * @param aggregatedOutput the aggregated output to set
	 */
	public void setAggregatedOutput(final Double aggregatedOutput) {
		this.aggregatedOutput = aggregatedOutput;
	}

	/**
	 * @param input the input to set
	 */
	public void setInput(final long[] input) {
		this.input = input;
		elementCount = input == null ? 0 : input.length;
	}

	/**
	 * @param output the output to set
	 */
	public void setOutput(final long[] output) {
		this.output = output;
		elementCount = output == null ? 0 : output.length;
	}

	/**
	 * Writes the output to a file. The output file will be comma-separated.
	 * If the aggregated result was set using {@link #setAggregatedOutput(Double)} then the
	 * aggregated result is written, otherwise the output vector is written.
	 * @param file file to write to
	 * @throws FileNotFoundException
	 */
	public void writeOutputToFile(final File file) throws FileNotFoundException {
		logger.info("Writing output file: "+file.getName());

		final StringBuffer line = new StringBuffer();
		if (aggregatedOutput != null) {
			line.append(aggregatedOutput);
		} else {
			for(int i=0; i<elementCount; i++) {
				if (i>0) {
					line.append(CSV_SEPARATOR);
					line.append(" ");
				}
				line.append(output[i]);
			}
		}
		line.append('\n');
		final PrintWriter writer = new PrintWriter(new FileOutputStream(file));
		writer.print(line.toString());
		writer.flush();
		writer.close();
	}

}
