/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import ij.IJ;
import ij.io.DirectoryChooser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultTreeModel;

/**
 *
 * @author dwcai
 * 
 * 
 * Ver 1.0
 */
public class Batch_Process extends javax.swing.JFrame {

    /**
     * Creates new form nTracer_Batch_Analysis
     */
    public Batch_Process() {
        IO = new ntIO();
        // set up windows look and feel
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (ClassNotFoundException ex) {
        } catch (IllegalAccessException ex) {
        } catch (InstantiationException ex) {
        } catch (UnsupportedLookAndFeelException ex) {
        }
        initComponents();
        this.setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fileOrFolderSWC_buttonGroup = new javax.swing.ButtonGroup();
        fileOrFolderSynapse_buttonGroup = new javax.swing.ButtonGroup();
        fileOrFolderConnection_buttonGroup = new javax.swing.ButtonGroup();
        fileOrFolderSkeleton_buttonGroup = new javax.swing.ButtonGroup();
        jTabbedPane = new javax.swing.JTabbedPane();
        data_jPanel = new javax.swing.JPanel();
        exportSWC_jPanel = new javax.swing.JPanel();
        exportSWC_jButton = new javax.swing.JButton();
        fileToSWC_jRadioButton = new javax.swing.JRadioButton();
        folderToSWC_jRadioButton = new javax.swing.JRadioButton();
        subfolderToSWC_jCheckBox = new javax.swing.JCheckBox();
        extendedSWC_jCheckBox = new javax.swing.JCheckBox();
        exportSpine_jCheckBox = new javax.swing.JCheckBox();
        exportSynapse_jPanel = new javax.swing.JPanel();
        exportSynapse_jButton = new javax.swing.JButton();
        fileToSynapse_jRadioButton = new javax.swing.JRadioButton();
        folderToSynapse_jRadioButton = new javax.swing.JRadioButton();
        subfolderToSynapse_jCheckBox = new javax.swing.JCheckBox();
        analysis_jPanel = new javax.swing.JPanel();
        exportNeuronConnections_jPanel = new javax.swing.JPanel();
        exportConnection_jButton = new javax.swing.JButton();
        fileToConnection_jRadioButton = new javax.swing.JRadioButton();
        folderToConnection_jRadioButton = new javax.swing.JRadioButton();
        subfolderToConnection_jCheckBox = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("nTracer Batch Process 1.0");

        exportSWC_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 0, 51)), "SWC format", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(204, 0, 51))); // NOI18N
        exportSWC_jPanel.setToolTipText("");

        exportSWC_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        exportSWC_jButton.setForeground(new java.awt.Color(204, 0, 51));
        exportSWC_jButton.setText("Export");
        exportSWC_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportSWC_jButtonActionPerformed(evt);
            }
        });

        fileOrFolderSWC_buttonGroup.add(fileToSWC_jRadioButton);
        fileToSWC_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        fileToSWC_jRadioButton.setSelected(true);
        fileToSWC_jRadioButton.setText("Single File");
        fileToSWC_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileToSWC_jRadioButtonActionPerformed(evt);
            }
        });

        fileOrFolderSWC_buttonGroup.add(folderToSWC_jRadioButton);
        folderToSWC_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        folderToSWC_jRadioButton.setText("Folder");
        folderToSWC_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                folderToSWC_jRadioButtonActionPerformed(evt);
            }
        });

        subfolderToSWC_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        subfolderToSWC_jCheckBox.setText("subfolder");
        subfolderToSWC_jCheckBox.setEnabled(false);

        extendedSWC_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        extendedSWC_jCheckBox.setText("Extended Format");

        exportSpine_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        exportSpine_jCheckBox.setText("Spine");

        javax.swing.GroupLayout exportSWC_jPanelLayout = new javax.swing.GroupLayout(exportSWC_jPanel);
        exportSWC_jPanel.setLayout(exportSWC_jPanelLayout);
        exportSWC_jPanelLayout.setHorizontalGroup(
            exportSWC_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(exportSWC_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(exportSWC_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(exportSWC_jPanelLayout.createSequentialGroup()
                        .addComponent(folderToSWC_jRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(subfolderToSWC_jCheckBox))
                    .addGroup(exportSWC_jPanelLayout.createSequentialGroup()
                        .addGroup(exportSWC_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(exportSWC_jPanelLayout.createSequentialGroup()
                                .addComponent(exportSpine_jCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(exportSWC_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))
                            .addGroup(exportSWC_jPanelLayout.createSequentialGroup()
                                .addGroup(exportSWC_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(fileToSWC_jRadioButton)
                                    .addComponent(extendedSWC_jCheckBox))
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addGap(6, 6, 6))))
        );
        exportSWC_jPanelLayout.setVerticalGroup(
            exportSWC_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, exportSWC_jPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(fileToSWC_jRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(exportSWC_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(folderToSWC_jRadioButton)
                    .addComponent(subfolderToSWC_jCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(extendedSWC_jCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(exportSWC_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exportSpine_jCheckBox)
                    .addComponent(exportSWC_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        exportSynapse_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 153, 153)), "Synapse Coordinates", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(0, 153, 153))); // NOI18N
        exportSynapse_jPanel.setToolTipText("");

        exportSynapse_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        exportSynapse_jButton.setForeground(new java.awt.Color(0, 153, 153));
        exportSynapse_jButton.setText("Export");
        exportSynapse_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportSynapse_jButtonActionPerformed(evt);
            }
        });

        fileOrFolderSynapse_buttonGroup.add(fileToSynapse_jRadioButton);
        fileToSynapse_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        fileToSynapse_jRadioButton.setSelected(true);
        fileToSynapse_jRadioButton.setText("Single File");
        fileToSynapse_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileToSynapse_jRadioButtonActionPerformed(evt);
            }
        });

        fileOrFolderSynapse_buttonGroup.add(folderToSynapse_jRadioButton);
        folderToSynapse_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        folderToSynapse_jRadioButton.setText("Folder");
        folderToSynapse_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                folderToSynapse_jRadioButtonActionPerformed(evt);
            }
        });

        subfolderToSynapse_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        subfolderToSynapse_jCheckBox.setText("subfolder");
        subfolderToSynapse_jCheckBox.setEnabled(false);

        javax.swing.GroupLayout exportSynapse_jPanelLayout = new javax.swing.GroupLayout(exportSynapse_jPanel);
        exportSynapse_jPanel.setLayout(exportSynapse_jPanelLayout);
        exportSynapse_jPanelLayout.setHorizontalGroup(
            exportSynapse_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(exportSynapse_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(exportSynapse_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(fileToSynapse_jRadioButton)
                    .addComponent(folderToSynapse_jRadioButton)
                    .addComponent(subfolderToSynapse_jCheckBox)
                    .addComponent(exportSynapse_jButton))
                .addContainerGap(35, Short.MAX_VALUE))
        );
        exportSynapse_jPanelLayout.setVerticalGroup(
            exportSynapse_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, exportSynapse_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fileToSynapse_jRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(folderToSynapse_jRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(subfolderToSynapse_jCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exportSynapse_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout data_jPanelLayout = new javax.swing.GroupLayout(data_jPanel);
        data_jPanel.setLayout(data_jPanelLayout);
        data_jPanelLayout.setHorizontalGroup(
            data_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(data_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(exportSWC_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exportSynapse_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        data_jPanelLayout.setVerticalGroup(
            data_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(data_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(data_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(exportSWC_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(exportSynapse_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane.addTab("Export Data", data_jPanel);

        exportNeuronConnections_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 153, 153)), "Neuron Connections", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(0, 153, 153))); // NOI18N
        exportNeuronConnections_jPanel.setToolTipText("");

        exportConnection_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        exportConnection_jButton.setForeground(new java.awt.Color(0, 153, 153));
        exportConnection_jButton.setText("Export");
        exportConnection_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportConnection_jButtonActionPerformed(evt);
            }
        });

        fileOrFolderConnection_buttonGroup.add(fileToConnection_jRadioButton);
        fileToConnection_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        fileToConnection_jRadioButton.setSelected(true);
        fileToConnection_jRadioButton.setText("Single File");
        fileToConnection_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileToConnection_jRadioButtonActionPerformed(evt);
            }
        });

        fileOrFolderConnection_buttonGroup.add(folderToConnection_jRadioButton);
        folderToConnection_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        folderToConnection_jRadioButton.setText("Folder");
        folderToConnection_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                folderToConnection_jRadioButtonActionPerformed(evt);
            }
        });

        subfolderToConnection_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        subfolderToConnection_jCheckBox.setText("subfolder");
        subfolderToConnection_jCheckBox.setEnabled(false);

        javax.swing.GroupLayout exportNeuronConnections_jPanelLayout = new javax.swing.GroupLayout(exportNeuronConnections_jPanel);
        exportNeuronConnections_jPanel.setLayout(exportNeuronConnections_jPanelLayout);
        exportNeuronConnections_jPanelLayout.setHorizontalGroup(
            exportNeuronConnections_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(exportNeuronConnections_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(exportNeuronConnections_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(exportNeuronConnections_jPanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(exportConnection_jButton))
                    .addGroup(exportNeuronConnections_jPanelLayout.createSequentialGroup()
                        .addGroup(exportNeuronConnections_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(fileToConnection_jRadioButton)
                            .addGroup(exportNeuronConnections_jPanelLayout.createSequentialGroup()
                                .addComponent(folderToConnection_jRadioButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(subfolderToConnection_jCheckBox)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        exportNeuronConnections_jPanelLayout.setVerticalGroup(
            exportNeuronConnections_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, exportNeuronConnections_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fileToConnection_jRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(exportNeuronConnections_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(folderToConnection_jRadioButton)
                    .addComponent(subfolderToConnection_jCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 26, Short.MAX_VALUE)
                .addComponent(exportConnection_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout analysis_jPanelLayout = new javax.swing.GroupLayout(analysis_jPanel);
        analysis_jPanel.setLayout(analysis_jPanelLayout);
        analysis_jPanelLayout.setHorizontalGroup(
            analysis_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(analysis_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(exportNeuronConnections_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(169, Short.MAX_VALUE))
        );
        analysis_jPanelLayout.setVerticalGroup(
            analysis_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, analysis_jPanelLayout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addComponent(exportNeuronConnections_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane.addTab("Analysis", analysis_jPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 352, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void folderToConnection_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_folderToConnection_jRadioButtonActionPerformed
        subfolderToConnection_jCheckBox.setEnabled(true);
    }//GEN-LAST:event_folderToConnection_jRadioButtonActionPerformed

    private void fileToConnection_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileToConnection_jRadioButtonActionPerformed
        subfolderToConnection_jCheckBox.setEnabled(false);
    }//GEN-LAST:event_fileToConnection_jRadioButtonActionPerformed

    private void exportConnection_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportConnection_jButtonActionPerformed
        exportConnectionFromTracingResult();
    }//GEN-LAST:event_exportConnection_jButtonActionPerformed

    private void folderToSynapse_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_folderToSynapse_jRadioButtonActionPerformed
        subfolderToSynapse_jCheckBox.setEnabled(true);
    }//GEN-LAST:event_folderToSynapse_jRadioButtonActionPerformed

    private void fileToSynapse_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileToSynapse_jRadioButtonActionPerformed
        subfolderToSynapse_jCheckBox.setEnabled(false);
    }//GEN-LAST:event_fileToSynapse_jRadioButtonActionPerformed

    private void exportSynapse_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportSynapse_jButtonActionPerformed
        exportSynapseFromTracingResult();
    }//GEN-LAST:event_exportSynapse_jButtonActionPerformed

    private void folderToSWC_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_folderToSWC_jRadioButtonActionPerformed
        subfolderToSWC_jCheckBox.setEnabled(true);
    }//GEN-LAST:event_folderToSWC_jRadioButtonActionPerformed

    private void fileToSWC_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileToSWC_jRadioButtonActionPerformed
        subfolderToSWC_jCheckBox.setEnabled(false);
    }//GEN-LAST:event_fileToSWC_jRadioButtonActionPerformed

    private void exportSWC_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportSWC_jButtonActionPerformed
        exportSWCfromTracingResult();
    }//GEN-LAST:event_exportSWC_jButtonActionPerformed
    private void exportSWCfromTracingResult(){
        if (fileToSWC_jRadioButton.isSelected()){
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(IJ.getDirectory("current") + "/"));
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (f.isDirectory()) {
                        return true;
                    }
                    final String name = f.getName();
                    return name.endsWith(".zip");
                }

                @Override
                public String getDescription() {
                    return "*.zip";
                }
            });
            int returnVal = fileChooser.showOpenDialog(this);
            if (returnVal == JFileChooser.CANCEL_OPTION) {
                return;
            }
            File selectedFile = fileChooser.getSelectedFile();
            String fileName = fileChooser.getName(selectedFile);
            if (fileName == null) {
                return;
            }
            String selectedDirectory = selectedFile.getParent()+"/";
            exportSWCfromFile(selectedFile, selectedDirectory, !extendedSWC_jCheckBox.isSelected(), exportSpine_jCheckBox.isSelected());
        } else if (folderToSWC_jRadioButton.isSelected()) {
            DirectoryChooser dc = new DirectoryChooser("Choose directory ...");
            String rootDirectory = dc.getDirectory();
            if (rootDirectory == null) {
                return;
            }
            File selectedFolder = new File(rootDirectory);
            IJ.log("Export SWC from folder: " + rootDirectory + "\n");

            try {
                exportSWCfromFolder(selectedFolder, rootDirectory, !extendedSWC_jCheckBox.isSelected(), 
                        subfolderToSWC_jCheckBox.isSelected(), exportSpine_jCheckBox.isSelected());
            } catch (IOException e) {
                IJ.log(e.getMessage());
            }
        }
    }
    private void exportSWCfromFile(File selectedFile, String selectedDirectory, boolean stdSWC, boolean exportSpine) {
        String dataFileName = selectedFile.getName();
        String prefixName = dataFileName.substring(0, dataFileName.length() - 4);
        IJ.log(selectedFile.getAbsolutePath());
        try {
            ZipFile zipFile = new ZipFile(selectedFile.getAbsolutePath());
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            ZipEntry entry = null;
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                if (entry.toString().endsWith("-data.txt")) {
                    break;
                }
            }
            if (entry != null) {
                InputStream parameterAndNeuronIS = zipFile.getInputStream(entry);
                nTracer_.xyzResolutions[0] = 0;
                nTracer_.xyzResolutions[1] = 0;
                nTracer_.xyzResolutions[2] = 0;
                DefaultTreeModel[] treeModels = IO.loadResolutionsAndNeuronTreeModels(parameterAndNeuronIS);
                ntNeuronNode exportRootAllSomaNode = (ntNeuronNode) (treeModels[0].getRoot());
                ntNeuronNode exportRootNeuronNode = (ntNeuronNode) (treeModels[1].getRoot());
                ntNeuronNode exportRootSpineNode = (ntNeuronNode) (treeModels[2].getRoot());
                ArrayList<String> allNeuronNumbers = new ArrayList<String>();
                for (int i = 0; i < exportRootAllSomaNode.getChildCount(); i++) {
                    allNeuronNumbers.add(((ntNeuronNode) exportRootAllSomaNode.getChildAt(i)).getNeuronNumber());
                }
                IO.exportSelectedNeuronSWC(exportRootNeuronNode, exportRootAllSomaNode, exportRootSpineNode, allNeuronNumbers, 
                        selectedDirectory, prefixName, nTracer_.xyzResolutions, stdSWC, exportSpine);
                IJ.log("Exported SWC!" + "\n");
            }
        } catch (IOException e) {
            IJ.log("Fail: " + e.getMessage() + "\n");
        }
    }
    private void exportSWCfromFolder(File selectedFolder, String rootDirectory,
            boolean stdSWC, boolean exportSubfolder, boolean exportSpine) throws IOException {
        File multFiles[] = selectedFolder.listFiles();
        String selectedDirectory = selectedFolder.getAbsolutePath() + "\\";
        ArrayList<File> zipFileList = new ArrayList<File>();
        ArrayList<File> subfolderList = new ArrayList<File>();
        for (File file : multFiles) {
            if (file.isFile()) {
                if (file.getName().endsWith(".zip")) {
                    zipFileList.add(file);
                }
            } else if (exportSubfolder) {
                subfolderList.add(file);
            }
        }

        for (File file : zipFileList) {
            exportSWCfromFile(file, selectedDirectory, stdSWC, exportSpine);
        }

        if (exportSubfolder) {
            for (File file : subfolderList) {
                IJ.log("");
                exportSWCfromFolder(file, rootDirectory, stdSWC, exportSubfolder, exportSpine);
            }
        }
    }
        private void exportSynapseFromTracingResult(){
        if (fileToSynapse_jRadioButton.isSelected()){
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(IJ.getDirectory("current") + "/"));
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (f.isDirectory()) {
                        return true;
                    }
                    final String name = f.getName();
                    return name.endsWith(".zip");
                }

                @Override
                public String getDescription() {
                    return "*.zip";
                }
            });
            int returnVal = fileChooser.showOpenDialog(this);
            if (returnVal == JFileChooser.CANCEL_OPTION) {
                return;
            }
            File selectedFile = fileChooser.getSelectedFile();
            String fileName = fileChooser.getName(selectedFile);
            if (fileName == null) {
                return;
            }
            String selectedDirectory = selectedFile.getParent()+"/";
            exportSynapseFromFile(selectedFile, selectedDirectory);
        } else if (folderToSynapse_jRadioButton.isSelected()) {
            DirectoryChooser dc = new DirectoryChooser("Choose directory ...");
            String rootDirectory = dc.getDirectory();
            if (rootDirectory == null) {
                return;
            }
            File selectedFolder = new File(rootDirectory);
            IJ.log("Export Synapse from folder: " + rootDirectory + "\n");

            try {
                exportSynapseFromFolder(selectedFolder, rootDirectory, subfolderToSynapse_jCheckBox.isSelected());
            } catch (IOException e) {
                IJ.log(e.getMessage());
            }
        }
    }
    private void exportSynapseFromFile(File selectedFile, String selectedDirectory) {
        String dataFileName = selectedFile.getName();
        String prefixName = dataFileName.substring(0, dataFileName.length() - 4);
        IJ.log(selectedFile.getAbsolutePath());
        try {
            ZipFile zipFile = new ZipFile(selectedFile.getAbsolutePath());
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            ZipEntry entry = null;
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                if (entry.toString().endsWith("-data.txt")) {
                    break;
                }
            }
            if (entry != null) {
                InputStream parameterAndNeuronIS = zipFile.getInputStream(entry);
                nTracer_.xyzResolutions[0] = 0;
                nTracer_.xyzResolutions[1] = 0;
                nTracer_.xyzResolutions[2] = 0;
                DefaultTreeModel[] treeModels = IO.loadResolutionsAndNeuronTreeModels(parameterAndNeuronIS);
                ntNeuronNode exportRootAllSomaNode = (ntNeuronNode) (treeModels[0].getRoot());
                ntNeuronNode exportRootNeuronNode = (ntNeuronNode) (treeModels[1].getRoot());
                IO.exportAllNeuronSynapse(exportRootNeuronNode, exportRootAllSomaNode, 
                        selectedDirectory, prefixName, nTracer_.xyzResolutions);
                IJ.log("Exported Synapse!" + "\n");
            }
        } catch (IOException e) {
            IJ.log("Fail: " + e.getMessage() + "\n");
        }
    }
    private void exportSynapseFromFolder(File selectedFolder, String rootDirectory,
            boolean exportSubfolder) throws IOException {
        File multFiles[] = selectedFolder.listFiles();
        String selectedDirectory = selectedFolder.getAbsolutePath() + "\\";
        ArrayList<File> zipFileList = new ArrayList<File>();
        ArrayList<File> subfolderList = new ArrayList<File>();
        for (File file : multFiles) {
            if (file.isFile()) {
                if (file.getName().endsWith(".zip")) {
                    zipFileList.add(file);
                }
            } else if (exportSubfolder) {
                subfolderList.add(file);
            }
        }

        for (File file : zipFileList) {
            exportSynapseFromFile(file, selectedDirectory);
        }

        if (exportSubfolder) {
            for (File file : subfolderList) {
                IJ.log("");
                exportSynapseFromFolder(file, rootDirectory, exportSubfolder);
            }
        }
    }
     private void exportConnectionFromTracingResult(){
        if (fileToConnection_jRadioButton.isSelected()){
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(IJ.getDirectory("current") + "/"));
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (f.isDirectory()) {
                        return true;
                    }
                    final String name = f.getName();
                    return name.endsWith(".zip");
                }

                @Override
                public String getDescription() {
                    return "*.zip";
                }
            });
            int returnVal = fileChooser.showOpenDialog(this);
            if (returnVal == JFileChooser.CANCEL_OPTION) {
                return;
            }
            File selectedFile = fileChooser.getSelectedFile();
            String fileName = fileChooser.getName(selectedFile);
            if (fileName == null) {
                return;
            }
            String selectedDirectory = selectedFile.getParent()+"/";
            exportConnectionFromFile(selectedFile, selectedDirectory);
        } else if (folderToConnection_jRadioButton.isSelected()) {
            DirectoryChooser dc = new DirectoryChooser("Choose directory ...");
            String rootDirectory = dc.getDirectory();
            if (rootDirectory == null) {
                return;
            }
            File selectedFolder = new File(rootDirectory);
            IJ.log("Export Connection from folder: " + rootDirectory + "\n");

            try {
                exportConnectionFromFolder(selectedFolder, rootDirectory, subfolderToConnection_jCheckBox.isSelected());
            } catch (IOException e) {
                IJ.log(e.getMessage());
            }
        }
    }
    private void exportConnectionFromFile(File selectedFile, String selectedDirectory) {
        String dataFileName = selectedFile.getName();
        String prefixName = dataFileName.substring(0, dataFileName.length() - 4);
        IJ.log(selectedFile.getAbsolutePath());
        try {
            ZipFile zipFile = new ZipFile(selectedFile.getAbsolutePath());
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            ZipEntry entry = null;
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                if (entry.toString().endsWith("-data.txt")) {
                    break;
                }
            }
            if (entry != null) {
                InputStream parameterAndNeuronIS = zipFile.getInputStream(entry);
                nTracer_.xyzResolutions[0] = 0;
                nTracer_.xyzResolutions[1] = 0;
                nTracer_.xyzResolutions[2] = 0;
                DefaultTreeModel[] treeModels = IO.loadResolutionsAndNeuronTreeModels(parameterAndNeuronIS);
                ntNeuronNode exportRootAllSomaNode = (ntNeuronNode) (treeModels[0].getRoot());
                ntNeuronNode exportRootNeuronNode = (ntNeuronNode) (treeModels[1].getRoot());
                IO.exportAllNeuronConnection(exportRootNeuronNode, exportRootAllSomaNode, 
                        selectedDirectory, prefixName);
                IJ.log("Exported Connection!" + "\n");
            }
        } catch (IOException e) {
            IJ.log("Fail: " + e.getMessage() + "\n");
        }
    }
    private void exportConnectionFromFolder(File selectedFolder, String rootDirectory,
            boolean exportSubfolder) throws IOException {
        File multFiles[] = selectedFolder.listFiles();
        String selectedDirectory = selectedFolder.getAbsolutePath() + "\\";
        ArrayList<File> zipFileList = new ArrayList<File>();
        ArrayList<File> subfolderList = new ArrayList<File>();
        for (File file : multFiles) {
            if (file.isFile()) {
                if (file.getName().endsWith(".zip")) {
                    zipFileList.add(file);
                }
            } else if (exportSubfolder) {
                subfolderList.add(file);
            }
        }

        for (File file : zipFileList) {
            exportConnectionFromFile(file, selectedDirectory);
        }

        if (exportSubfolder) {
            for (File file : subfolderList) {
                IJ.log("");
                exportConnectionFromFolder(file, rootDirectory, exportSubfolder);
            }
        }
    }
 
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new Batch_Process().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel analysis_jPanel;
    private javax.swing.JPanel data_jPanel;
    private javax.swing.JButton exportConnection_jButton;
    private javax.swing.JPanel exportNeuronConnections_jPanel;
    private javax.swing.JButton exportSWC_jButton;
    private javax.swing.JPanel exportSWC_jPanel;
    private javax.swing.JCheckBox exportSpine_jCheckBox;
    private javax.swing.JButton exportSynapse_jButton;
    private javax.swing.JPanel exportSynapse_jPanel;
    private javax.swing.JCheckBox extendedSWC_jCheckBox;
    private javax.swing.ButtonGroup fileOrFolderConnection_buttonGroup;
    private javax.swing.ButtonGroup fileOrFolderSWC_buttonGroup;
    private javax.swing.ButtonGroup fileOrFolderSkeleton_buttonGroup;
    private javax.swing.ButtonGroup fileOrFolderSynapse_buttonGroup;
    private javax.swing.JRadioButton fileToConnection_jRadioButton;
    private javax.swing.JRadioButton fileToSWC_jRadioButton;
    private javax.swing.JRadioButton fileToSynapse_jRadioButton;
    private javax.swing.JRadioButton folderToConnection_jRadioButton;
    private javax.swing.JRadioButton folderToSWC_jRadioButton;
    private javax.swing.JRadioButton folderToSynapse_jRadioButton;
    private javax.swing.JTabbedPane jTabbedPane;
    private javax.swing.JCheckBox subfolderToConnection_jCheckBox;
    private javax.swing.JCheckBox subfolderToSWC_jCheckBox;
    private javax.swing.JCheckBox subfolderToSynapse_jCheckBox;
    // End of variables declaration//GEN-END:variables
    
    private final ntIO IO;
}