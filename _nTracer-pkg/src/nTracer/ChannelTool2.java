package nTracer;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author loganaw
 */
//package ij.plugin.frame;
import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.plugin.frame.Channels;
import ij.plugin.frame.PlugInDialog;
import ij.plugin.frame.Recorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Displays the ImageJ Channels window.
 */
public class ChannelTool2 extends PlugInDialog implements PlugIn, ItemListener, ActionListener {

    private static String[] modes = {"Composite", "Color", "Grayscale"};
    private static String[] menuItems = {"Make Composite", "Convert to RGB", "Split Channels", "Merge Channels...",
        "Edit LUT...", "-", "Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Grays"};

    private static final String moreLabel = "More " + '\u00bb';
    //private String[] title = {"Red", "Green", "Blue"};
    private Choice choice;
    private Checkbox[] checkbox;
    private Button moreButton;
    private ChannelTool2 instance; // make this not static to allow multiple instances -LAW
    //private int id;
    private static Point location;
    private PopupMenu pm;
    private CompositeImage imp;
    
    public ChannelTool2(ij.ImagePlus imp_in, String label) {
        super(label);

        if (imp_in == null || !imp_in.isComposite()) {
            //Print an error message to say this is wrong
            System.err.println( "Error finding composite image in Channels tool!");
            return;
        } else {
            imp = (CompositeImage) imp_in;
        }

        if (instance != null) {
            instance.toFront();
            return;
        }
        
        ImageJ ij = IJ.getInstance();
        WindowManager.addWindow(this);
        instance = this;
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        setLayout(gridbag);
        int y = 0;
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;
        int margin = 32;
        if (IJ.isMacOSX()) {
            margin = 20;
        }
        c.insets = new Insets(10, margin, 10, margin);
        choice = new Choice();
        for (int i = 0; i < modes.length; i++) {
            choice.addItem(modes[i]);
        }
        choice.select(0);
        choice.addItemListener(this);
        choice.addKeyListener(ij);
        add(choice, c);

        CompositeImage ci = getImage();
        int nCheckBoxes = ci != null ? ci.getNChannels() : 3;
        if (nCheckBoxes > CompositeImage.MAX_CHANNELS) {
            nCheckBoxes = CompositeImage.MAX_CHANNELS;
        }
        checkbox = new Checkbox[nCheckBoxes];
        for (int i = 0; i < nCheckBoxes; i++) {
            checkbox[i] = new Checkbox("Channel " + (i + 1), true);
            c.insets = new Insets(0, 25, i < nCheckBoxes - 1 ? 0 : 10, 5);
            c.gridy = y++;
            add(checkbox[i], c);
            checkbox[i].addItemListener(this);
            checkbox[i].addKeyListener(ij);
        }

        c.insets = new Insets(0, 15, 10, 15);
        c.fill = GridBagConstraints.NONE;
        c.gridy = y++;
        moreButton = new Button(moreLabel);
        moreButton.addActionListener(this);
        moreButton.addKeyListener(ij);
        add(moreButton, c);
        update();

        pm = new PopupMenu();
        GUI.scalePopupMenu(pm);
        for (int i = 0; i < menuItems.length; i++) {
            addPopupItem(menuItems[i]);
        }
        add(pm);

        addKeyListener(ij);  // ImageJ handles keyboard shortcuts
        setResizable(false);
        GUI.scale(this);
        pack();
        if (location == null) {
            //GUI.centerOnImageJScreen(this); // IDK What this does -LAW
            location = getLocation();
        } else {
            setLocation(location);
        }
        show(); //depricated interface
    }

    public void update() {
        CompositeImage ci = getImage();
        if (ci == null || checkbox == null) {
            return;
        }
        int n = checkbox.length;
        int nChannels = ci.getNChannels();
        if (nChannels != n && nChannels <= CompositeImage.MAX_CHANNELS) {
            instance = null;
            location = getLocation();
            close();
            new Channels();
            return;
        }
        boolean[] active = ci.getActiveChannels();
        for (int i = 0; i < checkbox.length; i++) {
            checkbox[i].setState(active[i]);
        }
        int index = 0;
        switch (ci.getMode()) {
            case IJ.COMPOSITE:
                index = 0;
                break;
            case IJ.COLOR:
                index = 1;
                break;
            case IJ.GRAYSCALE:
                index = 2;
                break;
        }
        choice.select(index);
    }

    public void updateChannels() { // remove static declaration
        if (instance != null) {
            instance.update();
        }
    }

    void addPopupItem(String s) {
        MenuItem mi = new MenuItem(s);
        mi.addActionListener(this);
        pm.add(mi);
    }

    CompositeImage getImage() {
        return imp;
    }

    public void itemStateChanged(ItemEvent e) {
        WindowManager.setCurrentWindow( imp.getWindow() );
        if (imp == null) {
            return;
        }
        if (!imp.isComposite()) {
            int channels = imp.getNChannels();
            if (channels == 1 && imp.getStackSize() <= 4) {
                channels = imp.getStackSize();
            }
            if (imp.getBitDepth() == 24 || (channels > 1 && channels < CompositeImage.MAX_CHANNELS)) {
                GenericDialog gd = new GenericDialog(imp.getTitle());
                gd.addMessage("Convert to multi-channel composite image?");
                gd.showDialog();
                if (gd.wasCanceled()) {
                    return;
                } else {
                    IJ.doCommand("Make Composite");
                }
            } else {
                IJ.error("Channels", "A composite image is required (e.g., " + moreLabel + " Open HeLa Cells),\nor create one using " + moreLabel + " Make Composite.");
                return;
            }
        }
        if (!imp.isComposite()) {
            return;
        }
        CompositeImage ci = (CompositeImage) imp;
        Object source = e.getSource();
        if (source == choice) {
            int index = ((Choice) source).getSelectedIndex();
            switch (index) {
                case 0:
                    ci.setMode(IJ.COMPOSITE);
                    break;
                case 1:
                    ci.setMode(IJ.COLOR);
                    break;
                case 2:
                    ci.setMode(IJ.GRAYSCALE);
                    break;
            }
            ci.updateAndDraw();
            if (Recorder.record) {
                String mode = null;
                if (Recorder.scriptMode()) {
                    switch (index) {
                        case 0:
                            mode = "IJ.COMPOSITE";
                            break;
                        case 1:
                            mode = "IJ.COLOR";
                            break;
                        case 2:
                            mode = "IJ.GRAYSCALE";
                            break;
                    }
                    Recorder.recordCall("imp.setDisplayMode(" + mode + ");");
                } else {
                    switch (index) {
                        case 0:
                            mode = "composite";
                            break;
                        case 1:
                            mode = "color";
                            break;
                        case 2:
                            mode = "grayscale";
                            break;
                    }
                    Recorder.record("Stack.setDisplayMode", mode);
                }
            }
        } else if (source instanceof Checkbox) {
            for (int i = 0; i < checkbox.length; i++) {
                Checkbox cb = (Checkbox) source;
                if (cb == checkbox[i]) {
                    if (ci.getMode() == IJ.COMPOSITE) {
                        boolean[] active = ci.getActiveChannels();
                        active[i] = cb.getState();
                        if (Recorder.record) {
                            String str = "";
                            for (int c = 0; c < ci.getNChannels(); c++) {
                                str += active[c] ? "1" : "0";
                            }
                            if (Recorder.scriptMode()) {
                                Recorder.recordCall("imp.setActiveChannels(\"" + str + "\");");
                            } else {
                                Recorder.record("Stack.setActiveChannels", str);
                            }
                        }
                    } else {
                        imp.setPosition(i + 1, imp.getSlice(), imp.getFrame());
                        if (Recorder.record) {
                            if (Recorder.scriptMode()) {
                                Recorder.recordCall("imp.setC(" + (i + 1) + ");");
                            } else {
                                Recorder.record("Stack.setChannel", i + 1);
                            }
                        }
                    }
                    ci.updateAndDraw();
                    return;
                }
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        if (command == null) {
            return;
        }
        if (command.equals(moreLabel)) {
            Point bloc = moreButton.getLocation();
            pm.show(this, bloc.x, bloc.y);
        } else if (command.equals("Convert to RGB")) {
            WindowManager.setCurrentWindow( imp.getWindow() );
            IJ.doCommand("Stack to RGB");
        } else {
            WindowManager.setCurrentWindow( imp.getWindow() );
            IJ.doCommand(command);
        }
    }

    /**
     * Obsolete; always returns null.
     */
    public static Frame getInstance() {
        return null;
    }

    public void close() {
        super.close();
        instance = null;
        location = getLocation();
    }

}
