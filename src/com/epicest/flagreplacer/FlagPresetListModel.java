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
import java.util.List;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.ListModel;
import javax.swing.event.ListDataListener;

/**
 *
 * @author Jair
 */
public class FlagPresetListModel implements ListModel<FlagPreset> {

    FlagPreset[] list;

    public FlagPresetListModel() {
        list = new FlagPreset[0];
        try {
            FileReader fileReader = new FileReader(getClass().getResource("/presets/presets.txt").getFile());

            List<FlagPreset> presets = new ArrayList<>();

            try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
                int i = 0;
                String nextLine;
                while ((nextLine = bufferedReader.readLine()) != null) {
                    String[] input = nextLine.split(":");
                    FlagPreset preset = new FlagPreset();
                    preset.materialPath = input[1];
                    preset.type = ReplacementType.valueOf(input[0]);
                    preset.previewIcon = new ImageIcon(ImageIO.read(getClass().getResource("/presets/" + (i + 1) + ".png")));
                    preset.index = i;
                    presets.add(preset);

                    i++;
                }
            }

            presets.sort(null);
            list = presets.toArray(list);
        } catch (FileNotFoundException fnfe) {
            Logger.getLogger(FlagPresetListModel.class.getName()).log(Level.SEVERE, null, fnfe);
        } catch (IOException ioe) {
            Logger.getLogger(FlagPresetListModel.class.getName()).log(Level.SEVERE, null, ioe);
        }
    }

    @Override
    public int getSize() {
        return list.length;
    }

    @Override
    public FlagPreset getElementAt(int index) {
        return list[index];
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        //do nothing
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        //do nothing
    }

}
