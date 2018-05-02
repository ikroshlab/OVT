package com.ikroshlab.vtmexample.test;


import com.ikroshlab.vtmexample.R;

import static org.oscim.utils.ColorUtil.modHsv;
import static org.oscim.utils.ColorUtil.shiftHue;

import org.oscim.android.MapActivity;
import org.oscim.android.MapView;
import org.oscim.android.cache.TileCache;
import org.oscim.core.MapPosition;
import org.oscim.layers.TileGridLayer;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.buildings.S3DBLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.map.Layers;
import org.oscim.renderer.MapRenderer;
import org.oscim.theme.RenderTheme;
import org.oscim.theme.VtmThemes;
import org.oscim.theme.rule.Rule;
import org.oscim.theme.rule.Rule.RuleVisitor;
import org.oscim.theme.styles.AreaStyle;
import org.oscim.theme.styles.AreaStyle.AreaBuilder;
import org.oscim.theme.styles.LineStyle;
import org.oscim.theme.styles.LineStyle.LineBuilder;
import org.oscim.theme.styles.RenderStyle;
import org.oscim.tiling.TileSource;
import org.oscim.tiling.source.oscimap4.OSciMap4TileSource;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ToggleButton;

import java.io.File;
import java.io.IOException;


public class ThemeStylerActivity extends MapActivity implements OnSeekBarChangeListener {

    final static boolean           USE_CACHE     = true;

    protected MapView              mMapView;
    protected VectorTileLayer      mBaseLayer;
    protected TileSource           mTileSource;
    protected TileGridLayer        mGridLayer;
    private   TileCache            mCache;


    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map_styler);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		((SeekBar) findViewById(R.id.seekBarH)).setOnSeekBarChangeListener(this);
		((SeekBar) findViewById(R.id.seekBarS)).setOnSeekBarChangeListener(this);
		((SeekBar) findViewById(R.id.seekBarV)).setOnSeekBarChangeListener(this);
        ((SeekBar) findViewById(R.id.seekBarTilt)).setOnSeekBarChangeListener(this);


        mMapView = (MapView) findViewById(R.id.mapView);
        registerMapView(mMapView);


        // set map tile source
        mTileSource = new OSciMap4TileSource();
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

        TileSource ts = OSciMap4TileSource.builder().url("http://opensciencemap.org/tiles/s3db").zoomMin(16).zoomMax(17).build();

		Layers layers = mMap.layers();
		layers.add(new BuildingLayer(mMap, mBaseLayer));
        layers.add(new S3DBLayer(mMap, ts, true, false));
		layers.add(new LabelLayer(mMap, mBaseLayer));


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

        if (mCache != null) mCache.dispose();
    }




    /////////////////////////////  styler  /////////////////////////////////////////////////////////
    public static class HSV {
        public double   hue = 0;
        public double   sat = 1;
        public double   val = 1;
        public boolean  changed;
    }

    HSV lineColor    = new HSV();
    HSV outlineColor = new HSV();
    HSV areaColor    = new HSV();


	class ModStyleVisitor extends RuleVisitor {

		private final LineBuilder<?> lineBuilder = LineStyle.builder();
		private final AreaBuilder<?> areaBuilder = AreaStyle.builder();


		@Override
		public void apply(Rule r) {

			for (RenderStyle style : r.styles) {

				if (style instanceof LineStyle) {
					LineStyle s = (LineStyle) style;
					HSV       c = lineColor;

					if (lineColor.changed && s.outline)  continue;

					if (outlineColor.changed) {
						if (!s.outline)                  continue;
						c = outlineColor;
					}

					s.set(lineBuilder.set(s)
                                     .color(modColor(s.color, c))
                                     .stippleColor(modColor(s.stippleColor, c))
                                     .build());
					continue;
				}

				if (areaColor.changed && style instanceof AreaStyle) {
					AreaStyle s = (AreaStyle) style;

					s.set(areaBuilder.set(s)
                                     .color(modColor(s.color, areaColor))
                                     .blendColor(modColor(s.blendColor, areaColor))
                                     .strokeColor(modColor(s.strokeColor, areaColor))
                                     .build());
				}
			}
			super.apply(r);
		}
	}

	int modColor(int color, HSV hsv) {
		return modHsv(shiftHue(color, hsv.hue), 1, hsv.sat, hsv.val, true);
	}

    ModStyleVisitor mStyleVisitor = new ModStyleVisitor();





	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

		if (!fromUser) return;
		int id = seekBar.getId();

		boolean modLine = ((RadioButton) findViewById(R.id.checkBoxLine)).isChecked();
		boolean modArea = ((RadioButton) findViewById(R.id.checkBoxArea)).isChecked();

		HSV c;
		if      (modArea)  	c = areaColor;
		else if (modLine) 	c = lineColor;
		else     			c = outlineColor;

		if      (id == R.id.seekBarS) 	  c.sat = progress / 50f;
		else if (id == R.id.seekBarV)	  c.val = progress / 50f;
		else if (id == R.id.seekBarH)	  c.hue = progress / 100f;
        else if (id == R.id.seekBarTilt) {
            MapPosition p = new MapPosition();
            mMapView.map().getMapPosition(p);
            p.setTilt(progress * 0.6f);
            mMapView.map().animator().animateTo(500L, p);
            return;
        }

		logDebug((modArea ? "area" : "line")
		        + " h:" + c.hue
		        + " s:" + c.sat
		        + " v:" + c.val);

		VectorTileLayer l = (VectorTileLayer) mMap.layers().get(1);
		RenderTheme     t = (RenderTheme)     l.getTheme();

		c.changed = true;
		t.traverseRules(mStyleVisitor);
		t.updateStyles();
		c.changed = false;

		if (modArea) MapRenderer.setBackgroundColor(modColor(t.getMapBackground(), c));

		mMap.render();
	}


	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	public void onToggleControls(View view) {
		findViewById(R.id.controls).setVisibility(((ToggleButton) view).isChecked() ? View.VISIBLE : View.GONE);
	}

	public void onRadioButtonClicked(View view) {
		boolean checked = ((RadioButton) view).isChecked();
		if (!checked) return;

		HSV c = null;
		switch (view.getId()) {
			case R.id.checkBoxArea:
				c = areaColor;
				break;
			case R.id.checkBoxLine:
				c = lineColor;
				break;
			case R.id.checkBoxOutline:
				c = outlineColor;
				break;
		}
		if (c == null) return;

		((SeekBar) findViewById(R.id.seekBarS)).setProgress((int) (c.sat * 50));
		((SeekBar) findViewById(R.id.seekBarV)).setProgress((int) (c.val * 50));
		((SeekBar) findViewById(R.id.seekBarH)).setProgress((int) (c.hue * 100));
	}

	////////////////////////////////////////////////////////////////////////////////////////////////





    // handling Logging:
    private void logDebug(String msg) { Log.d("ikroshlab", " ### " + getClass().getName() + " ###  " + msg); }
}
