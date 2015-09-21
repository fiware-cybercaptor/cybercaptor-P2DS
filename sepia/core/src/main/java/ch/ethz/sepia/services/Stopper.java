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

import java.util.Observable;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

/**
 * Stopper class that can be used to stop running threads. The thread is given a
 * reference to the stopper and should check every once in a while if
 * computation should be stopped.
 *
 * @author Lisa Barisic, ETH Zurich
 */
public class Stopper extends Observable {
    /** The logger. */
    private static final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(Stopper.class));
    private Exception exception;
    private boolean isStopped;
    private String message;

    /**
     * Creates a new stopper (with stopped = false).
     */
    public Stopper() {
        this.isStopped = false;
        this.exception = null;
        this.message = null;
    }

    public Exception getException() {
        return exception;
    }

    public String getMessage() {
        return message;
    }

    public boolean hasException() {
        return exception != null;
    }

    public boolean isStopped() {
        return this.isStopped;
    }

    public void stop() {
        LOGGER.info("NotifyObservers: " + this.countObservers());
        this.isStopped = true;
        setChanged();
        notifyObservers();
    }

    public void stop(final Exception exception, final String message) {
        this.exception = exception;
        this.message = message;
        stop();
    }
}
