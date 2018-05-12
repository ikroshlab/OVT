/*
 * Copyright 2014 Hannes Janetzek
 * Copyright 2016-2018 devemux86
 * Copyright 2017 Longri
 * Copyright 2018 Gustl22
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.ikroshlab.vtmexample.filepicker.FilePicker;
import com.ikroshlab.vtmexample.filepicker.FilterByFileExtension;
import com.ikroshlab.vtmexample.filepicker.ValidMapFile;
import com.ikroshlab.vtmexample.filepicker.ValidRenderTheme;

import org.oscim.android.MapView;
import org.oscim.android.cache.TileCache;
import org.oscim.backend.CanvasAdapter;
import org.oscim.core.MapElement;
import org.oscim.core.MapPosition;
import org.oscim.core.Tag;
import org.oscim.core.Tile;
import org.oscim.layers.TileGridLayer;
import org.oscim.layers.tile.MapTile;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.map.Map;
import org.oscim.renderer.BitmapRenderer;
import org.oscim.renderer.GLViewport;
import org.oscim.renderer.bucket.RenderBuckets;
import org.oscim.scalebar.DefaultMapScaleBar;
import org.oscim.scalebar.ImperialUnitAdapter;
import org.oscim.scalebar.MapScaleBar;
import org.oscim.scalebar.MapScaleBarLayer;
import org.oscim.scalebar.MetricUnitAdapter;
import org.oscim.theme.ExternalRenderTheme;
import org.oscim.theme.ThemeUtils;
import org.oscim.theme.VtmThemes;
import org.oscim.theme.styles.AreaStyle;
import org.oscim.theme.styles.RenderStyle;
import org.oscim.tiling.TileSource;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapfile.MapInfo;

import java.io.File;
import java.io.IOException;


public class MapsforgeMapActivity extends Activity {

    static final int SELECT_MAP_FILE   = 0;
    static final int SELECT_THEME_FILE = SELECT_MAP_FILE + 1;

    private static final Tag ISSEA_TAG = new Tag("natural", "issea");
    private static final Tag NOSEA_TAG = new Tag("natural", "nosea");
    private static final Tag SEA_TAG   = new Tag("natural", "sea");


    private MapView             mMapView;
    private Map                 mMap;
    private VectorTileLayer     mBaseLayer;

    private TileGridLayer       mGridLayer;
    private DefaultMapScaleBar  mMapScaleBar;
    private  Menu               mMenu;
    private boolean             mS3db;
    private VectorTileLayer     mTileLayer;
    MapFileTileSource           mTileSource;
    private   TileCache         mCache;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mMapView = (MapView) findViewById(R.id.mapView);
        mMap     = mMapView.map();

        startActivityForResult(new Intent(this, MapFilePicker.class),   SELECT_MAP_FILE);
    }


    @Override
    protected void onResume() {
        super.onResume();

        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        mMapView.onPause();

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mCache != null) mCache.dispose();

        if (mMapScaleBar != null)   mMapScaleBar.destroy();

        mMapView.onDestroy();
    }


    public static class MapFilePicker extends FilePicker {
        public MapFilePicker() {
            setFileDisplayFilter(new FilterByFileExtension(".map"));
            setFileSelectFilter(new ValidMapFile());
        }
    }

    public static class ThemeFilePicker extends FilePicker {
        public ThemeFilePicker() {
            setFileDisplayFilter(new FilterByFileExtension(".xml"));
            setFileSelectFilter(new ValidRenderTheme());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.theme_menu, menu);
        mMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.theme_default:
                mMap.setTheme(VtmThemes.DEFAULT);
                item.setChecked(true);
                return true;

            case R.id.theme_osmarender:
                mMap.setTheme(VtmThemes.OSMARENDER);
                item.setChecked(true);
                return true;

            case R.id.theme_tubes:
                mMap.setTheme(VtmThemes.TRONRENDER);
                item.setChecked(true);
                return true;

            case R.id.theme_newtron:
                mMap.setTheme(VtmThemes.NEWTRON);
                item.setChecked(true);
                return true;
             /*
            case R.id.theme_external:
                startActivityForResult(new Intent(this, ThemeFilePicker.class),
                        SELECT_THEME_FILE);
                return true;
            */
            case R.id.gridlayer:
                if (item.isChecked()) {
                    item.setChecked(false);
                    mMap.layers().remove(mGridLayer);
                } else {
                    item.setChecked(true);
                    if (mGridLayer == null)
                        mGridLayer = new TileGridLayer(mMap);

                    mMap.layers().add(mGridLayer);
                }
                mMap.updateMap(true);
                return true;
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        if (requestCode == SELECT_MAP_FILE) {
            if (resultCode != RESULT_OK || intent == null || intent.getStringExtra(FilePicker.SELECTED_FILE) == null) {
                finish();
                return;
            }

            mTileSource = new MapFileTileSource();
            //mTileSource.setPreferredLanguage("en");
            String file = intent.getStringExtra(FilePicker.SELECTED_FILE);
            if (mTileSource.setMapFile(file)) {

                mTileLayer = mMap.setBaseMap(mTileSource);
                loadTheme(null);


                mMap.layers().add(new BuildingLayer(mMap, mTileLayer));
                mMap.layers().add(new LabelLayer(mMap, mTileLayer));

                mMapScaleBar = new DefaultMapScaleBar(mMap);
                mMapScaleBar.setScaleBarMode(DefaultMapScaleBar.ScaleBarMode.BOTH);
                mMapScaleBar.setDistanceUnitAdapter(MetricUnitAdapter.INSTANCE);
                mMapScaleBar.setSecondaryDistanceUnitAdapter(ImperialUnitAdapter.INSTANCE);
                mMapScaleBar.setScaleBarPosition(MapScaleBar.ScaleBarPosition.BOTTOM_LEFT);

                MapScaleBarLayer mapScaleBarLayer = new MapScaleBarLayer(mMap, mMapScaleBar);
                BitmapRenderer renderer = mapScaleBarLayer.getRenderer();
                renderer.setPosition(GLViewport.Position.BOTTOM_LEFT);
                renderer.setOffset(5 * CanvasAdapter.getScale(), 0);
                mMap.layers().add(mapScaleBarLayer);

                MapInfo info = mTileSource.getMapInfo();
                if (!info.boundingBox.contains(mMap.getMapPosition().getGeoPoint())) {
                    MapPosition pos = new MapPosition();
                    pos.setByBoundingBox(info.boundingBox, Tile.SIZE * 4, Tile.SIZE * 4);
                    mMap.setMapPosition(pos);
                }
            }
        } else if (requestCode == SELECT_THEME_FILE) {
            if (resultCode != RESULT_OK || intent == null || intent.getStringExtra(FilePicker.SELECTED_FILE) == null) {
                return;
            }

            String file = intent.getStringExtra(FilePicker.SELECTED_FILE);
            ExternalRenderTheme externalRenderTheme = new ExternalRenderTheme(file);

            // Use tessellation with sea and land for Mapsforge themes
            if (ThemeUtils.isMapsforgeTheme(externalRenderTheme)) {
                mTileLayer.addHook(new VectorTileLayer.TileLoaderThemeHook() {
                    @Override
                    public boolean process(MapTile tile, RenderBuckets buckets, MapElement element, RenderStyle style, int level) {
                        if (element.tags.contains(ISSEA_TAG) || element.tags.contains(SEA_TAG) || element.tags.contains(NOSEA_TAG)) {
                            if (style instanceof AreaStyle)
                                ((AreaStyle) style).mesh = true;
                        }
                        return false;
                    }

                    @Override
                    public void complete(MapTile tile, boolean success) {
                    }
                });
            }

            mMap.setTheme(externalRenderTheme);
           // mMenu.findItem(R.id.theme_external).setChecked(true);
        }
    }

    protected void loadTheme(final String styleId) {
        mMap.setTheme(VtmThemes.DEFAULT);
    }


    // handling Logging:
    private void logDebug(String msg) { Log.d("ikroshlab", " ### " + getClass().getName() + " ###  " + msg); }

}