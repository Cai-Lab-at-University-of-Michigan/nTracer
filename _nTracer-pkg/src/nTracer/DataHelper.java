/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import ij.IJ;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Scanner;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import static nTracer.nTracer_.activeChannels;
import static nTracer.nTracer_.analysisChannels;
import static nTracer.nTracer_.imp;
import static nTracer.nTracer_.impNChannel;
import static nTracer.nTracer_.rootAllSomaNode;
import static nTracer.nTracer_.rootDisplaySomaNode;
import static nTracer.nTracer_.rootNeuronNode;
import static nTracer.nTracer_.rootSpineNode;
import static nTracer.nTracer_.toggleChannels;
import static nTracer.nTracer_.xyzResolutions;

/**
 * Methods to save, load, and process data.
 *
 * @author Dawen, Wei Jie
 */
public class DataHelper {
    private nTracer_ nTracer;
    public DataHelper(nTracer_ nTracer) {
        this.nTracer = nTracer;
    }
    
    /**
    * Save data
    */
    public boolean saveData() {
        if (imp != null) {
            String directory = IJ.getDirectory("current");
            String fileName = imp.getTitle() + ".zip";

            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(directory + "/" + fileName));
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
            int returnVal = fileChooser.showOpenDialog(nTracer);
            if (returnVal == JFileChooser.CANCEL_OPTION) {
                return false;
            }
            File selectedFile = fileChooser.getSelectedFile();
            fileName = fileChooser.getName(selectedFile);
            if (fileName == null) {
                return false;
            }

            String[] panelParameters = {nTracer.xyRadius + "", nTracer.zRadius + "",
                nTracer.colorThreshold + "", nTracer.intensityThreshold + "",
                nTracer.extendDisplayPoints + "",
                nTracer.overlayAllPoints_jCheckBox.isSelected() + "",
                nTracer.overlayAllName_jCheckBox.isSelected() + "",
                nTracer.overlayAllSoma_jCheckBox.isSelected() + "",
                nTracer.overlayAllNeuron_jCheckBox.isSelected() + "",
                nTracer.overlayAllSpine_jCheckBox.isSelected() + "",
                nTracer.overlayAllSynapse_jCheckBox.isSelected() + "",
                nTracer.overlayAllConnection_jCheckBox.isSelected() + "",
                nTracer.overlayAllSelectedPoints_jCheckBox.isSelected() + "",
                nTracer.overlaySelectedName_jCheckBox.isSelected() + "",
                nTracer.overlaySelectedSoma_jCheckBox.isSelected() + "",
                nTracer.overlaySelectedNeuron_jCheckBox.isSelected() + "",
                nTracer.overlaySelectedArbor_jCheckBox.isSelected() + "",
                nTracer.overlaySelectedBranch_jCheckBox.isSelected() + "",
                nTracer.overlaySelectedSpine_jCheckBox.isSelected() + "",
                nTracer.overlaySelectedSynapse_jCheckBox.isSelected() + "",
                nTracer.overlaySelectedConnection_jCheckBox.isSelected() + "",
                nTracer.overlayPointBox_jCheckBox.isSelected() + "",
                (int) (nTracer.somaLine * 2) + "", (int) (nTracer.neuronLine * 2) + "",
                (int) (nTracer.arborLine * 2) + "", (int) (nTracer.branchLine * 2) + "",
                (int) (nTracer.spineLine * 2) + "", (int) (nTracer.pointBoxLine * 2) + "",
                (int) nTracer.synapseRadius + "", nTracer.pointBoxRadius + "", (int) (nTracer.lineWidthOffset * 2) + "",
                nTracer.history.autosaveIntervalMin + "", nTracer.history.delAutosaved + ""};
            //synapseSize = synapseRadius*2+1 

            //Calibration impCal = imp.getCalibration();
            try {
                nTracer.IO.savePackagedData(rootNeuronNode, rootAllSomaNode, rootSpineNode,
                        nTracer.neuronList_jTree, nTracer.displaySomaList_jTree, nTracer.pointTable_jTable, selectedFile,
                        imp.getC(), imp.getZ(), imp.getFrame(), panelParameters);
            } catch (IOException e) {
                return false;
            }
            return true;
        } else {
            return false;
        }
    }
    
    /**
    * Load data
    */
    public boolean loadData() {
        nTracer.canUpdateDisplay = false;
        boolean returnValue = false;
        if (imp != null) {
            String directory = IJ.getDirectory("current");
            String dataFileName = (String) (imp.getTitle() + ".zip");
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(directory + "/" + dataFileName));
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
            int returnVal = fileChooser.showOpenDialog(nTracer);
            if (returnVal == JFileChooser.CANCEL_OPTION) {
                return false;
            }
            File selectedFile = fileChooser.getSelectedFile();
            if (directory == null || dataFileName == null) {
                return false;
            }

            InputStream parameterAndNeuronIS, expansionAndSelectionIS, colorTableIS;
            try {
                System.gc();
                parameterAndNeuronIS = nTracer.IO.loadPackagedParameterAndNeuron(selectedFile);
                expansionAndSelectionIS = nTracer.IO.loadPackagedExpansionAndSelection(selectedFile);
                colorTableIS = nTracer.IO.loadPackagedColorTable(selectedFile);
                System.gc();
            } catch (IOException e) {
                return false;
            }

            if (parameterAndNeuronIS == null) {
                IJ.error("No neurite tracing data file found !");
                return false;
            }
            if (expansionAndSelectionIS == null) {
                IJ.error("No tree status file found !");
                return false;
            }
            if (colorTableIS == null) {
                IJ.log("No saved color lookup table was found, will calculate from file...");
            } else {
                try {
                    // I am going to load the color table here, instead of branching again                        
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(colorTableIS, "UTF-8"));
                    Scanner loadScanner = new Scanner(bufferedReader);

                    while (loadScanner.hasNext()) {
                        String line = loadScanner.nextLine();
                        line = line.replaceAll("\n", "");

                        String[] line_split = line.split("\t");

                        int[] coords = {
                            Integer.parseInt(line_split[0]),
                            Integer.parseInt(line_split[1]),
                            Integer.parseInt(line_split[2])
                        };

                        coord3D newPt = new coord3D(coords);
                        float newFloat = Float.parseFloat(line_split[3]);

                        nTracer.color_buffer.put(newPt, newFloat);
                    }
                } catch (Exception e) {
                } // we dont care, because the table can just be remade
            }
            returnValue = loadInputStreamData(parameterAndNeuronIS, expansionAndSelectionIS);
        }
        if (returnValue) {
            nTracer.history = new History(nTracer);
            nTracer.history.saveHistory();
        }
        nTracer.canUpdateDisplay = true;
        return returnValue;
    }
    
    /**
    * Load input stream data
    */
    public boolean loadInputStreamData(InputStream parameterAndNeuronIS, InputStream expansionAndSelectionIS) {
        // load tracing parameters and neurons
        try {
            // load neurons
            DefaultTreeModel[] treeModels = nTracer.IO.loadTracingParametersAndNeurons(parameterAndNeuronIS);
            nTracer.allSomaTreeModel = treeModels[0];
            rootAllSomaNode = (ntNeuronNode) (nTracer.allSomaTreeModel.getRoot());
            nTracer.neuronTreeModel = treeModels[1];
            rootNeuronNode = (ntNeuronNode) (nTracer.neuronTreeModel.getRoot());
            nTracer.spineTreeModel = treeModels[2];
            rootSpineNode = (ntNeuronNode) (nTracer.spineTreeModel.getRoot());
            nTracer.neuronList_jTree.setModel(nTracer.neuronTreeModel);
            nTracer.startPoint = new int[7];
            nTracer.hasStartPt = false;
            nTracer.endPoint = new int[7];
            nTracer.hasEndPt = false;

            if (impNChannel >= 1) {
                nTracer.toggleCh1_jCheckBox.setSelected(toggleChannels[0]);
                nTracer.analysisCh1_jCheckBox.setSelected(analysisChannels[0]);
            }
            if (impNChannel >= 2) {
                nTracer.toggleCh2_jCheckBox.setSelected(toggleChannels[1]);
                nTracer.analysisCh2_jCheckBox.setSelected(analysisChannels[1]);
            }
            if (impNChannel >= 3) {
                nTracer.toggleCh3_jCheckBox.setSelected(toggleChannels[2]);
                nTracer.analysisCh3_jCheckBox.setSelected(analysisChannels[2]);
            }
            if (impNChannel >= 4) {
                nTracer.toggleCh4_jCheckBox.setSelected(toggleChannels[3]);
                nTracer.analysisCh4_jCheckBox.setSelected(analysisChannels[3]);
            }
            if (impNChannel >= 5) {
                nTracer.toggleCh5_jCheckBox.setSelected(toggleChannels[4]);
                nTracer.analysisCh5_jCheckBox.setSelected(analysisChannels[4]);
            }
            if (impNChannel >= 6) {
                nTracer.toggleCh6_jCheckBox.setSelected(toggleChannels[5]);
                nTracer.analysisCh6_jCheckBox.setSelected(analysisChannels[5]);
            }
            if (impNChannel >= 7) {
                nTracer.toggleCh7_jCheckBox.setSelected(toggleChannels[6]);
                nTracer.analysisCh7_jCheckBox.setSelected(analysisChannels[6]);
            }
            if (impNChannel >= 8) {
                nTracer.toggleCh8_jCheckBox.setSelected(toggleChannels[7]);
                nTracer.analysisCh8_jCheckBox.setSelected(analysisChannels[7]);
            }
            for (int ch = 1; ch <= impNChannel; ch++) {
                if (toggleChannels[ch - 1]) {
                    nTracer.toggleChannel(ch);
                }
            }
            boolean[] active = nTracer.cmp.getActiveChannels();
            System.arraycopy(activeChannels, 0, active, 0, active.length);
            nTracer.cmp.updateAndDraw();
            nTracer.setTitle(nTracer.VERSION + " - pixel resolutions (x, y, z) um/pixel: (" + xyzResolutions[0] + ", " + xyzResolutions[1] + ", " + xyzResolutions[2] + ")");
        } catch (IOException e) {
            return false;
        }

        // set tree expansion and selection
        try {
            ArrayList[] status = nTracer.IO.loadExpansionSelectionPositionParameter(expansionAndSelectionIS);
            // status[0] -- neuron tree expansion status
            if (status[0].size() > 0) {
                for (int i = 0; i < status[0].size(); i++) {
                    String neuronNumber = (String) status[0].get(i);
                    if (neuronNumber.contains("/")) {
                        neuronNumber = neuronNumber.split("/")[0];
                    }
                    ntNeuronNode expandedNeurite = nTracer.getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber);
                    if (expandedNeurite != null) {
                        nTracer.neuronList_jTree.expandPath(new TreePath(expandedNeurite.getPath()));
                    }
                }
            }
            //IJ.log("set neuron tree expansion");

            // status[1] -- neuron tree selection status
            if (status[1].size() > 0) {
                for (int i = 0; i < status[1].size(); i++) {
                    String nodeName = (String) status[1].get(i);
                    ntNeuronNode selectedNeuriteNode = nTracer.getNodeFromNeuronTreeByNodeName(nodeName);
                    if (selectedNeuriteNode != null) {
                        nTracer.neuronList_jTree.addSelectionPath(new TreePath(selectedNeuriteNode.getPath()));
                    }
                }
            }
            //IJ.log("set neuron tree selection");

            // status[2] -- neuron tree visible rectangle
            if (status[2].size() > 0) {
                int x = Integer.parseInt((String) status[2].get(0));
                int y = Integer.parseInt((String) status[2].get(1));
                int width = Integer.parseInt((String) status[2].get(2));
                int height = Integer.parseInt((String) status[2].get(3));
                Rectangle neuronListVisibleRectangle = new Rectangle(x, y, width, height);
                nTracer.neuronList_jTree.scrollRectToVisible(neuronListVisibleRectangle);
            }
            //IJ.log("set neuronList rectangle");

            // status[3] -- soma tree selection status
            if (status[3].size() > 0) {
                for (int i = 0; i < status[3].size(); i++) {
                    String somaName = (String) status[3].get(i);
                    for (int n = 0; n < rootDisplaySomaNode.getChildCount(); n++) {
                        ntNeuronNode somaSlice = (ntNeuronNode) rootDisplaySomaNode.getChildAt(n);
                        if (somaName.equals(somaSlice.toString())) {
                            nTracer.displaySomaList_jTree.addSelectionRow(n);
                            break;
                        }
                    }
                }
            }
            //IJ.log("set soma tree selection status");

            // status[4] -- soma tree visible rectangle
            if (status[4].size() > 0) {
                int x = Integer.parseInt((String) status[4].get(0));
                int y = Integer.parseInt((String) status[4].get(1));
                int width = Integer.parseInt((String) status[4].get(2));
                int height = Integer.parseInt((String) status[4].get(3));
                Rectangle somaListVisibleRectangle = new Rectangle(x, y, width, height);
                nTracer.displaySomaList_jTree.scrollRectToVisible(somaListVisibleRectangle);
            }
            //IJ.log("set soma tree visible rectangle");

            // status[5] -- tracePoint_Table selection status
            if (status[5].size() > 0) {
                for (int i = 0; i < status[5].size(); i++) {
                    int selectedRow = Integer.parseInt((String) status[5].get(i));
                    nTracer.pointTable_jTable.addRowSelectionInterval(selectedRow, selectedRow);
                }
            }
            //IJ.log("tracePoint_Table selection status");

            // status[6] -- traced point table visible rectangle
            if (status[6].size() > 0) {
                int x = Integer.parseInt((String) status[6].get(0));
                int y = Integer.parseInt((String) status[6].get(1));
                int width = Integer.parseInt((String) status[6].get(2));
                int height = Integer.parseInt((String) status[6].get(3));
                Rectangle pointTableVisibleRectangle = new Rectangle(x, y, width, height);
                nTracer.pointTable_jTable.scrollRectToVisible(pointTableVisibleRectangle);
            }
            //IJ.log("traced point table visible rectangle");

            // status[7] -- image position
            if (status[7].size() > 0) {
                int c = Integer.parseInt((String) status[7].get(0));
                int z = Integer.parseInt((String) status[7].get(1));
                int f = Integer.parseInt((String) status[7].get(2));
                imp.setPosition(c, z, f);
            }

            // status[8] -- nTracer control panel parameters
            if (status[8].size() > 0) {
                int size = status[8].size();
                if (Utils.isInteger((String) status[8].get(0))) {
                    if (Integer.parseInt((String) status[8].get(0)) > 0) {
                        nTracer.xyRadius = Integer.parseInt((String) status[8].get(0));
                        nTracer.xyRadius_jSpinner.setValue((Integer) nTracer.xyRadius);
                    }
                }
                if (Utils.isInteger((String) status[8].get(1))) {
                    if (Integer.parseInt((String) status[8].get(1)) > 0) {
                        nTracer.zRadius = Integer.parseInt((String) status[8].get(1));
                        nTracer.zRadius_jSpinner.setValue((Integer) nTracer.zRadius);
                    }
                }
                if (Utils.isFloat((String) status[8].get(2))) {
                    if (Float.parseFloat((String) status[8].get(2)) > 0) {
                        nTracer.colorThreshold = Float.parseFloat((String) status[8].get(2));
                        nTracer.colorThreshold_jSpinner.setValue((Float) nTracer.colorThreshold);
                    }
                }
                if (Utils.isFloat((String) status[8].get(3))) {
                    if (Float.parseFloat((String) status[8].get(3)) > 0) {
                        nTracer.intensityThreshold = Float.parseFloat((String) status[8].get(3));
                        nTracer.intensityThreshold_jSpinner.setValue((Float) nTracer.intensityThreshold);
                    }
                }
                if (Utils.isInteger((String) status[8].get(4))) {
                    if (Integer.parseInt((String) status[8].get(4)) > 0) {
                        nTracer.extendDisplayPoints = Integer.parseInt((String) status[8].get(4));
                        nTracer.extendAllDisplayPoints_jSpinner.setValue((Integer) nTracer.extendDisplayPoints);
                    }
                }
                nTracer.overlayAllPoints_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(5)));
                nTracer.overlayAllName_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(6)));
                nTracer.overlayAllSoma_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(7)));
                nTracer.overlayAllNeuron_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(8)));
                nTracer.overlayAllSpine_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(9)));
                nTracer.overlayAllSynapse_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(10)));
                nTracer.overlayAllConnection_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(11)));
                nTracer.overlayAllSelectedPoints_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(12)));
                nTracer.overlaySelectedName_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(13)));
                nTracer.overlaySelectedSoma_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(14)));
                nTracer.overlaySelectedNeuron_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(15)));
                nTracer.overlaySelectedArbor_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(16)));
                nTracer.overlaySelectedBranch_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(17)));
                nTracer.overlaySelectedSpine_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(18)));
                nTracer.overlaySelectedSynapse_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(19)));
                nTracer.overlaySelectedConnection_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(20)));
                nTracer.overlayPointBox_jCheckBox.setSelected(Boolean.parseBoolean((String) status[8].get(21)));
                if (size > 22) {
                    if (Utils.isFloat((String) status[8].get(22))) {
                        if (Float.parseFloat((String) status[8].get(22)) > 0) {
                            nTracer.somaLine = Float.parseFloat((String) status[8].get(22)) / 2;
                            nTracer.somaLineWidth_jSpinner.setValue((int) (nTracer.somaLine * 2));
                        }
                    }
                }
                if (size > 23) {
                    if (Utils.isFloat((String) status[8].get(23))) {
                        if (Float.parseFloat((String) status[8].get(23)) > 0) {
                            nTracer.neuronLine = Float.parseFloat((String) status[8].get(23)) / 2;
                            nTracer.neuronLineWidth_jSpinner.setValue((int) (nTracer.neuronLine * 2));
                        }
                    }
                }
                if (size > 24) {
                    if (Utils.isFloat((String) status[8].get(24))) {
                        if (Float.parseFloat((String) status[8].get(24)) > 0) {
                            nTracer.arborLine = Float.parseFloat((String) status[8].get(24)) / 2;
                           nTracer. arborLineWidth_jSpinner.setValue((int) (nTracer.arborLine * 2));
                        }
                    }
                }
                if (size > 25) {
                    if (Utils.isFloat((String) status[8].get(25))) {
                        if (Float.parseFloat((String) status[8].get(25)) > 0) {
                            nTracer.branchLine = Float.parseFloat((String) status[8].get(25)) / 2;
                            nTracer.branchLineWidth_jSpinner.setValue((int) (nTracer.branchLine * 2));
                        }
                    }
                }
                if (size > 26) {
                    if (Utils.isFloat((String) status[8].get(26))) {
                        if (Float.parseFloat((String) status[8].get(26)) > 0) {
                            nTracer.spineLine = Float.parseFloat((String) status[8].get(26)) / 2;
                            nTracer.spineLineWidth_jSpinner.setValue((int) (nTracer.spineLine * 2));
                        }
                    }
                }
                if (size > 27) {
                    if (Utils.isFloat((String) status[8].get(27))) {
                        if (Float.parseFloat((String) status[8].get(27)) > 0) {
                            nTracer.pointBoxLine = Float.parseFloat((String) status[8].get(27)) / 2;
                            nTracer.pointBoxLineWidth_jSpinner.setValue((int) (nTracer.pointBoxLine * 2));
                        }
                    }
                }
                if (size > 28) {
                    if (Utils.isDouble((String) status[8].get(28))) {
                        if (Double.parseDouble((String) status[8].get(28)) > 0) {
                            nTracer.synapseRadius = Double.parseDouble((String) status[8].get(28));
                            nTracer.synapseSize = nTracer.synapseRadius * 2 + 1;
                            nTracer.synapseRadius_jSpinner.setValue((int) nTracer.synapseRadius);
                        }
                    }
                }
                if (size > 29) {
                    if (Utils.isInteger((String) status[8].get(29))) {
                        if (Float.parseFloat((String) status[8].get(29)) > 0) {
                            nTracer.pointBoxRadius = Integer.parseInt((String) status[8].get(29));
                            nTracer.pointBoxRadiu_jSpinner.setValue((Integer) nTracer.pointBoxRadius);
                        }
                    }
                }
                if (size > 30) {
                    if (Utils.isFloat((String) status[8].get(30))) {
                        if (Float.parseFloat((String) status[8].get(30)) > 0) {
                            nTracer.lineWidthOffset = Float.parseFloat((String) status[8].get(30)) / 2;
                            nTracer.allNeuronLineWidthOffset_jSpinner.setValue((int) (nTracer.lineWidthOffset * 2));
                            nTracer.allSomaLine = (nTracer.somaLine - nTracer.lineWidthOffset > 0.5) ? nTracer.somaLine - nTracer.lineWidthOffset : 0.5f;
                            nTracer.allNeuronLine = (nTracer.neuronLine - nTracer.lineWidthOffset > 0.5) ? nTracer.neuronLine - nTracer.lineWidthOffset : 0.5f;
                            nTracer.allSpineLine = (nTracer.spineLine - nTracer.lineWidthOffset > 0.5) ? nTracer.spineLine - nTracer.lineWidthOffset : 0.5f;
                            nTracer.allSynapseRadius = (nTracer.synapseRadius - nTracer.lineWidthOffset / 2 > 0.5) ? nTracer.synapseRadius - nTracer.lineWidthOffset / 2 : 0.5;
                            nTracer.allSynapseSize = nTracer.allSynapseRadius * 2;
                        }
                    }
                    if (size > 31) {
                        if (Utils.isLong((String) status[8].get(31))) {
                            if (Long.parseLong((String) status[8].get(31)) > 0) {
                                nTracer.history.autosaveIntervalMin = Long.parseLong((String) status[8].get(31));
                            }
                        }
                    }
                    if (size > 32) {
                        nTracer.history.delAutosaved = Boolean.parseBoolean((String) status[8].get(32));
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }

        imp.updateAndRepaintWindow();
        nTracer.updateDisplay();
        return true;
    }
    
    /**
    * Clear data
    */
    public void clearData() {
        nTracer.initPointTable();
        nTracer.initNeuriteTree();
        nTracer.initSomaTree();
        nTracer.initSpineTree();
        nTracer.history.saveHistory();
        nTracer.updateDisplay();
    }
}
