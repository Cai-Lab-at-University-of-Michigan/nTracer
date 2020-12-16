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
}
