/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.utils;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;



/**
 * A utility class with IO-specific helper methods.
 */
public final class IOUtils {

	/**
	 * Invokes the {@link Closeable#close()} method on the given object. If an
	 * {@link IOException} occurs during the
	 * method call, it will be caught and logged.
	 * 
	 * @param closeable  the data source which should be closed (may be null).
	 */
	public static void closeQuietly(Closeable closeable) {
		if (closeable == null) return;

		try {
			closeable.close();
		} catch (IOException e) {
			logDebug(e.getMessage());
		}
	}

	/* for old java versions */
	public static void closeQuietly(Socket closeable) {
		if (closeable == null) return;

		try {
			closeable.close();
		} catch (IOException e) {
			logDebug(e.getMessage());
		}
	}

	private IOUtils() {
	}


    // handling Logging:
    private static void logDebug(String msg) { Log.d("VTM", " ### " + "IOUtils" + " ###  " + msg); }
}
