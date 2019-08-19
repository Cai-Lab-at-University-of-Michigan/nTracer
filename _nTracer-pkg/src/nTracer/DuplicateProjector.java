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
 * both in tandem:
 * - https://imagej.nih.gov/ij/source/ij/plugin/Duplicator.java
 * - https://github.com/imagej/imagej1/blob/master/ij/plugin/ZProjector.java
 *
 */
import java.awt.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.util.ArrayList;


public class DuplicateProjector {

    public static ImagePlus duplicateAndProject(ImagePlus imp, ImagePlus impZproj, int firstC, int lastC, int firstZ, int lastZ, Roi roi) {
        final Roi roi2 = cropRoi(imp, roi);

        Rectangle rect_build;
        if (roi2 != null && roi2.isArea()) {
            rect_build = roi2.getBounds();
        } else {
            rect_build = new Rectangle( imp.getWidth(), imp.getHeight() );
        }
        
        final Rectangle rect = rect_build;

        ImageStack stack = imp.getStack();
        ImageStack stack2 = new ImageStack(rect.width, rect.height);
        
        ArrayList<ImageProcessor> channel_frames = new ArrayList<>();
        for( int c = firstC; c <= lastC; c++ ) channel_frames.add( null );
        
        ArrayList<Thread> threads = new ArrayList<>();
        for (int c = firstC; c <= lastC; c++) {
            ProjectThread t = new ProjectThread(channel_frames, rect, c, firstZ, lastZ, imp, stack, firstC);
            Thread tt = new Thread(t);
            threads.add( tt );
            tt.start();
        }
        
        for( Thread t : threads ) try {
            t.join();
        } catch (InterruptedException ex) {
            return null;
        }
        
        channel_frames.stream().forEach( fp -> stack2.addSlice("", fp ) );

        ImagePlus imp2 = imp.createImagePlus();
        imp2.setStack("DUP_" + imp.getTitle(), stack2);
        imp2.setDimensions(lastC - firstC + 1, 1, 1);
        if (imp.isComposite()) {
            int mode = ((CompositeImage) impZproj).getMode();
            if (lastC > firstC) {
                imp2 = new CompositeImage(imp2, mode);
                int i2 = 1;
                for (int i = firstC; i <= lastC; i++) {
                    LUT lut = ((CompositeImage) imp).getChannelLut(i);
                    ((CompositeImage) imp2).setChannelLut(lut, i2++);
                }
            } else if (firstC == lastC) {
                LUT lut = ((CompositeImage) impZproj).getChannelLut(firstC);
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

        // I think this section copies the old overlay over, which we don't want -LAW
        //Overlay overlay = imp.getOverlay();
        //if (overlay != null && !imp.getHideOverlay()) {
        //    Overlay overlay2 = overlay.crop(roi2 != null ? roi2.getBounds() : null);
        //    overlay2.crop(firstC, lastC, 1, 1, 1, 1);
        //    imp2.setOverlay(overlay2);
        //}

        return imp2;
    }
    
    public static void calcProjection(ArrayList<ImageProcessor> channel_frames, Rectangle rect, int c, int firstZ, int lastZ, ImagePlus imp, ImageStack stack, int firstC ){//, Rectangle rect) {
        float[] projection = new float[rect.width * rect.height];
        //Arrays.fill( projection, new Integer(0) ); //Float.MIN_VALUE);
        
        for( int i = 0; i < projection.length; i++)
            projection[i] = 0;

        for (int z = firstZ; z <= lastZ; z++) {
            int frame_n = imp.getStackIndex(c, z, 1);

            ImageProcessor ip = stack.getProcessor(frame_n);
            ip.setRoi(rect);

            ip = ip.crop();
            Object frame_object = ip.getPixels();

            if (frame_object instanceof byte[]) {
                byte[] frame_data = (byte[]) frame_object;

                for (int j = 0; j < rect.height; j++) {
                    for (int i = 0; i < rect.width; i++) { // loop over width
                        final int frame_coord = j * rect.width + i;
                        final int projection_coord = j * rect.width + i;

                        if ( (frame_data[frame_coord] & 0xff) > projection[projection_coord]) {
                            projection[projection_coord] = frame_data[frame_coord];
                        }
                    }
                }
            } else if (frame_object instanceof short[]) {
                short[] frame_data = (short[]) frame_object;

                for (int j = 0; j < rect.height; j++) {
                    for (int i = 0; i < rect.width; i++) { // loop over width
                        final int frame_coord = j * rect.width + i;
                        final int projection_coord = j * rect.width + i;

                        if ( (frame_data[frame_coord] & 0xffff) > projection[projection_coord]) {
                            projection[projection_coord] = frame_data[frame_coord]&0xffff;
                        }
                    }
                }
            } else if (frame_object instanceof float[]) {
                float[] frame_data = (float[]) frame_object;

                for (int j = 0; j < rect.height; j++) {
                    for (int i = 0; i < rect.width; i++) { // loop over width
                        final int frame_coord = j * rect.width + i;
                        final int projection_coord = j * rect.width + i;

                        if (frame_data[frame_coord] > projection[projection_coord] * 2) {
                            projection[projection_coord] = (frame_data[frame_coord] * 2);
                        }
                    }
                }
            }
        }

        FloatProcessor fp = new FloatProcessor(rect.width, rect.height, projection);
        channel_frames.set(c - firstC, fp);
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

    private static class ProjectThread implements Runnable {
        private final Rectangle rect;
        private final int c, firstZ, lastZ, firstC;
        private final ImagePlus imp;
        private final ImageStack stack;
        private final ArrayList<ImageProcessor> channel_frames;
        
        ProjectThread( ArrayList<ImageProcessor> channel_frames, Rectangle rect, int c, int firstZ, int lastZ, ImagePlus imp, ImageStack stack, int firstC ) {
            this.channel_frames = channel_frames;
            this.imp = imp;
            this.rect = rect;
            this.firstZ = firstZ;
            this.lastZ = lastZ;
            this.firstC = firstC;
            this.c = c;
            this.stack = stack;
        }

        @Override
        public void run() {
            //System.err.println("thread " + c + " is running...");
            calcProjection(channel_frames, rect, c, firstZ, lastZ, imp, stack, firstC);
        }
}  

}
