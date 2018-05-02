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
package com.ikroshlab.vtmexample.test;


import com.ikroshlab.vtmexample.R;

import org.oscim.android.MapActivity;
import org.oscim.android.MapView;
import org.oscim.android.cache.TileCache;
import org.oscim.core.MapPosition;
import org.oscim.layers.TileGridLayer;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.renderer.MapRenderer;
import org.oscim.theme.VtmThemes;
import org.oscim.tiling.TileSource;
import org.oscim.tiling.source.mapsforgeonline.ForgeOnlineTileSource;

import android.os.Bundle;
import android.app.ActionBar;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;

public class ForgeOnlineMapActivity extends MapActivity {

	final static boolean           USE_CACHE    = true;

	protected MapView              mMapView;
	protected VectorTileLayer      mBaseLayer;
	protected TileSource           mTileSource;
	private   TileCache            mCache;




	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mMapView = (MapView) findViewById(R.id.mapView);
		registerMapView(mMapView);


		// set map tile source
		mTileSource = new ForgeOnlineTileSource();
        logDebug("tile source: " + mTileSource.toString());


        // set cache DB
		if (USE_CACHE) {

			String DIR = Environment.getExternalStorageDirectory().getPath() + "/vtm";

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


        // set layers
		mBaseLayer = mMap.setBaseMap(mTileSource);
		mMap.setTheme(VtmThemes.DEFAULT);

        mMap.layers().add(new BuildingLayer(mMap, mBaseLayer));
        mMap.layers().add(new LabelLayer(mMap, mBaseLayer));
        mMap.layers().add(new TileGridLayer(mMap));


		// set initial position
		mMap.setMapPosition(49.275498, -0.703742, Math.pow(2, 8));

        MapPosition p = new MapPosition();
        mMapView.map().getMapPosition(p);
        p.setTilt(30f);
        mMapView.map().animator().animateTo(1000L, p);
	}



	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (mCache != null)  mCache.dispose();
	}



	// handling Logging:
	private void logDebug(String msg) { Log.d("ikroshlab", " ### " + getClass().getName() + " ###  " + msg); }
}
