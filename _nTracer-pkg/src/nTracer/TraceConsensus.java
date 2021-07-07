/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import ij.IJ;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import javax.swing.JFileChooser;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import static nTracer.nTracer_.imp;

/**
 * 
 * @author Wei Jie
 */

/**
* Comparator for points in trace
*/
class PointsCompare implements Comparator<String[]> {
    public int compare(String[] arr1, String[] arr2) {
        if (Integer.parseInt(arr1[1]) < Integer.parseInt(arr2[1])) return -1;
        else if (Integer.parseInt(arr1[1]) > Integer.parseInt(arr2[1])) return 1;
        else if (Integer.parseInt(arr1[2]) < Integer.parseInt(arr2[2])) return -1;
        else if (Integer.parseInt(arr1[2]) > Integer.parseInt(arr2[2])) return 1;
        else return 0;
    }
}

/**
 * Methods to determine matching branches and generate consensus branch
 */
public class TraceConsensus {
    private nTracer_ nTracer;
    protected ntNeuronNode consensusRootNode;
    protected ArrayList<ArrayList<String[]>> combinedBranches;
    
    TraceConsensus(nTracer_ nTracer) {
        this.nTracer = nTracer;
        this.combinedBranches = new ArrayList<>();
    }
    
    protected void outputMatchedNeurons() {
        String directory = IJ.getDirectory("current");
        String dataFileName = (String) (imp.getTitle() + ".zip");
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
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
            return;
        }
        File[] selectedFiles = fileChooser.getSelectedFiles();
        if (selectedFiles.length == 0 || directory == null || dataFileName == null) {
            return;
        }
        
        ArrayList<ArrayList<ArrayList<String[]>>> neuronTraces= new ArrayList<>();
        ArrayList<ntNeuronNode> neuronRoots = new ArrayList<>();
        
        ntNeuronNode prevSomaRoot = null, prevSpineRoot = null;
        
        for (File selectedFile: selectedFiles) {
            ArrayList<ArrayList<String[]>> trace = new ArrayList<>();
            try {
                System.gc();
                InputStream parameterAndNeuronIS = nTracer.IO.loadPackagedParameterAndNeuron(selectedFile);
                DefaultTreeModel[] treeModels = nTracer.IO.loadTracingParametersAndNeurons(parameterAndNeuronIS);
                ntNeuronNode neuronRoot = (ntNeuronNode) treeModels[1].getRoot();
                if (prevSomaRoot == null) {
                    prevSomaRoot = (ntNeuronNode) treeModels[0].getRoot();
                    prevSpineRoot = (ntNeuronNode) treeModels[2].getRoot();
                }
                neuronRoots.add(neuronRoot);
                for (int n = 0; n < neuronRoot.getChildCount(); ++n) {
                    ArrayList<String[]> neuronPoints = new ArrayList<>();
                    
                    ntNeuronNode neuron = (ntNeuronNode) neuronRoot.getChildAt(n);
                    ArrayList<ntNeuronNode> branches = new ArrayList<>();
                    decomposeBranches(neuron, branches);
                    for (ntNeuronNode branch: branches) {
                        for (String[] point: branch.getTracingResult()) neuronPoints.add(point);
                    }
                    trace.add(neuronPoints);
                }
                
                
                neuronTraces.add(trace);
                System.gc();
            } catch (IOException e) { 
                return;
            }

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
        
        final String folder = IJ.getDirectory("current") + "consensus/";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }
        
        ArrayList<ArrayList<Integer>> matchings = neuronConsensus(neuronTraces);
        
        ArrayList<ntNeuronNode> newNeuronRoots = new ArrayList<>();
        for (ArrayList<Integer> match: matchings) {
            ntNeuronNode newNeuronRoot = new ntNeuronNode("root", new ArrayList<String[]>());
            String fileName = "match";
            for (int t = 0; t < neuronRoots.size(); ++t) {
                int n = match.get(t);
                fileName += "-" + n;
                ntNeuronNode trace = new ntNeuronNode("", null);
                copyNeuron((ntNeuronNode) neuronRoots.get(t).getChildAt(n), trace, t + 1);
                newNeuronRoot.add(trace);
            }
            newNeuronRoots.add(newNeuronRoot);
            
            String path = folder + fileName + ".zip";
            File saveFile = new File(path);
            
            try {
                nTracer.IO.savePackagedData(newNeuronRoot, prevSomaRoot, prevSpineRoot,
                        nTracer.neuronList_jTree, nTracer.displaySomaList_jTree, nTracer.pointTable_jTable, saveFile,
                        imp.getC(), imp.getZ(), imp.getFrame(), panelParameters);
            } catch (IOException e) {
                return;
            }
        }
        
        return;
    }
    
    private void copyNeuron(ntNeuronNode node, ntNeuronNode newNode, int newNeuronNumber) {
        String[] splits = node.toString().split("-");
        String newName = newNeuronNumber + "";
        for (int s = 1; s < splits.length; ++s) {
            newName += "-" + splits[s];
        }
        newNode.setName(newName);
        newNode.setTracingResult(node.getTracingResult());
        
        for (int i = 0; i < node.getChildCount(); ++i) {
            ntNeuronNode newChildNode = new ntNeuronNode("", null);
            newNode.add(newChildNode);
            copyNeuron((ntNeuronNode) node.getChildAt(i), newChildNode, newNeuronNumber);
        }
    }
    
    /**
    * Determine matching neurons
    */
    private ArrayList<ArrayList<Integer>> neuronConsensus(ArrayList<ArrayList<ArrayList<String[]>>> neuronTraces) {
        if (neuronTraces.size() < 2) return new ArrayList<>();
        
        Map<String, Integer> neuronMap = new HashMap<>();
        ArrayList<ArrayList<Integer>> neuronMatchings = new ArrayList<>();
        
        for (int n = 0; n < neuronTraces.get(0).size(); ++n) {
            neuronMatchings.add(new ArrayList<>());
            neuronMatchings.get(n).add(n);
            for (int t = 1; t < neuronTraces.size(); ++t) neuronMatchings.get(n).add(-1);
            
            ArrayList<String[]> neuron = neuronTraces.get(0).get(n);
            for (String[] point: neuron) {
                int POINT_BUFFER = 3;
                neuronMap.put(point[1] + "," + point[2] + "," + point[3], n);
                for (int xBuffer = -POINT_BUFFER; xBuffer <= POINT_BUFFER; ++xBuffer) {
                    for (int yBuffer = -POINT_BUFFER; yBuffer <= POINT_BUFFER; ++yBuffer) {
                        for (int zBuffer = -POINT_BUFFER; zBuffer <= POINT_BUFFER; ++zBuffer) {
                           neuronMap.put((Integer.parseInt(point[1]) + xBuffer)
                                   + "," + (Integer.parseInt(point[2]) + yBuffer)
                                   + "," + (Integer.parseInt(point[3]) + zBuffer), n);
                        }
                    }
                }
            }
        }
        
        
        
        for (int t = 1; t < neuronTraces.size(); ++t) {
            ArrayList<ArrayList<String[]>> trace = neuronTraces.get(t);
            for (int n = 0; n < trace.size(); ++n) {
                ArrayList<String[]> neuron = trace.get(n);
                Map<Integer, Integer> potentialMatches = new HashMap<>();
                for (String[] point: neuron) {
                    int matchingNeuronIndex = neuronMap.getOrDefault(point[1] + "," + point[2] + "," + point[3], -1);
                    if (matchingNeuronIndex == -1) continue;
                    
                    potentialMatches.put(matchingNeuronIndex, potentialMatches.getOrDefault(matchingNeuronIndex, 0) + 1);
                }
                float MATCH_THRESHOLD = (float) 0.05;
                int bestMatchingNeuronIndex = -1;
                int bestNeuronMatches = 0;
                for (Map.Entry<Integer, Integer> entry: potentialMatches.entrySet()) {
                    if (entry.getValue() > bestNeuronMatches) {
                        bestNeuronMatches = entry.getValue();
                        bestMatchingNeuronIndex = entry.getKey();
                    }
                }
                if (bestNeuronMatches / (float) trace.size() > MATCH_THRESHOLD) {
                    neuronMatchings.get(bestMatchingNeuronIndex).set(t, n);
                } else {
                    int newIndex = neuronMatchings.size();
                    neuronMatchings.add(new ArrayList<>());
                    for (int m = 0;  m < neuronTraces.size(); ++m) {
                        if (m == t) neuronMatchings.get(newIndex).add(n);
                        else neuronMatchings.get(newIndex).add(-1);
                    }
                    for (int t2 = 0; t2 < neuronMatchings.size(); ++t2) neuronMatchings.get(newIndex).add(-1);
                    
                    int POINT_BUFFER = 3;
                    for (String[] point: neuron) {
                        neuronMap.put(point[1] + "," + point[2] + "," + point[3], n);
                        for (int xBuffer = -POINT_BUFFER; xBuffer <= POINT_BUFFER; ++xBuffer) {
                            for (int yBuffer = -POINT_BUFFER; yBuffer <= POINT_BUFFER; ++yBuffer) {
                                for (int zBuffer = -POINT_BUFFER; zBuffer <= POINT_BUFFER; ++zBuffer) {
                                   neuronMap.put((Integer.parseInt(point[1]) + xBuffer)
                                           + "," + (Integer.parseInt(point[2]) + yBuffer)
                                           + "," + (Integer.parseInt(point[3]) + zBuffer), newIndex);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        ArrayList<ArrayList<Integer>> filteredNeuronMatchings = new ArrayList<>();
        for (ArrayList<Integer> match: neuronMatchings) {
            int matches = 0;
            for (int n: match) if (n != -1) ++matches;
            if (matches > 1) filteredNeuronMatchings.add(match);
        }
        return filteredNeuronMatchings;
    }

    /**
    * DEPRECATED
    */    
    private ArrayList<Integer[]> getRankedTraces(ntNeuronNode traces) {
        // Setup point map
        Map<String, Integer> allPoints = new HashMap<String, Integer>();
        for (int i = 0; i < traces.getChildCount(); ++i) {
            ntNeuronNode node = (ntNeuronNode) traces.getChildAt(i).getChildAt(0);
            for (String[] el: node.getTracingResult()) {
                int count = allPoints.getOrDefault(el[1] + "," + el[2] + "," + el[3], 1);
                allPoints.put(el[1] + "," + el[2] + "," + el[3], count);
            }
        }
        
        // Rank traces based on their correlation
        ArrayList<Integer[]> traceOrders = new ArrayList<Integer[]>();
        for (int i = 0; i < traces.getChildCount(); ++i) {
            ntNeuronNode node = (ntNeuronNode) traces.getChildAt(i).getChildAt(0);
            int score = 0;
            for (String[] el: node.getTracingResult()) {
                score += allPoints.getOrDefault(el[1] + "," + el[2] + "," + el[3], 0);
            }
            traceOrders.add(new Integer[] {i, score});
        }
        traceOrders.sort((a, b) -> b[1].compareTo(a[1]));
        return traceOrders;
    }
    
    /**
    * DEPRECATED
    */ 
    public void aggregateSomaTraces() {
        if ( nTracer.rootAllSomaNode.getChildCount() == 0) return;

        ArrayList<Integer[]> rankedTraces = getRankedTraces(nTracer.rootAllSomaNode);
        
        // Prepare first trace
        ntNeuronNode result = (ntNeuronNode) nTracer.rootAllSomaNode
                .getChildAt(rankedTraces.get(0)[0])
                .getChildAt(0);
        ArrayList<String[]> resultList = new ArrayList<String []>();
        for (String[] el: result.getTracingResult()) {
            resultList.add(Arrays.copyOf(el, el.length));
        }
        // Record previous order
        for (int i = 0; i < resultList.size(); ++i) {
            resultList.get(i)[4] = i + "";
        }
        // Sort
        resultList.sort(new PointsCompare());
        
        // Start multi-pass merging of traces
        for (Integer[] trace: rankedTraces.subList(1, rankedTraces.size())) {
            ntNeuronNode node = (ntNeuronNode) nTracer.rootAllSomaNode.getChildAt(trace[0]).getChildAt(0);
            ArrayList<String[]> nodeList = new ArrayList<String []> (node.getTracingResult());
            for (String[] el: node.getTracingResult()) {
                nodeList.add(Arrays.copyOf(el, el.length));
            }
            nodeList.sort(new PointsCompare());
            int resultIndex = 0, nodeIndex = 0;
            
            // Iterate through the x-coordinates
            while (nodeIndex < nodeList.size() && resultIndex < resultList.size()) {
                if (Integer.parseInt(nodeList.get(nodeIndex)[1]) > Integer.parseInt(resultList.get(resultIndex)[1])) {
                    ++resultIndex;
                } else if (Integer.parseInt(nodeList.get(nodeIndex)[1]) < Integer.parseInt(resultList.get(resultIndex)[1])) {
                    ++nodeIndex;
                } else {
                    // Take average of y-coordinates
                    int y1 = Integer.parseInt(resultList.get(resultIndex)[2]);
                    int y2 = Integer.parseInt(nodeList.get(nodeIndex)[2]);
                    int yAvg = (y1 + y2) / 2;
                    if (Math.abs(y1 - y2) > 4) yAvg = y1;
                    resultList.get(resultIndex)[2] = yAvg + "";
                    ++resultIndex;
                    ++nodeIndex;
                }
            }
        }
        
        // Restore previous order of resultList
        resultList.sort((arr1, arr2) -> Integer.parseInt(arr1[4]) - Integer.parseInt(arr2[4]));
        for (int i = 0; i < resultList.size(); ++i) {
            resultList.get(i)[4] = "0";
        }
        
        // Add tracing
        nTracer.traceHelper.addTracingToSoma(resultList);
    }
    
    // DEPRECATED
    public void aggregateNeuriteTraces() {
        if ( nTracer.rootNeuronNode.getChildCount() == 0) return;
        
        ArrayList<Integer[]> rankedTraces = getRankedTraces(nTracer.rootNeuronNode);
        
        // Prepare first trace
        ntNeuronNode result = (ntNeuronNode) nTracer.rootNeuronNode.getChildAt(rankedTraces.get(0)[0]).getChildAt(0);
        ArrayList<String[]> resultList = new ArrayList<String []>();
        for (String[] el: result.getTracingResult()) {
            resultList.add(Arrays.copyOf(el, el.length));
        }
        
        // Record previous order
        for (int i = 0; i < resultList.size(); ++i) {
            resultList.get(i)[4] = i + "";
        }
        
        // Sort
        resultList.sort(new PointsCompare());

        
        for (Integer[] trace: rankedTraces.subList(1, rankedTraces.size())) {
            ntNeuronNode node = (ntNeuronNode) nTracer.rootNeuronNode.getChildAt(trace[0]).getChildAt(0);
            ArrayList<String[]> nodeList = new ArrayList<String []> (node.getTracingResult());
            for (String[] el: node.getTracingResult()) {
                nodeList.add(Arrays.copyOf(el, el.length));
            }
            nodeList.sort(new PointsCompare());
            int resultIndex = 0, nodeIndex = 0;
            
            // Iterate through the x-coordinates
            while (nodeIndex < nodeList.size() && resultIndex < resultList.size()) {
                if (Integer.parseInt(nodeList.get(nodeIndex)[1]) > Integer.parseInt(resultList.get(resultIndex)[1])) {
                    ++resultIndex;
                } else if (Integer.parseInt(nodeList.get(nodeIndex)[1]) < Integer.parseInt(resultList.get(resultIndex)[1])) {
                    ++nodeIndex;
                } else {
                    // Take average of y-coordinates
                    int y1 = Integer.parseInt(resultList.get(resultIndex)[2]);
                    int y2 = Integer.parseInt(nodeList.get(nodeIndex)[2]);
                    int yAvg = (y1 + y2) / 2;
                    if (Math.abs(y1 - y2) > 4) yAvg = y1;
                    int z1 = Integer.parseInt(resultList.get(resultIndex)[3]);
                    int z2 = Integer.parseInt(nodeList.get(nodeIndex)[3]);
                    int zAvg = (z1 + z2) / 2;
                    
                    resultList.get(resultIndex)[2] = yAvg + "";
                    resultList.get(resultIndex)[3] = zAvg + "";
                    ++resultIndex;
                    ++nodeIndex;
                }
            }
        }
        
        // Restore previous order of resultList
        resultList.sort((arr1, arr2) -> Integer.parseInt(arr1[4]) - Integer.parseInt(arr2[4]));
        for (int i = 0; i < resultList.size(); ++i) {
            resultList.get(i)[4] = "0";
        }
        
        // Add tracing
        nTracer.traceHelper.addTracingToNeuron(resultList);
    }
    
    /**
    * Show consensus tree after matching is performed
    */
    protected void initConsensusTree() {        
        DefaultTreeModel neuronTreeModel = new DefaultTreeModel(consensusRootNode);
        nTracer.consensusList_jTree = new JTree(neuronTreeModel);
        
        nTracer.neuronList_jScrollPane.setSize(300, 369);
        nTracer.neuronList_jScrollPane.setPreferredSize(new Dimension(300, 369));
        
        nTracer.consensusList_jScrollPane.setLocation(nTracer.consensusList_jScrollPane.getX() - 300, nTracer.consensusList_jScrollPane.getY());
        nTracer.consensusList_jScrollPane.setPreferredSize(new Dimension(300, 369));
        nTracer.consensusList_jScrollPane.setSize(300, 369);
        
        nTracer.consensusList_jTree.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Branches", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N
        ConsensusCellRenderer renderer = new ConsensusCellRenderer();
        renderer.setBackground(Color.red);
        nTracer.consensusList_jTree.setCellRenderer(renderer);
        nTracer.consensusList_jTree.setEditable(false);
        nTracer.consensusList_jTree.setToggleClickCount(0);
        //neuronList_jTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tableTreeSelectionListener treeSelectionListener = new tableTreeSelectionListener();
        nTracer.consensusList_jTree.addTreeSelectionListener(treeSelectionListener);
        nTracer.consensusList_jTree.setRootVisible(false);
        nTracer.consensusList_jTree.setShowsRootHandles(true);
        nTracer.consensusList_jScrollPane.setViewportView(nTracer.consensusList_jTree);
    }
    
    protected void clearConsensusTree() {
        nTracer.consensusList_jTree.removeAll();
        combinedBranches = new ArrayList<>();
        consensusRootNode = null;
        nTracer.consensusList_jScrollPane.setSize(0, 369);
        nTracer.consensusList_jScrollPane.setPreferredSize(new Dimension(0, 369));
        nTracer.consensusList_jScrollPane.setLocation(nTracer.consensusList_jScrollPane.getX() + 300, nTracer.consensusList_jScrollPane.getY());
        
        nTracer.neuronList_jScrollPane.setSize(600, 369);
        nTracer.consensusList_jScrollPane.setViewportView(null);

    }
    
    
    
    /**
    * Generate consensus trace
    */
    public ArrayList<String[]> combineBranches(ArrayList<ntNeuronNode> branches) {
        Map<String, Float> densityMap = new HashMap<>();
        
        int[] deltaMain = new int[]{-1, 0, 1};
        
        // Calculate densities
        for (ntNeuronNode branch: branches) {
            Set<String> visited = new HashSet<>();
            for (String[] point: branch.getTracingResult()) {
                String coordString = point[1] + "," + point[2] + "," + point[3];
                float currDensity = densityMap.getOrDefault(coordString, (float) 0);
                densityMap.put(coordString, currDensity + 1);   
                visited.add(coordString);
            }
            
            int[] deltas = new int[]{-6,-5,-4,-3, -2, -1, 0, 1, 2, 3,4,5,6};

            for (String[] point: branch.getTracingResult()) {
                for (int dx: deltas) {
                    for (int dy: deltas) {
                        for (int dz: deltas) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            int currX = Integer.parseInt(point[1]) + dx, currY = Integer.parseInt(point[2]) + dy, currZ = Integer.parseInt(point[3]) + dz;
                            String coordString = currX + "," + currY + "," + currZ;
                            
                            if (visited.contains(coordString)) {
                                continue;
                            }
                            
                            visited.add(coordString);
                            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
                            float currDensity = densityMap.getOrDefault(coordString, (float) 0);
                            float weight = (float) (1 / (float) (1 + 0.8 * distance));
                            densityMap.put(coordString, currDensity + weight);
                        }
                    }
                }
            }
        }
        
        ArrayList<String []> origins = new ArrayList<>();
        ArrayList<int []> goals = new ArrayList<>();
        Map<String, Float> visited = new HashMap<>();
        Map<String, String> history = new HashMap<>();
        
        
        String[] firstStart = null;
        float BOUNDARY_THRESHOLD = (float) 1;

        for (ntNeuronNode branch: branches) {
            int samplingWindow = Math.min((int) ((float) branch.getTracingResult().size() * 0.05), 20);
            String[] point1 = null, point2 = null;
            for (int b = 0; b < branch.getTracingResult().size(); ++b) {
                String[] point = branch.getTracingResult().get(b);
                if (densityMap.get(point[1] + "," + point[2] + "," + point[3]) > BOUNDARY_THRESHOLD) {
                    boolean windowCheckPassed = true;
                    
                    // Check if surrounding points have high enough density
                    for (int c = b + 1; c < Math.min(b + 1 + samplingWindow, branch.getTracingResult().size()); ++c) {
                        String[] nextPoint = branch.getTracingResult().get(c);
                        if (densityMap.get(nextPoint[1] + "," + nextPoint[2] + "," + nextPoint[3]) <= 1) {
                            windowCheckPassed = false;
                            break;
                        }
                    }
                    if (!windowCheckPassed) continue;
                    
                    point1 = point;
                    break;
                }
            }
            
            for (int b = 0; b < branch.getInvertTracingResult().size(); ++b) {
                String[] point = branch.getInvertTracingResult().get(b);
                if (densityMap.get(point[1] + "," + point[2] + "," + point[3]) > BOUNDARY_THRESHOLD) {
                    boolean windowCheckPassed = true;
                    
                    // Check if surrounding points have high enough density
                    for (int c = b + 1; c < Math.min(b + 1 + samplingWindow, branch.getInvertTracingResult().size()); ++c) {
                        String[] nextPoint = branch.getInvertTracingResult().get(c);
                        if (densityMap.get(nextPoint[1] + "," + nextPoint[2] + "," + nextPoint[3]) <= 1) {
                            windowCheckPassed = false;
                            break;
                        }
                    }
                    if (!windowCheckPassed) continue;
                    point2 = point;
                    break;
                }
            }
            
            if (point1 == null || point2 == null) return new ArrayList<String[]>();
            
            // Assign points to start/end
            String[] startPoint, endPoint;
            if (origins.size() == 0) {
                startPoint = firstStart = point1;
                endPoint = point2;
            } else {
                float d1 = (float) Math.sqrt(
               Math.pow(Integer.parseInt(point1[1]) - Integer.parseInt(firstStart[1]), 2)
               + Math.pow(Integer.parseInt(point1[2]) - Integer.parseInt(firstStart[2]), 2)
               +Math.pow(Integer.parseInt(point1[3]) - Integer.parseInt(firstStart[3]), 2));
                
               float d2 = (float) Math.sqrt(
               Math.pow(Integer.parseInt(point2[1]) - Integer.parseInt(firstStart[1]), 2)
               + Math.pow(Integer.parseInt(point2[2]) - Integer.parseInt(firstStart[2]), 2)
               +Math.pow(Integer.parseInt(point2[3]) - Integer.parseInt(firstStart[3]), 2));
               
               if (d1 < d2) {
                   startPoint = point1;
                   endPoint = point2;
               } else {
                   startPoint = point2;
                   endPoint = point1;
               }
            }
            
            origins.add(new String[] {startPoint[1], startPoint[2], startPoint[3], densityMap.get(startPoint[1] + "," + startPoint[2] + "," + startPoint[3])+""});
            
            goals.add(new int[] {Integer.parseInt(endPoint[1]),
                Integer.parseInt(endPoint[2]),
                Integer.parseInt(endPoint[3])});
            visited.put(startPoint[1] + "," + startPoint[2] + "," + startPoint[3], (float) 0);
        }
        
        
        // A-star search from different origins
        int maxMaxLength = 0;
        ArrayList<String[]> maxMaxBranch = new ArrayList<>();
        for (String[] origin: origins) {
            PriorityQueue<String []> pq = new PriorityQueue<>((x, y) -> Float.compare(Float.parseFloat(x[3]), Float.parseFloat(y[3]))); // Min pq with ordering by density
            pq.add(origin);
            ArrayList<int []> targets = new ArrayList<>();
            while(pq.size() > 0) {
                String[] current = pq.poll();

                for (int[] goal: goals) {
                    // If reached goal, add to backtrack
                    if (goal[0] == Integer.parseInt(current[0]) &&
                            goal[1] == Integer.parseInt(current[1]) &&
                            goal[2] == Integer.parseInt(current[2])) {
                        targets.add(new int[] {Integer.parseInt(current[0]), Integer.parseInt(current[1]), Integer.parseInt(current[2])});
                    }
                }

                String prevCoordinates = current[0] + "," + current[1] + "," + current[2];

                for (int dx: deltaMain) {
                    for (int dy: deltaMain) {
                        for (int dz: deltaMain) { 
                            int currX = Integer.parseInt(current[0]) + dx, currY = Integer.parseInt(current[1]) + dy, currZ = Integer.parseInt(current[2]) + dz;
                            String currCoordinates = currX + "," + currY + "," + currZ;

                            if (visited.containsKey(currCoordinates) || !densityMap.containsKey(currCoordinates)) {
                                continue;
                            }

                            if (densityMap.get(currCoordinates) <= 0.5) {
                                continue;
                            }

                            // Use distance as heuristic to estimate future cost of path
                            float hcost = 0;
                            for (int[] goal: goals) {
                               float distance = (float) Math.sqrt(
                                       Math.pow(goal[0] - currX, 2)
                                       + Math.pow(goal[1] - currY, 2)
                                       +Math.pow(goal[2] - currZ, 2)
                               );
                               float cost = Math.max(distance - 1, 0);
                               if (cost > hcost) {
                                   hcost = cost;
                               }
                            }

                            float ecost = 1 / densityMap.get(currCoordinates);
//                            float ecost = 1 + (1 / densityMap.get(currCoordinates));
                            float pcost = visited.getOrDefault(prevCoordinates, (float) 0) + ecost;
                            visited.put(currCoordinates, pcost);
                            history.put(currCoordinates, prevCoordinates);
                            pq.add(new String[]{currX+"", currY+"", currZ+"", (hcost + pcost) + ""});
                        }
                    }
                }
            }

            
            // Choose target that gives longest path
            int maxLength = 0;
            ArrayList<String[]> maxBranch = new ArrayList<>();
            for (int[] current: targets) {
                ArrayList<String[]> combinedBranch = new ArrayList<>();

                if (current == null) return combinedBranch;

                int length = 0;
                while (true) {
                    ++length;
                    combinedBranch.add(new String[]{current[0]+"", current[1]+"", current[2]+""});
                    String[] currentString = history.getOrDefault(current[0] + "," + current[1] + "," + current[2], "").split(",");
                    if (currentString[0] == "") break; // at the source

                    current = new int[] {Integer.parseInt(currentString[0]), Integer.parseInt(currentString[1]), Integer.parseInt(currentString[2])};
                }

                if (length > maxLength) {
                    maxLength = length;
                    maxBranch = combinedBranch;
                }
            }
            
            // Choose origin that gives longest path
            if (maxLength > maxMaxLength) {
                maxMaxLength = maxLength;
                maxMaxBranch = maxBranch;
            }
        }
       
        return maxMaxBranch; 
    }
    
    /**
    * Determine matching branches
    */
    public void aggregateBranches() {
        if (nTracer.rootNeuronNode.getChildCount() == 0) return;
        
        ArrayList<ArrayList<String[]>>[] simplifiedTraces = new ArrayList[nTracer.rootNeuronNode.getChildCount()];
        ArrayList<ntNeuronNode>[] allBranches = new ArrayList[nTracer.rootNeuronNode.getChildCount()];
        
        for (int i = 0; i < nTracer.rootNeuronNode.getChildCount(); ++i) { // for each trace
            ArrayList<ntNeuronNode> branches = new ArrayList<>();
            decomposeBranches((ntNeuronNode) nTracer.rootNeuronNode.getChildAt(i), branches);
            allBranches[i] = branches;

            ArrayList<ArrayList<String[]>> branchPoints = new ArrayList<>();
            
            for (ntNeuronNode branch: branches) {
                ArrayList<String[]> points = branch.getTracingResult();
                branchPoints.add(points);
            }
            
            simplifiedTraces[i] = branchPoints;
        }

        

        ArrayList<ArrayList<int[]>> uniqueBranches = new ArrayList<ArrayList<int[]>>();
        int matches = 0;
        for (int i = 0; i < simplifiedTraces.length; ++i) { // for each trace
            // Find best branch matchings
            ArrayList<Integer>[] branchMatchings = new ArrayList[simplifiedTraces[i].size()];
            for (int b = 0; b < simplifiedTraces[i].size(); ++b) {
                branchMatchings[b] = new ArrayList<>();
            }
            final double COST_THRESHOLD = 0.05;
            
            for (int b = 0; b < simplifiedTraces[i].size(); ++b) {
                ArrayList<String[]> branch = simplifiedTraces[i].get(b);
                boolean newBranch = true;
                for (int u = 0; u < uniqueBranches.size(); ++u) {
                    boolean reject = false;
                    for (int[] uniqueBranch: uniqueBranches.get(u)) {
                        ArrayList<String[]> otherBranch = simplifiedTraces[uniqueBranch[0]].get(uniqueBranch[1]);
                        BranchCorrelation bc = new BranchCorrelation(branch, otherBranch);
                        if (bc.cost < COST_THRESHOLD) { // Determine if branches are similar enough
                            reject = true;
                            break;
                        }
                    }
                    
                    if (!reject) {
                        ++matches;
                        int newArr[] = {i, b};
                        uniqueBranches.get(u).add(newArr);
                        branchMatchings[b].add(u);
                        newBranch = false;
                    }   
                }
                if (newBranch) {
                    int newArr[] = {i, b};
                    ArrayList<int[]> newArrList = new ArrayList<int[]>();
                    newArrList.add(newArr);
                    uniqueBranches.add(newArrList);
                }
            }
        }
        
        consensusRootNode = new ntNeuronNode("Root", new ArrayList<String[]>());
//      Update GUI to display branch matchings
        for (int i = 0; i < uniqueBranches.size(); ++i) {
            ntNeuronNode branchNode = new ntNeuronNode("Branch " + i, new ArrayList<String[]>());
            ArrayList<ntNeuronNode> matchedBranches = new ArrayList<>();
            
            ArrayList<int[]> branch = uniqueBranches.get(i);
            for (int[] match: branch) {
                int traceIndex = match[0], branchIndex = match[1];
                allBranches[traceIndex].get(branchIndex).addBranchLabelToName(i);
                allBranches[traceIndex].get(branchIndex).branchMatches += branch.size();
                
                ntNeuronNode sourceNode = allBranches[traceIndex].get(branchIndex);
                branchNode.add(new ntNeuronNode(sourceNode.toString(), sourceNode.getTracingResult()));
                matchedBranches.add(sourceNode);
            }
            
            combinedBranches.add(combineBranches(matchedBranches));
            consensusRootNode.add(branchNode);
        }
        initConsensusTree();
        nTracer.neuronList_jTree.updateUI();
    }
    
    /**
    * Decompose branches in trace to linear ArrayList
    */
    protected void decomposeBranches(ntNeuronNode node, ArrayList<ntNeuronNode> branches) {
        for (int i = 0; i < node.getChildCount(); ++i) {
            ntNeuronNode currentNode = (ntNeuronNode) node.getChildAt(i);
            if (currentNode.getTracingResult().size() > 1) branches.add(currentNode);
            decomposeBranches(currentNode, branches);
        }
    }
    
    class tableTreeSelectionListener implements TreeSelectionListener {
        /**
        * Table row selected
        */
        @Override
        public void valueChanged(TreeSelectionEvent e) {
            ArrayList<String[]> tablePoints = new ArrayList<>();
            if (nTracer.consensusList_jTree.getSelectionCount() == 1) {
                ntNeuronNode somaZ = (ntNeuronNode) nTracer.consensusList_jTree.getLastSelectedPathComponent();
                tablePoints = somaZ.getTracingResult();
            } else if (nTracer.consensusList_jTree.getSelectionCount() == 0) {
                if (nTracer.consensusList_jTree.getSelectionCount() == 1) {
                    ntNeuronNode node = (ntNeuronNode) nTracer.consensusList_jTree.getLastSelectedPathComponent();
                    // select neurite in the neuron tree
                    if (node.isBranchNode()) {
                        tablePoints = node.getTracingResult();
                    }
                }
            }
            nTracer.updatePointTable(tablePoints);
            if (nTracer.canUpdateDisplay) {
                nTracer.updateDisplay();
            }
            
            TreePath[] paths = nTracer.consensusList_jTree.getSelectionPaths();
            
            Overlay ov = new Overlay();
            boolean overlayUpdated = false;
            ArrayList<TreePath> neuronListSelections = new ArrayList<>();
            
            if (paths == null) return;
            for (TreePath path: paths) {
                ntNeuronNode selected = (ntNeuronNode) path.getLastPathComponent();
                if (selected.getChildCount() > 0) { // group of branches
                    Map<Integer, Integer> zMap = new HashMap<>();
                    Color [] colors = {Color.GREEN, Color.CYAN, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.PINK};

                    for (int t = 0; t < selected.getChildCount(); ++t) {
                        ntNeuronNode trace = (ntNeuronNode) selected.getChildAt(t);
                        ArrayList<String[]> points = trace.getTracingResult();
                        float [] xarr = new float[points.size()], yarr = new float[points.size()];
                        for (int i = 0; i < points.size(); ++i) {
                            xarr[i] = Float.parseFloat(points.get(i)[1]);
                            yarr[i] = Float.parseFloat(points.get(i)[2]);
                            int z = Integer.parseInt(points.get(i)[3]);
                            zMap.put(z, zMap.getOrDefault(z, 0) + 1);
                            
                                                        
                            if (nTracer.consensusStartEnd_jCheckBox.isSelected() && (i == 0 || i == points.size() - 1)) {
                                OvalRoi circle = new OvalRoi(xarr[i] - 4, yarr[i] - 4, 8, 8);
                                circle.setFillColor(colors[t % 7]);
                                ov.add(circle);
                            }
                            
                            if (i == points.size() - 1) {
                                TextRoi branchIndex = new TextRoi(xarr[i], yarr[i], "" + trace.toString());
                                branchIndex.setStrokeColor(colors[t % 7]);
                                ov.add(branchIndex);
                            }
                        }
 
                        PolygonRoi roiLine = new PolygonRoi(xarr, yarr, Roi.POLYLINE);
                        roiLine.setStrokeColor(colors[t % 7]);
                        roiLine.setStrokeWidth(2);
                        ov.add(roiLine);
                    }
                    
                    overlayUpdated = true;
                    
                    if (nTracer.consensusResult_jCheckBox.isSelected()) {
                        String[] splits = selected.toString().split(" ");
                        ArrayList<String[]> branch = combinedBranches.get(Integer.parseInt(splits[1]));
                        float [] xarr = new float[branch.size()], yarr = new float[branch.size()];
                        for (int i = 0; i < branch.size(); ++i) {
                            xarr[i] = Float.parseFloat(branch.get(i)[0]);
                            yarr[i] = Float.parseFloat(branch.get(i)[1]);
                            int z = Integer.parseInt(branch.get(i)[2]);
                        }

                        PolygonRoi roiLine = new PolygonRoi(xarr, yarr, Roi.POLYLINE);
                        roiLine.setStrokeColor(Color.WHITE);
                        roiLine.setStrokeWidth(2);
                        ov.add(roiLine);
                    }
                    
                } else { // selected individual branch
                    ntNeuronNode current = nTracer.rootNeuronNode;
                    String[] split = selected.toString().split("-");
                    for (String component: split) {
                        current = (ntNeuronNode) current.getChildAt(Integer.parseInt(component) - 1);
                    }
                    
                    neuronListSelections.add(new TreePath(current.getPath()));
                }
            }
            
            if (neuronListSelections.size() > 0) {
                nTracer.neuronList_jTree.setSelectionPaths(neuronListSelections.toArray(new TreePath[0]));
            }
            
            if (overlayUpdated) {
                imp.setOverlay(ov);
            }
        }

    }
    
    /**
    * Color rows depending on matching success/failure
    */
    protected class ConsensusCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean exp, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, hasFocus);
            
            ntNeuronNode node = (ntNeuronNode) value;
            if (node.getChildCount() == 1) { // failed match
                setForeground(Color.RED);
            } else if (node.getChildCount() > 1) { // successful match
                setForeground(Color.GREEN);
            }
            
            return this;
        }
    }
}

/**
* Helper class to calculate similarity between two branches
*/
class BranchCorrelation {
    protected double cost;
    
    BranchCorrelation(ArrayList<String[]> sps1, ArrayList<String[]> sps2) {
        ArrayList<String[]> sBranch = (sps1.size() < sps2.size()) ? sps1 : sps2;
        ArrayList<String[]> lBranch = (sps1.size() < sps2.size()) ? sps2 : sps1;

        HashSet<String> sMap = new HashSet<>();
        int POINT_BUFFER = 3;
        for (String[] point: sBranch) {
            sMap.add(point[1] + "," + point[2] + "," + point[3]);
            for (int xBuffer = -POINT_BUFFER; xBuffer <= POINT_BUFFER; ++xBuffer) {
                for (int yBuffer = -POINT_BUFFER; yBuffer <= POINT_BUFFER; ++yBuffer) {
                    for (int zBuffer = -POINT_BUFFER; zBuffer <= POINT_BUFFER; ++zBuffer) {
                        sMap.add(point[1] + xBuffer + "," + point[2] + yBuffer + "," + point[3] + zBuffer);
                    }
                }
            }
        }
        
        float matches = 0;
        for (String[] point: lBranch) {
            if (sMap.contains(point[1] + "," + point[2] + "," + point[3])) {
                ++matches;
            }
        }
        cost = matches / (float) sBranch.size();
    }
}
