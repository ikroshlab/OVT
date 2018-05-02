package org.oscim.layers.tile;

import static org.oscim.backend.GLAdapter.gl;
import static org.oscim.layers.tile.MapTile.PROXY_GRAMPA;
import static org.oscim.layers.tile.MapTile.PROXY_PARENT;
import static org.oscim.layers.tile.MapTile.State.READY;
import static org.oscim.renderer.MapRenderer.COORD_SCALE;
import static org.oscim.renderer.bucket.RenderBucket.BITMAP;
import static org.oscim.renderer.bucket.RenderBucket.HAIRLINE;
import static org.oscim.renderer.bucket.RenderBucket.LINE;
import static org.oscim.renderer.bucket.RenderBucket.MESH;
import static org.oscim.renderer.bucket.RenderBucket.POLYGON;
import static org.oscim.renderer.bucket.RenderBucket.TEXLINE;

import org.oscim.backend.GL;
import org.oscim.backend.canvas.Color;
import org.oscim.core.MapPosition;
import org.oscim.core.Tile;
import org.oscim.renderer.GLMatrix;
import org.oscim.renderer.GLViewport;
import org.oscim.renderer.MapRenderer;
import org.oscim.renderer.bucket.BitmapBucket;
import org.oscim.renderer.bucket.HairLineBucket;
import org.oscim.renderer.bucket.LineBucket;
import org.oscim.renderer.bucket.LineTexBucket;
import org.oscim.renderer.bucket.MeshBucket;
import org.oscim.renderer.bucket.PolygonBucket;
import org.oscim.renderer.bucket.RenderBucket;
import org.oscim.renderer.bucket.RenderBuckets;
import org.oscim.utils.FastMath;



public class VectorTileRenderer extends TileRenderer {

	static final boolean debugOverdraw = false;

	protected int      mClipMode;

	protected GLMatrix mClipProj = new GLMatrix();
	protected GLMatrix mClipMVP  = new GLMatrix();

	/**
	 * Current number of frames drawn, used to not draw a
	 * tile twice per frame.
	 */
	protected int mDrawSerial;

	@Override
	public synchronized void render(GLViewport v) {

		/* discard depth projection from tilt, depth buffer
		 * is used for clipping */
		mClipProj.copy(v.proj);
		mClipProj.setValue(10, 0);
		mClipProj.setValue(14, 0);
		mClipProj.multiplyRhs(v.view);

		mClipMode = PolygonBucket.CLIP_STENCIL;

		int tileCnt = mDrawTiles.cnt + mProxyTileCnt;

		MapTile[] tiles = mDrawTiles.tiles;

		boolean drawProxies = false;

		mDrawSerial++;

		for (int i = 0; i < tileCnt; i++) {
			MapTile t = tiles[i];

			if (t.isVisible && !t.state(READY)) {
				gl.depthMask(true);
				gl.clear(GL.DEPTH_BUFFER_BIT);

				/* always write depth for non-proxy tiles
				 * this is used in drawProxies pass to not
				 * draw where tiles were already drawn */
				gl.depthFunc(GL.ALWAYS);

				mClipMode = PolygonBucket.CLIP_DEPTH;
				drawProxies = true;

				break;
			}
		}

		/* draw visible tiles */
		for (int i = 0; i < tileCnt; i++) {
			MapTile t = tiles[i];
			if (t.isVisible && t.state(READY)) 	drawTile(t, v, 0);
		}

		/* draw parent or children as proxy for visibile tiles that dont
		 * have data yet. Proxies are clipped to the region where nothing
		 * was drawn to depth buffer.
		 * TODO draw proxies for placeholder */
		if (!drawProxies)
			return;

		/* only draw where no other tile is drawn */
		gl.depthFunc(GL.LESS);

		/* draw child or parent proxies */
		boolean preferParent = (v.pos.getZoomScale() < 1.5)
		        || (v.pos.zoomLevel < tiles[0].zoomLevel);

		if (preferParent) {
			for (int i = 0; i < tileCnt; i++) {
				MapTile t = tiles[i];
				if ((!t.isVisible) || (t.lastDraw == mDrawSerial))
					continue;
				if (!drawParent(t, v))
					drawChildren(t, v);
			}
		} else {
			for (int i = 0; i < tileCnt; i++) {
				MapTile t = tiles[i];
				if ((!t.isVisible) || (t.lastDraw == mDrawSerial))
					continue;
				drawChildren(t, v);
			}
			for (int i = 0; i < tileCnt; i++) {
				MapTile t = tiles[i];
				if ((!t.isVisible) || (t.lastDraw == mDrawSerial))
					continue;
				drawParent(t, v);
			}
		}

		/* draw grandparents */
		for (int i = 0; i < tileCnt; i++) {
			MapTile t = tiles[i];
			if ((!t.isVisible) || (t.lastDraw == mDrawSerial))
				continue;
			drawGrandParent(t, v);
		}

		gl.depthMask(false);

		/* make sure stencil buffer write is disabled */
		//GL.stencilMask(0x00);
	}

	private void drawTile(MapTile tile, GLViewport v, int proxyLevel) {

		/* ensure to draw parents only once */
		if (tile.lastDraw == mDrawSerial)
			return;

		tile.lastDraw = mDrawSerial;

		/* use holder proxy when it is set */
		RenderBuckets buckets = (tile.holder == null)
		        ? tile.getBuckets()
		        : tile.holder.getBuckets();

		if (buckets == null || buckets.vbo == null) {
			//log.debug("{} no buckets!", tile);
			return;
		}

		MapPosition pos = v.pos;
		/* place tile relative to map position */
		int z = tile.zoomLevel;
		float div = FastMath.pow(z - pos.zoomLevel);
		double tileScale = Tile.SIZE * pos.scale;
		float x = (float) ((tile.x - pos.x) * tileScale);
		float y = (float) ((tile.y - pos.y) * tileScale);

		/* scale relative to zoom-level of this tile */
		float scale = (float) (pos.scale / (1 << z));

		v.mvp.setTransScale(x, y, scale / COORD_SCALE);
		v.mvp.multiplyLhs(v.viewproj);

		mClipMVP.setTransScale(x, y, scale / COORD_SCALE);
		mClipMVP.multiplyLhs(mClipProj);

		buckets.bind();

		PolygonBucket.Renderer.clip(mClipMVP, mClipMode);
		boolean first = true;

		for (RenderBucket b = buckets.get(); b != null;) {
			switch (b.type) {
				case POLYGON:
					b = PolygonBucket.Renderer.draw(b, v, div, first);
					first = false;
					/* set test for clip to tile region */
					gl.stencilFunc(GL.EQUAL, 0x80, 0x80);
					break;
				case LINE:
					b = LineBucket.Renderer.draw(b, v, scale, buckets);
					break;
				case TEXLINE:
					b = LineTexBucket.Renderer.draw(b, v, div, buckets);
					break;
				case MESH:
					b = MeshBucket.Renderer.draw(b, v);
					break;
				case HAIRLINE:
					b = HairLineBucket.Renderer.draw(b, v);
					break;
				case BITMAP:
					b = BitmapBucket.Renderer.draw(b, v, 1, mLayerAlpha);
					break;
				default:
					/* just in case */
					//log.error("unknown layer {}", b.type);
					b = b.next;
					break;
			}

			/* make sure buffers are bound again */
			buckets.bind();
		}

		if (debugOverdraw) {
			if (tile.zoomLevel > pos.zoomLevel)
				PolygonBucket.Renderer.drawOver(mClipMVP, Color.BLUE, 0.5f);
			else if (tile.zoomLevel < pos.zoomLevel)
				PolygonBucket.Renderer.drawOver(mClipMVP, Color.RED, 0.5f);
			else
				PolygonBucket.Renderer.drawOver(mClipMVP, Color.GREEN, 0.5f);

			return;
		}

		long fadeTime = tile.fadeTime;
		if (fadeTime == 0) {
			if (tile.holder == null) {
				fadeTime = getMinFade(tile, proxyLevel);
			} else {
				/* need to use time from original tile */
				fadeTime = tile.holder.fadeTime;
				if (fadeTime == 0)
					fadeTime = getMinFade(tile.holder, proxyLevel);
			}
			tile.fadeTime = fadeTime;
		}

		long dTime = MapRenderer.frametime - fadeTime;

		if (mOverdrawColor == 0 || dTime > FADE_TIME) {
			PolygonBucket.Renderer.drawOver(mClipMVP, 0, 1);
			return;
		}

		float fade = 1 - dTime / FADE_TIME;
		PolygonBucket.Renderer.drawOver(mClipMVP, mOverdrawColor, fade * fade);

		MapRenderer.animate();
	}

	protected boolean drawChildren(MapTile t, GLViewport v) {
		int drawn = 0;
		for (int i = 0; i < 4; i++) {
			MapTile c = t.getProxyChild(i, READY);
			if (c == null)
				continue;

			drawTile(c, v, 1);
			drawn++;
		}
		if (drawn == 4) {
			t.lastDraw = mDrawSerial;
			return true;
		}
		return false;
	}

	protected boolean drawParent(MapTile t, GLViewport v) {
		MapTile proxy = t.getProxy(PROXY_PARENT, READY);
		if (proxy != null) {
			drawTile(proxy, v, -1);
			t.lastDraw = mDrawSerial;
			return true;
		}
		return false;
	}

	protected void drawGrandParent(MapTile t, GLViewport v) {
		MapTile proxy = t.getProxy(PROXY_GRAMPA, READY);
		if (proxy != null) {
			drawTile(proxy, v, -2);
			t.lastDraw = mDrawSerial;
		}
	}
}
