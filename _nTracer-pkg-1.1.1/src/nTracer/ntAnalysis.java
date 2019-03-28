/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import ij.IJ;
import ij.gui.FreehandRoi;
import ij.gui.GenericDialog;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import java.text.DecimalFormat;
import java.util.ArrayList;
import static nTracer.nTracer_.getAllPrimaryBranchPoints;
import static nTracer.nTracer_.imp;
import static nTracer.nTracer_.impNChannel;
import static nTracer.nTracer_.rootNeuronNode;
import static nTracer.nTracer_.stk;

/**
 *
 * @author Dawen Cai <dwcai@umich.edu>
 */
public class ntAnalysis {
    
    public ntAnalysis() {
    }    

    // log perimeter and volume of all traced somas
    public void logAllSomaStatistics(){
        for (int i = 0; i < nTracer_.rootAllSomaNode.getChildCount(); i++) {
            ntNeuronNode childSoma = (ntNeuronNode) nTracer_.rootAllSomaNode.getChildAt(i);
            if (childSoma.getChildCount()>0){
                logOneSomaStatistics(childSoma);
            }
        }
    }   
    // log perimeter and volume of a selected neuron
    public void logOneSomaStatistics(ntNeuronNode somaNode) {
        //ImageProcessor ip = imp.getProcessor();
        DecimalFormat twoDigit = new DecimalFormat("#.00");
        String neuronNumber = somaNode.getNeuronNumber();
        IJ.log("Neuron "+neuronNumber+"; total "+somaNode.getChildCount()+" slices");
        double totalSurface = 0.00;
        double totalVolume = 0.00;
        for (int i = 0; i<somaNode.getChildCount(); i++){
            ntNeuronNode somaSliceNode = (ntNeuronNode)somaNode.getChildAt(i);
            ArrayList<String[]> tracingResult = somaSliceNode.getTracingResult();
            int totalTracedPoints = tracingResult.size();
            int[] tracedXs = new int [totalTracedPoints];
            int[] tracedYs = new int [totalTracedPoints];
            String tracedZ = tracingResult.get(0)[3];
            for (int r = 0; r < totalTracedPoints; r++) {
                tracedXs[r] = (Integer)(Integer.parseInt(tracingResult.get(r)[1]));
                tracedYs[r] = (Integer)(Integer.parseInt(tracingResult.get(r)[2]));
            }
            PolygonRoi sliceRoi = new PolygonRoi (tracedXs, tracedYs, totalTracedPoints, Roi.FREEROI);
            //sliceRoi.fitSpline();
            double perimeter = sliceRoi.getLength();
            totalSurface = totalSurface+perimeter;
            //ip.setRoi(sliceRoi);
            //ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.MEAN, imp.getCalibration());
            double area = sliceRoi.getStatistics().area;
            totalVolume = totalVolume+area;
            IJ.log("Slice: "+tracedZ+" perimeter: "+Double.valueOf(twoDigit.format(perimeter))
                    +" area: "+Double.valueOf(twoDigit.format(area)));
        }
        Calibration cal = imp.getCalibration();
        IJ.log("Image resolutions:   x = " + cal.pixelWidth + " " + cal.getUnit() + "/pixel; "+
                "y = " + cal.pixelHeight + " " + cal.getUnit() + "/pixel; "+
                "z = " + cal.pixelDepth + " " + cal.getUnit() + "/pixel");
        IJ.log("total surface area: "+Double.valueOf(twoDigit.format(totalSurface))+" pixel^2 = "+
                Double.valueOf(twoDigit.format((totalSurface*Math.sqrt(cal.pixelWidth*cal.pixelHeight)*cal.pixelDepth)))+" "+cal.getUnit()+"^2; ");
        IJ.log("total volume: "+Double.valueOf(twoDigit.format(totalVolume))+" voxels = "+Double.valueOf(twoDigit.format((totalVolume*cal.pixelWidth*cal.pixelHeight*cal.pixelDepth)))+" "+cal.getUnit()+"^3");
        IJ.log("");
        IJ.log("----------------------------------------------------------");
        IJ.log("");
    }
    
    // log connections of all traced neurons
    public void logAllNeuronConnections(){
        for (int i = 0; i < nTracer_.rootNeuronNode.getChildCount(); i++) {
            ntNeuronNode childNeuron = (ntNeuronNode) nTracer_.rootNeuronNode.getChildAt(i);
            logWholeNeuronConnections(childNeuron);
        }
    }    
    // log connections of a selected neuron and its branches
    public void logWholeNeuronConnections(ntNeuronNode neuronNode) {
        ArrayList<String> allInputNeuronNumber = new ArrayList<String>();
        ArrayList<String> somaConnectedNeuronNumber = new ArrayList<String>();
        ArrayList<String> neuriteConnectedNeuronNumber = new ArrayList<String>();
        ArrayList<String> dendriteConnectedNeuronNumber = new ArrayList<String>();
        ArrayList<String> axonConnectedNeuronNumber = new ArrayList<String>();
        
        String neuronNumber = neuronNode.getNeuronNumber();
        IJ.log("Neuron "+neuronNumber);
        
        ntNeuronNode somaNode = nTracer_.getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNumber);
        logAllSomaConnections(somaNode, somaConnectedNeuronNumber);
        logAllBranchConnections(neuronNode, neuriteConnectedNeuronNumber, 
                dendriteConnectedNeuronNumber, axonConnectedNeuronNumber);
        for (String connected : somaConnectedNeuronNumber) {
            allInputNeuronNumber.add(connected);
        }
        for (String connected : dendriteConnectedNeuronNumber) {
            allInputNeuronNumber.add(connected);
        }

        if (allInputNeuronNumber.isEmpty() && neuriteConnectedNeuronNumber.isEmpty() && axonConnectedNeuronNumber.isEmpty()){
            IJ.log("has no connections");
        } else {
            IJ.log("");
            IJ.log("Soma receives "+somaConnectedNeuronNumber.size()+" connections to "
                    +sortUniqueNeuronCountConnections(somaConnectedNeuronNumber).size()+" neurons.");     
            IJ.log("Dendrites receive "+dendriteConnectedNeuronNumber.size()+" connections to "
                    +sortUniqueNeuronCountConnections(dendriteConnectedNeuronNumber).size()+" neurons."); 
            IJ.log("Axon sends out " + axonConnectedNeuronNumber.size() + " connections to "
                    + sortUniqueNeuronCountConnections(axonConnectedNeuronNumber).size() + " neurons.");
            if (neuriteConnectedNeuronNumber.size() > 0) {
                IJ.log("*** Check connection ERROR *** Neurite made " + neuriteConnectedNeuronNumber.size() + " connections to "
                        + sortUniqueNeuronCountConnections(neuriteConnectedNeuronNumber).size() + " neurons.");
            }
            IJ.log("");
            ArrayList<int[]> sortedUniqueInputConnection = sortUniqueNeuronCountConnections(allInputNeuronNumber);
            IJ.log("Neuron "+neuronNumber+" receive "+allInputNeuronNumber.size()+" total somatic and dendritic inputs from "
                    +sortedUniqueInputConnection.size()+" other neurons.");
            String[][] binInputNeurons = sortNeuronsWithSameConnectionNumber(sortedUniqueInputConnection);
            for (int i = 1; i< binInputNeurons.length; i++){
                if (!binInputNeurons[i][0].equals("0")){
                    IJ.log(binInputNeurons[i][0] + " other neurons, each of which contributes "+i+" inputs ( "+binInputNeurons[i][1]+")");
                }
            }
            IJ.log("");
            ArrayList<int[]> sortedUniqueOutputConnection = sortUniqueNeuronCountConnections(axonConnectedNeuronNumber);
            IJ.log("Neuron "+neuronNumber+" send out "+axonConnectedNeuronNumber.size()+" total axonal outputs to "
                    +sortedUniqueOutputConnection.size()+" other neurons.");
            String[][] binOutputNeurons = sortNeuronsWithSameConnectionNumber(sortedUniqueOutputConnection);
            for (int i = 1; i< binOutputNeurons.length; i++){
                if (!binOutputNeurons[i][0].equals("0")){
                    IJ.log(binOutputNeurons[i][0]+ " other neurons, each of which receives "+i+" outputs ( "+binOutputNeurons[i][1]+")");
                }
            }
        }
        IJ.log("");
        IJ.log("----------------------------------------------------------");
        IJ.log("");
    }
    
    // log (and return) connections of selected soma(s)
    public void logAllSomaConnections(ntNeuronNode somaSomaNode, 
            ArrayList<String> somaConnectedNeuronNumber){
        boolean nameLogged;

        for (int i = 0; i < somaSomaNode.getChildCount(); i++) {
            nameLogged = false;
            String logTag = "";
            ntNeuronNode somaSliceNode = (ntNeuronNode) somaSomaNode.getChildAt(i);
            ArrayList<String[]> tracedPoints = somaSliceNode.getTracingResult();
            for (String[] tracedPoint : tracedPoints) {
                if (!tracedPoint[6].equals("0")) {
                    if (!nameLogged) {
                        logTag = "Soma "+somaSliceNode.toString()+ "<>";
                        nameLogged = true;
                    }
                    String connectedName = tracedPoint[6].split("#")[1];
                    logTag = logTag+" #"+connectedName;
                    if (connectedName.contains(":")){
                        somaConnectedNeuronNumber.add(connectedName.split(":")[0]);
                    } else {
                        somaConnectedNeuronNumber.add(connectedName.split("-")[0]);
                    }                    
                }
            }
            if (!"".equals(logTag)){
                IJ.log(logTag);      
            }
        }
    }
    
    // log (and return) connections of selected branch(es)
    public void logAllBranchConnections(ntNeuronNode parentBranchNode,
                ArrayList<String> neuriteConnectedNeuronNumber, 
                ArrayList<String> dendriteConnectedNeuronNumber,
                ArrayList<String> axonConnectedNeuronNumber){
        boolean nameLogged = false;
        String logTag = "";
        //IJ.log("debug: "+neuronSomaNode.toString()+" has "+neuronSomaNode.getChildCount()+" childs");
        ArrayList<String[]> tracedPoints = parentBranchNode.getTracingResult();
        for (String[] tracedPoint : tracedPoints) {
            if (!tracedPoint[6].equals("0")) {
                if (!nameLogged) {
                    logTag = tracedPoint[0] + parentBranchNode.toString() + "<>";
                    nameLogged = true;
                }
                String connectedName = tracedPoint[6].split("#")[1];
                logTag = logTag + " #" + connectedName;
                if (connectedName.contains(":")) {
                    connectedName = connectedName.split(":")[0];
                } else {
                    connectedName = connectedName.split("-")[0];
                }
                if (tracedPoint[0].equals("Neurite")) {
                    neuriteConnectedNeuronNumber.add(connectedName);
                } else if (tracedPoint[0].equals("Axon")) {
                    axonConnectedNeuronNumber.add(connectedName);
                } else {
                    dendriteConnectedNeuronNumber.add(connectedName);
                }
            }
        }
        if (!"".equals(logTag)) {
            IJ.log(logTag);
        }
            
        for (int i = 0; i < parentBranchNode.getChildCount(); i++) {            
                logAllBranchConnections((ntNeuronNode)parentBranchNode.getChildAt(i),
                    neuriteConnectedNeuronNumber, dendriteConnectedNeuronNumber, axonConnectedNeuronNumber);
        }
    }
    
    // sort unique neuron and count the number of their connections
    public ArrayList<int[]> sortUniqueNeuronCountConnections(
            ArrayList<String> neuronNumbers){
        ArrayList<int[]> uniqueNeurons = new ArrayList<int[]>();
        // String[0] = unique neuron#; String[1] = number of connections to that neuron
        if (neuronNumbers != null) {
            for (String neuronNumber : neuronNumbers) {
                boolean isUnique = true;
                for (int[] uniqueNeuron : uniqueNeurons) {
                    if (neuronNumber.equals(uniqueNeuron[0] + "")) {
                        uniqueNeuron[1]++;
                        isUnique = false;
                        break;
                    }
                }
                if (isUnique) {
                    int[] uniqueNeuron = {Integer.parseInt(neuronNumber), 1};
                    uniqueNeurons.add(uniqueNeuron);
                }
            }
        }
        return uniqueNeurons;
    }
    
    // bin neurons with same connection number
    public String[][] sortNeuronsWithSameConnectionNumber(
            ArrayList<int[]> sortedUniqueNeuronConnection){
        if (sortedUniqueNeuronConnection==null){
            return null;
        }        
        // find the number of max connections to one neuron
        int maxConnections = 1;
        for (int[] sortedNeuron : sortedUniqueNeuronConnection){
            if (sortedNeuron[1] > maxConnections){
                maxConnections = sortedNeuron[1];
            }
        }
        String[][] binnedNeurons = new String[maxConnections+1][2];
        for (String[] binnedNeuron : binnedNeurons) {
            binnedNeuron[0] = "0";
            binnedNeuron[1] = "";
        }
        for (int[] sortedNeuron : sortedUniqueNeuronConnection){
                binnedNeurons[sortedNeuron[1]][0] = Integer.parseInt(binnedNeurons[sortedNeuron[1]][0])+1+"";
                binnedNeurons[sortedNeuron[1]][1] = binnedNeurons[sortedNeuron[1]][1]+"#"+sortedNeuron[0]+" ";
        }
        return binnedNeurons;
    }
    
    // log Color Ratio of all traced neurons
    public void logNeuronColorRatio() {
        for (int n = 0; n < rootNeuronNode.getChildCount(); n++) {
            ntNeuronNode neuronSomaNode = (ntNeuronNode) rootNeuronNode.getChildAt(n);
            float[] color = getNeuronColorRatioFromNode(neuronSomaNode);
            String logTag = "Neuron "+neuronSomaNode.getNeuronNumber()+" : color ("+color[0];
            for (int i = 1; i<color.length; i++){
                logTag = logTag + ", " + color[i];
            }
            logTag = logTag+")";
            IJ.log(logTag);
        }
    }
    // log ColorRatio of selected neuron(s)

    // log intensity of all traced neurons
    public void logNeuronNormChIntensity() {
        for (int n = 0; n < rootNeuronNode.getChildCount(); n++) {
            ntNeuronNode neuronSomaNode = (ntNeuronNode) rootNeuronNode.getChildAt(n);
            float[] intensity = getNeuronColorIntensityFromNode(neuronSomaNode);
            String logTag = "Neuron "+neuronSomaNode.getNeuronNumber()+" : intensity ("+intensity[0];
            for (int i = 1; i<intensity.length; i++){
                logTag = logTag + ", " + intensity[i];
            }
            logTag = logTag+")";
            IJ.log(logTag);
        }
    }
    
    // log intensity line measurement of selected branch
    public void logBranchIntensityLineMeasure(ntNeuronNode selectedNeuriteNode){
        IJ.log("Branch "+selectedNeuriteNode.toString()+" :");
        ArrayList<String[]> tracingResult = selectedNeuriteNode.getTracingResult();
        for (int n = 0; n < tracingResult.size(); n++){
            String[] coordinate = tracingResult.get(n);
            IJ.log("("+coordinate[1]+", "+coordinate[2]+", "+coordinate[3]+")");
        }
        IJ.log("");
    }
    
    
    // other methods for analysis -----------------------------------------------------------
    public float[] getNeuronColorRatioFromNode(ntNeuronNode neuronSomaNode) {
        ArrayList<int[]> neuronPoints = nTracer_.getAllPrimaryBranchPoints(neuronSomaNode);
        if (neuronPoints.isEmpty()) {
            return null;
        } else {
            float[] tempColor = new float[impNChannel];
            for (int channel = 0; channel < impNChannel; channel++) {
                tempColor[channel] = 0;
            }
            for (int[] neuronPt : neuronPoints) {
                for (int channel = 0; channel < impNChannel; channel++) {
                    int index = nTracer_.imp.getStackIndex(channel + 1, neuronPt[3], nTracer_.imp.getFrame());
                    // retrive color[channel] and calculate total intensity
                    tempColor[channel] = tempColor[channel] + nTracer_.stk.getProcessor(index).get(neuronPt[1], neuronPt[2]);
                }
            }

            float intensity = 0;
            for (int i = 0; i < impNChannel; i++) {
                intensity += tempColor[i];
            }
            float[] color = new float[impNChannel];
            for (int i = 0; i < impNChannel; i++) {
                color[i] = tempColor[i] / intensity;
            }
            return color;
        }
    }
    
    public float[] getNeuronColorIntensityFromNode(ntNeuronNode neuronSomaNode) {
        ArrayList<int[]> neuronPoints = getAllPrimaryBranchPoints(neuronSomaNode);
        if (neuronPoints.isEmpty()) {
            return null;
        } else {
            float[] tempColor = new float[impNChannel];
            for (int channel = 0; channel < impNChannel; channel++) {
                tempColor[channel] = 0;
            }
            for (int[] neuronPt : neuronPoints) {
                for (int channel = 0; channel < impNChannel; channel++) {
                    int index = imp.getStackIndex(channel + 1, neuronPt[3], imp.getFrame());
                    // retrive color[channel] and calculate total intensity
                    tempColor[channel] = tempColor[channel]
                            + stk.getProcessor(index).get(neuronPt[1], neuronPt[2]);
                }
            }

            float[] color = new float[impNChannel];
            for (int i = 0; i < impNChannel; i++) {
                color[i] = tempColor[i] / neuronPoints.size();
            }
            return color;
        }
    }

}
