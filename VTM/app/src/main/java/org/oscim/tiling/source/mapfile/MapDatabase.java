/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2013, 2014 Hannes Janetzek
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
package org.oscim.tiling.source.mapfile;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.oscim.core.GeometryBuffer.GeometryType.LINE;
import static org.oscim.core.GeometryBuffer.GeometryType.POLY;
import static org.oscim.tiling.ITileDataSink.QueryResult.FAILED;
import static org.oscim.tiling.ITileDataSink.QueryResult.SUCCESS;
import static org.oscim.tiling.source.mapsforgeonline.DeCoder.coder;
import static org.oscim.tiling.source.mapsforgeonline.DeCoder.decoder;

import org.oscim.android.cache.TileCache;
import org.oscim.core.GeometryBuffer.GeometryType;
import org.oscim.core.MapElement;
import org.oscim.core.MercatorProjection;
import org.oscim.core.PointF;
import org.oscim.core.Tag;
import org.oscim.core.TagSet;
import org.oscim.core.Tile;
import org.oscim.layers.tile.MapTile;
import org.oscim.tiling.ITileCache;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.source.mapfile.header.SubFileParameter;
import org.oscim.tiling.source.mapsforgeonline.TileDecoder;
import org.oscim.utils.geom.TileClipper;



/**
 * A class for reading binary map files.
 * 
 * @see <a href="http://code.google.com/p/mapsforge/wiki/SpecificationBinaryMapFile">Specification</a>
 */
public class MapDatabase implements ITileDataSource {

    private static final String  DEBUG_SIGNATURE_BLOCK             = "block signature: ";          // Debug message prefix for the block signature
    private static final String  DEBUG_SIGNATURE_POI               = "POI signature: ";            // Debug message prefix for the POI signature
    private static final String  DEBUG_SIGNATURE_WAY               = "way signature: ";            // Debug message prefix for the way signature
    private static final String  INVALID_FIRST_WAY_OFFSET          = "invalid first way offset: "; // Error message for an invalid first way offset


    private static final long    BITMASK_INDEX_OFFSET              = 0x7FFFFFFFFFL; // Bitmask to extract the block offset from an index entry
	private static final long    BITMASK_INDEX_WATER               = 0x8000000000L; // Bitmask to extract the water information from an index entry

	private static final int     MAXIMUM_WAY_NODES_SEQUENCE_LENGTH = 8192;          // Maximum way nodes sequence length which is considered as valid
	private static final int     MAXIMUM_ZOOM_TABLE_OBJECTS        = 65536 * 2;     // Maximum number of map objects in the zoom table which is considered as valid

    private static final byte    SIGNATURE_LENGTH_BLOCK            = 32;            // Length of the debug signature at the beginning of each block

    // POI
    private static final byte    SIGNATURE_LENGTH_POI              = 32;            // Length of the debug signature at the beginning of each POI
    private static final int     POI_FEATURE_ELEVATION             = 0x20;          // Bitmask for the optional POI feature "elevation"
	private static final int     POI_FEATURE_HOUSE_NUMBER          = 0x40;          // Bitmask for the optional POI feature "house number"
	private static final int     POI_FEATURE_NAME                  = 0x80;          // Bitmask for the optional POI feature "name"
	private static final int     POI_LAYER_BITMASK                 = 0xf0;          // Bitmask for the POI layer
	private static final int     POI_LAYER_SHIFT                   = 4;             // Bit shift for calculating the POI layer
	private static final int     POI_NUMBER_OF_TAGS_BITMASK        = 0x0f;          // Bitmask for the number of POI tags

	// ways
	private static final byte    SIGNATURE_LENGTH_WAY              = 32;            // Length of the debug signature at the beginning of each way
	private static final int     WAY_FEATURE_DATA_BLOCKS_BYTE      = 0x08;          // Bitmask for the optional way data blocks byte
	private static final int     WAY_FEATURE_DOUBLE_DELTA_ENCODING = 0x04;          // Bitmask for the optional way double delta encoding
	private static final int     WAY_FEATURE_HOUSE_NUMBER          = 0x40;          // Bitmask for the optional way feature "house number"
	private static final int     WAY_FEATURE_LABEL_POSITION        = 0x10;          // Bitmask for the optional way feature "label position"
	private static final int     WAY_FEATURE_NAME                  = 0x80;          // Bitmask for the optional way feature "name"
	private static final int     WAY_FEATURE_REF                   = 0x20;          // Bitmask for the optional way feature "reference"
	private static final int     WAY_LAYER_BITMASK                 = 0xf0;          // Bitmask for the way layer
	private static final int     WAY_LAYER_SHIFT                   = 4;             // Bit shift for calculating the way layer
	private static final int     WAY_NUMBER_OF_TAGS_BITMASK        = 0x0f;          // Bitmask for the number of way tags


    private boolean                 mUseCache                      = true;
	private long                    mFileSize;
	private boolean                 mDebugFile;
	private RandomAccessFile        mInputFile;
	private ReadBuffer              mReadBuffer;
	private String                  mSignatureBlock;
	private String                  mSignaturePoi;
	private String                  mSignatureWay;
	private int                     mTileLatitude;
	private int                     mTileLongitude;
	private int[]                   mIntBuffer;

	private  MapElement        mElem;//                          = new MapElement();

	private int                     minDeltaLat;
    private int                     minDeltaLon;

	private final TileProjection    mTileProjection;
	private final TileClipper       mTileClipper;

	private final MapFileTileSource mTileSource;

    private final static Tag        mWaterTag                      = new Tag("natural", "water");


    private int                     tX;
    private int                     tY;
    private int                     tZ;

    private ArrayList<MapElement>   waters;
    private ArrayList<MapElement>   pois;
    private ArrayList<MapElement>   ways;



    // constructor
	public MapDatabase(MapFileTileSource tileSource) throws IOException {
		mTileSource = tileSource;
		try {
			/* open the file in read only mode */
			mInputFile  = new RandomAccessFile(tileSource.mapFile, "r");
			mFileSize   = mInputFile.length();
			mReadBuffer = new ReadBuffer(mInputFile);

		} catch (IOException e) {
			logDebug(e.getMessage());
			/* make sure that the file is closed */
			dispose();
			throw new IOException();
		}

		mTileProjection = new TileProjection();
		mTileClipper    = new TileClipper(0, 0, 0, 0);
	}


	public MapFileTileSource getMapFileTileSource() {
	    return mTileSource;
    }



    // todo - called from VectorTileLoader
	@Override
	public void query(MapTile tile, ITileDataSink sink) {

        logDebug("query - requested tile: " + tile + " prov: mapsForge");

        // 1. check if tile exists in local database
        if (mUseCache) {

            boolean    ok    = false;
            TileCache mCache = mTileSource.getTileCache();
            ITileCache.TileReader cacheReader = mCache.getTile(tile, true, "mapsforge");

            if (cacheReader.checkTile()) {
                logDebug("query - FOUND in cache - tile: " + tile + " prov: mapsForge");

                byte[] raw_tile = cacheReader.getData();
                if (raw_tile != null && raw_tile.length > 0) {

                    logDebug("query - parsing raw data: " + tile + "   size: " + raw_tile.length + " prov: mapsForge");

                    TileDecoder mTileDecoder = new TileDecoder();  // todo - added call to decoder class from ForgeOnlineTileSource package

                    try {
                        ByteArrayInputStream byteStream = new ByteArrayInputStream(raw_tile);
                        byteStream.reset();

                        //ok = mTileDecoder.decode(tile, sink, raw_tile);
                        ok = mTileDecoder.decode(tile, sink, byteStream, false);
                        logDebug("query -  decoded successfully: " + ok + "  tile: " + tile + " prov: mapsForge");

                    } catch (IOException e) {
                        logDebug("query - Cache read failure: tile: " + tile + " prov: mapsForge; " + "  error:" + e.getMessage());
                    }

                    if (ok) {
                        sink.completed(SUCCESS);
                        return;
                    }
                }
                logDebug("query - NO raw data ! - skip; tile: " + tile + " prov: mapsForge");
            }
            logDebug("query - NOT found in cache - tile:" + tile + " prov: mapsForge");
        }




        // 2. so requested tile does NOT exist in DB, - requesting .map file (if provided)
        if (mTileSource.fileHeader == null) {
			sink.completed(FAILED);
			return;
		}

		if (mIntBuffer == null)  mIntBuffer = new int[MAXIMUM_WAY_NODES_SEQUENCE_LENGTH * 2];

		try {
			mTileProjection.setTile(tile);

			/* size of tile in map coordinates; */
			double size = 1.0 / (1 << tile.zoomLevel);

			/* simplification tolerance */
			int pixel = (tile.zoomLevel > 11) ? 1 : 2;

			int simplify = Tile.SIZE / pixel;

			/* translate screen pixel for tile to latitude and longitude
			 * tolerance for point reduction before projection. */
			minDeltaLat = (int) (Math.abs(MercatorProjection.toLatitude(tile.y + size)
			                            - MercatorProjection.toLatitude(tile.y)) * 1e6) / simplify;
			minDeltaLon = (int) (Math.abs(MercatorProjection.toLongitude(tile.x + size)
			                            - MercatorProjection.toLongitude(tile.x)) * 1e6) / simplify;

			QueryParameters queryParameters = new QueryParameters();
			queryParameters.queryZoomLevel  = mTileSource.fileHeader.getQueryZoomLevel(tile.zoomLevel);

			/* get and check the sub-file for the query zoom level */
			SubFileParameter subFileParameter = mTileSource.fileHeader.getSubFileParameter(queryParameters.queryZoomLevel);

			if (subFileParameter == null) {
				logDebug("no sub-file for zoom level: "   + queryParameters.queryZoomLevel);

				sink.completed(FAILED);
				return;
			}

			QueryCalculations.calculateBaseTiles(queryParameters, tile, subFileParameter);
			QueryCalculations.calculateBlocks(queryParameters, subFileParameter);

            waters = new ArrayList<>();
            pois   = new ArrayList<>();
            ways   = new ArrayList<>();
            tX     = tile.tileX;
            tY     = tile.tileY;
            tZ     = tile.zoomLevel;


			processBlocks(sink, queryParameters, subFileParameter);

		} catch (IOException e) {
			logDebug(e.getMessage());
            waters = null;
            pois   = null;
            ways   = null;

			sink.completed(FAILED);
			return;
		}



        // 3. process tile elements
        logDebug(">>>>>> WATERS -->   " + tX + "/" + tY + "/" + tZ + "     size: " + waters.size());
        for (MapElement e : waters) {
            if (e != null) sink.process(e);
        }
        logDebug(">>>>>>    POI -->   " + tX + "/" + tY + "/" + tZ + "     size: " + pois.size());
        for (MapElement e : pois) {
            if (e != null) sink.process(e);
        }
        logDebug(">>>>>>   WAYS -->   " + tX + "/" + tY + "/" + tZ + "     size: " + ways.size());
        for (MapElement e : ways) {
            if (e != null) sink.process(e);
        }



        // 4. save tile into local DB
        if (mUseCache) {

            Tile t = tile.getTile();
            TileCache mCache = mTileSource.getTileCache();

            // 1. check if this tile exists in local DB
            ITileCache.TileReader cacheReader = mCache.getTile(tile, true, "mapsforge");

            if (!cacheReader.checkTile()) {  // tile not found

                // 2. encode tile into byte array:
                byte[] rawTile = encodeTile();  // todo - added - calling tile ENCODER

                // 3. store this tile into DB:
                if (rawTile != null && rawTile.length>0) {
                    logDebug("query - storing into DB - tile: " + tile + "  size: " + rawTile.length + " prov: mapsForge");

                    ITileCache.TileWriter cacheWriter = mCache.writeTile(t, rawTile, "mapsforge");
                    cacheWriter.complete(true);
                }
            }
        }

        // todo - send tile ready message to VectorTileLoader --> TileLoader --> TileManager --> TileRenderer
        sink.completed(SUCCESS);

		waters = null;
        pois   = null;
        ways   = null;
	}





	/////////////////////////////////////  PROCESSING  /////////////////////////////////////////////
    private void processBlocks(ITileDataSink mapDataSink, QueryParameters queryParams, SubFileParameter subFileParameter) throws IOException {

		/* read and process all blocks from top to bottom and from left to right */
        for (long row = queryParams.fromBlockY; row <= queryParams.toBlockY; row++) {
            for (long column = queryParams.fromBlockX; column <= queryParams.toBlockX; column++) {
                //mCurrentCol = column - queryParameters.fromBlockX;
                //mCurrentRow = row    - queryParameters.fromBlockY;

                setTileClipping(queryParams, row - queryParams.fromBlockY,column - queryParams.fromBlockX);

                /* calculate the actual block number of the needed block in the file */
                long blockNumber = row * subFileParameter.blocksWidth + column;

                /* get the current index entry */
                long blockIndexEntry = mTileSource.databaseIndexCache.getIndexEntry(subFileParameter, blockNumber);

                /* check the water flag of the block in its index entry */
                /*
                if ((blockIndexEntry & BITMASK_INDEX_WATER) != 0) {
                    MapElement e = new MapElement();
                    e.clear();
                    e.tags.clear();
                    e.tags.add(mWaterTag);
                    e.startPolygon();
                    e.addPoint(xmin, ymin);
                    e.addPoint(xmax, ymin);
                    e.addPoint(xmax, ymax);
                    e.addPoint(xmin, ymax);


                    waters.add(e);
                }
                */


                /* get and check the current block pointer */
                long blockPointer = blockIndexEntry & BITMASK_INDEX_OFFSET;
                if (blockPointer < 1 || blockPointer > subFileParameter.subFileSize) {
                    logDebug("invalid current block pointer: " + blockPointer + "  subFileSize: " + subFileParameter.subFileSize);
                    return;
                }

                long nextBlockPointer;
                if (blockNumber + 1 == subFileParameter.numberOfBlocks) {    // check if the current block is the last block in the file
                    nextBlockPointer = subFileParameter.subFileSize;         // set the next block pointer to the end of the file
                }
                else {                                    					 // get and check the next block pointer
                    nextBlockPointer = mTileSource.databaseIndexCache.getIndexEntry(subFileParameter,blockNumber + 1);
                    nextBlockPointer &= BITMASK_INDEX_OFFSET;

                    if (nextBlockPointer < 1 || nextBlockPointer > subFileParameter.subFileSize) {
                        logDebug("invalid next block pointer: " + nextBlockPointer + "  sub-file size: " + subFileParameter.subFileSize);
                        return;
                    }
                }

                /* calculate the size of the current block */
                int blockSize = (int) (nextBlockPointer - blockPointer);
                if (blockSize < 0) {
                    logDebug("current block size must not be negative: "  + blockSize);
                    return;
                }
                else if (blockSize == 0) {	                             // the current block is empty, continue with the next block
                    continue;
                }
                else if (blockSize > ReadBuffer.MAXIMUM_BUFFER_SIZE) {   // the current block is too large, continue with the next block
                    logDebug("current block size too large: " + blockSize);
                    continue;
                }
                else if (blockPointer + blockSize > mFileSize) {
                    logDebug("current block larger than file size: " + blockSize);
                    return;
                }

                mInputFile.seek(subFileParameter.startAddress + blockPointer); // seek to the current block in the map file

                /* read the current block into the buffer */
                if (!mReadBuffer.readFromFile(blockSize)) { 	/* skip the current block */
                    logDebug("reading current block has failed: " + blockSize);
                    return;
                }

                /* calculate the top-left coordinates of the underlying tile */
                double tileLatitudeDeg  = Projection.tileYToLatitude(subFileParameter.boundaryTileTop   + row,    subFileParameter.baseZoomLevel);
                double tileLongitudeDeg = Projection.tileXToLongitude(subFileParameter.boundaryTileLeft + column, subFileParameter.baseZoomLevel);

                mTileLatitude  = (int) (tileLatitudeDeg  * 1E6);
                mTileLongitude = (int) (tileLongitudeDeg * 1E6);

                processBlock(queryParams, subFileParameter, mapDataSink);
            }
        }
    }





	/**
	 * Processes a single block and executes the callback functions on all map elements.
	 * 
	 * @param queryParameters  the parameters of the current query.
	 * @param subFileParameter the parameters of the current map file.
	 * @param mapDataSink      the callback which handles the extracted map elements.
	 */
	private void processBlock(QueryParameters queryParameters, SubFileParameter subFileParameter, ITileDataSink mapDataSink) {

		if (!processBlockSignature())  return;

		int[][] zoomTable = readZoomTable(subFileParameter);
		if (zoomTable == null)  return;

		int zoomTableRow         = queryParameters.queryZoomLevel - subFileParameter.zoomLevelMin;
		int poisOnQueryZoomLevel = zoomTable[zoomTableRow][0];
		int waysOnQueryZoomLevel = zoomTable[zoomTableRow][1];

		/* get the relative offset to the first stored way in the block */
		int firstWayOffset = mReadBuffer.readUnsignedInt();
		if (firstWayOffset < 0) {
			logDebug(INVALID_FIRST_WAY_OFFSET + firstWayOffset);
			if (mDebugFile)  logDebug(DEBUG_SIGNATURE_BLOCK + mSignatureBlock);

			return;
		}

		/* add the current buffer position to the relative first way offset */
		firstWayOffset += mReadBuffer.getBufferPosition();
		if (firstWayOffset > mReadBuffer.getBufferSize()) {
			logDebug(INVALID_FIRST_WAY_OFFSET + firstWayOffset);
			if (mDebugFile)  logDebug(DEBUG_SIGNATURE_BLOCK + mSignatureBlock);

			return;
		}


		// process POIs
		if (!processPOIs(mapDataSink, poisOnQueryZoomLevel))  return;


		/* finished reading POIs, check if the current buffer position is valid */
		if (mReadBuffer.getBufferPosition() > firstWayOffset) {
			logDebug("invalid buffer position: " + mReadBuffer.getBufferPosition());
			if (mDebugFile)  logDebug(DEBUG_SIGNATURE_BLOCK + mSignatureBlock);

			return;
		}

		/* move the pointer to the first way */
		mReadBuffer.setBufferPosition(firstWayOffset);

		// process WAYs
		if (!processWays(queryParameters, mapDataSink, waysOnQueryZoomLevel)) 	return;

	}

	//	private long mCurrentRow;
	//	private long mCurrentCol;

	private int xmin, ymin, xmax, ymax;

	private void setTileClipping(QueryParameters queryParameters, long mCurrentRow, long mCurrentCol) {
		long numRows = queryParameters.toBlockY - queryParameters.fromBlockY;
		long numCols = queryParameters.toBlockX - queryParameters.fromBlockX;

		//logDebug("setTileClipping: " + numCols + "/" + numRows + " " + mCurrentCol + " " + mCurrentRow);
		xmin = -16;
		ymin = -16;
		xmax = Tile.SIZE + 16;
		ymax = Tile.SIZE + 16;

		if (numRows > 0) {
			int w = (int) (Tile.SIZE / (numCols + 1));
			int h = (int) (Tile.SIZE / (numRows + 1));

			if (mCurrentCol > 0)    	xmin = (int) (mCurrentCol * w);
			if (mCurrentCol < numCols)	xmax = (int) (mCurrentCol * w + w);
			if (mCurrentRow > 0)		ymin = (int) (mCurrentRow * h);
			if (mCurrentRow < numRows)	ymax = (int) (mCurrentRow * h + h);
		}
		mTileClipper.setRect(xmin, ymin, xmax, ymax);
	}







	////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Processes the given number of POIs.
	 * 
	 * @param mapDataSink  the callback which handles the extracted POIs.
	 * @param numberOfPois how many POIs should be processed.
	 * @return             true if the POIs could be processed successfully, false  otherwise.
	 */
	private boolean processPOIs(ITileDataSink mapDataSink, int numberOfPois) {
		Tag[]      poiTags = mTileSource.fileInfo.poiTags;
		int        numTags = 0;

        for (int elementCounter = numberOfPois; elementCounter != 0; --elementCounter) {

            MapElement e = new MapElement();

            if (mDebugFile) {
                mSignaturePoi = mReadBuffer.readUTF8EncodedString(SIGNATURE_LENGTH_POI);        // get and check the POI signature
                if (!mSignaturePoi.startsWith("***POIStart")) {
                    logDebug("invalid POI signature: " + mSignaturePoi + " " + DEBUG_SIGNATURE_BLOCK + " " + mSignatureBlock);
                    return false;
                }
            }

            int  latitude     = mTileLatitude  + mReadBuffer.readSignedInt();                   //get the POI latitude offset (VBE-S)
            int  longitude    = mTileLongitude + mReadBuffer.readSignedInt();                   // get the POI longitude offset (VBE-S)

            byte specialByte  = mReadBuffer.readByte();                                         //  get the special byte which encodes multiple flags
            byte layer        = (byte) ((specialByte & POI_LAYER_BITMASK) >>> POI_LAYER_SHIFT); // bit 1-4 represent the layer
            byte numberOfTags = (byte) ( specialByte & POI_NUMBER_OF_TAGS_BITMASK);             // bit 5-8 represent the number of tag IDs

            if (numberOfTags != 0) {
                if (!mReadBuffer.readTags(e.tags, poiTags, numberOfTags))  return false;
            }


            /* get the feature bitmask (1 byte) */
            byte featureByte = mReadBuffer.readByte();

            /* bit 1-3 enable optional features
             * check if the POI has a name */
            if ((featureByte & POI_FEATURE_NAME) != 0) {
                String str = mReadBuffer.readUTF8EncodedString();
                e.tags.add(new Tag(Tag.KEY_NAME, str, false));
            }

            /* check if the POI has a house number */
            if ((featureByte & POI_FEATURE_HOUSE_NUMBER) != 0) {
                String str = mReadBuffer.readUTF8EncodedString();
                e.tags.add(new Tag(Tag.KEY_HOUSE_NUMBER, str, false));
            }

            /* check if the POI has an elevation */
            if ((featureByte & POI_FEATURE_ELEVATION) != 0) {
                String str = Integer.toString(mReadBuffer.readSignedInt());
                e.tags.add(new Tag(Tag.KEY_ELE, str, false));
            }


            mTileProjection.projectPoint(latitude, longitude, e);

            e.setLayer(layer);

            // filter tags
            List<Tag> tset = new ArrayList<>();
            for (Tag t : e.tags.asArray()) {
                if (t != null) tset.add(t);
            }
            Tag[] tarr = new Tag[tset.size()];
            for (int i=0; i<tset.size(); i++)
                tarr[i] = tset.get(i);

            e.tags.set(tarr);
            e.tags.numTags = tarr.length;

            pois.add(e);  // add this POI to main array
        }

        return true;
	}




	////////////////////////////////////////////////////////////////////////////////////////////////
	// processing ways
	private int stringOffset = -1;

	/**
	 * Processes the given number of ways.
	 *
	 * @param queryParameters  the parameters of the current query.
	 * @param mapDataSink      the callback which handles the extracted ways.
	 * @param numberOfWays     how many ways should be processed.
	 * @return                 true if the ways could be processed successfully, false otherwise.
	 */
	private boolean processWays(QueryParameters queryParameters, ITileDataSink mapDataSink, int numberOfWays) {

		Tag[]      wayTags        = mTileSource.fileInfo.wayTags;
		int        numTags        = 0;
		int        wayDataBlocks;

        // skip string block
        int stringsSize = 0;
        stringOffset    = 0;

        if (mTileSource.experimental) {
            stringsSize  = mReadBuffer.readUnsignedInt();
            stringOffset = mReadBuffer.getBufferPosition();
            mReadBuffer.skipBytes(stringsSize);
        }


        for (int elementCounter = numberOfWays; elementCounter != 0; --elementCounter) {

            MapElement e = new MapElement();

            if (mDebugFile) {   // get and check the way signature
                mSignatureWay = mReadBuffer.readUTF8EncodedString(SIGNATURE_LENGTH_WAY);
                if (!mSignatureWay.startsWith("---WayStart")) {
                    logDebug("invalid way signature: " + mSignatureWay + " " + DEBUG_SIGNATURE_BLOCK + " " + mSignatureBlock);
                    return false;
                }
            }

            if (queryParameters.useTileBitmask) {
                elementCounter = mReadBuffer.skipWays(queryParameters.queryTileBitmask, elementCounter);

                if (elementCounter == 0)  return true;

                if (elementCounter < 0)   return false;

                if (mTileSource.experimental && mReadBuffer.lastTagPosition > 0) {
                    int pos = mReadBuffer.getBufferPosition();
                    mReadBuffer.setBufferPosition(mReadBuffer.lastTagPosition);

                    byte numberOfTags = (byte) (mReadBuffer.readByte() & WAY_NUMBER_OF_TAGS_BITMASK);

                    if (!mReadBuffer.readTags(e.tags, wayTags, numberOfTags))  return false;

                    numTags = numberOfTags;

                    mReadBuffer.setBufferPosition(pos);
                }
            }
            else {
                int wayDataSize = mReadBuffer.readUnsignedInt();
                if (wayDataSize < 0) {
                    logDebug("invalid way data size: " + wayDataSize + "  BUG way 2");
                    if (mDebugFile)  logDebug(DEBUG_SIGNATURE_BLOCK + mSignatureBlock);

                    return false;
                }

                mReadBuffer.skipBytes(2);  // ignore the way tile bitmask (2 bytes)
            }


            byte specialByte  = mReadBuffer.readByte();                                         // get the special byte which encodes multiple flags
            byte layer        = (byte) ((specialByte & WAY_LAYER_BITMASK) >>> WAY_LAYER_SHIFT); // bit 1-4 represent the layer
            byte numberOfTags = (byte) ( specialByte & WAY_NUMBER_OF_TAGS_BITMASK);             // bit 5-8 represent the number of tag IDs

            if (numberOfTags != 0) {
                if (!mReadBuffer.readTags(e.tags, wayTags, numberOfTags))  return false;
                numTags = numberOfTags;
            }

            byte featureByte = mReadBuffer.readByte();                                          // get the feature bitmask (1 byte)

            boolean featureWayDoubleDeltaEncoding = (featureByte & WAY_FEATURE_DOUBLE_DELTA_ENCODING) != 0; // bit 1-6 enable optional features

            boolean hasName    = (featureByte & WAY_FEATURE_NAME)         != 0;
            boolean hasHouseNr = (featureByte & WAY_FEATURE_HOUSE_NUMBER) != 0;
            boolean hasRef     = (featureByte & WAY_FEATURE_REF)          != 0;

            e.tags.numTags = numTags;

            if (mTileSource.experimental) {
                if (hasName) {
                    int textPos = mReadBuffer.readUnsignedInt();
                    String str  = mReadBuffer.readUTF8EncodedStringAt(stringOffset + textPos);
                    e.tags.add(new Tag(Tag.KEY_NAME, str, false));
                }
                if (hasHouseNr) {
                    int textPos = mReadBuffer.readUnsignedInt();
                    String str  = mReadBuffer.readUTF8EncodedStringAt(stringOffset + textPos);
                    e.tags.add(new Tag(Tag.KEY_HOUSE_NUMBER, str, false));
                }
                if (hasRef) {
                    int textPos = mReadBuffer.readUnsignedInt();
                    String str  = mReadBuffer.readUTF8EncodedStringAt(stringOffset + textPos);
                    e.tags.add(new Tag(Tag.KEY_REF, str, false));
                }
            }
            else {
                if (hasName) {
                    String str = mReadBuffer.readUTF8EncodedString();
                    e.tags.add(new Tag(Tag.KEY_NAME, str, false));
                }
                if (hasHouseNr) {
                    String str = mReadBuffer.readUTF8EncodedString();
                    e.tags.add(new Tag(Tag.KEY_HOUSE_NUMBER, str, false));
                }
                if (hasRef) {
                    String str = mReadBuffer.readUTF8EncodedString();
                    e.tags.add(new Tag(Tag.KEY_REF, str, false));
                }
            }

            float[] labelPosition = null;
            if ((featureByte & WAY_FEATURE_LABEL_POSITION) != 0)
                labelPosition = readOptionalLabelPosition();



            if ((featureByte & WAY_FEATURE_DATA_BLOCKS_BYTE) != 0) {
                wayDataBlocks = mReadBuffer.readUnsignedInt();

                if (wayDataBlocks < 1) {
                    logDebug("invalid number of way data blocks: " + wayDataBlocks);
                    logDebugSignatures();

                    return false;
                }
            }
            else  wayDataBlocks = 1;


            /* some guessing if feature is a line or a polygon */
            boolean linearFeature = e.tags.containsKey("highway") || e.tags.containsKey("boundary") || e.tags.containsKey("railway");
            if (linearFeature) {
                Tag areaTag = e.tags.get("area");
                if (areaTag != null && areaTag.value == Tag.VALUE_YES)  linearFeature = false;
            }

            // todo - filter tags and thus elements to be added to main array:
            boolean toAdd = false;
            if (tZ <= 8) {
                Tag t = e.tags.asArray()[0];   // analyze only the first tag fo these zoom levels

                if      (t.key.equals("area")     &&  t.value.equals("yes"))              toAdd = true;
                else if (t.key.equals("highway")  && (
                                                      t.value.equals("primary")
                                                  ||  t.value.equals("motorway")
                                                  ||  t.value.equals("trunk")
                                                     ))                                   toAdd = true;
                else if (t.key.equals("natural")  && (
                                                      t.value.equals("wood")
                                               // ||  t.value.equals("water")
                                                     ))                                   toAdd = true;
            }

            else if (tZ > 8 && tZ < 11) {
                Tag t = e.tags.asArray()[0];   // analyze only the first tag fo these zoom levels
                if      (t.key.equals("area")     &&  t.value.equals("yes"))              toAdd = true;
                else if (t.key.equals("highway")  && (
                                                      t.value.equals("primary")
                                                  ||  t.value.equals("motorway")
                                                  ||  t.value.equals("trunk")
                                                     ))                                   toAdd = true;
                else if (t.key.equals("natural")  && (
                                                      t.value.equals("wood")
                                                  ||  t.value.equals("water")))           toAdd = true;
                else if (t.key.equals("waterway") && (
                                                      t.value.equals("riverbank")
                                                  ||  t.value.equals("river")
                                                     ))                                   toAdd = true;
                //else if (t.key.equals("boundary") &&  t.value.equals("administrative")) toAdd = true;
                //else if (t.key.equals("landuse")  &&  t.value.equals("forest"))         toAdd = true;
            }



            for (int wayDataBlock = 0; wayDataBlock < wayDataBlocks; wayDataBlock++) {
                //e.clear();

                if (!processWayDataBlock(e, featureWayDoubleDeltaEncoding, linearFeature))	return false;

                /* drop invalid outer ring */
                if (e.isPoly() && e.index[0] < 6) {
                    continue;
                }

                mTileProjection.project(e);

                if (!e.tags.containsKey("building")) {
                    if (!mTileClipper.clip(e)) {
                        continue;
                    }
                }
            }


            // filter tags
            List<Tag> tset = new ArrayList<>();
            for (Tag t : e.tags.asArray()) {
                if (t != null) tset.add(t);
            }
            Tag[] tarr = new Tag[tset.size()];
            for (int i=0; i<tset.size(); i++)
                tarr[i] = tset.get(i);

            e.tags.set(tarr);
            e.tags.numTags = tarr.length;


            // filter points
            float min_dist = 0.5f;
            /*
            if      (tZ <= 8)             min_dist = 200.0f;
            else if (tZ >  8 && tZ <= 10) min_dist = 100.0f;
            else if (tZ > 10 && tZ <= 12) min_dist =  50.0f;
            else if (tZ > 12 && tZ <= 15) min_dist =  10.0f;
            else if (tZ > 15 && tZ <= 17) min_dist =   2.0f;
            else if (tZ > 17)             min_dist =   1.0f;
            */

            e.simplify(min_dist, true);
            e.setLayer(layer);

            if (tZ < 11) {
                if (toAdd) {
                    ways.add(e);
                }
            }
            else {
                ways.add(e);
            }
        }

		return true;
	}



	private boolean processWayDataBlock(MapElement e, boolean doubleDeltaEncoding, boolean isLine) {
		/* get and check the number of way coordinate blocks (VBE-U) */
		int numBlocks = mReadBuffer.readUnsignedInt();
		if (numBlocks < 1 || numBlocks > Short.MAX_VALUE) {
			logDebug("invalid number of way coordinate blocks: " + numBlocks);
			return false;
		}

		int[] wayLengths = e.ensureIndexSize(numBlocks, false);
		if (wayLengths.length > numBlocks)	wayLengths[numBlocks] = -1;

		/* read the way coordinate blocks */
		for (int coordinateBlock = 0; coordinateBlock < numBlocks; ++coordinateBlock) {

			int numWayNodes = mReadBuffer.readUnsignedInt();

			if (numWayNodes < 2 || numWayNodes > MAXIMUM_WAY_NODES_SEQUENCE_LENGTH) {
				logDebug("invalid number of way nodes: " + numWayNodes);
				logDebugSignatures();
				return false;
			}

			/* each way node consists of latitude and longitude */
			int len = numWayNodes * 2;

            wayLengths[coordinateBlock] = decodeWayNodes(doubleDeltaEncoding, e, len, isLine);
		}

		return true;
	}




	private int decodeWayNodes(boolean doubleDelta, MapElement e, int length, boolean isLine) {

		int[] buffer = mIntBuffer;
		mReadBuffer.readSignedInt(buffer, length);
        int cnt = 2;

        float[] outBuffer = e.ensurePointSize(e.pointPos + length, true);
        int     outPos    = e.pointPos;
        int     lat, lon;

        /* first node latitude single-delta offset */
        int firstLat = lat = mTileLatitude  + buffer[0];
        int firstLon = lon = mTileLongitude + buffer[1];

        outBuffer[outPos++] = lon;
        outBuffer[outPos++] = lat;

        int deltaLat = 0;
        int deltaLon = 0;


        for (int pos = 2; pos < length; pos += 2) {

            if (doubleDelta) {
                deltaLat = buffer[pos]     + deltaLat;
                deltaLon = buffer[pos + 1] + deltaLon;
            }
            else {
                deltaLat = buffer[pos];
                deltaLon = buffer[pos + 1];
            }
            lat += deltaLat;
            lon += deltaLon;

            if (pos == length - 2) {
                boolean line = isLine || (lon != firstLon && lat != firstLat);

                if (line) {
                    outBuffer[outPos++] = lon;
                    outBuffer[outPos++] = lat;
                    cnt += 2;
                }

                if (e.type == GeometryType.NONE)  e.type = line ? LINE : POLY;

            }
            else if (deltaLon > minDeltaLon || deltaLon < -minDeltaLon
                  || deltaLat > minDeltaLat || deltaLat < -minDeltaLat) {

                outBuffer[outPos++] = lon;
                outBuffer[outPos++] = lat;
                cnt += 2;
            }
        }

        e.pointPos = outPos;

        return cnt;
	}








    ////////////////////////////////////////////////////////////////////////////////////////////////
	private float[] readOptionalLabelPosition() {
		float[] labelPosition = new float[2];

		/* get the label position latitude offset (VBE-S) */
		labelPosition[1] = mTileLatitude + mReadBuffer.readSignedInt();

		/* get the label position longitude offset (VBE-S) */
		labelPosition[0] = mTileLongitude + mReadBuffer.readSignedInt();

		return labelPosition;
	}



	private int[][] readZoomTable(SubFileParameter subFileParameter) {
		int rows = subFileParameter.zoomLevelMax - subFileParameter.zoomLevelMin + 1;
		int[][] zoomTable = new int[rows][2];

		int cumulatedNumberOfPois = 0;
		int cumulatedNumberOfWays = 0;

		for (int row=0; row<rows; row++) {
			cumulatedNumberOfPois += mReadBuffer.readUnsignedInt();
			cumulatedNumberOfWays += mReadBuffer.readUnsignedInt();

			if (cumulatedNumberOfPois < 0 || cumulatedNumberOfPois > MAXIMUM_ZOOM_TABLE_OBJECTS) {
				logDebug("invalid cumulated number of POIs in row " + row + ' ' + cumulatedNumberOfPois);
				if (mDebugFile) logDebug(DEBUG_SIGNATURE_BLOCK + mSignatureBlock);

				return null;
			}
			else if (cumulatedNumberOfWays < 0 || cumulatedNumberOfWays > MAXIMUM_ZOOM_TABLE_OBJECTS) {
				logDebug("invalid cumulated number of ways in row " + row + ' ' + cumulatedNumberOfWays);
				if (mTileSource.fileInfo.debugFile)  logDebug(DEBUG_SIGNATURE_BLOCK + mSignatureBlock);

				return null;
			}

			zoomTable[row][0] = cumulatedNumberOfPois;
			zoomTable[row][1] = cumulatedNumberOfWays;
		}

		return zoomTable;
	}





    /**
     * Processes the block signature, if present.
     *
     * @return true if the block signature could be processed successfully, false otherwise.
     */
    private boolean processBlockSignature() {
        if (mDebugFile) {
			/* get and check the block signature */
            mSignatureBlock = mReadBuffer.readUTF8EncodedString(SIGNATURE_LENGTH_BLOCK);
            if (!mSignatureBlock.startsWith("###TileStart")) {
                logDebug("invalid block signature: " + mSignatureBlock);
                return false;
            }
        }
        return true;
    }




    /**
     * Logs the debug signatures of the current way and block.
     */
    private void logDebugSignatures() {
        if (mDebugFile) {
            logDebug(DEBUG_SIGNATURE_WAY   + mSignatureWay);
            logDebug(DEBUG_SIGNATURE_BLOCK + mSignatureBlock);
        }
    }



    @Override
    public void dispose() {
        mReadBuffer = null;
        if (mInputFile != null) {

            try {
                mInputFile.close();
                mInputFile = null;
            } catch (IOException e) {
                logDebug(e.getMessage());
            }
        }
    }


    @Override
    public void cancel() {
    }




	// TileProjection inner class
	static class TileProjection {
		private static final double COORD_SCALE = 1000000.0;

		long   dx,   dy;
		double divx, divy;

		void setTile(Tile tile) {
			/* tile position in pixels at tile zoom */
			long x = tile.tileX * Tile.SIZE;
			long y = tile.tileY * Tile.SIZE + Tile.SIZE;

			/* size of the map in pixel at tile zoom */
			long mapExtents = Tile.SIZE << tile.zoomLevel;

			/* offset relative to lat/lon == 0 */
			dx = (x - (mapExtents >> 1));
			dy = (y - (mapExtents >> 1));

			/* scales longitude(1e6) to map-pixel */
			divx = (180.0 * COORD_SCALE) / (mapExtents >> 1);

			/* scale latitude to map-pixel */
			divy = (Math.PI * 2.0) / (mapExtents >> 1);
		}


		public void projectPoint(int lat, int lon, MapElement out) {
			out.clear();
			out.startPoints();
			out.addPoint(projectLon(lon), projectLat(lat));
		}


		public float projectLat(double lat) {
			double s = Math.sin(lat * ((Math.PI / 180) / COORD_SCALE));
			double r = Math.log((1.0 + s) / (1.0 - s));

			return Tile.SIZE - (float) (r / divy + dy);
		}


		public float projectLon(double lon) {
			return (float) (lon / divx - dx);
		}


		void project(MapElement e) {

			float[] coords  = e.points;
			int[]   indices = e.index;

			int inPos  = 0;
			int outPos = 0;

			boolean isPoly = e.isPoly();

			for (int idx = 0, m = indices.length; idx < m; idx++) {
				int len = indices[idx];
				if (len == 0)	continue;
				if (len < 0)	break;

				float lat, lon, pLon = 0, pLat = 0;
				int cnt = 0, first = outPos;

				for (int end = inPos + len; inPos < end; inPos += 2) {
					lon = projectLon(coords[inPos]);
					lat = projectLat(coords[inPos + 1]);

					if (cnt != 0) {
						/* drop small distance intermediate nodes */
						if (lat == pLat && lon == pLon) {
							//log.debug("drop zero delta ");
							continue;
						}
					}
					coords[outPos++] = pLon = lon;
					coords[outPos++] = pLat = lat;
					cnt += 2;
				}

				if (isPoly && coords[first] == pLon && coords[first + 1] == pLat) {
					/* remove identical start/end point */
					//logDebug("drop closing point " + e);
					indices[idx] = (short) (cnt - 2);
					outPos -= 2;
				}
				else {
					indices[idx] = (short) cnt;
				}
			}
		}
	}





    //  TODO  ///////////////////  added tile ENCODER for MapsForge format /////////////////////////
    /////////////////////////////  encode tile into byte array  ////////////////////////////////////
    private byte[] encodeTile() {

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(data);

        try {
            dos.writeInt(tX);
            dos.writeInt(tY);
            dos.writeInt(tZ);
            dos.writeInt(mTileLatitude);
            dos.writeInt(mTileLongitude);
            dos.writeInt(waters.size());
            dos.writeInt(pois.size());
            dos.writeInt(ways.size());

            for (int i=0; i<waters.size(); i++) {  // waters
                MapElement e = waters.get(i);

                // 1. meta
                dos.writeInt(e.layer);
                dos.writeInt(e.tags.numTags);
                dos.writeInt(e.points.length);
                dos.writeInt(e.index.length);
                dos.writeInt(e.pointPos);
                dos.writeInt(e.indexPos);
                dos.writeInt(e.getTypeInt());
                dos.writeFloat(e.mTmpPoint.getX());
                dos.writeFloat(e.mTmpPoint.getY());
                dos.writeInt(e.pointLimit);

                // 2. tags
                Tag[] tags = e.tags.asArray();
                for (Tag t : tags ) {
                    if (t == null)  continue;
                    dos.writeUTF(t.key);
                    dos.writeUTF(t.value);
                }

                // 3. points array
                float[] pts = e.points;
                for (float f : pts) {
                    dos.writeFloat(f);
                }

                // 4. index array
                int[] idx = e.index;
                for (int ix : idx) {
                    dos.writeInt(ix);
                }
            }

            for (int i=0; i<pois.size(); i++) {  // pois
                MapElement e = pois.get(i);

                // 1. meta
                dos.writeInt(e.layer);
                dos.writeInt(e.tags.numTags);
                dos.writeInt(e.points.length);
                dos.writeInt(e.index.length);
                dos.writeInt(e.pointPos);
                dos.writeInt(e.indexPos);
                dos.writeInt(e.getTypeInt());
                dos.writeFloat(e.mTmpPoint.getX());
                dos.writeFloat(e.mTmpPoint.getY());
                dos.writeInt(e.pointLimit);

                // 2. tags
                Tag[] tags = e.tags.asArray();
                for (Tag t : tags ) {
                    if (t == null)  continue;
                    dos.writeUTF(t.key);
                    dos.writeUTF(t.value);
                }

                // 3. points array
                float[] pts = e.points;
                for (float f : pts) {
                    dos.writeFloat(f);
                }

                // 4. index array
                int[] idx = e.index;
                for (int ix : idx) {
                    dos.writeInt(ix);
                }
            }

            for (int i=0; i<ways.size(); i++) {  // ways
                MapElement e = ways.get(i);

                // 1. meta
                dos.writeInt(e.layer);
                if (tZ<11) dos.writeInt(1);
                else       dos.writeInt(e.tags.numTags);
                dos.writeInt(e.points.length);
                dos.writeInt(e.index.length);
                dos.writeInt(e.pointPos);
                dos.writeInt(e.indexPos);
                dos.writeInt(e.getTypeInt());
                dos.writeFloat(e.mTmpPoint.getX());
                dos.writeFloat(e.mTmpPoint.getY());
                dos.writeInt(e.pointLimit);

                // 2. tags
                Tag[] tags = e.tags.asArray();
                if (tZ<11) {
                    if (coder.get(tags[0].key)   != null) {
                        dos.writeBoolean(true);
                        dos.writeByte(coder.get(tags[0].key));
                    }
                    else {
                        dos.writeBoolean(false);
                        dos.writeUTF(tags[0].key);
                    }

                    if (coder.get(tags[0].value) != null)  {
                        dos.writeBoolean(true);
                        dos.writeByte(coder.get(tags[0].value));
                    }
                    else {
                        dos.writeBoolean(false);
                        dos.writeUTF(tags[0].value);
                    }
                }
                else {
                    for (Tag t : tags) {
                        if (t == null)  continue;

                        if (coder.get(t.key)   != null) {
                            dos.writeBoolean(true);
                            dos.writeByte(coder.get(t.key));
                        }
                        else {
                            dos.writeBoolean(false);
                            dos.writeUTF(t.key);
                        }

                        if (coder.get(t.value) != null)  {
                            dos.writeBoolean(true);
                            dos.writeByte(coder.get(t.value));
                        }
                        else {
                            dos.writeBoolean(false);
                            dos.writeUTF(t.value);
                        }
                    }
                }

                // 3. points array
                float[] pts = e.points;
                for (float f : pts) {
                    dos.writeFloat(f);
                }

                // 4. index array
                int[] idx = e.index;
                for (int ix : idx) {
                    dos.writeInt(ix);
                }
            }

            dos.flush();

            byte[] rawTile = data.toByteArray();

            data.close();

            return rawTile;

        } catch (IOException ex)     {
            ex.printStackTrace();
            return null;
        }

    }



    // handling Logging:
    private void logDebug(String msg) { Log.d("VTM", " ### " + getClass().getName() + " ###  " + msg); }
}
