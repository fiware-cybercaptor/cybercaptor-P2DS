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

package ch.ethz.sepia;

import java.io.File;
import java.io.LineNumberReader;

import ch.ethz.sepia.mpc.VectorData;
import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Utils;

/**
 * Service class to compute the Tsallis Entropy of the given data
 * or to compare results.
 * 
 * Tsallis entropy formula:
 * E_r(X) = \frac{1}{r-1}\left(1 - \sum_{i=1}^{n}p_i^r\right)
 * 
 * @author Lisa Barisic, Dilip Many, ETH Zurich
 */
public class TsallisEntropy {

    /** Argument saying that entropy computation should be done */
    public static final String ARG_ENTROPY_COMPUTATION = "-e";
    /** Argument saying that entropy computation and file comparison should be 
     * done 
     */
    public static final String ARG_FILE_COMPARISON = "-c";

    public static void main(String[] args) {
        String[] inputFiles;
        String resultFile;
        int tsallisExponent;
        int numberoftimeslots;
        int numberofitemsintimeslot;
        boolean timeseries = false;
        TsallisEntropy tsallisEntropy;


        System.out.println("Arguments: ");
        for (String s : args) {
            System.out.println(s);
        }
        System.out.println("");

        if (args != null) {
            if (args.length < 6) {
                usage();
            } else {
                try {
                    tsallisEntropy = new TsallisEntropy();
                    tsallisExponent = Integer.valueOf(args[1]);
                    System.out.println("tsallis Exponent: " + tsallisExponent);

                    numberoftimeslots = 1;
                    System.out.println("number of time slots: 1 (multiple in one file no longer supported)");
                    numberofitemsintimeslot = Integer.valueOf(args[3]);
                    System.out.println("number of items in time slot: " + numberofitemsintimeslot);
                    timeseries = Boolean.FALSE;
                    System.out.println("time series data: FALSE (no longer supported)\n");

//                    if (tsallisExponent <= 1) {
//                        System.out.println("Tsallis Exponents > 1 expected!");
//                        usage();
//                        System.exit(-1);
//                    }

                    if (args[0].equals(ARG_ENTROPY_COMPUTATION)) {
                        tsallisEntropy.computeEntropiesForFile(tsallisExponent, numberoftimeslots, numberofitemsintimeslot, args[5], args[6]);
                    } else if (args[0].equals(ARG_FILE_COMPARISON)) {
                        inputFiles = new String[args.length - 6];
                        for (int i = 5; i < args.length - 1; i++) {
                            inputFiles[i - 5] = args[i];
                            System.out.println("Input File: " + inputFiles[i - 5]);
                        }
                        resultFile = args[args.length - 1];
                        System.out.println("Result File: " + resultFile + "\n");

                        if (tsallisEntropy.compareFiles(tsallisExponent, numberoftimeslots, numberofitemsintimeslot, timeseries, inputFiles, resultFile)) {
                            System.out.println("\n***Comparison successful! *****");
                        } else {
                            System.out.println("\n***Comparison NOT successful! *****");
                        }
                    } else {
                        System.out.println("Unknown argument: " + args[0]);
                        usage();
                    }

                } catch (Exception e) {
                    System.out.println("Error: " + Utils.getStackTrace(e));
                    usage();
                }
            }
        }
    }

    /**
     * Reads the data from the given file and computes the entropy for each
     * time slot.
     */
    public void computeEntropiesForFile(int exponent, int numberOfTimeslots, int numberOfItems, String inputFile, String resultFile) throws Exception {
        VectorData data = new VectorData();

        data.readDataFromFile(new File(inputFile));
        double entropy = computeEntropy(exponent, data.getInput());
        System.out.println("Entropy: "+entropy);
        
        data.setAggregatedOutput(entropy);
        data.writeOutputToFile(new File(resultFile));
    }

    /**
     * Reads the data from the given file, adds up their values and computes
     * the entropy on the sum (for each time slot).
     * 
     * @param exponent Tsallis Exponent
     * @param inputFiles Input files to aggregate and compute entropy for
     * @param resultFile Result file to compare to computed entropies
     * 
     * @return true if comparison successful, false else
     */
    public boolean compareFiles(int exponent, int numberOfTimeslots, int numberOfItems, boolean timeSeries, String[] inputFiles, String resultFile) throws Exception {
        double computedEntropy;
        double resultFileEntropy = 0;
        double difference;
        boolean successful = true;

        // read in input data
        VectorData[] data = new VectorData[inputFiles.length]; 

        for(int peerIndex = 0; peerIndex < inputFiles.length; peerIndex++) {
	        data[peerIndex].readDataFromFile(new File(inputFiles[peerIndex]));
        }

        // Sum up values and compute entropies, then compare to result file
    	// sum up inputs of the peers
    	long[] itemSums = new long[numberOfItems];
    	for(int peerIndex = 0; peerIndex < data.length; peerIndex++) {
    		for(int itemIndex = 0; itemIndex < numberOfItems; itemIndex++) {
    			itemSums[itemIndex] += data[peerIndex].getInput()[itemIndex];
    		}
    	}

    	// compute entropy and compare to entropy from result file
    	computedEntropy = computeEntropy(exponent, itemSums);

        // read in result data
    	LineNumberReader resultFileLineNumberReader = Services.initializeLineNumberReader(resultFile);
        resultFileLineNumberReader.readLine(); // ignore header line
    	String line = resultFileLineNumberReader.readLine();
    	if(line != null) {
    		resultFileEntropy = Double.valueOf(line);
    	}
    	if (computedEntropy == resultFileEntropy) {
    		System.out.println("Comparison successful! Computed: " + computedEntropy + "==" + resultFileEntropy);
    	} else {
    		difference = computedEntropy - resultFileEntropy;
    		System.out.println("Entropy mismatch. Computed: " + computedEntropy + ", read from result file: " + resultFileEntropy + " (difference: " + difference + ")!");
    		successful = false;
    	}
        
        return successful;
    }

    public static void usage() {
        System.out.println("\nsepia: Computation of Tsallis Entropies");
        System.out.println("\njava -jar sepia.jar <arguments>");
        System.out.println("The main class of the jar must be services.TsallisEntropy.");
        System.out.println("To change the main class: 'jar -fue sepia.jar services.TsallisEntropy");
        System.out.println("\nArguments (use either, but not both at once!):");
        System.out.println("\n-----------------------------------------------------");
        System.out.println(ARG_ENTROPY_COMPUTATION + " <tsallisExponent> <numberOfTimeslots> <numberOfItemsPerTimeslot> <inputFile> <outputFileName>");
        System.out.println("\tExample: java -jar sepia.jar " + ARG_ENTROPY_COMPUTATION + " 2 10 21 myInputFile myOutputFile");
        System.out.println("\tUse this option to compute the entropies of the entries in the input file.");
        System.out.println("\tResulting entropies are stored in the given output file name.");
        System.out.println("\n-----------------------------------------------------");
        System.out.println(ARG_FILE_COMPARISON + "  <tsallisExponent> <numberOfTimeslots> <numberOfItemsPerTimeslot> <InputFile1> ... <inputFileN> <resultFileToCompareTo>");
        System.out.println("\tExample: java -jar sepia.jar " + ARG_FILE_COMPARISON + " 2 10 65536 myFile01 myFile02 myResultFile");
        System.out.println("\tUse this option if you want to compare the result file to the entropies of the input files");
        System.out.println("\tInput file values are added and the entropies are computed on the sum.");
        System.out.println("\tThese values are then compared to the ones in the result file.");
        System.out.println("\tThe header line is ignored, time stamps are NOT compared!.");
        System.out.println("\n-----------------------------------------------------");
        System.out.println("\nFile format (input files, comma-separated): timeStamp, startTime, stopTime, histogramData[]");
        System.out.println("\nFile format (result files, comma-separated): timeStamp, startTime, stopTime, entropy");
        System.out.println("\n-----------------------------------------------------");
    }

    /**
     * Computes the Tsallis entropy for one given data list
     * 
     * @param exponent Exponent to apply
     * @param dataList Data to compute entropy from
     * 
     * @return Tsalllis entropy for given data with given exponent
     */
    public double computeEntropy(int exponent, long[] dataList) {
        if (exponent >= 0) {
            return computeEntropyPositive(exponent, dataList);
        } else {
            return computeEntropyNegative(exponent, dataList);
        }
    }

    /**
     * Computes the Tsallis entropy for one given data list (positive exponents
     * only). The reason for extra methods for positive and negative exponents
     * is that the positive computes the entropy exactly the way the secret 
     * sharing does and thus produces less rounding errors when comparing
     * the results.
     * 
     * @param exponent Exponent to apply
     * @param dataList Data to compute entropy from
     * 
     * @return Tsalllis entropy for given data with given exponent
     */
    public double computeEntropyPositive(int exponent, long[] dataList) {
        long totalCount;
        double entropy;
        double sum_of_x_i_to_exponents;
        double tsallisExponent;

        tsallisExponent = Double.valueOf(exponent);
//        printVector("itemSums: ", dataList); // for DEBUG purposes only

        // Compute total counts and the sum of all (x_i)^exponent
        totalCount = 0;
        sum_of_x_i_to_exponents = 0;
        double[] exponentiatedItemSums = new double[dataList.length];
        for (int metric = 0; metric < dataList.length; metric++) {
            totalCount += dataList[metric];
            exponentiatedItemSums[metric] = java.lang.Math.pow(dataList[metric], tsallisExponent);
            sum_of_x_i_to_exponents += java.lang.Math.pow(dataList[metric], tsallisExponent);
        }
        System.out.println("totalSum="+totalCount+", sumOfExponentiatedItemSums="+sum_of_x_i_to_exponents+", tsallisExponent="+exponent);

        // Compute entropy
        if (totalCount != 0) {
            entropy = Double.valueOf(1 / (java.lang.Math.pow(Double.valueOf(totalCount), tsallisExponent)));
            entropy *= sum_of_x_i_to_exponents;
            entropy = (1 / (tsallisExponent - 1)) * (1 - entropy);
        } else {
            // Cannot divide by 0
            entropy = 0;
        }

        return entropy;
    }

    /**
     * Computes the Tsallis entropy for one given data list (for negative 
     * only). The reason for extra methods for positive and negative exponents
     * is that the positive computes the entropy exactly the way the secret 
     * sharing does and thus produces less rounding errors when comparing
     * the results.
     * 
     * @param exponent Exponent to apply
     * @param dataList Data to compute entropy from
     * 
     * @return Tsalllis entropy for given data with given exponent
     */
    public double computeEntropyNegative(int exponent, long[] dataList) {
        long totalCount;
        double entropy;
        double tsallisExponent;
        double sum_of_pi;
        double intermediate;

        tsallisExponent = Double.valueOf(exponent);
        sum_of_pi = 0.0;

        // Compute total counts and the sum of all (x_i)^exponent
        totalCount = 0;

        for (int metric = 0; metric < dataList.length; metric++) {
            totalCount += dataList[metric];
        }

        if (totalCount != 0) {
            for (int metric = 0; metric < dataList.length; metric++) {
                if (dataList[metric] != 0) {
                    intermediate = Double.valueOf(Double.valueOf(dataList[metric]) / Double.valueOf(totalCount));
                    sum_of_pi += java.lang.Math.pow(intermediate, tsallisExponent);
                }
            }

            // Compute entropy
            entropy = (1 / (tsallisExponent - 1)) * (1 - sum_of_pi);
        } else {
            // Cannot divide by 0
            entropy = 0;
        }

        return entropy;
    }


    /**
     * prints a vector of numbers to the standard output
     *
     * @param title		text printed before vector
     * @param vector	vector of numbers to print
     */
    public static void printVector(String title, double[] vector) {
    	StringBuilder output = new StringBuilder();
        for(int i = 0; i < vector.length; i++) {
        	output = output.append(vector[i]).append(", ");
        }
        System.out.println(title+output.substring(0, output.length()-2));
    }
    public static void printVector(String title, long[] vector) {
    	StringBuilder output = new StringBuilder();
        for(int i = 0; i < vector.length; i++) {
        	output = output.append(vector[i]).append(", ");
        }
        System.out.println(title+output.substring(0, output.length()-2));
    }
}
