/*
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
package org.oscim.tiling.source.mapsforgeonline;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.log4j.lf5.util.StreamUtils;
import org.oscim.tiling.source.MapElement;
import org.oscim.core.PointF;
import org.oscim.core.Tag;
import org.oscim.core.Tile;
import org.oscim.tiling.source.ITileDataSink;
import org.oscim.tiling.source.ITileDecoder;


import static org.oscim.tiling.source.mapsforgeonline.DeCoder.decoder;


public class TileDecoder implements ITileDecoder {

	private Tile                    mTile;
	private byte[]                  raw_tile;

    private int                     tX, tY, tZ;
    private int                     mTileLatitude;
    private int                     mTileLongitude;
    private ArrayList<MapElement>   waters;
    private ArrayList<MapElement>   pois;
    private ArrayList<MapElement>   ways;





    // constructor
	public TileDecoder() {	}

	@Override
	public byte[] getRawTile() {
        return raw_tile == null ? null : raw_tile.clone();
    }



	@Override
	public boolean decode(Tile tile, ITileDataSink sink, InputStream is, boolean preserveData)  throws IOException {

		mTile = tile;

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        BufferedOutputStream  out        = new BufferedOutputStream(dataStream, 8192);
        StreamUtils.copy(is, out);

        raw_tile = dataStream.toByteArray();
        ByteArrayInputStream byteStream = new ByteArrayInputStream(raw_tile);
        byteStream.reset();
        out.close();

        if (raw_tile != null && raw_tile.length>0) {

            // 1. parse elements and fill the arrays
            waters = new ArrayList<>();
            pois   = new ArrayList<>();
            ways   = new ArrayList<>();

            tileFromInputStream(byteStream);

            // 2. process tile elements
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

            waters = null;
            pois   = null;
            ways   = null;

            return true;
        }

		return false;
	}




    //////////////// parser /////////////////
    // reconstruct tile from input stream
    private void tileFromInputStream(ByteArrayInputStream dataIn) {

        DataInputStream  dis = new DataInputStream(dataIn);

        try {
            tX              = dis.readInt();
            tY              = dis.readInt();
            tZ              = dis.readInt();
            mTileLatitude   = dis.readInt();
            mTileLongitude  = dis.readInt();
            int waters_size = dis.readInt();
            int pois_size   = dis.readInt();
            int ways_size   = dis.readInt();

            for (int i=0; i<waters_size; i++) {  // waters
                MapElement e = new MapElement();

                // 1. meta
                e.layer           = dis.readInt();
                e.tags.numTags    = dis.readInt();
                int points_length = dis.readInt();
                int index_length  = dis.readInt();
                e.pointPos        = dis.readInt();
                e.indexPos        = dis.readInt();
                int   tp          = dis.readInt();       // type
                float px          = dis.readFloat();     // e.mTmpPoint.getX()
                float py          = dis.readFloat();     // e.mTmpPoint.getY()
                e.pointLimit      = dis.readInt();

                e.mTmpPoint       = new PointF(px, py);
                e.setTypeInt(tp);

                // 2. tags
                Tag[] tagsR = new Tag[e.tags.numTags];
                for (int j=0; j<e.tags.numTags; j++) {
                    tagsR[j] = new Tag(dis.readUTF(), dis.readUTF());
                }
                e.tags.set(tagsR);

                // 3. points array
                float[] ptsR = new float[points_length];
                for (int j=0; j<points_length; j++) {
                    ptsR[j] = dis.readFloat();
                }
                e.points = ptsR;

                // 4. index array
                int[] idxR = new int[index_length];
                for (int ixr=0; ixr<index_length; ixr++) {
                    idxR[ixr] = dis.readInt();
                }
                e.index = idxR;

                // 5. add to array waters
                waters.add(e);
            }

            for (int i=0; i<pois_size; i++) {  // pois
                MapElement e = new MapElement();

                // 1. meta
                e.layer           = dis.readInt();
                e.tags.numTags    = dis.readInt();
                int points_length = dis.readInt();
                int index_length  = dis.readInt();
                e.pointPos        = dis.readInt();
                e.indexPos        = dis.readInt();
                int   tp          = dis.readInt();       // type
                float px          = dis.readFloat();     // e.mTmpPoint.getX()
                float py          = dis.readFloat();     // e.mTmpPoint.getY()
                e.pointLimit      = dis.readInt();

                e.mTmpPoint       = new PointF(px, py);
                e.setTypeInt(tp);

                // 2. tags
                Tag[] tagsR = new Tag[e.tags.numTags];
                for (int j=0; j<e.tags.numTags; j++) {
                    tagsR[j] = new Tag(dis.readUTF(), dis.readUTF());
                }
                e.tags.set(tagsR);

                // 3. points array
                float[] ptsR = new float[points_length];
                for (int j=0; j<points_length; j++) {
                    ptsR[j] = dis.readFloat();
                }
                e.points = ptsR;

                // 4. index array
                int[] idxR = new int[index_length];
                for (int ixr=0; ixr<index_length; ixr++) {
                    idxR[ixr] = dis.readInt();
                }
                e.index = idxR;

                // 5. add to array pois
                pois.add(e);
            }

            for (int i=0; i<ways_size; i++) {  // ways
                MapElement e = new MapElement();

                // 1. meta
                e.layer           = dis.readInt();
                e.tags.numTags    = dis.readInt();
                int points_length = dis.readInt();
                int index_length  = dis.readInt();
                e.pointPos        = dis.readInt();
                e.indexPos        = dis.readInt();
                int   tp          = dis.readInt();       // type
                float px          = dis.readFloat();     // e.mTmpPoint.getX()
                float py          = dis.readFloat();     // e.mTmpPoint.getY()
                e.pointLimit      = dis.readInt();

                e.mTmpPoint       = new PointF(px, py);
                e.setTypeInt(tp);

                // 2. tags
                Tag[] tagsR = new Tag[e.tags.numTags];
                if (tZ<11) {
                    String key   = "";
                    if (dis.readBoolean())  key   = decoder.get(dis.readByte());
                    else                    key   = dis.readUTF();

                    String value = "";
                    if (dis.readBoolean())  value = decoder.get(dis.readByte());
                    else                    value = dis.readUTF();

                    tagsR[0] = new Tag(key, value);
                }
                else {
                    for (int j = 0; j < e.tags.numTags; j++) {
                        String key   = "";
                        if (dis.readBoolean())  key   = decoder.get(dis.readByte());
                        else                    key   = dis.readUTF();

                        String value = "";
                        if (dis.readBoolean())  value = decoder.get(dis.readByte());
                        else                    value = dis.readUTF();

                        tagsR[j] = new Tag(key, value);
                    }
                }
                e.tags.set(tagsR);

                // 3. points array
                float[] ptsR = new float[points_length];
                for (int j=0; j<points_length; j++) {
                    ptsR[j] = dis.readFloat();
                }
                e.points = ptsR;

                // 4. index array
                int[] idxR = new int[index_length];
                for (int j=0; j<index_length; j++) {
                    idxR[j] = dis.readInt();
                }
                e.index = idxR;

                // 5. add to array ways
                ways.add(e);
            }

            dataIn.close();

        } catch (IOException ex)     {
            ex.printStackTrace();
        }
    }



    // handling Logging:
    private void logDebug(String msg) { Log.d("VTM", " ### " + getClass().getName() + " ###  " + msg); }
}
