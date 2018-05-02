package org.oscim.tiling.source.bitmap;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.lf5.util.StreamUtils;
import org.oscim.backend.CanvasAdapter;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.core.Tile;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.source.ITileDecoder;
import org.oscim.tiling.source.UrlTileDataSource;
import org.oscim.tiling.source.UrlTileSource;



public class BitmapTileSource extends UrlTileSource {

	public static class Builder<T extends Builder<T>> extends UrlTileSource.Builder<T> {

		public Builder() {
			super(null, "/{Z}/{X}/{Y}.png", 0, 17);
		}

		public BitmapTileSource build() {
			return new BitmapTileSource(this);
		}
	}

	protected BitmapTileSource(Builder<?> builder) {
		super(builder);
	}

	@SuppressWarnings("rawtypes")
	public static Builder<?> builder() {
		return new Builder();
	}

	/**
	 * Create BitmapTileSource for 'url'
	 * 
	 * By default path will be formatted as: url/z/x/y.png
	 * Use e.g. setExtension(".jpg") to overide ending or
	 * implement getUrlString() for custom formatting.
	 */
	public BitmapTileSource(String url, int zoomMin, int zoomMax) {
		this(url, "/{Z}/{X}/{Y}.png", zoomMin, zoomMax);
	}

	public BitmapTileSource(String url, int zoomMin, int zoomMax, String extension) {
		this(url, "/{Z}/{X}/{Y}" + extension, zoomMin, zoomMax);
	}

	public BitmapTileSource(String url, String tilePath, int zoomMin, int zoomMax) {
		super(builder()
		     .url(url)
		     .tilePath(tilePath)
		     .zoomMin(zoomMin)
		     .zoomMax(zoomMax));
	}


	@Override
	public ITileDataSource getDataSource() {
		return new UrlTileDataSource(this, new BitmapTileDecoder(), getHttpEngine());
	}




	public class BitmapTileDecoder implements ITileDecoder {

	    private byte[]  raw_tile;

		@Override
		public  byte[]  getRawTile() {
			return raw_tile == null ? null : raw_tile.clone();
		}



		@Override
		public boolean decode(Tile tile, ITileDataSink sink, InputStream is, boolean preserveData)  throws IOException {

            Bitmap bitmap;

            if (preserveData) {
                ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
                BufferedOutputStream  out        = new BufferedOutputStream(dataStream, 8192);
                StreamUtils.copy(is, out);

                raw_tile = dataStream.toByteArray();
                out.close();

                ByteArrayInputStream byteStream  = new ByteArrayInputStream(raw_tile);
                byteStream.reset();

                bitmap = CanvasAdapter.decodeBitmap(byteStream);
            }
            else {
                bitmap = CanvasAdapter.decodeBitmap(is);
                raw_tile = null;
            }

            if (!bitmap.isValid()) {
                logDebug("invalid bitmap: " + tile);
                return false;
            }

			sink.setTileImage(bitmap);

			return true;
		}
	}



    // handling Logging:
    private void logDebug(String msg) { Log.d("VTM", " ### " + getClass().getName() + " ###  " + msg); }
}
