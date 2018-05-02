/*
 * Copyright 2012 Hannes Janetzek
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

import android.util.Log;

import static org.oscim.tiling.ITileDataSink.QueryResult.FAILED;
import static org.oscim.tiling.ITileDataSink.QueryResult.SUCCESS;
import static org.oscim.tiling.source.mapsforgeonline.DeCoder.decoder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import org.apache.log4j.lf5.util.StreamUtils;
import org.oscim.android.cache.TileCache;
import org.oscim.core.MapElement;
import org.oscim.core.PointF;
import org.oscim.core.Tag;
import org.oscim.core.Tile;
import org.oscim.layers.tile.MapTile;
import org.oscim.tiling.ITileCache;
import org.oscim.tiling.ITileCache.TileReader;
import org.oscim.tiling.ITileCache.TileWriter;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapsforgeonline.ForgeOnlineTileSource;
import org.oscim.tiling.source.oscimap4.OSciMap4TileSource;
import org.oscim.utils.IOUtils;



public class UrlTileDataSource implements ITileDataSource {

	protected final HttpEngine    mConn;
	protected final ITileDecoder  mTileDecoder;
	protected final UrlTileSource mTileSource;
	protected final boolean       mUseCache;


    // constructor
	public UrlTileDataSource(UrlTileSource tileSource, ITileDecoder tileDecoder, HttpEngine conn) {
		mTileDecoder = tileDecoder;
		mTileSource  = tileSource;
		mUseCache    = (tileSource.tileCache != null);
		mConn        = conn;
	}



	@Override
	public void query(MapTile tile, ITileDataSink sink) {

        logDebug("query - requested tile: " + tile);

		TileCache cache  = (TileCache)mTileSource.tileCache;
		boolean    ok    = false;

		// 1. check if the requested tile exists in DB:
		if (mUseCache) {

		    String provider = "";
            if (mTileSource instanceof ForgeOnlineTileSource)  provider = "mapsforge";
            else                                               provider = mTileSource.getClass().getSimpleName();

            ITileCache.TileReader cacheReader = cache.getTile(tile, true, provider);

            if (cacheReader.checkTile()) {

                logDebug("query - FOUND in cache - tile: " + tile + "  prov: " + provider);

                byte[] raw_tile = cacheReader.getData();
                if (raw_tile != null && raw_tile.length > 0) {

                    logDebug("query - parsing raw data: " + tile + "   size: " + raw_tile.length + "  prov: " + provider);

                    try {
                        ByteArrayInputStream byteStream = new ByteArrayInputStream(raw_tile);
                        byteStream.reset();

                        ok = mTileDecoder.decode(tile, sink, byteStream, false);
                        logDebug("query -  decoded successfully: " + ok + "  tile: " + tile + "  prov: " + provider);

                    } catch (IOException e) {
                        logDebug("query - Cache read failure: tile: " + tile + "  prov: " + provider + "  error:" + e.getMessage());
                    }

                    if (ok) {
                        sink.completed(SUCCESS);
                        return;
                    }
                }
                logDebug("query - NO raw data ! - skip; tile: " + tile + "  prov: " + provider);
            }
            logDebug("query - NOT found in cache - tile:" + tile + "  prov: " + provider);
		}



		// 2. so requested tile does NOT exist in DB, - request online tile provider:
		try {

			mConn.sendRequest(tile);
            logDebug("query - request sent for tile: " + tile + "  to url:" + mTileSource.getUrl());

            InputStream is = mConn.read();
            logDebug("query - tile has been read from Input Stream: " + tile);

            ok = mTileDecoder.decode(tile, sink, is, mUseCache);
            logDebug("query - decoded successfully: " + ok + "  tile: " + tile);

            // 3. store this tile in local DB:
            if (mUseCache && ok) {

                String provider = "";
                if (mTileSource instanceof ForgeOnlineTileSource)  provider = "mapsforge";
                else                                               provider = mTileSource.getClass().getSimpleName();

                byte[] raw_tile = mTileDecoder.getRawTile();

                logDebug("query - updating cache: " + tile + "  prov: " + provider);
                TileWriter cacheWriter = cache.writeTile(tile, raw_tile, provider);
                cacheWriter.complete(ok);
            }

		} catch (SocketException se) {
			logDebug("query - Socket Error:    tile:" + tile + "  prov: " + mTileSource.getClass().getSimpleName() + "  error:" + se.getMessage());
		} catch (SocketTimeoutException ste) {
			logDebug("query - Socket Timeout:  tile:" + tile + "  prov: " + mTileSource.getClass().getSimpleName() + "  error:" + ste.getMessage());
		} catch (UnknownHostException ue) {
			logDebug("query - Unknown host:    tile:" + tile + "  prov: " + mTileSource.getClass().getSimpleName() + "  error:" + ue.getMessage());
		} catch (IOException ie) {
			logDebug("query - Network Error:   tile:" + tile + "  prov: " + mTileSource.getClass().getSimpleName() + "  error:" + ie.getMessage());
		} finally {

			ok = mConn.requestCompleted(ok);

			sink.completed(ok ? SUCCESS : FAILED);
		}
	}



	public TileCache getTileCache() {
		return (TileCache)mTileSource.tileCache;
	}

	@Override
	public void dispose() {
		mConn.close();
	}

	@Override
	public void cancel() {
		mConn.close();
	}




    // handling Logging:
    private void logDebug(String msg) { Log.d("VTM", " ### " + getClass().getName() + " ###  " + msg); }
}
