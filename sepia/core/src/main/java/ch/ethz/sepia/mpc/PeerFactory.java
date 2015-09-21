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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.startup.Configuration;

/**
 * A factory that returns input and/or privacy peers.
 * 
 * @author Lisa Barisic, ETH Zurich
 */
public class PeerFactory {
    /**
     * Returns a new input/privacy peer.
     * 
     * @param peerName
     *            the name of the peer
     * @param isInputPeer
     *            true if this is an input peer
     * @param peerNumber
     *            Peer's number. Usage depends on implementation
     * @param cm
     *            the connection manager This is needed in case the framework
     *            {@link #CUSTOM_FRAMEWORK} is used and class names need to be
     *            read from the config files.
     * 
     * @return A MPC instance of the wanted type
     */
    public synchronized static PeerBase getPeerInstance(final String peerName,
            final boolean isInputPeer, final int peerNumber,
            final ConnectionManager cm, final Stopper stopper) throws Exception {
        final Properties props = Configuration.getInstance(peerName)
                .getProperties();
        if (isInputPeer) {
            return getPeerInstanceByName(
                    peerName,
                    props.getProperty(Configuration.PROP_MPC_CUSTOM_PEER_CLASS),
                    peerNumber, cm, stopper);
        } else {
            return getPeerInstanceByName(
                    peerName,
                    props.getProperty(Configuration.PROP_MPC_CUSTOM_PRIVACYPEER_CLASS),
                    peerNumber, cm, stopper);
        }
    }

    /**
     * Use reflection to instantiate custom class.
     * 
     * @param peerName
     *            the name of the peer
     * @param className
     *            the name of the class
     * @param cm
     *            the connection manager
     * @param peerNumber
     *            the peer number to be handed over to the constructor
     * @param stopper
     *            the stopper to be handed over to the constructor
     * @return the MpcPeer instance
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws SecurityException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws IllegalArgumentException
     */
    private static PeerBase getPeerInstanceByName(final String peerName,
            final String className, final int peerNumber,
            final ConnectionManager cm, final Stopper stopper)
            throws ClassNotFoundException, SecurityException,
            NoSuchMethodException, IllegalArgumentException,
            InstantiationException, IllegalAccessException,
            InvocationTargetException {
        final Class<?> c = Class.forName(className);
        final Constructor<?> con = c.getConstructor(new Class[] { String.class,
                int.class, ConnectionManager.class, Stopper.class });
        final PeerBase mpcInstance = (PeerBase) con.newInstance(new Object[] {
                peerName, peerNumber, cm, stopper });
        return mpcInstance;
    }
}
