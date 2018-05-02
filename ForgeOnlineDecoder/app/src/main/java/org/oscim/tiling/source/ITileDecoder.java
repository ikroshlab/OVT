/*
 * Copyright 2013 Hannes Janetzek
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
package org.oscim.tiling.source;

import java.io.IOException;
import java.io.InputStream;

import org.oscim.core.Tile;
import org.oscim.tiling.source.ITileDataSink;

public interface ITileDecoder {

	// todo - added "preserveData" to make tile raw data accessible for later use (caching into DB)
	boolean decode(Tile tile, ITileDataSink sink, InputStream is, boolean preserveData)  throws IOException;

    // todo - added to get preserved tile raw data to store into DB cache
	byte[]  getRawTile();
}
