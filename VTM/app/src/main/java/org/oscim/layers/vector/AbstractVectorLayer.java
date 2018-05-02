package org.oscim.layers.vector;

import android.util.Log;

import org.oscim.core.BoundingBox;
import org.oscim.core.GeometryBuffer;
import org.oscim.core.MapPosition;
import org.oscim.core.Tile;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map;
import org.oscim.map.Map.UpdateListener;
import org.oscim.map.Viewport;
import org.oscim.renderer.BucketRenderer;
import org.oscim.renderer.GLViewport;
import org.oscim.renderer.bucket.RenderBuckets;
import org.oscim.utils.async.SimpleWorker;
import org.oscim.utils.geom.TileClipper;



public abstract class AbstractVectorLayer<T> extends Layer implements UpdateListener {

	protected final static double UNSCALE_COORD = 4;

	protected final GeometryBuffer mGeom = new GeometryBuffer(128, 4);
	protected final TileClipper mClipper = new TileClipper(-1024, -1024, 1024, 1024);

	protected final Worker mWorker;
	protected long mUpdateDelay = 100;

	protected boolean mUpdate = true;

	protected double mMinX;
	protected double mMinY;

	public AbstractVectorLayer(Map map) {
		super(map);
		mWorker = new Worker(mMap);
		mRenderer = new Renderer();
	}

	@Override
	public void onDetach() {
		super.onDetach();

		mWorker.cancel(true);
	}

	@Override
	public void onMapEvent(Event e, MapPosition pos) {
		if (mUpdate) {
			mUpdate = false;
			mWorker.submit(0);
		} else if (e == Map.POSITION_EVENT || e == Map.CLEAR_EVENT) {
			// throttle worker
			mWorker.submit(mUpdateDelay);
		}
	}

	public void update() {
		mWorker.submit(0);
	}

	abstract protected void processFeatures(Task t, BoundingBox b);

	protected static class Task {
		public final RenderBuckets buckets = new RenderBuckets();
		public final MapPosition position = new MapPosition();
	}

	protected class Worker extends SimpleWorker<Task> {

		public Worker(Map map) {
			super(map, 50, new Task(), new Task());
		}

		/** automatically in sync with worker thread */
		@Override
		public void cleanup(Task t) {
			if (t.buckets != null)
				t.buckets.clear();
		}

		/** running on worker thread */
		@Override
		public boolean doWork(Task t) {
			Viewport v = mMap.viewport();
			BoundingBox bbox;
			synchronized (v) {
				bbox = v.getBBox();
				v.getMapPosition(t.position);
			}

			double scale = t.position.scale * Tile.SIZE;

			t.position.x = (long) (t.position.x * scale) / scale;
			t.position.y = (long) (t.position.y * scale) / scale;
			processFeatures(t, bbox);

			mMap.render();
			return true;
		}

	}

	public class Renderer extends BucketRenderer {
		MapPosition mTmpPos = new MapPosition();

		@Override
		public void update(GLViewport v) {

			Task t = mWorker.poll();

			if (t == null)
				return;

			mMapPosition.copy(t.position);
			mMapPosition.setScale(mMapPosition.scale / UNSCALE_COORD);

			buckets.setFrom(t.buckets);

			compile();
			//logDebug("is ready " + isReady() + " " + layers.getSize());
		}
	}


    // handling Logging:
    private void logDebug(String msg) { Log.d("VTM", " ### " + getClass().getName() + " ###  " + msg); }
}
