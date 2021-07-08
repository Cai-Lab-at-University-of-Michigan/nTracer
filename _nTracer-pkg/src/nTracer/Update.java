/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import ij.gui.Overlay;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import javax.swing.table.DefaultTableModel;
import static nTracer.nTracer_.imp;
import static nTracer.nTracer_.rootAllSomaNode;
import static nTracer.nTracer_.rootNeuronNode;

/**
 * Contains several functions that deal with updating important aspects of the program 
 * 
 * @author Dawen Cai, Devang Chaudhary
 */
public class Update {
    
    private nTracer_ nTracer;
     
    protected Update(nTracer_ nTracer) {
        this.nTracer = nTracer;
    }
    
    /**
     * updates the point table
     * @param dataPoints 
     */
    
    protected void updatePointTable(ArrayList<String[]> dataPoints) {
        Object[][] pointData = nTracer.dataHandler.getPointTableData(dataPoints);
        nTracer.pointTableModel = new DefaultTableModel(pointData, nTracer.pointColumnNames) {
            Class[] types = new Class[]{
                java.lang.String.class, java.lang.Float.class,
                java.lang.Float.class, java.lang.Float.class,
                java.lang.Float.class, java.lang.Integer.class,
                java.lang.String.class
            };
            boolean[] canEdit = new boolean[]{
                false, false, false, false, false, false, false
            };

            @Override
            public Class getColumnClass(int columnIndex) {
                return types[columnIndex];
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false; //canEdit[columnIndex];
            }
        };
//        pointTableModelListener = new ntPointTableModelListener();
//        pointTableModel.addTableModelListener(pointTableModelListener);
        nTracer.pointTable_jTable.setModel(nTracer.pointTableModel);
    }
    
    /**
     * updates position info based on MouseEvent e
     * @param e 
     */
    protected void updatePositionInfo(MouseEvent e) {
        nTracer.crossX = nTracer.cns.offScreenX(e.getX());
        nTracer.crossY = nTracer.cns.offScreenY(e.getY());
        nTracer.crossZ = imp.getZ();
        //updatePositionIntColor();
    }
    
    /**
     * updates neuron tree model and all soma tree model
     */
    
    protected void updateTrees() {
        nTracer.neuronTreeModel.nodeStructureChanged(rootNeuronNode);
        nTracer.allSomaTreeModel.nodeStructureChanged(rootAllSomaNode);
    }
    
    
    
    /**
     * updates the info so that the info jLabel's text is set to messega
     * @param messega 
     */
    
    protected void updateInfo(String messega) {
        // update information
        nTracer.info_jLabel.setText(messega);
    }
    
    
    /**
     * updates the display
     */
    
    protected void updateDisplay() {
        //IJ.log("update");
        if (imp != null) {
            //long startTime = System.currentTimeMillis();
            nTracer.allNeuronTraceOL.clear();
            nTracer.allNeuronNameOL.clear();
            nTracer.allNeuronSynapseOL.clear();
            nTracer.allNeuronConnectedOL.clear();
            nTracer.allNeuronSpineOL.clear();
            nTracer.selectedNeuronTraceOL.clear();
            nTracer.selectedNeuronNameOL.clear();
            nTracer. selectedNeuronSynapseOL.clear();
            nTracer.selectedNeuronConnectedOL.clear();
            nTracer.selectedNeuronSpineOL.clear();
            nTracer.selectedArborTraceOL.clear();
            nTracer.selectedArborNameOL.clear();
            nTracer.selectedArborSynapseOL.clear();
            nTracer.selectedArborConnectedOL.clear();
            nTracer.selectedArborSpineOL.clear();
            nTracer.selectedBranchTraceOL.clear();
            nTracer.selectedBranchNameOL.clear();
            nTracer.selectedBranchSynapseOL.clear();
            nTracer.selectedBranchConnectedOL.clear();
            nTracer.selectedBranchSpineOL.clear();
            nTracer.allSomaSynapseOL.clear();
            nTracer.allSomaConnectedOL.clear();
            nTracer.selectedSomaSynapseOL.clear();
            nTracer.selectedSomaConnectedOL.clear();

            nTracer.allNeuronTraceOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.allNeuronNameOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.allNeuronSpineOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedNeuronTraceOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedNeuronNameOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedNeuronSpineOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedArborTraceOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedArborNameOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedArborSpineOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedBranchTraceOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedBranchNameOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedBranchSpineOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.allSomaTraceOL = new Overlay[nTracer.impNSlice];
            nTracer.allSomaNameOL = new Overlay[nTracer.impNSlice];
            nTracer.allSomaSpineOLextPt = new Overlay[nTracer.impNSlice];
            nTracer.selectedSomaTraceOL = new Overlay[nTracer.impNSlice];
            nTracer.selectedSomaNameOL = new Overlay[nTracer.impNSlice];
            nTracer.selectedSomaSpineOLextPt = new Overlay[nTracer.impNSlice];

            for (int i = 0; i < nTracer.impNSlice; i++) {
                nTracer.allNeuronTraceOLextPt[i] = new Overlay();
                nTracer.allNeuronNameOLextPt[i] = new Overlay();
                nTracer.allNeuronSpineOLextPt[i] = new Overlay();
                nTracer.selectedNeuronTraceOLextPt[i] = new Overlay();
                nTracer.selectedNeuronNameOLextPt[i] = new Overlay();
                nTracer.selectedNeuronSpineOLextPt[i] = new Overlay();
                nTracer.selectedArborTraceOLextPt[i] = new Overlay();
                nTracer.selectedArborNameOLextPt[i] = new Overlay();
                nTracer.selectedArborSpineOLextPt[i] = new Overlay();
                nTracer.selectedBranchTraceOLextPt[i] = new Overlay();
                nTracer.selectedBranchNameOLextPt[i] = new Overlay();
                nTracer.selectedBranchSpineOLextPt[i] = new Overlay();
                nTracer.allSomaTraceOL[i] = new Overlay();
                nTracer.allSomaNameOL[i] = new Overlay();
                nTracer.allSomaSpineOLextPt[i] = new Overlay();
                nTracer.selectedSomaTraceOL[i] = new Overlay();
                nTracer.selectedSomaNameOL[i] = new Overlay();
                nTracer.selectedSomaSpineOLextPt[i] = new Overlay();
            }

            // overlay all neurons            
            if (nTracer.overlayAllNeuron_jCheckBox.isSelected()
                    || nTracer.overlayAllSynapse_jCheckBox.isSelected()) {
                if (nTracer.overlayAllPoints_jCheckBox.isSelected()) {
                    nTracer.getAllNeuronAndNameOLMultiThread(nTracer.allNeuronTraceOL,
                            nTracer.allNeuronNameOL, nTracer.allNeuronSynapseOL, nTracer.allNeuronConnectedOL, nTracer.allNeuronSpineOL);
                } else {
                    nTracer.getAllNeuronAndNameOLextPtMultiThread(nTracer.allNeuronTraceOLextPt,
                            nTracer.allNeuronNameOLextPt, nTracer.allNeuronSynapseOL, nTracer.allNeuronConnectedOL, nTracer.allNeuronSpineOLextPt, nTracer.extendDisplayPoints);
                    for (int j = 0; j < nTracer.impNSlice; j++) {
                        if (nTracer.allNeuronTraceOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.allNeuronTraceOLextPt[j].size(); n++) {
                                nTracer.allNeuronTraceOL.add(nTracer.allNeuronTraceOLextPt[j].get(n));
                            }
                        }
                        if (nTracer.allNeuronNameOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.allNeuronNameOLextPt[j].size(); n++) {
                                nTracer.allNeuronNameOL.add(nTracer.allNeuronNameOLextPt[j].get(n));
                            }
                        }
                        if (nTracer.allNeuronSpineOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.allNeuronSpineOLextPt[j].size(); n++) {
                               nTracer. allNeuronSpineOL.add(nTracer.allNeuronSpineOLextPt[j].get(n));
                            }
                        }
                    }
                }
            }

            // overlay selected neuron(s)
            if (nTracer.overlaySelectedNeuron_jCheckBox.isSelected()
                    || nTracer.overlaySelectedSynapse_jCheckBox.isSelected()) {
                if (nTracer.overlayAllSelectedPoints_jCheckBox.isSelected()) {
                    nTracer.getSelectedNeuronAndNameOLMultiThread(nTracer.selectedNeuronTraceOL,
                            nTracer.selectedNeuronNameOL, nTracer.selectedNeuronSynapseOL, nTracer.selectedNeuronConnectedOL, nTracer.selectedNeuronSpineOL);
                } else {
                    nTracer.getSelectedNeuronAndNameOLextPtMultiThread(nTracer.selectedNeuronTraceOLextPt,
                            nTracer.selectedNeuronNameOLextPt, nTracer.selectedNeuronSynapseOL, nTracer.selectedNeuronConnectedOL, nTracer.selectedNeuronSpineOLextPt, nTracer.extendDisplayPoints);
                    for (int j = 0; j < nTracer.impNSlice; j++) {
                        if (nTracer.selectedNeuronTraceOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedNeuronTraceOLextPt[j].size(); n++) {
                                nTracer.selectedNeuronTraceOL.add(nTracer.selectedNeuronTraceOLextPt[j].get(n));
                            }
                        }
                        if (nTracer.selectedNeuronNameOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedNeuronNameOLextPt[j].size(); n++) {
                                nTracer.selectedNeuronNameOL.add(nTracer.selectedNeuronNameOLextPt[j].get(n));
                            }
                        }
                        if (nTracer.selectedNeuronSpineOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedNeuronSpineOLextPt[j].size(); n++) {
                                nTracer.selectedNeuronSpineOL.add(nTracer.selectedNeuronSpineOLextPt[j].get(n));
                            }
                        }
                    }
                }
            }

            // overlay selected arbor(s)
            if (nTracer.overlaySelectedArbor_jCheckBox.isSelected()
                    || nTracer.overlaySelectedSynapse_jCheckBox.isSelected()) {
                if (nTracer.overlayAllSelectedPoints_jCheckBox.isSelected()) {
                    nTracer.getSelectedArborAndNameOLMultiThread(nTracer.selectedArborTraceOL,
                            nTracer.selectedArborNameOL, nTracer.selectedArborSynapseOL, nTracer.selectedArborConnectedOL, nTracer.selectedArborSpineOL);
                } else {
                    nTracer.getSelectedArborAndNameOLextPtMultiThread(nTracer.selectedArborTraceOLextPt,
                            nTracer.selectedArborNameOLextPt, nTracer.selectedArborSynapseOL, nTracer.selectedArborConnectedOL, nTracer.selectedArborSpineOLextPt, nTracer.extendDisplayPoints);
                    for (int j = 0; j < nTracer.impNSlice; j++) {
                        if (nTracer.selectedArborTraceOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedArborTraceOLextPt[j].size(); n++) {
                                nTracer.selectedArborTraceOL.add(nTracer.selectedArborTraceOLextPt[j].get(n));
                            }
                        }
                        if (nTracer.selectedArborNameOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedArborNameOLextPt[j].size(); n++) {
                                nTracer.selectedArborNameOL.add(nTracer.selectedArborNameOLextPt[j].get(n));
                            }
                        }
                        if (nTracer.selectedArborSpineOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedArborSpineOLextPt[j].size(); n++) {
                                nTracer.selectedArborSpineOL.add(nTracer.selectedArborSpineOLextPt[j].get(n));
                            }
                        }
                    }
                }
            }

            // overlay selected branch(es)
            if (nTracer.overlaySelectedBranch_jCheckBox.isSelected()
                    || nTracer.overlayAllName_jCheckBox.isSelected()
                    || nTracer.overlaySelectedName_jCheckBox.isSelected()
                    || nTracer.overlaySelectedSynapse_jCheckBox.isSelected()) {
                if (nTracer.overlayAllSelectedPoints_jCheckBox.isSelected() || nTracer.overlayAllPoints_jCheckBox.isSelected()) {
                    nTracer.getSelectedBranchAndNameOLMultiThread(nTracer.selectedBranchTraceOL,
                            nTracer.selectedBranchNameOL, nTracer.selectedBranchSynapseOL, nTracer.selectedBranchConnectedOL, nTracer.selectedBranchSpineOL);
                } else if (!nTracer.overlayAllSelectedPoints_jCheckBox.isSelected()) {
                    nTracer.getSelectedBranchAndNameOLextPtMultiThread(nTracer.selectedBranchTraceOLextPt,
                            nTracer.selectedBranchNameOLextPt, nTracer.selectedBranchSynapseOL, nTracer.selectedBranchConnectedOL, nTracer.selectedBranchSpineOLextPt, nTracer.extendDisplayPoints);
                    for (int j = 0; j < nTracer.impNSlice; j++) {
                        if (nTracer.selectedBranchTraceOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedBranchTraceOLextPt[j].size(); n++) {
                                nTracer.selectedBranchTraceOL.add(nTracer.selectedBranchTraceOLextPt[j].get(n));
                            }
                        }
                        if (nTracer.selectedBranchNameOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedBranchNameOLextPt[j].size(); n++) {
                                nTracer.selectedBranchNameOL.add(nTracer.selectedBranchNameOLextPt[j].get(n));
                            }
                        }
                        if (nTracer.selectedBranchSpineOLextPt[j] != null) {
                            for (int n = 0; n < nTracer.selectedBranchSpineOLextPt[j].size(); n++) {
                                nTracer.selectedBranchSpineOL.add(nTracer.selectedBranchSpineOLextPt[j].get(n));
                            }
                        }
                    }
                }
            }

            // overlay all somas
            if (nTracer.overlayAllSoma_jCheckBox.isSelected() || nTracer.overlayAllSynapse_jCheckBox.isSelected()) {
                nTracer.getAllSomaAndNameOLextPtMultiThread(nTracer.allSomaTraceOL, nTracer.allSomaNameOL,
                        nTracer.allSomaSynapseOL, nTracer.allSomaConnectedOL, nTracer.allSomaSpineOLextPt, !nTracer.overlayAllPoints_jCheckBox.isSelected());
            }

            // overlay selected soma(s)
            if (nTracer.overlaySelectedSoma_jCheckBox.isSelected()
                    || nTracer.overlayAllName_jCheckBox.isSelected()
                    || nTracer.overlaySelectedName_jCheckBox.isSelected()
                    || nTracer.overlaySelectedSynapse_jCheckBox.isSelected()) {
                nTracer.getSelectedSomaAndNameOLextPtMultiThread(nTracer.selectedSomaTraceOL, nTracer.selectedSomaNameOL,
                        nTracer.selectedSomaSynapseOL, nTracer.selectedSomaConnectedOL, nTracer.selectedSomaSpineOLextPt, !nTracer.overlayAllSelectedPoints_jCheckBox.isSelected());
            }

            nTracer.updateOverlay();
            //long endTime = System.currentTimeMillis();
            //IJ.log("total display update time = "+(endTime-startTime));
        }
    }
    
    
    
}
