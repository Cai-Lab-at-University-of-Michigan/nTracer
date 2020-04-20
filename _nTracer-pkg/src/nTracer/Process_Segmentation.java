package nTracer;

/**
 * Image processing plugin that distinguishes particles from one another in a
 * 16-bit image stack by filtering (Gaussian), thresholding, and flood-filling
 * the given image stack. Output summary can be saved as .csv file.
 *
 * @author Sara Azzouz
 * @since 01/13/2020
 */
import java.awt.*;
import java.awt.event.*;

import ij.*;
import ij.plugin.frame.*;
import ij.process.*;
import ij.gui.*;
import static ij.plugin.ChannelSplitter.split;

import java.io.*;
import java.util.*;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Process_Segmentation extends PlugInDialog implements
        AdjustmentListener, ActionListener {

    protected ImagePlus imp;
    protected ImageStack stack;
    protected ImageProcessor[] ip;
    private int width;
    private int height;
    private int length;
    private int currentChannel;
    private int currentZPosition;
    private int threshold;
    private int diameter;
    private double stdev;
    private static String filepath;
    
    HistPlot plot = new HistPlot();
    int sliderRange = 256;

    Panel panel;
    Button applyB;
    ImageJ ij;
    double min, max;
    Scrollbar thresholdS, stdevS, diameterS;
    Label minLabel, maxLabel, thresholdL, stdevL, diameterL;
    boolean done;
    GridBagLayout gridbag;
    GridBagConstraints c;
    int y = 0;
    Font monoFont = new Font("Monospaced", Font.PLAIN, 11);
    Font sanFont = ImageJ.SansSerif12;
    private String blankMinLabel = "-------";
    private String blankMaxLabel = "--------";
    private double scale = Prefs.getGuiScale();

    public Process_Segmentation() {
        super("Process_Segmentation");
    }

    @Override
    public void run(String arg) {
        setTitle("Process_Segmentation");
        IJ.register(Process_Segmentation.class);
        WindowManager.addWindow(this);

        ij = IJ.getInstance();
        gridbag = new GridBagLayout();
        c = new GridBagConstraints();
        setLayout(gridbag);
        if (scale > 1.0) {
            sanFont = sanFont.deriveFont((float) (sanFont.getSize() * scale));
            monoFont = monoFont.deriveFont((float) (monoFont.getSize() * scale));
        }

        // plot
        c.gridx = 0;
        y = 0;
        c.gridy = y++;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(10, 10, 0, 10);
        gridbag.setConstraints(plot, c);
        add(plot);
        plot.addKeyListener(ij);

        // min and max labels
        panel = new Panel();
        c.gridy = y++;
        c.insets = new Insets(0, 10, 0, 10);
        gridbag.setConstraints(panel, c);
        panel.setLayout(new BorderLayout());
        minLabel = new Label(blankMinLabel, Label.LEFT);
        minLabel.setFont(monoFont);
        panel.add("West", minLabel);
        maxLabel = new Label(blankMaxLabel, Label.RIGHT);
        maxLabel.setFont(monoFont);
        panel.add("East", maxLabel);
        add(panel);
        blankMinLabel = "       ";
        blankMaxLabel = "        ";

        // threshold slider
        threshold = 0;
        thresholdS = new Scrollbar(Scrollbar.HORIZONTAL, threshold, 1, 0, 256);
        c.gridy = y++;
        c.insets = new Insets(2, 10, 0, 10);
        gridbag.setConstraints(thresholdS, c);
        add(thresholdS);
        thresholdS.addAdjustmentListener(this);
        thresholdS.addKeyListener(ij);
        thresholdS.setUnitIncrement(1);
        thresholdS.setFocusable(false);
        addLabel("Threshold", null);

        // standard deviation slider
        stdev = 26 / 2;
        stdevS = new Scrollbar(Scrollbar.HORIZONTAL, 13, 1, 0, 26);
        c.gridy = y++;
        c.insets = new Insets(2, 10, 0, 10);
        gridbag.setConstraints(stdevS, c);
        add(stdevS);
        stdevS.addAdjustmentListener(this);
        stdevS.addKeyListener(ij);
        stdevS.setUnitIncrement(1);
        stdevS.setFocusable(false);
        addLabel("Standard Deviation", null);

        // diameter slider
        diameter = 3;
        diameterS = new Scrollbar(Scrollbar.HORIZONTAL, diameter, 1, 1, 5);
        c.gridy = y++;
        c.insets = new Insets(2, 10, 0, 10);
        gridbag.setConstraints(diameterS, c);
        add(diameterS);
        diameterS.addAdjustmentListener(this);
        diameterS.addKeyListener(ij);
        diameterS.setUnitIncrement(1);
        diameterS.setFocusable(false);
        addLabel("Diameter", null);

        // buttons
        if (scale > 1.0) {
            Font font = getFont();
            if (font != null) {
                font = font.deriveFont((float) (font.getSize() * scale));
            } else {
                font = new Font("SansSerif", Font.PLAIN, (int) (12 * scale));
            }
            setFont(font);
        }
        int trim = IJ.isMacOSX() ? 20 : 0;
        panel = new Panel();
        panel.setLayout(new BorderLayout());
        applyB = new TrimmedButton("Apply", trim);
        applyB.addActionListener(this);
        applyB.addKeyListener(ij);
        panel.add(applyB);
        c.gridy = y++;
        c.insets = new Insets(8, 5, 10, 5);
        gridbag.setConstraints(panel, c);
        add(panel);

        addKeyListener(ij);
        pack();
        if (IJ.isMacOSX()) {
            setResizable(false);
        }
        setVisible(true);
        setup();
    }

    void addLabel(String text, Label label2) {
        if (label2 == null && IJ.isMacOSX()) {
            text += "    ";
        }
        panel = new Panel();
        c.gridy = y++;
        int bottomInset = IJ.isMacOSX() ? 4 : 0;
        c.insets = new Insets(0, 10, bottomInset, 0);
        gridbag.setConstraints(panel, c);
        panel.setLayout(new FlowLayout(label2 == null ? FlowLayout.CENTER : FlowLayout.LEFT, 0, 0));
        Label label = new TrimmedLabel(text);
        label.setFont(sanFont);
        panel.add(label);
        if (label2 != null) {
            label2.setFont(monoFont);
            label2.setAlignment(Label.LEFT);
            panel.add(label2);
        }
        add(panel);
    }

    void setup() {
        imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.beep();
            IJ.showMessage("No image");
            IJ.showStatus("No image");
            return;
        }
        else if (imp.getType() != ImagePlus.GRAY16) {
            IJ.beep();
            IJ.showMessage("Not Supported: requires 16-bit");
            IJ.showStatus("Not Supported: requires 16-bit");
            imp = null;
            return;
        }
        else{
            if(imp.isHyperStack()){
                currentChannel = imp.getC();
                currentZPosition = imp.getZ();
                ImagePlus[] channels = split(imp);
                imp.setC(currentChannel);
                imp.setZ(currentZPosition);
                imp = channels[currentChannel-1];
            }
            width = imp.getWidth();
            height = imp.getHeight();

            stack = imp.getImageStack();
            length = stack.getSize();
            ip = new ImageProcessor[stack.getSize()];
            for (int k = 0; k < stack.getSize(); k++) {
                ip[k] = stack.getProcessor(k + 1);
            }   
            min = imp.getDisplayRangeMin();
            max = imp.getDisplayRangeMax();
            thresholdS.setMinimum((int) min);
            thresholdS.setMaximum((int) max + 1);
        }
        plotHistogram(imp);
        updatePlot();
        updateLabels(imp);  
    }

    @Override
    public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
        Object source = e.getSource();
        if (source == thresholdS) {
            if(WindowManager.getCurrentImage()!=null&&WindowManager.getCurrentImage().isHyperStack()){
                if(WindowManager.getCurrentImage().getChannel()!=currentChannel){
                    setup();
                }
            }
            else if(WindowManager.getCurrentImage()!=null&&WindowManager.getCurrentImage()!=imp){
                setup();
            }
            threshold = thresholdS.getValue();
            min = threshold;
            if(WindowManager.getCurrentImage()!=null && WindowManager.getCurrentImage().getType() == ImagePlus.GRAY16){
                updatePlot();
                updateLabels(WindowManager.getCurrentImage()); 
            }
        } else if (source == stdevS) {
            stdev = stdevS.getValue();
        } else if (source == diameterS) {
            diameter = diameterS.getValue();
        }
        notify();
    }

    @Override
    public synchronized void actionPerformed(ActionEvent e) {
        Thread execute = new Thread(new Runnable() {
            public void run() {
                if(WindowManager.getCurrentImage()!=null&&WindowManager.getCurrentImage().isHyperStack()){
                    if(WindowManager.getCurrentImage().getChannel()!=currentChannel){
                        setup();
                    }
                }
                else if(WindowManager.getCurrentImage()!=null&&WindowManager.getCurrentImage()!=imp){
                    setup();
                }
                if (WindowManager.getCurrentImage() != null && WindowManager.getCurrentImage().getType() == ImagePlus.GRAY16) {
                    JFileChooser fc = new JFileChooser();
                    fc.setFileFilter(new FileNameExtensionFilter("CSV Files (.csv)", "csv"));
                    fc.setDialogTitle("Select CSV File");
                    if(fc.showSaveDialog(Process_Segmentation.this) == JFileChooser.APPROVE_OPTION) {
                        filepath = fc.getSelectedFile().getAbsolutePath()+".csv";
                        IJ.showProgress(0);
                        IJ.showStatus("Segmentation in Progress");
                        segmentation();
                        IJ.showStatus("Segmentation Complete");
                        setup();
                    }
                }
            }
        });
        execute.start();
    }

    void updatePlot() {
        plot.min = min;
        plot.max = max;
        plot.defaultMin = imp.getDisplayRangeMin();
        plot.defaultMax = imp.getDisplayRangeMax();
        plot.repaint();
    }

    void updateLabels(ImagePlus imp) {
        String minString = IJ.d2s(min, 0) + blankMinLabel;
        minLabel.setText(minString.substring(0, blankMinLabel.length()));
        String maxString = blankMaxLabel + IJ.d2s(max, 0);
        maxString = maxString.substring(maxString.length() - blankMaxLabel.length(), maxString.length());
        maxLabel.setText(maxString);
    }

    void plotHistogram(ImagePlus imp) {
        ImageStatistics stats;
        int range = imp.getType() == ImagePlus.GRAY16 ? ImagePlus.getDefault16bitRange() : 0;
        if (range != 0 && imp.getProcessor().getMax() == Math.pow(2, range) - 1 && !(imp.getCalibration().isSigned16Bit())) {
            ImagePlus imp2 = new ImagePlus("Temp", imp.getProcessor());
            stats = new StackStatistics(imp2, 256, 0, Math.pow(2, range));
        } else {
            stats = imp.getStatistics();
        }
        Color color = Color.gray;
        plot.setHistogram(stats, color);
    }   
    
    @Override
    public void close() {
        super.close();
        done = true;
        synchronized (this) {
            notify();
        }
    }

    public void segmentation(){
        short[][][] pixels = new short[length][height][width];
        for (int k = 0; k < length; ++k) {
            for (int i = 0; i < height; ++i) {
                for (int j = 0; j < width; ++j) {
                    pixels[k][i][j] = (short) ip[k].get(j, i);
                }
            }
        }
        IJ.showStatus("Creating Kernel Array");
        double[][][] kernel_array = create_kernel_array(diameter, stdev);
        IJ.showProgress(1.0);
        
        IJ.showProgress(0);
        IJ.showStatus("Filtering in Progress");
        short[][][] filtered = kernel_apply_3d(pixels, kernel_array);
        updateStack(filtered);
        IJ.showStatus("Filtering Currently Displayed");
        IJ.wait(5000);
        
        IJ.showProgress(0);
        IJ.showStatus("Thresholding in Progress");
        short[][][] thresholded = threshold_image(filtered, threshold);
        updateStack(thresholded);
        IJ.showStatus("Thesholding Currently Displayed");
        IJ.wait(5000);
        
        IJ.showProgress(0);
        IJ.showStatus("Floodfilling in Progress");
        short[][][] floodfilled = flood_fill(thresholded);
        updateStack(floodfilled);
    }
    
    /**
     * This method is a helper method to update the image stack from new
     * pixel array.
     * 
     * @param newArr - pixel array to update image with
     */
    public void updateStack(short[][][] newArr){
        for (int k = 0; k < length; ++k) {
            for (int i = 0; i < height; ++i) {
                for (int j = 0; j < width; ++j) {
                    ip[k].set(j, i, newArr[k][i][j]);
                }
            }
        }
        WindowManager.getCurrentImage().resetDisplayRange();
        WindowManager.getCurrentImage().updateAndDraw();
        IJ.showProgress(1.0);
    }
    
    /**
     * This method generates a 3D kernel array for gaussian filtering from given parameters.
     * 
     * @param diameter - side dimension of 'kernel cube' being created
     * @param stdv - standard deviation used in filtering
     * @return 3D double kernel array for gaussian filtering with specified diameter and standard deviation
     */
    public static double[][][] create_kernel_array(int diameter, double stdv){
	double[][][] weight = new double[diameter][diameter][diameter];
	double sum = 0;
        double zlim = (double)weight.length;
	
	for(int k = 0; k < weight.length; ++k) {
            for(int i = 0; i < weight[0].length; ++i) {
		for(int j = 0; j < weight[0][0].length; ++j) {
                    weight[k][i][j] = gaussMod(i-diameter/2,j-diameter/2,k-diameter/2,stdv);
                    sum += weight[k][i][j];
		}
            }
            IJ.showProgress(0.5*k/zlim);
	}
        IJ.showProgress(0.5);
	for(int k = 0; k < weight.length; ++k) {
            for(int i = 0; i < weight[0].length; ++i) {
		for(int j = 0; j < weight[0][0].length; ++j) {
                    weight[k][i][j] /= sum;
		}
            }
            IJ.showProgress(0.5+0.5*k/zlim);
	}
	return weight;
    }
	
    /**
     * Helper Gaussian Function
     * 
     * @param i - integer for 'x' coordinate position
     * @param j - integer for 'y' coordinate position
     * @param k - integer for 'z' coordinate position
     * @param stdev - standard deviation used in gaussian filter
     * @return corresponding gaussian function value from position and standard deviation
     */
    private static double gaussMod(int i, int j, int k, double stdev) {
    	return (1/(2*Math.PI*Math.pow(stdev, 2))*Math.exp(Math.pow(i, 2)+Math.pow(j,2)+Math.pow(k, 2)/(2*Math.pow(stdev, 2))));
    }

    /**
     * Perform filter on a 3D short array from pre-existing short array
     * 
     * @param input_array - 3D short array corresponding to image stack pixels
     * @param kernel_array - kernel array to apply filter
     * @return 3D short array filtered in accordance with the kernel array
     */
    public static short[][][] kernel_apply_3d(short[][][] input_array, double[][][] kernel_array){
        int zlim = input_array.length;
	int xlim = input_array[0].length;
	int ylim = input_array[0][0].length;
	
	int kernelz = kernel_array.length;
	int kernelx = kernel_array[0].length;
	int kernely = kernel_array[0][0].length;
	
	double accum = 0;
	int dx = 0;
	int dy = 0;
	int dz = 0;
		
	short[][][] output_array = new short[zlim][xlim][ylim];
        
        for(int k = 0; k < zlim; ++k) {
            for(int i = 0; i < xlim; ++i) {
       		for(int j = 0; j < ylim; ++j) {
                    accum = 0;
                    for(int kkern = 0; kkern < kernelz; ++kkern) {
                        for(int ikern = 0; ikern < kernelx; ++ikern) {
                            for(int jkern = 0; jkern < kernely; ++jkern) {
                                dx = ikern - kernelx/2;
                                dy = jkern - kernely/2;
                                dz = kkern - kernelz/2;
        						
                                dx += i; dy += j; dz += k;
        				
                                if(dx < 0) {
                                    dx *= -1;
                                }
                                if(dy < 0) {
                                    dy *= -1;
                                }
                                if(dz < 0) {
                                    dz *= -1;
                                }
                                if(dx >= xlim) {
                                    dx = 2*xlim-dx-1;
                                }
                                if(dy >= ylim) {
                                    dy = 2*ylim-dy-1;
                                }
                                if(dz >= zlim) {
                                    dz = 2*zlim-dz-1;
                                }
                                accum += kernel_array[kkern][ikern][jkern]*input_array[dz][dx][dy];
                            }
                        }
                    }
                    output_array[k][i][j] = (short)(accum);
                }
            }
            IJ.showProgress(k/(double)zlim);
        }
	return output_array;
    }
	
    /**
     * Set array elements below a given threshold to zero (or a black color).
     * 
     * @param input_array - 3D short array corresponding to image stack pixels
     * @param limit - array threshold
     * @return thresholded 3D short array
     */
    public static short[][][] threshold_image(short[][][] input_array, int limit) {
    	int zlim = input_array.length;
    	int xlim = input_array[0].length;
    	int ylim = input_array[0][0].length;
    	for(int k = 0; k < zlim; ++k) {
            for(int j = 0; j < ylim; ++j) {
                for(int i = 0; i < xlim; ++i) {
                    input_array[k][i][j] = (input_array[k][i][j] <= (short) limit ? (short) 0 : (short) 1);
                }
            }
            IJ.showProgress(k/(double)zlim);
	}
	return input_array;
    }
	
    /**
     * Distinguish directly connected 'pixels' from one another by flood-filling each blob with
     * different color value.
     * 
     * TODO: integrate count HashMap into program for center of blobs
     * 
     * @param input_array - 3D short array corresponding to image stack pixels
     * @return flood filled array
     */
    public static short[][][] flood_fill(short[][][] input_array){
	int zlim = input_array.length;
	int xlim = input_array[0].length;
	int ylim = input_array[0][0].length;
	short color;
	double total = 0;
	int[] pt;        
        double sumZ = 0, sumX = 0, sumY = 0;
        
	Deque<int[]> q = new ArrayDeque<>();
        ArrayList<double[]> count = new ArrayList<>();
		
	for (int k = 0; k < zlim; ++k) {
            for(int i = 0; i < xlim; ++i) {
    		for(int j = 0; j < ylim; ++j) {
                    if(input_array[k][i][j] == 1) {
			color = (short)(Math.random()*(Math.pow(2, 16)-1));
			total = 0;
                        sumZ = 0; sumX = 0; sumY = 0;
			q.add(new int[]{k,i,j});
			
			while(!q.isEmpty()) {
                            pt = q.poll();
                            if(input_array[pt[0]][pt[1]][pt[2]] != 1){
                                continue;
                            }
                            total += 1;
                            sumZ += pt[0]; sumX += pt[1]; sumY += pt[2];//
                            input_array[pt[0]][pt[1]][pt[2]] = color;
                            if(pt[0]-1 >= 0 /*&& input_array[pt[0]-1][pt[1]][pt[2]] == 1*/) {
                                q.add(new int[]{pt[0]-1,pt[1],pt[2]});
                            }
                            if(pt[0]+1 != zlim /*&& input_array[pt[0]+1][pt[1]][pt[2]] == 1*/) {
                                q.add(new int[]{pt[0]+1,pt[1],pt[2]});
                            }
                            if(pt[1]-1 >= 0 /*&& input_array[pt[0]][pt[1]-1][pt[2]] == 1*/) {
                                q.add(new int[]{pt[0],pt[1]-1,pt[2]});
                            }
                            if(pt[1]+1 != xlim /*&& input_array[pt[0]][pt[1]+1][pt[2]] == 1*/) {
                                q.add(new int[]{pt[0],pt[1]+1,pt[2]});
                            }
                            if(pt[2]-1 >= 0 /*&& input_array[pt[0]][pt[1]][pt[2]-1] == 1*/) {
                                q.add(new int[]{pt[0],pt[1],pt[2]-1});
                            }
                            if(pt[2]+1 != ylim /*&& input_array[pt[0]][pt[1]][pt[2]+1] == 1*/) {
                                q.add(new int[]{pt[0],pt[1],pt[2]+1});
                            }							
			}
			count.add(new double[]{sumZ/total,sumX/total,sumY/total,total});
                    }
		}
            }
            IJ.showProgress(k/(double)zlim);
	}
        
        try {
            FileWriter csvWriter = new FileWriter(filepath);
            csvWriter.append("Center of Mass X"); csvWriter.append(",");
            csvWriter.append("Center of Mass Y"); csvWriter.append(",");
            csvWriter.append("Center of Mass Z"); csvWriter.append(",");
            csvWriter.append("Total Pixel Points"); csvWriter.append("\n");
            
            for (int i = 0; i < count.size(); ++i) {
                csvWriter.append(count.get(i)[2]+""); csvWriter.append(",");
                csvWriter.append(count.get(i)[1]+""); csvWriter.append(",");
                csvWriter.append(count.get(i)[0]+""); csvWriter.append(",");
                csvWriter.append(((int)count.get(i)[3])+""); csvWriter.append("\n");
            }
            
            csvWriter.flush();
            csvWriter.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }  
	return input_array;
    }
}

class HistPlot extends Canvas implements MouseListener {

    static final int WIDTH = 128, HEIGHT = 64;
    double defaultMin = 0;
    double defaultMax = 255;
    double min = 0;
    double max = 255;
    int[] histogram;
    int hmax;
    Image os;
    Graphics osg;
    Color color = Color.gray;
    double scale = Prefs.getGuiScale();
    int width = WIDTH;
    int height = HEIGHT;

    public HistPlot() {
        addMouseListener(this);
        if (scale > 1.0) {
            width = (int) (width * scale);
            height = (int) (height * scale);
        }
        setSize(width + 1, height + 1);
    }

    /**
     * Overrides Component getPreferredSize(). Added to work around a bug in
     * Java 1.4.1 on Mac OS X.
     */
    public Dimension getPreferredSize() {
        return new Dimension(width + 1, height + 1);
    }

    void setHistogram(ImageStatistics stats, Color color) {
        this.color = color;
        histogram = stats.histogram;
        if (histogram.length != 256) {
            histogram = null;
            return;
        }
        int maxCount = 0;
        int mode = 0;
        for (int i = 0; i < 256; i++) {
            if (histogram[i] > maxCount) {
                maxCount = histogram[i];
                mode = i;
            }
        }
        int maxCount2 = 0;
        for (int i = 0; i < 256; i++) {
            if ((histogram[i] > maxCount2) && (i != mode)) {
                maxCount2 = histogram[i];
            }
        }
        hmax = stats.maxCount;
        if ((hmax > (maxCount2 * 2)) && (maxCount2 != 0)) {
            hmax = (int) (maxCount2 * 1.5);
            histogram[mode] = hmax;
        }
        os = null;
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        int x1, y1, x2, y2;
        double scale = (double) width / (defaultMax - defaultMin);
        double slope = 0.0;
        if (max != min) {
            slope = height / (max - min);
        }
        if (min >= defaultMin) {
            x1 = (int) (scale * (min - defaultMin));
            y1 = height;
        } else {
            x1 = 0;
            if (max > min) {
                y1 = height - (int) ((defaultMin - min) * slope);
            } else {
                y1 = height;
            }
        }
        if (max <= defaultMax) {
            x2 = (int) (scale * (max - defaultMin));
            y2 = 0;
        } else {
            x2 = width;
            if (max > min) {
                y2 = height - (int) ((defaultMax - min) * slope);
            } else {
                y2 = 0;
            }
        }
        if (histogram != null) {
            if (os == null && hmax != 0) {
                os = createImage(width, height);
                osg = os.getGraphics();
                osg.setColor(Color.white);
                osg.fillRect(0, 0, width, height);
                osg.setColor(color);
                double scale2 = width / 256.0;
                for (int i = 0; i < 256; i++) {
                    int x = (int) (i * scale2);
                    osg.drawLine(x, height, x, height - ((int) (height * histogram[i]) / hmax));
                }
                osg.dispose();
            }
            if (os != null) {
                g.drawImage(os, 0, 0, this);
            }
        } else {
            g.setColor(Color.white);
            g.fillRect(0, 0, width, height);
        }
        g.setColor(Color.black);
        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, height - 5, x2, height);
        g.drawRect(0, 0, width, height);
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

} // HistPlot class