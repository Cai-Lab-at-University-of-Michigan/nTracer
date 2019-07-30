/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

/**
 *
 * @author loganaw
 *
 * This combines two plugin implementations to reduce data copies when using
 * both in tandem: - https://imagej.nih.gov/ij/source/ij/plugin/Duplicator.java
 * - https://github.com/imagej/imagej1/blob/master/ij/plugin/ZProjector.java
 *
 */
import java.awt.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;


public class DuplicateProjector {

    public static ImagePlus duplicateAndProject(ImagePlus imp, int firstC, int lastC, int firstZ, int lastZ) {

        // !!!!
        //  Code from Duplicator.run(__) method:
        // !!!!
        Rectangle rect = null;
        Roi roi = imp.getRoi();
        Roi roi2 = cropRoi(imp, roi);

        if (roi2 != null && roi2.isArea()) {
            rect = roi2.getBounds();
        } else {
            rect = new Rectangle( imp.getWidth(), imp.getHeight() );
        }

        ImageStack stack = imp.getStack();
        ImageStack stack2 = new ImageStack(rect.width, rect.height);
        
        for (int c = firstC; c <= lastC; c++) {
            float[] projection = new float[rect.width * rect.height];
            Arrays.fill(projection, Float.MIN_VALUE);

            for (int z = firstZ; z <= lastZ; z++) {
                int frame_n = imp.getStackIndex(c, z, 1);
                
                long startTime = System.nanoTime();
                ImageProcessor ip = stack.getProcessor(frame_n);
                ip.setRoi(rect);
                
                long endTime = System.nanoTime();
		long timeElapsed = endTime - startTime;
		System.out.println("Load time in nanoseconds  : " + timeElapsed);
                
                startTime = System.nanoTime();
                ip = ip.crop();
                
                endTime = System.nanoTime();
		timeElapsed = endTime - startTime;
		System.out.println("Crop Execution time in nanoseconds  : " + timeElapsed);
                             
                startTime = System.nanoTime();
                Object frame_object = ip.getPixels();
                
                endTime = System.nanoTime();
		timeElapsed = endTime - startTime;
		System.out.println("Execution time in nanoseconds  : " + timeElapsed);
                
                startTime = System.nanoTime();
                
                if (frame_object instanceof byte[]) {
                    byte[] frame_data = (byte[]) frame_object;

                    for (int j = 0; j < rect.height; j++) {
                        for (int i = 0; i < rect.width; i++) { // loop over width
                            final int frame_coord = j * rect.width + i;
                            final int projection_coord = j * rect.width + i;
                            
                            if (frame_data[frame_coord] > projection[projection_coord]) {
                                projection[projection_coord] = frame_data[frame_coord];
                            }
                        }
                    }
                }
                
                endTime = System.nanoTime();
		timeElapsed = endTime - startTime;
		System.out.println("Project Execution time in nanoseconds  : " + timeElapsed);
        
                /*
                for (int j = 0; j < frame_data[0].length; j++) {
                        for (int i = 0; i < frame_data.length; i++) { // loop over width
                            if (frame_data[i][j] > projection[j * rect.width + i]) {
                                projection[j * rect.width + i] = frame_data[i][j];
                            }
                        }
                }*/

            }

            FloatProcessor fp = new FloatProcessor(rect.width, rect.height, projection);
            stack2.addSlice("", fp);
        }

        ImagePlus imp2 = imp.createImagePlus();
        imp2.setStack("DUP_" + imp.getTitle(), stack2);
        imp2.setDimensions(lastC - firstC + 1, 1, 1);
        if (imp.isComposite()) {
            int mode = ((CompositeImage) imp).getMode();
            if (lastC > firstC) {
                imp2 = new CompositeImage(imp2, mode);
                int i2 = 1;
                for (int i = firstC; i <= lastC; i++) {
                    LUT lut = ((CompositeImage) imp).getChannelLut(i);
                    ((CompositeImage) imp2).setChannelLut(lut, i2++);
                }
            } else if (firstC == lastC) {
                LUT lut = ((CompositeImage) imp).getChannelLut(firstC);
                imp2.getProcessor().setColorModel(lut);
                imp2.setDisplayRange(lut.min, lut.max);
            }
        }

        imp2.setOpenAsHyperStack(true);
        Calibration cal = imp2.getCalibration();
        if (roi != null && (cal.xOrigin != 0.0 || cal.yOrigin != 0.0)) {
            cal.xOrigin -= roi.getBounds().x;
            cal.yOrigin -= roi.getBounds().y;
        }

        Overlay overlay = imp.getOverlay();
        if (overlay != null && !imp.getHideOverlay()) {
            Overlay overlay2 = overlay.crop(roi2 != null ? roi2.getBounds() : null);
            overlay2.crop(firstC, lastC, 1, 1, 1, 1);
            imp2.setOverlay(overlay2);
        }

        return imp2;

        //return null;
    }

    /*
	* Returns the part of 'roi' overlaping 'imp'
	* Author Marcel Boeglin 2013.12.15
     */
    public static Roi cropRoi(ImagePlus imp, Roi roi) {
        if (roi == null) {
            return null;
        }
        if (imp == null) {
            return roi;
        }
        Rectangle b = roi.getBounds();
        int w = imp.getWidth();
        int h = imp.getHeight();
        if (b.x < 0 || b.y < 0 || b.x + b.width > w || b.y + b.height > h) {
            ShapeRoi shape1 = new ShapeRoi(roi);
            ShapeRoi shape2 = new ShapeRoi(new Roi(0, 0, w, h));
            roi = shape2.and(shape1);
        }

        if (roi.getBounds().width == 0 || roi.getBounds().height == 0) {
            throw new IllegalArgumentException("Selection is outside the image");
        }

        return roi;
    }

    public static Overlay cropOverlay(Overlay overlay, Rectangle bounds) {
        return overlay.crop(bounds);
    }

}
