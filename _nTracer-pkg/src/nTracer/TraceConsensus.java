/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Wei Jie
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


public class TraceConsensus {
    private nTracer_ nTracer;
    
    TraceConsensus(nTracer_ nTracer) {
        this.nTracer = nTracer;
    }
    
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
    
    public void aggregateBranches() {
        if (nTracer.rootNeuronNode.getChildCount() == 0) return;
        
        ArrayList<ArrayList<Superpoint>>[] simplifiedTraces = new ArrayList[nTracer.rootNeuronNode.getChildCount()];
        
        for (int i = 0; i < nTracer.rootNeuronNode.getChildCount(); ++i) { // for each trace
            ntNeuronNode trace = (ntNeuronNode) nTracer.rootNeuronNode.getChildAt(i).getChildAt(0);
            ArrayList<ArrayList<String[]>> branches = new ArrayList<ArrayList<String[]>>();
            branches.add(trace.getTracingResult());
            for (int j = 0; j < trace.getChildCount(); ++j) {
                ntNeuronNode branch = (ntNeuronNode) trace.getChildAt(j);
                branches.add(branch.getTracingResult());
            }
            
            
            // Generate superpoints
            ArrayList<ArrayList<Superpoint>> branchSuperpoints = new ArrayList<ArrayList<Superpoint>>();
            final int SUPERPOINT_STEP = 5;
            
            for (ArrayList<String[]> branch: branches) {
                ArrayList<Superpoint> sps = new ArrayList<Superpoint>();
                for (int j = 0; j < branch.size(); j += SUPERPOINT_STEP) {
                    int end = (j + SUPERPOINT_STEP < branch.size()) ? j + SUPERPOINT_STEP : branch.size();
                    // Average the points
                    int avgX = 0, avgY = 0, avgZ = 0;
                    for (int p = j; p < end; ++p) {
                        avgX += Integer.parseInt(branch.get(p)[1]);
                        avgY += Integer.parseInt(branch.get(p)[2]);
                        avgZ += Integer.parseInt(branch.get(p)[3]);
                    }
                    
                    avgX /= end - j;
                    avgY /= end - j;
                    avgZ /= end - j;
                    
                    Superpoint sp = new Superpoint(avgX, avgY, avgZ, end - j);
                    sps.add(sp);
                }
                
                // Sort the superpoints
                sps.sort(null);
                branchSuperpoints.add(sps);
            }
            
            simplifiedTraces[i] = branchSuperpoints;
        }
        
        // Generate list of unique branches from traces
        ArrayList<ArrayList<int[]>> uniqueBranches = new ArrayList<ArrayList<int[]>>();
        
        for (int i = 0; i < simplifiedTraces.length; ++i) { // for each trace
            // Find best branch matchings
            ArrayList<Integer>[] branchMatchings = new ArrayList[simplifiedTraces[i].size()];
            for (int b = 0; b < simplifiedTraces[i].size(); ++b) {
                branchMatchings[b] = new ArrayList<>();
            }

            for (int b = 0; b < simplifiedTraces[i].size(); ++b) {
                ArrayList<Superpoint> branch = simplifiedTraces[i].get(b);
                for (int u = 0; u < uniqueBranches.size(); ++u) {
                    int[] uniqueBranch = uniqueBranches.get(u).get(0);
                    ArrayList<Superpoint> otherBranch = simplifiedTraces[uniqueBranch[0]].get(uniqueBranch[1]);
                    BranchCorrelation bc = new BranchCorrelation(branch, otherBranch);
                    final float COST_THRESHOLD = 1;
                    final double DEGREE_THRESHOLD = 30;
                    if (bc.cost < COST_THRESHOLD && bc.degree < DEGREE_THRESHOLD) { // Determine if branches are similar enough
                        branchMatchings[b].add(u);
                    }
                }
            }
            
            for (int b = 0; b < simplifiedTraces[i].size(); ++b) {
                if (branchMatchings[b].size() == 0) {
                    int newArr[] = {i, b};
                    ArrayList<int[]> newArrList = new ArrayList<int[]>();
                    newArrList.add(newArr);
                    uniqueBranches.add(newArrList);
                } else {
                    for (int correspondingBranch: branchMatchings[b]) {
                        int newArr[] = {i, b};
                        uniqueBranches.get(correspondingBranch).add(newArr);
                    }
                }
            }
        }
        
        // Update GUI to display branch matchings
        for (int i = 0; i < uniqueBranches.size(); ++i) {
            ArrayList<int[]> branch = uniqueBranches.get(i);
            for (int[] match: branch) {
                int traceIndex = match[0], branchIndex = match[1];
                ntNeuronNode trace = (ntNeuronNode) nTracer.rootNeuronNode.getChildAt(traceIndex).getChildAt(0);
                if (branchIndex == 0) {
                    trace.addBranchLabelToName(i);
                } else {
                    ((ntNeuronNode )trace.getChildAt(branchIndex - 1)).addBranchLabelToName(i);
                }
            }
        }
        nTracer.neuronList_jTree.updateUI();
    }
}

class BranchCorrelation {
    protected double cost, degree;
    
    BranchCorrelation(ArrayList<Superpoint> sps1, ArrayList<Superpoint> sps2) {
        int totalPoints1 = 0, totalPoints2 = 0;
        for (Superpoint sp: sps1) {
            totalPoints1 += sp.size;
        }
        
        for (Superpoint sp: sps2) {
            totalPoints2 += sp.size;
        }
        
        // Align the 2 branches
        int startingi = 0, startingj = 0;
        int startingScore = calculateEclidianDistanceSquared(sps1.get(0), sps2.get(0));
        
        while(startingi < sps1.size() / 3 && startingj < sps2.size() / 3) { // Do not align beyond 1/3 of branches
            if (sps1.get(startingi).x < sps1.get(startingj).x) {
                if (calculateEclidianDistanceSquared(sps1.get(startingi + 1), sps2.get(startingj)) > startingScore) {
                    ++startingi;
                } else {
                    break;
                }
            } else if (sps1.get(startingi).x > sps1.get(startingj).x) {
                if (calculateEclidianDistanceSquared(sps1.get(startingi), sps2.get(startingj + 1)) > startingScore) {
                    ++startingj;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        
        // Calculate eclidian distances and angles
        int distances = 0;
        double deltaX1 = 0, deltaY1 = 0, deltaX2 = 0, deltaY2 = 0;
        for (int i = startingi, j = startingj; i < sps1.size() && j < sps2.size(); ++i, ++j) {
            Superpoint sp1 = sps1.get(i), sp2 = sps2.get(j);
            distances += calculateEclidianDistanceSquared(sp1, sp2);
            if (i != startingi) {
                deltaX1 += sp1.x - sps1.get(i-1).x;
                deltaY1 += sp1.y - sps1.get(i-1).y;
                deltaX2 += sp2.x - sps2.get(j-1).x;
                deltaY2 += sp2.y - sps2.get(j-1).y;
            }
        }
        
        deltaX1 = Math.abs(deltaX1);
        deltaY1 = Math.abs(deltaY1);
        deltaX2 = Math.abs(deltaX2);
        deltaY2 = Math.abs(deltaY2);
        
        double degree1 = Math.atan2(deltaY1, deltaX1), degree2 = Math.atan2(deltaY2, deltaX2);
        
        // Angular distance between 2 branches
        degree = Math.abs(degree1 - degree2) / Math.PI * 180;
        // Normalized distance between 2 branches. Longer traces have less penalty (power of 1.5 to length)
        cost = distances / (double) Math.pow(Math.min(totalPoints1-startingi, totalPoints2-startingj), 1.5);
    }
    
    // Calculate square of eclidian distance between 2 points. We avoid taking square root to speed up computation.
    private int calculateEclidianDistanceSquared(Superpoint sp1, Superpoint sp2) {
        return (int) Math.pow((Math.pow((sp1.x - sp2.x), 2) + Math.pow((sp1.y - sp2.y), 2) + Math.pow((sp1.z - sp2.z), 2)), 0.5);
    }
}

class Superpoint implements Comparable<Superpoint> {
    protected int x, y, z, size;
    
    Superpoint(int x, int y, int z, int size) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.size = size;
    }

    @Override
    public int compareTo(Superpoint sp) {
        if (this.x < sp.x) return -1;
        if (this.x > sp.x) return 1;
        if (this.y < sp.y) return -1;
        if (this.y < sp.y) return 1;
        if (this.z < sp.z) return -1;
        if (this.z > sp.z) return -1;
        return 0;
    }

    @Override
    public String toString() {
        return "x=" + x + ", y=" + y;
    }
}
