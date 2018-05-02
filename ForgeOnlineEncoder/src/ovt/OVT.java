/*
 * These packages are parts of the OpenScienceMap project (http://www.opensciencemap.org).
 * Copyright OpenScienceMap.org
 * Copyright ikroshlab.com
 * 
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 */
package ovt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import ovt.TileSource.OpenResult;
import ovt.tileextractor.MyMath;
import ovt.header.MapFileInfo;


public class OVT {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        logDebug("Starting application...");
        
        String fname = "<your .map file path>";      // set .map file location!
        
        
        
        MapFileTileSource src = new MapFileTileSource();
        boolean ok = src.setMapFile(fname);
        
        if (!ok) {
            logDebug("Bad file !");
            return;
        }
        
        logDebug("file has been set. Getting datasource...");
        OpenResult res = src.open();
        if (!res.isSuccess()) {
            logDebug("file cannot be open !");
            return;
        }
        
        ITileDataSource mdb = src.getDataSource();
        logDebug("data source OK: " + (mdb != null));
        
        // extract tile numbers from bounding box
        MapFileInfo fileInfo = src.fileHeader.getMapFileInfo();
        BoundingBox bb       = fileInfo.boundingBox;       
        int[] zooms          = fileInfo.zoomLevel;
        Arrays.sort(zooms);
        byte pZoomMin = (byte)zooms[0];       
        byte pZoomMax = (byte)zooms[zooms.length-1];
                 
        long start = System.currentTimeMillis();
        for (byte zoomLevel=1; zoomLevel<=9; zoomLevel++) {                // change the range as you wish!
            Collection<Tile> tiles = getTilesCoverage(bb, zoomLevel);  
            
            for (Tile t : tiles) {
                //logDebug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
                mdb.query(t);
            }
        }                                                     
        
        long end = System.currentTimeMillis();
        logDebug("closing source... time: " + (end-start)/1000);
        src.close();
        
    }
    
    
    
    
    ////////////////////////////////////////////////////////////////////////////
    
    public static List<Tile> getTilesCoverage(BoundingBox pBB, byte pZoomMin, byte pZoomMax) {
        ArrayList<Tile> result = new ArrayList<>();

        for(byte zoomLevel = pZoomMin; zoomLevel <= pZoomMax; ++zoomLevel) {
            Collection<Tile> resultForZoom = getTilesCoverage(pBB, zoomLevel);
            result.addAll(resultForZoom);
        }
        return result;
    }


    public static Collection<Tile> getTilesCoverage(BoundingBox pBB, byte pZoomLevel) {

        HashSet<Tile> result = new HashSet<>();
        int mapTileUpperBound   = 1 << pZoomLevel;

        Point lowerRight = getMapTileFromCoordinates(pBB.getMinLatitude(), pBB.getMaxLongitude(), pZoomLevel);
        Point upperLeft  = getMapTileFromCoordinates(pBB.getMaxLatitude(), pBB.getMinLongitude(), pZoomLevel);

        int width  = (int) (lowerRight.x - upperLeft.x + 1);
        int height = (int) (lowerRight.y - upperLeft.y + 1);

        if(width  <= 0)  width  += mapTileUpperBound;
        if(height <= 0)  height += mapTileUpperBound;

        for(int i = 0; i < width; ++i) {
            for(int j = 0; j < height; ++j) {
                int x = MyMath.mod((int) (upperLeft.x + i), mapTileUpperBound);
                int y = MyMath.mod((int) (upperLeft.y + j), mapTileUpperBound);
                result.add(new Tile(x, y, pZoomLevel));
            }
        }

        return result;
    }
    
    
    
    
    public static Point getMapTileFromCoordinates(double aLat, double aLon, int zoom) {
        int y = (int)Math.floor((1.0D - Math.log(Math.tan(aLat * 3.141592653589793D / 180.0D) + 1.0D / Math.cos(aLat * 3.141592653589793D / 180.0D)) / 3.141592653589793D) / 2.0D * (double)(1 << zoom));
        int x = (int)Math.floor((aLon + 180.0D) / 360.0D * (double)(1 << zoom));
        return new Point(x, y);
    }
    
    
    
    
    
    
    
    
    
    
    
    // handling Logging:
    static void logDebug(String msg) { System.out.println(" ### MAIN ###  " + msg); }
}
