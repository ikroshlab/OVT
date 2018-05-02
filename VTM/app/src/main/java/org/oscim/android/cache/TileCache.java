/*
 * Copyright 2013 Hannes Janetzek
 * Copyright 2018 ikroshlab.com
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
package org.oscim.android.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.oscim.core.Tile;
import org.oscim.layers.tile.MapTile;
import org.oscim.tiling.ITileCache;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.ParcelFileDescriptor;
import android.util.Log;



public class TileCache implements ITileCache {
	
	final static boolean          dbg = true;

	private final ArrayList<ByteArrayOutputStream> mCacheBuffers;
	private final SQLiteHelper    dbHelper;
	private final SQLiteDatabase  mDatabase;
	private final SQLiteStatement mStmtGetTile;
	private final SQLiteStatement mStmtPutTile;
	private final SQLiteStatement mStmtUpdateTile;

    static final String TABLE_NAME = "tiles";

	static final String COL_PROV   = "provider";
    static final String COL_TIME   = "time";
    static final String COL_ACCESS = "last_access";
    static final String COL_DATA   = "data";
    static final String COL_SIZE   = "size";



    // constructor
	public TileCache(Context context, String cacheDirectory, String dbName) {
		if (dbg) {
            File d = new File(dbName);
		    logDebug("TileCache - directory: " + d.getAbsolutePath() + " - is writable: " + d.canWrite());
        }

        dbHelper = new SQLiteHelper(context, dbName);

		dbHelper.setWriteAheadLoggingEnabled(true);

		mDatabase = dbHelper.getWritableDatabase();

		String s = "SELECT " + COL_DATA + " FROM " + TABLE_NAME + " WHERE provider=? AND x=? AND y=? AND z =?";
		mStmtGetTile = mDatabase.compileStatement(s);

		s = "INSERT INTO " + TABLE_NAME + " (provider,x, y, z, time, last_access, data, size)" + " VALUES(?,?,?,?,?,?,?,?)";
		mStmtPutTile = mDatabase.compileStatement(s);

		s = "UPDATE " + TABLE_NAME + "  SET time=?, last_access=?, data=?, size=? " + "  WHERE provider=? AND x=? AND y=? AND z=?";
		mStmtUpdateTile = mDatabase.compileStatement(s);

		mCacheBuffers = new ArrayList<ByteArrayOutputStream>();
	}




	class CacheTileReader implements TileReader {
		final InputStream mInputStream;
		final Tile        mTile;
		final String      mTileProvider;
		final byte[]      mData;

		public CacheTileReader(Tile tile, InputStream is, String provider) {
			mTile         = tile;
            mTileProvider = provider;
			mInputStream  = is;
			mData         = null;
		}

        public CacheTileReader(Tile tile, byte[] raw, String provider) {
            mTile         = tile;
            mTileProvider = provider;
            mInputStream  = null;
            mData         = raw;
        }

		@Override
		public Tile getTile() {
			return mTile;
		}

		@Override
		public InputStream getInputStream() {
			return mInputStream;
		}

		@Override
        public byte[] getData() {
			return mData;
		}

		@Override
		public boolean checkTile() {
		    return exists(mTile, mTileProvider);
        }
	}


	class CacheTileWriter implements TileWriter {
		final ByteArrayOutputStream mOutputStream;
		final Tile                  mTile;
        final String                mTileProvider;
        final byte[]                raw;



        CacheTileWriter(Tile tile, ByteArrayOutputStream os, String provider) {
			mTile         = tile;
            mTileProvider = provider;
			mOutputStream = os;
			raw           = null;
		}

        CacheTileWriter(Tile tile, byte[] rawdata, String provider) {
            mTile         = tile;
            mTileProvider = provider;
            raw           = rawdata;
            mOutputStream = null;
        }

		@Override
		public Tile getTile() {
			return mTile;
		}

		@Override
		public OutputStream getOutputStream() {
			return mOutputStream;
		}

		@Override
		public void complete(boolean success) {
            if (dbg) logDebug("CacheTileWriter - complete - saving...  " + mTile + " prov:" + mTileProvider);
			//saveTile(mTile, mOutputStream, success, mTileProvider);
            saveTile(mTile, raw, mTileProvider);
		}
	}


	class SQLiteHelper extends SQLiteOpenHelper {

		//private static final String DATABASE_NAME = "tile.db";
		private static final int DATABASE_VERSION = 1;

		private static final String TILE_SCHEMA =
		        "CREATE TABLE IF NOT EXISTS "
		                + TABLE_NAME + "("
                        + COL_PROV   + " VARCHAR NOT NULL,"
		                + "x             INTEGER NOT NULL,"
		                + "y             INTEGER NOT NULL,"
		                + "z             INTEGER NOT NULL,"
		                + COL_TIME   + " LONG    NOT NULL,"
		                + COL_ACCESS + " LONG    NOT NULL,"
		                + COL_DATA   + " BLOB    NOT NULL,"
						+ COL_SIZE   + " INTEGER NOT NULL,"
		                + "PRIMARY KEY(provider,x,y,z));";

		public SQLiteHelper(Context context, String dbName) {
			super(context, dbName, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			logDebug("create table");
			db.execSQL(TILE_SCHEMA);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			logDebug("drop table");
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			onCreate(db);
		}

		@Override
		public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			onUpgrade(db, oldVersion, newVersion);
		}
	}



	public void saveTile(Tile tile, ByteArrayOutputStream data, boolean success, String provider) {

		byte[] rawData = null;

		if (success) rawData = data.toByteArray();

		synchronized (mCacheBuffers) {
			data.reset();
			mCacheBuffers.add(data);
		}

		if (!success) return;

        if (dbg) logDebug(">>>>>>>>>>> saveTile - storing tile in DB... " +  tile + " prov:" + provider);

		synchronized (mStmtPutTile) {
            mStmtPutTile.bindString(1, provider);
            mStmtPutTile.bindLong(2, tile.tileX);
            mStmtPutTile.bindLong(3, tile.tileY);
            mStmtPutTile.bindLong(4, tile.zoomLevel);
            mStmtPutTile.bindLong(5, 0);
            mStmtPutTile.bindLong(6, System.currentTimeMillis());
            mStmtPutTile.bindBlob(7, rawData);
            mStmtPutTile.bindLong(8, rawData.length);

			mStmtPutTile.execute();
			mStmtPutTile.clearBindings();
		}
        if (dbg) logDebug(">>>>>>>>>>> saveTile - tile stored in DB: " +  tile + " prov:" + provider);
	}



    public void saveTile(Tile tile, byte[] rawData, String provider) {

	    if (exists(tile, provider)) {
            if (dbg) logDebug(">>>>>>>>>>> saveTile - update tile in DB... " +  tile + "   size: " + rawData.length + " prov:" + provider);
            synchronized (mStmtUpdateTile) {
                mStmtUpdateTile.bindLong(1, System.currentTimeMillis());
                mStmtUpdateTile.bindLong(2, System.currentTimeMillis());
                mStmtUpdateTile.bindBlob(3, rawData);
                mStmtUpdateTile.bindLong(4, rawData.length);
                mStmtUpdateTile.bindString(5, provider);
                mStmtUpdateTile.bindLong(6, tile.tileX);
                mStmtUpdateTile.bindLong(7, tile.tileY);
                mStmtUpdateTile.bindLong(8, tile.zoomLevel);

                mStmtUpdateTile.execute();
                mStmtUpdateTile.clearBindings();
            }
            if (dbg) logDebug(">>>>>>>>>>> saveTile - tile updated in DB: " + tile + " prov:" + provider);
        }
        else {
            if (dbg) logDebug(">>>>>>>>>>> saveTile - storing tile in DB... " + tile + "   size: " + rawData.length + " prov:" + provider);

            synchronized (mStmtPutTile) {
                mStmtPutTile.bindString(1, provider);
                mStmtPutTile.bindLong(2, tile.tileX);
                mStmtPutTile.bindLong(3, tile.tileY);
                mStmtPutTile.bindLong(4, tile.zoomLevel);
                mStmtPutTile.bindLong(5, 0);
                mStmtPutTile.bindLong(6, System.currentTimeMillis());
                mStmtPutTile.bindBlob(7, rawData);
                mStmtPutTile.bindLong(8, rawData.length);

                mStmtPutTile.execute();
                mStmtPutTile.clearBindings();
            }
            if (dbg) logDebug(">>>>>>>>>>> saveTile - tile stored in DB: " + tile + " prov:" + provider);
        }
    }




	public TileReader getTile(Tile tile, String provider) {
		InputStream in = null;

        mStmtGetTile.bindString(1, provider);
		mStmtGetTile.bindLong(2, tile.tileX);
		mStmtGetTile.bindLong(3, tile.tileY);
		mStmtGetTile.bindLong(4, tile.zoomLevel);

		try {
            if (dbg) logDebug(">>>>>>>>>>> getTile - requesting tile " + tile + " prov:" + provider);
			ParcelFileDescriptor result = mStmtGetTile.simpleQueryForBlobFileDescriptor();
			in = new FileInputStream(result.getFileDescriptor());
            if (dbg) logDebug(">>>>>>>>>>> getTile - tile read into stream: " + tile + " prov:" + provider);
		} catch (SQLiteDoneException e) {
			logDebug(">>>>>>>>>>> getTile - tile not in cache: " + tile + " prov:" + provider);
			return null;
		} finally {
			mStmtGetTile.clearBindings();
		}

		return new CacheTileReader(tile, in, provider);
	}


    public TileReader getTile(Tile tile, boolean raw, String provider) {

        String[] cols = new String[] {"data"};

        Cursor cur = mDatabase.query(TABLE_NAME, cols, "provider = " + "\"" + provider + "\""  +
                                                               " and    x = " + tile.tileX +
                                                               " and    y = " + tile.tileY +
                                                               " and    z = " + tile.zoomLevel,
                                     (String[])null, (String)null, (String)null, (String)null);

        byte[] bytes         = null;
        //long   lastModified = 0L;

        if (cur.getCount() != 0) {
            cur.moveToFirst();
            bytes         = cur.getBlob(cur.getColumnIndex("data"));
            //lastModified = cur.getLong(cur.getColumnIndex("time"));
        }
        cur.close();

        return new CacheTileReader(tile, bytes, provider);
    }



    // check whether the tile exists in DB
    public boolean exists(Tile tile, String provider) {

        long     lastModified = 0L;
        String[] cols         = new String[] {"last_access"};

        Cursor cur = mDatabase.query(TABLE_NAME, cols, "provider = " + "\"" + provider + "\""  +
                                                                " and x   = " + tile.tileX +
                                                                " and y   = " + tile.tileY +
                                                                " and z   = " + tile.zoomLevel,
                                     (String[])null, (String)null, (String)null, (String)null);

        if (cur.getCount() != 0) {
            cur.moveToFirst();
            lastModified = cur.getLong(cur.getColumnIndex("last_access"));
        }
        cur.close();

        return lastModified > 0L;
    }



    @Override
    public TileWriter writeTile(Tile tile, String provider) {

        if (dbg) logDebug("writeTile - writing...  " + tile + " prov:" + provider);
        ByteArrayOutputStream os;

        synchronized (mCacheBuffers) {
            if (mCacheBuffers.size() == 0)  os = new ByteArrayOutputStream(32 * 1024);
            else                            os = mCacheBuffers.remove(mCacheBuffers.size() - 1);
        }
        return new CacheTileWriter(tile, os, provider);
    }

    @Override
    public TileWriter writeTile(Tile tile, byte[] rawData, String provider) {

        if (dbg) logDebug("writeTile - writing...  " + tile + "  size:" + rawData.length + " prov:" + provider );
        return new CacheTileWriter(tile, rawData, provider);
    }



	@Override
	public void setCacheSize(long size) {
	}


    public void dispose() {
        if (mDatabase.isOpen())	mDatabase.close();
    }




    // handling Logging:
    private void logDebug(String msg) { Log.d("ikroshlab", " ### " + getClass().getName() + " ###  " + msg); }
}
