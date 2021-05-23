/*
 * Copyright (C) 2021 Jair
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epicest.filechooser;

import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.filechooser.FileFilter;

/**
 *
 * @author Jair
 */
public class ImageIOFilter extends FileFilter {

    private String[] filetypes;
    protected String description;

    public ImageIOFilter() {
        filetypes = ImageIO.getReaderFileSuffixes();
        description = "Supported image files (";
        for (String extension : filetypes) {
            description += "*." + extension + ";";
        }
        description = description.substring(0, description.lastIndexOf(';'));
        description += ")";
    }

    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        for (String extension : filetypes) {
            if (f.getName().toLowerCase().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDescription() {
        return description;
    }

}
