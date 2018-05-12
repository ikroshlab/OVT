These packages are parts of the OpenScienceMap project (http://www.opensciencemap.org).

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.

There are two projects:

   - VTMencoder: this is an example of code to show how to encode map tiles from Mapsforge original map file to a byte array.
     This byte array can be put further into a database or so.
     This project keep reference to VTM_0.9.2 as a Maven dependency. 
     This project is developed under NetBeans, pure Java SE. 

   - VTMexample: this is the client part for the VTMencoder - it demonstrates how to get map tiles from online datasource using
     the ForgeOnlineTileSource.class and decode them.
     This project keep reference to VTM_0.9.2 as a Gradle dependency. 
     This project is developed under Android Studio, min sdk version 23.
     
The underground problem is now largely discussed on https://groups.google.com/forum/#!topic/mapsforge-dev/YPUd-9zfPuU 
If you are interested in, join this forum, please!

      

