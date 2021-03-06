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
package com.epicest.flagreplacer;

import com.epicest.flagreplacer.FlagReplacer.ReplacementType;
import javax.swing.ImageIcon;

/**
 *
 * @author Jair
 */
public class FlagPreset implements Comparable {
    
    public ImageIcon previewIcon;
    public String materialPath;
    public ReplacementType type;
    public int index;
    
    public String toString() {
        return materialPath;
    }

    @Override
    public int compareTo(Object o) {
        return toString().compareTo(o.toString());
    }
}
