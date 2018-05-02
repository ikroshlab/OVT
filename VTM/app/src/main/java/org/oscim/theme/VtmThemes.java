/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2013 Hannes Janetzek
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
package org.oscim.theme;

import java.io.InputStream;

import org.oscim.backend.AssetAdapter;

/**
 * Enumeration of all internal rendering themes.
 */
public enum VtmThemes implements ThemeFile {

	DEFAULT(   "styles/default.xml"),
	TRONRENDER("styles/tronrender.xml"),
	NEWTRON(   "styles/newtron.xml"),
	OSMARENDER("styles/osmarender.xml");

	private final String mPath;

	private VtmThemes(String path) {
		mPath = path;
	}

	@Override
	public InputStream getRenderThemeAsStream() {
		return AssetAdapter.readFileAsStream(mPath);
	}
}
