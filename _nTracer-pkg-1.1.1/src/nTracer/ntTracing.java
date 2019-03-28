/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.plugin.Duplicator;
import ij.gui.Roi;
import ij.plugin.filter.Convolver;
import ij.process.LUT;
import nTracer.Pathfinding.grid.GridLocation;
import nTracer.Pathfinding.grid.GridMap;
import nTracer.Pathfinding.grid.GridPath;
import nTracer.Pathfinding.grid.astar.GridAstar;
import nTracer.Pathfinding.grid.astar.GridHeuristicEuclidean;
import umontreal.iro.lecuyer.functionfit.SmoothingCubicSpline;
import java.awt.Color;
import java.util.ArrayList;
import static nTracer.nTracer_.activeChannels;
import static nTracer.nTracer_.analysisChannels;

/**
 * Functions that perform tracing
 *
 * @author Dawen Cai <dwcai@umich.edu>
 */
public class ntTracing {

    public ntTracing() {
    }

    public void setup(ImagePlus imp) {
        this.imp = imp;
        impWidth = imp.getWidth();
        impHeight = imp.getHeight();
        impNChannel = imp.getNChannels();
        impNSlice = imp.getNSlices();
        stk = imp.getImageStack();
    }

    /**
     * Retrieve the minimum cost path between startPoint and endPoint. The cost
     * map is a crop of the original image wrapping around the startPoint and
     * endPoint. The cost of each pixel is weighted by intensity and color
     * distance to the average color of startPoint and endPoint
     *
     * @param startPoint
     * @param endPoint
     * @param analysisCh
     * @param frame
     * @param xyExtension
     * @param zExtension
     * @return
     */
    public ArrayList<int[]> getMinCostPath3D(int[] startPoint, int[] endPoint, boolean[] analysisCh, int frame,
            int xyExtension, int zExtension) {
        ArrayList<int[]> linkedPoints = new ArrayList<int[]>();
        //IJ.log("start (" + startPoint[1] + ", " + startPoint[2] + ", " + startPoint[3] + ")");
        //IJ.log("end (" + endPoint[1] + ", " + endPoint[2] + ", " + endPoint[3] + ")");
        float[] startIntColor = getWinMeanIntColor(startPoint, analysisCh, frame, 1, 1);
        float[] endIntColor = getWinMeanIntColor(endPoint, analysisCh, frame, 1, 1);
        //float[] startIntColor = getPtIntColor(startPoint, frame);
        //float[] endIntColor = getPtIntColor(endPoint, frame);
        float[] baseIntColor = new float[startIntColor.length];
        for (int i = 0; i < baseIntColor.length; i++) {
            baseIntColor[i] = (startIntColor[i] + endIntColor[i]) / 2;
        }

        // find the minimum cost path between startPoint and endPoint
        // --- 1) base on a cost map with cropped dimensions
        // calculate offset for X, Y, Z,
        int minX = startPoint[1] < endPoint[1] ? startPoint[1] : endPoint[1];
        int minY = startPoint[2] < endPoint[2] ? startPoint[2] : endPoint[2];
        int minZ = startPoint[3] < endPoint[3] ? startPoint[3] : endPoint[3];
        int offsetX = minX <= xyExtension ? 0 : minX - xyExtension;
        int offsetY = minY <= xyExtension ? 0 : minY - xyExtension;
        int offsetZ = minZ <= zExtension + 1 ? 1 : minZ - zExtension - 1;
//IJ.log("received startPoint("+startPoint[1]+", "+startPoint[2]+", "+startPoint[3]+")");
//IJ.log("received endPoint("+endPoint[1]+", "+endPoint[2]+", "+endPoint[3]+")");
//IJ.log("minX="+minX+"; minY="+minY+", minZ="+minZ);
//IJ.log("offsetX="+offsetX+"; offsetY="+offsetY+", offsetZ="+offsetZ);

        int roiW = Math.abs(startPoint[1] - endPoint[1]) + 2 * xyExtension + offsetX;
        roiW = roiW < impWidth - 1 ? roiW - offsetX : impWidth - 1 - offsetX;
        int roiH = Math.abs(startPoint[2] - endPoint[2]) + 2 * xyExtension + offsetY;
        roiH = roiH < impHeight - 1 ? roiH - offsetY : impHeight - 1 - offsetY;
        int maxZ = Math.abs(startPoint[3] - endPoint[3]) + 2 * zExtension + offsetZ;
        maxZ = maxZ < impNSlice ? maxZ : impNSlice;
//IJ.log("roiWidth="+roiW+"; roiHeight="+roiH+", roiDepth="+maxZ);

        imp.setRoi(offsetX, offsetY, roiW, roiH);
        ImagePlus impCrop;
        // mean smoothing of impCrop for smoother tracing
        int kernelR = 1;
        impCrop = meanSmoothImage(kernelR, imp, 1, impNChannel,
                offsetZ, maxZ, frame, frame);
        //impCrop.show();
        imp.killRoi();

        //GridMap map = new GridMap(getCostImage(impCrop, baseIntColor, int2ColDisRatio));
        GridMap map = new GridMap(getCostImage(impCrop, baseIntColor, analysisCh, normSumIntColDis));
        /*
         boolean debug = false;
         if (debug) {// Debug test map
         ImageStack stkDebug = new ImageStack(map.getSizeX(), map.getSizeY(), map.getSizeZ());
         for (int z = 0; z < map.getSizeZ(); z++) {
         ImageProcessor ipDebug = new FloatProcessor(map.getSizeX(), map.getSizeY());
         for (int x = 0; x < map.getSizeX(); x++) {
         for (int y = 0; y < map.getSizeY(); y++) {
         ipDebug.setf(x, y, (float) map.get(x, y, z));
         }
         }
         stkDebug.setPixels(ipDebug.getPixels(), z + 1);
         }
         ImagePlus impDebug = new ImagePlus("Debug", stkDebug);
         impDebug.show();
         }
         */
        GridLocation start = new GridLocation(startPoint[1] - offsetX, startPoint[2] - offsetY, startPoint[3] - offsetZ, false);
        GridLocation end = new GridLocation(endPoint[1] - offsetX, endPoint[2] - offsetY, endPoint[3] - offsetZ, true);
//IJ.log("start in map ("+start.getX()+", "+start.getY()+", "+start.getZ()+")");
//IJ.log("end in map ("+end.getX()+", "+end.getY()+", "+end.getZ()+")");
//IJ.log("start tracing");
        GridHeuristicEuclidean heuristicDiagonal = new GridHeuristicEuclidean();
//IJ.log("got heuristicDiagonal");
        GridAstar pathSolver = new GridAstar(start, end, map, heuristicDiagonal);
//IJ.log("got pathSolver");
        GridPath bestPath = pathSolver.getPath();
//IJ.log("got bestPath");
        if (bestPath == null) {
//IJ.log("bestPath == null");
            return null;
        }
        ArrayList<GridLocation> bestLocationList = bestPath.getList();
        for (int i = bestLocationList.size() - 1; i >= 0; i--) {
            int[] addPoint = {0, bestLocationList.get(i).getX() + offsetX,
                bestLocationList.get(i).getY() + offsetY,
                bestLocationList.get(i).getZ() + offsetZ, 0, 0, 0};
            linkedPoints.add(bestLocationList.size() - i - 1, addPoint);
//IJ.log("get point ("+linkedPoints.get(bestLocationList.size()-i-1)[0]+
//", "+linkedPoints.get(bestLocationList.size()-i-1)[1]+
//", "+linkedPoints.get(bestLocationList.size()-i-1)[2]+")");
        }
        impCrop.flush();
        impCrop.close();
        /*
         if (debug) { // debug
         ImageProcessor ipPath = new FloatProcessor(impWidth, impHeight);
         for (int i = 0; i < linkedPoints.size(); i++) {
         int[] points = linkedPoints.get(i);
         //IJ.log("Point" + (i + 1) + " (" + points[0] + ", " + points[1] + ", " + points[2] + ")");
         ipPath.setf(points[0], points[1], 1000f);
         }
         ImagePlus impPath = new ImagePlus("Max Intensity Path", ipPath);
         impPath.show();
         }
         */
        /*
         // --- 2) base on a cost map with full dimensions
         GridMap map = new GridMap(getCostImage(imp, baseIntColor));
         GridLocation start = new GridLocation(startPoint[1], startPoint[2], startPoint[3]-1, false);
         GridLocation end = new GridLocation(endPoint[1], endPoint[2], endPoint[3]-1, true);
         //IJ.log("start intensity = "+map.get(startPoint[1], startPoint[2], startPoint[3]));
         //IJ.log("end intensity = "+map.get(endPoint[1], endPoint[2], endPoint[3]));
         GridHeuristicEuclidean heuristicDiagonal = new GridHeuristicEuclidean();
         GridAstar pathSolver = new GridAstar(start, end, map, heuristicDiagonal);
         GridPath bestPath = pathSolver.getPath();
         ArrayList<GridLocation> bestLocationList = bestPath.getList();
         linkedPoints = new ArrayList<int[]>();
         for (int i = bestLocationList.size()-1; i >=0 ; i--) {
         int[] addPoint = {bestLocationList.get(i).getX(),
         bestLocationList.get(i).getY(),
         bestLocationList.get(i).getZ()+1, 0};
         linkedPoints.add(bestLocationList.size()-i-1, addPoint);
         }

         * 
         */
        return linkedPoints;
    }

    public ArrayList<int[]> getMinCostPath2D(int[] startPoint, int[] endPoint, boolean[] analysisCh, int frame,
            int xyExtension) {
        int zExtension = 1;
        ArrayList<int[]> linkedPoints = new ArrayList<int[]>();
        //IJ.log("start (" + startPoint[0] + ", " + startPoint[1] + ", " + startPoint[2] + ")");
        //IJ.log("end (" + endPoint[0] + ", " + endPoint[1] + ", " + endPoint[2] + ")");
        float[] startIntColor = getWinMeanIntColor(startPoint, analysisCh, frame, 1, 1);
        float[] endIntColor = getWinMeanIntColor(endPoint, analysisCh, frame, 1, 1);
        //float[] startIntColor = getPtIntColor(startPoint, frame);
        //float[] endIntColor = getPtIntColor(endPoint, frame);
        float[] baseIntColor = new float[startIntColor.length];
        for (int i = 0; i < baseIntColor.length; i++) {
            baseIntColor[i] = (startIntColor[i] + endIntColor[i]) / 2;
        }

        // find the minimum cost path between startPoint and endPoint
        // --- 1) base on a cost map with cropped dimensions
        // calculate offset for X, Y, Z,
        int minX = startPoint[1] < endPoint[1] ? startPoint[1] : endPoint[1];
        int minY = startPoint[2] < endPoint[2] ? startPoint[2] : endPoint[2];
        int minZ = startPoint[3] < endPoint[3] ? startPoint[3] : endPoint[3];
        int offsetX = minX <= xyExtension ? 0 : minX - xyExtension;
        int offsetY = minY <= xyExtension ? 0 : minY - xyExtension;
        int offsetZ = minZ <= zExtension + 1 ? 1 : minZ - zExtension - 1;
//IJ.log("received startPoint("+startPoint[1]+", "+startPoint[2]+", "+startPoint[3]+")");
//IJ.log("received endPoint("+endPoint[1]+", "+endPoint[2]+", "+endPoint[3]+")");
//IJ.log("minX="+minX+"; minY="+minY+", minZ="+minZ);
//IJ.log("offsetX="+offsetX+"; offsetY="+offsetY+", offsetZ="+offsetZ);

        int roiW = Math.abs(startPoint[1] - endPoint[1]) + 2 * xyExtension + offsetX;
        roiW = roiW < impWidth - 1 ? roiW - offsetX : impWidth - 1 - offsetX;
        int roiH = Math.abs(startPoint[2] - endPoint[2]) + 2 * xyExtension + offsetY;
        roiH = roiH < impHeight - 1 ? roiH - offsetY : impHeight - 1 - offsetY;
        int maxZ = Math.abs(startPoint[3] - endPoint[3]) + 2 * zExtension + offsetZ;
        maxZ = maxZ < impNSlice ? maxZ : impNSlice;
//IJ.log("roiWidth="+roiW+"; roiHeight="+roiH+", roiDepth="+maxZ);

        imp.setRoi(offsetX, offsetY, roiW, roiH);
        ImagePlus impCrop;
        // mean smoothing of impCrop for smoother tracing
        int kernelR = 1;
        impCrop = meanSmoothImage(kernelR, imp, 1, impNChannel,
                offsetZ, maxZ, frame, frame);
        //impCrop.show();
        imp.killRoi();

        //GridMap map = new GridMap(getCostImage(impCrop, baseIntColor, int2ColDisRatio));
        GridMap map = new GridMap(getCostImage(impCrop, baseIntColor, analysisCh, normSumIntColDis));
        /*
         boolean debug = true;
         if (debug) {// Debug test map
         ImageStack stkDebug = new ImageStack(map.getSizeX(), map.getSizeY(), map.getSizeZ());
         for (int z = 0; z < map.getSizeZ(); z++) {
         ImageProcessor ipDebug = new FloatProcessor(map.getSizeX(), map.getSizeY());
         for (int x = 0; x < map.getSizeX(); x++) {
         for (int y = 0; y < map.getSizeY(); y++) {
         ipDebug.setf(x, y, (float) map.get(x, y, z));
         }
         }
         stkDebug.setPixels(ipDebug.getPixels(), z + 1);
         }
         ImagePlus impDebug = new ImagePlus("Debug", stkDebug);
         impDebug.show();
         }
         */
        GridLocation start = new GridLocation(startPoint[1] - offsetX, startPoint[2] - offsetY, startPoint[3] - offsetZ, false);
        GridLocation end = new GridLocation(endPoint[1] - offsetX, endPoint[2] - offsetY, endPoint[3] - offsetZ, true);
//IJ.log("start in map ("+start.getX()+", "+start.getY()+", "+start.getZ()+")");
//IJ.log("end in map ("+end.getX()+", "+end.getY()+", "+end.getZ()+")");
//IJ.log("start tracing");
        GridHeuristicEuclidean heuristicDiagonal = new GridHeuristicEuclidean();
//IJ.log("got heuristicDiagonal");
        GridAstar pathSolver = new GridAstar(start, end, map, heuristicDiagonal);
//IJ.log("got pathSolver");
        GridPath bestPath = pathSolver.getPath();
//IJ.log("got bestPath");
        if (bestPath == null) {
//IJ.log("bestPath == null");
            return null;
        }
        ArrayList<GridLocation> bestLocationList = bestPath.getList();

        for (int i = bestLocationList.size() - 1; i >= 0; i--) {
            int[] addPoint = {0, bestLocationList.get(i).getX() + offsetX,
                bestLocationList.get(i).getY() + offsetY,
                startPoint[3], 0, 0, 0};
            linkedPoints.add(bestLocationList.size() - i - 1, addPoint);
//IJ.log("get point ("+linkedPoints.get(bestLocationList.size()-i-1)[0]+
//", "+linkedPoints.get(bestLocationList.size()-i-1)[1]+
//", "+linkedPoints.get(bestLocationList.size()-i-1)[2]+")");
        }

        impCrop.flush();
        impCrop.close();
        /*
         if (debug) { // debug
         ImageProcessor ipPath = new FloatProcessor(impWidth, impHeight);
         for (int i = 0; i < linkedPoints.size(); i++) {
         int[] points = linkedPoints.get(i);
         //IJ.log("Point" + (i + 1) + " (" + points[0] + ", " + points[1] + ", " + points[2] + ")");
         ipPath.setf(points[0], points[1], 1000f);
         }
         ImagePlus impPath = new ImagePlus("Max Intensity Path", ipPath);
         impPath.show();
         }
         */

        return linkedPoints;
    }

    /**
     * Automatically finds the endPoint based on startPoint and tablePoints
     * Retrieve the minimum cost path between startPoint and endPoint. The cost
     * map is a crop of the original image wrapping around the startPoint and
     * endPoint. The cost of each pixel is weighted by intensity and color
     * distance to the average color of startPoint and endPoint
     *
     * @param endPoint
     * @param tablePoints
     * @param analysisCh
     * @param frame
     * @param xyRadius
     * @param zRadius
     * @param colorThreshold
     * @param intensityThreshold
     * @return
     */
    public ArrayList<int[]> semiAutoGetMinCostPath3D(int[] endPoint, ArrayList<String[]> tablePoints, boolean[] analysisCh,
            int frame, int xyRadius, int zRadius, float colorThreshold, float intensityThreshold) {
        ArrayList<int[]> tracedPoints = convertStringArray2IntArray(tablePoints);
        ArrayList<int[]> linkedPoints = new ArrayList<int[]>();

        int[] xyzBoundsOffsets = getBounds(endPoint);
        // xtzBoundsOffsets = {xMin, xMax, yMin, yMax, zMin, zMax}
        int xyDelRadius = xyRadius-1>0?xyRadius-1:1;
        int zDelRadius = zRadius-1>0?zRadius-1:1;
        ImagePlus workImp = getWorkImp(tracedPoints, xyzBoundsOffsets, frame, xyDelRadius, zDelRadius);
        workImp.show();
        IJ.run(workImp, "Mean...", "radius=1");
        
        String[] firstTablePoint = tablePoints.get(0);
        String[] lastTablePoint = tablePoints.get(tablePoints.size()-2);
        int[] nextPoint = {0, endPoint[1]-xyzBoundsOffsets[0], endPoint[2]-xyzBoundsOffsets[2], 
            endPoint[3]-xyzBoundsOffsets[4], 0, 0, 0};
        int[] lastPoint = {0, Integer.parseInt(lastTablePoint[1])-xyzBoundsOffsets[0], 
            Integer.parseInt(lastTablePoint[2])-xyzBoundsOffsets[2], 
            Integer.parseInt(lastTablePoint[3])-xyzBoundsOffsets[4], 0, 0, 0};
        if (Integer.parseInt(firstTablePoint[1])==endPoint[1]
                &&Integer.parseInt(firstTablePoint[2])==endPoint[2]
                &&Integer.parseInt(firstTablePoint[3])==endPoint[3]){
            lastTablePoint = tablePoints.get(1);
            lastPoint[1] = Integer.parseInt(lastTablePoint[1])-xyzBoundsOffsets[0];
            lastPoint[2] = Integer.parseInt(lastTablePoint[2])-xyzBoundsOffsets[2];
            lastPoint[3] = Integer.parseInt(lastTablePoint[3])-xyzBoundsOffsets[4];
        }
IJ.log("endPoint ("+endPoint[1]+", "+endPoint[2]+", "+endPoint[3]+")");        
        float[] baseIntColor = getTracingPtsMeanIntColor(tracedPoints, analysisCh, frame, xyRadius, zRadius);
        int maxItreation = 5;
        int xPredict = 0, yPredict = 0, zPredict = 0;
        for (int n = 0; n < 30; n++) {
            if (nextPoint[1]>lastPoint[1]){
                xPredict = 1;
            } else if (nextPoint[1]==lastPoint[1]){
                xPredict = 0;
            } else{
                xPredict = -1;
            }
            if (nextPoint[2]>lastPoint[2]){
                yPredict = 1;
            } else if (nextPoint[2]==lastPoint[2]){
                yPredict = 0;
            } else{
                yPredict = -1;
            }
            if (nextPoint[3]>lastPoint[3]){
                zPredict = 1;
            } else if (nextPoint[3]==lastPoint[3]){
                zPredict = 0;
            } else{
                zPredict = -1;
            }
            int[] predictPoint = {0, nextPoint[1]+xPredict, nextPoint[2]+yPredict, nextPoint[3], 0, 0, 0};
IJ.log("lastPoint ("+(lastPoint[1]+xyzBoundsOffsets[0])+", "+(lastPoint[2]+xyzBoundsOffsets[2])+", "+(lastPoint[3]+xyzBoundsOffsets[4])+")");
IJ.log("predictPoint ("+(predictPoint[1]+xyzBoundsOffsets[0])+", "+(predictPoint[2]+xyzBoundsOffsets[2])+", "+(predictPoint[3]+xyzBoundsOffsets[4])+")");
            lastPoint[1] = nextPoint[1];
            lastPoint[2] = nextPoint[2];
            lastPoint[3] = nextPoint[3];
            nextPoint = meanShift2ColoThreshCentOfInt(workImp, predictPoint, analysisCh,
                    imp.getFrame(), baseIntColor, colorThreshold, xyRadius, zRadius, 1, maxItreation);
            deletePointFromWorkImage(workImp, nextPoint, frame, xyDelRadius, zDelRadius);
IJ.log("nextPoint ("+(nextPoint[1]+xyzBoundsOffsets[0])+", "+(nextPoint[2]+xyzBoundsOffsets[2])+", "+(nextPoint[3]+xyzBoundsOffsets[4])+")");
        }
        workImp.updateAndDraw();
        return linkedPoints;
    }
    
    public Color getColorFromPoints(ArrayList<int[]> neuronPoints, float alpha){
        if (neuronPoints.isEmpty()) {
            return Color.white;
        } else {
            float[] tempColor = new float[impNChannel];
            for (int channel = 0; channel < impNChannel; channel++) {
                tempColor[channel] = 0;
            }
            for (int[] neuronPt : neuronPoints) {
                for (int channel = 0; channel < impNChannel; channel++) {
                    if (analysisChannels[channel]) {
                        int index = imp.getStackIndex(channel + 1, neuronPt[3], imp.getFrame());
                        // retrive color[channel] and calculate total intensity
                        tempColor[channel] = tempColor[channel] + stk.getProcessor(index).get(neuronPt[1], neuronPt[2]);
                    }
                }
            }

            float[][] allChRGBratios = getAllChRGBratios();
            float[] allChActiveFloat = getAllChActiveFloat();
            float[] allChAnalysisFloat = getAllChAnalysisFloat();
            float[] rgbColor = {0, 0, 0};

            // calculate normalized color - normalize to the max intensity channel
            float max = 0;
            for (int color = 0; color < 3; color++) {
                for (int channel = 0; channel < impNChannel; channel++) {
                    rgbColor[color] = rgbColor[color] + (tempColor[channel] * allChActiveFloat[channel] * allChAnalysisFloat[channel] * allChRGBratios[channel][color]);
                }
                //IJ.log("color "+color+" = "+rgbColor[color]);
                max = rgbColor[color] > max ? rgbColor[color] : max;
            }
            //IJ.log("max = "+max);
        
            return new Color(rgbColor[0] / max, rgbColor[1] / max, rgbColor[2] / max, alpha);
        }
    }
        
    public float[] getAllChActiveFloat() {
        float[] allChActiveIndex = new float[impNChannel];
        for (int ch = 0; ch < impNChannel; ch++) {
            if (activeChannels[ch]) {
                allChActiveIndex[ch] = 1f;
            } else {
                allChActiveIndex[ch] = 0f;
            }
            //IJ.log("Ch "+ch+" active = "+allChActiveIndex[ch]);
        }
        return allChActiveIndex;
    }    
        
    public float[] getAllChAnalysisFloat() {
        float[] allChAnalysisIndex = new float[impNChannel];
        for (int ch = 0; ch < impNChannel; ch++) {
            if (analysisChannels[ch]) {
                allChAnalysisIndex[ch] = 1f;
            } else {
                allChAnalysisIndex[ch] = 0f;
            }
            //IJ.log("Ch "+ch+" analysis = "+allChAnalysisIndex[ch]);
        }
        return allChAnalysisIndex;
    }

    public float[][] getAllChRGBratios() {
        float[][] allChRGBratios = new float[impNChannel][3];
        LUT[] luts = imp.getLuts();
        //IJ.log("total lut = "+luts.length);
        for (int ch = 0; ch < impNChannel; ch++) {
            allChRGBratios[ch] = getChRGBratios(luts[ch], ch + 1);
        }
        return allChRGBratios;
    }

    public float[] getChRGBratios(LUT lut, int channel) {
        int mapSize = lut.getMapSize();
        byte[] reds = new byte[mapSize];
        lut.getReds(reds);
        byte[] greens = new byte[mapSize];
        lut.getGreens(greens);
        byte[] blues = new byte[mapSize];
        lut.getBlues(blues);
        int n = 100;
        float sum = reds[n] + greens[n] + blues[n];
        float redRatio = reds[n] / sum;
        float greenRatio = greens[n] / sum;
        float blueRatio = blues[n] / sum;
        float[] RGBratios = {redRatio, greenRatio, blueRatio};
        //IJ.log("Channel "+channel+": red = " + RGBratios[0]);
        //IJ.log("Channel "+channel+": green = " + RGBratios[1]);
        //IJ.log("Channel "+channel+": blue = " + RGBratios[2]);
        return RGBratios;
    }
    
    private int[] getBounds(int[] startPoint) {
        int xyRadias = 60, zRadius = 20;
        int xMin = startPoint[1] - xyRadias < 0 ? 0 : startPoint[1] - xyRadias;
        int xMax = startPoint[1] + xyRadias >= impWidth ? impWidth - 1 : startPoint[1] + xyRadias;
        int yMin = startPoint[2] - xyRadias < 0 ? 0 : startPoint[2] - xyRadias;
        int yMax = startPoint[2] + xyRadias >= impHeight ? impHeight - 1 : startPoint[2] + xyRadias;
        int zMin = startPoint[3] - zRadius < 1 ? 1 : startPoint[3] - zRadius;
        int zMax = startPoint[3] + zRadius > impNSlice ? impNSlice : startPoint[3] + zRadius;
        int [] xyzBounds = {xMin, xMax, yMin, yMax, zMin, zMax};
        return xyzBounds;
    }

    private ImagePlus getWorkImp(ArrayList<int[]> tracedPoints, int[] xyzBounds,
            int impFrame, int xyDelRadius, int zDelRadius) {
        //xyzBounds = {xMin, xMax, yMin, yMax, zMin, zMax}
        imp.setRoi(xyzBounds[0], xyzBounds[2], xyzBounds[1] - xyzBounds[0] + 1, xyzBounds[3] - xyzBounds[2] + 1);
        ImagePlus workImage = impDuplicator.run(imp, 1, impNChannel, xyzBounds[4], xyzBounds[5], impFrame, impFrame);
        imp.killRoi();
        ImageStack workStack = workImage.getImageStack();

        int workImageWidth = workImage.getWidth();
        int workImageHeight = workImage.getHeight();
        int workImageNSlice = workImage.getNSlices();
        //IJ.log(xyzBounds[0]+", "+xyzBounds[2]+", "+xyzBounds[4]);
        for (int[] tracedPoint : tracedPoints) {
            if (tracedPoint[1] >= xyzBounds[0] && tracedPoint[1] <= xyzBounds[1]
                    && tracedPoint[2] >= xyzBounds[2] && tracedPoint[2] <= xyzBounds[3]
                    && tracedPoint[3] >= xyzBounds[4] && tracedPoint[3] <= xyzBounds[5]) {
                for (int z = tracedPoint[3]-xyzBounds[4] - zDelRadius; z <= tracedPoint[3]-xyzBounds[4] + zDelRadius; z++) {
                    if (z >= 1 && z <= workImageNSlice) {
                        for (int c = 1; c <= impNChannel; c++) {
                            ImageProcessor ip = workStack.getProcessor(workImage.getStackIndex(c, z, impFrame));
                            for (int x = tracedPoint[1]-xyzBounds[0] - xyDelRadius; x <= tracedPoint[1]-xyzBounds[0] + xyDelRadius; x++) {
                                for (int y = tracedPoint[2]-xyzBounds[2] - xyDelRadius; y <= tracedPoint[2]-xyzBounds[2] + xyDelRadius; y++) {
                                    //IJ.log(x+", "+y+", "+z);
                                    if (x>=0 && x< workImageWidth && y>=0 && y<workImageHeight){
                                        ip.set(x, y, 0);                                        
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        workImage.setStack(workStack);
        return workImage;
    }
    private void deletePointFromWorkImage(ImagePlus workImage, int[] tracedPoint, int impFrame, int xyDelRadius, int zDelRadius){
        ImageStack workStack = workImage.getImageStack();
        int workImageWidth = workImage.getWidth();
        int workImageHeight = workImage.getHeight();
        int workImageNSlice = workImage.getNSlices();

        for (int z = tracedPoint[3] - zDelRadius; z <= tracedPoint[3] + zDelRadius; z++) {
            if (z >= 1 && z <= workImageNSlice) {
                for (int c = 1; c <= impNChannel; c++) {
                    ImageProcessor ip = workStack.getProcessor(workImage.getStackIndex(c, z, impFrame));
                    for (int x = tracedPoint[1] - xyDelRadius; x <= tracedPoint[1] + xyDelRadius; x++) {
                        for (int y = tracedPoint[2] - xyDelRadius; y <= tracedPoint[2] + xyDelRadius; y++) {
                            //IJ.log(x+", "+y+", "+z);
                            if (x >= 0 && x < workImageWidth && y >= 0 && y < workImageHeight) {
                                ip.set(x, y, 0);
                            }
                        }
                    }
                }
            }
        }
        workImage.setStack(workStack);
    }

    /**
     * Use cubic smoothing Spline to refine/smooth traced path points:
     * http://en.wikipedia.org/wiki/Smoothing_spline
     *
     * used SSJ library: http://www.iro.umontreal.ca/~simardr/ssj/indexe.html
     * http://www.iro.umontreal.ca/~simardr/ssj/doc/html/index.html
     *
     * @param pathPoints
     * @param rho smoothing factor between [0.0, 1.0] -- rho = 1 ->
     * interpolating spline; rho = 0 -> linear function
     * @return smoothed points as ArrayList<int[]>
     */
    private ArrayList<int[]> cubicSmoothingSpline(ArrayList<int[]> pathPoints, double rho) {
        ArrayList<int[]> copyPoints = (ArrayList<int[]>)pathPoints.clone();
        if (pathPoints.size()>1) {
            ArrayList<int[]> refinePoints = new ArrayList<int[]>();
            double[] n = new double[pathPoints.size()];
            double[] x = new double[pathPoints.size()];
            double[] y = new double[pathPoints.size()];
            double[] z = new double[pathPoints.size()];

            for (int i = 0; i < pathPoints.size(); i++) {
                n[i] = i;
                x[i] = pathPoints.get(i)[1];
                y[i] = pathPoints.get(i)[2];
                z[i] = pathPoints.get(i)[3];
            }
            SmoothingCubicSpline scsX = new SmoothingCubicSpline(n, x, rho);
            SmoothingCubicSpline scsY = new SmoothingCubicSpline(n, y, rho);
            SmoothingCubicSpline scsZ = new SmoothingCubicSpline(n, z, rho);

            refinePoints.add(pathPoints.get(0));
            for (double i = 0; i < pathPoints.size() - 1; i++) {
                int[] smoothPonit = new int[7];
                smoothPonit[0] = 0;
                smoothPonit[1] = (int) (scsX.evaluate(i));
                smoothPonit[2] = (int) (scsY.evaluate(i));
                smoothPonit[3] = (int) (scsZ.evaluate(i));
                if (smoothPonit[3] == 0) {
                    smoothPonit[3] = 1;
                }
                smoothPonit[4] = 0;
                smoothPonit[5] = 0;
                smoothPonit[6] = 0;
                refinePoints.add(smoothPonit);
            }
            refinePoints.add(pathPoints.get(pathPoints.size() - 1));
            if (refinePoints.size() > 1) {
                return refinePoints;
            } else {
                return copyPoints;
            }
        } else {
            return pathPoints;
        }
    }

    public ArrayList<int[]> getKickBallPath(int[] startPoint, boolean[] analysisCh, int frame,
            float colDisThresh, int xyRadius, int zRadius, int maskRadius) {
        boolean continueKicking = true;
        ArrayList<int[]> linkedPoints = new ArrayList<int[]>();
        linkedPoints.add(startPoint);
        IJ.log("start (" + startPoint[1] + ", " + startPoint[2] + ", " + startPoint[3] + ")");
        // "kick ball" from startPoint
        float[] baseIntColor = getWinMeanIntColor(startPoint, analysisCh, frame, 1, 1);
        float[] currentIntColor = getWinMeanIntColor(startPoint, analysisCh, frame, xyRadius, zRadius);
        IJ.log("color (" + baseIntColor[1] + ", " + baseIntColor[2] + ", " + baseIntColor[3] + ")");

        int[] lastPt = {0, startPoint[1], startPoint[2] - 1, startPoint[3], 0, 0, 0};
        int[] currentPt = {0, startPoint[1], startPoint[2], startPoint[3], 0, 0, 0};
        int maxIteration = 3;
        do {
            int[] newPoint = meanShift2ColoThreshCentOfInt(imp, currentPt, lastPt, analysisCh,
                    frame, currentIntColor, colDisThresh, xyRadius, zRadius, maskRadius, maxIteration);
            if (newPoint[1] != currentPt[1] || newPoint[2] != currentPt[2] || newPoint[3] != currentPt[3]) {
                linkedPoints.add(newPoint);
                IJ.log("point " + " (" + newPoint[1] + ", " + newPoint[2] + ", " + newPoint[3] + ")");
                lastPt[1] = currentPt[1];
                lastPt[2] = currentPt[2];
                lastPt[3] = currentPt[3];
                currentPt[1] = newPoint[1];
                currentPt[2] = newPoint[2];
                currentPt[3] = newPoint[3];
                // update color information based on previous points
                currentIntColor = updateCurrentBaseIntColor(baseIntColor, analysisCh, currentPt, lastPt, frame);
            } else {
                IJ.log("quit");
                continueKicking = false;
            }
        } while (continueKicking);
        /*
         boolean debug = false;
         if (debug) { // debug
         for (int z = startPoint[3] - zRadius; z <= startPoint[3] + zRadius; z++) {
         if (z >= 1 && z <= impNSlice) {
         for (int x = startPoint[1] - xyRadius; x <= startPoint[1] + xyRadius; x++) {
         if (x >= 0 && x < impWidth) {
         for (int y = startPoint[2] - xyRadius; y <= startPoint[2] + xyRadius; y++) {
         if (y >= 0 && y < impHeight) {
         int[] currentPoint = {0, x, y, z, 0, 0, 0};
         currentIntColor = getPtIntColor(currentPoint, frame);
         IJ.log("Cor = [" + currentIntColor[1] + ", " + currentIntColor[2] + ", " + currentIntColor[3] + "]");
         float corDis2 = getColorDistanceSquare(startPoint, currentPoint, frame);
         IJ.log("CorDis2 @ (" + x + ", " + y + ", " + z + ") = " + corDis2);
         }
         }
         }
         }
         }
         }
         }
         */
        return linkedPoints;
    }

    public ArrayList<int[]> getOutLinkPath(int[] startPoint, boolean[] analysisCh, int frame,
            float colDisThresh, float intThresh, int xyRadius, int zRadius, int outLinkRadius) {
        float xyzRatio = (float) xyRadius / (float) zRadius;
        ArrayList<int[]> linkedPoints = new ArrayList<int[]>();
        //linkedPoints.add(startPoint);
        //IJ.log("start (" + startPoint[0] + ", " + startPoint[1] + ", " + startPoint[2] + ")");
        // search for next point from startPoint
        float[] baseIntColor = getWinMeanIntColor(startPoint, analysisCh, frame, xyRadius, zRadius);
        //IJ.log("intensity = "+baseIntColor[0]+"; color (" + baseIntColor[1] + ", " + baseIntColor[2] + ", " + baseIntColor[3] + ")");
        ArrayList<int[]> outLinkPoints = getOutLinkPoints(baseIntColor, startPoint, analysisCh, frame,
                colDisThresh, intThresh, xyRadius, zRadius, outLinkRadius);

        if (!outLinkPoints.isEmpty()) {
            for (int[] tempPt : outLinkPoints) {
                linkedPoints.add(tempPt);
            }
        }

        return linkedPoints;
    }

    private ArrayList<int[]> getOutLinkPoints(float[] baseIntColor, int[] currentPoint, boolean[] analysisCh, int frame,
            float colDisThresh, float intThresh, int xyRadius, int zRadius, int outLinkRadius) {
        float xyzRatio = (float) xyRadius / (float) zRadius;
        int zSearchMax = (int) (outLinkRadius / zRadius);
        float colDisThresh2 = colDisThresh * colDisThresh;
        ArrayList<int[]> outLinkPoint = new ArrayList<int[]>();
        for (int z = currentPoint[3] - zSearchMax; z <= currentPoint[3] + zSearchMax; z++) {
            for (int y = currentPoint[2] - outLinkRadius; y <= currentPoint[2] + outLinkRadius; y++) {
                for (int x = currentPoint[1] - outLinkRadius; x <= currentPoint[1] + outLinkRadius; x++) {
                    if (x >= 0 && x < impWidth && y >= 0 && y < impHeight && z >= 0 && z < impNSlice) {
                        int[] searchPt = {0, x, y, z, 0, 0, 0};
                        int distance = (int) (Math.sqrt((x - currentPoint[1]) * (x - currentPoint[1])
                                + (y - currentPoint[2]) * (y - currentPoint[2])
                                + (z - currentPoint[3]) * (z - currentPoint[3]) * xyzRatio * xyzRatio));
                        //IJ.log("(" + searchPt[0] + ", " + searchPt[1] + ", " + searchPt[2] + ") to currentPoint = " + distance);
                        if (distance == outLinkRadius) {
                            float[] tempIntColor = getWinMeanIntColor(searchPt, analysisCh, frame, xyRadius, zRadius);
                            float corDis2 = getColorDistanceSquare(baseIntColor, tempIntColor);
                            //IJ.log("intensity = "+tempIntColor[0]+"; color (" + tempIntColor[1] + ", " + tempIntColor[2] + ", " + tempIntColor[3] + ")");
                            if (corDis2 <= colDisThresh2
                                    && tempIntColor[0] >= (1 - intThresh) * baseIntColor[0]
                                    && tempIntColor[0] <= (1 + intThresh) * baseIntColor[0]) {
                                //IJ.log("added (" + searchPt[0] + ", " + searchPt[1] + ", " + searchPt[2] + ") to currentPoint = " + distance);
                                //IJ.log("color (" + tempIntColor[1] + ", " + tempIntColor[2] + ", " + tempIntColor[3] + ")");
                                outLinkPoint.add(searchPt);
                            }
                        }
                    }
                }
            }
        }

        return outLinkPoint;
    }

    public ArrayList<ArrayList<int[]>> clusterOutLinkPathPoints(ArrayList<int[]> outLinkPathPoints) {
        int minDistance = 2;
        int minPoints = 3;
        ArrayList<ArrayList<int[]>> clustered = new ArrayList<ArrayList<int[]>>();

        while (!outLinkPathPoints.isEmpty()) {
            ArrayList<int[]> nextCluster = getNextCluster(minDistance, outLinkPathPoints);
            clustered.add(nextCluster);
        }
        //IJ.log("total of " + clustered.size() + " clustered have points within "+minDistance+" pixels");

        for (int i = clustered.size() - 1; i >= 0; i--) {
            if (clustered.get(i).size() < minPoints) {
                clustered.remove(i);
            }
        }
        //IJ.log("total of " + clustered.size() + " clusters have more than "+minPoints+" points");
        return clustered;
    }

    private ArrayList<int[]> getNextCluster(int minDistance, ArrayList<int[]> points) {
        int counter = points.size();
        ArrayList<int[]> cluster = new ArrayList<int[]>();
        counter--;
        int[] seedPoint = points.get(counter);
        //IJ.log("Seed --------------- (" + seedPoint[0] + ", " + seedPoint[1] + ", " + seedPoint[2] + ")");
        cluster.add(seedPoint);
        points.remove(counter);
        while (counter > 0) {
            //IJ.log("move to "+counter);
            counter--;
            int[] comparePoint = points.get(counter).clone();
            //IJ.log("compare -- (" + comparePoint[0] + ", " + comparePoint[1] + ", " + comparePoint[2] + ")");
            for (int i = 0; i < cluster.size(); i++) {
                int[] clusterPoint = cluster.get(i);
                if (Math.abs(comparePoint[0] - clusterPoint[0]) <= minDistance
                        && Math.abs(comparePoint[1] - clusterPoint[1]) <= minDistance
                        && Math.abs(comparePoint[2] - clusterPoint[2]) <= 1) {
                    //IJ.log("added (" + comparePoint[0] + ", " + comparePoint[1] + ", " + comparePoint[2] + ")");
                    cluster.add(comparePoint);
                    points.remove(counter);
                    break;
                }
            }
        }

        return cluster;
    }

    /**
     * Get intensity and normalized color at a point.
     *
     * @param point X, Y, Z coordinates in center[0], center[1], center[2]
     * @param analysisCh
     * @param frame current composite image frame
     * @return intensity (ptIntColor[0]) and the normalized color
     * (ptIntColor[i])
     */
    public float[] getPtIntColor(int[] point, boolean[] analysisCh, int frame) {
        float ptIntColor[] = new float[impNChannel + 1];
        for (int channel = 1; channel <= impNChannel; channel++) {
            if (analysisCh[channel - 1]) {
                int index = imp.getStackIndex(channel, point[3], frame);
                ImageProcessor ipChannel = stk.getProcessor(index);
                // retrive color[channel] and calculate total intensity
                ptIntColor[channel] = ipChannel.get(point[1], point[2]);
                //sum intensity
                ptIntColor[0] += ptIntColor[channel];
                //IJ.log("Chennel "+channel+": color = "+color[channel-1]+"; sum = "+sum);
            }
        }
        // calculate normalized color
        for (int channel = 1; channel <= impNChannel; channel++) {
            if (analysisCh[channel - 1]) {
                ptIntColor[channel] = ptIntColor[channel] / ptIntColor[0];
            } else {
                ptIntColor[channel] = 0;
            }
        }

        return ptIntColor;
    }

    /**
     * Get mean intensity and normalized color in a search window of
     * (xyRadius*2+1, xyRadius*2+1, zRadius*2+1)
     *
     * @param centerPt X, Y, Z coordinates in center[0], center[1], center[2]
     * @param analysisCh
     * @param frame current composite image frame
     * @param xyRadius X-Y radius of the average window
     * @param zRadius Z radius of the average window
     * @return mean intensity (meanIntColor[0]) and the normalized mean color
     * (meanIntColor[i])
     */
    public float[] getWinMeanIntColor(int[] centerPt, boolean[] analysisCh, int frame,
            int xyRadius, int zRadius) {
        int count = 0;
        float[] meanIntColor = new float[impNChannel + 1];
        int ipIndex;
        ImageProcessor[] ipChannel = new ImageProcessor[impNChannel];
        for (int z = centerPt[3] - zRadius; z <= centerPt[3] + zRadius; z++) {
            if (z >= 1 && z <= impNSlice) {
                for (int channel = 0; channel < impNChannel; channel++) {
                    ipIndex = imp.getStackIndex(channel + 1, z, frame);
                    ipChannel[channel] = stk.getProcessor(ipIndex);
                }
                for (int x = centerPt[1] - xyRadius; x <= centerPt[1] + xyRadius; x++) {
                    if (x >= 0 && x < impWidth) {
                        for (int y = centerPt[2] - xyRadius; y <= centerPt[2] + xyRadius; y++) {
                            if (y >= 0 && y < impHeight) {
                                for (int channel = 0; channel < impNChannel; channel++) {
                                    if (analysisCh[channel]) {
                                        meanIntColor[0] += ipChannel[channel].get(x, y);
                                        meanIntColor[channel + 1] += ipChannel[channel].get(x, y);
                                        //IJ.log("Position ("+x+", "+y+", "+z+")");
                                        //IJ.log("accumulateIntColor ["+meanIntColor[0]+": "+meanIntColor[1]+", "+meanIntColor[2]+", "+meanIntColor[3]+"]");
                                    }
                                }
                                count++;
                                //IJ.log("Count "+count);
                            }
                        }
                    }
                }
            }
        }
        for (int c = 1; c <= impNChannel; c++) {
            if (analysisCh[c - 1]) {
                meanIntColor[c] = meanIntColor[c] / meanIntColor[0];
            } else {
                meanIntColor[c] = 0;
            }
        }
        meanIntColor[0] = meanIntColor[0] / count;
        //IJ.log("meanIntColor ["+meanIntColor[0]+": "+meanIntColor[1]+", "+meanIntColor[2]+", "+meanIntColor[3]+"]");

        return meanIntColor;
    }
    
    public float[] getTracingPtsMeanIntColor(ArrayList<int[]> tractingPts, boolean[] analysisCh, int frame,
            int xyRadius, int zRadius){
        if (tractingPts!= null){
            float[] allPtIntColor = new float[analysisCh.length+1];
            for (int i=0; i< allPtIntColor.length; i++){
                allPtIntColor[i] = 0;
            }
            for (int[] point : tractingPts){
                float[] ptIntColor = getWinMeanIntColor(point, analysisCh, frame, xyRadius, zRadius);
                for (int i =0; i< ptIntColor.length; i++){
                    allPtIntColor[i] = ptIntColor[i]+allPtIntColor[i];
                }
            }
            float totalColor = 0;
            for (int i = 1; i<allPtIntColor.length; i++){
                totalColor = totalColor+allPtIntColor[i];
            }
            allPtIntColor[0] = allPtIntColor[0]/allPtIntColor.length;
            for (int i = 1; i<allPtIntColor.length; i++){
                allPtIntColor[i] = allPtIntColor[i]/totalColor;
            }
            return allPtIntColor;
        }else{
            return null;
        }
    }
    /**
     * Calculate the weighted average intensity and color of two inputs.
     *
     * @param intColorA intensity and color of point A
     * @param intColorB intensity and color of point B
     * @param weightA the weight of point A in weighted average calculation
     * @return weighted average intensity and color
     */
    public float[] getWeightedAVeIntColor(float[] intColorA, float[] intColorB, float weightA) {
        float[] weightedIntColor = new float[intColorA.length];
        float weightB = 1 - weightA;
        for (int i = 0; i < intColorA.length; i++) {
            weightedIntColor[i] = intColorA[i] * weightA + intColorB[i] * weightB;
        }
        return weightedIntColor;
    }

    public float[] updateCurrentBaseIntColor(float[] baseIntColor, boolean[] analysisCh, int[] lastPt, int frame) {
        float[] lastPtIntColor = getWinMeanIntColor(lastPt, analysisCh, frame, 1, 1);
        return getWeightedAVeIntColor(baseIntColor, lastPtIntColor, 0.6f);
    }

    public float[] updateCurrentBaseIntColor(float[] baseIntColor, boolean[] analysisCh, int[] maskPt1, int[] maskPt2, int frame) {
        float[] maskPt1IntColor = getWinMeanIntColor(maskPt1, analysisCh, frame, 1, 1);
        float[] maskPt2IntColor = getWinMeanIntColor(maskPt2, analysisCh, frame, 1, 1);
        float[] maskPtWeightedAveIntColor = getWeightedAVeIntColor(maskPt1IntColor, maskPt2IntColor, 0.6f);

        return getWeightedAVeIntColor(baseIntColor, maskPtWeightedAveIntColor, 0.5f);
    }

    public int getPointDistanceSquare(int[] pointA, int[] pointB) {
        return (pointA[0] - pointB[0]) * (pointA[0] - pointB[0]) + (pointA[1] - pointB[1]) * (pointA[1] - pointB[1]) + (pointA[2] - pointB[2]) * (pointA[2] - pointB[2]);
    }

    /**
     * Calculates the square of the normalized color distance between two
     * points.
     *
     * @param pointA pointA pixel coordinates
     * @param pointB pointB pixel coordinates
     * @param analysisCh
     * @param frame frame index of the image stack
     * @return the square of the normalized color distance between pointA and
     * pointB
     */
    public float getColorDistanceSquare(int[] pointA, int[] pointB, boolean[] analysisCh, int frame) {
        float colorDistanceSquare = 0;

        float[] colorA = getPtIntColor(pointA, analysisCh, frame);
        float[] colorB = getPtIntColor(pointB, analysisCh, frame);

        for (int i = 1; i < colorA.length; i++) {
            colorDistanceSquare += (colorA[i] - colorB[i]) * (colorA[i] - colorB[i]);
        }

        return colorDistanceSquare;
    }

    /**
     * Calculates the square of the normalized color distance between two
     * colors.
     *
     * @param colorA
     * @param colorB
     * @return the square of the normalized color distance between colorA and
     * colorB
     */
    public float getColorDistanceSquare(float[] colorA, float[] colorB) {
        float colorDistanceSquare = 0;

        for (int i = 1; i < colorA.length; i++) {
            colorDistanceSquare += (colorA[i] - colorB[i]) * (colorA[i] - colorB[i]);
        }

        return colorDistanceSquare;
    }

    public String[] getCentOfInt(ArrayList<int[]> points, boolean[] analysisCh, int frame) {
        float sumInt = 0, sumIntX = 0, sumIntY = 0, sumIntZ = 0;
        for (int[] point : points) {
            float[] pixelInt = getWinMeanIntColor(point, analysisCh, frame, 1, 1);
            sumInt = sumInt + pixelInt[0];
            sumIntX = sumIntX + pixelInt[0] * point[1];
            sumIntY = sumIntY + pixelInt[0] * point[2];
            sumIntZ = sumIntZ + pixelInt[0] * point[3];
        }
        String[] centOfIntPt = {"0", (int) (sumIntX / sumInt) + "", (int) (sumIntY / sumInt) + "", (int) (sumIntZ / sumInt) + "", "0", "0", "0"};
        return centOfIntPt;
    }

    /**
     *
     * @param centerX
     * @param centerY
     * @param centerZ
     * @param frame
     * @param xyRadius
     * @param zRadius
     * @return
     */
    private int[] getCentOfInt(int centerX, int centerY, int centerZ, int frame,
            int xyRadius, int zRadius) {
        int[] tempPoint = {0, 0, 0, 0, 0, 0, 0};
        double sumInt = 0, sumIntX = 0, sumIntY = 0, sumIntZ = 0;
        ImageProcessor[] ip = new ImageProcessor[impNChannel];
        int ipIndex;
        for (int z = centerZ - zRadius; z <= centerZ + zRadius; z++) {
            if (z >= 1 && z <= impNSlice) {
                ImageProcessor ipSum = new FloatProcessor(impWidth, impHeight);
                for (int channel = 1; channel <= impNChannel; channel++) {
                    ipIndex = imp.getStackIndex(channel, z, frame);
                    ip[channel - 1] = stk.getProcessor(ipIndex);
                    for (int x = centerX - xyRadius; x <= centerX + xyRadius; x++) {
                        if (x >= 0 && x < impWidth) {
                            for (int y = centerY - xyRadius; y <= centerY + xyRadius; y++) {
                                if (y >= 0 && y < impHeight) {
                                    ipSum.setf(x, y, ipSum.getf(x, y) + ip[channel - 1].getf(x, y));
                                }
                            }
                        }
                    }
                }
                for (int x = centerX - xyRadius; x <= centerX + xyRadius; x++) {
                    if (x >= 0 && x < impWidth) {
                        for (int y = centerY - xyRadius; y <= centerY + xyRadius; y++) {
                            if (y >= 0 && y < impHeight) {
                                double pixelInt = 0;
                                //for (int c = 0; c < impNChannel; c++) {
                                pixelInt = pixelInt + ipSum.getf(x, y);
                                //}
                                sumInt = sumInt + pixelInt;
                                sumIntX = sumIntX + pixelInt * x;
                                sumIntY = sumIntY + pixelInt * y;
                                sumIntZ = sumIntZ + pixelInt * z;
                                //IJ.log("Slice ("+z+") * "+pixelInt+" = "+pixelInt * z);
                                //IJ.log("sumIntPosition ["+sumIntZ+"]");
                                //IJ.log("Position ("+x+", "+y+", "+z+")");
                                //IJ.log("sumIntPosition ["+sumIntX+", "+sumIntX+", "+sumIntX+"]");
                                //IJ.log("sumIntensity = "+sumInt);
                            }
                        }
                    }
                }
            }

            //IJ.log("sumIntensity = "+sumInt);
        }
        tempPoint[1] = (int) Math.round(sumIntX / sumInt);
        tempPoint[2] = (int) Math.round(sumIntY / sumInt);
        tempPoint[3] = (int) Math.round(sumIntZ / sumInt);
        //IJ.log("tempPoint [" + tempPoint[1] + ", " + tempPoint[2] + ", " + tempPoint[3] + "]");
        //IJ.log("float result [" + sumIntX / sumInt + ", " + sumIntY / sumInt + ", " + sumIntZ / sumInt + "]");
        return tempPoint;
    }

    private int[] getColoThreshCentOfInt(ImagePlus image, int[] centerPt, boolean[] analysisCh, int frame,
            float[] baseIntColor, float colDisThresh,
            int xyRadius, int zRadius, int maskRadius) {
        float colDisThresh2 = colDisThresh * colDisThresh;
        int[] tempPoint = {0, 0, 0, 0, 0, 0, 0};
        double sumInt = 0, sumIntX = 0, sumIntY = 0, sumIntZ = 0;
        ImageProcessor[] ip = new ImageProcessor[impNChannel];
        int ipIndex;
        for (int z = centerPt[3] - zRadius; z <= centerPt[3] + zRadius; z++) {
            if (z >= 1 && z <= impNSlice) {
                ImageProcessor ipSum = new FloatProcessor(impWidth, impHeight);
                for (int channel = 1; channel <= impNChannel; channel++) {
                    ipIndex = image.getStackIndex(channel, z, frame);
                    ip[channel - 1] = stk.getProcessor(ipIndex);
                    for (int x = centerPt[1] - xyRadius; x <= centerPt[1] + xyRadius; x++) {
                        if (x >= 0 && x < impWidth) {
                            for (int y = centerPt[2] - xyRadius; y <= centerPt[2] + xyRadius; y++) {
                                if (y >= 0 && y < impHeight) {
                                    ipSum.setf(x, y, ipSum.getf(x, y) + ip[channel - 1].getf(x, y));
                                }
                            }
                        }
                    }
                }
                for (int x = centerPt[1] - xyRadius; x <= centerPt[1] + xyRadius; x++) {
                    if (x >= 0 && x < impWidth) {
                        for (int y = centerPt[2] - xyRadius; y <= centerPt[2] + xyRadius; y++) {
                            if (y >= 0 && y < impHeight) {
                                // determine whether (x, y, z) is masked out
                                int[] currentPt = {0, x, y, z, 0, 0, 0};
                                // determin whether cuttent point color is within color distance threshold
                                float[] currentIntColor = getPtIntColor(currentPt, analysisCh, frame);
                                float corDis2 = getColorDistanceSquare(baseIntColor, currentIntColor);
                                if (corDis2 <= colDisThresh2) {
                                    double pixelInt = 0;
                                    pixelInt = pixelInt + ipSum.getf(x, y);
                                    sumInt = sumInt + pixelInt;
                                    sumIntX = sumIntX + pixelInt * x;
                                    sumIntY = sumIntY + pixelInt * y;
                                    sumIntZ = sumIntZ + pixelInt * z;
                                    //IJ.log("Slice ("+z+") * "+pixelInt+" = "+pixelInt * z);
                                    //IJ.log("sumIntPosition ["+sumIntZ+"]");
                                    //IJ.log("Position ("+x+", "+y+", "+z+")");
                                    //IJ.log("sumIntPosition ["+sumIntX+", "+sumIntX+", "+sumIntX+"]");
                                    //IJ.log("sumIntensity = "+sumInt);
                                }
                            }
                        }
                    }
                }
            }
            //IJ.log("sumIntensity = "+sumInt);
        }
        tempPoint[1] = (int) Math.round(sumIntX / sumInt);
        tempPoint[2] = (int) Math.round(sumIntY / sumInt);
        tempPoint[3] = (int) Math.round(sumIntZ / sumInt);
        //IJ.log("tempPoint [" + tempPoint[0] + ", " + tempPoint[1] + ", " + tempPoint[2] + "]");
        //IJ.log("float result [" + sumIntX / sumInt + ", " + sumIntY / sumInt + ", " + sumIntZ / sumInt + "]");
        return tempPoint;
    }

    private int[] getColoThreshCentOfInt(ImagePlus image, int[] centerPt, int[] lastPt, boolean[] analysisCh,
            int frame, float[] baseIntColor, float colDisThresh,
            int xyRadius, int zRadius, int maskRadius) {
        float colDisThresh2 = colDisThresh * colDisThresh;
        int[] tempPoint = {0, 0, 0, 0, 0, 0, 0};
        double sumInt = 0, sumIntX = 0, sumIntY = 0, sumIntZ = 0;
        ImageProcessor[] ip = new ImageProcessor[impNChannel];
        int ipIndex;
        for (int z = centerPt[3] - zRadius; z <= centerPt[3] + zRadius; z++) {
            if (z >= 1 && z <= impNSlice) {
                ImageProcessor ipSum = new FloatProcessor(impWidth, impHeight);
                for (int channel = 1; channel <= impNChannel; channel++) {
                    ipIndex = image.getStackIndex(channel, z, frame);
                    ip[channel - 1] = stk.getProcessor(ipIndex);
                    for (int x = centerPt[1] - xyRadius; x <= centerPt[1] + xyRadius; x++) {
                        if (x >= 0 && x < impWidth) {
                            for (int y = centerPt[2] - xyRadius; y <= centerPt[2] + xyRadius; y++) {
                                if (y >= 0 && y < impHeight) {
                                    ipSum.setf(x, y, ipSum.getf(x, y) + ip[channel - 1].getf(x, y));
                                }
                            }
                        }
                    }
                }
                for (int x = centerPt[1] - xyRadius; x <= centerPt[1] + xyRadius; x++) {
                    if (x >= 0 && x < impWidth) {
                        for (int y = centerPt[2] - xyRadius; y <= centerPt[2] + xyRadius; y++) {
                            if (y >= 0 && y < impHeight) {
                                // determine whether (x, y, z) is masked out
                                int[] currentPt = {0, x, y, z, 0, 0, 0};
                                if (!isMasked(currentPt, centerPt, lastPt, maskRadius)) {
                                    // determin whether cuttent point color is within color distance threshold
                                    float[] currentIntColor = getPtIntColor(currentPt, analysisCh, frame);
                                    float corDis2 = getColorDistanceSquare(baseIntColor, currentIntColor);
                                    if (corDis2 <= colDisThresh2) {
                                        double pixelInt = 0;
                                        pixelInt = pixelInt + ipSum.getf(x, y);
                                        sumInt = sumInt + pixelInt;
                                        sumIntX = sumIntX + pixelInt * x;
                                        sumIntY = sumIntY + pixelInt * y;
                                        sumIntZ = sumIntZ + pixelInt * z;
                                        //IJ.log("Slice ("+z+") * "+pixelInt+" = "+pixelInt * z);
                                        //IJ.log("sumIntPosition ["+sumIntZ+"]");
                                        //IJ.log("Position ("+x+", "+y+", "+z+")");
                                        //IJ.log("sumIntPosition ["+sumIntX+", "+sumIntX+", "+sumIntX+"]");
                                        //IJ.log("sumIntensity = "+sumInt);
                                    }
                                } else {
                                    //IJ.log("masked point ("+x+", "+y+", "+z+")");
                                }
                            }
                        }
                    }
                }
            }
            //IJ.log("sumIntensity = "+sumInt);
        }
        tempPoint[1] = (int) Math.round(sumIntX / sumInt);
        tempPoint[2] = (int) Math.round(sumIntY / sumInt);
        tempPoint[3] = (int) Math.round(sumIntZ / sumInt);
        //IJ.log("tempPoint [" + tempPoint[1] + ", " + tempPoint[2] + ", " + tempPoint[3] + "]");
        //IJ.log("float result [" + sumIntX / sumInt + ", " + sumIntY / sumInt + ", " + sumIntZ / sumInt + "]");
        return tempPoint;
    }
    /*    
     private int[] getColoThreshCentOfInt(int[] centerPt, int[] maskPt1, int[] maskPt2, 
     int frame, float[] baseIntColor, float colDisThresh, 
     int xyRadius, int zRadius, int maskRadius) {
     float colDisThresh2 = colDisThresh * colDisThresh;
     int[] tempPoint = {0, 0, 0, 0, 0, 0, 0};
     double sumInt = 0, sumIntX = 0, sumIntY = 0, sumIntZ = 0;
     ImageProcessor[] ip = new ImageProcessor[impNChannel];
     int ipIndex;
     for (int z = centerPt[3] - zRadius; z <= centerPt[3] + zRadius; z++) {
     if (z >= 1 && z <= impNSlice) {
     ImageProcessor ipSum = new FloatProcessor(impWidth, impHeight);
     for (int channel = 1; channel <= impNChannel; channel++) {
     ipIndex = imp.getStackIndex(channel, z, frame);
     ip[channel - 1] = stk.getProcessor(ipIndex);
     for (int x = 0; x < impWidth; x++) {
     for (int y = 0; y < impHeight; y++) {
     ipSum.setf(x, y, ipSum.getf(x, y) + ip[channel - 1].getf(x, y));
     }
     }
     }
     for (int x = centerPt[1] - xyRadius; x <= centerPt[1] + xyRadius; x++) {
     if (x >= 0 && x < impWidth) {
     for (int y = centerPt[2] - xyRadius; y <= centerPt[2] + xyRadius; y++) {
     if (y >= 0 && y < impHeight) {
     // determine whether (x, y, z) is masked out
     int[] currentPt = {0, x, y, z, 0, 0, 0};
     if (!isMasked(currentPt, maskPt1, maskPt2, maskRadius)){                                    
     // determin whether cuttent point color is within color distance threshold
     float[] currentIntColor = getPtIntColor(currentPt, frame);
     float corDis2 = getColorDistanceSquare(baseIntColor, currentIntColor);
     if (corDis2 <= colDisThresh2) {
     double pixelInt = 0;
     pixelInt = pixelInt + ipSum.getf(x, y);
     sumInt = sumInt + pixelInt;
     sumIntX = sumIntX + pixelInt * x;
     sumIntY = sumIntY + pixelInt * y;
     sumIntZ = sumIntZ + pixelInt * z;
     //IJ.log("Slice ("+z+") * "+pixelInt+" = "+pixelInt * z);
     //IJ.log("sumIntPosition ["+sumIntZ+"]");
     //IJ.log("Position ("+x+", "+y+", "+z+")");
     //IJ.log("sumIntPosition ["+sumIntX+", "+sumIntX+", "+sumIntX+"]");
     //IJ.log("sumIntensity = "+sumInt);
     }
     } else {
     //IJ.log("masked point ("+x+", "+y+", "+z+")");
     }                                
     }
     }
     }
     }
     }
     //IJ.log("sumIntensity = "+sumInt);
     }
     tempPoint[1] = (int) Math.round(sumIntX / sumInt);
     tempPoint[2] = (int) Math.round(sumIntY / sumInt);
     tempPoint[3] = (int) Math.round(sumIntZ / sumInt);
     //IJ.log("tempPoint [" + tempPoint[1] + ", " + tempPoint[2] + ", " + tempPoint[3] + "]");
     //IJ.log("float result [" + sumIntX / sumInt + ", " + sumIntY / sumInt + ", " + sumIntZ / sumInt + "]");
     return tempPoint;
     }
     */

    /**
     *
     * @param startPoint
     * @param frame
     * @param xyRadius
     * @param zRadius
     * @param iteration
     * @return
     */
    public int[] meanShift2CentOfInt(int[] startPoint, int frame,
            int xyRadius, int zRadius, int iteration) {
        int[] currentPoint = {0, startPoint[1], startPoint[2], startPoint[3], 0, 0, 0};
        int count = 0;
        do {
            //int currentXYRadius = checkXYRadius(currentPoint, xyRadius);
            //int currentZRadius = checkZRadius(currentPoint, zRadius);
            int[] nextPoint = getCentOfInt(currentPoint[1], currentPoint[2],
                    currentPoint[3], frame, xyRadius, zRadius);
            if (nextPoint[1] - currentPoint[1] != 0
                    || nextPoint[2] - currentPoint[2] != 0
                    || nextPoint[3] - currentPoint[3] != 0) {
                currentPoint[1] = nextPoint[1];
                currentPoint[2] = nextPoint[2];
                currentPoint[3] = nextPoint[3];
            } else {
                break;
            }
            count++;
            //IJ.log("Count = " + count);
        } while (count <= iteration);

        return currentPoint;
    }

    public int[] meanShift2ColoThreshCentOfInt(ImagePlus image, int[] startPoint, boolean[] analysisCh,
            int frame, float[] baseIntColor, float colDisThresh,
            int xyRadius, int zRadius, int maskRadius, int iteration) {
        int[] currentPoint = {0, startPoint[1], startPoint[2], startPoint[3], 0, 0, 0};
        int count = 0;
        do {
            int[] nextPoint = getColoThreshCentOfInt(image, currentPoint, analysisCh,
                    frame, baseIntColor, colDisThresh, xyRadius, zRadius, maskRadius);
            if (nextPoint[1] - currentPoint[1] != 0
                    || nextPoint[2] - currentPoint[2] != 0
                    || nextPoint[3] - currentPoint[3] != 0) {
                currentPoint[1] = nextPoint[1];
                currentPoint[2] = nextPoint[2];
                currentPoint[3] = nextPoint[3];
            } else {
                break;
            }
            count++;
            //IJ.log("Count = " + count);
        } while (count <= iteration);

        return currentPoint;
    }

    public int[] meanShift2ColoThreshCentOfInt(ImagePlus image, int[] startPoint, int[] lastPt,
            boolean[] analysisCh, int frame, float[] baseIntColor, float colDisThresh,
            int xyRadius, int zRadius, int maskRadius, int iteration) {
        int[] currentPoint = {0, startPoint[1], startPoint[2], startPoint[3], 0, 0, 0};
        int count = 0;
        do {
            int[] nextPoint = getColoThreshCentOfInt(image, currentPoint, lastPt, analysisCh,
                    frame, baseIntColor, colDisThresh, xyRadius, zRadius, maskRadius);
            if (nextPoint[1] - currentPoint[1] != 0
                    || nextPoint[2] - currentPoint[2] != 0
                    || nextPoint[3] - currentPoint[3] != 0) {
                currentPoint[1] = nextPoint[1];
                currentPoint[2] = nextPoint[2];
                currentPoint[3] = nextPoint[3];
            } else {
                break;
            }
            count++;
            //IJ.log("Count = " + count);
        } while (count <= iteration);

        return currentPoint;
    }

    public Roi getCytoSomaRoi(int x, int y, int z, int somaChannel, int frame, String property) {
        if (z < 1 || z > impNSlice) {
            return null;
        }
        ImagePlus impSoma = impDuplicator.run(imp, somaChannel, somaChannel,
                z, z, frame, frame);
        IJ.run(impSoma, "Gaussian Blur...", "sigma=3");
        IJ.setAutoThreshold(impSoma, "Huang dark");
        IJ.run(impSoma, "Convert to Mask", "");
        //IJ.run(impSoma, "Watershed", "");
        //impSoma.show();
        IJ.doWand(impSoma, x, y, 1.0, "8-connected");
        //IJ.run(impSoma, "Fill", "slice");
        Roi somaRoi = impSoma.getRoi();
        if (property.equals("fill")) {
            somaRoi.setFillColor(somaRoiFillColor);
        }
        if (property.equals("stroke")) {
            somaRoi.setStrokeColor(Color.yellow);
            somaRoi.setStrokeWidth(thinLine);
        }
        somaRoi.setPosition(0, z, frame);
        int perimeter = (int) (2 * (somaRoi.getBounds().getWidth() + somaRoi.getBounds().getHeight()));
        if (perimeter < 40 || perimeter > 1000) {
            somaRoi = null;
        }
        impSoma.flush();
        //impSoma.close();
        return somaRoi;
    }

    // private methods
    private ImagePlus meanSmoothImage(int kernelR, ImagePlus imp,
            int minCh, int maxCh, int minZ, int maxZ, int minFrame, int maxFrame) {
        ImagePlus impSmooth = impDuplicator.run(imp, minCh, maxCh,
                minZ, maxZ, minFrame, maxFrame);
        int kernelD = kernelR * 2 + 1;
        int kernelPixels = kernelD * kernelD;
        float meanWeight = 1f / (float) kernelPixels;
        float[] kernel = new float[kernelPixels];
        for (int n = 0; n < kernelPixels; n++) {
            kernel[n] = meanWeight;
        }
        ImageStack stkSmooth = impSmooth.getImageStack();
        for (int i = 1; i <= stkSmooth.getSize(); i++) {
            ImageProcessor ip = stkSmooth.getProcessor(i);
            meanFilter.convolve(ip, kernel, kernelD, kernelD);
            stkSmooth.setPixels(ip.getPixels(), i);
        }
        return impSmooth;
    }

    /**
     * Return the cost map of the input image. The cost of each pixel is
     * calculated as "Intensity/color distance" or "normalized Intensity +
     * normalized color distance". Color distance is the square of the Euclidean
     * distance between the normalized color of the pixel to the baseIntColor.
     *
     * @param impInput input image
     * @param baseIntColor
     * @return
     */
    private ImagePlus getCostImage(ImagePlus impInput, float[] baseIntColor, boolean[] analysisCh, int costFunction) {
        ImageStack stkCost = new ImageStack(impInput.getWidth(), impInput.getHeight(), impInput.getNSlices());
        if (costFunction == int2ColDisRatio) {
            for (int z = 1; z <= impInput.getNSlices(); z++) {
                ImageProcessor ipIntColorCost = new FloatProcessor(impInput.getWidth(), impInput.getHeight());
                ImageProcessor[] ipChannel = new ImageProcessor[impInput.getNChannels()];
                for (int c = 1; c <= impInput.getNChannels(); c++) {
                    int index = impInput.getStackIndex(c, z, 1);
                    ipChannel[c - 1] = impInput.getImageStack().getProcessor(index);
                    if (!analysisCh[c - 1]) {
                        ipChannel[c - 1].multiply(0);
                    }
                }
                for (int x = 0; x < impInput.getWidth(); x++) {
                    for (int y = 0; y < impInput.getHeight(); y++) {
                        float[] chIntensity = new float[impInput.getNChannels()];
                        float intensity = 0;
                        double colorDistance = 0;
                        for (int c = 0; c < impInput.getNChannels(); c++) {
                            // store intensity of each channel (c) at each position (x, y)
                            chIntensity[c] = ipChannel[c].getf(x, y);
                                // sum intensity at each position (x, y)
                            // +1 is to make sure ipIntensity never equals to 0
                            intensity += chIntensity[c] + 1;
                        }
                        for (int c = 0; c < impInput.getNChannels(); c++) {
                                // sum colorDistance of each channel (c) at each position (x, y)
                            // +0.000001 is to make sure colorDistance never equals to 0
                            colorDistance += Math.pow((baseIntColor[c + 1] - chIntensity[c] / intensity), 2) + 0.000001;
                        }
                        // set intensityColorCost at each position (x, y)
                        ipIntColorCost.setf(x, y, (float) (colorDistance / intensity));
                    }
                }
                // set ipIntColorCost at slice (z) to srkProb (as pro
                stkCost.setPixels(ipIntColorCost.getPixels(), z);
            }
        }
        if (costFunction == normSumIntColDis) {
            for (int z = 1; z <= impInput.getNSlices(); z++) {
                ImageProcessor ipIntColorCost = new FloatProcessor(impInput.getWidth(), impInput.getHeight());
                ImageProcessor[] ipChannel = new ImageProcessor[impInput.getNChannels()];
                for (int c = 1; c <= impInput.getNChannels(); c++) {
                    int index = impInput.getStackIndex(c, z, 1);
                    ipChannel[c - 1] = impInput.getImageStack().getProcessor(index);
                    if (!analysisCh[c - 1]) {
                        ipChannel[c - 1].multiply(0);
                    }
                }
                for (int x = 0; x < impInput.getWidth(); x++) {
                    for (int y = 0; y < impInput.getHeight(); y++) {
                        float[] chIntensity = new float[impInput.getNChannels()];
                        float intensity = 0;
                        double colorDistance = 0;
                        for (int c = 0; c < impInput.getNChannels(); c++) {
                            // store intensity of each channel (c) at each position (x, y)
                            chIntensity[c] = ipChannel[c].getf(x, y);
                            // sum intensity at each position (x, y)
                            // +1 is to make sure ipIntensity never equals to 0
                            intensity += chIntensity[c] + 1;
                        }
                        for (int c = 0; c < impInput.getNChannels(); c++) {
                            // sum colorDistance of each channel (c) at each position (x, y)
                            // +0.0001 is to make sure colorDistance never equals to 0
                            colorDistance += Math.pow((baseIntColor[c + 1] - chIntensity[c] / intensity), 2) + 0.0001;
                        }
                        // set intensityColorCost at each position (x, y)
                        ipIntColorCost.setf(x, y, (float) (colorDistance + (baseIntColor[0] / intensity) * 0.1f));
                    }
                }
                // set ipIntColorCost at slice (z) to srkProb (as pro
                stkCost.setPixels(ipIntColorCost.getPixels(), z);
            }
        }

        ImagePlus impCost = new ImagePlus("Cost Map", stkCost);
        //impProb.show();

        return impCost;
    }

    private boolean isMasked(int[] checkPt, int[] maskPt, int maskRadius) {
        return checkPt[0] >= maskPt[0] - maskRadius && checkPt[0] <= maskPt[0] + maskRadius
                && checkPt[1] >= maskPt[1] - maskRadius && checkPt[1] <= maskPt[1] + maskRadius
                && checkPt[2] >= maskPt[2] - maskRadius && checkPt[2] <= maskPt[2] + maskRadius;
    }

    private boolean isMasked(int[] checkPt, int[] mask1Pt, int[] mask2Pt, int maskRadius) {
        return checkPt[0] >= min(mask1Pt[0], mask2Pt[0]) - maskRadius && checkPt[0] <= max(mask1Pt[0], mask2Pt[0]) + maskRadius
                && checkPt[1] >= min(mask1Pt[1], mask2Pt[1]) - maskRadius && checkPt[1] <= max(mask1Pt[1], mask2Pt[1]) + maskRadius
                && checkPt[2] >= min(mask1Pt[2], mask2Pt[2]) - maskRadius && checkPt[2] <= max(mask1Pt[2], mask2Pt[2]) + maskRadius;
    }

    private int min(int a, int b) {
        int c = a < b ? a : b;
        return c;
    }

    private int max(int a, int b) {
        int c = a > b ? a : b;
        return c;
    }

    // <editor-fold defaultstate="collapsed" desc="check whether X, Y, Z is out of bound">
    private int xBoundCheck(int X, int xyRadius) {
        if (X - xyRadius < 0) {
            return xyRadius;
        } else if (X + xyRadius >= impWidth) {
            return impWidth - xyRadius - 1;
        } else {
            return X;
        }
    }

    private int yBoundCheck(int Y, int xyRadius) {
        if (Y - xyRadius < 0) {
            return xyRadius;
        } else if (Y + xyRadius >= impHeight) {
            return impHeight - xyRadius - 1;
        } else {
            return Y;
        }
    }

    private int zBoundCheck(int Z, int zRadius) {
        if (Z - zRadius < 1) {
            return zRadius + 1;
        } else if (Z + zRadius > impNSlice) {
            return impNSlice - zRadius;
        } else {
            return Z;
        }
    }
    // </editor-fold>

    public ArrayList<String[]> convertIntArray2StringArray(ArrayList<int[]> intPoints) {
        ArrayList<String[]> stringPoints = new ArrayList<String[]>();
        for (int[] intPt : intPoints) {
            String[] stringPt = {intPt[0] + "", intPt[1] + "", intPt[2] + "", intPt[3] + "",
                intPt[4] + "", intPt[5] + "", intPt[6] + ""};
            stringPoints.add(stringPt);
        }
        return stringPoints;
    }

    public ArrayList<int[]> convertStringArray2IntArray(ArrayList<String[]> stringPoints) {
        ArrayList<int[]> intPoints = new ArrayList<int[]>();
        for (String[] strinPt : stringPoints) {
            int[] intPt = {0, Integer.parseInt(strinPt[1]), Integer.parseInt(strinPt[2]), 
                Integer.parseInt(strinPt[3]), Integer.parseInt(strinPt[4]), Integer.parseInt(strinPt[5]), 0};
            intPoints.add(intPt);
        }
        return intPoints;
    }

    public ArrayList<int[]> removeRedundantTracingPoints(ArrayList<int[]> tracingPoints) {
        if (tracingPoints.size()>1) {
            ArrayList<int[]> finalPathPoints = new ArrayList<int[]>();
            for (int[] tracingPoint : tracingPoints) {
                boolean add = true;
                int[] addPoint = tracingPoint;
                for (int[] finalPoint : finalPathPoints) {
                    if (addPoint[1] == finalPoint[1] && addPoint[2] == finalPoint[2] && addPoint[3] == finalPoint[3]) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    finalPathPoints.add(addPoint);
                }
            }
            return finalPathPoints;
        } else {
            return tracingPoints;
        }
    }

    // variables
    private ImagePlus imp;
    private ImageStack stk;
    private int impWidth, impHeight, impNChannel, impNSlice;
    private final Duplicator impDuplicator = new Duplicator();
    private final int int2ColDisRatio = 1, normSumIntColDis = 2;
    Convolver meanFilter = new Convolver();
    Color somaRoiFillColor = new Color(0.0f, 0.8f, 0.8f, 0.2f);
    private final float thinLine = 0.5f, thickLine = 1.5f;
}
