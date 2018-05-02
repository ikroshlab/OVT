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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.oscim.theme.IRenderTheme.ThemeException;


/**
 * An ExternalRenderTheme allows for customizing the rendering style of the map via an XML file.
 */
public class ExternalRenderTheme implements ThemeFile {

	private static final long serialVersionUID = 1L;

	private final long   mFileModificationDate;
	private final String mPath;

	/**
	 * @param fileName
	 *            the path to the XML render theme file.
	 * @throws FileNotFoundException
	 *             if the file does not exist or cannot be read.
	 */
	public ExternalRenderTheme(String fileName) {

		File themeFile = new File(fileName);
		if (!themeFile.exists()) {
			throw new ThemeException("file does not exist: " + themeFile.getAbsolutePath());
		} else if (!themeFile.isFile()) {
			throw new ThemeException("not a file: " + fileName);
		} else if (!themeFile.canRead()) {
			throw new ThemeException("cannot read file: " + fileName);
		}

		mFileModificationDate = themeFile.lastModified();
		if (mFileModificationDate == 0L) {
			throw new ThemeException("cannot read last modification time");
		}
		mPath = fileName;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (!(obj instanceof ExternalRenderTheme)) {
			return false;
		}
		ExternalRenderTheme other = (ExternalRenderTheme) obj;
		if (mFileModificationDate != other.mFileModificationDate) {
			return false;
		} else if (mPath == null && other.mPath != null) {
			return false;
		} else if (mPath != null && !mPath.equals(other.mPath)) {
			return false;
		}
		return true;
	}

	@Override
	public InputStream getRenderThemeAsStream() {
		InputStream is;

		try {
			is = new FileInputStream(mPath);
		} catch (FileNotFoundException e) {
			throw new ThemeException(e.getMessage());
		}
		return is;
	}
}
