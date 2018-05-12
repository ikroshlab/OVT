/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2016-2017 devemux86
 * Copyright 2017 Longri
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
package com.ikroshlab.vtmexample.filepicker;

import org.oscim.theme.ExternalRenderTheme;
import org.oscim.theme.ThemeFile;
import org.oscim.theme.ThemeUtils;
import org.oscim.theme.XmlMapsforgeThemeBuilder;
import org.oscim.theme.XmlThemeBuilder;
import org.oscim.tiling.TileSource.OpenResult;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;

import javax.xml.parsers.SAXParserFactory;

/**
 * Accepts all valid render theme XML files.
 */
public final class ValidRenderTheme implements ValidFileFilter {
    private OpenResult mOpenResult;

    @Override
    public boolean accept(File file) {

        try {
            ThemeFile theme = new ExternalRenderTheme(file.getAbsolutePath());
            DefaultHandler renderThemeHandler;
            if (ThemeUtils.isMapsforgeTheme(theme))
                renderThemeHandler = new XmlMapsforgeThemeBuilder(theme);
            else
                renderThemeHandler = new XmlThemeBuilder(theme);
            XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            xmlReader.setContentHandler(renderThemeHandler);
            xmlReader.parse(new InputSource(theme.getRenderThemeAsStream()));
            mOpenResult = OpenResult.SUCCESS;
        } catch (Exception e) {
            mOpenResult = new OpenResult(e.getMessage());
        }
        return mOpenResult.isSuccess();
    }

    @Override
    public OpenResult getFileOpenResult() {
        return mOpenResult;
    }
}
