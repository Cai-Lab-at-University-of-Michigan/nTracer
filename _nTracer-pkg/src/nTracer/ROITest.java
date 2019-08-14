package nTracer;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.*;
import ij.plugin.PlugIn;
import java.awt.Color;
import java.io.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ROITest implements PlugIn {

    /* Default constructor. */
    public ROITest() {

    }

    @Override
    public void run(String arg) {
        if (WindowManager.getCurrentImage() == null) {
            IJ.open();
        }
        ImagePlus imp = WindowManager.getCurrentImage();

        OpenDialog od = new OpenDialog("Select Points File", arg);
        String name = od.getFileName();
        String dir = od.getDirectory();

        if (name == null || name.length() == 0) {
            return;
        }

        List<SynapsePoint> list = new ArrayList<>();
        File file = new File( dir + name );
        System.out.println( "Loading points from: " + dir + name );
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(file));
            String text;

            while ((text = reader.readLine()) != null) {
                //list.add(Integer.parseInt(text));
                String[] parts = text.split("\t");
                
                int x = parseIntClean( parts[0] );
                int y = parseIntClean( parts[1] );
                int z = (int) Math.round( Float.valueOf( parts[2] ) );
                float r = Float.valueOf( parts[3] );
                
                list.add( new SynapsePoint( x, y, z, r ) );
            }
        } catch (FileNotFoundException ex) {
            //Logger.getLogger(ROITest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            //Logger.getLogger(ROITest.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
            }
        }

        System.out.println(list.size());

        Overlay over = imp.getOverlay();
        if (over == null) {
            over = new Overlay();
        }

        Color nextcolor = Color.red;
        if (over.size() > 0) {
            nextcolor = Color.green;
        }

        int current_z = imp.getZ();
        for (SynapsePoint p : list) {
            float r = p.getR();
            Roi toadd = new OvalRoi(p.getY() - r, p.getX() - r, r * 2, r * 2);
            toadd.setPosition( 1, p.getZ(), 1 );
            toadd.setStrokeColor(nextcolor);
            over.add(toadd);
        }

        imp.setOverlay(over);
        imp.updateAndDraw();
        
        
    }
    
    private int parseIntClean( String x ) { 
        double temp = Double.parseDouble( x );
        temp = Math.round( temp );
        
        return (int) temp;
    }

    private class SynapsePoint {

        int x, y, z;
        float r;

        public SynapsePoint(int x, int y, int z, float r) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public int getZ() {
            return this.z;
        }

        public float getR() {
            return this.r;
        }

        public void rescaleZ(float factor) {
            this.z /= factor;
        }
    }

}
