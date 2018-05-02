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
package org.oscim.tiling;

import java.io.InputStream;
import java.io.OutputStream;

import org.oscim.core.Tile;

/*
 * The interface CacheManager defines the operation for the cache.
 */
public interface ITileCache {


	/**
	 * @param size  The size for the cache directory.
	 */
	void setCacheSize(long size);



	public interface TileReader {
		Tile        getTile();
		InputStream getInputStream();
		byte[]      getData();
		boolean     checkTile();
	}

    /**
     * @param tile  The accessed tile.
     * @return      The stored file for this tile or null if tile is not stored.
     */
    TileReader getTile(Tile tile, String provider);



	public interface TileWriter {
		Tile         getTile();
		OutputStream getOutputStream();

		void complete(boolean success);
	}


    /**
     * @param tile  The accessed tile.
     * @return      The CacheFile which contains the Fileoutputstream for the cache.
     */
    TileWriter writeTile(Tile tile, String provider);

	TileWriter writeTile(Tile tile, byte[] raw, String provider);
}
