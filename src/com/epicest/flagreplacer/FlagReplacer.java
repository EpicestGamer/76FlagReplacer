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

import ddsutil.DDSUtil;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import jogl.DDSImage;
import org.jdesktop.swingx.graphics.BlendComposite;

/**
 *
 * @author Jair
 */
public class FlagReplacer extends javax.swing.JFrame {

    /**
     * Initializes initial look and feel and starts the application.
     *
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            //Set operating system look and feel.
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } //Jesus why so many different exceptions?
        catch (ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException ex) {
            Logger.getLogger(FlagReplacer.class.getName()).log(Level.WARNING, "Unable to set OS look and feel", ex);
        }

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> {
            new FlagReplacer().setVisible(true);
        });
    }

    /**
     * Type of UV used in a material
     *
     * Used for determining behavior of drawing and saving
     */
    public static enum ReplacementType {
        /**
         * Single flag taking up the whole texture
         */
        FLAG_SINGLE,
        /**
         * Single flag with seperate front and back images, stacked vertically
         * in the texture
         */
        FLAG_DUAL,
        /**
         * Two flags, stacked vertically in the texture
         * <br><br>
         * Treated as <code>DUAL</code> when drawing and saving for the time
         * being
         */
        FLAG_SEPERATE,
        /**
         * A wallpaper, left unused
         */
        WALLPAPER
    }

    /**
     * ListModel that loads FlagPresets
     */
    private final FlagPresetListModel flagPresetListModel;

    /**
     * Application icon
     */
    private BufferedImage appIcon;
    /**
     * Used when there is no material flag preview
     */
    private ImageIcon noFlagIcon;

    /**
     * Current UV image mode
     */
    private ReplacementType currentType = ReplacementType.FLAG_SINGLE;
    private Color flagpoleConnectionColor = new Color(66, 61, 59);
    // Constant compositing images.
    /**
     * No flag found image for compositing
     */
    private BufferedImage compositingNullFlag;
    /**
     * Stains image for compositing
     */
    private BufferedImage compositingStains;
    /**
     * Torn alpha for compositing
     */
    private BufferedImage compositingTorn;
    /**
     * Blasted variant 1 alpha for compositing
     */
    private BufferedImage compositingBlasted1;
    /**
     * Blasted variant 2 alpha for compositing
     */
    private BufferedImage compositingBlasted2;
    // Non-constant compositing images.
    /**
     * Input flag image, used as the base for new flags
     */
    private BufferedImage flagImageInput;
    /**
     * Output flag image, used for drawing and saving
     */
    private final BufferedImage textureOutput;
    /**
     * Output flag image preview icon, used for previewing the flag image in the
     * UI
     */
    private ImageIcon texturePreviewIcon;

    /**
     * Loads initial images and data, and then initializes the UI
     */
    public FlagReplacer() {
        try {
            //load app icon
            appIcon = ImageIO.read(getClass().getResource("/icons/appIcon.png"));
            //load no flag icon
            noFlagIcon = new ImageIcon(ImageIO.read(getClass().getResource("/icons/noFlag.png"))
                    .getScaledInstance(128, 128, Image.SCALE_SMOOTH));
            //load compositing images
            compositingNullFlag = getResourceImage("/compositing/noFlag.png");
            compositingStains = getResourceImage("/compositing/stains.png");
            compositingTorn = getResourceImage("/compositing/torn.png");
            compositingBlasted1 = getResourceImage("/compositing/blasted1.png");
            compositingBlasted2 = getResourceImage("/compositing/blasted2.png");
        } catch (IOException ioe) {
            // let the user know if failed
            JOptionPane.showMessageDialog(null,
                    "Error while initializing program, please alert the developer.\n\nException Message: " + ioe.getLocalizedMessage(),
                    "Fallout 76 Flag Replacer",
                    JOptionPane.ERROR_MESSAGE);
            Logger.getLogger(FlagReplacer.class.getName()).log(Level.SEVERE, "IOException occured while loading initial images.", ioe);
            System.exit(1);
        }
        textureOutput = new BufferedImage(1024, 1024, DEFAULT_IMAGE_TYPE);
        flagPresetListModel = new FlagPresetListModel();
        initComponents();
        setMinimumSize(getSize());
        repaintCustomFlag();
    }

    /**
     * Standardized image type to make working with images easier.
     */
    private static final int DEFAULT_IMAGE_TYPE = BufferedImage.TYPE_INT_ARGB;

    private static BufferedImage getResourceImage(String resourcePath) throws IOException {
        return convertBufferedImage(ImageIO.read(FlagReplacer.class.getResource(resourcePath)), DEFAULT_IMAGE_TYPE);
    }

    private static BufferedImage convertBufferedImage(BufferedImage image, int imageType) {
        BufferedImage returnedImage = new BufferedImage(image.getWidth(), image.getHeight(), imageType);
        Graphics rGraphics = returnedImage.getGraphics();
        rGraphics.drawImage(image, 0, 0, null);
        return returnedImage;
    }

    //TODO Rewrite method
    /**
     * Generates and saves the texture and material files used in this program.
     */
    private void saveFlagFiles() {
        int returnVal = saveFileChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File saveDirectory = saveFileChooser.getSelectedFile();
            File flexibleFile;
            //Path Seperator
            char sep = File.separatorChar;
            String materialPath = materialTargetChooserList.getSelectedValue().materialPath;
            materialPath = materialPath.replace('\\', sep);
            String materialIndex = String.format("%02d", materialTargetChooserList.getSelectedValue().index);
            try {
                //Create readme
                Files.copy(getClass().getResourceAsStream("/export/readme.txt"), saveDirectory.toPath().resolve("readme.txt"), StandardCopyOption.REPLACE_EXISTING);
                //Create materials folder, if it doesn't already exist
                flexibleFile = new File(saveDirectory.getAbsolutePath() + sep + materialPath.substring(0, materialPath.lastIndexOf(sep)));
                if (!flexibleFile.exists()) {
                    if (!flexibleFile.mkdirs()) {
                        // an issue occured
                        throw new IOException("Could not create material directories");
                    }
                }
                //Create material file
                flexibleFile = new File(saveDirectory.getAbsolutePath() + sep + materialPath);
                byte[] materialFileData;
                InputStream materialExportStream;
                if (currentType.equals(ReplacementType.FLAG_SINGLE)) {
                    materialExportStream = getClass().getResourceAsStream("/export/material-single.bgsm");
                } else {
                    materialExportStream = getClass().getResourceAsStream("/export/material-dual.bgsm");
                }
                materialFileData = new byte[materialExportStream.available()];
                materialExportStream.read(materialFileData);
                materialExportStream.close();
                //replace materialfile bytes 0x59-0x5A with bytes from materialIndex
                materialFileData[0x59] = (byte) materialIndex.charAt(0);
                materialFileData[0x5A] = (byte) materialIndex.charAt(1);
                Files.write(flexibleFile.toPath(), materialFileData, StandardOpenOption.CREATE);
                //Create texture folder
                String texturePath = "textures" + sep + "egfr" + sep + "SetDressing" + sep + "texture-" + materialIndex + "-d.dds";
                flexibleFile = new File(saveDirectory.getAbsolutePath() + sep + texturePath.substring(0, texturePath.lastIndexOf(sep)));
                if (!flexibleFile.exists()) {
                    if (!flexibleFile.mkdirs()) {
                        // an issue occured
                        throw new IOException("Could not create texture directories");
                    }
                }
                //Create texture
                flexibleFile = new File(saveDirectory.getAbsolutePath() + sep + texturePath);
                flexibleFile.createNewFile();
                DDSUtil.write(flexibleFile, convertBufferedImage(textureOutput, BufferedImage.TYPE_4BYTE_ABGR), DDSImage.D3DFMT_DXT5, true);
                //Open up an exploered window at the saved folder's location. or alert the user that the file is saved
                if (fileOpenCheckBox.isSelected()) {
                    Desktop.getDesktop().open(saveDirectory);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Saved textures and materials to \"" + saveDirectory.getAbsolutePath() + "\".",
                            "Fallout 76 Flag Replacer",
                            JOptionPane.INFORMATION_MESSAGE);
                    Logger.getLogger(FlagReplacer.class.getName()).log(Level.INFO, "Saved textures and materials to \"" + saveDirectory.getAbsolutePath() + "\".");
                }
            } catch (IOException ioe) {
                JOptionPane.showMessageDialog(this,
                        "Error while saving, please alert the developer.\n\nException Message: " + ioe.getLocalizedMessage(),
                        "Fallout 76 Flag Replacer",
                        JOptionPane.ERROR_MESSAGE);
                Logger.getLogger(FlagReplacer.class.getName()).log(Level.SEVERE, "IOException occured while saving.", ioe);
            }
        }
    }

    /**
     * Opens a file chooser and loads the selected file into the flag's input.
     */
    private void openFlagImage() {
        int returnVal = textureFlagImageFileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = textureFlagImageFileChooser.getSelectedFile();
            textureFlagImageTextbox.setText(file.getAbsolutePath());
            try {
                flagImageInput = ImageIO.read(file);
            } catch (IOException ioe) {
                flagImageInput = null;
                JOptionPane.showMessageDialog(this,
                        "Error while loading flag image file, please alert the developer.\n\nException Message: " + ioe.getLocalizedMessage(),
                        "Fallout 76 Flag Replacer",
                        JOptionPane.ERROR_MESSAGE);
                Logger.getLogger(FlagReplacer.class.getName()).log(Level.WARNING, "IOException occured while loading input flag image.", ioe);
            }
        }
        repaintCustomFlag();
    }

    /**
     * Refreshes the material preview and updates the current UV type
     */
    private void refreshMaterialChoice() {
        FlagPreset currentPreset = materialTargetChooserList.getSelectedValue();
        if (currentPreset != null) {
            materialPreviewLabel.setIcon(currentPreset.previewIcon);
            saveButton.setEnabled(true);
            currentType = currentPreset.type;
            switch (currentType) {
                case FLAG_SINGLE:
                    textureFlipCheckBox.setEnabled(false);
                    break;
                case FLAG_DUAL:
                    textureFlipCheckBox.setEnabled(true);
                    break;
            }
            repaintCustomFlag();
        } else {
            materialPreviewLabel.setIcon(noFlagIcon);
            saveButton.setEnabled(false);
        }
    }

    /**
     * Paints the flag texture.
     */
    public void repaintCustomFlag() {
        Graphics2D g = (Graphics2D) textureOutput.getGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.clearRect(0, 0, 1024, 1024);
        if (flagImageInput == null) {
            g.drawImage(compositingNullFlag, 0, 0, 1024, 1024, null);
        }
        if (textureFlagpoleConnectionCheckBox.isSelected()) {
            g.setColor(flagpoleConnectionColor);
            g.fillRect(0, 0, 13, 1024);
        }
        switch (currentType) {
            case FLAG_SINGLE:
                if (flagImageInput != null) {
                    g.drawImage(flagImageInput,
                            textureFlagpoleConnectionCheckBox.isSelected() ? 13 : 0, 0,
                            textureFlagpoleConnectionCheckBox.isSelected() ? 1011 : 1024, 1024,
                            null);
                }
                if (textureStainedCheckBox.isSelected()) {
                    g.setComposite(BlendComposite.Multiply);
                    g.drawImage(compositingStains, 0, 0, 1024, 1024, null);
                }
                g.setComposite(AlphaComposite.DstIn);
                if (textureTornCheckBox.isSelected()) {
                    g.drawImage(compositingTorn, 0, 0, 1024, 1024, null);
                }
                if (textureBlasted01CheckBox.isSelected()) {
                    g.drawImage(compositingBlasted1, 0, 0, 1024, 1024, null);
                }
                if (textureBlasted02CheckBox.isSelected()) {
                    g.drawImage(compositingBlasted2, 0, 0, 1024, 1024, null);
                }
                break;
            case FLAG_DUAL:
            case FLAG_SEPERATE:
                if (flagImageInput != null) {
                    g.drawImage(flagImageInput,
                            textureFlagpoleConnectionCheckBox.isSelected() ? 13 : 0, 0,
                            textureFlagpoleConnectionCheckBox.isSelected() ? 1011 : 1024, 512,
                            null);
                    if (!textureFlipCheckBox.isSelected()) //don't flip
                    {
                        g.drawImage(flagImageInput,
                                textureFlagpoleConnectionCheckBox.isSelected() ? 13 : 0, 512,
                                textureFlagpoleConnectionCheckBox.isSelected() ? 1011 : 1024, 512,
                                null);
                    } else //do flip
                    {
                        g.drawImage(flagImageInput, 1024, 512,
                                textureFlagpoleConnectionCheckBox.isSelected() ? -1011 : -1024, 512,
                                null);
                    }
                }
                if (textureStainedCheckBox.isSelected()) {
                    g.setComposite(BlendComposite.Multiply);
                    g.drawImage(compositingStains, 0, 0, 1024, 512, null);
                    g.drawImage(compositingStains, 0, 512, 1024, 512, null);
                }
                g.setComposite(AlphaComposite.DstIn);
                if (textureTornCheckBox.isSelected()) {
                    g.drawImage(compositingTorn, 0, 0, 1024, 512, null);
                    g.drawImage(compositingTorn, 0, 512, 1024, 512, null);
                }
                if (textureBlasted01CheckBox.isSelected()) {
                    g.drawImage(compositingBlasted1, 0, 0, 1024, 512, null);
                    g.drawImage(compositingBlasted1, 0, 512, 1024, 512, null);
                }
                if (textureBlasted02CheckBox.isSelected()) {
                    g.drawImage(compositingBlasted2, 0, 0, 1024, 512, null);
                    g.drawImage(compositingBlasted2, 0, 512, 1024, 512, null);
                }
                break;
        }

        texturePreviewIcon = new ImageIcon(textureOutput
                .getScaledInstance(128, 128, Image.SCALE_SMOOTH));
        texturePreviewLabel.setIcon(texturePreviewIcon);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        textureFlagImageFileChooser = new javax.swing.JFileChooser();
        saveFileChooser = new javax.swing.JFileChooser();
        aboutDialog = new javax.swing.JDialog();
        javax.swing.JLabel aboutLabel = new javax.swing.JLabel();
        javax.swing.JButton nexusButton = new javax.swing.JButton();
        javax.swing.JButton githubButton = new javax.swing.JButton();
        javax.swing.JButton doneButton = new javax.swing.JButton();
        javax.swing.JScrollPane licenseScrollPane = new javax.swing.JScrollPane();
        javax.swing.JTextArea licenseTextArea = new javax.swing.JTextArea();
        javax.swing.JPanel materialPanel = new javax.swing.JPanel();
        javax.swing.JPanel materialPreviewPanel = new javax.swing.JPanel();
        materialPreviewLabel = new javax.swing.JLabel();
        javax.swing.JScrollPane materialTargetChooserScrollPane = new javax.swing.JScrollPane();
        materialTargetChooserList = new javax.swing.JList<>();
        javax.swing.JPanel texturePanel = new javax.swing.JPanel();
        javax.swing.JPanel texturePreviewPanel = new javax.swing.JPanel();
        texturePreviewLabel = new javax.swing.JLabel();
        textureFlagImageTextbox = new javax.swing.JTextField();
        javax.swing.JButton textureFlagImageButton = new javax.swing.JButton();
        javax.swing.JPanel textureWearTearPanel = new javax.swing.JPanel();
        textureStainedCheckBox = new javax.swing.JCheckBox();
        textureTornCheckBox = new javax.swing.JCheckBox();
        textureBlasted01CheckBox = new javax.swing.JCheckBox();
        textureBlasted02CheckBox = new javax.swing.JCheckBox();
        javax.swing.JPanel textureOptionsPanel = new javax.swing.JPanel();
        textureFlagpoleConnectionCheckBox = new javax.swing.JCheckBox();
        textureFlipCheckBox = new javax.swing.JCheckBox();
        javax.swing.JButton aboutButton = new javax.swing.JButton();
        saveButton = new javax.swing.JButton();
        fileOpenCheckBox = new javax.swing.JCheckBox();

        textureFlagImageFileChooser.setAcceptAllFileFilterUsed(false);
        textureFlagImageFileChooser.setDialogTitle("Open");
        textureFlagImageFileChooser.setFileFilter(new com.epicest.filechooser.ImageIOFilter());

        saveFileChooser.setAcceptAllFileFilterUsed(false);
        saveFileChooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
        saveFileChooser.setDialogTitle("Save to...");
        saveFileChooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);

        aboutDialog.setTitle("Fallout 76 Flag Replacer About");
        aboutDialog.setIconImage(appIcon);
        aboutDialog.setResizable(false);

        aboutLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        aboutLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/appIcon.png"))); // NOI18N
        aboutLabel.setText("<html>\n<div style='text-align: center;'>\n<h1> Fallout 76 Flag Replacer Tool v1.0 </h1>\n<h2> Developed by Jair - EpicestGamer </h2>\n</div>\n<html>");
        aboutLabel.setToolTipText("");
        aboutLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        aboutLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        nexusButton.setText("Nexus Mods");
        nexusButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nexusButtonActionPerformed(evt);
            }
        });

        githubButton.setText("Github");
        githubButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                githubButtonActionPerformed(evt);
            }
        });

        doneButton.setText("Done");
        doneButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doneButtonActionPerformed(evt);
            }
        });

        licenseScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        licenseTextArea.setEditable(false);
        licenseTextArea.setColumns(20);
        licenseTextArea.setLineWrap(true);
        licenseTextArea.setRows(5);
        licenseTextArea.setText("76 Flag Replacer Tool\nCopyright (C) 2021  Jair\n\nThis program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.\n\nYou should have received a copy of the GNU General Public License along with this program.  If not, see https://www.gnu.org/licenses/.");
        licenseTextArea.setWrapStyleWord(true);
        licenseScrollPane.setViewportView(licenseTextArea);

        javax.swing.GroupLayout aboutDialogLayout = new javax.swing.GroupLayout(aboutDialog.getContentPane());
        aboutDialog.getContentPane().setLayout(aboutDialogLayout);
        aboutDialogLayout.setHorizontalGroup(
            aboutDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aboutDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(aboutDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(licenseScrollPane)
                    .addComponent(aboutLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, aboutDialogLayout.createSequentialGroup()
                        .addComponent(nexusButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(githubButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 242, Short.MAX_VALUE)
                        .addComponent(doneButton)))
                .addContainerGap())
        );
        aboutDialogLayout.setVerticalGroup(
            aboutDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aboutDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(aboutLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(licenseScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 186, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(aboutDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(doneButton)
                    .addComponent(nexusButton)
                    .addComponent(githubButton))
                .addContainerGap())
        );

        aboutLabel.getAccessibleContext().setAccessibleName("<html>\n<div style='text-align: center;'>\n<h1> Fallout 76 Flag Replacer Tool v1.0 </h1>\n<h2> Developed by Jair - EpicestGamer </h2>\n\n<p>76 Flag Replacer Tool</p>\n<p>Copyright (C) 2021  Jair</p>\n<p>This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.</p>\n<p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.</p>\n<p>You should have received a copy of the GNU General Public License along with this program.  If not, see https://www.gnu.org/licenses/.</p>\n</div>\n<html>");

        aboutDialog.pack();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Fallout 76 Flag Replacer");
        setIconImage(appIcon);

        materialPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Target Flag Material", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION));

        materialPreviewPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1), "Texture Preview", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.ABOVE_TOP));

        materialPreviewLabel.setBackground(new java.awt.Color(255, 255, 255));
        materialPreviewLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        materialPreviewLabel.setIcon(noFlagIcon);
        materialPreviewLabel.setToolTipText("");
        materialPreviewLabel.setMaximumSize(new java.awt.Dimension(128, 128));
        materialPreviewLabel.setMinimumSize(new java.awt.Dimension(128, 128));
        materialPreviewLabel.setName(""); // NOI18N

        javax.swing.GroupLayout materialPreviewPanelLayout = new javax.swing.GroupLayout(materialPreviewPanel);
        materialPreviewPanel.setLayout(materialPreviewPanelLayout);
        materialPreviewPanelLayout.setHorizontalGroup(
            materialPreviewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(materialPreviewLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        materialPreviewPanelLayout.setVerticalGroup(
            materialPreviewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, materialPreviewPanelLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(materialPreviewLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        materialTargetChooserList.setModel(flagPresetListModel);
        materialTargetChooserList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                materialTargetChooserListValueChanged(evt);
            }
        });
        materialTargetChooserScrollPane.setViewportView(materialTargetChooserList);

        javax.swing.GroupLayout materialPanelLayout = new javax.swing.GroupLayout(materialPanel);
        materialPanel.setLayout(materialPanelLayout);
        materialPanelLayout.setHorizontalGroup(
            materialPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(materialPanelLayout.createSequentialGroup()
                .addComponent(materialPreviewPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(materialTargetChooserScrollPane)
                .addContainerGap())
        );
        materialPanelLayout.setVerticalGroup(
            materialPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(materialPanelLayout.createSequentialGroup()
                .addComponent(materialPreviewPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, materialPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(materialTargetChooserScrollPane)
                .addContainerGap())
        );

        texturePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "New Flag Texture", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION));

        texturePreviewPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1), "Texture Preview", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.ABOVE_TOP));

        texturePreviewLabel.setBackground(new java.awt.Color(255, 255, 255));
        texturePreviewLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        texturePreviewLabel.setToolTipText("");
        texturePreviewLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        texturePreviewLabel.setMaximumSize(new java.awt.Dimension(128, 128));
        texturePreviewLabel.setMinimumSize(new java.awt.Dimension(128, 128));
        texturePreviewLabel.setName(""); // NOI18N

        javax.swing.GroupLayout texturePreviewPanelLayout = new javax.swing.GroupLayout(texturePreviewPanel);
        texturePreviewPanel.setLayout(texturePreviewPanelLayout);
        texturePreviewPanelLayout.setHorizontalGroup(
            texturePreviewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(texturePreviewLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        texturePreviewPanelLayout.setVerticalGroup(
            texturePreviewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, texturePreviewPanelLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(texturePreviewLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        textureFlagImageTextbox.setText("...");
        textureFlagImageTextbox.setEnabled(false);

        textureFlagImageButton.setText("Browse");
        textureFlagImageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textureFlagImageButtonActionPerformed(evt);
            }
        });

        textureWearTearPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Wear and Tear"));

        textureStainedCheckBox.setSelected(true);
        textureStainedCheckBox.setText("Stained");
        textureStainedCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textureCheckboxActionPerformed(evt);
            }
        });

        textureTornCheckBox.setText("Torn");
        textureTornCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textureCheckboxActionPerformed(evt);
            }
        });

        textureBlasted01CheckBox.setText("Blasted (Variant 1)");
        textureBlasted01CheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textureCheckboxActionPerformed(evt);
            }
        });

        textureBlasted02CheckBox.setText("Blasted (Variant 2)");
        textureBlasted02CheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textureCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout textureWearTearPanelLayout = new javax.swing.GroupLayout(textureWearTearPanel);
        textureWearTearPanel.setLayout(textureWearTearPanelLayout);
        textureWearTearPanelLayout.setHorizontalGroup(
            textureWearTearPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(textureWearTearPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(textureWearTearPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(textureWearTearPanelLayout.createSequentialGroup()
                        .addComponent(textureBlasted02CheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(textureTornCheckBox))
                    .addGroup(textureWearTearPanelLayout.createSequentialGroup()
                        .addComponent(textureBlasted01CheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(textureStainedCheckBox)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        textureWearTearPanelLayout.setVerticalGroup(
            textureWearTearPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(textureWearTearPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(textureWearTearPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(textureBlasted01CheckBox)
                    .addComponent(textureStainedCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(textureWearTearPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(textureTornCheckBox)
                    .addComponent(textureBlasted02CheckBox))
                .addGap(32, 32, 32))
        );

        textureOptionsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Flag Options"));

        textureFlagpoleConnectionCheckBox.setSelected(true);
        textureFlagpoleConnectionCheckBox.setText("Flagpole Connection");
        textureFlagpoleConnectionCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textureCheckboxActionPerformed(evt);
            }
        });

        textureFlipCheckBox.setText("Flip Backside");
        textureFlipCheckBox.setEnabled(false);
        textureFlipCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textureCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout textureOptionsPanelLayout = new javax.swing.GroupLayout(textureOptionsPanel);
        textureOptionsPanel.setLayout(textureOptionsPanelLayout);
        textureOptionsPanelLayout.setHorizontalGroup(
            textureOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(textureOptionsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(textureOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(textureFlagpoleConnectionCheckBox)
                    .addComponent(textureFlipCheckBox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        textureOptionsPanelLayout.setVerticalGroup(
            textureOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(textureOptionsPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(textureFlagpoleConnectionCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(textureFlipCheckBox))
        );

        javax.swing.GroupLayout texturePanelLayout = new javax.swing.GroupLayout(texturePanel);
        texturePanel.setLayout(texturePanelLayout);
        texturePanelLayout.setHorizontalGroup(
            texturePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(texturePanelLayout.createSequentialGroup()
                .addComponent(texturePreviewPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(texturePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(texturePanelLayout.createSequentialGroup()
                        .addComponent(textureFlagImageTextbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(textureFlagImageButton))
                    .addGroup(texturePanelLayout.createSequentialGroup()
                        .addComponent(textureOptionsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(textureWearTearPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        texturePanelLayout.setVerticalGroup(
            texturePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(texturePanelLayout.createSequentialGroup()
                .addGroup(texturePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(texturePanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(texturePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(textureFlagImageTextbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(textureFlagImageButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(texturePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(textureOptionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(textureWearTearPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 0, Short.MAX_VALUE)))
                    .addComponent(texturePreviewPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        aboutButton.setText("About");
        aboutButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutButtonActionPerformed(evt);
            }
        });

        saveButton.setText("Save");
        saveButton.setEnabled(false);
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        fileOpenCheckBox.setSelected(true);
        fileOpenCheckBox.setText("Open saved folder in explorer.");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(texturePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(materialPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(fileOpenCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(aboutButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(saveButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(materialPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(texturePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(saveButton)
                    .addComponent(aboutButton)
                    .addComponent(fileOpenCheckBox))
                .addContainerGap())
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void textureFlagImageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_textureFlagImageButtonActionPerformed
        openFlagImage();
    }//GEN-LAST:event_textureFlagImageButtonActionPerformed

    private void textureCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_textureCheckboxActionPerformed
        repaintCustomFlag();
    }//GEN-LAST:event_textureCheckboxActionPerformed

    private void materialTargetChooserListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_materialTargetChooserListValueChanged
        refreshMaterialChoice();
    }//GEN-LAST:event_materialTargetChooserListValueChanged

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        saveFlagFiles();
    }//GEN-LAST:event_saveButtonActionPerformed

    private void aboutButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutButtonActionPerformed
        aboutDialog.setLocationRelativeTo(this);
        aboutDialog.setVisible(true);
    }//GEN-LAST:event_aboutButtonActionPerformed

    private void doneButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doneButtonActionPerformed
        aboutDialog.setVisible(false);
    }//GEN-LAST:event_doneButtonActionPerformed

    private void githubButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_githubButtonActionPerformed
        try {
            Desktop.getDesktop().browse(new URI("https://github.com/EpicestGamer/76FlagReplacer"));
        } catch (IOException | URISyntaxException e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to open Github link. Apologies for the inconvenience.\n\nException Message: " + e.getLocalizedMessage(),
                    "Fallout 76 Flag Replacer",
                    JOptionPane.ERROR_MESSAGE);
            Logger.getLogger(FlagReplacer.class.getName()).log(Level.WARNING, "Could not open Github link", e);
        }
    }//GEN-LAST:event_githubButtonActionPerformed

    private void nexusButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nexusButtonActionPerformed
        try {
            Desktop.getDesktop().browse(new URI("https://www.nexusmods.com/fallout76/mods/932"));
        } catch (IOException | URISyntaxException e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to open Nexus Mods link. Apologies for the inconvenience.\n\nException Message: " + e.getLocalizedMessage(),
                    "Fallout 76 Flag Replacer",
                    JOptionPane.ERROR_MESSAGE);
            Logger.getLogger(FlagReplacer.class.getName()).log(Level.WARNING, "Could not open Nexus Mods link", e);
        }
    }//GEN-LAST:event_nexusButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JDialog aboutDialog;
    private javax.swing.JCheckBox fileOpenCheckBox;
    private javax.swing.JLabel materialPreviewLabel;
    private javax.swing.JList<FlagPreset> materialTargetChooserList;
    private javax.swing.JButton saveButton;
    private javax.swing.JFileChooser saveFileChooser;
    private javax.swing.JCheckBox textureBlasted01CheckBox;
    private javax.swing.JCheckBox textureBlasted02CheckBox;
    private javax.swing.JFileChooser textureFlagImageFileChooser;
    private javax.swing.JTextField textureFlagImageTextbox;
    private javax.swing.JCheckBox textureFlagpoleConnectionCheckBox;
    private javax.swing.JCheckBox textureFlipCheckBox;
    private javax.swing.JLabel texturePreviewLabel;
    private javax.swing.JCheckBox textureStainedCheckBox;
    private javax.swing.JCheckBox textureTornCheckBox;
    // End of variables declaration//GEN-END:variables
}
