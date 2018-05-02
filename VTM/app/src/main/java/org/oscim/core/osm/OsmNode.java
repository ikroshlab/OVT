/*
 * Copyright 2013 Tobias Knerr
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
package org.oscim.core.osm;

import org.oscim.core.TagSet;

import com.vividsolutions.jts.geom.Geometry;

public class OsmNode extends OsmElement {

	public final double lat;
	public final double lon;

	public OsmNode(double lat, double lon, TagSet tags, long id) {
		super(tags, id);
		this.lat = lat;
		this.lon = lon;
	}

	@Override
	public String toString() {
		return "n" + id;
	}

	@Override
	public Geometry toJts() {
		return null; //bnew Point(new Coordinate(lat, lon), null);
	}
}
