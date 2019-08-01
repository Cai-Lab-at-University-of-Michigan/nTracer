package nTracer;

import ij.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.PlugIn;
import java.util.*;
import java.io.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This plugin opens a multi-page TIFF file as a virtual stack. It implements
 * the File/Import/TIFF Virtual Stack command. 
 */
public class BufferedVirtualStack extends VirtualStack implements PlugIn {
    protected List<FileInfo> info;
    protected BufferVirtualStackGUI gui;
    
    public Map<Integer,ImageProcessor> processor_buffer; // 300 * ~20MB -> ~6GB? 100 * ~30MB -> ~2GB?
    public int proc_buffer_MAX; 
    public Queue<Integer> processor_fifo;
    public int buffer_clean_count;
    
    public Lock test_lock;

    /* Default constructor. */
    public BufferedVirtualStack() {
        this.test_lock = new ReentrantLock();
        this.proc_buffer_MAX = 100;
        this.buffer_clean_count = 0;
        this.processor_fifo = new LinkedList<>();
        this.processor_buffer = new HashMap<>();
        this.info = new ArrayList<>();
        
        this.gui = new BufferVirtualStackGUI( this );
    }

    /* Constructs a FileInfoVirtualStack from a FileInfo object. */
    public BufferedVirtualStack(FileInfo fi) {
        this.test_lock = new ReentrantLock();
        this.proc_buffer_MAX = 100;
        this.buffer_clean_count = 0;
        this.processor_fifo = new LinkedList<>();
        this.processor_buffer = new HashMap<>();
        this.info = new ArrayList<>();
        
        info.add( fi );
        
        ImagePlus imp = open();
        if (imp != null) {
            imp.show();
        }
        
        this.gui = new BufferVirtualStackGUI( this );
    }

    /* Constructs a FileInfoVirtualStack from a FileInfo 
		object and displays it if 'show' is true. */
    public BufferedVirtualStack(FileInfo fi, boolean show) {
        this.test_lock = new ReentrantLock();
        this.proc_buffer_MAX = 100;
        this.buffer_clean_count = 0;
        this.processor_fifo = new LinkedList<>();
        this.processor_buffer = new HashMap<>();
        this.info = new ArrayList<>();
        
        info.add( fi );

        ImagePlus imp = open();
        if (imp != null && show) {
            imp.show();
        }
        
        this.gui = new BufferVirtualStackGUI( this );
    }

    /**
     * Opens the specified tiff file as a virtual stack.
     */
    public static ImagePlus openVirtual(String path) {
        OpenDialog od = new OpenDialog("Open TIFF", path);
        String name = od.getFileName();
        String dir = od.getDirectory();
        if (name == null) {
            return null;
        }
        BufferedVirtualStack stack = new BufferedVirtualStack();
        stack.init(dir, name);
        if (stack.info == null) {
            return null;
        } else {
            return stack.open();
        }
    }

    public void run(String arg) {
        OpenDialog od = new OpenDialog("Open TIFF", arg);
        String name = od.getFileName();
        String dir = od.getDirectory();
        if (name == null) {
            return;
        }
        init(dir, name);
        if (info == null) {
            return;
        }
        ImagePlus imp = open();
        if (imp != null) {
            imp.show();
        }
    }

    private void init(String dir, String name) {
        if (name.endsWith(".zip")) {
            IJ.error("Virtual Stack", "ZIP compressed stacks not supported");
            return;
        }
        TiffDecoder td = new TiffDecoder(dir, name);
        if (IJ.debugMode) {
            td.enableDebugging();
        }
        IJ.showStatus("Decoding TIFF header...");
        try {
            info = Arrays.asList( td.getTiffInfo() );
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg == null || msg.equals("")) {
                msg = "" + e;
            }
            IJ.error("TiffDecoder", msg);
            return;
        }
        if (info == null || info.isEmpty()) {
            IJ.error("Virtual Stack", "This does not appear to be a TIFF stack");
            return;
        }
        if (IJ.debugMode) {
            IJ.log(info.get(0).debugInfo);
        }

    }

    private ImagePlus open() {
        FileInfo fi = info.get(0);
        int n = fi.nImages;
        
        if (info.size() == 1 && n > 1) {
            info = new ArrayList<>();
            long size = fi.width * fi.height * fi.getBytesPerPixel();
            for (int i = 0; i < n; i++) {
                FileInfo toadd = (FileInfo) fi.clone();
                toadd.nImages = 1;
                toadd.longOffset = fi.getOffset() + i * (size + fi.gapBetweenImages);
                info.add( toadd );
            }
        }
        
        FileOpener fo = new FileOpener(info.get(0));
        ImagePlus imp = fo.open(false);
        if ( getSize() == 1 && fi.fileType == FileInfo.RGB48) {
            return imp;
        }
        
        Properties props = fo.decodeDescriptionString(fi);
        ImagePlus imp2 = new ImagePlus(fi.fileName, this);
        imp2.setFileInfo(fi);
        if (imp != null && props != null) {
            setBitDepth(imp.getBitDepth());
            imp2.setCalibration(imp.getCalibration());
            imp2.setOverlay(imp.getOverlay());
            
            if (fi.info != null) {
                imp2.setProperty("Info", fi.info);
            }
            
            int channels = getInt(props, "channels");
            int slices = getInt(props, "slices");
            int frames = getInt(props, "frames");
            if ( channels * slices * frames == getSize() ) {
                imp2.setDimensions(channels, slices, frames);
                if (getBoolean(props, "hyperstack")) {
                    imp2.setOpenAsHyperStack(true);
                }
            }
            
            if (channels > 1 && fi.description != null) {
                int mode = IJ.COMPOSITE;
                if ( fi.description.contains("mode=color") ) {
                    mode = IJ.COLOR;
                } else if ( fi.description.contains("mode=gray") ) {
                    mode = IJ.GRAYSCALE;
                }
                imp2 = new CompositeImage(imp2, mode);
            }
        }
        
        return imp2;
    }

    int getInt(Properties props, String key) {
        Double n = getNumber(props, key);
        return n != null ? (int) n.doubleValue() : 1;
    }

    Double getNumber(Properties props, String key) {
        String s = props.getProperty(key);
        if (s != null) {
            try {
                return Double.valueOf(s);
            } catch (NumberFormatException e) {
            }
        }
        return null;
    }

    boolean getBoolean(Properties props, String key) {
        String s = props.getProperty(key);
        return s != null && s.equals("true");
    }

    /**
     * Deletes the specified image, were 1<=n<=nImages.
     */
    public void deleteSlice(int n) {
        if (n < 1 || n > getSize()) {
            throw new IllegalArgumentException("Argument out of range: " + n);
        }
        
        if (getSize() == 0) {
            return;
        }
        
        info.remove(n-1);
    }

    /**
     * Returns an ImageProcessor for the specified image, were 1<=n<=nImages.
     * Returns null if the stack is empty.
     */
    public ImageProcessor getProcessor(int n){
        //IJ.log("Current Cache Size: " + processor_fifo.size());
        
        test_lock.lock();
        if( processor_fifo.size() > proc_buffer_MAX ) {
            int to_remove = processor_fifo.remove();
            //IJ.log( "\tClearing Cache Item " + to_remove );
            processor_buffer.remove( to_remove );
            
            buffer_clean_count++;
            if( buffer_clean_count % 10 == 0 ) {
                System.gc();
                buffer_clean_count = 0;
            }
        }
        
        ImageProcessor to_return = null;
        if( processor_buffer.containsKey(n) ){
            IJ.log("Cache HIT (" + n + ") " + processor_buffer.size()) ;
            to_return = processor_buffer.get(n);
        } else {
            IJ.log( "Cache MISS (" + n + ")");
            to_return = getProcessor_internal(n);
            processor_buffer.put(n, to_return);
            processor_fifo.add(n);
        }
        this.gui.updateStatus();
        test_lock.unlock();
        
        return to_return;
    }
    
    public ImageProcessor getProcessor_internal(int n) {
        IJ.log("Loading Processor " + n + "...");
        if ( isOutOfRange(n) ) {
            throw new IllegalArgumentException("Argument out of range: " + n);
        }
        
        //if (n>1) IJ.log("  "+(info[n-1].getOffset()-info[n-2].getOffset()));
        info.get(n-1).nImages = 1; // why is this needed?
        
        long t0 = System.currentTimeMillis(); // Used by debug, but doesn't hurt to not use
        
        FileOpener fo = new FileOpener( info.get(n-1) );
        ImagePlus imp = fo.open(false);

        if (IJ.debugMode) {
            t0 = System.currentTimeMillis() - t0; // calc time delta
            IJ.log( "FileInfoVirtualStack: " + n + ", offset=" + info.get(n-1).getOffset() + ", " + t0 + "ms" );
        }
        
        if (imp != null) {
            return imp.getProcessor();
        } else {
            int w = getWidth(), h = getHeight();
            IJ.log( "Read error or file not found (" + n + "): " + info.get(n-1).directory + " " + info.get(n-1).fileName );
            switch (getBitDepth()) {
                case 8:
                    return new ByteProcessor(w, h);
                case 16:
                    return new ShortProcessor(w, h);
                case 24:
                    return new ColorProcessor(w, h);
                case 32:
                    return new FloatProcessor(w, h);
                default:
                    return null;
            }
        }
    }

    /**
     * Returns the label of the Nth image.
     */
    public String getSliceLabel(int n) {
        if ( isOutOfRange(n) ) {
            throw new IllegalArgumentException("Argument out of range: " + n);
        }
        
        if ( info.get(0).sliceLabels == null || info.get(0).sliceLabels.length != getSize() ) {
            return null;
        }
        
        return info.get(0).sliceLabels[n - 1];
    }

    public int getWidth() {
        return info.get(0).width;
    }

    public int getHeight() {
        return info.get(0).height;
    }
    
    /**
     * Returns the number of images in this stack.
     */
    public int getSize() {
        return info.size();
    }
    
    /**
     * Checks if a given 'n' is a valid processor index
     * 
     * @param n a test index variable
     * @return (boolean) True if n is a valid processor, false otherwise
     */
    public boolean isOutOfRange( int n ) {
        return n < 1 || n > getSize();
    }

    /**
     * Adds an image to this stack.
     */
    public synchronized void addImage(FileInfo fileInfo) {
        info.add( fileInfo );
    }
    
    //ImagePlus.close();
}
