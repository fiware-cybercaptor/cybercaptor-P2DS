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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.InetAddress;

import ch.ethz.sepia.services.BloomFilter;
import ch.ethz.sepia.services.MultiHash;

public class HashEvaluation {

	public static final String TESTDATA = "TestData";

	/**
	 * Percentile values for 1% significance
	 * (with 99% probability the null hypothesis is accepted)
	 * Degrees of freedom V are powers of 2 from 2^1-1 to 20^30-1 for chi^2 distribution
	 * computed by Matlab R2009b chi2inv(0.99, V);
	 */
	public static final double [] CHI2_099P = {
		6.63489660102121,	11.3448667301444,	18.4753069065824,
		30.5779141668925,	52.1913948331920,	92.0100236141319,
		166.987390136674,	310.457388219906,	588.297794145237,
		1131.15873896349,	2198.78449726431,	4308.46786557997,
		8491.69234285249,	16807.0400581458,	33365.4756113377,
		66380.1615899862,	132265.026024922,	263830.393270819,
		526672.118812236,	1051946.85095196,	2101918.30037238,
		4201043.76347670,	8398138.66148679,	16790693.5871081,
		33573491.3827130,	67135817.2336620,	134255844.824664,
		268489360.526415,	536947143.708316,	1073849631.11175
	};

	/**
	 * Percentile values for 5% significance
	 * (with 95% probability the null hypothesis is accepted)
	 * Degrees of freedom V are powers of 2 from 2^1-1 to 20^30-1 for chi^2 distribution
	 * computed by Matlab R2009b chi2inv(0.95, V);
	 */
	public static final double [] CHI2_095P = {
		3.84145882069413,	7.81472790325118,	14.0671404493402,
		24.9957901397286,	44.9853432803651,	82.5287265414717,
		154.301516165350,	293.247835080701,	564.696133086912,
		1098.52078192459,	2153.36963141515,	4244.98530792545,
		8402.65929539664,	16681.8739101654,	33189.2100625595,
		66131.6309385434,	131914.297339483,	263335.134918260,
		525972.464769963,	1050958.13784178,	2100520.79607052,
		4199068.14117150,	8395345.45689176,	16786744.1465724,
		33567906.7776444,	67127920.1567451,	134244677.418705,
		268473568.176776,	536924810.700603,	1073818048.21668
	};

	/**
	 * Percentile values for 50% significance
	 * (with 50% probability the null hypothesis is accepted)
	 * Degrees of freedom V are powers of 2 from 2^1-1 to 20^30-1 for chi^2 distribution
	 * computed by Matlab R2009b chi2inv(0.50, V);
	 */
	public static final double [] CHI2_050P ={
		0.454936423119573,	2.36597388437534,	6.34581119552152,
		14.3388595109567,	30.3359424581981,	62.3346020723063,
		126.333959059615,	254.333644073511,	510.333488177335,
		1022.33341062439,	2046.33337194620,	4094.33335263161,
		8190.33334298043,	16382.3333381564,	32766.3333357447,
		65534.3333345390,	131070.333333936,	262142.333333635,
		524286.333333484,	1048574.33333341,	2097150.33333337,
		4194302.33333335,	8388606.33333334,	16777214.3333333,
		33554430.3333333,	67108862.3333333,	134217726.333333,
		268435454.333333,	536870910.333333,	1073741822.33333
	};

	/**
	 * Converts an integer to a byte array
	 * @param val value to convert
	 * @return [0]..[3] MSB to LSB representation of val
	 */
    public static byte[] IntToByteArr(int val) {
        byte[] buffer = new byte[4];

        // >>> is unsigned  and >> signed bitshift
        buffer[0] = (byte) (val >>> 24);
        buffer[1] = (byte) (val >>> 16);
        buffer[2] = (byte) (val >>> 8);
        buffer[3] = (byte) val;

        return buffer;
    }

	public static BufferedWriter getWriter(String filename) throws Exception{
		FileWriter fw = new FileWriter(filename);
		BufferedWriter bw = new BufferedWriter(fw);
		return bw;
	}

	public static BufferedReader getReader(String filename) throws Exception{
		FileReader fr = new FileReader(filename);
		BufferedReader br = new BufferedReader(fr);
		return br;
	}

	/**
	 * Benchmark function for MultiHash class.
	 * Sweeps over the specified range, produces maxHashes*log2(maxHashrange) resultFiles
	 * Measures time in nanoseconds to compute hashes and writes them to the TIMINGBENCHMARKFILE
	 * <br />
	 * <pre>
	 * file layout: x-axis range beginning with 2 to maxRange  (column n: range = 2^n)
	 * 	y-axis number of hash values computed 1 to maxHashes (row m: m hash values)
	 * </pre>
	 * ATTENTION! A different number of items is hashed for every row, so only timings of
	 * the individual rows of the 4 different files can be compared!
	 * <br /><br />
	 * Performs a chi-square test for uniform distribution on the generated hashvalues with a
	 * significance value of 5%. If the generated distribution is found not to be uniform
	 * a notification is written to STATISTICS_CHECK file together with the corresponding
	 * histogram for further evaluation.
	 * <br /><br />
	 * Inputs must be generated in advance and at least 5*maxRange values are needed.
	 * <br />
	 * @param maxHashes tests with 1... maxHashes number of Hashfunctions
	 * @param maxRange Hashfunctions with Range minRange .. maxRange are used,
	 * 						the range is doubled in each iteration. Iteration stops on largest
	 * 						power of 2 that is <= maxRange.
	 * @throws Exception
	 */
	public static void sweepMultiHash(int minHash, int maxHash, int minRange, int maxRange) throws Exception{
		//final String TIMINGBENCHMARKFILE = "HashTimings";
		final String STATISTICS_CHECK = "mh_eval_"+minHash+"-"+maxHash+"h_"+minRange+"-"+maxRange+"r.txt";

		// ensure min and maxRange are powers of 2
		int bits = (int)Math.ceil(Math.log((double)minRange)/Math.log(2.0));
		minRange = (int)Math.pow(2, bits);
		bits = (int)Math.ceil(Math.log((double)maxRange)/Math.log(2.0));
		maxRange = (int)Math.pow(2, bits);

//		BufferedWriter md5timingfile = getWriter(TIMINGBENCHMARKFILE+"MD5fast.txt");
//		BufferedWriter sha1timingfile = getWriter(TIMINGBENCHMARKFILE+"SHA1fast.txt");
//		BufferedWriter md5slowtimingfile = getWriter(TIMINGBENCHMARKFILE+"MD5slow.txt");
//		BufferedWriter sha1slowtimingfile = getWriter(TIMINGBENCHMARKFILE+"SHA1slow.txt");

		BufferedWriter badStatistics = getWriter(STATISTICS_CHECK);

		for(int hc = minHash; hc <= maxHash; hc ++){
			// progress counter
			System.out.println("Progress: "+ (hc-minHash) + "/"+ (maxHash-minHash));
			// expected frequency for histogram positions
			double expected = 5*hc; //(double)amount*(double)hc/(double)range;

			for(int range = minRange; range <= maxRange; range *=2){
				MultiHash md5 = new MultiHash(hc, range, "HmacMD5");
				MultiHash sha1 = new MultiHash(hc, range, "HmacSHA1");

				// Timing file layout  colums = ranges  rows = number of hashes

				// progress
				System.out.println("Range: " + range + ", Hashcount: " + hc);
				//long startTime, sha1time = 0, md5time = 0, sha1slowtime = 0, md5slowtime = 0;

				// histograms for statistical evaluation
				int [] sha1fast = new int[range];
				int [] sha1slow = new int[range];
				int [] md5fast = new int[range];
				int [] md5slow = new int[range];


				BufferedReader br =  getReader("SweepData.txt");
				String val;
				int counter = 0;
				//read file to the end
				while(counter < 5*range ){
					counter++;
					val = br.readLine();

					// hash all values and measure times
					// fast methods

					//startTime = System.nanoTime();
					int [] rsfast = sha1.hash(val);
					//sha1time += System.nanoTime() - startTime;

					//startTime = System.nanoTime();
					int [] rmfast = md5.hash(val);
					//md5time += System.nanoTime() - startTime;

					// slow methods
					//startTime = System.nanoTime();
					int [] rsslow = sha1.slowHash(val);
					//sha1slowtime += System.nanoTime() - startTime;

					//startTime = System.nanoTime();
					int [] rmslow = md5.slowHash(val);
					//md5slowtime += System.nanoTime() - startTime;


					// update histograms
					for(int k=0; k < hc; k++){
						sha1fast[rsfast[k]] += 1;
						sha1slow[rsslow[k]] += 1;
						md5fast[rmfast[k]] += 1;
						md5slow[rmslow[k]] += 1;
						//average is already known: #keys*#hashes / range
					}

				} // end while: parse input files

				// write timings to file
//				sha1timingfile.write(new Long(sha1time).toString() + ";");
//				md5timingfile.write(new Long(md5time).toString() + ";");
//				md5slowtimingfile.write(new Long(md5slowtime).toString() + ";");
//				sha1slowtimingfile.write(new Long(sha1slowtime).toString() + ";");

				// examine histograms for strange things
				// chi square test for uniform distribution
				if(!chiSquareUniform(sha1fast, expected)){
					badStatistics.write("SHA1_FAST fail! Range: " + range + ", HashCount: " + hc
											+ ", Expected: " + expected);
					badStatistics.newLine();
					//badStatistics.write(arrToString(sha1fast));
					badStatistics.newLine();
				}
				if(!chiSquareUniform(sha1slow, expected)){
					badStatistics.write("SHA1_SLOW fail! Range: " + range + ", HashCount: " + hc
											+ ", Expected: " + expected);
					badStatistics.newLine();
					//badStatistics.write(arrToString(sha1slow));
					badStatistics.newLine();
				}
				if(!chiSquareUniform(md5fast, expected)){
					badStatistics.write("MD5_FAST fail! Range: " + range + ", HashCount: " + hc
										+ ", Expected: " + expected);
					badStatistics.newLine();
					//badStatistics.write(arrToString(md5fast));
					badStatistics.newLine();
				}
				if(!chiSquareUniform(md5slow, expected)){
					badStatistics.write("MD5_SLOW fail! Range: " + range + ", HashCount: " + hc
										+ ", Expected: " + expected);
					badStatistics.newLine();
					//badStatistics.write(arrToString(md5slow));
					badStatistics.newLine();
				}
				badStatistics.flush();
			}// end for:  range

//			md5timingfile.newLine();
//			sha1timingfile.newLine();
//			md5slowtimingfile.newLine();
//			sha1slowtimingfile.newLine();
//			md5timingfile.flush();
//			sha1timingfile.flush();
//			md5slowtimingfile.flush();
//			sha1slowtimingfile.flush();
		}// end for: hashCount

		// close files
//		md5timingfile.close();
//		sha1timingfile.close();
//		md5slowtimingfile.close();
//		sha1slowtimingfile.close();
		badStatistics.close();
	}

	/**
	 * Performs the chi-square test on a histogram
	 * assumes uniform distribution as null hypothesis
	 * @param histo Histogram to analyze
	 * @return true if the Histogram is uniform distribution
	 */
	public static boolean chiSquareUniform(int [] histo, double expected){
		// since null hypothesis is a uniform distribution
		// the expected value for each index in the histogram is
		// #keys*#hashes / range

		// compute chi^2
		double chi2 = 0;
		for(int i = 0; i < histo.length; i++){
			chi2 += ((double)histo[i]-expected)*((double)histo[i]-expected)/
												expected;
		}
		// if chi^2 is greater than comparison value
		int index = (int)(Math.log(histo.length)/Math.log(2))-1;
		if(chi2 <= CHI2_095P[index]){
			// with xx% probability the value is uniform
			return true;
		}else{
			// not uniformly distributed
			return false;
		}
	}

	public static String arrToString(int [] arr){
		String s = "";
		for(int i = 0; i < arr.length; i++){
			s += arr[i] + ";";
		}
		return s;
	}


	/**
	 * Outputfile "SweepCardinalityLog.txt" contains cardinality estimates at
	 * <br />
	 * rows: hashCount, columns: relative fill ratio 0 - 100% in 1% steps there are 202 columns
	 * <br />
	 * Layout is estimae;actualvalue;estimat2;actualvalue2; ...
	 * <br />
	 * Only non counting cardinality is of interest, since counting will be correct unless we have a
	 * counter overflow which would need at least 2^31 elements but we only have 1 mio.
	 * <br />
	 * <br />
	 * Tests with bloomfilter fpr 0.0001  range 16777216,  500k elements
	 * @param minHash Number of hash values to begin with
	 * @param maxHash maximum Number of hash values
	 */
	public static void sweepCardinality(int minHash, int maxHash) throws Exception {
		// fixed range: 2^24 = 16777216
		final int BFDIM = 16777216;
		final int ITEMS = 1000000;

		generateNamedFiles("cardinalityData.txt", ITEMS);
		BufferedWriter bwnc = getWriter("CardinalityNC"+minHash+"-"+maxHash+"hash.txt");
//		BufferedWriter bwc = getWriter("CardinalityC.txt");
		for(int i = minHash; i <= maxHash; i++){
			//progress
			System.out.println("HashCount: "+i);

//			BloomFilter bfc = new BloomFilter(i, 33554432, true);
			BloomFilter bfnc = new BloomFilter(i, BFDIM, false);

			BufferedReader br = getReader("cardinalityData.txt");
			//read file to the end
			String val;
			int previous = -1;
			int current = 0;
			int count = 0;
			while(null != (val = br.readLine())){
				current = (int)Math.floor(100*bfnc.getNonZeros()/(double)BFDIM);
				if(current > 100)
					break;
				if(current > previous){ // generate output
					previous = current;
					int cardNC = (int)BloomFilter.getCardinality(bfnc.getNonZeros(),
						bfnc.getRange(), bfnc.getHashCount(), false);
//					double cardC = BloomFilter.getCardinality(bfc.getNonZeros(),
//							bfc.getRange(), bfc.getHashCount(), true);

					// estimate vs atual value
					bwnc.write(cardNC+";"+count+";");
					bwnc.flush();
//					bwc.write(new Double(cardC)+";");
				}
				//				bfc.insert(val);
				bfnc.insert(val);
				count++;
			}// end while (eof)
			br.close();

			// if 100% fillratio was not reached fill the remaining with NaN
			for(int k = current+1; k <=100; k++){
				bwnc.write("NaN;NaN;");
				bwnc.flush();
			}
			bwnc.newLine();
//			bwc.newLine();
		}// end for: hashes
		bwnc.close();
//		bwc.close();
	}

	/**
	 * Outputfile contains number of false positives at <br />
	 * col = #players, rows = %overlap 0.0 to 1.0 steps 0.2
	 * <br />
	 * BloomFilter dimensioning: Items 100k, fpr 0.001 ->
	 * Range 2097152, Hashes = 30, fpr = 2.7E-4
	 *
	 * @param players maximum number of players to simulate
	 */
	public static void cardinalityIntersection(int players) throws Exception{

		final int BFDIM = 2097152;
		final int HASH = 30;
		final int ITEMS = 100000;

		BufferedWriter bw = getWriter("IntersectionFalsePositives.txt");
		BufferedWriter bwcardi = getWriter("IntersectionCardinality.txt");
		// test intersection for 0, 0.2, 0.4, 0.6, 0.8,1  overlap

		BloomFilter intersection = new BloomFilter(HASH, BFDIM, false);
		int pc = 0; //progress counter
		for(double overlap = 0; overlap <= 0.21; overlap += 0.05){
			generateTestFiles(players, ITEMS, overlap);
			generateNamedFiles("intersectiontester.txt", (int)(players*ITEMS*(1-overlap)+ITEMS*overlap));
			// prepare initial filter
			BufferedReader br = getReader(TESTDATA+0+".txt");
			String val;
			while(null != (val = br.readLine())){
				intersection.insert(val);
			}
			br.close();
			// do intersection
			for(int i = 1; i< players; i++){
				//progress
				pc++;
				System.out.println("Progress "+(100*(double)pc/((players-1)*6))+"%");

				BloomFilter current = new BloomFilter(HASH, BFDIM, false);
				br = getReader(TESTDATA+i+".txt");
				while(null != (val = br.readLine())){
					current.insert(val);
				}
				br.close();

				long tbA = current.getNonZeros();
				long tbB = intersection.getNonZeros();
				intersection = intersection.intersect(current, false);

				// check for false positives
				br = getReader("intersectiontester.txt");
				int falsePositives = 0;
				int count = 0;
				while(null != (val = br.readLine())){
					if(count >= ITEMS*overlap){
						if(intersection.check(val)){
							falsePositives++;
						}
					}
					count++;
				}
				br.close();
				bw.write(falsePositives+";");
				bw.flush(); // write buffer to disk

				int cardinality = (int)BloomFilter.getCardinality(intersection.getNonZeros(),
						intersection.getRange(), intersection.getHashCount(), intersection.isCounting());



				int correctedCard = (int)BloomFilter.getIntersectionCardinality(tbA, tbB, intersection.getNonZeros(),
						intersection.getHashCount(), intersection.getRange(), i+1, intersection.isCounting());
				bwcardi.write(cardinality+";"+correctedCard+";"+(overlap*ITEMS)+";");
				bwcardi.flush();
			}
			bwcardi.newLine();
			bw.newLine();
		}
		bwcardi.close();
		bw.close();
	}

	/**
	 * Outputfile contains number of false negatives at <br />
	 *  rows = #players, cols = %overlap 0.0 to 1.0 steps 0.2
	 * <br />
	 * BloomFilter dimensioning: Items in union 100*100k, fpr 0.1 ->
	 * Range 67108864, Hashes = 5
	 *
	 * @param players maximum number of players to simulate
	 */
	public static void cardinalityUnion(int players) throws Exception{
		final int BFDIM = 67108864;
		final int HASH = 5;
		final int ITEMS = 100000;
		final boolean ISCOUNTING = true;

		BufferedWriter bw = getWriter("cardUnion.txt");
		// test intersection for 0, 0.2, 0.4, 0.6, 0.8,1  overlap
		// test for 2 to 100 players
		for(int p = 2; p <= players; p++){
			//progress
			System.out.println("Simulating union for "+p+" players");
			BloomFilter union = new BloomFilter(HASH, BFDIM, ISCOUNTING);
			for(double overlap = 0; overlap <= 1; overlap +=0.2){
				generateTestFiles(p, ITEMS, overlap);
				// prepare initial filter
				BufferedReader br = getReader(TESTDATA+0+".txt");
				String val;
				while(null != (val = br.readLine())){
					union.insert(val);
				}
				br.close();
				// do intersection
				for(int i = 1; i < p; i++){
					BloomFilter current = new BloomFilter(HASH, BFDIM, ISCOUNTING);
					br = getReader(TESTDATA+i+".txt");
					while(null != (val = br.readLine())){
						current.insert(val);
					}
					br.close();
					union = union.join(current, ISCOUNTING);
				}

				// check for false positives
				generateNamedFiles("uniontester.txt", (int)(p*(ITEMS*(1-overlap))+ITEMS*overlap));
				br = getReader("uniontester.txt");
				int falseNegatives = 0;
				int count = 0;
				while(null != (val = br.readLine())){
					count = p;
					// we test if we can remove each element as many times as we inserted it
					while(union.remove(val)){
						count--;
					}
					if(count < 0){
						falseNegatives++;
					}
				}
				br.close();
				bw.write(falseNegatives+";");
				bw.flush();
			}// end for: overlap
			bw.newLine();
		}// end for: players
		bw.close();

	}

	/**
	 * Generates testfiles with name TESTDATA#.txt
	 * @param numberOfFiles to generate
	 * @param size number distinct values in each file
	 * @param overlap percentage of values that all files will have in common
	 */
	public static void generateTestFiles(int numberOfFiles, int size, double overlap) throws Exception{
		BufferedWriter [] bw = new BufferedWriter[numberOfFiles];
		int shared = (int)(size*overlap);
		String [] values = new String[shared];

		// generate mutual values
		for(int i = 0; i < shared; i++){
			values[i] = InetAddress.getByAddress(IntToByteArr(i)).getHostAddress();
		}

		for(int i = 0; i < numberOfFiles; i++){
			bw[i] = getWriter(TESTDATA+i+".txt");
			// write shared values
			for(String v : values){
				bw[i].write(v);
				bw[i].newLine();
			}
			// write distinct values
			int diff = size - shared;
			String val;
			for(int k=shared+i*diff; k<shared+(i+1)*diff; k++){
				val = InetAddress.getByAddress(IntToByteArr(k)).getHostAddress();
				bw[i].write(val);
				bw[i].newLine();
			}
			bw[i].close();
		}
	}


	public static void generateNamedFiles(String filename, int size) throws Exception{
		BufferedWriter bw = getWriter(filename);
		for(int i=0; i<size ; i++){
			String val = InetAddress.getByAddress(IntToByteArr(i)).getHostAddress();
			bw.write(val);
			bw.newLine();
		}
		bw.close();
	}



	public static void main(String [] args) throws Exception{
		int [] argnums = new int[4];
		// take arguments from command line only if they match
		// parse numbers
		for(int i = 0; i < args.length; i++){
			argnums[i] = Integer.valueOf(args[i]);
		}
		if(args.length == 4){
			sweepMultiHash(argnums[0], argnums[1], argnums[2], argnums[3]);
		}else if(args.length == 1){
			argnums[0] = Integer.valueOf(args[0]);
			// for chi2 test to be reliable we need an expected frequency of
			// each histogram entry of at least 5 (schaum's outline series Statistics first edition)
			int amount = (int)5*argnums[0];
			generateNamedFiles("SweepData.txt", amount);
		}else if(args.length == 3){
			generateTestFiles(argnums[0], argnums[1], (double)argnums[2]/100.0);
		}else{
			printUsage();
			//generateTestFiles(25, 100000, 0.1);
			// range 2^30 NOT possible 2*2^30 = 2^31 = negative number
			// BUT MAX_INT = 2^31 - 1 !!!!!!!!!!
			//generateTestFiles(9, 500, 0.2);

			// 2^27 is max value for which we don't need more than
			// 2GB heap for the array int[2^27]
			//int two_pow_27 = 134217728;
			//int two_pow_16 = 65536;
			//sweepMultiHash(21, 21, 131072, 131072);

			System.out.println(BloomFilter.getFalsePositiveRate(100000, 1048576, 10));

			//sweepCardinality(1,150);

			// don't test more than 50 intersections, convergence of uncorrected is at about 42
			//cardinalityIntersection(50);
			//cardinalityUnion(3);
			//		double [] e = BloomFilter.getParameterEstimate(100000, 0.01);
			//		System.out.println(e[0]+", "+e[1]+", fpr:"+e[2]);
		}
		System.out.println("finished!");
	}

	public static void printUsage(){
		System.out.println("Multihash test: 4 arguments");
		System.out.println("minHash, maxHash, minRange, maxRange\n");
		System.out.println("Sweep input data generation: 1 argument");
		System.out.println("number of values >= 5* maxRange\n");
		System.out.println("Testfile generation for intersection: 3 arguments");
		System.out.println("numberOfFiles, ValuesPerFile, overlap*100");
	}

}
