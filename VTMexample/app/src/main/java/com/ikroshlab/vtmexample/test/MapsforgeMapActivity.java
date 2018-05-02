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


import org.oscim.android.MapActivity;
import org.oscim.android.MapView;

import com.ikroshlab.vtmexample.R;
import com.ikroshlab.vtmexample.filepicker.FilePicker;
import com.ikroshlab.vtmexample.filepicker.FilterByFileExtension;
import com.ikroshlab.vtmexample.filepicker.ValidMapFile;

import org.oscim.android.cache.TileCache;
import org.oscim.core.MapPosition;
import org.oscim.core.Tile;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.theme.VtmThemes;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapfile.MapInfo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;


public class MapsforgeMapActivity extends MapActivity {


	private static final int         SELECT_MAP_FILE = 0;
    final static boolean             USE_CACHE       = true;

	private MapView                  mMapView;
	private MapFileTileSource        mTileSource;
    private VectorTileLayer          mBaseLayer;
    private TileCache                mCache;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


		mMapView = (MapView) findViewById(R.id.mapView);
		registerMapView(mMapView);

		startActivityForResult(new Intent(this, MapFilePicker.class), SELECT_MAP_FILE);
	}

	public static class MapFilePicker extends FilePicker {
		public MapFilePicker() {
			setFileDisplayFilter(new FilterByFileExtension(".map"));
			setFileSelectFilter(new ValidMapFile());
		}
	}


	// build map from selected file
	private void buildMap(String file) {


		// set map tile source
        mTileSource = new MapFileTileSource();

        if (mTileSource.setMapFile(file)) {
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


            // set initial position
            MapInfo info = mTileSource.getMapInfo();
            if (info.boundingBox != null) {
                MapPosition pos = new MapPosition();
                pos.setByBoundingBox(info.boundingBox, Tile.SIZE * 4,Tile.SIZE * 4);
                mMap.setMapPosition(pos);
                logDebug("set position " + pos);
            }
            else if (info.mapCenter != null) {

                double scale = 1 << 8;
                if (info.startZoomLevel != null)  scale = 1 << info.startZoomLevel.intValue();

                mMap.setMapPosition(info.mapCenter.getLatitude(), info.mapCenter.getLongitude(), scale);
            }
            MapPosition p = new MapPosition();
            mMapView.map().getMapPosition(p);
            p.setTilt(60f);
            mMapView.map().animator().animateTo(1000L, p);
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mCache != null)  mCache.dispose();
    }


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

		if (requestCode == SELECT_MAP_FILE) {
			if (resultCode != RESULT_OK || intent == null)				 return;
			if (intent.getStringExtra(FilePicker.SELECTED_FILE) == null) return;

            String file = intent.getStringExtra(FilePicker.SELECTED_FILE);
			buildMap(file);
		}
	}



    // handling Logging:
    private void logDebug(String msg) { Log.d("ikroshlab", " ### " + getClass().getName() + " ###  " + msg); }
}
