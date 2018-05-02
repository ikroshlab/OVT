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
package org.oscim.layers.tile;

import android.util.Log;

import static org.oscim.tiling.ITileDataSink.QueryResult.FAILED;
import static org.oscim.tiling.ITileDataSink.QueryResult.SUCCESS;

import org.oscim.android.cache.TileCache;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.core.MapElement;
import org.oscim.renderer.bucket.RenderBuckets;
import org.oscim.tiling.ITileCache;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.source.mapfile.MapDatabase;
import org.oscim.utils.PausableThread;
import org.oscim.utils.pool.Inlist;


public abstract class TileLoader extends PausableThread implements ITileDataSink {

	private static int id;

	private final String      THREAD_NAME;
	private final TileManager mTileManager;
	protected MapTile         mTile;               // currently processed tile

    protected boolean         mUseCache;
    protected TileCache       mCache;


	public TileLoader(TileManager tileManager) {
		super();
		mTileManager = tileManager;
		THREAD_NAME  = "TileLoader" + (id++);
	}

	protected abstract boolean loadTile(MapTile tile);

	public void go() {
		synchronized (this) {
			notify();
		}
	}

	@Override
	protected void doWork() {
		mTile = mTileManager.getTileJob();

		if (mTile == null)	return;

		try {
			loadTile(mTile);
		} catch (Exception e) {
			e.printStackTrace();
			completed(FAILED);
		}
	}

	@Override
	protected String getThreadName() {
		return THREAD_NAME;
	}

	@Override
	protected int getThreadPriority() {
		return (Thread.NORM_PRIORITY + Thread.MIN_PRIORITY) / 2;
	}

	@Override
	protected boolean hasWork() {
		return mTileManager.hasTileJobs();
	}

	public abstract void dispose();

	public abstract void cancel();


	/**
	 * Callback to be called by TileDataSource when finished loading or on failure.
	 * MUST BE CALLED IN ANY CASE!
	 */
	@Override
	public void completed(QueryResult result) {
		boolean ok = (result == SUCCESS);

		if (ok && (isCanceled() || isInterrupted())) ok = false;

        logDebug(">>>>>> completed: " + mTile  + "  loaded:" + ok);


		mTileManager.jobCompleted(mTile, ok);
		mTile = null;
	}

	/**
	 * Called by TileDataSource
	 */
	@Override
	public void process(MapElement element) {

	}

	/**
	 * Called by TileDataSource
	 */
	@Override
	public void setTileImage(Bitmap bitmap) {

	}


    // handling Logging:
    private void logDebug(String msg) { Log.d("VTM", " ### " + getClass().getName() + " ###  " + msg); }
}
