// Copyright 2015 Zürcher Hochschule der Angewandten Wissenschaften
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
/**
 * @author Stephan Neuhaus
 */
package ch.ethz.sepia.connections;

import java.io.IOException;

public interface Connection {

    public void close() throws IOException;

    public boolean isConnected();

    public byte[] read() throws IOException, InterruptedException;

    public void write(byte[] bytes) throws IOException;
}
