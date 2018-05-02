/*
 * Copyright 2014 Hannes Janetzek
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
package com.ikroshlab.vtmexample.test;

import com.ikroshlab.vtmexample.R;
import org.oscim.android.MapActivity;
import org.oscim.android.MapView;
import org.oscim.android.cache.TileCache;
import org.oscim.core.MapPosition;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.renderer.MapRenderer;
import org.oscim.tiling.TileSource;
import org.oscim.tiling.source.bitmap.DefaultSources;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class BitmapTileMapActivity extends MapActivity {

	private final static boolean   USE_CACHE     = true;

	private final TileSource       mTileSource;
	protected     BitmapTileLayer  mBitmapLayer;
    MapView                        mMapView;
    private       TileCache        mCache;



	public BitmapTileMapActivity() {
		mTileSource = DefaultSources.OPENSTREETMAP.build();
	}


	public BitmapTileMapActivity(TileSource tileSource) {
		mTileSource = tileSource;
	}



	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		mMapView = (MapView) findViewById(R.id.mapView);
		registerMapView(mMapView);


		// set cache DB
		if (USE_CACHE) {
			String DIR = Environment.getExternalStorageDirectory().getPath() + "/vtm/";

			File d = new File(DIR);
			if (!d.exists()) d.mkdir();
			logDebug("directory - " + d.getAbsolutePath() + " - is writable: " + d.canWrite());

			File f = new File(d, "tile.db");
			try {
				if (!f.exists()) f.createNewFile();
			} catch(IOException ioe) {
				ioe.printStackTrace();
			}

			mCache = new TileCache(this, null, f.getAbsolutePath());
			mCache.setCacheSize(512 * (1 << 10));
			mTileSource.setCache(mCache);
		}


		// add layers
        MapRenderer.setBackgroundColor(0xff777777);
		mBitmapLayer = new BitmapTileLayer(mMap, mTileSource);
		mMap.layers().add(mBitmapLayer);


		// set initial position
        mMap.setMapPosition(49.275498, -0.703742, Math.pow(2, 12));

        MapPosition p = new MapPosition();
        mMapView.map().getMapPosition(p);
        p.setTilt(30f);
        mMapView.map().animator().animateTo(1000L, p);
	}



	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (USE_CACHE)	mCache.dispose();
	}




	// handling Logging:
	private void logDebug(String msg) { Log.d("ikroshlab", " ### " + getClass().getName() + " ###  " + msg); }
}
