/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package AlignMaster;

import ij.*;
import ij.gui.Roi;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.plugin.CanvasResizer;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.plugin.filter.Translator;
import ij.process.ImageProcessor;
import java.util.ArrayList;

import imagescience.image.Image;
//import imagescience.transform.Translate;
//import imagescience.transform.Transform;

/**
 *
 * @author Dawen
 */
public class AMfunctions {

    public AMfunctions() {
    }

    // public methods
//    public void doCorrectionFile(CompositeImage correction, double[][] transformMatrix) {
//        doChannelRegistrationMultiThread(correction, transformMatrix);
//    }

    public double[][] getNoMaskTranslateMatrix(ImagePlus impROI, int refCh,
            double maxXYshift, double maxZshift, double accuracy) {
        // return a translation transform matrix for all channels

        // store the best cross correlation result:
        // [channel][dx, dy, dz, correlation]
        // lastShift[0][] is not used
        double[][] lastShift = new double[impROI.getNChannels() + 1][4];
        for (int m = 0; m < impROI.getNChannels() + 1; m++) {
            for (int n = 0; n < 4; n++) {
                lastShift[m][n] = 0;
            }
        }
        // reference channel needs no shift and correlation is 1
        lastShift[refCh][0] = 0; // x translation
        lastShift[refCh][1] = 0; // y translation
        lastShift[refCh][2] = 0; // z translation
        lastShift[refCh][3] = 1; // correlation value

        iterativeNoMaskTranslateMultiThread(impROI, refCh, maxXYshift, maxZshift, accuracy, lastShift);

        return lastShift;
    }

    public double[][] getMaskedTranslateMatrix(ImagePlus impROI, ImagePlus mask,
            int refCh, double maxXYshift, double maxZshift) {
        // return a translation transform matrix for all channels

        // store the best cross correlation result:
        // [channel][dx, dy, dz, correlation]
        // lastShift[0][] is not used
        double[][] lastShift = new double[impROI.getNChannels() + 1][4];
        for (int m = 0; m < impROI.getNChannels() + 1; m++) {
            for (int n = 0; n < 4; n++) {
                lastShift[m][n] = 0;
            }
        }
        // reference channel needs no shift and correlation is 1
        lastShift[refCh][0] = 0; // x translation
        lastShift[refCh][1] = 0; // y translation
        lastShift[refCh][2] = 0; // z translation
        lastShift[refCh][3] = 1; // correlation value

        iterativeMaskedTranslateMultiThread(impROI, mask, refCh, maxXYshift, maxZshift, lastShift);

        return lastShift;
    }

    public double[][][] getTransformMatrix(ImagePlus imp, int refCh, boolean subpixel) {
        // return a transform matrix for all channels
        //Transform transformer = new Transform();
        int totalCh = imp.getNChannels();
        double[][][] transformMatrix = new double[totalCh][4][4];
        for (int n = 0; n < totalCh; n++) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    transformMatrix[n][i][j] = 0;
                }
            }
        }
        for (int n = 0; n < totalCh; n++) {
            transformMatrix[n][3][0] = 0;
            transformMatrix[n][3][1] = 0;
            transformMatrix[n][3][2] = 0;
            transformMatrix[n][3][3] = 1;
        }

        return transformMatrix;
    }

    public void midsliceAlignment(ImagePlus imp, double[][][] translationMatrix) {

    }

    private void translateImgCh (ImagePlus imp, int channel,
            double xOffset, double yOffset, double zOffset) {
        int width = imp.getWidth();
        int height = imp.getHeight();
        int depth = imp.getNSlices();
        ImageStack stk = imp.getImageStack();
        ImagePlus impTranslate = (new Duplicator()).run(imp, channel, channel, 1, depth, 1, 1);
        ImageStack stkTranslate = impTranslate.getStack();
        if (xOffset != 0 || yOffset != 0) { // X-Y translation                
            IJ.run(impTranslate, "Translate...", "x=" + xOffset + " y=" + yOffset + " interpolation=None");
            IJ.log("Ch" + channel + " done");
            for (int z = 1; z <= depth; z++) {
                ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                ImageProcessor sourceIP = stkTranslate.getProcessor(z);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        targetIP.set(x, y, sourceIP.get(x, y));
                    }
                }
            }
        }
        if (zOffset < 0) {
            for (int z = 1; z < depth + (int) zOffset; z++) {
                ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                ImageProcessor sourceIP = stkTranslate.getProcessor(z - (int) zOffset);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        targetIP.set(x, y, sourceIP.get(x, y));
                    }
                }
            }
            for (int z = depth + (int) zOffset; z <= depth; z++) {
                ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        targetIP.set(x, y, 0);
                    }
                }
            }
        }
        if (zOffset > 0) {
            for (int z = 1; z <= (int) zOffset; z++) {
                ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        targetIP.set(x, y, 0);
                    }
                }
            }
            for (int z = 1 + (int) zOffset; z <= depth; z++) {
                ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                ImageProcessor sourceIP = stkTranslate.getProcessor(z - (int) zOffset);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        targetIP.set(x, y, sourceIP.get(x, y));
                    }
                }
            }
        }
        impTranslate.close();
    }

    public ImagePlus doChannelRegistration(ImagePlus imp, double[][] translateMatrix){
        int nChannels = imp.getNChannels();
        if (nChannels == (translateMatrix.length - 1)) {
            for (int channel = 1; channel <= nChannels; channel++) {
                if (translateMatrix[channel][3] != 1) {
                    int width = imp.getWidth();
                    int height = imp.getHeight();
                    int depth = imp.getNSlices();
                    int xOffset = (int) translateMatrix[channel][0];
                    int yOffset = (int) translateMatrix[channel][1];
                    int zOffset = (int) translateMatrix[channel][2];
                    ImageStack stk = imp.getImageStack();
                    ImagePlus impTranslate = (new Duplicator()).run(imp, channel, channel, 1, depth, 1, 1);
                    ImageStack stkTranslate = impTranslate.getStack();
                    if (xOffset != 0 || yOffset != 0) { // X-Y translation                
                        IJ.run(impTranslate, "Translate...", "x=" + xOffset + " y=" + yOffset + " interpolation=None");
                        IJ.log("Ch" + channel + " done");
                        for (int z = 1; z <= depth; z++) {
                            ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                            ImageProcessor sourceIP = stkTranslate.getProcessor(z);
                            for (int x = 0; x < width; x++) {
                                for (int y = 0; y < height; y++) {
                                    targetIP.set(x, y, sourceIP.get(x, y));
                                }
                            }
                        }
                    }
                    if (zOffset < 0) {
                        for (int z = 1; z < depth + (int) zOffset; z++) {
                            ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                            ImageProcessor sourceIP = stkTranslate.getProcessor(z - (int) zOffset);
                            for (int x = 0; x < width; x++) {
                                for (int y = 0; y < height; y++) {
                                    targetIP.set(x, y, sourceIP.get(x, y));
                                }
                            }
                        }
                        for (int z = depth + (int) zOffset; z <= depth; z++) {
                            ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                            for (int x = 0; x < width; x++) {
                                for (int y = 0; y < height; y++) {
                                    targetIP.set(x, y, 0);
                                }
                            }
                        }
                    }
                    if (zOffset > 0) {
                        for (int z = 1; z <= (int) zOffset; z++) {
                            ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                            for (int x = 0; x < width; x++) {
                                for (int y = 0; y < height; y++) {
                                    targetIP.set(x, y, 0);
                                }
                            }
                        }
                        for (int z = 1 + (int) zOffset; z <= depth; z++) {
                            ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                            ImageProcessor sourceIP = stkTranslate.getProcessor(z - (int) zOffset);
                            for (int x = 0; x < width; x++) {
                                for (int y = 0; y < height; y++) {
                                    targetIP.set(x, y, sourceIP.get(x, y));
                                }
                            }
                        }
                    }
                    impTranslate.close();
                    imp.updateChannelAndDraw();
                    //translateImgCh (imp, channel,translateMatrix[channel][0], 
                    //        translateMatrix[channel][1], translateMatrix[channel][2]);
                }
            }
        } else {
            IJ.error("Channel numbers do NOT match!");
        }
        return imp;
    }
    
    public void doChannelRegistrationMultiThread(ImagePlus imp, double[][] translateMatrix) {
        int nChannels = imp.getNChannels();
        ArrayList<translateImgChThread> threads = new ArrayList<>();
        if (nChannels == (translateMatrix.length - 1)) {
            for (int channel = 1; channel <= nChannels; channel++) {
                if (translateMatrix[channel][3] != 1) {
                    translateImgChThread tiThread = new translateImgChThread(imp, channel,
                            translateMatrix[channel][0], translateMatrix[channel][1], translateMatrix[channel][2]);
                    threads.add(tiThread);
                    tiThread.start();
                }
            }
            threads.stream().forEach((thread) -> {
                try {
                    thread.join();
                } catch (Exception e) {
                }
            });
        } else {
            IJ.error("Channel numbers do NOT match!");
        }
    }

    public void updateCorChannel(ImagePlus impROI, int channel, ImagePlus impUpdate) {
        int nChannels = impROI.getNChannels();
        int nFrames = impROI.getNFrames();
        int nSlices = impROI.getNSlices();
        if (impUpdate.getNSlices() != nSlices) {
            return;
        }
        ImageStack stkROI = impROI.getStack();
        ImageStack stkTranslate = impUpdate.getStack();
        for (int s = 0; s < nSlices; s++) {
            stkROI.setPixels(stkTranslate.getProcessor((s + 1)).getPixels(), (s * nChannels * nFrames + channel));
        }
        impROI.updateChannelAndDraw();
    }

    /**
     * return cross-correlation value of two double Arrays with identical length
     * Array[0] stores the average value of the rest elements
     *
     * @param refChImg
     * @param corChImg
     * @return
     */
    /**
     * return cross-correlation value of two double Arrays with identical length
     * Array[0] stores the average value of the rest elements
     *
     * @param reference
     * @param correction
     * @return
     */
    public double getCrossCorrelation(double[] reference, double[] correction) {
        double refAve = reference[0], corAve = correction[0];
        double refcorSum = 0;
        double ref2Sum = 0, cor2sum = 0;
        double ref = 0, cor = 0;
        double refDif = 0, corDif = 0;
        int nValues = correction.length - 1;

        /*
         * // multithreading
         ThreadUtil tu = new ThreadUtil();
         int cpuNb = ThreadUtil.getNbCpus();
         if (cpuNb>1){
         cpuNb--;
         }
         Thread[] threads = ThreadUtil.createThreadArray(cpuNb);
         *
         */
        for (int i = 1; i < nValues; i++) {
            refDif = reference[i] - refAve;
            corDif = correction[i] - corAve;
            refcorSum += refDif * corDif;
            ref2Sum += Math.pow(refDif, 2);
            cor2sum += Math.pow(corDif, 2);
        }

        double crossCo = refcorSum / Math.sqrt(ref2Sum) / Math.sqrt(cor2sum);
        return crossCo;
    }

    private ArrayList<double[]> getNoMaskLinearArrayValues(ImagePlus refChImg, ImagePlus corChImg) {
        ArrayList refChValueList = new ArrayList();
        ArrayList corChValueList = new ArrayList();

        int nFrames = refChImg.getNFrames();
        int nSlices = refChImg.getNSlices();
        int width = refChImg.getWidth();
        int hight = refChImg.getHeight();
        double refChPixelValue, corChPixelValue;
        double refChSum = 0, corChSum = 0;
        ImageStack refChStk = refChImg.getImageStack();
        ImageStack corChStk = corChImg.getImageStack();

        for (int frame = 1; frame <= nFrames; frame++) {
            for (int z = 0; z < nSlices; z++) {
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < hight; y++) {
                        refChPixelValue = refChStk.getVoxel(x, y, z);
                        corChPixelValue = corChStk.getVoxel(x, y, z);
                        if (refChPixelValue * corChPixelValue > 0) {
                            refChValueList.add(refChPixelValue);
                            refChSum = refChSum + refChPixelValue;
                            corChValueList.add(corChPixelValue);
                            corChSum = corChSum + corChPixelValue;
                        }
                    }
                }
            }
        }

        double[] refChValue = new double[refChValueList.size() + 1];
        double[] corChValue = new double[corChValueList.size() + 1];
        refChValue[0] = refChSum / refChValueList.size();
        corChValue[0] = corChSum / corChValueList.size();
        for (int i = 1; i <= refChValueList.size(); i++) {
            refChValue[i] = (Double) refChValueList.get(i - 1);
            corChValue[i] = (Double) corChValueList.get(i - 1);
        }
        ArrayList<double[]> returnValues = new ArrayList<>();
        returnValues.add(refChValue);
        returnValues.add(corChValue);
        return returnValues;
    }
    private ArrayList<double[]> getMaskedLinearArrayValues(ImagePlus refChImg, ImagePlus corChImg, ImagePlus maskImg) {
        ArrayList refChValueList = new ArrayList();
        ArrayList corChValueList = new ArrayList();

        int nFrames = refChImg.getNFrames();
        int nSlices = refChImg.getNSlices();
        int width = refChImg.getWidth();
        int hight = refChImg.getHeight();
        double refChPixelValue, corChPixelValue, maskPixelValue;
        double refChSum = 0, corChSum = 0;
        ImageStack refChStk = refChImg.getImageStack();
        ImageStack corChStk = corChImg.getImageStack();
        ImageStack maskStk = maskImg.getImageStack();

        for (int frame = 1; frame <= nFrames; frame++) {
            for (int z = 1; z <= nSlices; z++) {
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < hight; y++) {
                        refChPixelValue = refChStk.getVoxel(x, y, z);
                        corChPixelValue = corChStk.getVoxel(x, y, z);
                        maskPixelValue = maskStk.getVoxel(x, y, z);
                        if (refChPixelValue * corChPixelValue * maskPixelValue > 0) {
                            refChValueList.add(refChPixelValue);
                            refChSum = refChSum + refChPixelValue;
                            corChValueList.add(corChPixelValue);
                            corChSum = corChSum + corChPixelValue;
                        }
                    }
                }
            }
        }

        double[] refChValue = new double[refChValueList.size() + 1];
        double[] corChValue = new double[corChValueList.size() + 1];
        refChValue[0] = refChSum / refChValueList.size();
        corChValue[0] = corChSum / corChValueList.size();
        for (int i = 1; i <= refChValueList.size(); i++) {
            refChValue[i] = (Double) refChValueList.get(i - 1);
            corChValue[i] = (Double) corChValueList.get(i - 1);
        }
        ArrayList<double[]> returnValues = new ArrayList<double[]>();
        returnValues.add(refChValue);
        returnValues.add(corChValue);
        return returnValues;
    }

    private double[] getMaskedLinearArrayValues(ImagePlus imp, ImagePlus mask) {
        ArrayList valueList = new ArrayList();
        int nFrames = imp.getNFrames();
        int nSlices = imp.getNSlices();
        int width = imp.getWidth();
        int hight = imp.getHeight();
        int index = 0;
        double pixelValue = 0;
        double sum = 0;
        ImageStack stk = imp.getImageStack();
        ImageStack maskStk = mask.getImageStack();

        for (int frame = 1; frame <= nFrames; frame++) {
            for (int slice = 1; slice <= nSlices; slice++) {
                index = imp.getStackIndex(1, slice, frame);
                ImageProcessor ip = stk.getProcessor(index);
                ImageProcessor maskIP = maskStk.getProcessor(index);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < hight; y++) {
                        if (maskIP.get(x, y) > 0) {
                            pixelValue = ip.get(x, y);
                            valueList.add(pixelValue);
                            sum = sum + pixelValue;
                        }
                    }
                }
            }
        }

        double[] value = new double[valueList.size() + 1];
        value[0] = sum / valueList.size();
        for (int i = 1; i <= valueList.size(); i++) {
            value[i] = (Double) valueList.get(i - 1);
        }

        return value;
    }

    public class noMaskIterativeCorrelationThread extends Thread {
        ImagePlus refImp, corImp;
        double searchXYrange, xyAccuracy, dz;
        double[][] lastShift;
        int corCh;
        double maxZshift;
        Roi corROI;
        double[] ccValue;

        public noMaskIterativeCorrelationThread(
                ImagePlus refImp, ImagePlus corImp, double searchXYrange, double xyAccuracy, double dz,
                double[][] lastShift, int corCh, double maxZshift, Roi corROI) {
            this.refImp = refImp;
            this.corImp = corImp;
            this.searchXYrange = searchXYrange;
            this.xyAccuracy = xyAccuracy;
            this.dz = dz;
            this.lastShift = lastShift;
            this.corCh = corCh;
            this.maxZshift = maxZshift;
            this.corROI = corROI;
        }

        /**
         * Thread for mean-shift iteration to calculate the best X-Y-Z shift for the alignment image to the reference image 
         */
        @Override
        public void run() {
            // generate a Z translated image
            ArrayList<double[]> croCorResult = new ArrayList<>();
            ImagePlus zTranslateCorImp = (new Duplicator()).run(corImp, 1, 1,
                    1 + (int) maxZshift - (int) dz, corImp.getNSlices() - (int) maxZshift - (int) dz, 1, 1);
            for (double dx = -searchXYrange; dx <= searchXYrange; dx += xyAccuracy) {
                for (double dy = -searchXYrange; dy <= searchXYrange; dy += xyAccuracy) {
                    // generate a X-Y translated image 
                    ImagePlus xyzTranslateCorImg = zTranslateCorImp.duplicate();
                    ImageStack xyzTranslateCorStk = xyzTranslateCorImg.getImageStack();
                    for (int z = 1; z <= xyzTranslateCorStk.getSize(); z++) {
                        ImageProcessor xyzTranslateCorIp = xyzTranslateCorStk.getProcessor(z);
                        xyzTranslateCorIp.setInterpolationMethod(ImageProcessor.NONE);
                        xyzTranslateCorIp.translate(dx, dy);
                    }
                    xyzTranslateCorImg.setStack(xyzTranslateCorStk);
                    // get cross corelation value
                    ArrayList<double[]> lineaArrayValues = getNoMaskLinearArrayValues(refImp, xyzTranslateCorImg);
                    double correlation = getCrossCorrelation(lineaArrayValues.get(0), lineaArrayValues.get(1));
                    double[] croCor = {dx, dy, dz, correlation};
                    croCorResult.add(croCor);
                    xyzTranslateCorImg.close();
                }
            }
            zTranslateCorImp.close();
            ccValue = croCorResult.get(0);
            for (int i = 1; i < croCorResult.size(); i++) {
                double[] currentValue = croCorResult.get(i);
                if (currentValue[3] > ccValue[3]) {
                    ccValue[0] = currentValue[0];
                    ccValue[1] = currentValue[1];
                    ccValue[2] = currentValue[2];
                    ccValue[3] = currentValue[3];
                }
            }
        }

        public double[] getCCValue() {
            return ccValue;
        }
    }

    public class translateShiftCorrelationThread extends Thread {
        Image translate;
        double dx, dy, dz;
        double[][] lastShift;
        int corCh;
        double maxZ;
        Roi corROI;
        ImagePlus impROI;
        ImagePlus corMask;
        double[] refValues;
        double croCor;

        public translateShiftCorrelationThread(
                Image translate, double dx, double dy, double dz,
                double[][] lastShift, int corCh, double maxZ, Roi corROI,
                ImagePlus impROI, ImagePlus corMask, double[] refValues) {
            this.translate = translate;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.lastShift = lastShift;
            this.corCh = corCh;
            this.maxZ = maxZ;
            this.corROI = corROI;
            this.impROI = impROI;
            this.corMask = corMask;
            this.refValues = refValues;
        }

        /**
         * Thread for calculating the correlation between translated image and reference image 
         */
        @Override
        public void run() {
            // generate a translated/shifted image
            ImagePlus impTranslate = translator.run(translate,
                    lastShift[corCh][0] + dx,
                    lastShift[corCh][1] + dy,
                    lastShift[corCh][2] + dz,
                    AMtranslate.BSPLINE5).imageplus();
            // crop down to corROI and sub-Zstack
            impTranslate.setRoi(corROI, false);
            ImagePlus correction = (new Duplicator()).run(
                    impTranslate, 1, 1,
                    1 + (int) maxZ, impROI.getNSlices() - (int) maxZ,
                    1, impROI.getNFrames());
            //impTranslate.flush();
            // get cross corelation value
            double[] corValues = getMaskedLinearArrayValues(correction, corMask);
            croCor = getCrossCorrelation(refValues, corValues);
            //correction.flush();
        }

        public double getCCValue() {
            return croCor;
        }
    }

    public class stitchHorizontalThread extends Thread {
        ImagePlus refImg, algImg;
        int xWidth, xStitchOffset, yStitchOffset, zStitchOffset;
        int core, increment;

        public stitchHorizontalThread(ImagePlus refImg, ImagePlus algImg,
                int xWidth, int xStitchOffset, int yStitchOffset, int zStitchOffset,
                int core, int increment) {
            this.refImg = refImg;
            this.algImg = algImg;
            this.xWidth = xWidth;
            this.xStitchOffset = xStitchOffset;
            this.yStitchOffset = yStitchOffset;
            this.zStitchOffset = zStitchOffset;
            this.core = core;
            this.increment = increment;
        }

        /**
         * Thread for stitching two stacks horizontally
         */
        @Override
        public void run() {
            ImageStack refStack = refImg.getImageStack();
            ImageStack algStack = algImg.getImageStack();
            int maxZ = (core + 1) * increment < refImg.getNSlices() ? (core + 1) * increment : refImg.getNSlices();
            for (int z = 1 + core * increment; z <= maxZ; z++) {
                for (int c = 1; c <= refImg.getNChannels(); c++) {
                    int algStackIndex = algImg.getStackIndex(c, z, 1);
                    ImageProcessor algIP = algStack.getProcessor(algStackIndex);
                    int refStackIndex = refImg.getStackIndex(c, z + zStitchOffset, 1);
                    ImageProcessor refIP = refStack.getProcessor(refStackIndex);
                    for (int x = 0; x < algImg.getWidth(); x++) {
                        float algWeight = getNonLinearFusionWeight(x, xWidth);
                        float refWeight = 1 - algWeight;
                        for (int y = 0; y < algImg.getHeight(); y++) {
                            float refInt = refIP.get(x + xStitchOffset, y + yStitchOffset);
                            float algInt = algIP.get(x, y);
                            float fuseIntensity;
                            if (refInt == 0 || algInt == 0) {
                                fuseIntensity = refInt + algInt;
                            } else {
                                fuseIntensity = refWeight * refInt + algWeight * algInt;
                            }
                            refIP.set(x + xStitchOffset, y + yStitchOffset, (int) fuseIntensity);
                        }
                    }
                    refStack.setProcessor(refIP, refStackIndex);
                }
            }
        }
    }

    public class stitchVerticalThread extends Thread {
        ImagePlus refImg, algImg;
        int yWidth, xStitchOffset, yStitchOffset, zStitchOffset;
        int core, increment;

        public stitchVerticalThread(ImagePlus refImg, ImagePlus algImg,
                int yWidth, int xStitchOffset, int yStitchOffset, int zStitchOffset,
                int core, int increment) {
            this.refImg = refImg;
            this.algImg = algImg;
            this.yWidth = yWidth;
            this.xStitchOffset = xStitchOffset;
            this.yStitchOffset = yStitchOffset;
            this.zStitchOffset = zStitchOffset;
            this.core = core;
            this.increment = increment;
        }

        /**
         * Thread for stitch two stacks vertically
         */
        @Override
        public void run() {
            ImageStack refStack = refImg.getImageStack();
            ImageStack algStack = algImg.getImageStack();
            int maxZ = (core + 1) * increment < refImg.getNSlices() ? (core + 1) * increment : refImg.getNSlices();
            for (int z = 1 + core * increment; z <= maxZ; z++) {
                for (int c = 1; c <= refImg.getNChannels(); c++) {
                    int algStackIndex = algImg.getStackIndex(c, z, 1);
                    ImageProcessor algIP = algStack.getProcessor(algStackIndex);
                    int refStackIndex = refImg.getStackIndex(c, z + zStitchOffset, 1);
                    ImageProcessor refIP = refStack.getProcessor(refStackIndex);
                    for (int y = 0; y < algImg.getHeight(); y++) {
                        float algWeight = getNonLinearFusionWeight(y, yWidth);
                        float refWeight = 1 - algWeight;
                        for (int x = 0; x < algImg.getWidth(); x++) {
                            float refInt = refIP.get(x + xStitchOffset, y + yStitchOffset);
                            float algInt = algIP.get(x, y);
                            float fuseIntensity;
                            if (refInt == 0 || algInt == 0) {
                                fuseIntensity = refInt + algInt;
                            } else {
                                fuseIntensity = refWeight * refInt + algWeight * algInt;
                            }
                            refIP.set(x + xStitchOffset, y + yStitchOffset, (int) fuseIntensity);
                        }
                    }
                    refStack.setProcessor(refIP, refStackIndex);
                }
            }
        }
    }

    public class translateImgChThread extends Thread {
        ImagePlus imp;
        int channel;
        double xOffset, yOffset, zOffset;

        public translateImgChThread(ImagePlus imp, int channel, 
                double xOffset, double yOffset, double zOffset) {
            this.imp = imp;
            this.channel = channel;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.zOffset = zOffset;
        }

        /**
         * Thread for creating translated image for calculating correlation to refImg
         */
        @Override
        public void run() {
            int width = imp.getWidth();
            int height = imp.getHeight();
            int depth = imp.getNSlices();
            ImageStack stk = imp.getImageStack();
            ImagePlus impTranslate = (new Duplicator()).run(imp, channel, channel, 1, depth, 1, 1);
            ImageStack stkTranslate = impTranslate.getStack();
            if (xOffset != 0 || yOffset != 0) { // X-Y translation                
                for (int z = 1; z <= depth; z++){
                    stkTranslate.getProcessor(z).translate(xOffset, yOffset);
                }
            }
            if (zOffset == 0) {
                for (int z = 1; z < depth; z++) {
                    ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                    ImageProcessor sourceIP = stkTranslate.getProcessor(z);
                    for (int x = 0; x< width; x++){
                        for (int y = 0; y<height; y++){
                            targetIP.set(x, y, sourceIP.get(x, y));
                        }
                    }
                }
            }
            if (zOffset < 0) {
                for (int z = 1; z < depth+(int)zOffset; z++) {
                    ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                    ImageProcessor sourceIP = stkTranslate.getProcessor(z-(int)zOffset);
                    for (int x = 0; x< width; x++){
                        for (int y = 0; y<height; y++){
                            targetIP.set(x, y, sourceIP.get(x, y));
                        }
                    }
                }
                for (int z = depth+(int)zOffset; z <= depth; z++) {
                    ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                    for (int x = 0; x< width; x++){
                        for (int y = 0; y<height; y++){
                            targetIP.set(x, y, 0);
                        }
                    }
                }
            }
            if (zOffset > 0) {
                for (int z = 1; z <= (int)zOffset; z++) {
                    ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                    for (int x = 0; x< width; x++){
                        for (int y = 0; y<height; y++){
                            targetIP.set(x, y, 0);
                        }
                    }
                }
                for (int z = 1+(int)zOffset; z <= depth; z++) {
                    ImageProcessor targetIP = stk.getProcessor(imp.getStackIndex(channel, z, 1));
                    ImageProcessor sourceIP = stkTranslate.getProcessor(z-(int)zOffset);
                    for (int x = 0; x< width; x++){
                        for (int y = 0; y<height; y++){
                            targetIP.set(x, y, sourceIP.get(x, y));
                        }
                    }
                }
            }
            impTranslate.updateImage();
            impTranslate.close();
        }
    }

    public void autoAffineMultiThread(int refCh) {

    }

    public void iterativeMaskedTranslateMultiThread(ImagePlus impROI, ImagePlus mask, int refCh,
            double maxShift, double maxZ, double[][] lastShift) {
        // correlation is done in a smaller ROI (less of maxShift pixels from
        // each side) -- avoid correlating 0 values at the boundary due to transform
        Roi corROI = new Roi(maxShift, maxShift, impROI.getWidth() - 2 * maxShift, impROI.getHeight() - 2 * maxShift);
        Roi allROI = new Roi(0, 0, impROI.getWidth(), impROI.getHeight());

        // replicate just the reference channel
        // crop it down to corROI and sub-Zstack for cross-correlation
        impROI.setRoi(corROI, false);
        final ImagePlus reference
                = (new Duplicator()).run(impROI, refCh, refCh,
                        1 + (int) maxZ, impROI.getNSlices() - (int) maxZ,
                        1, impROI.getNFrames());

        // replicate the mask
        // crop it down to corROI and sub-Zstack for cross-correlation
        mask.setRoi(corROI, false);
        final ImagePlus corMask
                = (new Duplicator()).run(mask, 1, 1,
                        1 + (int) maxZ, mask.getNSlices() - (int) maxZ,
                        1, mask.getNFrames());
        // generate a double[] array to hold the masked refChImp pixel values for
        // computing correlation. Caution: double[0] holds the average value
        double[] refValues = getMaskedLinearArrayValues(reference, corMask);

        // set ROI back to replicate only the correlation channel
        // for transformation and then later crop down to corROI and sub-Zstack
        impROI.setRoi(allROI, false);

        for (int corCh = 1; corCh <= impROI.getNChannels(); corCh++) {
            if (corCh != refCh) {
                ImagePlus corChimp
                        = (new Duplicator()).run(impROI, corCh, corCh,
                                1, impROI.getNSlices(), 1, impROI.getNFrames());
                Image translate = Image.wrap(corChimp);

                while (Math.abs(lastShift[corCh][0]) < maxShift
                        & Math.abs(lastShift[corCh][1]) < maxShift
                        & Math.abs(lastShift[corCh][2]) < maxZ) {
                    double[] curShift = {0, 0, 0, 0};

                    // multi-threading on comparing correlations at different dz
                    int threadIndex = 0;
                    ArrayList<translateShiftCorrelationThread> threads = new ArrayList<>();
                    for (double dx = -1; dx <= 1; dx++) {
                        for (double dy = -1; dy <= 1; dy++) {
                            for (double dz = -1; dz <= 1; dz++) {
                                translateShiftCorrelationThread tscThread
                                        = new translateShiftCorrelationThread(translate,
                                                dx, dy, dz, lastShift, corCh, maxZ, corROI, impROI, corMask, refValues);
                                threads.add(tscThread);
                                threadIndex++;
                            }
                        }
                    }
                    threadIndex = 0;
//                        IJ.log("total threads = " + threads.size());
                    threads.stream().forEach((thread) -> {
                        thread.start();
                    });
                    threads.stream().forEach((thread) -> {
                        try {
                            thread.join();
                        } catch (Exception e) {
                        }
                    });
                    for (double dx = -1; dx <= 1; dx++) {
                        for (double dy = -1; dy <= 1; dy++) {
                            for (double dz = -1; dz <= 1; dz++) {
                                if (((translateShiftCorrelationThread) threads.get(threadIndex)).getCCValue() > curShift[3]) {
                                    curShift[0] = lastShift[corCh][0] + dx;
                                    curShift[1] = lastShift[corCh][1] + dy;
                                    curShift[2] = lastShift[corCh][2] + dz;
                                    curShift[3] = ((translateShiftCorrelationThread) threads.get(threadIndex)).getCCValue();
                                }
                                threadIndex++;
                            }
                        }
                    }
                    if (curShift[3] > lastShift[corCh][3]) {
                        lastShift[corCh][0] = ((double) (Math.round(curShift[0] * 100))) / 100;
                        lastShift[corCh][1] = ((double) (Math.round(curShift[1] * 100))) / 100;
                        lastShift[corCh][2] = ((double) (Math.round(curShift[2] * 100))) / 100;
                        lastShift[corCh][3] = curShift[3];
                    } else {
                        break;
                    }
                }

                corChimp.flush();
            }
        }
    }

    public void iterativeNoMaskTranslateMultiThread(ImagePlus patchedImg, int refCh,
            double maxXYshift, double maxZshift, double accuracy, double[][] lastShift) {
        // correlation is done in a patched reference image (add maxShift pixels in XYZ directions)
        // -- avoid correlating 0 values at the boundary due to transform

        // replicate just the reference channel
        // crop it down to corROI and sub-Zstack for cross-correlation
        Roi corROI = new Roi(maxXYshift, maxXYshift, patchedImg.getWidth() - 2 * maxXYshift, patchedImg.getHeight() - 2 * maxXYshift);
        patchedImg.setRoi(corROI, false);
        ImagePlus refChImp
                = (new Duplicator()).run(patchedImg, refCh, refCh,
                        1 + (int) maxZshift, patchedImg.getNSlices() - (int) maxZshift,
                        1, patchedImg.getNFrames());

        // generate a double[] array to hold the refChImp pixel values for
        // computing correlation. Caution: double[0] holds the average value
        for (int corCh = 1; corCh <= patchedImg.getNChannels(); corCh++) {
            if (corCh != refCh) {
                ImagePlus corChImp
                        = (new Duplicator()).run(patchedImg, corCh, corCh,
                                1, patchedImg.getNSlices(), 1, patchedImg.getNFrames());
                // iterate accuracy -- accuracy[0] = 1; accuracy[1] = 0.1
                double searchXYrange = maxXYshift / 2;
                double searchZrange = maxZshift;
                double[] curShift = {0, 0, 0, 0};

                // multi-threading on comparing correlations at different dz
                ArrayList<noMaskIterativeCorrelationThread> threads = new ArrayList<>();

                for (double dz = -searchZrange; dz <= searchZrange; dz += accuracy) {
                    noMaskIterativeCorrelationThread nmicThread
                            = new noMaskIterativeCorrelationThread(refChImp, corChImp,
                                    searchXYrange, accuracy, dz, lastShift, corCh, maxZshift, corROI);
                    threads.add(nmicThread);
                    nmicThread.start();
                }
                threads.stream().forEach((thread) -> {
                    try {
                        thread.join();
                    } catch (Exception e) {
                    }
                });
                for (noMaskIterativeCorrelationThread thread : threads) {
                    double[] ccValue = thread.getCCValue();
                    if (ccValue[3] > curShift[3]) {
                        curShift[0] = lastShift[corCh][0] + ccValue[0];
                        curShift[1] = lastShift[corCh][1] + ccValue[1];
                        curShift[2] = lastShift[corCh][2] + ccValue[2];
                        curShift[3] = ccValue[3];
                    }
                }

                if (curShift[3] > lastShift[corCh][3]) {
                    lastShift[corCh][0] = ((double) (Math.round(curShift[0] * 100))) / 100;
                    lastShift[corCh][1] = ((double) (Math.round(curShift[1] * 100))) / 100;
                    lastShift[corCh][2] = ((double) (Math.round(curShift[2] * 100))) / 100;
                    lastShift[corCh][3] = curShift[3];
                }
                corChImp.close();
            }
        }
    }

    public ImagePlus generateMask(ImagePlus imp, int maxShift) {
        ImagePlus tempImg = imp.duplicate();
        ImageStack tempStk = tempImg.getStack();
        int nFrames = tempImg.getNFrames();
        int nSlices = tempImg.getNSlices();
        int nChannels = tempImg.getNChannels();
        int width = tempImg.getWidth();
        int hight = tempImg.getHeight();
        ImagePlus maskImg = (new Duplicator()).run(tempImg, 1, 1, 1, nSlices, 1, nFrames);
        ImageStack maskStk = maskImg.getStack();
        int tempIndex, maskIndex;

        // generate autothreshold images and devide by 255
        for (int frame = 1; frame <= nFrames; frame++) {
            for (int slice = 1; slice <= nSlices; slice++) {
                ImageProcessor[] tempIP = new ImageProcessor[tempImg.getNChannels()];
                for (int channel = 1; channel <= nChannels; channel++) {
                    tempIndex = tempImg.getStackIndex(channel, slice, frame);
                    tempIP[channel - 1] = tempStk.getProcessor(tempIndex);
                    tempIP[channel - 1].autoThreshold();
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < hight; y++) {
                            tempIP[channel - 1].set(x, y, tempIP[channel - 1].get(x, y) / 255);
                        }
                    }
                }
            }
        }

        // generate mask stack that each mask is the common set of autothreshold
        // channel masks
        for (int frame = 1; frame <= nFrames; frame++) {
            for (int slice = 1; slice <= nSlices; slice++) {
                ImageProcessor[] tempIP = new ImageProcessor[tempImg.getNChannels()];
                // get threshold images from each channel
                for (int channel = 1; channel <= nChannels; channel++) {
                    tempIndex = tempImg.getStackIndex(channel, slice, frame);
                    tempIP[channel - 1] = tempStk.getProcessor(tempIndex);
                }
                // generate a common mask for all channels and put it into maskIP
                maskIndex = maskImg.getStackIndex(1, slice, frame);
                ImageProcessor maskIP = maskStk.getProcessor(maskIndex);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < hight; y++) {
                        int maskValue = 1;
                        for (int c = 0; c < nChannels; c++) {
                            maskValue = maskValue * tempIP[c].get(x, y);
                        }
                        maskIP.set(x, y, maskValue);
                    }
                }
            }
        }
        tempImg.flush();

        ImagePlus maskImgFuse = maskImg.duplicate();
        ImageStack maskStkFuse = maskImgFuse.getImageStack();
        for (int frame = 1; frame <= nFrames; frame++) {
            for (int slice = 1; slice <= nSlices; slice++) {
                // generate a common mask for all channels and put it into maskIP
                maskIndex = maskImg.getStackIndex(1, slice, frame);
                ImageProcessor maskIP = maskStk.getProcessor(maskIndex);
                ImageProcessor maskFuseIP = maskStkFuse.getProcessor(maskIndex);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < hight; y++) {
                        if (maskIP.get(x, y) == 1) {
                            for (int dx = x - maxShift; dx <= x + maxShift; dx++) {
                                for (int dy = y - maxShift; dy <= y + maxShift; dy++) {
                                    if (dx >= 0 && dx < width && dy >= 0 && dy < hight) {
                                        maskFuseIP.set(dx, dy, 1);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        maskImg.flush();

        maskImgFuse.getProcessor().setMinAndMax(0, 1);
        //maskImgFuse.show();
        return maskImgFuse;
    }

    public void applyMask(ImagePlus image, ImagePlus mask) {
        if (image.getWidth() == mask.getWidth()
                && image.getHeight() == mask.getHeight()
                && image.getNSlices() == mask.getNSlices()
                && image.getNFrames() == mask.getNFrames()) {
            ImageStack imageStk = image.getImageStack();
            ImageStack maskStk = mask.getImageStack();
            int imageIndex, maskIndex;

            for (int frame = 1; frame <= image.getNFrames(); frame++) {
                for (int slice = 1; slice <= image.getNSlices(); slice++) {
                    maskIndex = mask.getStackIndex(1, slice, frame);
                    ImageProcessor maskIP = maskStk.getProcessor(maskIndex);
                    ImageProcessor[] imageIP = new ImageProcessor[image.getNChannels()];
                    for (int channel = 1; channel <= image.getNChannels(); channel++) {
                        imageIndex = image.getStackIndex(channel, slice, frame);
                        imageIP[channel - 1] = imageStk.getProcessor(imageIndex);
                        for (int x = 0; x < image.getWidth(); x++) {
                            for (int y = 0; y < image.getHeight(); y++) {
                                imageIP[channel - 1].set(x, y, imageIP[channel - 1].get(x, y) * maskIP.get(x, y));
                            }
                        }
                    }

                }
            }
        } else {
            IJ.error("Image and Mask need to be in the same dimensions!");
        }
    }

    /////////////// Methods for stitching ////////////////////
    public ImagePlus getAllChannelRoiCropImg(ImagePlus imp, int maxXYshift, int maxZshift) {
        // return image is maxXYshift and maxZshift padded
        Roi pointRoi = imp.getRoi();
        int xMin = pointRoi.getBounds().x - maxXYshift > 0 ? pointRoi.getBounds().x - maxXYshift : 0;
        int xMax = xMin + maxXYshift * 2 < imp.getWidth() - 1 ? xMin + maxXYshift * 2 : imp.getWidth() - 1;
        int yMin = pointRoi.getBounds().y - maxXYshift > 0 ? pointRoi.getBounds().y - maxXYshift : 0;
        int yMax = yMin + maxXYshift * 2 < imp.getHeight() - 1 ? yMin + maxXYshift * 2 : imp.getHeight() - 1;
        int zMin = imp.getZ() - maxZshift > 1 ? imp.getZ() - maxZshift : 1;
        int zMax = zMin + maxZshift * 2 < imp.getNSlices() ? zMin + maxZshift * 2 : imp.getNSlices();
        Roi boxRoi = new Roi(xMin, yMin, xMax - xMin, yMax - yMin);
        imp.setRoi(boxRoi);
        ImagePlus impAlignment = (new Duplicator()).run(imp, 1, imp.getNChannels(), zMin, zMax, 1, 1);
        impAlignment.hide();
        impAlignment.setTitle("ROI aligned");
        imp.setRoi(pointRoi);
        
        // pad impAlignment 
        CanvasResizer cr = new CanvasResizer();
        impAlignment.setStack(cr.expandStack(impAlignment.getImageStack(), 
                impAlignment.getWidth() + maxXYshift * 2, impAlignment.getHeight() + maxXYshift * 2, maxXYshift, maxXYshift));
        for (int i = 0; i < maxZshift; i++) {
            IJ.run(impAlignment, "Add Slice", "add=slice prepend");
        }
        impAlignment.setZ(impAlignment.getNSlices());
        for (int i = 0; i < maxZshift; i++) {
            IJ.run(impAlignment, "Add Slice", "add=slice");
        }
        impAlignment.show();
        impAlignment.setZ(1);
        return impAlignment;
    }

    public ImagePlus getStackingRefAlgImg(ImagePlus imp, int refCh, int algCh,
            int maxXYshift, int maxZshift) {
        ImagePlus refImg = (new Duplicator()).run(imp, refCh, refCh, 1, imp.getNSlices(), 1, 1);
        ImagePlus algImg = (new Duplicator()).run(imp, algCh, algCh, 1, imp.getNSlices(), 1, 1);
        ImagePlus[] alignImages = {refImg, algImg};
        ImagePlus impWork = RGBStackMerge.mergeChannels(alignImages, false);
        refImg.close();
        algImg.close();
        return impWork;
    }
    
    public ImagePlus getStackingRefAlgImg(ImagePlus refImg, ImagePlus algImg,
            int maxXYshift, int maxZshift) {
        ImagePlus refImgChProj, algImgChProj;
        //int refImgChProjxMinBound = 0, refImgChProjxMaxBound = 0, refImgChProjyMinBound = 0, refImgChProjyMaxBound = 0, refImgChProjzMinBound = 0, refImgChProjzMaxBound = 0;
        //int algImgChProjxMinBound = 0, algImgChProjxMaxBound = 0, algImgChProjyMinBound = 0, algImgChProjyMaxBound = 0, algImgChProjzMinBound = 0, algImgChProjzMaxBound = 0;
        int refImgChProjBounds[] = getCropBounds (refImg, maxXYshift, maxZshift);
        int algImgChProjBounds[] = getCropBounds (algImg, maxXYshift, maxZshift);
        refImgChProj = projectChannel(refImg, refImg.getTitle() + "_channel projected", maxXYshift, maxZshift);     
        algImgChProj = projectChannel(algImg, algImg.getTitle() + "_channel projected", maxXYshift, maxZshift);
        //refImgChProj.show();
        //algImgChProj.show();
/*
        IJ.log("refImgChProj total z = "+refImgChProj.getNSlices());
        IJ.log("algImgChProj total z = "+algImgChProj.getNSlices());
        IJ.log("refImgChProjxMinBound = "+refImgChProjBounds[0]+", refImgChProjxMaxBound = "+ refImgChProjBounds[1]);
        IJ.log("refImgChProjyMinBound = "+refImgChProjBounds[2]+", refImgChProjyMaxBound = "+ refImgChProjBounds[3]);
        IJ.log("refImgChProjzMinBound = "+refImgChProjBounds[4]+", refImgChProjzMaxBound = "+ refImgChProjBounds[5]);
        IJ.log("algImgChProjxMinBound = "+algImgChProjBounds[0]+", algImgChProjxMaxBound = "+ algImgChProjBounds[1]);
        IJ.log("algImgChProjyMinBound = "+algImgChProjBounds[2]+", algImgChProjyMaxBound = "+ algImgChProjBounds[3]);
        IJ.log("algImgChProjzMinBound = "+algImgChProjBounds[4]+", algImgChProjzMaxBound = "+ algImgChProjBounds[5]);
*/
        int refImgChProjX = 0, refImgChProjY = 0, algImgChProjX = 0, algImgChProjY = 0;
        if (refImgChProjBounds[0] < algImgChProjBounds[0]){
            algImgChProjX = algImgChProjBounds[0] - refImgChProjBounds[0];
        } else {
            refImgChProjX = refImgChProjBounds[0] - algImgChProjBounds[0];
        }
        if (refImgChProjBounds[2] < algImgChProjBounds[2]){
            algImgChProjY = algImgChProjBounds[2] - refImgChProjBounds[2];
        } else {
            refImgChProjY = refImgChProjBounds[2] - algImgChProjBounds[2];
        }
        int xMinOffset = refImgChProjBounds[0] < algImgChProjBounds[0] ? refImgChProjBounds[0] : algImgChProjBounds[0];
        int xMaxOffset = refImgChProjBounds[1] < algImgChProjBounds[1] ? refImgChProjBounds[1] : algImgChProjBounds[1];
        int yMinOffset = refImgChProjBounds[2] < algImgChProjBounds[2] ? refImgChProjBounds[2] : algImgChProjBounds[2];
        int yMaxOffset = refImgChProjBounds[3] < algImgChProjBounds[3] ? refImgChProjBounds[3] : algImgChProjBounds[3];
        int minWidth = xMaxOffset + xMinOffset;
        int minHeight = yMaxOffset + yMinOffset;
        refImgChProj.setRoi(refImgChProjX, refImgChProjY, minWidth, minHeight);     
        algImgChProj.setRoi(algImgChProjX, algImgChProjY, minWidth, minHeight);
        //IJ.log("refImgChProj ("+refImgChProjX+ ", "+refImgChProjY+") "+minWidth+", "+minHeight+", "+refImgChProj.getNSlices());
        //IJ.log("algImgChProj ("+algImgChProjX+ ", "+algImgChProjY+") "+minWidth+", "+minHeight+", "+algImgChProj.getNSlices());
        IJ.run(refImgChProj, "Crop", "");
        IJ.run(algImgChProj, "Crop", "");
        //refImgChProj.show();
        //refImgChProj.changes = false;
        //algImgChProj.show();
        //algImgChProj.changes = false;
        for (int i = 0; i < algImgChProjBounds[4]-refImgChProjBounds[4]; i++) {
            refImgChProj.setZ(1);
            IJ.run(refImgChProj, "Add Slice", "add=slice prepend");
        }
        for (int i = 0; i < algImgChProjBounds[5]-refImgChProjBounds[5]; i++) {
            refImgChProj.setZ(refImgChProj.getNSlices());
            IJ.run(refImgChProj, "Add Slice", "add=slice");
        }
        for (int i = 0; i < refImgChProjBounds[4]- algImgChProjBounds[4]; i++) {
            algImgChProj.setZ(1);
            IJ.run(algImgChProj, "Add Slice", "add=slice prepend");
        }
        for (int i = 0; i < refImgChProjBounds[5]- algImgChProjBounds[5]; i++) {
            algImgChProj.setZ(algImgChProj.getNSlices());
            IJ.run(algImgChProj, "Add Slice", "add=slice");
        }
        ImagePlus[] alignImages = {refImgChProj, algImgChProj};
        //IJ.log("ok");
        ImagePlus impWork = RGBStackMerge.mergeChannels(alignImages, false);
        //impWork.show();
        CanvasResizer cr = new CanvasResizer();
        impWork.setStack(cr.expandStack(impWork.getImageStack(), impWork.getWidth() + maxXYshift * 2, impWork.getHeight() + maxXYshift * 2, maxXYshift, maxXYshift));
        for (int i = 0; i < maxZshift; i++) {
            impWork.setZ(1);
            IJ.run(impWork, "Add Slice", "add=slice prepend");
        }
        for (int i = 0; i < maxZshift; i++) {
            impWork.setZ(impWork.getNSlices());
            IJ.run(impWork, "Add Slice", "add=slice");
        }
        impWork.setZ(impWork.getNSlices()/2);
        impWork.changes = false;
        return impWork;
    }
    public int[] getCropBounds (ImagePlus imp, int maxXYshift, int maxZshift){
        Roi pointRoi = imp.getRoi();
        int xMin = pointRoi.getBounds().x - maxXYshift > 0 ? pointRoi.getBounds().x - maxXYshift : 0;
        int xMax = pointRoi.getBounds().x + maxXYshift < imp.getWidth() - 1 ? pointRoi.getBounds().x + maxXYshift : imp.getWidth() - 1;
        int yMin = pointRoi.getBounds().y - maxXYshift > 0 ? pointRoi.getBounds().y - maxXYshift : 0;
        int yMax = pointRoi.getBounds().y + maxXYshift < imp.getHeight() - 1 ? pointRoi.getBounds().y + maxXYshift : imp.getHeight() - 1;
        int zMin = imp.getZ() - maxZshift > 1 ? imp.getZ() - maxZshift : 1;
        int zMax = imp.getZ() + maxZshift < imp.getNSlices() ? imp.getZ() + maxZshift : imp.getNSlices();
        int[] bounds = new int[6];
        bounds[0] = pointRoi.getBounds().x - xMin;  //xMinBound
        bounds[1] = xMax - pointRoi.getBounds().x;  //xMaxBound
        bounds[2] = pointRoi.getBounds().y - yMin;  //yMinBound
        bounds[3] = yMax - pointRoi.getBounds().y;  //yMaxBound
        bounds[4] = imp.getSlice() - zMin;          //zMinBound
        bounds[5] = zMax - imp.getSlice();          //zMaxBound
        return bounds;
    }
    public ImagePlus projectChannel(ImagePlus imp, String title, int maxXYshift, int maxZshift) {
        Roi pointRoi = imp.getRoi();
        int xMin = pointRoi.getBounds().x - maxXYshift > 0 ? pointRoi.getBounds().x - maxXYshift : 0;
        int xMax = pointRoi.getBounds().x + maxXYshift < imp.getWidth() - 1 ? pointRoi.getBounds().x + maxXYshift : imp.getWidth() - 1;
        int yMin = pointRoi.getBounds().y - maxXYshift > 0 ? pointRoi.getBounds().y - maxXYshift : 0;
        int yMax = pointRoi.getBounds().y + maxXYshift < imp.getHeight() - 1 ? pointRoi.getBounds().y + maxXYshift : imp.getHeight() - 1;
        int zMin = imp.getZ() - maxZshift > 1 ? imp.getZ() - maxZshift : 1;
        int zMax = imp.getZ() + maxZshift < imp.getNSlices() ? imp.getZ() + maxZshift : imp.getNSlices();
        Roi cropRoi = new Roi(xMin, yMin, xMax - xMin +1 , yMax - yMin+1);
        //IJ.log(title+" x="+xMin+"-"+xMax+", y="+yMin+"-"+yMax+", z="+zMin+"-"+zMax);
        ZProjector maxProjector = new ZProjector();
        maxProjector.setMethod(ZProjector.MAX_METHOD);
        ImageStack stk = imp.getImageStack();
        ImageStack stkProj = new ImageStack(xMax - xMin +1, yMax - yMin+1, zMax - zMin + 1);
        ImageStack stkTemp = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getNChannels());
        ImagePlus impTemp = new ImagePlus();
        for (int z = zMin; z <= zMax; z++) {
            for (int c = 1; c <= imp.getNChannels(); c++) {
                ImageProcessor ipTemp = stk.getProcessor(imp.getStackIndex(c, z, 1));
                stkTemp.setProcessor(ipTemp, c);
            }
            impTemp.setStack(stkTemp);
            maxProjector.setImage(impTemp);
            maxProjector.doProjection();
            ImageProcessor ipProj = maxProjector.getProjection().getProcessor();
            ipProj.setRoi(cropRoi);
            ImageProcessor ipSet = ipProj.crop();
            stkProj.setProcessor(ipSet, z - zMin + 1);
            //IJ.log("set z = "+(z - zMin + 1));
        }
        return new ImagePlus(title, stkProj);
    }

    public void stitchHorizontalMultiThread(ImagePlus refImg, ImagePlus algImg,
            int xOffset, int yOffset, int zOffset) {
        //int totalChannel = imp.getNChannels();
        int refImgWidth = refImg.getWidth();
        int refImgHeight = refImg.getHeight();
        int refImgDepth = refImg.getNSlices();
        int algImgWidth = algImg.getWidth();
        int algImgHeight = algImg.getHeight();
        int algImgDepth = algImg.getNSlices();
        int stitchWidth = refImgWidth >= algImgWidth + xOffset ? refImgWidth : algImgWidth + xOffset;
        int stitchHeight, stitchDepth;

        // expand refChImp to position it in the final stitched stack
        // take care of X
        if (stitchWidth > refImgWidth) { // xOffset always >= 0
            IJ.run(refImg, "Canvas Size...", "width=" + stitchWidth + " height=" + refImgHeight + " position=Center-Left zero");
        }
        // take care of Y
        if (yOffset <= 0) {
            stitchHeight = refImgHeight - yOffset;
            IJ.run(refImg, "Canvas Size...", "width=" + stitchWidth + " height=" + stitchHeight + " position=Bottom-Center zero");
            if (algImgHeight > stitchHeight) {
                stitchHeight = algImgHeight;
                IJ.run(refImg, "Canvas Size...", "width=" + stitchWidth + " height=" + stitchHeight + " position=Top-Center zero");
            }
        } else {
            stitchHeight = algImgHeight + yOffset;
            if (stitchHeight > refImgHeight) {
                IJ.run(refImg, "Canvas Size...", "width=" + stitchWidth + " height=" + stitchHeight + " position=Top-Center zero");
            }
        }
        // take care of Z
        if (zOffset < 0) {
            stitchDepth = refImgDepth - zOffset;
            for (int i = refImgDepth; i < stitchDepth; i++) {
                refImg.setPositionWithoutUpdate(1, 1, 1);
                IJ.run(refImg, "Add Slice", "add=slice prepend");
            }
            if (algImgDepth > stitchDepth) {
                for (int i = stitchDepth; i < algImgDepth; i++) {
                    refImg.setPositionWithoutUpdate(1, i, 1);
                    IJ.run(refImg, "Add Slice", "add=slice");
                }
                stitchDepth = algImgDepth;
            }
        } else {
            stitchDepth = algImgDepth + zOffset;
            for (int i = refImgDepth; i < stitchDepth; i++) {
                refImg.setPositionWithoutUpdate(1, i, 1);
                IJ.run(refImg, "Add Slice", "add=slice");
            }
        }
        refImg.setSlice(1);
        //IJ.log("stichWidth = " + stitchWidth + "; stitchHeight = " + stitchHeight+"; stichDepth = " + stitchDepth);

        // fuse image
        ImageStack refStack = refImg.getImageStack();
        //ImageStack algStack = algImg.getImageStack();
        int minX = xOffset;
        int maxX = refImgWidth <= xOffset + algImgWidth ? refImgWidth : xOffset + algImgWidth;
        int xWidth = maxX - minX;
        int xStitchOffset = xOffset <= 0 ? 0 : xOffset;
        int yStitchOffset = yOffset <= 0 ? 0 : yOffset;
        int zStitchOffset = zOffset <= 0 ? 0 : zOffset;

        int cores = Runtime.getRuntime().availableProcessors();
        int increment = (int) Math.floor((double)algImgDepth / (double)cores)+1;
        ArrayList<stitchHorizontalThread> threads = new ArrayList<>();
        for (int core = 0; core < cores; core++) {
            stitchHorizontalThread shThread
                    = new stitchHorizontalThread(refImg, algImg,
                            xWidth, xStitchOffset, yStitchOffset, zStitchOffset,
                            core, increment);
            threads.add(shThread);
            shThread.start();
        }
        threads.stream().forEach((thread) -> {
            try {
                thread.join();
            } catch (Exception e) {
            }
        });

        refImg.setStack(refImg.getShortTitle() + "<>" + algImg.getShortTitle() + "_Fused", refStack);
        refImg.updateAndDraw();
        algImg.close();
    }

    public void stitchVerticalMultiThread(ImagePlus refImg, ImagePlus algImg,
            int xOffset, int yOffset, int zOffset) {
        int refImgWidth = refImg.getWidth();
        int refImgHeight = refImg.getHeight();
        int refImgDepth = refImg.getNSlices();
        int algImgWidth = algImg.getWidth();
        int algImgHeight = algImg.getHeight();
        int algImgDepth = algImg.getNSlices();
        int stitchHeight = refImgHeight >= algImgHeight + yOffset ? refImgHeight : algImgHeight + yOffset;
        int stitchWidth, stitchDepth;

        // position refChImp in stitched stack
        // take care of Y
        if (stitchHeight > refImgHeight) { // yOffset always >= 0
            IJ.run(refImg, "Canvas Size...", "width=" + refImgWidth + " height=" + stitchHeight + " position=Top-Center zero");
        }
        // take care of X
        if (xOffset < 0) {
            stitchWidth = refImgWidth - xOffset;
            IJ.run(refImg, "Canvas Size...", "width=" + stitchWidth + " height=" + stitchHeight + " position=Center-Right zero");
            if (algImgWidth > stitchWidth) {
                stitchWidth = algImgWidth;
                IJ.run(refImg, "Canvas Size...", "width=" + stitchWidth + " height=" + stitchHeight + " position=Center-Left zero");
            }
        } else {
            stitchWidth = refImgWidth >= xOffset + algImgWidth ? refImgWidth : xOffset + algImgWidth;
            if (stitchWidth > refImgWidth) {
                IJ.run(refImg, "Canvas Size...", "width=" + stitchWidth + " height=" + stitchHeight + " position=Center-Left zero");
            }
        }

        // take care of Z
        if (zOffset < 0) {
            stitchDepth = refImgDepth - zOffset;
            for (int i = refImgDepth; i < stitchDepth; i++) {
                refImg.setPositionWithoutUpdate(1, 1, 1);
                IJ.run(refImg, "Add Slice", "add=slice prepend");
            }
            if (algImgDepth > stitchDepth) {
                for (int i = stitchDepth; i < algImgDepth; i++) {
                    refImg.setPositionWithoutUpdate(1, i, 1);
                    IJ.run(refImg, "Add Slice", "add=slice");
                }
                stitchDepth = algImgDepth;
            }
        } else {
            stitchDepth = algImgDepth + zOffset;
            for (int i = refImgDepth; i < stitchDepth; i++) {
                refImg.setPositionWithoutUpdate(1, i, 1);
                IJ.run(refImg, "Add Slice", "add=slice");
            }
        }
        refImg.setSlice(1);
        //IJ.log("stichWidth = " + stitchWidth + "; stitchHeight = " + stitchHeight+"; stichDepth = " + stitchDepth);

        // fuse vertical image
        ImageStack refStack = refImg.getImageStack();
        int minY = yOffset;
        int maxY = refImgHeight <= yOffset + algImgHeight ? refImgHeight : yOffset + algImgHeight;
        int yWidth = maxY - minY;
        int xStitchOffset = xOffset <= 0 ? 0 : xOffset;
        int yStitchOffset = yOffset <= 0 ? 0 : yOffset;
        int zStitchOffset = zOffset <= 0 ? 0 : zOffset;        

        int cores = Runtime.getRuntime().availableProcessors();
        int increment = (int) Math.floor((double)algImgDepth / (double)cores)+1;
        ArrayList<stitchVerticalThread> threads = new ArrayList<>();
        for (int core = 0; core < cores; core++) {
            stitchVerticalThread svThread = new stitchVerticalThread(
                    refImg, algImg, yWidth, xStitchOffset, yStitchOffset, zStitchOffset, core, increment);
            threads.add(svThread);
            svThread.start();
        }
        threads.stream().forEach((thread) -> {
            try {
                thread.join();
            } catch (Exception e) {
            }
        });

        refImg.setStack(refImg.getShortTitle() + "<>" + algImg.getShortTitle() + "_Fused", refStack);
        refImg.updateAndDraw();
        algImg.close();
    }

    private float getNonLinearFusionWeight(int x, int d) {
        return (float) x / (float) d;
    }

    // private parameters
    private final AMtranslate translator = new AMtranslate();
}
