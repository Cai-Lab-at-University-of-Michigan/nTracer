/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import ij.IJ;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import static java.util.concurrent.TimeUnit.MINUTES;
import javax.swing.tree.TreePath;
import static nTracer.nTracer_.imp;
import static nTracer.nTracer_.rootAllSomaNode;
import static nTracer.nTracer_.rootDisplaySomaNode;
import static nTracer.nTracer_.rootNeuronNode;
import static nTracer.nTracer_.rootSpineNode;

/**
 *
 * @author Dawen, Wei Jie
 */
public class History {
    private nTracer_ nTracer;
    private int historyPointer;
    private ArrayList<Integer> historyIndexStack;
    private final int maxHistoryLevel = 30;
    private String[][] nTracerParameters;
    private int[][] impPosition;
    
    private ntNeuronNode[] historyNeuronNode, historyAllSomaNode, historySpineNode;
    private ArrayList<ArrayList<String>> historyExpandedNeuronNames, historySelectedNeuronNames, historySelectedSomaSliceNames;
    private ArrayList<ArrayList<Integer>> historySelectedTableRows;
    private Rectangle[] historyNeuronTreeVisibleRect, historyDisplaySomaTreeVisibleRect, historyPointTableVisibleRect;
    private int[][] historyStartPoint, historyEndPoint;
    private boolean[] historyHasStartPt, historyHasEndPt;
    
    public History(nTracer_ nTracer) {
        this.nTracer = nTracer;
        historyIndexStack = new ArrayList<>();
        historyPointer = 0;
        nTracerParameters = new String[maxHistoryLevel][33];
        impPosition = new int[maxHistoryLevel][3];
        historyNeuronNode = new ntNeuronNode[maxHistoryLevel];
        historyAllSomaNode = new ntNeuronNode[maxHistoryLevel];
        historySpineNode = new ntNeuronNode[maxHistoryLevel];
        historyExpandedNeuronNames = new ArrayList<>();
        historySelectedNeuronNames = new ArrayList<>();
        historySelectedSomaSliceNames = new ArrayList<>();
        historySelectedTableRows = new ArrayList<>();
        historyNeuronTreeVisibleRect = new Rectangle[maxHistoryLevel];
        historyDisplaySomaTreeVisibleRect = new Rectangle[maxHistoryLevel];
        historyPointTableVisibleRect = new Rectangle[maxHistoryLevel];
        historyStartPoint = new int[maxHistoryLevel][7];
        historyEndPoint = new int[maxHistoryLevel][7];
        historyHasStartPt = new boolean[maxHistoryLevel];
        historyHasEndPt = new boolean[maxHistoryLevel];
        for (int i = 0; i < maxHistoryLevel; i++) {
            historyNeuronNode[i] = new ntNeuronNode("", new ArrayList<>());
            historyAllSomaNode[i] = new ntNeuronNode("", new ArrayList<>());
            historySpineNode[i] = new ntNeuronNode("", new ArrayList<>());

            ArrayList<String> historyExpandedNeuronName = new ArrayList<>();
            historyExpandedNeuronNames.add(historyExpandedNeuronName);

            ArrayList<String> historySelectedNeuronName = new ArrayList<>();
            historySelectedNeuronNames.add(historySelectedNeuronName);

            ArrayList<String> historySelectedSomaSliceName = new ArrayList<>();
            historySelectedSomaSliceNames.add(historySelectedSomaSliceName);

            ArrayList<Integer> historySelectedTableRow = new ArrayList<>();
            historySelectedTableRows.add(historySelectedTableRow);

            historyIndexStack.add(-1);
        }
    }
    
        
    public void saveHistory() {
        if (historyPointer < 0) { // initial saving
            historyPointer++;
            historyIndexStack.set(historyPointer, 0);
            saveHistory2Memory(historyIndexStack.get(historyPointer));
        } else if (historyPointer < maxHistoryLevel - 1) {
            historyPointer++;
            if (historyIndexStack.get(historyPointer - 1) == maxHistoryLevel - 1) {
                historyIndexStack.set(historyPointer, 0);
            } else {
                historyIndexStack.set(historyPointer, historyIndexStack.get(historyPointer - 1) + 1);
            }
            for (int i = historyPointer + 1; i < maxHistoryLevel; i++) {
                historyIndexStack.set(i, -1);
            }
            //IJ.log("currentHistoryLevel = "+currentHistoryLevel+"; save to memory level = "+historyIndex[currentHistoryLevel]);
            saveHistory2Memory(historyIndexStack.get(historyPointer));
        } else { // historyPonter = maxHistoryLevel-1 : at the end of the index stack
            historyIndexStack.add(historyIndexStack.get(0));
            historyIndexStack.remove(0);
            saveHistory2Memory(historyIndexStack.get(historyIndexStack.size() - 1));
        }
        //IJ.log("prevHistoryLevel = "+(historyPointer-1)+"; currentHistoryLevel = "+historyPointer+"; load memory "+historyIndexStack.get(historyPointer));
    }

    public void backwardHistory() {
        if (historyPointer > 0) {
            historyPointer--;
            //IJ.log("prevHistoryLevel = "+(historyPointer+1)+"; currentHistoryLevel = "+historyPointer+"; load memory "+historyIndexStack.get(historyPointer));
            try {
                if (!loadHistoryFromMemory(historyIndexStack.get(historyPointer))) {
                    historyPointer++;
                }
            } catch (Exception e) {
            }
        }
    }

    public void forwardHistory() {
        if (historyPointer < maxHistoryLevel - 1 && historyIndexStack.get(historyPointer + 1) >= 0) {
            historyPointer++;
            //IJ.log("prevHistoryLevel = "+(historyPointer-1)+"; currentHistoryLevel = "+historyPointer+"; load memory "+historyIndexStack.get(historyPointer));
            try {
                if (!loadHistoryFromMemory(historyIndexStack.get(historyPointer))) {
                    historyPointer--;
                }
            } catch (Exception e) {
            }
        }
    }
    
    public void startAutosave(long interval) {
        //IJ.log(IJ.getDirectory("current"));
        final String folder = IJ.getDirectory("current") + "/" + imp.getTitle() + "_nTracer_Autosave" + "/";
        File autosaveFolder = new File(folder);
        if (!autosaveFolder.exists()) {
            autosaveFolder.mkdirs();
        }
        //IJ.log("autosave: "+historyIndexStack.get(historyPointer)+"; "+interval+"; "+MINUTES);

        nTracer.scheduler.scheduleAtFixedRate(() -> {
            autoSave2File(folder, historyIndexStack.get(historyPointer));
        }, 0, interval, MINUTES);

    }
    
    private void recordTreeExpansionSelectionStatus(int historyLevel) {
        recordNeuronTreeExpansionStatus(historyLevel);
        recordTreeSelectionStatus(historyLevel);
    }
    
    private void restoreTreeExpansionSelectionStatus(int historyLevel) {
        restoreNeuronTreeExpansionStatus(historyLevel);
        restoreTreeSelectionStatus(historyLevel);
    }
    
    
    private void recordNeuronTreeExpansionStatus(int historyLevel) {
        ArrayList<String> levelExpandedNeuronNames = historyExpandedNeuronNames.get(historyLevel);
        levelExpandedNeuronNames.clear();
        nTracer.expandedNeuronNames.clear();
        for (int n = 0; n < rootNeuronNode.getChildCount(); n++) {
            ntNeuronNode neuron = (ntNeuronNode) rootNeuronNode.getChildAt(n);
            TreePath neuronPath = new TreePath(neuron.getPath());
            if (nTracer.neuronList_jTree.isExpanded(neuronPath)) {
                String neuronName = neuron.toString();
                levelExpandedNeuronNames.add(neuronName);
                nTracer.expandedNeuronNames.add(neuronName);
            }
        }
    }
    
    private void restoreNeuronTreeExpansionStatus(int historyLevel) {
        ArrayList<String> levelExpandedNeuronNames = historyExpandedNeuronNames.get(historyLevel);
        for (int n = 0; n < rootNeuronNode.getChildCount(); n++) {
            ntNeuronNode neuron = (ntNeuronNode) rootNeuronNode.getChildAt(n);
            String neuronNumber = neuron.getNeuronNumber();
            for (String expandedNeuronName : levelExpandedNeuronNames) {
                if (expandedNeuronName.contains("/")) {
                    expandedNeuronName = expandedNeuronName.split("/")[0];
                }
                if (expandedNeuronName.equals(neuronNumber)) {
                    TreePath neuronPath = new TreePath(neuron.getPath());
                    nTracer.neuronList_jTree.expandPath(neuronPath);
                    break;
                }
            }
        }
    }
    
    private void recordTreeSelectionStatus(int historyLevel) {
        ArrayList<String> levelSelectedNeuronNames = historySelectedNeuronNames.get(historyLevel);
        ArrayList<String> levelSelectedSomaSliceNames = historySelectedSomaSliceNames.get(historyLevel);
        ArrayList<Integer> levelSelectedTableRows = historySelectedTableRows.get(historyLevel);
        levelSelectedNeuronNames.clear();
        levelSelectedSomaSliceNames.clear();
        levelSelectedTableRows.clear();
        nTracer.selectedNeuronNames.clear();
        nTracer.selectedSomaSliceNames.clear();
        nTracer.selectedTableRows.clear();
        TreePath[] selectedNeuronTreePaths = nTracer.neuronList_jTree.getSelectionPaths();
        if (selectedNeuronTreePaths != null) {
            for (TreePath selectedNeuronTreePath : selectedNeuronTreePaths) {
                ntNeuronNode selectedNeuronNode = (ntNeuronNode) selectedNeuronTreePath.getLastPathComponent();
                levelSelectedNeuronNames.add(selectedNeuronNode.toString());
                nTracer.selectedNeuronNames.add(selectedNeuronNode.toString());
            }
        }
        TreePath[] selectedSomaSliceTreePaths = nTracer.displaySomaList_jTree.getSelectionPaths();
        if (selectedSomaSliceTreePaths != null) {
            for (TreePath selectedSomaSliceTreePath : selectedSomaSliceTreePaths) {
                ntNeuronNode selectedSomaSliceNode = (ntNeuronNode) selectedSomaSliceTreePath.getLastPathComponent();
                levelSelectedSomaSliceNames.add(selectedSomaSliceNode.toString());
                nTracer.selectedSomaSliceNames.add(selectedSomaSliceNode.toString());
            }
        }
        int[] selectedRows = nTracer.pointTable_jTable.getSelectedRows();
        if (selectedRows != null) {
            for (int selectedRow : selectedRows) {
                levelSelectedTableRows.add(selectedRow);
                nTracer.selectedTableRows.add(selectedRow);
            }
        }
        // record trees and table's view
        historyNeuronTreeVisibleRect[historyLevel] = nTracer.neuronList_jTree.getVisibleRect();
        historyDisplaySomaTreeVisibleRect[historyLevel] = nTracer.displaySomaList_jTree.getVisibleRect();
        historyPointTableVisibleRect[historyLevel] = nTracer.pointTable_jTable.getVisibleRect();
    }
    
    private void restoreTreeSelectionStatus(int historyLevel) {
        ArrayList<String> levelSelectedNeuronNames = historySelectedNeuronNames.get(historyLevel);
        ArrayList<String> levelSelectedSomaSliceNames = historySelectedSomaSliceNames.get(historyLevel);
        ArrayList<Integer> levelSelectedTableRows = historySelectedTableRows.get(historyLevel);
        ArrayList<TreePath> selectedPaths = new ArrayList<TreePath>();
        for (String selectedNeuronName : levelSelectedNeuronNames) {
            ntNeuronNode selectedNeuronNode = nTracer.getNodeFromNeuronTreeByNodeName(selectedNeuronName);
            if (selectedNeuronNode != null) {
                TreePath selectedNeuronPath = new TreePath(selectedNeuronNode.getPath());
                selectedPaths.add(selectedNeuronPath);
            }
        }
        if (selectedPaths.size() > 0) {
            TreePath[] selectionPaths = new TreePath[selectedPaths.size()];
            for (int i = 0; i < selectedPaths.size(); i++) {
                selectionPaths[i] = (TreePath) selectedPaths.get(i);
            }
            nTracer.neuronList_jTree.setSelectionPaths(selectionPaths);
        }

        for (String selectedSomaSliceName : levelSelectedSomaSliceNames) {
            //IJ.log("input "+selectedSomaSliceName);
            for (int i = 0; i < rootDisplaySomaNode.getChildCount(); i++) {
                ntNeuronNode somaSliceNode = (ntNeuronNode) rootDisplaySomaNode.getChildAt(i);
                //IJ.log("compare selected "+selectedSomaSliceName+" to "+somaSliceNode.toString());
                if (selectedSomaSliceName.equals(somaSliceNode.toString())) {
                    nTracer.displaySomaList_jTree.addSelectionRow(i);
                    //IJ.log("added "+i);
                    break;
                }
                //IJ.log("not added "+i);
            }
        }
        int totalTableRows = nTracer.pointTable_jTable.getRowCount();
        if (nTracer.pointTable_jTable.getRowCount() > 0) {
            for (int selectedTableRow : levelSelectedTableRows) {
                if (selectedTableRow < totalTableRows) {
                    nTracer.pointTable_jTable.addRowSelectionInterval(selectedTableRow, selectedTableRow);
                }
            }
        }
        // restore trees and table's view
        nTracer.neuronList_jTree.scrollRectToVisible(historyNeuronTreeVisibleRect[historyLevel]);
        nTracer.displaySomaList_jTree.scrollRectToVisible(historyDisplaySomaTreeVisibleRect[historyLevel]);
        nTracer.pointTable_jTable.scrollRectToVisible(historyPointTableVisibleRect[historyLevel]);
    }
    
    private boolean saveHistory2Memory(int historyLevel) {
        if (imp == null) {
            return false;
        }
        //IJ.log("saved historyLevel "+historyLevel);
        recordPanelParameters(historyLevel);
        recordImagePosition(historyLevel);
        recordStartEndPointStatus(historyLevel);
        historyNeuronNode[historyLevel] = ntDataHandler.replicateNodeAndChild(rootNeuronNode);
        /*
        IJ.log("new neuron tree");
        for (int i = 0; i<historyNeuronNode[historyLevel].getChildCount();i++){
            IJ.log("new child "+i+"; name = "+((ntNeuronNode)historyNeuronNode[historyLevel].getChildAt(i)).toString()+
                    " old name = "+((ntNeuronNode)rootNeuronNode.getChildAt(i)).toString());
        }
        IJ.log("after replicate neuron name = "+historyNeuronNode[historyLevel].toString()+"; child = "+historyNeuronNode[historyLevel].getChildCount());
        IJ.log("replicated "+historyNeuronNode[historyLevel].getChildCount());
         */
        historyAllSomaNode[historyLevel] = ntDataHandler.replicateNodeAndChild(rootAllSomaNode);
        //IJ.log("after replicate soma name = "+historyAllSomaNode[historyLevel].toString()+"; child = "+historyAllSomaNode[historyLevel].getChildCount());
        historySpineNode[historyLevel] = ntDataHandler.replicateNodeAndChild(rootSpineNode);
        recordTreeExpansionSelectionStatus(historyLevel);
        //IJ.log("ok4 "+(historyExpandedNeuronNames.get(historyLevel)).get(0));        
//        IJ.log("ok5 "+(historySelectedNeuronNames.get(historyLevel)).get(0)); 
//        IJ.log("ok6 "+(historySelectedSomaSliceNames.get(historyLevel)).get(0));
//        IJ.log("ok7 "+(historySelectedTableRows.get(historyLevel)).get(0));

        return true;
    }

    private boolean loadHistoryFromMemory(int historyLevel) {
        if (imp == null) {
            return false;
        }
        try {
            nTracer.canUpdateDisplay = false;
            rootNeuronNode = ntDataHandler.replicateNodeAndChild(historyNeuronNode[historyLevel]);
            nTracer.neuronTreeModel.setRoot(rootNeuronNode);
            nTracer.neuronList_jTree.setModel(nTracer.neuronTreeModel);
            //IJ.log("load "+historyLevel);
            rootAllSomaNode = ntDataHandler.replicateNodeAndChild(historyAllSomaNode[historyLevel]);
            rootSpineNode = ntDataHandler.replicateNodeAndChild(historySpineNode[historyLevel]);
            if (rootNeuronNode == null || rootAllSomaNode == null) {
                IJ.error("No neurite tracing data file found !");
                nTracer.canUpdateDisplay = true;
                return false;
            }
            //updateTrees();
            restoreImagePosition(historyLevel);
            restoreStartEndPointStatus(historyLevel);
            restoreTreeExpansionSelectionStatus(historyLevel);

            nTracer.canUpdateDisplay = true;
            nTracer.updateDisplay();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void restoreImagePosition(int historyLevel) {
        int c = impPosition[historyLevel][0];
        int z = impPosition[historyLevel][1];
        int f = impPosition[historyLevel][2];
        imp.setPosition(c, z, f);
    }

    private void recordPanelParameters(int historyLevel) {
        nTracerParameters[historyLevel][0] = nTracer.xyRadius + "";
        nTracerParameters[historyLevel][1] = nTracer.zRadius + "";
        nTracerParameters[historyLevel][2] = nTracer.colorThreshold + "";
        nTracerParameters[historyLevel][3] = nTracer.intensityThreshold + "";
        nTracerParameters[historyLevel][4] = nTracer.extendDisplayPoints + "";
        nTracerParameters[historyLevel][5] = nTracer.overlayAllPoints_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][6] = nTracer.overlayAllName_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][7] = nTracer.overlayAllSoma_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][8] = nTracer.overlayAllNeuron_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][9] = nTracer.overlayAllSpine_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][10] = nTracer.overlayAllSynapse_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][11] = nTracer.overlayAllConnection_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][12] = nTracer.overlayAllSelectedPoints_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][13] = nTracer.overlaySelectedName_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][14] = nTracer.overlaySelectedSoma_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][15] = nTracer.overlaySelectedNeuron_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][16] = nTracer.overlaySelectedArbor_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][17] = nTracer.overlaySelectedBranch_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][18] = nTracer.overlaySelectedSpine_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][19] = nTracer.overlaySelectedSynapse_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][20] = nTracer.overlaySelectedConnection_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][21] = nTracer.overlayPointBox_jCheckBox.isSelected() + "";
        nTracerParameters[historyLevel][22] = (int) (nTracer.somaLine * 2) + "";
        nTracerParameters[historyLevel][23] = (int) (nTracer.neuronLine * 2) + "";
        nTracerParameters[historyLevel][24] = (int) (nTracer.arborLine * 2) + "";
        nTracerParameters[historyLevel][25] = (int) (nTracer.branchLine * 2) + "";
        nTracerParameters[historyLevel][26] = (int) (nTracer.spineLine * 2) + "";
        nTracerParameters[historyLevel][27] = (int) (nTracer.pointBoxLine * 2) + "";
        nTracerParameters[historyLevel][28] = (int) nTracer.synapseRadius + "";
        nTracerParameters[historyLevel][29] = nTracer.pointBoxRadius + "";
        nTracerParameters[historyLevel][30] = nTracer.lineWidthOffset + "";
        nTracerParameters[historyLevel][31] = nTracer.autosaveIntervalMin + "";
        nTracerParameters[historyLevel][32] = nTracer.delAutosaved + "";
        //synapseSize = synapseRadius*2+1 
    }

    private void recordImagePosition(int historyLevel) {
        impPosition[historyLevel][0] = imp.getC();
        impPosition[historyLevel][1] = imp.getZ();
        impPosition[historyLevel][2] = imp.getFrame();
    }

    private void restoreStartEndPointStatus(int historyLevel) {
        for (int i = 0; i < 5; i++) {
            nTracer.startPoint[i] = historyStartPoint[historyLevel][i];
            nTracer.endPoint[i] = historyEndPoint[historyLevel][i];
        }
        nTracer.hasStartPt = historyHasStartPt[historyLevel];
        nTracer.hasEndPt = historyHasEndPt[historyLevel];
    }

    private void recordStartEndPointStatus(int historyLevel) {
        if (nTracer.startPoint == null) {
            historyStartPoint[historyLevel] = new int[7];
        } else {
            System.arraycopy(nTracer.startPoint, 0, historyStartPoint[historyLevel], 0, 7);
        }
        if (nTracer.endPoint == null) {
            historyEndPoint[historyLevel] = new int[7];
        } else {
            System.arraycopy(nTracer.endPoint, 0, historyEndPoint[historyLevel], 0, 7);
        }
        historyHasStartPt[historyLevel] = nTracer.hasStartPt;
        historyHasEndPt[historyLevel] = nTracer.hasEndPt;
    }

    private boolean autoSave2File(String folder, int historyLevel) {
        if (imp != null && (rootNeuronNode.getChildCount() > 0 || rootAllSomaNode.getChildCount() > 0)) {
            SimpleDateFormat fileFormatter = new SimpleDateFormat("yyyy-MM-dd'at'HHmm");
            Date now = new Date();
            String fileName = imp.getTitle() + "_" + fileFormatter.format(now);

            ntNeuronNode autosaveNeuronNode = ntDataHandler.replicateNodeAndChild(historyNeuronNode[historyLevel]);
            ntNeuronNode autosaveAllSomaNode = ntDataHandler.replicateNodeAndChild(historyAllSomaNode[historyLevel]);
            ntNeuronNode autosaveSpineNode = ntDataHandler.replicateNodeAndChild(historySpineNode[historyLevel]);
            ArrayList<String> autosaveExpandedNeuronNames = historyExpandedNeuronNames.get(historyLevel);
            ArrayList<String> autosaveSelectedNeuronNames = historySelectedNeuronNames.get(historyLevel);
            ArrayList<String> autosaveSelectedSomaSliceNames = historySelectedSomaSliceNames.get(historyLevel);
            ArrayList<Integer> autosaveSelectedTableRows = historySelectedTableRows.get(historyLevel);
            Rectangle autosaveNeuronTreeVisibleRect = historyNeuronTreeVisibleRect[historyLevel];
            Rectangle autosaveDisplaySomaTreeVisibleRect = historyDisplaySomaTreeVisibleRect[historyLevel];
            Rectangle autosavePointTableVisibleRect = historyPointTableVisibleRect[historyLevel];

            try {
                nTracer.IO.savePackagedData(autosaveNeuronNode, autosaveAllSomaNode, autosaveSpineNode, autosaveExpandedNeuronNames,
                        autosaveSelectedNeuronNames, autosaveSelectedSomaSliceNames, autosaveSelectedTableRows,
                        autosaveNeuronTreeVisibleRect, autosaveDisplaySomaTreeVisibleRect, autosavePointTableVisibleRect,
                        folder, fileName, impPosition[historyLevel], 0, 0, 0, nTracerParameters[historyLevel]);
            } catch (IOException e) {
                //IJ.log("autosave fail");
                return false;
            }
            //IJ.log("autosaved");
            return true;
        } else {
            //IJ.log("NOT autosaved");
            return false;
        }
    }
}
