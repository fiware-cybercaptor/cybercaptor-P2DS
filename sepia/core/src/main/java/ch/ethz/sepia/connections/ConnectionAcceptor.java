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

package ch.ethz.sepia.connections;

import java.io.IOException;
import java.net.Socket;
import java.util.Observable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

/**
 * The connection acceptor is a thread that runs on privacy peer hosts and constantly accepts incoming
 * connections. This class implements {@link Observable} and notifies its observers with new socket connections.
 * @author martibur
 *
 */
public class ConnectionAcceptor extends Observable implements Runnable {

	private static final XLogger logger = new XLogger(LoggerFactory.getLogger(ConnectionAcceptor.class));
	private final int listeningPort;
	private SSLServerSocket serverSocket;
	private final SSLContext sslContext;
	private boolean stopped = false;

	/**
	 * Thread for accepting connections.
	 * @param listeningPort the port for incoming connections
	 * @param sslContext the SSLContext, properly initialized for client authentication
	 */
	public ConnectionAcceptor(final int listeningPort, final SSLContext sslContext) {
		this.listeningPort = listeningPort;
		this.sslContext = sslContext;
	}

	/**
	 * Starts listening for connections on the given port.
	 */
	public void run() {
		try {
			final SSLServerSocketFactory socketFactory  = sslContext.getServerSocketFactory();
			serverSocket = (SSLServerSocket)socketFactory.createServerSocket(listeningPort);
			serverSocket.setEnabledCipherSuites(serverSocket.getSupportedCipherSuites());
			serverSocket.setNeedClientAuth(true);

		} catch (final IOException e) {
			logger.error("Could not open server socket. Details: " + e.getMessage());
			return;
		}

		while(!stopped) {
			try {
				final Socket socket = serverSocket.accept();
				setChanged();
				notifyObservers(socket);
			} catch (final IOException e) {
				if(!stopped) {
					logger.error("Problem during serverSocket.accept(). Details: "+e.getMessage());
				}
			}
		}

	}

	/**
	 * Closes the open server socket and finishes the thread.
	 */
	public void stopAccepting() {
		stopped = true;
		try {
			if (serverSocket!=null) {
				serverSocket.close();
			}
		} catch (final IOException e) {
			// ignore
		}
	}

}
