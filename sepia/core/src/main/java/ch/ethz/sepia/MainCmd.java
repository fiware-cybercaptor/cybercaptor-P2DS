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

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.startup.Configuration;
import ch.ethz.sepia.startup.PeerStarter;

/**
 * The main class to start the secret traffic measurements sharing from a
 * command line window.
 * 
 * The Syntax for starting SEPIA input and privacy peers from command line is as
 * follows:
 * 
 * <p/>
 * java -jar sepia.jar [-options]
 * <p/>
 * 
 * The following options are available:
 * <ul>
 * <li>v: Enable verbose logging mode. This creates quite big log files.</li>
 * <li>help: Show usage information.</li>
 * <li>p peerType: Specifies the peer type (0: input peer: 1: privacy peer).</li>
 * <li>c configFile: Path to the configuration file.</li>
 * <li>t network: Perform a basic connection test. (0: standard connection, 1:
 * SSL connection).</li>
 * </ul>
 * Thus, an input peer with verbose logging can be started by the following
 * command:
 * <p/>
 * java -jar sepia.jar -v -p 0 -c MyConfig.properties
 * 
 * @author Lisa Barisic
 */
public class MainCmd {

    private static final String ARG_BE_VERBOSE = "-v";
    private static final String ARG_CONFIG_FILE = "-c";
    private static final String ARG_HELP = "-help";
    private static final String ARG_PEER_TYPE = "-p";
    private static final int INPUT_PEER_TYPE = 0;
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(MainCmd.class));

    private static final int PRIVACY_PEER_TYPE = 1;

    /**
     * The main class to start a peer or a privacy peer
     * 
     * @param args
     *            arguments
     */
    public static void main(final String[] args) {
        String configFile = null;
        String argument = null;
        int peerType = 11111;
        boolean peerTypeDone = false;
        final boolean configFileDone = false;
        boolean beVerbose = false;
        boolean argumentsOK = true;
        int argIndex;
        final String peerName = "default";

        logger.info(Services.getApplicationName() + ": "
                + Services.getApplicationDescription());

        logger.info("Arguments:" + String.join(" ", args));

        if (args.length > 0) {
            argIndex = 0;
            while (argIndex < args.length) {
                argument = args[argIndex++];

                if (argument != null) {
                    if (argument.length() > 0 && argument.charAt(0) == '-') {
                        if (argument.equals(ARG_PEER_TYPE)) {
                            try {
                                if (!peerTypeDone) {
                                    if (argIndex < args.length) {
                                        argument = args[argIndex++];
                                        peerType = Integer.parseInt(argument);
                                        peerTypeDone = true;
                                    } else {
                                        logger.error("Argument missing (peer type)");
                                        argumentsOK = false;
                                    }
                                } else {
                                    logger.error("Peer type option already set: "
                                            + argument);
                                    argumentsOK = false;
                                }
                            } catch (final NumberFormatException e) {
                                logger.error("Unexpected peer type: "
                                        + argument);
                                argumentsOK = false;
                            }

                        } else if (argument.equals(ARG_CONFIG_FILE)) {
                            if (!configFileDone) {
                                if (argIndex < args.length) {
                                    argument = args[argIndex++];
                                    configFile = argument;
                                } else {
                                    logger.error("Argument missing (config file)");
                                    argumentsOK = false;
                                }
                            } else {
                                argumentsOK = false;
                            }
                        } else if (argument.equals(ARG_BE_VERBOSE)) {
                            beVerbose = true;
                        } else if (argument.equals(ARG_HELP)) {
                            printUsage();
                            System.exit(0);
                        } else {
                            logger.error("Config file option already set: "
                                    + argument);
                            argumentsOK = false;
                        }
                    }
                }
            }
        } else {
            logger.error("No arguments found!");
            argumentsOK = false;
        }

        if (!peerTypeDone) {
            argumentsOK = false;
        }

        if (argumentsOK) {
            if (configFile == null) {
                logger.info("No config file given... Setting empty properties");
            } else {
                logger.info("Config file: " + configFile);
                Configuration.getInstance(peerName).initialize(configFile);
            }

            // Set log filter (if any)
            if (beVerbose) {
                logger.info("Be verbose when logging");
            } else {
                logger.info("Logging in non-verbose mode (no effect)");
                // logger.setFilter(new LogFilterNonVerbose());
            }

            // Start the peers
            boolean isInputPeer;
            if (peerType == INPUT_PEER_TYPE) {
                logger.info("Starting INPUT PEER");
                isInputPeer = true;
            } else if (peerType == PRIVACY_PEER_TYPE) {
                logger.info("Starting PRIVACY PEER");
                isInputPeer = false;
            } else {
                logger.error("Unexpected peer Type: " + peerType);
                printUsage();
                return;
            }

            final Thread t = new Thread(new PeerStarter(peerName,
                    new Stopper(), isInputPeer, new Stopper()));
            t.start();

            boolean hasStopped = false;
            while (!hasStopped) {
                try {
                    t.join();
                    hasStopped = true;
                } catch (final InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else {
            printUsage();
        }
    }

    /**
     * Let the user know how to handle this program
     */
    private static void printUsage() {
        System.err.println("\n\nUsage: java -jar sepia.jar [-options]");
        System.err.println("\nOptions:");
        System.err.println(ARG_BE_VERBOSE
                + "\t\tBe verbose (Creates large log files!)");
        System.err.println(ARG_HELP + "\t\tShow usage information");
        System.err
                .println(ARG_PEER_TYPE
                        + " <peerType>\tWhat kind of peer (0 = input peer, 1 = privacy peer)");
        System.err.println(ARG_CONFIG_FILE
                + " <configFile>\tSpecifies the config file.");
        System.err.println("\nExample:");
        System.err.println("\tjava -jar sepia.jar " + ARG_BE_VERBOSE + " "
                + ARG_PEER_TYPE + " 0 " + ARG_CONFIG_FILE
                + " c:\\my.config.properties");

        System.exit(-1);
    }
}
