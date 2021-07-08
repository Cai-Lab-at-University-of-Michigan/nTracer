/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import ij.IJ;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.gui.YesNoCancelDialog;
import ij.process.FloatPolygon;
import java.util.ArrayList;
import javax.swing.tree.TreePath;
import static nTracer.nTracer_.analysisChannels;
import static nTracer.nTracer_.getSomaNodeFromAllSomaTreeByNeuronNumber;
import static nTracer.nTracer_.getSomaNodeFromNeuronTreeByNeuronNumber;
import static nTracer.nTracer_.imp;
import static nTracer.nTracer_.rootAllSomaNode;
import static nTracer.nTracer_.rootNeuronNode;
import static nTracer.nTracer_.rootSpineNode;

/**
 * Helper methods for tracing, branching and node creation.
 *
 * @author Dawen Cai, Wei Jie Lee
 */
public class TraceHelper {
    private nTracer_ nTracer;
    
    public TraceHelper(nTracer_ nTracer) {
        this.nTracer = nTracer;
    }

    protected void traceNeurite() {
        if (nTracer.manualTracing_jRadioButton.isSelected()) {
            manualTraceNeurite();
        } else if (nTracer.semiAutoTracing_jRadioButton.isSelected()) {
            semiAutoTraceNeurite();
        } else if (nTracer.autoTracing_jRadioButton.isSelected()) {
            autoTraceNeurite();
        }
    }

    protected void traceSpine() {
        if (nTracer.manualTracing_jRadioButton.isSelected()) {
            manualTraceSpine();
        } else if (nTracer.semiAutoTracing_jRadioButton.isSelected()) {
        } else if (nTracer.autoTracing_jRadioButton.isSelected()) {
        }
    }
    
    protected void traceSoma() {
        if (nTracer.membraneLabel_jRadioButton.isSelected()) {
            if (nTracer.manualTracing_jRadioButton.isSelected()) {
                Roi impROI = imp.getRoi();
                if (impROI != null) {
                    if (Toolbar.getToolName().equals("freeline") && impROI.isLine()) {
                        traceSomaROI(impROI);
                    } else {
                        IJ.error("Requires an erea Roi !");
                    }
                } else {
                    traceSomaMinCostPath();
                }
            } else if (nTracer.semiAutoTracing_jRadioButton.isSelected()) {
                traceSomaMinCostPath();
            } else if (nTracer.autoTracing_jRadioButton.isSelected()) {
                // coming soon ...
            }
        } else if (nTracer.cytoplasmLabel_jRadioButton.isSelected()) {
            // coming soon ...
        }
    }

    protected void completeSomaMinCostPath() {
        if (nTracer.displaySomaList_jTree.getSelectionCount() != 1) {
            IJ.error("Select only ONE soma slice to complete its Roi!");
            return;
        }
        ntNeuronNode selectedSomaSliceNode = (ntNeuronNode) nTracer.displaySomaList_jTree.getSelectionPath().getLastPathComponent();
        ArrayList<String[]> sliceTracingResult = selectedSomaSliceNode.getTracingResult();
        if (sliceTracingResult.size() < 2) {
            IJ.error("Requires at least 2 traced points on soma slice to complete its Roi!");
            return;
        }
        String[] firstPt = sliceTracingResult.get(0);
        String[] lastPt = sliceTracingResult.get(sliceTracingResult.size() - 1);
        int[] firstPoint = {0, Integer.parseInt(firstPt[1]), Integer.parseInt(firstPt[2]), Integer.parseInt(firstPt[3]), 0, 0, 0};
        int[] lastPoint = {0, Integer.parseInt(lastPt[1]), Integer.parseInt(lastPt[2]), Integer.parseInt(lastPt[3]), 0, 0, 0};
        if (firstPoint[1] == lastPoint[1] && firstPoint[2] == lastPoint[2]) {
            nTracer.updateInfo("Roi on this soma slice is already complete!");
            return;
        }
        ArrayList<int[]> minCostPathPoints = nTracer.Functions.getMinCostPath2D(lastPoint, firstPoint, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.xyExtension);
        if (minCostPathPoints == null) {
            nTracer.updateInfo(nTracer.endPtTooFarError);
            return;
        }
        //minCostPathPoints = Functions.cubicSmoothingSpline(minCostPathPoints, rho);
        // remove redundant points
        ArrayList<int[]> finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(minCostPathPoints);
        finalPathPoints.remove(finalPathPoints.size() - 1);
        if (finalPathPoints.isEmpty()) {
            nTracer.updateInfo(nTracer.endPtTooFarError);
            return;
        }
        nTracer.recordNeuronTreeExpansionStatus();
        // add tracing result to neuron tree at the soma node
        nTracer.pointTable_jTable.setRowSelectionInterval(nTracer.pointTable_jTable.getRowCount() - 1, nTracer.pointTable_jTable.getRowCount() - 1);
        for (int i = 0; i < 7; i++) {
            nTracer.startPoint[i] = lastPoint[i];
            nTracer.endPoint[i] = firstPoint[i];
        }
        nTracer.hasStartPt = true;
        nTracer.hasEndPt = true;
        int tableSelectRow = addTracingToSoma(nTracer.Functions.convertIntArray2StringArray(finalPathPoints));
        if (tableSelectRow >= 0) {
            nTracer.update.updatePointTable(nTracer.tablePoints);
            if (nTracer.tablePoints != null) {
                nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
                nTracer.scroll2pointTableVisible(tableSelectRow, 0);
                nTracer.restoreNeuronTreeExpansionStatus();
                nTracer.history.saveHistory();
                nTracer.updateDisplay();
                nTracer.updateInfo("Roi on this soma slice is now complete!");
            }
        }
    }

    protected void traceSomaROI(Roi impROI) {
        FloatPolygon roiPolygon = impROI.getFloatPolygon();
        if (roiPolygon.npoints < 1) {
            IJ.error("Need Roi points !");
            nTracer.imp.killRoi();
            nTracer.updateInfo("Need Roi points !");
            return;
        }
        int[] xPts = Roi.toIntR(roiPolygon.xpoints);
        int[] yPts = Roi.toIntR(roiPolygon.ypoints);
        String z = nTracer.imp.getZ() + "";
        ArrayList<String[]> somaPts = new ArrayList<String[]>();
        for (int i = 0; i < roiPolygon.npoints; i++) {
            String[] point = {"0", xPts[i] + "", yPts[i] + "", z, "0", "0", "0"};
            somaPts.add(point);
        }
        nTracer.endPoint[1] = xPts[0];
        nTracer.endPoint[2] = yPts[0];
        nTracer.endPoint[3] = nTracer.imp.getZ();
        if (nTracer.hasStartPt) {
            ArrayList<int[]> minCostPathPoints = nTracer.Functions.getMinCostPath2D(nTracer.startPoint, nTracer.endPoint, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.xyExtension);
            if (minCostPathPoints == null) {
                nTracer.endPoint = new int[7];
                nTracer.hasEndPt = false;
                nTracer.endPosition_jLabel.setText("     ");
                nTracer.endIntensity_jLabel.setText("     ");
                nTracer.endColor_jLabel.setText("     ");
                nTracer.updateDisplay();
                nTracer.updateInfo(nTracer.endPtTooFarError);
                return;
            }
            //minCostPathPoints = Functions.cubicSmoothingSpline(minCostPathPoints, rho);
            // remove redundant points
            ArrayList<int[]> finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(minCostPathPoints);
            nTracer.recordTreeExpansionSelectionStatus();
            // add tracing result to neuron tree at the soma node
            int tableSelectRow = addTracingToSoma(nTracer.Functions.convertIntArray2StringArray(finalPathPoints));
            if (tableSelectRow < 0) {
                nTracer.restoreTreeExpansionSelectionStatus();
                nTracer.updateInfo(nTracer.endPtTooFarError);
                return;
            }
        }
        nTracer.recordTreeExpansionSelectionStatus();
        // add tracing result to neuron tree at the soma node
        int tableSelectRow = addTracingToSoma(somaPts);
        if (tableSelectRow >= 0) {
            nTracer.update.updatePointTable(nTracer.tablePoints);
            if (nTracer.tablePoints != null) {
                nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
                nTracer.scroll2pointTableVisible(tableSelectRow, 0);
                nTracer.history.saveHistory();
                nTracer.updateDisplay();
            }
        } else {
            nTracer.restoreTreeExpansionSelectionStatus();
        }
    }

    /**
    * Semi-Automated tracing using kick-ball to trace from single point
    */
    protected void traceKickBallPath() {
        ArrayList<int[]> kickBallPathPoints;
        kickBallPathPoints = nTracer.Functions.getKickBallPath(nTracer.startPoint, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.colorThreshold, nTracer.xyRadius, nTracer.zRadius, nTracer.maskRadius);
        if (kickBallPathPoints == null) {
            IJ.error("No trace found!");
            return;
        }
        // refine path by cubic spline smoothing
        //kickBallPathPoints = Functions.cubicSmoothingSpline(kickBallPathPoints, rho);
        // remove redundant points
        ArrayList<int[]> finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(kickBallPathPoints);
        nTracer.recordNeuronTreeExpansionStatus();
        int tableSelectRow = addTracingToNeuron(nTracer.Functions.convertIntArray2StringArray(finalPathPoints));
        if (tableSelectRow >= 0) {
            nTracer.update.updatePointTable(nTracer.tablePoints);
            if (nTracer.tablePoints != null) {
                nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
                nTracer.scroll2pointTableVisible(tableSelectRow, 0);
                nTracer.restoreNeuronTreeExpansionStatus();
                nTracer.history.saveHistory();
                nTracer.updateDisplay();
            }
        }
    }

    /**
    * Manual tracing of neurite
    */
    protected void manualTraceNeurite() {
        if (nTracer.hasStartPt && nTracer.hasEndPt) {
            ArrayList<int[]> minCostPathPoints = nTracer.Functions.getMinCostPath3D(nTracer.startPoint, nTracer.endPoint, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.xyExtension, nTracer.zExtension);
            //IJ.log("got min path");
            if (minCostPathPoints == null) {
                nTracer.endPoint = new int[7];
                nTracer.hasEndPt = false;
                nTracer.endPosition_jLabel.setText("     ");
                nTracer.endIntensity_jLabel.setText("     ");
                nTracer.endColor_jLabel.setText("     ");
                nTracer.updateDisplay();
                nTracer.updateInfo(nTracer.endPtTooFarError);
                return;
            }
            // refine path by cubic spline smoothing
            //minCostPathPoints = Functions.cubicSmoothingSpline(minCostPathPoints, rho);
            // remove redundant points
            ArrayList<int[]> finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(minCostPathPoints);
            nTracer.recordNeuronTreeExpansionStatus();
            // add tracing result to neuron tree
            int tableSelectRow = addTracingToNeuron(nTracer.Functions.convertIntArray2StringArray(finalPathPoints));
            if (tableSelectRow >= 0) {
                nTracer.update.updatePointTable(nTracer.tablePoints);
                if (nTracer.tablePoints != null) {
                    nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
                    nTracer.scroll2pointTableVisible(tableSelectRow, 0);
                    nTracer.restoreNeuronTreeExpansionStatus();
                    nTracer.history.saveHistory();
                    nTracer.updateDisplay();
                    nTracer.updateInfo(nTracer.pickNextEndPt);
                }
            }
        } else {
            nTracer.updateInfo("Pick both Start Point and End Point before Manual Tracing!");
        }
    }

    /**
    * Semi-automated tracing of neurite
    */
    protected void semiAutoTraceNeurite() {
        if (nTracer.hasStartPt && nTracer.hasEndPt) {
            ArrayList<int[]> minCostPathPoints = nTracer.Functions.getMinCostPath3D(nTracer.startPoint, nTracer.endPoint, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.xyExtension, nTracer.zExtension);
            //IJ.log("got min path");
            if (minCostPathPoints == null) {
                nTracer.endPoint = new int[7];
                nTracer.hasEndPt = false;
                nTracer.endPosition_jLabel.setText("     ");
                nTracer.endIntensity_jLabel.setText("     ");
                nTracer.endColor_jLabel.setText("     ");
                nTracer.updateDisplay();
                nTracer.updateInfo(nTracer.endPtTooFarError);
                return;
            }
            // refine path by cubic spline smoothing
            //minCostPathPoints = Functions.cubicSmoothingSpline(minCostPathPoints, rho);
            // remove redundant points
            ArrayList<int[]> finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(minCostPathPoints);
            nTracer.recordNeuronTreeExpansionStatus();
            // add tracing result to neuron tree
            int tableSelectRow1 = addTracingToNeuron(nTracer.Functions.convertIntArray2StringArray(finalPathPoints));
            if (tableSelectRow1 >= 0) {
                if (nTracer.tablePoints != null) {
                    System.arraycopy(nTracer.endPoint, 0, nTracer.startPoint, 0, 7);
                    nTracer.endPoint = new int[7];
                    nTracer.hasEndPt = false;
                    nTracer.endPosition_jLabel.setText("     ");
                    nTracer.endIntensity_jLabel.setText("     ");
                    nTracer.endColor_jLabel.setText("     ");
                    // continue tracing
                    /*
                    minCostPathPoints = Functions.getMinCostPath3D(
                    startPoint, endPoint, analysisChannels, imp.getFrame(),
                    xyExtension, zExtension);
                     */
                    minCostPathPoints = nTracer.Functions.semiAutoGetMinCostPath3D(nTracer.startPoint, nTracer.tablePoints, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.xyRadius, nTracer.zRadius, nTracer.colorThreshold, nTracer.intensityThreshold);
                    if (minCostPathPoints == null) {
                        nTracer.update.updatePointTable(nTracer.tablePoints);
                        nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow1, tableSelectRow1);
                        nTracer.scroll2pointTableVisible(tableSelectRow1, 0);
                    } else {
                        // refine path by cubic spline smoothing
                        //minCostPathPoints = Functions.cubicSmoothingSpline(minCostPathPoints, rho);
                        // remove redundant points
                        finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(minCostPathPoints);
                        int tableSelectRow2 = addTracingToNeuron(nTracer.Functions.convertIntArray2StringArray(finalPathPoints));
                        nTracer.update.updatePointTable(nTracer.tablePoints);
                        if (tableSelectRow2 >= 0) {
                            nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow2, tableSelectRow2);
                            nTracer.scroll2pointTableVisible(tableSelectRow2, 0);
                        } else {
                            nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow1, tableSelectRow1);
                            nTracer.scroll2pointTableVisible(tableSelectRow1, 0);
                        }
                    }
                    nTracer.restoreNeuronTreeExpansionStatus();
                    nTracer.history.saveHistory();
                    nTracer.updateDisplay();
                    nTracer.updateInfo(nTracer.pickNextEndPt);
                }
            }
        } else {
            nTracer.updateInfo("Pick both Start Point and End Point before Semi-Auto Tracing!");
        }
    }

    /**
    * Trace soma min cost path
    */
    protected void traceSomaMinCostPath() {
        if (nTracer.hasStartPt && nTracer.hasEndPt) {
            if (nTracer.startPoint[3] == nTracer.endPoint[3]) {
                ArrayList<int[]> minCostPathPoints = nTracer.Functions.getMinCostPath2D(nTracer.startPoint, nTracer.endPoint, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.xyExtension);
                if (minCostPathPoints == null) {
                    nTracer.endPoint = new int[7];
                    nTracer.hasEndPt = false;
                    nTracer.endPosition_jLabel.setText("     ");
                    nTracer.endIntensity_jLabel.setText("     ");
                    nTracer.endColor_jLabel.setText("     ");
                    //updateDisplay();
                    nTracer.updateInfo(nTracer.endPtTooFarError);
                    return;
                }
                //minCostPathPoints = Functions.cubicSmoothingSpline(minCostPathPoints, rho);
                // remove redundant points
                ArrayList<int[]> finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(minCostPathPoints);
                nTracer.recordTreeExpansionSelectionStatus();
                // add tracing result to neuron tree at the soma node
                int tableSelectRow = addTracingToSoma(nTracer.Functions.convertIntArray2StringArray(finalPathPoints));
                nTracer.restoreTreeExpansionSelectionStatus();
                if (tableSelectRow >= 0) {
                    nTracer.update.updatePointTable(nTracer.tablePoints);
                    if (nTracer.tablePoints != null) {
                        nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
                        nTracer.scroll2pointTableVisible(tableSelectRow, 0);
                        nTracer.history.saveHistory();
                        nTracer.updateDisplay();
                        nTracer.updateInfo(nTracer.pickNextEndPt);
                    }
                }
            } else {
                IJ.error("Start and End Points need to be on the same slice!");
                nTracer.updateInfo("Start and End Points need to be on the same slice!");
            }
        } else {
            nTracer.updateInfo("Pick both Start and End Points before Linking!");
        }
    }

    /**
    * Fully automated tracing
    */
    protected void autoTraceNeurite() {
        IJ.error("Coming soon ...");
    }
    
    /**
    * Manual tracing using A-star to find minimum cost path between two points.
    */
    protected void manualTraceSpine() {
        if (nTracer.hasStartPt && nTracer.hasEndPt) {
            ArrayList<int[]> minCostPathPoints = nTracer.Functions.getMinCostPath3D(nTracer.startPoint, nTracer.endPoint, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.xyExtension, nTracer.zExtension);
            //IJ.log("got min path");
            if (minCostPathPoints == null) {
                nTracer.endPoint = new int[7];
                nTracer.hasEndPt = false;
                nTracer.endPosition_jLabel.setText("     ");
                nTracer.endIntensity_jLabel.setText("     ");
                nTracer.endColor_jLabel.setText("     ");
                nTracer.updateDisplay();
                nTracer.updateInfo(nTracer.endPtTooFarError);
                return;
            }
            // refine path by cubic spline smoothing
            //minCostPathPoints = Functions.cubicSmoothingSpline(minCostPathPoints, rho);
            // remove redundant points
            ArrayList<int[]> finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(minCostPathPoints);
            // add tracing result to neuron tree
            addTracingAsSpine(nTracer.Functions.convertIntArray2StringArray(finalPathPoints));
            nTracer.history.saveHistory();
            nTracer.updateDisplay();
            nTracer.updateInfo("Spine traced !");
        } else {
            nTracer.updateInfo("Pick both Start Point and End Point before Manual Tracing!");
        }
    }

    protected void traceOutLinkPath() {
        if (nTracer.hasStartPt) {
            nTracer.recordNeuronTreeExpansionStatus();
            ArrayList<int[]> outLinkPathPoints = nTracer.Functions.getOutLinkPath(nTracer.startPoint, nTracer.analysisChannels, nTracer.imp.getFrame(), nTracer.colorThreshold, nTracer.intensityThreshold, nTracer.xyRadius, nTracer.zRadius, nTracer.outLinkXYradius);
            if (outLinkPathPoints.isEmpty()) {
                IJ.error("No trace found!");
                return;
            }
            int tableSelectRow; // = addPointsToResult(outLinkPathPoints);
            ArrayList<ArrayList<int[]>> clustered = nTracer.Functions.clusterOutLinkPathPoints(outLinkPathPoints);
            ArrayList<String[]> clusteredCentOfInt = new ArrayList<String[]>();
            for (int i = 0; i < clustered.size(); i++) {
                ArrayList<int[]> cluster = clustered.get(i);
                //debug //IJ.log("cluster "+i);
                //debug //tablePoints = clustered.get(i);
                //debug //tableSelectRow = addPointsToResult(cluster);
                String[] clusterCentOfInt = nTracer.Functions.getCentOfInt(cluster, nTracer.analysisChannels, nTracer.imp.getFrame());
                clusteredCentOfInt.add(clusterCentOfInt);
                IJ.log("cluster " + i + ": centOfInt [" + clusterCentOfInt[1] + ", " + clusterCentOfInt[2] + ", " + clusterCentOfInt[3] + "]");
            }
            tableSelectRow = addTracingToNeuron(clusteredCentOfInt);
            if (tableSelectRow >= 0) {
                nTracer.update.updatePointTable(nTracer.tablePoints);
                if (!nTracer.tablePoints.isEmpty()) {
                    nTracer.pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
                    nTracer.scroll2pointTableVisible(tableSelectRow, 0);
                    nTracer.restoreNeuronTreeExpansionStatus();
                    nTracer.history.saveHistory();
                    nTracer.updateDisplay();
                }
            }
        }
    }
    
    protected int addTracingToSoma(ArrayList<String[]> points) {
        if (nTracer.neuronList_jTree.getSelectionCount() == 0) {// start fresh tracing
            for (int i = 0; i < points.size(); i++) {
                String[] addPoint = points.get(i);
                addPoint[0] = "Soma";
                nTracer.tablePoints.add(i, addPoint);
            }
            // create a new soma
            createNewNeuronWithSomaData(nTracer.tablePoints);
            return nTracer.tablePoints.size() - 1;
        } else if (nTracer.neuronList_jTree.getSelectionCount() == 1) {
            // try to add to soma - selected soma node in neuronList_Tree
            ntNeuronNode selectedNode = (ntNeuronNode) nTracer.neuronList_jTree.getLastSelectedPathComponent();
            if (selectedNode.getParent().equals(rootNeuronNode)) {
                if (nTracer.displaySomaList_jTree.getSelectionCount() == 0) { // add slice to soma
                    for (int i = 0; i < points.size(); i++) {
                        String[] addPoint = points.get(i);
                        addPoint[0] = "Soma";
                        nTracer.tablePoints.add(i, addPoint);
                    }
                    // create new soma slice or replace existing soma slice
                    insertNewSomaSliceIntoSelectedNeuronTreeSoma(selectedNode);
                    return nTracer.tablePoints.size() - 1;
                } else if (nTracer.displaySomaList_jTree.getSelectionCount() == 1) {
                    if (nTracer.pointTable_jTable.getSelectedRowCount() == 0) { // replace tracing on a z-plane
                        return nTracer.tablePoints.size() - 1;
                    } else if (nTracer.pointTable_jTable.getSelectedRowCount() == 1) { // add tracing to selescted soma z-plane
                        String[] add1Pt = points.get(0);
                        String[] table1Pt = nTracer.tablePoints.get(0);
                        String[] tableNPt = nTracer.tablePoints.get(nTracer.tablePoints.size() - 1);
                        // add in front of first point
                        if (add1Pt[1].equals(table1Pt[1])
                                && add1Pt[2].equals(table1Pt[2])
                                && add1Pt[3].equals(table1Pt[3])) {
                            addToFrontOfSelectedSoma(points);
                            return 0;
                        } // add after last point
                        else if (add1Pt[1].equals(tableNPt[1])
                                && add1Pt[2].equals(tableNPt[2])
                                && add1Pt[3].equals(tableNPt[3])) {
                            addToEndOfSelectedSoma(points);
                            return nTracer.tablePoints.size() - 1;
                        } else {
                            return -1;
                        }
                    } else { // do nothing if selected more than 1 points
                        return -1;
                    }
                } else { // do nothing if selected more than 1 soma z-planes
                    return -1;
                }
            } else { // cannot add to neurite
                return -1;
            }
        } else { // do nothing if selected more than 1 neurons
            return -1;
        }
    }
    
    protected void changeTracingCompleteness() {
        if (nTracer.neuronList_jTree.getSelectionCount() > 0) {
            nTracer.recordTreeExpansionSelectionStatus();
            TreePath[] selectedPaths = nTracer.neuronList_jTree.getSelectionPaths();
            for (TreePath selectedPath : selectedPaths) {
                ntNeuronNode node = (ntNeuronNode) selectedPath.getLastPathComponent();
                node.toggleComplete();
            }
            nTracer.updateTrees();
            nTracer.restoreTreeExpansionSelectionStatus();
        }
    }
    
    /**
    * Break branch of trace
    */
    protected void breakBranch() {
        ntNeuronNode selectedBranchNode = (ntNeuronNode) nTracer.neuronList_jTree.getLastSelectedPathComponent();
        if (!(nTracer.pointTable_jTable.getSelectedRowCount() == 1 && selectedBranchNode.isBranchNode())) {
            IJ.error("Select ONE branch to break !");
            return;
        }
        int breakPosition = nTracer.pointTable_jTable.getSelectedRow();
        if (breakPosition >= selectedBranchNode.getTracingResult().size()) {
            IJ.error("Break position is outside of tracing result !");
            return;
        }
        if (breakPosition == nTracer.pointTable_jTable.getRowCount() - 1) {
            IJ.error("Cannot break at a terminal point !");
            return;
        }
        //saveHistory();
        nTracer.recordTreeExpansionSelectionStatus();

        String createdPrimaryBranchName = breakBranchByBranchNode(selectedBranchNode, breakPosition);

        nTracer.updateTrees();
        nTracer.restoreTreeExpansionSelectionStatus();
        ntNeuronNode createdPrimaryBranchNode = nTracer.getTracingNodeByNodeName(createdPrimaryBranchName);
        TreePath newNodeTreePath = new TreePath(createdPrimaryBranchNode.getPath());
        nTracer.neuronList_jTree.expandPath(newNodeTreePath);
        nTracer.neuronList_jTree.setSelectionPath(newNodeTreePath);
        nTracer.neuronList_jTree.scrollPathToVisible(newNodeTreePath);
        nTracer.pointTable_jTable.setRowSelectionInterval(0, 0);
        nTracer.history.saveHistory();
        nTracer.updateDisplay();
    }
    
    /**
    * Join branches
    */
    protected void joinBranches() {
        if (nTracer.neuronList_jTree.getSelectionCount() != 1 || nTracer.editTargetNodeName.equals("0")) {
            IJ.error("Select one branch from the tree list and" + "/n"
                    + "get Second Selection branch from screen to combine!");
            return;
        }
        ntNeuronNode node0 = (ntNeuronNode) (nTracer.neuronList_jTree.getLastSelectedPathComponent());
        String node0Name = node0.toString();
        String node0NeuronNumber = node0.getNeuronNumber();

        ntNeuronNode node1 = nTracer.getTracingNodeByNodeName(nTracer.editTargetNodeName);
        String node1Name = node1.toString();
        String node1NeuronNumber = node1.getNeuronNumber();
        if (node0NeuronNumber.equals(node1NeuronNumber)) {
            IJ.error("Select branches from two different neurons to join !");
            return;
        }
        if (!node0.isTerminalBranchNode()) {
            IJ.error("Neuron " + node0Name + " needs to be a terminal branch !");
            return;
        }
        if (!node1.isTerminalBranchNode()) {
            IJ.error("Neuron " + node1Name + " needs to be a terminal branch !");
            return;
        }
        if (!node0Name.contains("-")) {
            IJ.error("Neuron Tree Selection " + node0Name + " is a soma Slice!" + "/n"
                    + "Get a terminal branch instead !");
            return;
        }
        if (node1Name.contains(":")) {
            IJ.error("Second Selection " + node1Name + " is a soma Slice!" + "/n"
                    + "Get a terminal branch instead !");
            return;
        }
        ntNeuronNode somaSomaNode0 = getSomaNodeFromAllSomaTreeByNeuronNumber(node0NeuronNumber);
        ntNeuronNode somaSomaNode1 = getSomaNodeFromAllSomaTreeByNeuronNumber(node1NeuronNumber);
        if (somaSomaNode0.getChildCount() > 0 && somaSomaNode1.getChildCount() > 0) {
            IJ.error("Both neurons contain soma tracing results !" + "/n" + "Use 'Combine 2' neurons instead !");
            return;
        }

        // targetNode eventually keep in neuronTree; sourceNode eventually remove from neuronTree
        // the following codes try to determine which node is targetNode:
        // default: node1 has soma traced, while node0 has NO soma traced
        ntNeuronNode sourceNode = node0;
        String type0 = node0.getType();
        ntNeuronNode targetNode = node1;
        String type1 = node1.getType();

        // or if node0 has soma traced, while node1 has NO soma traced
        if (somaSomaNode0.getChildCount() > 0) {
            sourceNode = node1;
            targetNode = node0;
        } else { // or if both neurons have NO soma traced
            // set neuron contains more arbors as targetNode -- default is node1
            ntNeuronNode neuronSomaNode0 = getSomaNodeFromNeuronTreeByNeuronNumber(node0NeuronNumber);
            ntNeuronNode neuronSomaNode1 = getSomaNodeFromNeuronTreeByNeuronNumber(node1NeuronNumber);

            // if node0 has more arbors, then set node0 as targetNode
            if (neuronSomaNode0.getChildCount() > neuronSomaNode1.getChildCount()) {
                sourceNode = node1;
                targetNode = node0;
            } else if (neuronSomaNode0.getChildCount() == neuronSomaNode1.getChildCount()) {
                // if both contains the same number of arbors
                // then the smaler neuron number is set to targetNode : default targetNode is node1
                if (Integer.parseInt(node0NeuronNumber) < Integer.parseInt(node1NeuronNumber)) {
                    // when neither neuron contains soma tracing result, neuron number determine sourceNode and targetNode
                    sourceNode = node1;
                    targetNode = node0;
                }
            }
        }
        //IJ.log("sourceNode = "+sourceNode.toString());
        //IJ.log("targetNode = "+targetNode.toString());

        // then determines the connecting points, which are the closet two end points between the two nodes
        ArrayList<String[]> targetTracingPts = targetNode.getTracingResult();
        String[] targetFirstPoint = targetTracingPts.get(0);
        String[] targetLastPoint = targetTracingPts.get(targetTracingPts.size() - 1);

        ArrayList<String[]> sourceTracingPts = sourceNode.getTracingResult();
        String[] sourceFirstPoint = sourceTracingPts.get(0);
        String[] sourceLastPoint = sourceTracingPts.get(sourceTracingPts.size() - 1);
        int[] s0 = {Integer.parseInt(sourceFirstPoint[1]), Integer.parseInt(sourceFirstPoint[2]), Integer.parseInt(sourceFirstPoint[3]), 0, 0};
        int[] s1 = {Integer.parseInt(sourceLastPoint[1]), Integer.parseInt(sourceLastPoint[2]), Integer.parseInt(sourceLastPoint[3]), 0, 0};

        //IJ.log("t0s1 = "+t0s1+"; t1s1 = "+t1s1+"; t0s0 = "+t0s0+"; t1s0 = "+t1s0);
        if (targetNode.isPrimaryBranchNode() && !targetNode.isLeaf()) {
            ntNeuronNode newTragetPrimaryNode = (ntNeuronNode) targetNode.getChildAt(0);
            while (newTragetPrimaryNode.getChildCount() > 0) {
                newTragetPrimaryNode = (ntNeuronNode) newTragetPrimaryNode.getChildAt(0);
            }
            setPrimaryBranchByTernimalNode(newTragetPrimaryNode);
            newTragetPrimaryNode = nTracer.getTracingNodeByNodeName(targetNode.toString());
            targetNode = searchNodeByEndPoints(targetFirstPoint, targetLastPoint, newTragetPrimaryNode);
        }

        targetFirstPoint = targetTracingPts.get(0);
        targetLastPoint = targetTracingPts.get(targetTracingPts.size() - 1);
        int[] t0 = {Integer.parseInt(targetFirstPoint[1]), Integer.parseInt(targetFirstPoint[2]), Integer.parseInt(targetFirstPoint[3]), 0, 0};
        int[] t1 = {Integer.parseInt(targetLastPoint[1]), Integer.parseInt(targetLastPoint[2]), Integer.parseInt(targetLastPoint[3]), 0, 0};
        // now needs to determine whether tracing results of sourceNode and/or targetNode need to be inverted
        int t0s1 = nTracer.Functions.getPointDistanceSquare(t0, s1);
        int t1s1 = nTracer.Functions.getPointDistanceSquare(t1, s1);
        int t0s0 = nTracer.Functions.getPointDistanceSquare(t0, s0);
        int t1s0 = nTracer.Functions.getPointDistanceSquare(t1, s0);

        if (targetNode.isPrimaryBranchNode() && targetNode.isLeaf()) {
            //IJ.log(targetNode.toString() + " is primary branch");
            if (sourceNode.isPrimaryBranchNode() && sourceNode.isLeaf()) {
                //IJ.log(sourceNode.toString() + " is primary branch");
                if (t0s0 < t0s1 && t0s0 < t1s1 && t0s0 < t1s0) {
                    targetNode.invertTracingResult();
                    sourceNode.invertTracingResult();
                    //IJ.log("invert both");
                } else if (t0s1 < t1s1 && t0s1 < t0s0 && t0s1 < t1s0) {
                    targetNode.invertTracingResult();
                    //IJ.log("invert target");
                } else if (t1s0 < t0s1 && t1s0 < t1s1 && t1s0 < t0s0) {
                    sourceNode.invertTracingResult();
                    //IJ.log("invert source");
                }
            } else {
                if (t0s1 < t1s1 && t0s1 < t0s0 && t0s1 < t1s0) {
                    targetNode.invertTracingResult();
                    //IJ.log("invert target");
                }
            }
        } else {
            if (sourceNode.isPrimaryBranchNode() && sourceNode.isLeaf()) {
                //IJ.log(sourceNode.toString() + " is primary branch");
                if (t1s0 < t0s1 && t1s0 < t1s1 && t1s0 < t0s0) {
                    sourceNode.invertTracingResult();
                    //IJ.log("invert source");
                }
            }
        }

        // trace between end points of targetNode and sourceNode
        // add result to the end of targetNode
        targetTracingPts = targetNode.getTracingResult();
        int originalLastTargetPoint = targetTracingPts.size() - 1;
        String[] targetPoint = targetTracingPts.get(originalLastTargetPoint);
        sourceTracingPts = sourceNode.getTracingResult();
        String[] sourcePoint = sourceTracingPts.get(sourceTracingPts.size() - 1);
        if (sourceNode.isPrimaryBranchNode() && !sourceNode.isLeaf()) {
            sourcePoint = sourceTracingPts.get(0);
        }
        int[] startPt = {0, Integer.parseInt(targetPoint[1]), Integer.parseInt(targetPoint[2]),
            Integer.parseInt(targetPoint[3]), 0, 0, 0};
        int[] endPt = {0, Integer.parseInt(sourcePoint[1]), Integer.parseInt(sourcePoint[2]),
            Integer.parseInt(sourcePoint[3]), 0, 0, 0};

        ArrayList<String[]> addPts = new ArrayList<>();
        // get minimal cost path between two points when they are not at the same location
        if (!(startPt[1] == endPt[1] && startPt[2] == endPt[2] && startPt[3] == endPt[3])) {
            ArrayList<int[]> minCostPathPoints = nTracer.Functions.getMinCostPath3D(
                    startPt, endPt, analysisChannels, imp.getFrame(),
                    nTracer.xyExtension, nTracer.zExtension);
            if (minCostPathPoints == null) {
                IJ.error("The two branches are too far to join!" + "/n"
                        + "Try to trace them closer first!");
                return;
            }
            // refine path by cubic spline smoothing
            //minCostPathPoints = Functions.cubicSmoothingSpline(minCostPathPoints, rho);
            // remove redundant points
            ArrayList<int[]> finalPathPoints = nTracer.Functions.removeRedundantTracingPoints(minCostPathPoints);
            // convert new tracing result to String[] Array
            addPts = nTracer.Functions.convertIntArray2StringArray(finalPathPoints);
        }

        //saveHistory();
        nTracer.recordTreeExpansionSelectionStatus();

        // add finalPathPoints to targetResult
        String targetType = targetNode.getType();
        for (int i = 1; i < addPts.size() - 1; i++) {
            String[] addPt = addPts.get(i);
            addPt[0] = targetType;
            targetTracingPts.add(addPt);
        }
        targetNode.setTracingResult(targetTracingPts);
        joinSouceToTargetBranchByNode(targetNode, sourceNode);

        nTracer.updateTrees();
        nTracer.restoreTreeExpansionSelectionStatus();
        String targetNodeName = targetNode.toString();
        ntNeuronNode jointBranchNode = nTracer.getTracingNodeByNodeName(targetNodeName);
        TreePath jointBranchPath = new TreePath(jointBranchNode.getPath());
        nTracer.neuronList_jTree.setSelectionPath(jointBranchPath);
        nTracer.neuronList_jTree.scrollPathToVisible(jointBranchPath);
        nTracer.pointTable_jTable.setRowSelectionInterval(originalLastTargetPoint, originalLastTargetPoint);
        nTracer.scroll2pointTableVisible(originalLastTargetPoint, 0);
        // set tracing type
        if (!type0.equals(type1)) {
            if (!type0.equals("Neurite") && !type1.equals("Neurite")) {
                nTracer.setTracingType("Neurite");
            } else if (type0.equals("Neurite")) {
                nTracer.setTracingType(type1);
            } else if (type1.equals("Neurite")) {
                nTracer.setTracingType(type0);
            }
        }
        nTracer.history.saveHistory();
        nTracer.updateDisplay();
    }
    
    /**
    * Set primary branch
    */
    protected void setPrimaryBranch() {
        if (nTracer.neuronList_jTree.getSelectionCount() != 1) {
            IJ.error("Select only ONE branch !");
            return;
        }
        ntNeuronNode selectedNode = (ntNeuronNode) nTracer.neuronList_jTree.getLastSelectedPathComponent();
        if (!selectedNode.isTerminalBranchNode()) {
            IJ.error("Select a terminal branch !");
            return;
        }

        String[] names = selectedNode.toString().split("-");
        String primaryBranchName = names[0] + "-" + names[1];

        //saveHistory();
        nTracer.recordNeuronTreeExpansionStatus();

        setPrimaryBranchByTernimalNode(selectedNode);

        nTracer.updateTrees();
        nTracer.restoreNeuronTreeExpansionStatus();
        ntNeuronNode selectedPrimaryBranchNode = nTracer.getNodeFromNeuronTreeByNodeName(primaryBranchName);
        if (selectedPrimaryBranchNode != null) {
            TreePath selectedNeuronPath = new TreePath(selectedPrimaryBranchNode.getPath());
            nTracer.neuronList_jTree.setSelectionPath(selectedNeuronPath);
            nTracer.pointTable_jTable.setRowSelectionInterval(0, 0);
            nTracer.history.saveHistory();
        }
    }
    
    /**
    * Delete branch from neuron tree
    */
    protected void deleteOneBranchFromNeuronTree() {
        if (nTracer.neuronList_jTree.getSelectionCount() == 1) {
            ntNeuronNode delNode = (ntNeuronNode) (nTracer.neuronList_jTree.getLastSelectedPathComponent());
            if (delNode.isBranchNode()) { // selected branch node
                //saveHistory();
                nTracer.recordTreeExpansionSelectionStatus();

                //somehow MUST deselecte everyting in neuronList_jTree before deleting! 
                nTracer.neuronList_jTree.clearSelection();

                deleteOneBranchAndChildByNode(delNode);

                nTracer.updateTrees();
                nTracer.restoreTreeExpansionSelectionStatus();
                nTracer.history.saveHistory();
            }
           nTracer. updateDisplay();
        }
    }
    
    /**
    * Delete branch and its child node
    */
    protected void deleteBranchAndChildNode(ntNeuronNode delParentBranchNode) {

        // delete all nodes by recursion -- delete the most distal branches first
        for (int i = delParentBranchNode.getChildCount() - 1; i >= 0; i--) {
            ntNeuronNode childNode = (ntNeuronNode) delParentBranchNode.getChildAt(i);
            deleteBranchAndChildNode(childNode);
        }
        String selectedNodeName = delParentBranchNode.toString();
        ArrayList<String[]> tracingPts = delParentBranchNode.getTracingResult();
        // remove all the connected synapses from all branch tracing points
        for (int i = 0; i < tracingPts.size(); i++) {
            String[] tracingPt = tracingPts.get(i);
            if (!tracingPt[6].equals("0")) {
                nTracer.removeConnectionBySelectedNodeAndSynapseName(selectedNodeName, tracingPt[6]);
            }
            // determine whether a spine needs to be removed
            if (tracingPt[0].contains(":Spine#")) {
                removeSpine(tracingPt[0]);
                delParentBranchNode.setSpine(i, "0");
            }
        }
        // delete node from neuronTreeModel
        nTracer.neuronTreeModel.removeNodeFromParent(delParentBranchNode);
    }
    
    /**
     * Find a node by name and replace a current node
     */
    protected void renameNodeByNewNodeNameAndSetConnection(ntNeuronNode node, String newNodeName, int synapseNumberOffset) {
        // set the node's connection first with synapseNumberOffset 
        String oldNodeName = node.toString();
        if (oldNodeName.contains("/")) {
            oldNodeName = oldNodeName.split("/")[0];
        }
        if (newNodeName.contains("/")) {
            newNodeName = newNodeName.split("/")[0];
        }
        ArrayList<String[]> nodeTracingResult = node.getTracingResult();
        for (int i = 0; i < nodeTracingResult.size(); i++) {
            String[] tracingResult = nodeTracingResult.get(i);
            String selectedSynapseName = tracingResult[6];
            if (!selectedSynapseName.equals("0")) {
                String[] connectedNames = selectedSynapseName.split("#");
                String connectedNodeName = connectedNames[1];
                // search connectedNode and connectionPosition by oldConnectedSynapseName
                String oldConnectedSynapseName = connectedNames[2] + "#" + oldNodeName + "#" + connectedNames[0];
                ntNeuronNode connectedNode = nTracer.getTracingNodeByNodeName(connectedNodeName);
                int connectedPosition = nTracer.getPositionInTracingResultBySynapseName(connectedNode.getTracingResult(), oldConnectedSynapseName);
                String newConnectedSynapseName = connectedNames[2] + "#" + newNodeName + "#" + (Integer.parseInt(connectedNames[0]) + synapseNumberOffset);
                String newTargetSynapseName = (Integer.parseInt(connectedNames[0]) + synapseNumberOffset) + "#" + connectedNodeName + "#" + connectedNames[2];
                String newNodeNumber = newNodeName;
                if (newNodeNumber.contains("-")) {
                    newNodeNumber = newNodeNumber.split("-")[0];
                } else if (newNodeNumber.contains(":")) {
                    newNodeNumber = newNodeNumber.split(":")[0];
                }
                String connectedNodeNumber = connectedNodeName;
                if (connectedNodeNumber.contains("-")) {
                    connectedNodeNumber = connectedNodeNumber.split("-")[0];
                } else if (connectedNodeNumber.contains(":")) {
                    connectedNodeNumber = connectedNodeNumber.split(":")[0];
                }
                if (newNodeNumber.equals(connectedNodeNumber)) {
                    connectedNode.setConnectionTo(connectedPosition, "0");
                    node.setConnectionTo(i, "0");
                } else {
                    // set connection to newConnectedSynapseName in connectedNode
                    connectedNode.setConnectionTo(connectedPosition, newConnectedSynapseName);
                    // set connection to newTargetSynapseName in node
                    node.setConnectionTo(i, newTargetSynapseName);
                }
            }
        }

        // then rename the node
        node.setName(newNodeName);
    }

    protected int getNextPrimaryBranchNodePositionINneuronSomaNode(ntNeuronNode neuronSomaNode) {
        for (int n = 0; n < neuronSomaNode.getChildCount(); n++) {
            ntNeuronNode primaryBranchNode = (ntNeuronNode) neuronSomaNode.getChildAt(n);
            String[] primaryNames = primaryBranchNode.toString().split("-");
            int somaNumber = Integer.parseInt(primaryNames[1]);
            if (somaNumber > n + 1) {
                return n;
            }
        }
        return neuronSomaNode.getChildCount();
    }
    
    /**
     * Remove spine from spine tree
     */
    protected void removeSpine(String spineTag) {
        //IJ.log("spineTag = "+spineTag);
        ntNeuronNode removeNode = getSpineNode(spineTag);
        //IJ.log("removeNode = "+removeNode.toString());
        if (removeNode != null) {
            nTracer.spineTreeModel.removeNodeFromParent(removeNode);
            //IJ.log("removed ");
        }
    }
    
    /**
     * Get spine from spine tree
     */
    protected ntNeuronNode getSpineNode(String spineTag) {
        int totalSpine = rootSpineNode.getChildCount();
        String spineNumber = getSpineNumberFromTag(spineTag);
        //IJ.log("#"+spineNumber);
        if (Integer.parseInt(spineNumber) >= totalSpine) {
            for (int i = totalSpine - 1; i >= 0; i--) {
                ntNeuronNode spineNode = (ntNeuronNode) rootSpineNode.getChildAt(i);
                if (spineNumber.equals(spineNode.toString())) {
                    return spineNode;
                }
            }
        } else {
            for (int i = Integer.parseInt(spineNumber) - 1; i >= 0; i--) {
                ntNeuronNode spineNode = (ntNeuronNode) rootSpineNode.getChildAt(i);
                if (spineNumber.equals(spineNode.toString())) {
                    return spineNode;
                }
            }
        }
        return null;
    }
    
    /**
    * Add tracing point as neuron
    */
    private int addTracingToNeuron(ArrayList<String[]> points) {
        //IJ.log("tablePoints is empty? " + (tablePoints.isEmpty()));
        if (nTracer.neuronList_jTree.getSelectionCount() == 0) { // start fresh tracing
            nTracer.tablePoints.clear();
            for (int i = 0; i < points.size(); i++) {
                String[] addPoint = points.get(i);
                addPoint[0] = "Neurite";
                nTracer.tablePoints.add(i, addPoint);
                //IJ.log("added (" + tablePoints.get(i)[1] + ", " + tablePoints.get(i)[2] + ", " + tablePoints.get(i)[3] + ")");
            }
            createNewNeuronWithNeuriteData(nTracer.tablePoints);
            return nTracer.tablePoints.size() - 1;
        } else if (nTracer.neuronList_jTree.getSelectionCount() == 1) { // add to existing neuron
            ntNeuronNode selectedNode = (ntNeuronNode) nTracer.neuronList_jTree.getLastSelectedPathComponent();
            if (selectedNode.isBranchNode()) { // selected one neurite
                if (nTracer.pointTable_jTable.getSelectedRowCount() == 1) { // add to existing tracing
                    //IJ.log("selected rows = "+pointTable_jTable.getSelectedRowCount());
                    String[] add1Pt = points.get(0);
                    String[] table1Pt = nTracer.tablePoints.get(0);
                    String nodeType = selectedNode.getType();
                    String[] tableNPt = nTracer.tablePoints.get(nTracer.tablePoints.size() - 1);
                    for (String[] addpoint : points) {
                        addpoint[0] = nodeType;
                    }
                    // add in front of first point
                    if (add1Pt[1].equals(table1Pt[1])
                            && add1Pt[2].equals(table1Pt[2])
                            && add1Pt[3].equals(table1Pt[3])) {
                        addToFrontOfSelectedBranch(points);
                        return 0;
                    } // add after last point
                    else if (add1Pt[1].equals(tableNPt[1])
                            && add1Pt[2].equals(tableNPt[2])
                            && add1Pt[3].equals(tableNPt[3])) {
                        addToEndOfSelectedBranch(points);
                        return nTracer.tablePoints.size() - 1;
                    } else { // add a branch?
                        YesNoCancelDialog yncDialog = new YesNoCancelDialog(new java.awt.Frame(),
                                "", "Add a new branch?");
                        if (yncDialog.yesPressed()) {
                            createNewBranch(points);
                            return nTracer.tablePoints.size() - 1;
                        } else { // do nothing
                            return -1;
                        }
                    }
                } else {// do nothing
                    return -1;
                }
            } else { // selected a soma -- create a new primary arbor
                for (String[] addpoint : points) {
                    addpoint[0] = "Neurite";
                }
                insertNewArborPrimaryBranch(selectedNode, points);
                return nTracer.tablePoints.size() - 1;
            }
        } else { // add a neuron?
            //IJ.log("selected rows = "+pointTable_jTable.getSelectedRowCount());
            YesNoCancelDialog yncDialog = new YesNoCancelDialog(new java.awt.Frame(),
                    "", "Add a new neuron?");
            if (yncDialog.yesPressed()) {
                nTracer.tablePoints = new ArrayList<String[]>();
                for (String[] point : points) {
                    nTracer.tablePoints.add(nTracer.tablePoints.size(), point);
                }
                createNewNeuronWithNeuriteData(nTracer.tablePoints);
                return nTracer.tablePoints.size() - 1;
            } else { // do nothing
                return -1;
            }
        }
    }
    
    /**
    * Add tracing point as spine
    */
    private void addTracingAsSpine(ArrayList<String[]> points) {
        //IJ.log("tablePoints is empty? " + (tablePoints.isEmpty()));
        if (nTracer.neuronList_jTree.getSelectionCount() != 1) { // start fresh tracing
            IJ.error("Select one dendritic branch to add a spine !");
        } else {
            ntNeuronNode selectedNode = (ntNeuronNode) nTracer.neuronList_jTree.getLastSelectedPathComponent();
            String nodeType = selectedNode.getType();
            if (selectedNode.isBranchNode()
                    && (nodeType.equals("Dendrite") || nodeType.equals("Apical"))) { // selected one dendrite
                if (nTracer.pointTable_jTable.getSelectedRowCount() == 1) { // add to existing tracing
                    int row = nTracer.pointTable_jTable.getSelectedRow();
                    //IJ.log("selected rows = "+pointTable_jTable.getSelectedRowCount());
                    //if (row == 0 || row == pointTable_jTable.getRowCount()-1){
                    //    IJ.error("Cannot assign end points as spine point !");
                    //    return;
                    //}
                    for (String[] addpoint : points) {
                        addpoint[0] = "Spine";
                    }
                    String spineNumber = getNextSpineNumber();
                    selectedNode.setSpine(row, spineNumber);
                    nTracer.pointTableModel.setValueAt(nodeType + ":Spine#" + spineNumber, row, 0);
                    nTracer.pointTableModel.setValueAt((int) 1, row, 5); //TODO FIX SYNAPSE
                    createSpine(spineNumber, points);
                } else {// selected more than one point
                    IJ.error("Select single point on a dendritic branch to add a spine !");
                }
            } else { // selected a soma -- create a new arbor
                IJ.error("Select one dendritic branch to add a spine !");
            }
        }
    }
    
    private void insertNewArborPrimaryBranch(ntNeuronNode neuronSomaNode, ArrayList<String[]> points) {
        int insertPosition = getNextPrimaryBranchNodePositionINneuronSomaNode(neuronSomaNode);
        String newPrimaryNeuriteName = neuronSomaNode.getNeuronNumber() + "-" + (insertPosition + 1);

        nTracer.updateTrees();
        nTracer.recordTreeExpansionSelectionStatus();

        //add to rootNeuronNode
        ntNeuronNode primaryNeurite = new ntNeuronNode(newPrimaryNeuriteName, points);
        nTracer.neuronTreeModel.insertNodeInto(primaryNeurite, neuronSomaNode, insertPosition);

        nTracer.updateTrees();
        nTracer.restoreTreeExpansionSelectionStatus();
        TreePath childPath = new TreePath(primaryNeurite.getPath());
        nTracer.neuronList_jTree.scrollPathToVisible(childPath);
        nTracer.neuronList_jTree.setSelectionPath(childPath);
        nTracer.history.saveHistory();
    }

    private void createNewNeuronWithNeuriteData(ArrayList<String[]> dataPoints) {
        int newSomaPosition = getNextSomaNodePositionINrootNeuronNode();
        String neuronName = "" + (newSomaPosition + 1);
        nTracer.history.saveHistory();
        nTracer.recordTreeExpansionSelectionStatus();

        //add to rootAllSomaNode
        ArrayList<String[]> newSomaSomaData = new ArrayList<String[]>();
        String[] somaSomaData = {"Soma", "-1", "-1", "-1", "0", "0", "0"};

        newSomaSomaData.add(somaSomaData);
        ntNeuronNode newSomaSoma = new ntNeuronNode(neuronName, newSomaSomaData);
        nTracer.allSomaTreeModel.insertNodeInto(newSomaSoma, rootAllSomaNode, newSomaPosition);

        //add to rootNeuronNode
        ArrayList<String[]> newNeuronSomaData = new ArrayList<String[]>();
        String[] neuronSomaData = {"Neurite", "-1", "-1", "-1", "0", "0", "0"};
        newNeuronSomaData.add(neuronSomaData);
        ntNeuronNode newNeuronSoma = new ntNeuronNode(neuronName, newNeuronSomaData);
        nTracer.neuronTreeModel.insertNodeInto(newNeuronSoma, rootNeuronNode, newSomaPosition);
        ntNeuronNode primaryNeurite = new ntNeuronNode(neuronName + "-1", dataPoints);
        nTracer.neuronTreeModel.insertNodeInto(primaryNeurite, newNeuronSoma, 0);

        nTracer.updateTrees();

        nTracer.restoreTreeExpansionSelectionStatus();
        TreePath childPath = new TreePath(primaryNeurite.getPath());
        nTracer.neuronList_jTree.scrollPathToVisible(childPath);
        nTracer.neuronList_jTree.setSelectionPath(childPath);

        nTracer.history.saveHistory();
        nTracer.updateDisplay();
    }

    private String breakBranchByBranchNode(ntNeuronNode branchNode, int breakPosition) {
        ntNeuronNode parentNode = (ntNeuronNode) branchNode.getParent();
        ArrayList<String[]> selectedBranchPoints = branchNode.getTracingResult();
        ArrayList<String[]> leftoverBranchPoints = new ArrayList<>();
        ArrayList<String[]> newBranchPoints = new ArrayList<>();

        // break the original branchNode results into two results
        for (int i = 0; i <= breakPosition; i++) {
            leftoverBranchPoints.add(selectedBranchPoints.get(i));
        }
        for (int i = breakPosition + 1; i < selectedBranchPoints.size(); i++) {
            newBranchPoints.add(selectedBranchPoints.get(i));
        }

        // replace the original branchNode in NeuronTree 
        // by a new leftoverBranchNode, which contains the front part of the original result
        ntNeuronNode leftoverBranchNode = new ntNeuronNode(branchNode.toString(), leftoverBranchPoints);
        int branchIndex = nTracer.neuronTreeModel.getIndexOfChild(parentNode, branchNode);
        nTracer.neuronTreeModel.removeNodeFromParent(branchNode);
        nTracer.neuronTreeModel.insertNodeInto(leftoverBranchNode, parentNode, branchIndex);
        if (breakPosition == 0) {
            deleteOneBranchAndChildByNode(leftoverBranchNode);
        }

        // create a new neuron with the newBranchNode and all of its child
        branchNode.setTracingResult(newBranchPoints);
        int newSomaPosition = getNextSomaNodePositionINrootNeuronNode();
        String newNeuronSomaName = "" + (newSomaPosition + 1);
        String newPrimaryBranchNodeName = newNeuronSomaName + "-1";
        renameBranchNodeAndChildByNewNodeNameAndSetConnection(branchNode, newPrimaryBranchNodeName, 0);
        createNewNeuronWithPrimaryBranchNode(branchNode);

        //return newNeuronSomaName;
        return newPrimaryBranchNodeName;
    }
    
    private void joinSouceToTargetBranchByNode(ntNeuronNode targetNode, ntNeuronNode sourceNode) {
        String[] sourceNames = sourceNode.toString().split("-");
        String sourceNeuronNumber = sourceNames[0];
        String sourcePrimaryNodeName = sourceNames[0] + "-" + sourceNames[1];
//IJ.log(sourceNode.toString()+" with primary name = "+sourcePrimaryNodeName);
        setPrimaryBranchByTernimalNode(sourceNode);
//IJ.log("primary reset for "+sourceNode.toString());
        sourceNode = nTracer.getNodeFromNeuronTreeByNodeName(sourcePrimaryNodeName);
//IJ.log(sourceNode.toString()+" set to primary; has child = "+sourceNode.getChildCount());
        int connectionOffset = 0;
        for (String[] tracingPt : targetNode.getTracingResult()) {
            if (!tracingPt[6].equals("0")) {
                connectionOffset = connectionOffset + 1;
            }
        }
//IJ.log("connection offset = "+connectionOffset);
        renameBranchNodeAndChildByNewNodeNameAndSetConnection(sourceNode, targetNode.toString(), connectionOffset);
//IJ.log("source renamed as "+sourceNode.toString());

        // add sourceNode points to targetNode
        ArrayList<String[]> sourcePts = sourceNode.getTracingResult();
        ArrayList<String[]> targetPts = targetNode.getTracingResult();
        for (String[] sourcePt : sourcePts) {
            targetPts.add(sourcePt);
        }
        targetNode.setTracingResult(targetPts);

        // replicate all child nodes in sourceNode and insert into targetNode
        replicateBranchToNode(sourceNode, targetNode);
//IJ.log("replication done");
        // remove old sourceNode
        nTracer.neuronTreeModel.removeNodeFromParent(getSomaNodeFromNeuronTreeByNeuronNumber(sourceNeuronNumber));
        nTracer.allSomaTreeModel.removeNodeFromParent(getSomaNodeFromAllSomaTreeByNeuronNumber(sourceNeuronNumber));
    }
    
    /**
    * Search for node by end points
    */
    private ntNeuronNode searchNodeByEndPoints(String[] firstPt, String[] lastPt, ntNeuronNode primaryNode) {
        ArrayList<ntNeuronNode> allNodes = new ArrayList<>();
        getAllNodes(primaryNode, allNodes);
        for (ntNeuronNode node : allNodes) {
            if (nodeContainsEndPoints(firstPt, lastPt, node)) {
                return node;
            }
        }
        return null;
    }
    
    /**
    * Check if a node contain a set of end points
    */
    private boolean nodeContainsEndPoints(String[] firstPt, String[] lastPt, ntNeuronNode node) {
        ArrayList<String[]> result = node.getTracingResult();
        String[] result0 = result.get(0);
        String[] result1 = result.get(result.size() - 1);
        return (firstPt[1].equals(result0[1]) && firstPt[2].equals(result0[2]) && firstPt[3].equals(result0[3])
                && lastPt[1].equals(result1[1]) && lastPt[2].equals(result1[2]) && lastPt[3].equals(result1[3]))
                || (firstPt[1].equals(result1[1]) && firstPt[2].equals(result1[2]) && firstPt[3].equals(result1[3])
                && lastPt[1].equals(result0[1]) && lastPt[2].equals(result0[2]) && lastPt[3].equals(result0[3]));
    } 
    
    private void getAllNodes(ntNeuronNode primaryNode, ArrayList<ntNeuronNode> allNodes) {
        allNodes.add(primaryNode);
        for (int i = 0; i < primaryNode.getChildCount(); i++) {
            ntNeuronNode childNode = (ntNeuronNode) primaryNode.getChildAt(i);
            getAllNodes(childNode, allNodes);
        }
    }
    
    private void replicateBranchToNode(ntNeuronNode replicateNode, ntNeuronNode insert2Node) {
        for (int i = 0; i < replicateNode.getChildCount(); i++) {
            ntNeuronNode replicateChildNode = (ntNeuronNode) replicateNode.getChildAt(i);
            //IJ.log("insert "+replicateChildNode.toString());
            ntNeuronNode insertChildNode
                    = new ntNeuronNode(replicateChildNode.toString(), replicateChildNode.getTracingResult());
            insert2Node.insert(insertChildNode, i);
            if (replicateChildNode.getChildCount() > 0) {
                replicateBranchToNode(replicateChildNode, insertChildNode);
            }
        }
    }

    private void setPrimaryBranchByTernimalNode(ntNeuronNode selectedNode) {
        if (selectedNode.isPrimaryBranchNode()) {
            if (selectedNode.isLeaf()) {
                selectedNode.invertTracingResult();
            }
            //IJ.log(selectedNode.toString()+" is already primary");
            return;
        }
        String[] names = selectedNode.toString().split("-");
        String primaryBranchName = names[0] + "-" + names[1];
        ntNeuronNode newPrimaryNode = createNoteWithInvertResultAndOldPlusNewName(selectedNode, primaryBranchName);
//IJ.log(selectedNode.toString() + "->" + newPrimaryNode.toString());
        inverseBranchToNodeWithOldPlusNewName(selectedNode, newPrimaryNode);
        removeOldNameFromWholeBranchAndResetConnectionSynapse(newPrimaryNode);

        // replace oldPrimaryNode in neuronTree with primaryNode
        ntNeuronNode oldPrimaryNode = nTracer.getNodeFromNeuronTreeByNodeName(primaryBranchName);
        ntNeuronNode neuronSomaNode = (ntNeuronNode) oldPrimaryNode.getParent();
        int oldPrimaryNodeIndex = nTracer.neuronTreeModel.getIndexOfChild(neuronSomaNode, oldPrimaryNode);
        nTracer.neuronTreeModel.removeNodeFromParent(oldPrimaryNode);
        nTracer.neuronTreeModel.insertNodeInto(newPrimaryNode, neuronSomaNode, oldPrimaryNodeIndex);
    }

    private void deleteOneBranchAndChildByNode(ntNeuronNode delBranchNode) {
        if (delBranchNode.isPrimaryBranchNode()) { // delNode is a primary branch node
            String deleteNeuronNumber = delBranchNode.getNeuronNumber();
            deleteBranchAndChildNode(delBranchNode);
            // if the neuron is empty -- delete the leftover somaNode from allSomaTreeModel and neuronTreeModel
            ntNeuronNode neuronSomaNode = getSomaNodeFromNeuronTreeByNeuronNumber(deleteNeuronNumber);
            ntNeuronNode somaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(deleteNeuronNumber);
            if (neuronSomaNode.getChildCount() == 0 && somaSomaNode.getChildCount() == 0) {
                nTracer.allSomaTreeModel.removeNodeFromParent(somaSomaNode);
                nTracer.neuronTreeModel.removeNodeFromParent(neuronSomaNode);
            }
        } else { // delNode is NOT a primary branch node
            deleteOneWholeBranchAndMergeUndeletedByNode(delBranchNode);
        }
    }

    private void deleteOneWholeBranchAndMergeUndeletedByNode(ntNeuronNode delBranchNode) {
        ntNeuronNode parentNode = (ntNeuronNode) delBranchNode.getParent();

        // first delete the branch from neuron
        deleteBranchAndChildNode(delBranchNode);

        if (parentNode.getChildCount() > 0) {
            // then retrieve the undeleted sibling branch note
            ntNeuronNode undeleteNode = (ntNeuronNode) parentNode.getChildAt(0);
            // merge undeleteNode into parentNode
            int synapseNumberOffset = 0;
            for (String[] tracingPt : parentNode.getTracingResult()) {
                if (!tracingPt[6].equals("0")) {
                    synapseNumberOffset = synapseNumberOffset + 1;
                }
            }
            renameBranchNodeAndChildByNewNodeNameAndSetConnection(undeleteNode, parentNode.toString(), synapseNumberOffset);
            mergeBranchNodeAndChild2ParentNode(undeleteNode, parentNode);
        }
    }

    /**
    * Merge branch node and child to parent node
    */
    private void mergeBranchNodeAndChild2ParentNode(ntNeuronNode mergeNode, ntNeuronNode parentNode) {
        // insert parentNode tracing results to the front of the mergeNode result
        ArrayList<String[]> mergeNodeResults = mergeNode.getTracingResult();
        ArrayList<String[]> parentNodeResults = parentNode.getTracingResult();
        for (int i = parentNodeResults.size() - 2; i >= 0; i--) { // the last point in parentNode is identical to mergeNode - no need to insert, otherwise redundant
            String[] parentNodeResult = parentNodeResults.get(i);
            mergeNodeResults.add(0, parentNodeResult);
        }

        // insert mergeNode to tree
        ntNeuronNode grandparentNode = (ntNeuronNode) parentNode.getParent();
        int mergePosition = nTracer.neuronTreeModel.getIndexOfChild(grandparentNode, parentNode);
        nTracer.neuronTreeModel.insertNodeInto(mergeNode, grandparentNode, mergePosition);
        // remove parentNode from tree
        nTracer.neuronTreeModel.removeNodeFromParent(parentNode);
    }

    private void renameBranchNodeAndChildByNewNodeNameAndSetConnection(ntNeuronNode node, String newNodeName, int synapseNumberOffset) {
        // set the node's connection first with synapseNumberOffset and then rename to newNodeName
        renameNodeByNewNodeNameAndSetConnection(node, newNodeName, synapseNumberOffset);

        // reset all the childs with synapseNumberOffset=0
        for (int i = 0; i < node.getChildCount(); i++) {
            ntNeuronNode childNode = (ntNeuronNode) node.getChildAt(i);
            renameBranchNodeAndChildByNewNodeNameAndSetConnection(childNode, newNodeName + "-" + (i + 1), 0);
        }
    }
    
    /**
    * Create neuron with primary branch node
    */
    private void createNewNeuronWithPrimaryBranchNode(ntNeuronNode primaryBranchNode) {
        int newSomaPosition = getNextSomaNodePositionINrootNeuronNode();
        String neuronName = "" + (newSomaPosition + 1);

        //add to rootAllSomaNode
        ArrayList<String[]> newSomaSomaData = new ArrayList<String[]>();
        String[] somaSomaData = {"Soma", "-1", "-1", "-1", "0", "0", "0"};

        newSomaSomaData.add(somaSomaData);
        ntNeuronNode newSomaSoma = new ntNeuronNode(neuronName, newSomaSomaData);
        nTracer.allSomaTreeModel.insertNodeInto(newSomaSoma, rootAllSomaNode, newSomaPosition);

        //add to rootNeuronNode
        ArrayList<String[]> newNeuronSomaData = new ArrayList<String[]>();
        String[] neuronSomaData = {"Neurite", "-1", "-1", "-1", "0", "0", "0"};
        newNeuronSomaData.add(neuronSomaData);
        ntNeuronNode newNeuronSoma = new ntNeuronNode(neuronName, newNeuronSomaData);
        nTracer.neuronTreeModel.insertNodeInto(newNeuronSoma, rootNeuronNode, newSomaPosition);
        nTracer.neuronTreeModel.insertNodeInto(primaryBranchNode, newNeuronSoma, 0);
    }

    /**
    * Create new neuron with soma
    */
    private void createNewNeuronWithSomaData(ArrayList<String[]> dataPoints) {
        int newSomaPosition = getNextSomaNodePositionINrootNeuronNode();
        String neuronName = "" + (newSomaPosition + 1);
        //saveHistory();
        //recordTreeExpansionSelectionStatus();
        // add to rootAllSomaNode
        ArrayList<String[]> newSomaSomaData = new ArrayList<String[]>();
        String[] somaSomaData = {"Soma", "-1", "-1", "-1", "0", "0", "0"};
        newSomaSomaData.add(somaSomaData);
        ntNeuronNode newSomaSoma = new ntNeuronNode(neuronName, newSomaSomaData);
        nTracer.allSomaTreeModel.insertNodeInto(newSomaSoma, rootAllSomaNode, newSomaPosition);
        ntNeuronNode newSomaTracing = new ntNeuronNode(neuronName + ":" + dataPoints.get(0)[3], dataPoints);
        nTracer.allSomaTreeModel.insertNodeInto(newSomaTracing, newSomaSoma, newSomaSoma.getChildCount());

        //add to rootNeuronNode
        ArrayList<String[]> newNeuronSomaData = new ArrayList<String[]>();
        String[] neuronSomaData = {"Neurite", "-1", "-1", "-1", "0", "0", "0"};
        newNeuronSomaData.add(neuronSomaData);
        ntNeuronNode newNeuronSoma = new ntNeuronNode(neuronName, newNeuronSomaData);
        nTracer.neuronTreeModel.insertNodeInto(newNeuronSoma, rootNeuronNode, newSomaPosition);

        nTracer.updateTrees();
        nTracer.restoreTreeExpansionSelectionStatus();
        TreePath childPath = new TreePath(newNeuronSoma.getPath());
        nTracer.neuronList_jTree.scrollPathToVisible(childPath);
        nTracer.neuronList_jTree.setSelectionPath(childPath);
        nTracer.displaySomaList_jTree.setSelectionInterval(0, 0);
        //saveHistory();
        //updateDisplayMultiThread();
    }

    /**
    * Insert new soma slice into soma neuron tree
    */
    private void insertNewSomaSliceIntoSelectedNeuronTreeSoma(ntNeuronNode neuronSomaNode) {
        String neuronNumber = neuronSomaNode.getNeuronNumber();
        String sliceNumber = nTracer.tablePoints.get(0)[3];
        int insertPosition = 0; // add to first slice by default (e.g. to an empty soma)
        ntNeuronNode newSomaSlice = new ntNeuronNode(neuronNumber + ":" + sliceNumber, nTracer.tablePoints);
        ntNeuronNode somaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNumber);

        if (somaNode.getChildCount() > 0) { // soma already contain slices
            for (int i = 0; i < somaNode.getChildCount(); i++) {
                String[] sliceNames = ((ntNeuronNode) somaNode.getChildAt(i)).toString().split(":");
                if (Integer.parseInt(sliceNumber) > Integer.parseInt(sliceNames[1])) {
                    insertPosition = i + 1;
                }
            }
            for (int i = 0; i < somaNode.getChildCount(); i++) {
                ntNeuronNode sliceNode = (ntNeuronNode) somaNode.getChildAt(i);
                String[] sliceNames = sliceNode.toString().split(":");
                if (sliceNumber.equals(sliceNames[1])) {
                    ArrayList<String[]> sliceTracingResult = sliceNode.getTracingResult();
                    String hasConnection = "";
                    for (String[] tracing : sliceTracingResult) {
                        if (!tracing[6].equals("0")) {
                            hasConnection = "    And has connection(s) !\n";
                            break;
                        }
                    }
                    YesNoCancelDialog replaceDialog = new YesNoCancelDialog(new java.awt.Frame(),
                            "Replace traced Soma slice", "Current slice has been traced!\n"
                            + hasConnection + "        Want to replace?");
                    if (replaceDialog.yesPressed()) {
                        nTracer.deleteOneSomaSliceNodeByName(sliceNode.toString());

                    } else {
                        nTracer.tablePoints = new ArrayList<String[]>();
                        nTracer.clearStartEndPts();
                        return;
                    }
                    break;
                }
            }
        }
        nTracer.recordTreeExpansionSelectionStatus();
        // add to rootAllSomaNode 
        nTracer.allSomaTreeModel.insertNodeInto(newSomaSlice, somaNode, insertPosition);
        imp.killRoi();
        nTracer.updateTrees();
        nTracer.restoreTreeExpansionSelectionStatus();
        //IJ.log(insertPosition+"");
        nTracer.displaySomaList_jTree.setSelectionInterval(insertPosition, insertPosition);
        nTracer.displaySomaList_jTree.scrollRowToVisible(insertPosition);
        int endPosition = nTracer.pointTable_jTable.getRowCount() - 1;
        nTracer.pointTable_jTable.setRowSelectionInterval(endPosition, endPosition);
        nTracer.scroll2pointTableVisible(endPosition, 0);
        //saveHistory();
    }

    /**
    * Add to front of branch
    */
    private void addToFrontOfSelectedBranch(ArrayList<String[]> points) {
        for (int i = 1; i < points.size(); i++) {
            nTracer.tablePoints.add(0, points.get(i));
        }
        ((ntNeuronNode) (nTracer.neuronList_jTree.getLastSelectedPathComponent())).setTracingResult(nTracer.tablePoints);
    }

    /**
    * Add to end of branch
    */
    private void addToEndOfSelectedBranch(ArrayList<String[]> points) {
        for (int i = 1; i < points.size(); i++) {
            nTracer.tablePoints.add(nTracer.tablePoints.size(), points.get(i));
        }
        ((ntNeuronNode) (nTracer.neuronList_jTree.getLastSelectedPathComponent())).setTracingResult(nTracer.tablePoints);
    }

    /**
    * Add to front of soma
    */
    private void addToFrontOfSelectedSoma(ArrayList<String[]> points) {
        for (int i = 1; i < points.size(); i++) {
            String[] addPoint = points.get(i);
            addPoint[0] = "Soma";
            nTracer.tablePoints.add(i, addPoint);
        }
        ((ntNeuronNode) (nTracer.displaySomaList_jTree.getLastSelectedPathComponent())).setTracingResult(nTracer.tablePoints);
    }

    /**
    * Add to end of soma
    */
    private void addToEndOfSelectedSoma(ArrayList<String[]> points) {
        for (int i = 1; i < points.size(); i++) {
            String[] addPoint = points.get(i);
            addPoint[0] = "Soma";
            nTracer.tablePoints.add(nTracer.tablePoints.size(), addPoint);
        }
        ((ntNeuronNode) (nTracer.displaySomaList_jTree.getLastSelectedPathComponent())).setTracingResult(nTracer.tablePoints);
    }

    /**
    * Create spine and add to spine tree model
    */
    private void createSpine(String spineNumber, ArrayList<String[]> points) {
        ntNeuronNode newSpineNode = new ntNeuronNode(spineNumber + "", points);
        nTracer.spineTreeModel.insertNodeInto(newSpineNode, rootSpineNode, Integer.parseInt(spineNumber) - 1);
    }
    
    private String getNextSpineNumber() {
        int nextNumber = 1;
        for (int i = 0; i < rootSpineNode.getChildCount(); i++) {
            ntNeuronNode spineNode = (ntNeuronNode) rootSpineNode.getChildAt(i);
            if (Integer.parseInt(spineNode.toString()) - nextNumber > 0) {
                break;
            }
            nextNumber++;
        }
        return nextNumber + "";
    }

    private int getNextSomaNodePositionINrootNeuronNode() {
        for (int n = 0; n < rootNeuronNode.getChildCount(); n++) {
            ntNeuronNode somaNode = (ntNeuronNode) rootNeuronNode.getChildAt(n);
            int somaNumber = Integer.parseInt(somaNode.getNeuronNumber());
            if (somaNumber > n + 1) {
                return n;
            }
        }
        return rootNeuronNode.getChildCount();
    }

    private String getSpineNumberFromTag(String spineTag) {
        String spineNumber = spineTag;
        if (spineTag.contains("/")) {
            spineNumber = spineTag.split("/")[0];
        } else if (spineTag.contains("*")) {
            spineNumber = spineTag.split("\\*")[0];
        }
        spineNumber = spineNumber.split("#")[1];
        return spineNumber;
    }

    private void createNewBranch(ArrayList<String[]> points) {
        nTracer.recordTreeExpansionSelectionStatus();

        ntNeuronNode oldParentNode = (ntNeuronNode) nTracer.neuronList_jTree.getLastSelectedPathComponent();
        String parentName = oldParentNode.toString();
        int branchPosition = nTracer.pointTable_jTable.getSelectedRow();
        ArrayList<String[]> newParentPoints = new ArrayList<String[]>();
        for (int i = 0; i <= branchPosition; i++) {
            newParentPoints.add(nTracer.tablePoints.get(i));
        }

        ArrayList<String[]> splitChildPoints = new ArrayList<String[]>();
        for (int i = branchPosition + 1; i < nTracer.tablePoints.size(); i++) {
            splitChildPoints.add(nTracer.tablePoints.get(i));
        }
        nTracer.tablePoints = new ArrayList<String[]>(); // tablePoints == child2Points
        for (int i = 1; i < points.size(); i++) {
            String[] point = points.get(i);
            nTracer.tablePoints.add(point);
        }
        // update tree database
        ntNeuronNode newParentNode = new ntNeuronNode(parentName, newParentPoints);
        oldParentNode.setTracingResult(splitChildPoints);
        renameBranchNodeAndChildByNewNodeNameAndSetConnection(oldParentNode, parentName + "-1", 0);
        ntNeuronNode newChildNode = new ntNeuronNode(newParentNode.toString() + "-2", nTracer.tablePoints);
        nTracer.neuronTreeModel.insertNodeInto(newParentNode, (ntNeuronNode) oldParentNode.getParent(), oldParentNode.getParent().getIndex(oldParentNode));
        nTracer.neuronTreeModel.removeNodeFromParent(oldParentNode);
        nTracer.neuronTreeModel.insertNodeInto(oldParentNode, newParentNode, 0);
        nTracer.neuronTreeModel.insertNodeInto(newChildNode, newParentNode, 1);

        nTracer.updateTrees();
        nTracer.restoreTreeExpansionSelectionStatus();
        TreePath newChildPath = new TreePath(newChildNode.getPath());
        nTracer.neuronList_jTree.scrollPathToVisible(newChildPath);
        nTracer.neuronList_jTree.setSelectionPath(newChildPath);
        nTracer.history.saveHistory();
    }
    
    private void removeOldNameFromNodeAndResetConnectionSynapse(ntNeuronNode oldPlusNewNameNode) {
        ArrayList<String[]> tracingResults = oldPlusNewNameNode.getTracingResult();
        String oldPlusNewNameNodeName = oldPlusNewNameNode.toString();
        String[] names = oldPlusNewNameNodeName.split("#");
        String oldName = names[0];
        String newName = names[1];
        for (String[] tracingResult : tracingResults) {
            if (!tracingResult[6].equals("0")) {
                // get connectedNode, insertPosition and newSynapseName to replace
                String[] synapseNames = tracingResult[6].split("#");
                String connectedNodeName = synapseNames[1];
//IJ.log(oldPlusNewNameNodeName+" connected to "+tracingResult[6]);
                String connectedOldSynapseName = synapseNames[2] + "#" + oldName + "#" + synapseNames[0];
                String connectedNewSynapseName = synapseNames[2] + "#" + newName + "#" + synapseNames[0];
                ntNeuronNode connectedNode = nTracer.getTracingNodeByNodeName(connectedNodeName);
                int connectedPosition = nTracer.getPositionInTracingResultBySynapseName(connectedNode.getTracingResult(), connectedOldSynapseName);
//IJ.log(connectedOldSynapseName+"->"+connectedNewSynapseName+" at "+connectedPosition);
                connectedNode.setConnectionTo(connectedPosition, connectedNewSynapseName);
            }
        }
        oldPlusNewNameNode.setName(newName);
//IJ.log(oldPlusNewNameNodeName+"name set to "+oldPlusNewNameNode.toString());
    }

    private void removeOldNameFromWholeBranchAndResetConnectionSynapse(ntNeuronNode primaryBranchNode) {
        for (int i = 0; i < primaryBranchNode.getChildCount(); i++) {
            ntNeuronNode childNode = (ntNeuronNode) primaryBranchNode.getChildAt(i);
            removeOldNameFromWholeBranchAndResetConnectionSynapse(childNode);
        }
        removeOldNameFromNodeAndResetConnectionSynapse(primaryBranchNode);
    }

    private ntNeuronNode createNoteWithInvertResultAndOldPlusNewName(ntNeuronNode oldNode, String newNodeName) {
        String comboName = oldNode.toString() + "#" + newNodeName;
        ntNeuronNode newNode = new ntNeuronNode(comboName, oldNode.getInvertTracingResult());
        return newNode;
    }

    private ntNeuronNode createNoteWithSameResultAndOldPlusNewName(ntNeuronNode oldNode, String newNodeName) {
        String comboName = oldNode.toString() + "#" + newNodeName;
        ntNeuronNode newNode = new ntNeuronNode(comboName, oldNode.getTracingResult());
        return newNode;
    }

    private void inverseBranchToNodeWithOldPlusNewName(ntNeuronNode inverseNode, ntNeuronNode insert2Node) {
        // deal with the parent node of inverseNode
        String newParentNodeName = insert2Node.toString().split("#")[1];
        ntNeuronNode inverseParentNode = (ntNeuronNode) inverseNode.getParent();
        ntNeuronNode newChildNode1 = createNoteWithInvertResultAndOldPlusNewName(inverseParentNode, newParentNodeName + "-1");
        insert2Node.insert(newChildNode1, 0);
//IJ.log(insert2Node.toString() + "->" + newChildNode1.toString()+ " at 0");
        if (inverseParentNode.isSubBranchNode()) { // is a branch node in the original neuronTree
            // continue adding backward for the oldParentNode
            inverseBranchToNodeWithOldPlusNewName(inverseParentNode, newChildNode1);
        } else { // is soma node in the original neuronTree
            // stops here
        }

        // deal with the sibling node of inverseNode
        // get oldNode's sibling node
        ntNeuronNode inverseSiblingNode = (ntNeuronNode) inverseParentNode.getChildAt(0);
        if (inverseParentNode.getIndex(inverseNode) == 0) {
            inverseSiblingNode = (ntNeuronNode) inverseParentNode.getChildAt(1);
        }
        // add sibling node
        ntNeuronNode newChildNode2 = createNoteWithSameResultAndOldPlusNewName(inverseSiblingNode, newParentNodeName + "-2");
        insert2Node.insert(newChildNode2, 1);
//IJ.log(insert2Node.toString() + "->" + newChildNode2.toString()+ " at 1");
        if (inverseSiblingNode.isLeaf()) {
            // stops here
        } else { // oldSiblingNode is NOT a terminal branch node, 
            // replicate the whole branch and change name and connectedSynapse ...
            replicateBranchToNodeWithOldPlusNewName(inverseSiblingNode, newChildNode2);
        }
    }

    private void replicateBranchToNodeWithOldPlusNewName(ntNeuronNode replicateNode, ntNeuronNode insert2Node) {
        String insert2NodeName = insert2Node.toString().split("#")[1];
        for (int i = 0; i < replicateNode.getChildCount(); i++) {
            ntNeuronNode replicateChildNode = (ntNeuronNode) replicateNode.getChildAt(i);
            ntNeuronNode insertChildNode = createNoteWithSameResultAndOldPlusNewName(replicateChildNode, insert2NodeName + "-" + (i + 1));
            insert2Node.insert(insertChildNode, i);
//IJ.log(insert2Node.toString()+"->"+insertChildNode.toString());
            if (replicateNode.getChildCount() > 0) {
                replicateBranchToNodeWithOldPlusNewName(replicateChildNode, insertChildNode);
            }
        }
    }
}
