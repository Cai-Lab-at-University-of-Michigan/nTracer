/*
 * 

A* Pathfinding source code is taken from 
https://code.google.com/p/protectcastle/source/browse/

Code license
New BSD License
http://opensource.org/licenses/BSD-3-Clause

Content license
Creative Commons 3.0 BY-SA
http://creativecommons.org/licenses/by-sa/3.0/

Authors:
marcandr...@gmail.com		Owner
kalle.ha...@gmail.com		Owner
ra4k...@gmail.com		Committer

 */



package nTracer;

import ij.*;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.*;
import ij.gui.PolygonRoi;
import ij.gui.YesNoCancelDialog;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.plugin.RGBStackMerge;
import ij.plugin.HyperStackConverter;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.MemoryMonitor;
import ij.plugin.ZProjector;
import ij.process.ByteProcessor;

import java.awt.*;
import java.awt.event.*;
import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static java.util.concurrent.TimeUnit.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.JTable;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;

import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Main class - contains the main function and implements the GUI. 
 * 
 * @author Dawen Cai <dwcai@umich.edu>
 */
public class nTracer_
        extends javax.swing.JFrame
        implements
        WindowListener,
        MouseListener,
        MouseMotionListener,
        MouseWheelListener,
        KeyListener {

    public static final String VERSION = "nTracer 1.3.5";

    private int last_current_z;
    private Instant last_z_update_time;
    public Map<coord3D, Float> color_buffer;
    public Lock color_lock;

    /**
     * default constructor for nTracer_ object
     */
    
    public nTracer_() {
        this.color_buffer = new HashMap<>();
        this.last_current_z = -1;
        this.last_z_update_time = Instant.now();
        this.color_lock = new ReentrantLock();

        IO = new ntIO(this);
        analysis = new ntAnalysis();
        Functions = new ntTracing();
        dataHelper = new DataHelper(this);
        traceHelper = new TraceHelper(this);
        dataHandler = new ntDataHandler();

        if (!IJ.isJava18()) {
            IJ.error("Fiji/ImageJ-Java8 version is required !");
            return;
        }

        setupFeelsAndTools(); // needs to be done before set up GUI        
        // set up GUI
        initComponents();

        semiAutoTracing_jRadioButton.setVisible(true);
        autoTracing_jRadioButton.setVisible(false);
        linkRadius_jLabel.setVisible(false);
        linkRadius_jSpinner.setVisible(false);

        overlayPointBox_jCheckBox.setVisible(true);
        overlayPointBox_jCheckBox.setEnabled(true);
        colorThreshold = (Float) (colorThreshold_jSpinner.getValue());
        intensityThreshold = (Float) (intensityThreshold_jSpinner.getValue());
        xyRadius = (Integer) (xyRadius_jSpinner.getValue());
        zRadius = (Integer) (zRadius_jSpinner.getValue());
        outLinkXYradius = (Integer) (linkRadius_jSpinner.getValue());
        extendDisplayPoints = (Integer) extendSelectedDisplayPoints_jSpinner.getValue();
        somaLine = (Integer) somaLineWidth_jSpinner.getValue() * 0.5f;
        neuronLine = (Integer) neuronLineWidth_jSpinner.getValue() * 0.5f;
        arborLine = (Integer) arborLineWidth_jSpinner.getValue() * 0.5f;
        branchLine = (Integer) branchLineWidth_jSpinner.getValue() * 0.5f;
        spineLine = (Integer) spineLineWidth_jSpinner.getValue() * 0.5f;
        synapseRadius = (Integer) synapseRadius_jSpinner.getValue() * 1.0f;
        pointBoxLine = (Integer) pointBoxLineWidth_jSpinner.getValue() * 0.5f;
        pointBoxRadius = (Integer) pointBoxRadiu_jSpinner.getValue();
        synapseSize = synapseRadius * 2 + 1;
        lineWidthOffset = (Integer) allNeuronLineWidthOffset_jSpinner.getValue() * 0.5f;
        allSomaLine = (somaLine - lineWidthOffset > 0.5) ? somaLine - lineWidthOffset : 0.5f;
        allNeuronLine = (neuronLine - lineWidthOffset > 0.5) ? neuronLine - lineWidthOffset : 0.5f;
        allSpineLine = (spineLine - lineWidthOffset > 0.5) ? spineLine - lineWidthOffset : 0.5f;
        allSynapseRadius = (synapseRadius - lineWidthOffset / 2 > 0.5) ? synapseRadius - lineWidthOffset / 2 : 0.5;
        allSynapseSize = allSynapseRadius * 2;
        //IJ.log("displayDepth = +/- "+extendDisplayPoints);

        this.setTitle(nTracer_.VERSION);

        history = new History(this);
        update = new Update(this);
        initPointTable();
        initNeuriteTree();
        initSomaTree();
        initSpineTree();
        deselectInvisualizeAllChannelCheckboxes();
        //visualizeDisableAllChannelCheckboxes();
        disableSpinners();
        cropData_jMenuItem.setVisible(false);
        debug_jMenu.setVisible(false);
        this.setVisible(true);
        this.setTitle(this.getTitle() + " - pixel resolutions (x, y, z) um/pixel:");

        if (!openImage()) {
            quit();
            return;
        }

        //IJ.run("Brightness/Contrast...");
        IJ.run("Misc...", "divide=Infinity require");
        //IJ.run("Synchronize Windows", "");        

        /* load channel windows */
        // Old way:
        // IJ.run("Channels Tool...", "");
        // New way:
        main_channel_window = new ChannelTool2(imp, "Main Image");
        main_channel_window.run("");

        mp_channel_window = new ChannelTool2(impZproj, "Maximum Projection");
        mp_channel_window.run("");
        
        contrast_window = new ContrastTool2( imp );
        contrast_window.run( "");
        
        roi_window = new ROIManager2( imp );
        roi_window.run( "" );
        /* end load channel windows */

        MemoryMonitor mm = new MemoryMonitor();
        mm.run(" ");
    }

    private ChannelTool2 main_channel_window, mp_channel_window;
    private ContrastTool2 contrast_window;
    private ROIManager2 roi_window;
    
    // <editor-fold defaultstate="collapsed" desc="methods for setting up GUI views and Table/Tree components">
    /**
     * Establish windows style
     */
    private void setupFeelsAndTools() {
        // set up system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException ex) {
        }

        System.setProperty("apple.laf.useScreenMenuBar", "true");

        // add KeyListener to all components of the GUI jFrame
        //ArrayList<Component> GUIcomponentList = getAllComponents(this);
        //for (Component comp : GUIcomponentList){
        //    comp.addKeyListener(this);
        //}
        // set Toolbar for Manual Tracer
        //Toolbar.removeMacroTools();
        Toolbar.getInstance().setTool("freeline");
        //Toolbar.addPlugInTool(ntToolTrace);
        //Toolbar.addPlugInTool(ntToolSoma);
        //Toolbar.getInstance().setTool(ntToolTrace.toolName);
    }

    private ArrayList<Component> getAllComponents(final Container c) {
        Component[] comps = c.getComponents();
        ArrayList<Component> compList = new ArrayList<>();
        for (Component comp : comps) {
            compList.add(comp);
            if (comp instanceof Container) {
                compList.addAll(getAllComponents((Container) comp));
            }
        }
        return compList;
    }
    
    /**
     * set up tracked point table
     * 
     */
    
    
    protected void initPointTable() {
        // set up tracked point table
        pointTableModel = new DefaultTableModel(
                dataHandler.getPointTableData(new ArrayList<String[]>()),
                pointColumnNames) {
            Class[] types = new Class[]{
                java.lang.String.class, java.lang.Float.class,
                java.lang.Float.class, java.lang.Float.class,
                java.lang.Float.class, java.lang.Integer.class,
                java.lang.String.class
            };
            boolean[] canEdit = new boolean[]{
                false, false, false, false, false, false, false
            };

            @Override
            public Class getColumnClass(int columnIndex) {
                return types[columnIndex];
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit[columnIndex];
            }
        };
//        pointTableModelListener = new ntPointTableModelListener();
//        pointTableModel.addTableModelListener(pointTableModelListener);
        pointTable_jTable.setModel(pointTableModel);
        pointSelectionListener = new ntPointSelectionListener(pointTable_jTable);
        pointTable_jTable.getSelectionModel().addListSelectionListener(pointSelectionListener);
//        pointTableModelListener = new ntPointTableModelListener();
//        pointTable_jTable.getModel().addTableModelListener(pointTableModelListener);
//        pointTable_jTable.addMouseListener(this);
        //pointTable_jTable.addKeyListener(this);
    }

    /**
     set up neuron list tree
     */
    
    protected void initNeuriteTree() {
        // set up neuron list tree
        rootNeuronNode = new ntNeuronNode("Traced Neuron", new ArrayList<String[]>());
        neuronTreeModel = new DefaultTreeModel(rootNeuronNode);
        neuronList_jTree = new JTree(neuronTreeModel);
        neuronList_jTree.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Neuron", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setBackground(Color.red);
        neuronList_jTree.setCellRenderer(renderer);
        neuronList_jTree.setEditable(false);
        neuronList_jTree.setToggleClickCount(0);
        //neuronList_jTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        neuriteTreeSlectionListener = new ntNeuriteTreeSelectionListener();
        neuronList_jTree.addTreeSelectionListener(neuriteTreeSlectionListener);
        neuriteTreeExpansionListener = new ntNeuriteTreeExpansionListener();
        neuronList_jTree.addTreeExpansionListener(neuriteTreeExpansionListener);
        neuronList_jTree.addMouseListener(this);
        neuronList_jTree.setRootVisible(false);
        neuronList_jTree.setShowsRootHandles(true);
        neuronList_jScrollPane.setViewportView(neuronList_jTree);
        //neuronList_jTree.addKeyListener(this);

        neuronList_jTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree pTree,
                    Object pValue, boolean pIsSelected, boolean pIsExpanded,
                    boolean pIsLeaf, int pRow, boolean pHasFocus) {
                JComponent c = (JComponent) super.getTreeCellRendererComponent(pTree, pValue, pIsSelected,
                        pIsExpanded, pIsLeaf, pRow, pHasFocus);
                ntNeuronNode node = (ntNeuronNode) pValue;
                if (node.isBranchNode()) {
                    if (node.isComplete()) {
                        String nodeType = node.getType();
                        if (nodeType.startsWith("Axon")) {
                            c.setForeground(Color.blue);
                        } else if (nodeType.startsWith("Dendrite")) {
                            c.setForeground(Color.red);
                        } else if (nodeType.startsWith("Apical")) {
                            c.setForeground(Color.magenta);
                        }
                    } else {
                        c.setForeground(Color.lightGray);
                    }
                }
                return (this);
            }
        });
    }
    
    /**
     * set up soma list tree
     */

    protected void initSomaTree() {
        // set up neuron list tree
        rootAllSomaNode = new ntNeuronNode("All Soma", new ArrayList<String[]>());
        allSomaTreeModel = new DefaultTreeModel(rootAllSomaNode);
        rootDisplaySomaNode = new ntNeuronNode("Display Soma", new ArrayList<String[]>());
        displaySomaTreeModel = new DefaultTreeModel(rootDisplaySomaNode);
        displaySomaList_jTree = new JTree(displaySomaTreeModel);
        displaySomaList_jTree.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Soma", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11)));
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setBackground(Color.red);
        displaySomaList_jTree.setCellRenderer(renderer);
        displaySomaList_jTree.setEditable(false);
        displaySomaList_jTree.setToggleClickCount(0);
        somaTreeSlectionListener = new ntSomaTreeSelectionListener();
        displaySomaList_jTree.addTreeSelectionListener(somaTreeSlectionListener);
        displaySomaList_jTree.addMouseListener(this);
        displaySomaList_jTree.setRootVisible(false);
        displaySomaList_jTree.setShowsRootHandles(true);
        somaList_jScrollPane.setViewportView(displaySomaList_jTree);
        //displaySomaList_jTree.addKeyListener(this);
    }

    /**
     * set up spine Tree
     */
    
    protected void initSpineTree() {
        rootSpineNode = new ntNeuronNode("All Spines", new ArrayList<String[]>());
        spineTreeModel = new DefaultTreeModel(rootSpineNode);
        spineList_jTree = new JTree(spineTreeModel);
    }

    private void deselectInvisualizeAllChannelCheckboxes() {
        toggleCh1_jCheckBox.setSelected(false);
        toggleCh2_jCheckBox.setSelected(false);
        toggleCh3_jCheckBox.setSelected(false);
        toggleCh4_jCheckBox.setSelected(false);
        toggleCh5_jCheckBox.setSelected(false);
        toggleCh6_jCheckBox.setSelected(false);
        toggleCh7_jCheckBox.setSelected(false);
        toggleCh8_jCheckBox.setSelected(false);
        analysisCh1_jCheckBox.setSelected(false);
        analysisCh2_jCheckBox.setSelected(false);
        analysisCh3_jCheckBox.setSelected(false);
        analysisCh4_jCheckBox.setSelected(false);
        analysisCh5_jCheckBox.setSelected(false);
        analysisCh6_jCheckBox.setSelected(false);
        analysisCh7_jCheckBox.setSelected(false);
        analysisCh8_jCheckBox.setSelected(false);

        toggleCh1_jCheckBox.setVisible(false);
        toggleCh2_jCheckBox.setVisible(false);
        toggleCh3_jCheckBox.setVisible(false);
        toggleCh4_jCheckBox.setVisible(false);
        toggleCh5_jCheckBox.setVisible(false);
        toggleCh6_jCheckBox.setVisible(false);
        toggleCh7_jCheckBox.setVisible(false);
        toggleCh8_jCheckBox.setVisible(false);
        analysisCh1_jCheckBox.setVisible(false);
        analysisCh2_jCheckBox.setVisible(false);
        analysisCh3_jCheckBox.setVisible(false);
        analysisCh4_jCheckBox.setVisible(false);
        analysisCh5_jCheckBox.setVisible(false);
        analysisCh6_jCheckBox.setVisible(false);
        analysisCh7_jCheckBox.setVisible(false);
        analysisCh8_jCheckBox.setVisible(false);
    }

    private void visualizeDisableAllChannelCheckboxes() {
        toggleCh1_jCheckBox.setVisible(true);
        toggleCh2_jCheckBox.setVisible(true);
        toggleCh3_jCheckBox.setVisible(true);
        toggleCh4_jCheckBox.setVisible(true);
        toggleCh5_jCheckBox.setVisible(true);
        toggleCh6_jCheckBox.setVisible(true);
        toggleCh7_jCheckBox.setVisible(true);
        toggleCh8_jCheckBox.setVisible(true);
        analysisCh1_jCheckBox.setVisible(true);
        analysisCh2_jCheckBox.setVisible(true);
        analysisCh3_jCheckBox.setVisible(true);
        analysisCh4_jCheckBox.setVisible(true);
        analysisCh5_jCheckBox.setVisible(true);
        analysisCh6_jCheckBox.setVisible(true);
        analysisCh7_jCheckBox.setVisible(true);
        analysisCh8_jCheckBox.setVisible(true);

        toggleCh1_jCheckBox.setEnabled(false);
        toggleCh2_jCheckBox.setEnabled(false);
        toggleCh3_jCheckBox.setEnabled(false);
        toggleCh4_jCheckBox.setEnabled(false);
        toggleCh5_jCheckBox.setEnabled(false);
        toggleCh6_jCheckBox.setEnabled(false);
        toggleCh7_jCheckBox.setEnabled(false);
        toggleCh8_jCheckBox.setEnabled(false);
        analysisCh1_jCheckBox.setEnabled(false);
        analysisCh2_jCheckBox.setEnabled(false);
        analysisCh3_jCheckBox.setEnabled(false);
        analysisCh4_jCheckBox.setEnabled(false);
        analysisCh5_jCheckBox.setEnabled(false);
        analysisCh6_jCheckBox.setEnabled(false);
        analysisCh7_jCheckBox.setEnabled(false);
        analysisCh8_jCheckBox.setEnabled(false);
    }

    private void disableSpinners() {
        ((JSpinner.DefaultEditor) colorThreshold_jSpinner.getEditor()).getTextField().setEditable(false);
        ((JSpinner.DefaultEditor) xyRadius_jSpinner.getEditor()).getTextField().setEditable(false);
        ((JSpinner.DefaultEditor) linkRadius_jSpinner.getEditor()).getTextField().setEditable(false);
        ((JSpinner.DefaultEditor) extendSelectedDisplayPoints_jSpinner.getEditor()).getTextField().setEditable(false);
        ((JSpinner.DefaultEditor) extendAllDisplayPoints_jSpinner.getEditor()).getTextField().setEditable(false);
    }

    // </editor-fold>
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tracingMethod_buttonGroup = new javax.swing.ButtonGroup();
        channelColor_buttonGroup = new javax.swing.ButtonGroup();
        showConnected_buttonGroup = new javax.swing.ButtonGroup();
        labelingMethod_buttonGroup = new javax.swing.ButtonGroup();
        jCheckBox9 = new javax.swing.JCheckBox();
        main_jTabbedPane = new javax.swing.JTabbedPane();
        editDisplay_jPanel = new javax.swing.JPanel();
        editBranch_jPanel = new javax.swing.JPanel();
        setPrimaryBranch_jButton = new javax.swing.JButton();
        joinBranches_jButton = new javax.swing.JButton();
        breakBranch_jButton = new javax.swing.JButton();
        editNeuron_jPanel = new javax.swing.JPanel();
        collapseAllNeuron_jButton = new javax.swing.JButton();
        expanAllNeuron_jButton = new javax.swing.JButton();
        comnibeTwoNeuron_jButton = new javax.swing.JButton();
        editSoma_jPanel = new javax.swing.JPanel();
        completeSomaSliceRoi_jButton = new javax.swing.JButton();
        editConnection_jPanel = new javax.swing.JPanel();
        toggleConnection_jButton = new javax.swing.JButton();
        gotoConnection_jButton = new javax.swing.JButton();
        delete_jPanel = new javax.swing.JPanel();
        deleteSomaSlice_jButton = new javax.swing.JButton();
        deletePoints_jButton = new javax.swing.JButton();
        deleteOneNeuron_jButton = new javax.swing.JButton();
        deleteOneBranch_jButton = new javax.swing.JButton();
        jumpTo_jPanel = new javax.swing.JPanel();
        jumpToNextSynapse_jButton = new javax.swing.JButton();
        jumpToNextConnected_jButton = new javax.swing.JButton();
        jumpToNextIncompleted_jButton = new javax.swing.JButton();
        jumpToNextSelected_jButton = new javax.swing.JButton();
        editSynapse_jPanel = new javax.swing.JPanel();
        toggleSynapse_jButton = new javax.swing.JButton();
        toggleSynapse_jButton1 = new javax.swing.JButton();
        overlay_jPanel = new javax.swing.JPanel();
        all_jPanel = new javax.swing.JPanel();
        overlayAllNeuron_jCheckBox = new javax.swing.JCheckBox();
        overlayAllSoma_jCheckBox = new javax.swing.JCheckBox();
        overlayAllSynapse_jCheckBox = new javax.swing.JCheckBox();
        blank_jCheckBox = new javax.swing.JCheckBox();
        overlayAllName_jCheckBox = new javax.swing.JCheckBox();
        overlayAllConnection_jCheckBox = new javax.swing.JCheckBox();
        extendAllDisplayPoints_jSpinner = new javax.swing.JSpinner();
        allPlusMinus_jLabel = new javax.swing.JLabel();
        overlayAllPoints_jCheckBox = new javax.swing.JCheckBox();
        lineWidthOffset_jLabel = new javax.swing.JLabel();
        allNeuronLineWidthOffset_jSpinner = new javax.swing.JSpinner();
        overlayAllSpine_jCheckBox = new javax.swing.JCheckBox();
        selected_jPanel = new javax.swing.JPanel();
        overlaySelectedSoma_jCheckBox = new javax.swing.JCheckBox();
        overlaySelectedBranch_jCheckBox = new javax.swing.JCheckBox();
        overlaySelectedName_jCheckBox = new javax.swing.JCheckBox();
        overlaySelectedNeuron_jCheckBox = new javax.swing.JCheckBox();
        overlayAllSelectedPoints_jCheckBox = new javax.swing.JCheckBox();
        overlaySelectedArbor_jCheckBox = new javax.swing.JCheckBox();
        overlaySelectedSynapse_jCheckBox = new javax.swing.JCheckBox();
        overlaySelectedConnection_jCheckBox = new javax.swing.JCheckBox();
        overlaySelectedSpine_jCheckBox = new javax.swing.JCheckBox();
        overlayPointBox_jCheckBox = new javax.swing.JCheckBox();
        brainbowColor_jCheckBox = new javax.swing.JCheckBox();
        selectedPlusMinus_jLabel = new javax.swing.JLabel();
        extendSelectedDisplayPoints_jSpinner = new javax.swing.JSpinner();
        somaLineWidth_jSpinner = new javax.swing.JSpinner();
        neuronLineWidth_jSpinner = new javax.swing.JSpinner();
        arborLineWidth_jSpinner = new javax.swing.JSpinner();
        branchLineWidth_jSpinner = new javax.swing.JSpinner();
        spineLineWidth_jSpinner = new javax.swing.JSpinner();
        pointBoxLineWidth_jSpinner = new javax.swing.JSpinner();
        synapseRadius_jSpinner = new javax.swing.JSpinner();
        pointBoxRadiu_jSpinner = new javax.swing.JSpinner();
        updateDisplay_jButton = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jComboBox1 = new javax.swing.JComboBox<>();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jButton2 = new javax.swing.JButton();
        tracing_jPanel = new javax.swing.JPanel();
        tracingMethod_jPanel = new javax.swing.JPanel();
        manualTracing_jRadioButton = new javax.swing.JRadioButton();
        semiAutoTracing_jRadioButton = new javax.swing.JRadioButton();
        linkRadius_jLabel = new javax.swing.JLabel();
        linkRadius_jSpinner = new javax.swing.JSpinner();
        colorSamplingRadius_jPanel = new javax.swing.JPanel();
        xyRadius_jLabel = new javax.swing.JLabel();
        xyRadius_jSpinner = new javax.swing.JSpinner();
        zRadius_jLabel = new javax.swing.JLabel();
        zRadius_jSpinner = new javax.swing.JSpinner();
        samplingTolerance_jPanel = new javax.swing.JPanel();
        colorThreshold_jLabel = new javax.swing.JLabel();
        colorThreshold_jSpinner = new javax.swing.JSpinner();
        intensityThreshold_jLabel = new javax.swing.JLabel();
        intensityThreshold_jSpinner = new javax.swing.JSpinner();
        clearPoints_jButton = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        traceNeurite_jButton = new javax.swing.JButton();
        traceSoma_jButton = new javax.swing.JButton();
        traceSpine_jButton = new javax.swing.JButton();
        labeling_jPanel = new javax.swing.JPanel();
        membraneLabel_jRadioButton = new javax.swing.JRadioButton();
        cytoplasmLabel_jRadioButton = new javax.swing.JRadioButton();
        autoTracing_jRadioButton = new javax.swing.JRadioButton();
        toogleTracingCompleteness_jButton = new javax.swing.JButton();
        zProjectionInterval_jPanel = new javax.swing.JPanel();
        zProjectionInterval_jLabel = new javax.swing.JLabel();
        zProjectionInterval_jSpinner = new javax.swing.JSpinner();
        xyProjectionArea_jLabel = new javax.swing.JLabel();
        xyProjectionArea_jSpinner = new javax.swing.JSpinner();
        projectionUpdate_jCheckBox = new javax.swing.JCheckBox();
        channel_jPanel = new javax.swing.JPanel();
        toggleColor_jLabel = new javax.swing.JLabel();
        r_jRadioButton = new javax.swing.JRadioButton();
        g_jRadioButton = new javax.swing.JRadioButton();
        b_jRadioButton = new javax.swing.JRadioButton();
        analysisChannel_jLabel = new javax.swing.JLabel();
        analysisCh1_jCheckBox = new javax.swing.JCheckBox();
        analysisCh2_jCheckBox = new javax.swing.JCheckBox();
        analysisCh3_jCheckBox = new javax.swing.JCheckBox();
        analysisCh4_jCheckBox = new javax.swing.JCheckBox();
        analysisCh5_jCheckBox = new javax.swing.JCheckBox();
        analysisCh6_jCheckBox = new javax.swing.JCheckBox();
        analysisCh7_jCheckBox = new javax.swing.JCheckBox();
        analysisCh8_jCheckBox = new javax.swing.JCheckBox();
        toggleChannel_jLabel = new javax.swing.JLabel();
        toggleCh1_jCheckBox = new javax.swing.JCheckBox();
        toggleCh2_jCheckBox = new javax.swing.JCheckBox();
        toggleCh3_jCheckBox = new javax.swing.JCheckBox();
        toggleCh4_jCheckBox = new javax.swing.JCheckBox();
        toggleCh5_jCheckBox = new javax.swing.JCheckBox();
        toggleCh6_jCheckBox = new javax.swing.JCheckBox();
        toggleCh7_jCheckBox = new javax.swing.JCheckBox();
        toggleCh8_jCheckBox = new javax.swing.JCheckBox();
        showSkeletonized_jButton = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        skeletonizedShowTracedPoints_jButton = new javax.swing.JCheckBox();
        neuronList_jScrollPane = new javax.swing.JScrollPane();
        neuronList_jTree = new javax.swing.JTree();
        somaList_jScrollPane = new javax.swing.JScrollPane();
        displaySomaList_jTree = new javax.swing.JTree();
        pointTable_jScrollPane = new javax.swing.JScrollPane();
        pointTable_jTable = new javax.swing.JTable();
        info_jLabel = new javax.swing.JLabel();
        srtPtInt_jLabel = new javax.swing.JLabel();
        endPtInt_jLabel = new javax.swing.JLabel();
        startPosition_jLabel = new javax.swing.JLabel();
        endPosition_jLabel = new javax.swing.JLabel();
        startPt_jLabel = new javax.swing.JLabel();
        endPt_jLabel = new javax.swing.JLabel();
        startIntensity_jLabel = new javax.swing.JLabel();
        endIntensity_jLabel = new javax.swing.JLabel();
        srtPtCol_jLabel = new javax.swing.JLabel();
        startColor_jLabel = new javax.swing.JLabel();
        endPtCol_jLabel = new javax.swing.JLabel();
        endColor_jLabel = new javax.swing.JLabel();
        editTargetName_jLabel = new javax.swing.JLabel();
        connectedSynapse_jLabel = new javax.swing.JLabel();
        copyToEditTarget_jButton = new javax.swing.JButton();
        neuronTree_jLabel = new javax.swing.JLabel();
        setNeurite_jButton = new javax.swing.JButton();
        setAxon_jButton = new javax.swing.JButton();
        setBasalDendrite_jButton = new javax.swing.JButton();
        setApicalDendrite_jButton = new javax.swing.JButton();
        selectionTag_jTextField = new javax.swing.JTextField();
        selectNeuronNumber_jTextField = new javax.swing.JTextField();
        addLabelToSelection_jButton = new javax.swing.JButton();
        selectNeurons_jButton = new javax.swing.JButton();
        selectTagOperator_jComboBox = new javax.swing.JComboBox();
        selectTagAdditionalCriteria_jComboBox = new javax.swing.JComboBox();
        showConnectedNeurons_jButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        copyNeuronTag_jButton = new javax.swing.JButton();
        menu_jMenuBar = new javax.swing.JMenuBar();
        menu_jMenu = new javax.swing.JMenu();
        xyzResolutions_jMenuItem = new javax.swing.JMenuItem();
        quit_jMenuItem = new javax.swing.JMenuItem();
        model3D_jMenu1 = new javax.swing.JMenu();
        selecAllNeuron_jMenuItem = new javax.swing.JMenuItem();
        deselectAllNeuon_jMenuItem = new javax.swing.JMenuItem();
        undo_jMenuItem = new javax.swing.JMenuItem();
        redo_jMenuItem = new javax.swing.JMenuItem();
        setScale_jMenuItem = new javax.swing.JMenuItem();
        data_jMenu = new javax.swing.JMenu();
        loadData_jMenuItem = new javax.swing.JMenuItem();
        loadSkeletonized_jMenuItem = new javax.swing.JMenuItem();
        saveData_jMenuItem = new javax.swing.JMenuItem();
        clearData_jMenuItem = new javax.swing.JMenuItem();
        cropData_jMenuItem = new javax.swing.JMenuItem();
        exportSWCfromSelectedNeurons_jMenuItem = new javax.swing.JMenuItem();
        exportSynapseFromSelectedNeurons_jMenuItem = new javax.swing.JMenuItem();
        autosaveSetup_jMenuItem = new javax.swing.JMenuItem();
        analysis_jMenu = new javax.swing.JMenu();
        logNormChIntensity_jMenuItem = new javax.swing.JMenuItem();
        jMenuItem1 = new javax.swing.JMenuItem();
        logColorRatio_jMenuItem = new javax.swing.JMenuItem();
        logNeuronConnection_jMenuItem = new javax.swing.JMenuItem();
        logSomaStatistics_jMenuItem = new javax.swing.JMenuItem();
        model3D_jMenu = new javax.swing.JMenu();
        skeleton_jMenuItem = new javax.swing.JMenuItem();
        volume_jMenuItem = new javax.swing.JMenuItem();
        help_jMenu = new javax.swing.JMenu();
        help_jMenuItem = new javax.swing.JMenuItem();
        debug_jMenu = new javax.swing.JMenu();
        debug_jMenuItem = new javax.swing.JMenuItem();

        jCheckBox9.setText("jCheckBox9");

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("nTracer");
        setBounds(new java.awt.Rectangle(0, 0, 0, 0));

        main_jTabbedPane.setTabLayoutPolicy(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT);
        main_jTabbedPane.setFocusable(false);
        main_jTabbedPane.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        main_jTabbedPane.setMinimumSize(new java.awt.Dimension(0, 0));

        editBranch_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), "Branch(es)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N

        setPrimaryBranch_jButton.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        setPrimaryBranch_jButton.setForeground(new java.awt.Color(0, 102, 204));
        setPrimaryBranch_jButton.setText("Set Primary");
        setPrimaryBranch_jButton.setToolTipText("Turn a terminal branch into primary branch");
        setPrimaryBranch_jButton.setFocusable(false);
        setPrimaryBranch_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        setPrimaryBranch_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setPrimaryBranch_jButtonActionPerformed(evt);
            }
        });

        joinBranches_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        joinBranches_jButton.setForeground(new java.awt.Color(0, 153, 0));
        joinBranches_jButton.setText("Join");
        joinBranches_jButton.setToolTipText("Join 2 terminal branches");
        joinBranches_jButton.setFocusable(false);
        joinBranches_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        joinBranches_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                joinBranches_jButtonActionPerformed(evt);
            }
        });

        breakBranch_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        breakBranch_jButton.setForeground(new java.awt.Color(0, 153, 0));
        breakBranch_jButton.setText("Break");
        breakBranch_jButton.setToolTipText("Break a branch into two");
        breakBranch_jButton.setFocusable(false);
        breakBranch_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        breakBranch_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                breakBranch_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout editBranch_jPanelLayout = new javax.swing.GroupLayout(editBranch_jPanel);
        editBranch_jPanel.setLayout(editBranch_jPanelLayout);
        editBranch_jPanelLayout.setHorizontalGroup(
            editBranch_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(editBranch_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(editBranch_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(setPrimaryBranch_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(joinBranches_jButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(breakBranch_jButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        editBranch_jPanelLayout.setVerticalGroup(
            editBranch_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(editBranch_jPanelLayout.createSequentialGroup()
                .addComponent(setPrimaryBranch_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(joinBranches_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(breakBranch_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(5, 5, 5))
        );

        editBranch_jPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {breakBranch_jButton, joinBranches_jButton, setPrimaryBranch_jButton});

        editNeuron_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), "Neuron(s)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N

        collapseAllNeuron_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        collapseAllNeuron_jButton.setForeground(new java.awt.Color(0, 153, 153));
        collapseAllNeuron_jButton.setText("Collapse All");
        collapseAllNeuron_jButton.setToolTipText("Collapse neurons in Tree");
        collapseAllNeuron_jButton.setFocusable(false);
        collapseAllNeuron_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        collapseAllNeuron_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                collapseAllNeuron_jButtonActionPerformed(evt);
            }
        });

        expanAllNeuron_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        expanAllNeuron_jButton.setForeground(new java.awt.Color(0, 153, 153));
        expanAllNeuron_jButton.setText("Expand All");
        expanAllNeuron_jButton.setToolTipText("Expands all neurons in Tree");
        expanAllNeuron_jButton.setFocusable(false);
        expanAllNeuron_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        expanAllNeuron_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                expanAllNeuron_jButtonActionPerformed(evt);
            }
        });

        comnibeTwoNeuron_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        comnibeTwoNeuron_jButton.setForeground(new java.awt.Color(0, 0, 204));
        comnibeTwoNeuron_jButton.setText("Combine 2");
        comnibeTwoNeuron_jButton.setToolTipText("Combine 2 selected neurons");
        comnibeTwoNeuron_jButton.setFocusable(false);
        comnibeTwoNeuron_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        comnibeTwoNeuron_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comnibeTwoNeuron_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout editNeuron_jPanelLayout = new javax.swing.GroupLayout(editNeuron_jPanel);
        editNeuron_jPanel.setLayout(editNeuron_jPanelLayout);
        editNeuron_jPanelLayout.setHorizontalGroup(
            editNeuron_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(comnibeTwoNeuron_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(expanAllNeuron_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(collapseAllNeuron_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );
        editNeuron_jPanelLayout.setVerticalGroup(
            editNeuron_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, editNeuron_jPanelLayout.createSequentialGroup()
                .addComponent(comnibeTwoNeuron_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(expanAllNeuron_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(collapseAllNeuron_jButton)
                .addGap(41, 41, 41))
        );

        editSoma_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), "Soma(s)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N

        completeSomaSliceRoi_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        completeSomaSliceRoi_jButton.setForeground(new java.awt.Color(204, 102, 0));
        completeSomaSliceRoi_jButton.setText("Complete");
        completeSomaSliceRoi_jButton.setToolTipText("Complete soma slice ROI (trace and join end points) (HotKey ' o')");
        completeSomaSliceRoi_jButton.setFocusable(false);
        completeSomaSliceRoi_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        completeSomaSliceRoi_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                completeSomaSliceRoi_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout editSoma_jPanelLayout = new javax.swing.GroupLayout(editSoma_jPanel);
        editSoma_jPanel.setLayout(editSoma_jPanelLayout);
        editSoma_jPanelLayout.setHorizontalGroup(
            editSoma_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(completeSomaSliceRoi_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );
        editSoma_jPanelLayout.setVerticalGroup(
            editSoma_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(completeSomaSliceRoi_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, 37, Short.MAX_VALUE)
        );

        editConnection_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 102, 255)), "Connection", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 10), new java.awt.Color(0, 153, 153))); // NOI18N

        toggleConnection_jButton.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        toggleConnection_jButton.setForeground(new java.awt.Color(0, 153, 153));
        toggleConnection_jButton.setText("+ / -");
        toggleConnection_jButton.setToolTipText("Form/Erase connection between one point and another neuron (HotKey ' n ')");
        toggleConnection_jButton.setFocusable(false);
        toggleConnection_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleConnection_jButtonActionPerformed(evt);
            }
        });

        gotoConnection_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        gotoConnection_jButton.setForeground(new java.awt.Color(0, 153, 153));
        gotoConnection_jButton.setText("GOTO");
        gotoConnection_jButton.setToolTipText("Select and display the connected synapse (HotKey ' t ')");
        gotoConnection_jButton.setFocusable(false);
        gotoConnection_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gotoConnection_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout editConnection_jPanelLayout = new javax.swing.GroupLayout(editConnection_jPanel);
        editConnection_jPanel.setLayout(editConnection_jPanelLayout);
        editConnection_jPanelLayout.setHorizontalGroup(
            editConnection_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(toggleConnection_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(gotoConnection_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        editConnection_jPanelLayout.setVerticalGroup(
            editConnection_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(editConnection_jPanelLayout.createSequentialGroup()
                .addComponent(toggleConnection_jButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(gotoConnection_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        delete_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 51, 0)), "Delete", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(204, 51, 0))); // NOI18N

        deleteSomaSlice_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        deleteSomaSlice_jButton.setForeground(new java.awt.Color(153, 0, 153));
        deleteSomaSlice_jButton.setText("Soma(s)");
        deleteSomaSlice_jButton.setToolTipText("Delete selected soma slices");
        deleteSomaSlice_jButton.setFocusable(false);
        deleteSomaSlice_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        deleteSomaSlice_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSomaSlice_jButtonActionPerformed(evt);
            }
        });

        deletePoints_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        deletePoints_jButton.setForeground(new java.awt.Color(204, 51, 0));
        deletePoints_jButton.setText("Point(s)");
        deletePoints_jButton.setToolTipText("Delete selected points");
        deletePoints_jButton.setFocusable(false);
        deletePoints_jButton.setMargin(new java.awt.Insets(2, 4, 2, 4));
        deletePoints_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deletePoints_jButtonActionPerformed(evt);
            }
        });

        deleteOneNeuron_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        deleteOneNeuron_jButton.setForeground(new java.awt.Color(153, 102, 0));
        deleteOneNeuron_jButton.setText("Neuron(s)");
        deleteOneNeuron_jButton.setToolTipText("Delete selected whole neurons");
        deleteOneNeuron_jButton.setFocusable(false);
        deleteOneNeuron_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        deleteOneNeuron_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteOneNeuron_jButtonActionPerformed(evt);
            }
        });

        deleteOneBranch_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        deleteOneBranch_jButton.setForeground(new java.awt.Color(153, 102, 0));
        deleteOneBranch_jButton.setText("1 Branch");
        deleteOneBranch_jButton.setToolTipText("Delete 1 branch");
        deleteOneBranch_jButton.setFocusable(false);
        deleteOneBranch_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        deleteOneBranch_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteOneBranch_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout delete_jPanelLayout = new javax.swing.GroupLayout(delete_jPanel);
        delete_jPanel.setLayout(delete_jPanelLayout);
        delete_jPanelLayout.setHorizontalGroup(
            delete_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(deleteOneNeuron_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(deleteSomaSlice_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(deleteOneBranch_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(deletePoints_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );
        delete_jPanelLayout.setVerticalGroup(
            delete_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, delete_jPanelLayout.createSequentialGroup()
                .addComponent(deleteOneBranch_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteOneNeuron_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteSomaSlice_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deletePoints_jButton))
        );

        jumpTo_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 51, 0)), "JumpTo Next", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 10), new java.awt.Color(153, 51, 0))); // NOI18N

        jumpToNextSynapse_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jumpToNextSynapse_jButton.setForeground(new java.awt.Color(153, 51, 0));
        jumpToNextSynapse_jButton.setText("Synapse");
        jumpToNextSynapse_jButton.setToolTipText("Jump to next synapse (HotKey ' p ')");
        jumpToNextSynapse_jButton.setFocusable(false);
        jumpToNextSynapse_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jumpToNextSynapse_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jumpToNextSynapse_jButtonActionPerformed(evt);
            }
        });

        jumpToNextConnected_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jumpToNextConnected_jButton.setForeground(new java.awt.Color(153, 51, 0));
        jumpToNextConnected_jButton.setText("Connected");
        jumpToNextConnected_jButton.setToolTipText("Jump to next connected synapse (HotKey ' f ')");
        jumpToNextConnected_jButton.setFocusable(false);
        jumpToNextConnected_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jumpToNextConnected_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jumpToNextConnected_jButtonActionPerformed(evt);
            }
        });

        jumpToNextIncompleted_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jumpToNextIncompleted_jButton.setForeground(new java.awt.Color(255, 0, 0));
        jumpToNextIncompleted_jButton.setText("Incomplete");
        jumpToNextIncompleted_jButton.setToolTipText("Jump to next incompletely traced process (Hotkey 'i')");
        jumpToNextIncompleted_jButton.setFocusable(false);
        jumpToNextIncompleted_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jumpToNextIncompleted_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jumpToNextIncompleted_jButtonActionPerformed(evt);
            }
        });

        jumpToNextSelected_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jumpToNextSelected_jButton.setForeground(new java.awt.Color(0, 51, 204));
        jumpToNextSelected_jButton.setText("Selected");
        jumpToNextSelected_jButton.setToolTipText("Scroll to next selected process (Hotkey 'l')");
        jumpToNextSelected_jButton.setFocusable(false);
        jumpToNextSelected_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jumpToNextSelected_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jumpToNextSelected_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jumpTo_jPanelLayout = new javax.swing.GroupLayout(jumpTo_jPanel);
        jumpTo_jPanel.setLayout(jumpTo_jPanelLayout);
        jumpTo_jPanelLayout.setHorizontalGroup(
            jumpTo_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jumpTo_jPanelLayout.createSequentialGroup()
                .addGroup(jumpTo_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jumpToNextIncompleted_jButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jumpToNextSelected_jButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(jumpToNextConnected_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(jumpToNextSynapse_jButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jumpTo_jPanelLayout.setVerticalGroup(
            jumpTo_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jumpTo_jPanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(jumpToNextIncompleted_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jumpToNextSelected_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(19, 19, 19)
                .addComponent(jumpToNextSynapse_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jumpToNextConnected_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12))
        );

        editSynapse_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 102, 255)), "Synapse", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(0, 102, 255))); // NOI18N

        toggleSynapse_jButton.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        toggleSynapse_jButton.setForeground(new java.awt.Color(0, 102, 255));
        toggleSynapse_jButton.setText("Type +");
        toggleSynapse_jButton.setToolTipText("Form/Erase synapse at one point (HotKey ' e ')");
        toggleSynapse_jButton.setFocusable(false);
        toggleSynapse_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleSynapse_jButtonActionPerformed(evt);
            }
        });

        toggleSynapse_jButton1.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        toggleSynapse_jButton1.setForeground(new java.awt.Color(0, 102, 255));
        toggleSynapse_jButton1.setText("Type -");
        toggleSynapse_jButton1.setToolTipText("Form/Erase synapse at one point (HotKey ' e ')");
        toggleSynapse_jButton1.setFocusable(false);
        toggleSynapse_jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleSynapse_jButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout editSynapse_jPanelLayout = new javax.swing.GroupLayout(editSynapse_jPanel);
        editSynapse_jPanel.setLayout(editSynapse_jPanelLayout);
        editSynapse_jPanelLayout.setHorizontalGroup(
            editSynapse_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(toggleSynapse_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(toggleSynapse_jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        editSynapse_jPanelLayout.setVerticalGroup(
            editSynapse_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(editSynapse_jPanelLayout.createSequentialGroup()
                .addComponent(toggleSynapse_jButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(toggleSynapse_jButton1)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout editDisplay_jPanelLayout = new javax.swing.GroupLayout(editDisplay_jPanel);
        editDisplay_jPanel.setLayout(editDisplay_jPanelLayout);
        editDisplay_jPanelLayout.setHorizontalGroup(
            editDisplay_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, editDisplay_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(editDisplay_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jumpTo_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(editNeuron_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(editBranch_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(editDisplay_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(editConnection_jPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(editSynapse_jPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(editSoma_jPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(delete_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(45, 45, 45))
        );
        editDisplay_jPanelLayout.setVerticalGroup(
            editDisplay_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(editDisplay_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(editDisplay_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(editDisplay_jPanelLayout.createSequentialGroup()
                        .addComponent(editBranch_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(editNeuron_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 118, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jumpTo_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(editDisplay_jPanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(delete_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(editSoma_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(editSynapse_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(editConnection_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(219, 219, 219))))
        );

        main_jTabbedPane.addTab("Edit     ", null, editDisplay_jPanel, "");

        all_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), "All Traced", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N

        overlayAllNeuron_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayAllNeuron_jCheckBox.setForeground(new java.awt.Color(0, 51, 204));
        overlayAllNeuron_jCheckBox.setSelected(true);
        overlayAllNeuron_jCheckBox.setText("Neuron");
        overlayAllNeuron_jCheckBox.setFocusable(false);
        overlayAllNeuron_jCheckBox.setMaximumSize(new java.awt.Dimension(77, 13));
        overlayAllNeuron_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlayAllNeuron_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayAllNeuron_jCheckBoxActionPerformed(evt);
            }
        });

        overlayAllSoma_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayAllSoma_jCheckBox.setForeground(new java.awt.Color(0, 153, 0));
        overlayAllSoma_jCheckBox.setSelected(true);
        overlayAllSoma_jCheckBox.setText("Soma");
        overlayAllSoma_jCheckBox.setFocusable(false);
        overlayAllSoma_jCheckBox.setMaximumSize(new java.awt.Dimension(77, 13));
        overlayAllSoma_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlayAllSoma_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayAllSoma_jCheckBoxActionPerformed(evt);
            }
        });

        overlayAllSynapse_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayAllSynapse_jCheckBox.setForeground(new java.awt.Color(153, 153, 0));
        overlayAllSynapse_jCheckBox.setSelected(true);
        overlayAllSynapse_jCheckBox.setText("Synapse");
        overlayAllSynapse_jCheckBox.setFocusable(false);
        overlayAllSynapse_jCheckBox.setMaximumSize(new java.awt.Dimension(77, 13));
        overlayAllSynapse_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlayAllSynapse_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayAllSynapse_jCheckBoxActionPerformed(evt);
            }
        });

        blank_jCheckBox.setText("Blank");
        blank_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                blank_jCheckBoxActionPerformed(evt);
            }
        });

        overlayAllName_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayAllName_jCheckBox.setForeground(new java.awt.Color(204, 0, 204));
        overlayAllName_jCheckBox.setText("Name");
        overlayAllName_jCheckBox.setToolTipText("Font size can be set in ImageJ menu");
        overlayAllName_jCheckBox.setFocusable(false);
        overlayAllName_jCheckBox.setMaximumSize(new java.awt.Dimension(77, 13));
        overlayAllName_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlayAllName_jCheckBox.setPreferredSize(new java.awt.Dimension(77, 13));
        overlayAllName_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayAllName_jCheckBoxActionPerformed(evt);
            }
        });

        overlayAllConnection_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayAllConnection_jCheckBox.setForeground(new java.awt.Color(51, 153, 0));
        overlayAllConnection_jCheckBox.setSelected(true);
        overlayAllConnection_jCheckBox.setText("Connection");
        overlayAllConnection_jCheckBox.setFocusable(false);
        overlayAllConnection_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlayAllConnection_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayAllConnection_jCheckBoxActionPerformed(evt);
            }
        });

        extendAllDisplayPoints_jSpinner.setModel(new javax.swing.SpinnerNumberModel(11, 1, 999, 5));
        extendAllDisplayPoints_jSpinner.setFocusable(false);
        extendAllDisplayPoints_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                extendAllDisplayPoints_jSpinnerStateChanged(evt);
            }
        });

        allPlusMinus_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        allPlusMinus_jLabel.setText("+ / -");

        overlayAllPoints_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayAllPoints_jCheckBox.setText("All Points");
        overlayAllPoints_jCheckBox.setFocusable(false);
        overlayAllPoints_jCheckBox.setMaximumSize(new java.awt.Dimension(77, 13));
        overlayAllPoints_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlayAllPoints_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayAllPoints_jCheckBoxActionPerformed(evt);
            }
        });

        lineWidthOffset_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        lineWidthOffset_jLabel.setText("Line-width offset");

        allNeuronLineWidthOffset_jSpinner.setModel(new javax.swing.SpinnerNumberModel(2, 0, 99, 1));
        allNeuronLineWidthOffset_jSpinner.setToolTipText("Set line width offset for overlaying all neuron tracing results.");
        allNeuronLineWidthOffset_jSpinner.setFocusable(false);
        allNeuronLineWidthOffset_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                allNeuronLineWidthOffset_jSpinnerStateChanged(evt);
            }
        });

        overlayAllSpine_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayAllSpine_jCheckBox.setSelected(true);
        overlayAllSpine_jCheckBox.setText("Spine");
        overlayAllSpine_jCheckBox.setFocusable(false);
        overlayAllSpine_jCheckBox.setMaximumSize(new java.awt.Dimension(77, 13));
        overlayAllSpine_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlayAllSpine_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayAllSpine_jCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout all_jPanelLayout = new javax.swing.GroupLayout(all_jPanel);
        all_jPanel.setLayout(all_jPanelLayout);
        all_jPanelLayout.setHorizontalGroup(
            all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(all_jPanelLayout.createSequentialGroup()
                .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(all_jPanelLayout.createSequentialGroup()
                        .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(overlayAllName_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(overlayAllSoma_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, 0)
                        .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(overlayAllSynapse_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(overlayAllSpine_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(all_jPanelLayout.createSequentialGroup()
                        .addComponent(overlayAllNeuron_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(overlayAllConnection_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(blank_jCheckBox)
                    .addGroup(all_jPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(all_jPanelLayout.createSequentialGroup()
                                .addComponent(lineWidthOffset_jLabel)
                                .addGap(18, 18, 18)
                                .addComponent(allNeuronLineWidthOffset_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(all_jPanelLayout.createSequentialGroup()
                                .addComponent(allPlusMinus_jLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(all_jPanelLayout.createSequentialGroup()
                                        .addGap(47, 47, 47)
                                        .addComponent(overlayAllPoints_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(extendAllDisplayPoints_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE))))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        all_jPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {overlayAllName_jCheckBox, overlayAllNeuron_jCheckBox, overlayAllPoints_jCheckBox, overlayAllSoma_jCheckBox, overlayAllSynapse_jCheckBox});

        all_jPanelLayout.setVerticalGroup(
            all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(all_jPanelLayout.createSequentialGroup()
                .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(extendAllDisplayPoints_jSpinner)
                    .addComponent(overlayAllPoints_jCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(allPlusMinus_jLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(overlayAllName_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(overlayAllSpine_jCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(overlayAllSoma_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(overlayAllSynapse_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(overlayAllNeuron_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(overlayAllConnection_jCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addComponent(blank_jCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(all_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lineWidthOffset_jLabel)
                    .addComponent(allNeuronLineWidthOffset_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        all_jPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {overlayAllName_jCheckBox, overlayAllNeuron_jCheckBox, overlayAllSoma_jCheckBox, overlayAllSynapse_jCheckBox});

        selected_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), "Selected", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N

        overlaySelectedSoma_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlaySelectedSoma_jCheckBox.setForeground(new java.awt.Color(0, 153, 0));
        overlaySelectedSoma_jCheckBox.setSelected(true);
        overlaySelectedSoma_jCheckBox.setText("Soma");
        overlaySelectedSoma_jCheckBox.setFocusable(false);
        overlaySelectedSoma_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlaySelectedSoma_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlaySelectedSoma_jCheckBoxActionPerformed(evt);
            }
        });

        overlaySelectedBranch_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlaySelectedBranch_jCheckBox.setForeground(new java.awt.Color(51, 0, 102));
        overlaySelectedBranch_jCheckBox.setSelected(true);
        overlaySelectedBranch_jCheckBox.setText("Branch");
        overlaySelectedBranch_jCheckBox.setFocusable(false);
        overlaySelectedBranch_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlaySelectedBranch_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlaySelectedBranch_jCheckBoxActionPerformed(evt);
            }
        });

        overlaySelectedName_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlaySelectedName_jCheckBox.setForeground(new java.awt.Color(204, 0, 204));
        overlaySelectedName_jCheckBox.setSelected(true);
        overlaySelectedName_jCheckBox.setText("Name");
        overlaySelectedName_jCheckBox.setToolTipText("Font size can be set in ImageJ menu");
        overlaySelectedName_jCheckBox.setFocusable(false);
        overlaySelectedName_jCheckBox.setMargin(new java.awt.Insets(0, 2, 0, 2));
        overlaySelectedName_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlaySelectedName_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlaySelectedName_jCheckBoxActionPerformed(evt);
            }
        });

        overlaySelectedNeuron_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlaySelectedNeuron_jCheckBox.setForeground(new java.awt.Color(0, 51, 204));
        overlaySelectedNeuron_jCheckBox.setSelected(true);
        overlaySelectedNeuron_jCheckBox.setText("Neuron");
        overlaySelectedNeuron_jCheckBox.setFocusable(false);
        overlaySelectedNeuron_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlaySelectedNeuron_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlaySelectedNeuron_jCheckBoxActionPerformed(evt);
            }
        });

        overlayAllSelectedPoints_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayAllSelectedPoints_jCheckBox.setText("All Points");
        overlayAllSelectedPoints_jCheckBox.setFocusable(false);
        overlayAllSelectedPoints_jCheckBox.setMaximumSize(new java.awt.Dimension(77, 13));
        overlayAllSelectedPoints_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlayAllSelectedPoints_jCheckBox.setPreferredSize(new java.awt.Dimension(77, 10));
        overlayAllSelectedPoints_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayAllSelectedPoints_jCheckBoxActionPerformed(evt);
            }
        });

        overlaySelectedArbor_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlaySelectedArbor_jCheckBox.setForeground(new java.awt.Color(0, 102, 102));
        overlaySelectedArbor_jCheckBox.setSelected(true);
        overlaySelectedArbor_jCheckBox.setText("Arbor");
        overlaySelectedArbor_jCheckBox.setFocusable(false);
        overlaySelectedArbor_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlaySelectedArbor_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlaySelectedArbor_jCheckBoxActionPerformed(evt);
            }
        });

        overlaySelectedSynapse_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        overlaySelectedSynapse_jCheckBox.setForeground(new java.awt.Color(153, 153, 0));
        overlaySelectedSynapse_jCheckBox.setSelected(true);
        overlaySelectedSynapse_jCheckBox.setText("Synapse");
        overlaySelectedSynapse_jCheckBox.setFocusable(false);
        overlaySelectedSynapse_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlaySelectedSynapse_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlaySelectedSynapse_jCheckBoxActionPerformed(evt);
            }
        });

        overlaySelectedConnection_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlaySelectedConnection_jCheckBox.setForeground(new java.awt.Color(51, 153, 0));
        overlaySelectedConnection_jCheckBox.setSelected(true);
        overlaySelectedConnection_jCheckBox.setText("Con...");
        overlaySelectedConnection_jCheckBox.setFocusable(false);
        overlaySelectedConnection_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlaySelectedConnection_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlaySelectedConnection_jCheckBoxActionPerformed(evt);
            }
        });

        overlaySelectedSpine_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlaySelectedSpine_jCheckBox.setSelected(true);
        overlaySelectedSpine_jCheckBox.setText("Spine");
        overlaySelectedSpine_jCheckBox.setFocusable(false);
        overlaySelectedSpine_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        overlaySelectedSpine_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlaySelectedSpine_jCheckBoxActionPerformed(evt);
            }
        });

        overlayPointBox_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        overlayPointBox_jCheckBox.setForeground(new java.awt.Color(204, 0, 51));
        overlayPointBox_jCheckBox.setSelected(true);
        overlayPointBox_jCheckBox.setText("Point");
        overlayPointBox_jCheckBox.setFocusable(false);
        overlayPointBox_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayPointBox_jCheckBoxActionPerformed(evt);
            }
        });

        brainbowColor_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        brainbowColor_jCheckBox.setForeground(new java.awt.Color(204, 102, 0));
        brainbowColor_jCheckBox.setSelected(true);
        brainbowColor_jCheckBox.setText("Color");
        brainbowColor_jCheckBox.setToolTipText("Font size can be set in ImageJ menu");
        brainbowColor_jCheckBox.setFocusable(false);
        brainbowColor_jCheckBox.setMargin(new java.awt.Insets(0, 2, 0, 2));
        brainbowColor_jCheckBox.setMinimumSize(new java.awt.Dimension(77, 10));
        brainbowColor_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                brainbowColor_jCheckBoxActionPerformed(evt);
            }
        });

        selectedPlusMinus_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        selectedPlusMinus_jLabel.setText("+ / -");

        extendSelectedDisplayPoints_jSpinner.setModel(new javax.swing.SpinnerNumberModel(11, 1, 999, 5));
        extendSelectedDisplayPoints_jSpinner.setFocusable(false);
        extendSelectedDisplayPoints_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                extendSelectedDisplayPoints_jSpinnerStateChanged(evt);
            }
        });

        somaLineWidth_jSpinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, 99, 1));
        somaLineWidth_jSpinner.setToolTipText("Set line width for overlaying tracing results.");
        somaLineWidth_jSpinner.setFocusable(false);
        somaLineWidth_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                somaLineWidth_jSpinnerStateChanged(evt);
            }
        });

        neuronLineWidth_jSpinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, 99, 1));
        neuronLineWidth_jSpinner.setToolTipText("Set line width for overlaying tracing results.");
        neuronLineWidth_jSpinner.setFocusable(false);
        neuronLineWidth_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                neuronLineWidth_jSpinnerStateChanged(evt);
            }
        });

        arborLineWidth_jSpinner.setModel(new javax.swing.SpinnerNumberModel(2, 1, 99, 1));
        arborLineWidth_jSpinner.setToolTipText("Set line width for overlaying tracing results.");
        arborLineWidth_jSpinner.setFocusable(false);
        arborLineWidth_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                arborLineWidth_jSpinnerStateChanged(evt);
            }
        });

        branchLineWidth_jSpinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, 99, 1));
        branchLineWidth_jSpinner.setToolTipText("Set line width for overlaying tracing results.");
        branchLineWidth_jSpinner.setFocusable(false);
        branchLineWidth_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                branchLineWidth_jSpinnerStateChanged(evt);
            }
        });

        spineLineWidth_jSpinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, 99, 1));
        spineLineWidth_jSpinner.setToolTipText("Set line width for overlaying tracing results.");
        spineLineWidth_jSpinner.setFocusable(false);
        spineLineWidth_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spineLineWidth_jSpinnerStateChanged(evt);
            }
        });

        pointBoxLineWidth_jSpinner.setModel(new javax.swing.SpinnerNumberModel(2, 1, 99, 1));
        pointBoxLineWidth_jSpinner.setToolTipText("Set line width for overlaying tracing results.");
        pointBoxLineWidth_jSpinner.setFocusable(false);
        pointBoxLineWidth_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                pointBoxLineWidth_jSpinnerStateChanged(evt);
            }
        });

        synapseRadius_jSpinner.setModel(new javax.swing.SpinnerNumberModel(2, 1, 99, 1));
        synapseRadius_jSpinner.setToolTipText("Set line width for overlaying tracing results.");
        synapseRadius_jSpinner.setFocusable(false);
        synapseRadius_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                synapseRadius_jSpinnerStateChanged(evt);
            }
        });

        pointBoxRadiu_jSpinner.setModel(new javax.swing.SpinnerNumberModel(8, 1, 99, 1));
        pointBoxRadiu_jSpinner.setToolTipText("Set line width for overlaying tracing results.");
        pointBoxRadiu_jSpinner.setFocusable(false);
        pointBoxRadiu_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                pointBoxRadiu_jSpinnerStateChanged(evt);
            }
        });

        updateDisplay_jButton.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        updateDisplay_jButton.setForeground(new java.awt.Color(0, 153, 153));
        updateDisplay_jButton.setText("Update");
        updateDisplay_jButton.setToolTipText("update display (Hotkey 'u')");
        updateDisplay_jButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        updateDisplay_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateDisplay_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout selected_jPanelLayout = new javax.swing.GroupLayout(selected_jPanel);
        selected_jPanel.setLayout(selected_jPanelLayout);
        selected_jPanelLayout.setHorizontalGroup(
            selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(selected_jPanelLayout.createSequentialGroup()
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(selected_jPanelLayout.createSequentialGroup()
                        .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(overlaySelectedBranch_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(overlaySelectedArbor_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(overlaySelectedNeuron_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(overlaySelectedSoma_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(overlayPointBox_jCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(overlaySelectedSynapse_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(overlaySelectedSpine_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE)
                        .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(pointBoxLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(synapseRadius_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(spineLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(branchLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(arborLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(neuronLineWidth_jSpinner)
                            .addComponent(somaLineWidth_jSpinner))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE)
                        .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(pointBoxRadiu_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(overlaySelectedConnection_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(selected_jPanelLayout.createSequentialGroup()
                        .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(selected_jPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(selectedPlusMinus_jLabel)
                                .addGap(3, 3, 3)
                                .addComponent(extendSelectedDisplayPoints_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(overlayAllSelectedPoints_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 96, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(selected_jPanelLayout.createSequentialGroup()
                                .addComponent(overlaySelectedName_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(updateDisplay_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(brainbowColor_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        selected_jPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {overlayPointBox_jCheckBox, overlaySelectedArbor_jCheckBox, overlaySelectedBranch_jCheckBox, overlaySelectedNeuron_jCheckBox, overlaySelectedSoma_jCheckBox, overlaySelectedSynapse_jCheckBox});

        selected_jPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {arborLineWidth_jSpinner, branchLineWidth_jSpinner, neuronLineWidth_jSpinner, pointBoxLineWidth_jSpinner, pointBoxRadiu_jSpinner, somaLineWidth_jSpinner, spineLineWidth_jSpinner, synapseRadius_jSpinner});

        selected_jPanelLayout.setVerticalGroup(
            selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(selected_jPanelLayout.createSequentialGroup()
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(selectedPlusMinus_jLabel)
                    .addComponent(extendSelectedDisplayPoints_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(overlayAllSelectedPoints_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(3, 3, 3)
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(overlaySelectedName_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(brainbowColor_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(updateDisplay_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(overlaySelectedSoma_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(somaLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(neuronLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(overlaySelectedNeuron_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 7, Short.MAX_VALUE)
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(overlaySelectedArbor_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(arborLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 7, Short.MAX_VALUE)
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(branchLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(overlaySelectedBranch_jCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, 14, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(overlaySelectedSpine_jCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(spineLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(synapseRadius_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(overlaySelectedConnection_jCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(overlaySelectedSynapse_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(selected_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(overlayPointBox_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pointBoxLineWidth_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pointBoxRadiu_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        selected_jPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {neuronLineWidth_jSpinner, overlayAllSelectedPoints_jCheckBox, overlaySelectedArbor_jCheckBox, overlaySelectedBranch_jCheckBox, overlaySelectedName_jCheckBox, overlaySelectedNeuron_jCheckBox, overlaySelectedSoma_jCheckBox, overlaySelectedSynapse_jCheckBox});

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        jPanel1.setName(""); // NOI18N

        jButton1.setText("Choose File");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Red", "Green", "Blue" }));
        jComboBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox1ActionPerformed(evt);
            }
        });

        jLabel6.setText("ROI File:");

        jLabel7.setText("ROI Color:");

        jButton2.setText("Clear ROIs");
        jButton2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton2MouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jLabel6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButton2)
                .addContainerGap())
        );

        javax.swing.GroupLayout overlay_jPanelLayout = new javax.swing.GroupLayout(overlay_jPanel);
        overlay_jPanel.setLayout(overlay_jPanelLayout);
        overlay_jPanelLayout.setHorizontalGroup(
            overlay_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(overlay_jPanelLayout.createSequentialGroup()
                .addGroup(overlay_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(all_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(selected_jPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(overlay_jPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        overlay_jPanelLayout.setVerticalGroup(
            overlay_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(overlay_jPanelLayout.createSequentialGroup()
                .addComponent(all_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selected_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(211, Short.MAX_VALUE))
        );

        jPanel1.getAccessibleContext().setAccessibleName("");

        main_jTabbedPane.addTab("Overlay  ", overlay_jPanel);

        tracingMethod_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 0, 0)), "Tracing", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(255, 0, 0))); // NOI18N

        tracingMethod_buttonGroup.add(manualTracing_jRadioButton);
        manualTracing_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        manualTracing_jRadioButton.setForeground(new java.awt.Color(255, 51, 51));
        manualTracing_jRadioButton.setSelected(true);
        manualTracing_jRadioButton.setText("Interactive");
        manualTracing_jRadioButton.setFocusable(false);
        manualTracing_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manualTracing_jRadioButtonActionPerformed(evt);
            }
        });

        tracingMethod_buttonGroup.add(semiAutoTracing_jRadioButton);
        semiAutoTracing_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        semiAutoTracing_jRadioButton.setForeground(new java.awt.Color(255, 51, 51));
        semiAutoTracing_jRadioButton.setText("Semi-Auto");
        semiAutoTracing_jRadioButton.setEnabled(false);
        semiAutoTracing_jRadioButton.setFocusable(false);
        semiAutoTracing_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                semiAutoTracing_jRadioButtonActionPerformed(evt);
            }
        });

        linkRadius_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        linkRadius_jLabel.setForeground(new java.awt.Color(255, 51, 51));
        linkRadius_jLabel.setText("Step");

        linkRadius_jSpinner.setModel(new javax.swing.SpinnerNumberModel(10, 2, 30, 2));
        linkRadius_jSpinner.setFocusable(false);
        linkRadius_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                linkRadius_jSpinnerStateChanged(evt);
            }
        });

        javax.swing.GroupLayout tracingMethod_jPanelLayout = new javax.swing.GroupLayout(tracingMethod_jPanel);
        tracingMethod_jPanel.setLayout(tracingMethod_jPanelLayout);
        tracingMethod_jPanelLayout.setHorizontalGroup(
            tracingMethod_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(manualTracing_jRadioButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(semiAutoTracing_jRadioButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(tracingMethod_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(linkRadius_jLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(linkRadius_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        tracingMethod_jPanelLayout.setVerticalGroup(
            tracingMethod_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tracingMethod_jPanelLayout.createSequentialGroup()
                .addComponent(manualTracing_jRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(semiAutoTracing_jRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tracingMethod_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(linkRadius_jLabel)
                    .addComponent(linkRadius_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        colorSamplingRadius_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 0, 102)), "Sampling Radius (pixels)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(153, 0, 102))); // NOI18N

        xyRadius_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        xyRadius_jLabel.setForeground(new java.awt.Color(153, 0, 102));
        xyRadius_jLabel.setText("X, Y");

        xyRadius_jSpinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, 30, 1));
        xyRadius_jSpinner.setFocusable(false);
        xyRadius_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                xyRadius_jSpinnerStateChanged(evt);
            }
        });

        zRadius_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        zRadius_jLabel.setForeground(new java.awt.Color(153, 0, 102));
        zRadius_jLabel.setText("Z");

        zRadius_jSpinner.setModel(new javax.swing.SpinnerNumberModel(2, 0, 15, 1));
        zRadius_jSpinner.setFocusable(false);
        zRadius_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zRadius_jSpinnerStateChanged(evt);
            }
        });

        javax.swing.GroupLayout colorSamplingRadius_jPanelLayout = new javax.swing.GroupLayout(colorSamplingRadius_jPanel);
        colorSamplingRadius_jPanel.setLayout(colorSamplingRadius_jPanelLayout);
        colorSamplingRadius_jPanelLayout.setHorizontalGroup(
            colorSamplingRadius_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(colorSamplingRadius_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(xyRadius_jLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(xyRadius_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(zRadius_jLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(zRadius_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18))
        );
        colorSamplingRadius_jPanelLayout.setVerticalGroup(
            colorSamplingRadius_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(colorSamplingRadius_jPanelLayout.createSequentialGroup()
                .addGroup(colorSamplingRadius_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(colorSamplingRadius_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(xyRadius_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(xyRadius_jLabel))
                    .addGroup(colorSamplingRadius_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(zRadius_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(zRadius_jLabel)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        samplingTolerance_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 102, 102)), "Sampling Tolerance", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(0, 102, 102))); // NOI18N

        colorThreshold_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        colorThreshold_jLabel.setForeground(new java.awt.Color(0, 102, 102));
        colorThreshold_jLabel.setText("Color");

        colorThreshold_jSpinner.setModel(new javax.swing.SpinnerNumberModel(Float.valueOf(0.15f), Float.valueOf(0.1f), Float.valueOf(0.9f), Float.valueOf(0.05f)));
        colorThreshold_jSpinner.setFocusable(false);
        colorThreshold_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                colorThreshold_jSpinnerStateChanged(evt);
            }
        });

        intensityThreshold_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        intensityThreshold_jLabel.setForeground(new java.awt.Color(0, 102, 102));
        intensityThreshold_jLabel.setText("Int");

        intensityThreshold_jSpinner.setModel(new javax.swing.SpinnerNumberModel(Float.valueOf(0.15f), Float.valueOf(0.1f), Float.valueOf(0.9f), Float.valueOf(0.05f)));
        intensityThreshold_jSpinner.setFocusable(false);
        intensityThreshold_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                intensityThreshold_jSpinnerStateChanged(evt);
            }
        });

        javax.swing.GroupLayout samplingTolerance_jPanelLayout = new javax.swing.GroupLayout(samplingTolerance_jPanel);
        samplingTolerance_jPanel.setLayout(samplingTolerance_jPanelLayout);
        samplingTolerance_jPanelLayout.setHorizontalGroup(
            samplingTolerance_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, samplingTolerance_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(colorThreshold_jLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(colorThreshold_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(intensityThreshold_jLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(intensityThreshold_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(72, 72, 72))
        );
        samplingTolerance_jPanelLayout.setVerticalGroup(
            samplingTolerance_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(samplingTolerance_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(intensityThreshold_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(intensityThreshold_jLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(colorThreshold_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(colorThreshold_jLabel))
        );

        clearPoints_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        clearPoints_jButton.setText("Clear Start/End Points");
        clearPoints_jButton.setToolTipText("Clear Start and/or End points (HotKey ' c ')");
        clearPoints_jButton.setFocusable(false);
        clearPoints_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearPoints_jButtonActionPerformed(evt);
            }
        });

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 0, 0)), "Trace !", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(204, 0, 0))); // NOI18N

        traceNeurite_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        traceNeurite_jButton.setForeground(new java.awt.Color(204, 0, 0));
        traceNeurite_jButton.setText("Neurite");
        traceNeurite_jButton.setToolTipText("Trace neurite (HotKey ' a ')");
        traceNeurite_jButton.setFocusable(false);
        traceNeurite_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        traceNeurite_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                traceNeurite_jButtonActionPerformed(evt);
            }
        });

        traceSoma_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        traceSoma_jButton.setForeground(new java.awt.Color(204, 0, 0));
        traceSoma_jButton.setText("Soma");
        traceSoma_jButton.setToolTipText("Trace soma (HotKey ' s ')");
        traceSoma_jButton.setFocusable(false);
        traceSoma_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        traceSoma_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                traceSoma_jButtonActionPerformed(evt);
            }
        });

        traceSpine_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        traceSpine_jButton.setForeground(new java.awt.Color(204, 0, 0));
        traceSpine_jButton.setText("Spine");
        traceSpine_jButton.setToolTipText("Trace spine (HotKey ' d ')");
        traceSpine_jButton.setFocusable(false);
        traceSpine_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        traceSpine_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                traceSpine_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(traceNeurite_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(traceSoma_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(traceSpine_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addGap(0, 7, Short.MAX_VALUE)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(traceNeurite_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(traceSoma_jButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(traceSpine_jButton)))
                .addGap(19, 19, 19))
        );

        labeling_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 153, 153)), "Labeling", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(0, 153, 153))); // NOI18N

        labelingMethod_buttonGroup.add(membraneLabel_jRadioButton);
        membraneLabel_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        membraneLabel_jRadioButton.setForeground(new java.awt.Color(0, 153, 153));
        membraneLabel_jRadioButton.setSelected(true);
        membraneLabel_jRadioButton.setText("Memb");
        membraneLabel_jRadioButton.setFocusable(false);

        labelingMethod_buttonGroup.add(cytoplasmLabel_jRadioButton);
        cytoplasmLabel_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        cytoplasmLabel_jRadioButton.setForeground(new java.awt.Color(0, 153, 153));
        cytoplasmLabel_jRadioButton.setText("Cyto");
        cytoplasmLabel_jRadioButton.setEnabled(false);
        cytoplasmLabel_jRadioButton.setFocusable(false);

        tracingMethod_buttonGroup.add(autoTracing_jRadioButton);
        autoTracing_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        autoTracing_jRadioButton.setForeground(new java.awt.Color(255, 51, 51));
        autoTracing_jRadioButton.setText("Auto");
        autoTracing_jRadioButton.setEnabled(false);
        autoTracing_jRadioButton.setFocusable(false);
        autoTracing_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoTracing_jRadioButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout labeling_jPanelLayout = new javax.swing.GroupLayout(labeling_jPanel);
        labeling_jPanel.setLayout(labeling_jPanelLayout);
        labeling_jPanelLayout.setHorizontalGroup(
            labeling_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(labeling_jPanelLayout.createSequentialGroup()
                .addGroup(labeling_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(membraneLabel_jRadioButton)
                    .addComponent(cytoplasmLabel_jRadioButton)
                    .addComponent(autoTracing_jRadioButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        labeling_jPanelLayout.setVerticalGroup(
            labeling_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(labeling_jPanelLayout.createSequentialGroup()
                .addComponent(membraneLabel_jRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cytoplasmLabel_jRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(autoTracing_jRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        toogleTracingCompleteness_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toogleTracingCompleteness_jButton.setForeground(new java.awt.Color(153, 0, 153));
        toogleTracingCompleteness_jButton.setText("Change 'completeness'");
        toogleTracingCompleteness_jButton.setToolTipText("Change process tracing incompleteness (Hotkey 'x')");
        toogleTracingCompleteness_jButton.setFocusable(false);
        toogleTracingCompleteness_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toogleTracingCompleteness_jButtonActionPerformed(evt);
            }
        });

        zProjectionInterval_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 0, 102)), "Substack Z Projection", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(153, 0, 102))); // NOI18N

        zProjectionInterval_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        zProjectionInterval_jLabel.setForeground(new java.awt.Color(153, 0, 102));
        zProjectionInterval_jLabel.setText("Z+/- ");

        zProjectionInterval_jSpinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, 20, 1));
        zProjectionInterval_jSpinner.setFocusable(false);
        zProjectionInterval_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zProjectionInterval_jSpinnerStateChanged(evt);
            }
        });

        xyProjectionArea_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        xyProjectionArea_jLabel.setForeground(new java.awt.Color(153, 0, 102));
        xyProjectionArea_jLabel.setText("X/Y");

        xyProjectionArea_jSpinner.setModel(new javax.swing.SpinnerListModel(new String[] {"500", "1000", "1500", "2000", "2500", "3000", "3500", "4000", "4500"}));
        xyProjectionArea_jSpinner.setFocusable(false);
        xyProjectionArea_jSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                xyProjectionArea_jSpinnerStateChanged(evt);
            }
        });

        projectionUpdate_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        projectionUpdate_jCheckBox.setSelected(true);
        projectionUpdate_jCheckBox.setText("continuous update?");
        projectionUpdate_jCheckBox.setFocusable(false);

        javax.swing.GroupLayout zProjectionInterval_jPanelLayout = new javax.swing.GroupLayout(zProjectionInterval_jPanel);
        zProjectionInterval_jPanel.setLayout(zProjectionInterval_jPanelLayout);
        zProjectionInterval_jPanelLayout.setHorizontalGroup(
            zProjectionInterval_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(zProjectionInterval_jPanelLayout.createSequentialGroup()
                .addComponent(projectionUpdate_jCheckBox)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(zProjectionInterval_jPanelLayout.createSequentialGroup()
                .addComponent(zProjectionInterval_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(zProjectionInterval_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 60, Short.MAX_VALUE)
                .addComponent(xyProjectionArea_jLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(xyProjectionArea_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        zProjectionInterval_jPanelLayout.setVerticalGroup(
            zProjectionInterval_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(zProjectionInterval_jPanelLayout.createSequentialGroup()
                .addGroup(zProjectionInterval_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(xyProjectionArea_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(xyProjectionArea_jLabel)
                    .addComponent(zProjectionInterval_jSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(zProjectionInterval_jLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(projectionUpdate_jCheckBox)
                .addGap(0, 5, Short.MAX_VALUE))
        );

        channel_jPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), "Channel", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N
        channel_jPanel.setMaximumSize(new java.awt.Dimension(349, 149));
        channel_jPanel.setMinimumSize(new java.awt.Dimension(349, 149));
        channel_jPanel.setPreferredSize(new java.awt.Dimension(197, 149));

        toggleColor_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleColor_jLabel.setText("Toggle Color");

        channelColor_buttonGroup.add(r_jRadioButton);
        r_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        r_jRadioButton.setForeground(new java.awt.Color(204, 0, 51));
        r_jRadioButton.setText("R");
        r_jRadioButton.setFocusable(false);
        r_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                r_jRadioButtonActionPerformed(evt);
            }
        });

        channelColor_buttonGroup.add(g_jRadioButton);
        g_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        g_jRadioButton.setForeground(new java.awt.Color(0, 153, 51));
        g_jRadioButton.setText("G");
        g_jRadioButton.setFocusable(false);
        g_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                g_jRadioButtonActionPerformed(evt);
            }
        });

        channelColor_buttonGroup.add(b_jRadioButton);
        b_jRadioButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        b_jRadioButton.setForeground(new java.awt.Color(0, 102, 204));
        b_jRadioButton.setText("B");
        b_jRadioButton.setFocusable(false);
        b_jRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                b_jRadioButtonActionPerformed(evt);
            }
        });

        analysisChannel_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisChannel_jLabel.setText("Analysis");

        analysisCh1_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisCh1_jCheckBox.setText("1");
        analysisCh1_jCheckBox.setFocusable(false);
        analysisCh1_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                analysisCh1_jCheckBoxActionPerformed(evt);
            }
        });

        analysisCh2_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisCh2_jCheckBox.setText("2");
        analysisCh2_jCheckBox.setFocusable(false);
        analysisCh2_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                analysisCh2_jCheckBoxActionPerformed(evt);
            }
        });

        analysisCh3_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisCh3_jCheckBox.setText("3");
        analysisCh3_jCheckBox.setFocusable(false);
        analysisCh3_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                analysisCh3_jCheckBoxActionPerformed(evt);
            }
        });

        analysisCh4_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisCh4_jCheckBox.setText("4");
        analysisCh4_jCheckBox.setFocusable(false);
        analysisCh4_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                analysisCh4_jCheckBoxActionPerformed(evt);
            }
        });

        analysisCh5_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisCh5_jCheckBox.setText("5");
        analysisCh5_jCheckBox.setFocusable(false);
        analysisCh5_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                analysisCh5_jCheckBoxActionPerformed(evt);
            }
        });

        analysisCh6_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisCh6_jCheckBox.setText("6");
        analysisCh6_jCheckBox.setFocusable(false);
        analysisCh6_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                analysisCh6_jCheckBoxActionPerformed(evt);
            }
        });

        analysisCh7_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisCh7_jCheckBox.setText("7");
        analysisCh7_jCheckBox.setFocusable(false);
        analysisCh7_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                analysisCh7_jCheckBoxActionPerformed(evt);
            }
        });

        analysisCh8_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        analysisCh8_jCheckBox.setText("8");
        analysisCh8_jCheckBox.setFocusable(false);
        analysisCh8_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                analysisCh8_jCheckBoxActionPerformed(evt);
            }
        });

        toggleChannel_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleChannel_jLabel.setText("Toggle");

        toggleCh1_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleCh1_jCheckBox.setText("1");
        toggleCh1_jCheckBox.setFocusable(false);
        toggleCh1_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleCh1_jCheckBoxActionPerformed(evt);
            }
        });

        toggleCh2_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleCh2_jCheckBox.setText("2");
        toggleCh2_jCheckBox.setFocusable(false);
        toggleCh2_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleCh2_jCheckBoxActionPerformed(evt);
            }
        });

        toggleCh3_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleCh3_jCheckBox.setText("3");
        toggleCh3_jCheckBox.setFocusable(false);
        toggleCh3_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleCh3_jCheckBoxActionPerformed(evt);
            }
        });

        toggleCh4_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleCh4_jCheckBox.setText("4");
        toggleCh4_jCheckBox.setFocusable(false);
        toggleCh4_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleCh4_jCheckBoxActionPerformed(evt);
            }
        });

        toggleCh5_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleCh5_jCheckBox.setText("5");
        toggleCh5_jCheckBox.setFocusable(false);
        toggleCh5_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleCh5_jCheckBoxActionPerformed(evt);
            }
        });

        toggleCh6_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleCh6_jCheckBox.setText("6");
        toggleCh6_jCheckBox.setFocusable(false);
        toggleCh6_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleCh6_jCheckBoxActionPerformed(evt);
            }
        });

        toggleCh7_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleCh7_jCheckBox.setText("7");
        toggleCh7_jCheckBox.setFocusable(false);
        toggleCh7_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleCh7_jCheckBoxActionPerformed(evt);
            }
        });

        toggleCh8_jCheckBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        toggleCh8_jCheckBox.setText("8");
        toggleCh8_jCheckBox.setFocusable(false);
        toggleCh8_jCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleCh8_jCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout channel_jPanelLayout = new javax.swing.GroupLayout(channel_jPanel);
        channel_jPanel.setLayout(channel_jPanelLayout);
        channel_jPanelLayout.setHorizontalGroup(
            channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(channel_jPanelLayout.createSequentialGroup()
                .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(channel_jPanelLayout.createSequentialGroup()
                        .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(analysisChannel_jLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(toggleChannel_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(toggleCh5_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(toggleCh1_jCheckBox)
                            .addComponent(analysisCh5_jCheckBox)
                            .addComponent(analysisCh1_jCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(toggleCh6_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(toggleCh2_jCheckBox)
                            .addComponent(analysisCh6_jCheckBox)
                            .addComponent(analysisCh2_jCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(toggleCh7_jCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(toggleCh3_jCheckBox)
                            .addComponent(analysisCh7_jCheckBox)
                            .addComponent(analysisCh3_jCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(toggleCh8_jCheckBox)
                            .addComponent(toggleCh4_jCheckBox)
                            .addComponent(analysisCh8_jCheckBox)
                            .addComponent(analysisCh4_jCheckBox)))
                    .addGroup(channel_jPanelLayout.createSequentialGroup()
                        .addComponent(toggleColor_jLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(r_jRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(g_jRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(b_jRadioButton)))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        channel_jPanelLayout.setVerticalGroup(
            channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(channel_jPanelLayout.createSequentialGroup()
                .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(analysisChannel_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(analysisCh1_jCheckBox)
                    .addComponent(analysisCh2_jCheckBox)
                    .addComponent(analysisCh3_jCheckBox)
                    .addComponent(analysisCh4_jCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(analysisCh5_jCheckBox)
                    .addComponent(analysisCh6_jCheckBox)
                    .addComponent(analysisCh7_jCheckBox)
                    .addComponent(analysisCh8_jCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(toggleCh4_jCheckBox)
                    .addComponent(toggleCh3_jCheckBox)
                    .addComponent(toggleCh2_jCheckBox)
                    .addComponent(toggleCh1_jCheckBox)
                    .addComponent(toggleChannel_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(toggleCh8_jCheckBox)
                    .addComponent(toggleCh7_jCheckBox)
                    .addComponent(toggleCh6_jCheckBox)
                    .addComponent(toggleCh5_jCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(channel_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(b_jRadioButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(g_jRadioButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(r_jRadioButton))
                    .addComponent(toggleColor_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(128, 128, 128))
        );

        showSkeletonized_jButton.setText("Show Skeletonized");
        showSkeletonized_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showSkeletonized_jButtonActionPerformed(evt);
            }
        });

        jButton3.setText("Show Skeletonized Projection");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        skeletonizedShowTracedPoints_jButton.setSelected(true);
        skeletonizedShowTracedPoints_jButton.setText("Show traced points");
        skeletonizedShowTracedPoints_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                skeletonizedShowTracedPoints_jButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout tracing_jPanelLayout = new javax.swing.GroupLayout(tracing_jPanel);
        tracing_jPanel.setLayout(tracing_jPanelLayout);
        tracing_jPanelLayout.setHorizontalGroup(
            tracing_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(channel_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 244, Short.MAX_VALUE)
            .addGroup(tracing_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tracing_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(toogleTracingCompleteness_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(zProjectionInterval_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(samplingTolerance_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 232, Short.MAX_VALUE)
                    .addComponent(colorSamplingRadius_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(clearPoints_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(tracing_jPanelLayout.createSequentialGroup()
                        .addComponent(tracingMethod_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(labeling_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(tracing_jPanelLayout.createSequentialGroup()
                        .addGroup(tracing_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(showSkeletonized_jButton)
                            .addComponent(jButton3)
                            .addComponent(skeletonizedShowTracedPoints_jButton))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        tracing_jPanelLayout.setVerticalGroup(
            tracing_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tracing_jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(zProjectionInterval_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tracing_jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tracingMethod_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(labeling_jPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(colorSamplingRadius_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(samplingTolerance_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(toogleTracingCompleteness_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(clearPoints_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(channel_jPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 176, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(showSkeletonized_jButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(skeletonizedShowTracedPoints_jButton)
                .addContainerGap())
        );

        main_jTabbedPane.addTab("Tracing   ", tracing_jPanel);

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        neuronList_jTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        neuronList_jTree.setToolTipText("\"Double Click\" blank area to de-select all.");
        neuronList_jTree.setMaximumSize(new java.awt.Dimension(30, 16));
        neuronList_jTree.setPreferredSize(new java.awt.Dimension(30, 16));
        neuronList_jTree.setRootVisible(false);
        neuronList_jScrollPane.setViewportView(neuronList_jTree);

        displaySomaList_jTree.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Soma Slices", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N
        treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        displaySomaList_jTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        displaySomaList_jTree.setToolTipText("Soma tracing result");
        displaySomaList_jTree.setMaximumSize(new java.awt.Dimension(30, 16));
        displaySomaList_jTree.setPreferredSize(new java.awt.Dimension(30, 16));
        displaySomaList_jTree.setRootVisible(false);
        somaList_jScrollPane.setViewportView(displaySomaList_jTree);

        pointTable_jScrollPane.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), "Traced points (Crtl-Home => top | Crtl-End => bottom)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N

        pointTable_jTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Type", "X", "Y", "Z", "Radius", "Synapse", "Connection"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.Float.class, java.lang.Float.class, java.lang.Float.class, java.lang.Float.class, java.lang.Integer.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        pointTable_jTable.setToolTipText("Scroll: \"Crtl-Home\" => top ; \"Ctrl-End\" => bottom");
        pointTable_jScrollPane.setViewportView(pointTable_jTable);

        info_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        info_jLabel.setText("Info");
        info_jLabel.setToolTipText("");

        srtPtInt_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        srtPtInt_jLabel.setForeground(new java.awt.Color(255, 0, 0));
        srtPtInt_jLabel.setText("Intensity");

        endPtInt_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        endPtInt_jLabel.setForeground(new java.awt.Color(0, 153, 153));
        endPtInt_jLabel.setText("Intensity");

        startPosition_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        startPosition_jLabel.setForeground(new java.awt.Color(255, 0, 0));
        startPosition_jLabel.setText("     ");

        endPosition_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        endPosition_jLabel.setForeground(new java.awt.Color(0, 153, 153));
        endPosition_jLabel.setText("     ");

        startPt_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        startPt_jLabel.setForeground(new java.awt.Color(255, 0, 0));
        startPt_jLabel.setText("Start Point");
        startPt_jLabel.setToolTipText("");

        endPt_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        endPt_jLabel.setForeground(new java.awt.Color(0, 153, 153));
        endPt_jLabel.setText("  End Point");

        startIntensity_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        startIntensity_jLabel.setForeground(new java.awt.Color(255, 0, 0));
        startIntensity_jLabel.setText("     ");

        endIntensity_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        endIntensity_jLabel.setForeground(new java.awt.Color(0, 153, 153));
        endIntensity_jLabel.setText("     ");

        srtPtCol_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        srtPtCol_jLabel.setForeground(new java.awt.Color(255, 0, 0));
        srtPtCol_jLabel.setText("Color");

        startColor_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        startColor_jLabel.setForeground(new java.awt.Color(255, 0, 0));
        startColor_jLabel.setText("     ");

        endPtCol_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        endPtCol_jLabel.setForeground(new java.awt.Color(0, 153, 153));
        endPtCol_jLabel.setText("Color");

        endColor_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        endColor_jLabel.setForeground(new java.awt.Color(0, 153, 153));
        endColor_jLabel.setText("     ");

        editTargetName_jLabel.setBackground(new java.awt.Color(255, 255, 255));
        editTargetName_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        editTargetName_jLabel.setForeground(new java.awt.Color(204, 0, 0));
        editTargetName_jLabel.setText("0");
        editTargetName_jLabel.setToolTipText("Second selected neuron name");

        connectedSynapse_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        connectedSynapse_jLabel.setForeground(new java.awt.Color(0, 102, 102));
        connectedSynapse_jLabel.setText("Connected Synapse =>");
        connectedSynapse_jLabel.setToolTipText("Connected synapse name of the selected point");

        copyToEditTarget_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        copyToEditTarget_jButton.setForeground(new java.awt.Color(204, 0, 0));
        copyToEditTarget_jButton.setText("Copy=>Edit Target");
        copyToEditTarget_jButton.setToolTipText("Copy Neuron Tree selection to Edit Target");
        copyToEditTarget_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        copyToEditTarget_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyToEditTarget_jButtonActionPerformed(evt);
            }
        });

        neuronTree_jLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        neuronTree_jLabel.setText("Set Type:");

        setNeurite_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        setNeurite_jButton.setForeground(new java.awt.Color(102, 102, 102));
        setNeurite_jButton.setText("Neurite");
        setNeurite_jButton.setToolTipText("set Neurite  (show node in 'gray')");
        setNeurite_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setNeurite_jButtonActionPerformed(evt);
            }
        });

        setAxon_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        setAxon_jButton.setForeground(new java.awt.Color(0, 0, 255));
        setAxon_jButton.setText("Axon");
        setAxon_jButton.setToolTipText("set Axon  (show node in 'blue')");
        setAxon_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setAxon_jButtonActionPerformed(evt);
            }
        });

        setBasalDendrite_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        setBasalDendrite_jButton.setForeground(new java.awt.Color(255, 0, 0));
        setBasalDendrite_jButton.setText("(Basal) Dendrite");
        setBasalDendrite_jButton.setToolTipText("set (basal) Dendrite (show node in 'red')");
        setBasalDendrite_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setBasalDendrite_jButtonActionPerformed(evt);
            }
        });

        setApicalDendrite_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        setApicalDendrite_jButton.setForeground(new java.awt.Color(255, 0, 255));
        setApicalDendrite_jButton.setText("Apical Dendrite");
        setApicalDendrite_jButton.setToolTipText("set Apical Dendrite (show node in 'magenta')");
        setApicalDendrite_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setApicalDendrite_jButtonActionPerformed(evt);
            }
        });

        selectionTag_jTextField.setForeground(new java.awt.Color(204, 0, 0));
        selectionTag_jTextField.setToolTipText("Type neuron tags (seperate tags by ; )");

        selectNeuronNumber_jTextField.setForeground(new java.awt.Color(153, 153, 0));
        selectNeuronNumber_jTextField.setToolTipText("Type neuron numbers (e.g. 2-8;13) seperate neurons by ; ");

        addLabelToSelection_jButton.setBackground(new java.awt.Color(255, 255, 255));
        addLabelToSelection_jButton.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        addLabelToSelection_jButton.setForeground(new java.awt.Color(204, 0, 0));
        addLabelToSelection_jButton.setText("Tag");
        addLabelToSelection_jButton.setToolTipText("Add tag to selected neuron or point (HotKey ' g ')");
        addLabelToSelection_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addLabelToSelection_jButtonActionPerformed(evt);
            }
        });

        selectNeurons_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        selectNeurons_jButton.setForeground(new java.awt.Color(0, 153, 153));
        selectNeurons_jButton.setText("Select");
        selectNeurons_jButton.setToolTipText("Select neuron(s)");
        selectNeurons_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectNeurons_jButtonActionPerformed(evt);
            }
        });

        selectTagOperator_jComboBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        selectTagOperator_jComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "+", "-" }));

        selectTagAdditionalCriteria_jComboBox.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        selectTagAdditionalCriteria_jComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Whole Neuron", "Neurite", "Axon", "All Dendrite", "(Basal) Dendrite", "Apical Dendite" }));

        showConnectedNeurons_jButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        showConnectedNeurons_jButton.setForeground(new java.awt.Color(0, 153, 153));
        showConnectedNeurons_jButton.setText("Connected");
        showConnectedNeurons_jButton.setToolTipText("Select all neurons connected to the selected ones");
        showConnectedNeurons_jButton.setFocusable(false);
        showConnectedNeurons_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        showConnectedNeurons_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showConnectedNeurons_jButtonActionPerformed(evt);
            }
        });

        jLabel1.setBackground(new java.awt.Color(255, 255, 255));
        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(153, 153, 0));
        jLabel1.setText("( Neuron #");

        jLabel2.setBackground(new java.awt.Color(255, 255, 255));
        jLabel2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(153, 153, 0));
        jLabel2.setText(")");

        jLabel3.setBackground(new java.awt.Color(255, 255, 255));
        jLabel3.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel3.setText("+");

        jLabel4.setBackground(new java.awt.Color(255, 255, 255));
        jLabel4.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(204, 0, 0));
        jLabel4.setText(" (");

        jLabel5.setBackground(new java.awt.Color(255, 255, 255));
        jLabel5.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(204, 0, 0));
        jLabel5.setText(")");

        copyNeuronTag_jButton.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        copyNeuronTag_jButton.setForeground(new java.awt.Color(204, 0, 0));
        copyNeuronTag_jButton.setText("Copy Tag");
        copyNeuronTag_jButton.setToolTipText("Copy selected neuron's tag to \"Tag field\"");
        copyNeuronTag_jButton.setFocusable(false);
        copyNeuronTag_jButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        copyNeuronTag_jButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyNeuronTag_jButtonActionPerformed(evt);
            }
        });

        menu_jMenuBar.setFocusable(false);

        menu_jMenu.setText("Menu");
        menu_jMenu.setFocusable(false);

        xyzResolutions_jMenuItem.setText("Set x/y/z Resolutions");
        xyzResolutions_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                xyzResolutions_jMenuItemActionPerformed(evt);
            }
        });
        menu_jMenu.add(xyzResolutions_jMenuItem);

        quit_jMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        quit_jMenuItem.setText("Quit");
        quit_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                quit_jMenuItemActionPerformed(evt);
            }
        });
        menu_jMenu.add(quit_jMenuItem);

        menu_jMenuBar.add(menu_jMenu);

        model3D_jMenu1.setText("Edit");

        selecAllNeuron_jMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        selecAllNeuron_jMenuItem.setText("Select all neurons");
        selecAllNeuron_jMenuItem.setToolTipText("Create Overlay flattened Image Stack");
        selecAllNeuron_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selecAllNeuron_jMenuItemActionPerformed(evt);
            }
        });
        model3D_jMenu1.add(selecAllNeuron_jMenuItem);

        deselectAllNeuon_jMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        deselectAllNeuon_jMenuItem.setText("Deselect all");
        deselectAllNeuon_jMenuItem.setToolTipText("Create Overlay flattened Image Stack");
        deselectAllNeuon_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deselectAllNeuon_jMenuItemActionPerformed(evt);
            }
        });
        model3D_jMenu1.add(deselectAllNeuon_jMenuItem);

        undo_jMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        undo_jMenuItem.setText("Undo");
        undo_jMenuItem.setToolTipText("Create Overlay flattened Image Stack");
        undo_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                undo_jMenuItemActionPerformed(evt);
            }
        });
        model3D_jMenu1.add(undo_jMenuItem);

        redo_jMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        redo_jMenuItem.setText("Redo");
        redo_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                redo_jMenuItemActionPerformed(evt);
            }
        });
        model3D_jMenu1.add(redo_jMenuItem);

        setScale_jMenuItem.setText("Set Scale");
        setScale_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setScale_jMenuItemActionPerformed(evt);
            }
        });
        model3D_jMenu1.add(setScale_jMenuItem);

        menu_jMenuBar.add(model3D_jMenu1);

        data_jMenu.setText("Data");

        loadData_jMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        loadData_jMenuItem.setText("Load Data");
        loadData_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData_jMenuItemActionPerformed(evt);
            }
        });
        data_jMenu.add(loadData_jMenuItem);

        loadSkeletonized_jMenuItem.setText("Load Skeletonized");
        loadSkeletonized_jMenuItem.setToolTipText("");
        loadSkeletonized_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadSkeletonized_jMenuItemActionPerformed(evt);
            }
        });
        data_jMenu.add(loadSkeletonized_jMenuItem);

        saveData_jMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        saveData_jMenuItem.setText("Save Data");
        saveData_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveData_jMenuItemActionPerformed(evt);
            }
        });
        data_jMenu.add(saveData_jMenuItem);

        clearData_jMenuItem.setText("Clear Data");
        clearData_jMenuItem.setToolTipText("");
        clearData_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearData_jMenuItemActionPerformed(evt);
            }
        });
        data_jMenu.add(clearData_jMenuItem);

        cropData_jMenuItem.setText("Crop Data");
        cropData_jMenuItem.setEnabled(false);
        cropData_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cropData_jMenuItemActionPerformed(evt);
            }
        });
        data_jMenu.add(cropData_jMenuItem);

        exportSWCfromSelectedNeurons_jMenuItem.setText("Export SWC");
        exportSWCfromSelectedNeurons_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportSWCfromSelectedNeurons_jMenuItemActionPerformed(evt);
            }
        });
        data_jMenu.add(exportSWCfromSelectedNeurons_jMenuItem);

        exportSynapseFromSelectedNeurons_jMenuItem.setText("Export Synapse");
        exportSynapseFromSelectedNeurons_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportSynapseFromSelectedNeurons_jMenuItemActionPerformed(evt);
            }
        });
        data_jMenu.add(exportSynapseFromSelectedNeurons_jMenuItem);

        autosaveSetup_jMenuItem.setText("Autosave Setup");
        autosaveSetup_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autosaveSetup_jMenuItemActionPerformed(evt);
            }
        });
        data_jMenu.add(autosaveSetup_jMenuItem);

        menu_jMenuBar.add(data_jMenu);

        analysis_jMenu.setText("Analysis");

        logNormChIntensity_jMenuItem.setText("Log Neuron Channel Intensity");
        logNormChIntensity_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logNormChIntensity_jMenuItemActionPerformed(evt);
            }
        });
        analysis_jMenu.add(logNormChIntensity_jMenuItem);

        jMenuItem1.setText("Log Neuron RGB Values");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        analysis_jMenu.add(jMenuItem1);

        logColorRatio_jMenuItem.setText("Log Neuron Color Ratio");
        logColorRatio_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logColorRatio_jMenuItemActionPerformed(evt);
            }
        });
        analysis_jMenu.add(logColorRatio_jMenuItem);

        logNeuronConnection_jMenuItem.setText("Log Neuron Connections");
        logNeuronConnection_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logNeuronConnection_jMenuItemActionPerformed(evt);
            }
        });
        analysis_jMenu.add(logNeuronConnection_jMenuItem);

        logSomaStatistics_jMenuItem.setText("Log Soma Statistics");
        logSomaStatistics_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logSomaStatistics_jMenuItemActionPerformed(evt);
            }
        });
        analysis_jMenu.add(logSomaStatistics_jMenuItem);

        menu_jMenuBar.add(analysis_jMenu);

        model3D_jMenu.setText("3D Model");

        skeleton_jMenuItem.setText("Skeleton");
        skeleton_jMenuItem.setToolTipText("Create Overlay flattened Image Stack");
        skeleton_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                skeleton_jMenuItemActionPerformed(evt);
            }
        });
        model3D_jMenu.add(skeleton_jMenuItem);

        volume_jMenuItem.setText("Volume");
        volume_jMenuItem.setEnabled(false);
        volume_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                volume_jMenuItemActionPerformed(evt);
            }
        });
        model3D_jMenu.add(volume_jMenuItem);

        menu_jMenuBar.add(model3D_jMenu);

        help_jMenu.setText("Help");

        help_jMenuItem.setText("Help");
        help_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                help_jMenuItemActionPerformed(evt);
            }
        });
        help_jMenu.add(help_jMenuItem);

        menu_jMenuBar.add(help_jMenu);

        debug_jMenu.setText("Debug");

        debug_jMenuItem.setText("Debug");
        debug_jMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                debug_jMenuItemActionPerformed(evt);
            }
        });
        debug_jMenu.add(debug_jMenuItem);

        menu_jMenuBar.add(debug_jMenu);

        setJMenuBar(menu_jMenuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGap(0, 207, Short.MAX_VALUE)
                                .addComponent(startPt_jLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(startPosition_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(endPt_jLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(endPosition_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(srtPtCol_jLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(startColor_jLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(endPtCol_jLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(endColor_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 427, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(info_jLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGap(42, 42, 42)
                                .addComponent(copyToEditTarget_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(selectNeurons_jButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(showConnectedNeurons_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(copyNeuronTag_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(neuronTree_jLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(setNeurite_jButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(setAxon_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(setBasalDendrite_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 117, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(setApicalDendrite_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(selectNeuronNumber_jTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 11, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 11, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(addLabelToSelection_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(selectionTag_jTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabel5)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(selectTagOperator_jComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(selectTagAdditionalCriteria_jComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE))))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(somaList_jScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(pointTable_jScrollPane))
                            .addComponent(neuronList_jScrollPane, javax.swing.GroupLayout.Alignment.LEADING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(editTargetName_jLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(srtPtInt_jLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(startIntensity_jLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(endPtInt_jLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(endIntensity_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 145, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(connectedSynapse_jLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 210, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(main_jTabbedPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 244, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(0, 0, Short.MAX_VALUE))))))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {endPt_jLabel, startPt_jLabel});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(main_jTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(connectedSynapse_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(setApicalDendrite_jButton)
                            .addComponent(setBasalDendrite_jButton)
                            .addComponent(setAxon_jButton)
                            .addComponent(setNeurite_jButton)
                            .addComponent(neuronTree_jLabel)
                            .addComponent(copyNeuronTag_jButton)
                            .addComponent(showConnectedNeurons_jButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(selectTagAdditionalCriteria_jComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(selectTagOperator_jComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel5)
                            .addComponent(selectionTag_jTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(addLabelToSelection_jButton)
                            .addComponent(jLabel4)
                            .addComponent(jLabel3)
                            .addComponent(jLabel2)
                            .addComponent(selectNeuronNumber_jTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(selectNeurons_jButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(neuronList_jScrollPane)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(pointTable_jScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                            .addComponent(somaList_jScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 342, Short.MAX_VALUE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(editTargetName_jLabel)
                    .addComponent(copyToEditTarget_jButton, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(info_jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(srtPtInt_jLabel)
                        .addComponent(startIntensity_jLabel))
                    .addComponent(srtPtCol_jLabel)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(startPosition_jLabel)
                        .addComponent(startPt_jLabel))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(startColor_jLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(endColor_jLabel)
                                .addComponent(endPtInt_jLabel)
                                .addComponent(endIntensity_jLabel))
                            .addComponent(endPtCol_jLabel)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(endPosition_jLabel)
                                .addComponent(endPt_jLabel)))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {endPosition_jLabel, startPosition_jLabel});

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {addLabelToSelection_jButton, copyNeuronTag_jButton, neuronTree_jLabel, selectNeuronNumber_jTextField, selectNeurons_jButton, selectTagAdditionalCriteria_jComboBox, selectTagOperator_jComboBox, selectionTag_jTextField, setApicalDendrite_jButton, setAxon_jButton, setBasalDendrite_jButton, setNeurite_jButton, showConnectedNeurons_jButton});

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel1, jLabel2, jLabel3, jLabel4, jLabel5});

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private boolean openImage() {
        //System.err.println("OpenImage Ran");

        System.gc();
        System.gc();
        imp = IO.loadImage();
        if (imp == null) {
            return false;
        }

        initiateTracing();
        System.gc();
        return true;
    }

    private void initiateTracing() {
        if (imp.getType() == ImagePlus.COLOR_RGB) {
            IJ.run(imp, "Make Composite", "");
        } else if (!imp.isComposite()) {
            String impTitle = imp.getTitle();
            Calibration impCal = imp.getCalibration();
            ImagePlus imp2 = imp.duplicate();
            RGBStackMerge merger = new RGBStackMerge();
            ImagePlus[] images = {imp, imp2};
            ImagePlus impMerge = merger.mergeHyperstacks(images, false);
            imp.close();
            imp2.close();
            impMerge.show();
            impMerge.setTitle(impTitle);
            impMerge.setCalibration(impCal);
            imp = impMerge;
            imp.setC(1);
            IJ.run(imp, "Red", "");
            imp.setC(2);
            IJ.run(imp, "Green", "");
            imp.setC(1);
        }

        if (impNChannel > 8) {
            IJ.error("Image needs to be 8 channels or less!");
            quit();
            return;
        }

        startPoint = new int[7];
        endPoint = new int[7];
        initImage();
        initiateCalibration();
        initChannels();
        Functions.setup(imp);
        update.updatePointTable(tablePoints);
        initImageOverlay();
        //initTempHistoryZipFile();
        dataHelper.loadData();
        history.startAutosave();
        initImageZproj();
    }

    private void initImage() {
        impWidth = imp.getWidth();
        impHeight = imp.getHeight();
        impNChannel = imp.getNChannels();
        impNSlice = imp.getNSlices();
        impNFrame = imp.getNFrames();
        crossX = (int) (impWidth / 2);
        crossY = (int) (impHeight / 2);
        crossZ = 1;
        ptIntColor = new float[impNChannel];
        tablePoints = new ArrayList<>();
        stk = imp.getImageStack();
        cns = imp.getCanvas();
        win = imp.getWindow();
        // initiate display event responses
        WindowListener impWL[] = win.getWindowListeners();
        for (WindowListener impWL1 : impWL) {
            win.removeWindowListener(impWL1);
        }
        MouseWheelListener[] mwl = win.getMouseWheelListeners();
        for (MouseWheelListener mwl1 : mwl) {
            win.removeMouseWheelListener(mwl1);
        }
        win.addMouseWheelListener(this);
        //MouseListener[] ml = cns.getMouseListeners();
        //for (int i=0; i<ml.length; i++){
        //    cns.removeMouseListener(ml[i]);
        //}
        cns.addMouseListener(this);
        cns.addMouseMotionListener(this);

        // remove default keyboard shortcut
        cns.removeKeyListener(IJ.getInstance());
        //cns.removeKeyListener(cns.getKeyListeners()[0]);
        cns.addKeyListener(this);
        cns.disablePopupMenu(true);
    }
    
    private void initImageWithSkeletonized() {
        stk = imp.getImageStack();
        cns = imp.getCanvas();
        win = imp.getWindow();
        // initiate display event responses
        WindowListener impWL[] = win.getWindowListeners();
        for (WindowListener impWL1 : impWL) {
            win.removeWindowListener(impWL1);
        }
        MouseWheelListener[] mwl = win.getMouseWheelListeners();
        for (MouseWheelListener mwl1 : mwl) {
            win.removeMouseWheelListener(mwl1);
        }
        win.addMouseWheelListener(this);
        cns.addMouseListener(this);
        cns.addMouseMotionListener(this);

        // remove default keyboard shortcut
        cns.removeKeyListener(IJ.getInstance());
        cns.addKeyListener(this);
        cns.disablePopupMenu(true);
        updateDisplay();
    }

    private void initImageZproj() {
        //impZproj = DuplicateProjector.duplicateAndProject( imp, 1, 1, 1, 1 );
        impZproj = new ZProjector().run(imp, "max", 1, 1);
        impZproj.show();
        cnsZproj = impZproj.getCanvas();
        cnsZproj.removeKeyListener(IJ.getInstance());
        cnsZproj.removeMouseListener(cns);
        //cnsZproj.addKeyListener(this);
        //cnsZproj.disablePopupMenu(true);
        winZproj = impZproj.getWindow();
        WindowListener impZprojWL[] = winZproj.getWindowListeners();
        for (WindowListener impZprojWL1 : impZprojWL) {
            winZproj.removeWindowListener(impZprojWL1);
        }
        FocusListener[] winZprojFL = cnsZproj.getFocusListeners();
        for (FocusListener winZprojFL1 : winZprojFL) {
            winZproj.removeFocusListener(winZprojFL1);
        }
        winZproj.removeMouseListener(this);
        MouseWheelListener[] impZprojMWL = winZproj.getMouseWheelListeners();
        for (int k = 0; k < impZprojMWL.length; k++) {
            winZproj.removeMouseWheelListener(impZprojMWL[k]);
        }
        zProjInterval = (Integer) zProjectionInterval_jSpinner.getValue();
        zProjXY = Integer.parseInt((String) xyProjectionArea_jSpinner.getValue());
        updateZprojectionImp();
    }

    private void initiateCalibration() {
        Calibration cal = imp.getCalibration();
        Calibration oriCal = cal.copy();
        GenericDialog gd = new GenericDialog("Set image scales ...");
        gd.addMessage("Current resolutions:");
        gd.addMessage("x: " + cal.pixelWidth + " " + cal.getUnit() + "/pixel");
        gd.addMessage("y: " + cal.pixelHeight + " " + cal.getUnit() + "/pixel");
        gd.addMessage("z: " + cal.pixelDepth + " " + cal.getUnit() + "/pixel");
        gd.addMessage("");
        gd.addMessage("Set new resolutions:");
        gd.addNumericField("x resolution", cal.pixelWidth, 3, 8, "um/pixel");
        gd.addNumericField("y resolution", cal.pixelHeight, 3, 8, "um/pixel");
        gd.addNumericField("z resolution", cal.pixelDepth, 3, 8, "um/pixel");
        gd.showDialog();

        if (gd.wasOKed()) {
            cal.pixelWidth = gd.getNextNumber();
            cal.pixelHeight = gd.getNextNumber();
            cal.pixelDepth = gd.getNextNumber();
            cal.setUnit("\u00B5m");
        }

        if (!(cal.pixelWidth == oriCal.pixelWidth && cal.pixelHeight == oriCal.pixelHeight && cal.pixelDepth == oriCal.pixelDepth)) {
            YesNoCancelDialog yncDialog = new YesNoCancelDialog(new java.awt.Frame(),
                    "Save image ...", "Image calibration changed !" + "\n" + "Do you want to save new calibration to image?");
            if (yncDialog.yesPressed()) {
                IJ.save(imp, IJ.getDirectory("image") + "/" + imp.getTitle());
            }
        }

        if (!cal.getUnit().equals("\u00B5m") || cal.pixelWidth < 0 || cal.pixelHeight < 0 || cal.pixelDepth < 0) {
            cal.pixelWidth = 0;
            cal.pixelHeight = 0;
            cal.pixelDepth = 0;
        }

        xyzResolutions[0] = cal.pixelWidth;
        xyzResolutions[1] = cal.pixelHeight;
        xyzResolutions[2] = cal.pixelDepth;
        this.setTitle(this.VERSION + " - pixel resolutions (x, y, z) um/pixel: (" + xyzResolutions[0] + ", " + xyzResolutions[1] + ", " + xyzResolutions[2] + ")");
        int currentZ = imp.getZ();
        imp.setZ(1);
        imp.setZ(imp.getNSlices());
        imp.setZ(currentZ);
    }

    private void initChannels() {
        toggleColor = "Red";
        r_jRadioButton.setSelected(true);
        g_jRadioButton.setSelected(false);
        b_jRadioButton.setSelected(false);
        
        javax.swing.JCheckBox[] toggleCh_jCheckboxes = {toggleCh1_jCheckBox, toggleCh2_jCheckBox, toggleCh3_jCheckBox,
            toggleCh4_jCheckBox, toggleCh5_jCheckBox, toggleCh6_jCheckBox, toggleCh7_jCheckBox, toggleCh8_jCheckBox};
        
        javax.swing.JCheckBox[] analysisCh_jCheckboxes = {analysisCh1_jCheckBox, analysisCh2_jCheckBox, analysisCh3_jCheckBox,
            analysisCh4_jCheckBox, analysisCh5_jCheckBox, analysisCh6_jCheckBox, analysisCh7_jCheckBox, analysisCh8_jCheckBox};
        
        deselectInvisualizeAllChannelCheckboxes();
        for (int i = 0; i < 8; ++i) {
            if (impNChannel > i) {
                toggleCh_jCheckboxes[i].setVisible(true);
                toggleCh_jCheckboxes[i].setEnabled(true);
                analysisCh_jCheckboxes[i].setVisible(true);
                analysisCh_jCheckboxes[i].setEnabled(true);
                analysisCh_jCheckboxes[i].setSelected(true);
            }
        }
        
        analysisChannels = new boolean[impNChannel];
        toggleChannels = new boolean[impNChannel];
        for (int n = 0; n < impNChannel; n++) {
            analysisChannels[n] = true;
            toggleChannels[n] = false;
        }
        cmp = (CompositeImage) imp;
        activeChannels = cmp.getActiveChannels();
    }

    private void initImageOverlay() {
        allNeuronTraceOLextPt = new Overlay[impNSlice];
        allNeuronNameOLextPt = new Overlay[impNSlice];
        selectedNeuronTraceOLextPt = new Overlay[impNSlice];
        selectedNeuronNameOLextPt = new Overlay[impNSlice];
        selectedArborTraceOLextPt = new Overlay[impNSlice];
        selectedArborNameOLextPt = new Overlay[impNSlice];
        selectedBranchTraceOLextPt = new Overlay[impNSlice];
        selectedBranchNameOLextPt = new Overlay[impNSlice];
        allSomaTraceOL = new Overlay[impNSlice];
        allSomaNameOL = new Overlay[impNSlice];
        selectedSomaTraceOL = new Overlay[impNSlice];
        selectedSomaNameOL = new Overlay[impNSlice];
    }

    private boolean closeImage() {
        if (imp != null) {
            YesNoCancelDialog saveResultBeforeClose = new YesNoCancelDialog(new java.awt.Frame(),
                    "Closing Image ...", "Do you want to save results before closing image?");
            if (saveResultBeforeClose.cancelPressed()) {
                return false;
            } else if (saveResultBeforeClose.yesPressed()) {
                if (!dataHelper.saveData()) {
                    return false;
                }
            }

            tempFolderDirectory = "";
            history.stopAutosave();
            history = new History(this);
            analysisChannels = new boolean[1];
            toggleChannels = new boolean[1];
            Prefs.requireControlKey = false;
            clearStartEndPts();
            initPointTable();
            tablePoints = new ArrayList<>();
            update.updatePointTable(tablePoints);
            initNeuriteTree();
            initSomaTree();
            initSpineTree();
            updateInfo(defaultInfo);
            Functions = new ntTracing();
            toggleColor = "Red";
            r_jRadioButton.setSelected(true);
            g_jRadioButton.setSelected(false);
            b_jRadioButton.setSelected(false);
            deselectInvisualizeAllChannelCheckboxes();
            //visualizeDisableAllChannelCheckboxes();            

            cmp.close();
            imp.close();
            impZproj.close();
            
            main_channel_window.close();
            mp_channel_window.close();
            contrast_window.close();
            roi_window.close();
            
            //imp.flush();
            //imp = null;
            return true;
        } else {
            return false;
        }
    }

    private void quit_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_quit_jMenuItemActionPerformed
        quit();
    }//GEN-LAST:event_quit_jMenuItemActionPerformed
    private void quit() {
        if (imp == null || (imp != null && closeImage())) {
            Toolbar.restoreTools();
            this.dispose();
            IJ.run("Misc...", "divide=Infinity");
            IJ.getInstance().addKeyListener(IJ.getInstance());
            System.gc();
        }
    }

    private void overlayAllNeuron_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayAllNeuron_jCheckBoxActionPerformed
        if (!overlayAllSoma_jCheckBox.isSelected() && !overlayAllNeuron_jCheckBox.isSelected()) {
            overlayAllName_jCheckBox.setSelected(false);
        }
        updateOverlay();
    }//GEN-LAST:event_overlayAllNeuron_jCheckBoxActionPerformed

    private void saveData_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveData_jMenuItemActionPerformed
        dataHelper.saveData();
    }//GEN-LAST:event_saveData_jMenuItemActionPerformed
    

    private void loadData_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData_jMenuItemActionPerformed
        dataHelper.loadData();
    }//GEN-LAST:event_loadData_jMenuItemActionPerformed


    private void overlaySelectedNeuron_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlaySelectedNeuron_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlaySelectedNeuron_jCheckBoxActionPerformed

    private void overlaySelectedBranch_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlaySelectedBranch_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlaySelectedBranch_jCheckBoxActionPerformed

    private void help_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_help_jMenuItemActionPerformed
        showHelp();
    }//GEN-LAST:event_help_jMenuItemActionPerformed
    private void showHelp() {
        try {
            openManual();
        } catch (IOException e) {
            IJ.error("Fail to load nTracer_Manual.pdf");
        }
    }

    private void openManual() throws IOException {
        InputStream resource = getClass().getResourceAsStream("nTracer_Manual.pdf");
        try {
            File file = File.createTempFile("nTracer_Manual", ".pdf");
            file.deleteOnExit();
            OutputStream out = new FileOutputStream(file);
            try {
                int read;
                byte[] bytes = new byte[1024];

                while ((read = resource.read(bytes)) != -1) {
                    out.write(bytes, 0, read);
                }
            } finally {
                out.close();
            }
            Desktop.getDesktop().open(file);
        } finally {
            resource.close();
        }
    }

    private void manualTracing_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manualTracing_jRadioButtonActionPerformed
        startPoint = new int[7];
        startPosition_jLabel.setText("");
        startIntensity_jLabel.setText("");
        startColor_jLabel.setText("");
        hasStartPt = false;
        endPoint = new int[7];
        endPosition_jLabel.setText("");
        endIntensity_jLabel.setText("");
        endColor_jLabel.setText("");
        hasEndPt = false;
        updateInfo(defaultInfo);
        if (imp != null) {
            imp.killRoi();
            updateOverlay();
        }

        //xyRadius_jSpinner.setEnabled(false);
        //zRadius_jSpinner.setEnabled(false);
        //colorThreshold_jSpinner.setEnabled(true);
    }//GEN-LAST:event_manualTracing_jRadioButtonActionPerformed

    private void semiAutoTracing_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_semiAutoTracing_jRadioButtonActionPerformed
        startPoint = new int[7];
        startPosition_jLabel.setText("");
        startIntensity_jLabel.setText("");
        startColor_jLabel.setText("");
        hasStartPt = false;
        endPoint = new int[7];
        endPosition_jLabel.setText("");
        endIntensity_jLabel.setText("");
        endColor_jLabel.setText("");
        hasEndPt = false;
        updateInfo(defaultInfo);
        if (imp != null) {
            imp.killRoi();
            updateOverlay();
        }

        //xyRadius_jSpinner.setEnabled(true);
        //zRadius_jSpinner.setEnabled(true);
        //colorThreshold_jSpinner.setEnabled(true);
    }//GEN-LAST:event_semiAutoTracing_jRadioButtonActionPerformed

    private void autoTracing_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoTracing_jRadioButtonActionPerformed
        startPoint = new int[7];
        startPosition_jLabel.setText("");
        startIntensity_jLabel.setText("");
        startColor_jLabel.setText("");
        hasStartPt = false;
        endPoint = new int[7];
        endPosition_jLabel.setText("");
        endIntensity_jLabel.setText("");
        endColor_jLabel.setText("");
        hasEndPt = false;
        updateInfo(defaultInfo);
        if (imp != null) {
            imp.killRoi();
            updateOverlay();
        }

        //xyRadius_jSpinner.setEnabled(true);
        //zRadius_jSpinner.setEnabled(true);
        //colorThreshold_jSpinner.setEnabled(true);
    }//GEN-LAST:event_autoTracing_jRadioButtonActionPerformed

    private void r_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_r_jRadioButtonActionPerformed
        toggleColor = "Red";
    }//GEN-LAST:event_r_jRadioButtonActionPerformed

    private void g_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_g_jRadioButtonActionPerformed
        toggleColor = "Green";
    }//GEN-LAST:event_g_jRadioButtonActionPerformed

    private void b_jRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_b_jRadioButtonActionPerformed
        toggleColor = "Blue";
    }//GEN-LAST:event_b_jRadioButtonActionPerformed

    private void overlayPointBox_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayPointBox_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlayPointBox_jCheckBoxActionPerformed

    private void analysisCh1_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analysisCh1_jCheckBoxActionPerformed
        analysisChannels[0] = analysisCh1_jCheckBox.isSelected();
        updateDisplay();
    }//GEN-LAST:event_analysisCh1_jCheckBoxActionPerformed

    private void analysisCh2_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analysisCh2_jCheckBoxActionPerformed
        analysisChannels[1] = analysisCh2_jCheckBox.isSelected();
        updateDisplay();
    }//GEN-LAST:event_analysisCh2_jCheckBoxActionPerformed

    private void analysisCh3_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analysisCh3_jCheckBoxActionPerformed
        analysisChannels[2] = analysisCh3_jCheckBox.isSelected();
        updateDisplay();
    }//GEN-LAST:event_analysisCh3_jCheckBoxActionPerformed

    private void analysisCh4_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analysisCh4_jCheckBoxActionPerformed
        analysisChannels[3] = analysisCh4_jCheckBox.isSelected();
        updateDisplay();
    }//GEN-LAST:event_analysisCh4_jCheckBoxActionPerformed

    private void analysisCh5_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analysisCh5_jCheckBoxActionPerformed
        analysisChannels[4] = analysisCh5_jCheckBox.isSelected();
        updateDisplay();
    }//GEN-LAST:event_analysisCh5_jCheckBoxActionPerformed

    private void analysisCh6_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analysisCh6_jCheckBoxActionPerformed
        analysisChannels[5] = analysisCh6_jCheckBox.isSelected();
        updateDisplay();
    }//GEN-LAST:event_analysisCh6_jCheckBoxActionPerformed

    private void analysisCh7_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analysisCh7_jCheckBoxActionPerformed
        analysisChannels[6] = analysisCh7_jCheckBox.isSelected();
        updateDisplay();
    }//GEN-LAST:event_analysisCh7_jCheckBoxActionPerformed

    private void analysisCh8_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analysisCh8_jCheckBoxActionPerformed
        analysisChannels[7] = analysisCh8_jCheckBox.isSelected();
        updateDisplay();
    }//GEN-LAST:event_analysisCh8_jCheckBoxActionPerformed

    private void overlaySelectedSoma_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlaySelectedSoma_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlaySelectedSoma_jCheckBoxActionPerformed

    private void overlayAllSoma_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayAllSoma_jCheckBoxActionPerformed
        if (!overlayAllSoma_jCheckBox.isSelected() && !overlayAllNeuron_jCheckBox.isSelected()) {
            overlayAllName_jCheckBox.setSelected(false);
        }
        updateOverlay();
    }//GEN-LAST:event_overlayAllSoma_jCheckBoxActionPerformed

    private void overlaySelectedSynapse_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlaySelectedSynapse_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlaySelectedSynapse_jCheckBoxActionPerformed

    private void toggleCh1_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleCh1_jCheckBoxActionPerformed
        if (impNChannel >= 1) {
            toggleChannels[0] = toggleCh1_jCheckBox.isSelected();
        }
    }//GEN-LAST:event_toggleCh1_jCheckBoxActionPerformed

    private void toggleCh2_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleCh2_jCheckBoxActionPerformed
        if (impNChannel >= 2)
            toggleChannels[1] = toggleCh2_jCheckBox.isSelected();    }//GEN-LAST:event_toggleCh2_jCheckBoxActionPerformed

    private void toggleCh3_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleCh3_jCheckBoxActionPerformed
        if (impNChannel >= 3) {
            toggleChannels[2] = toggleCh3_jCheckBox.isSelected();
        }
    }//GEN-LAST:event_toggleCh3_jCheckBoxActionPerformed

    private void toggleCh4_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleCh4_jCheckBoxActionPerformed
        if (impNChannel >= 4) {
            toggleChannels[3] = toggleCh4_jCheckBox.isSelected();
        }
    }//GEN-LAST:event_toggleCh4_jCheckBoxActionPerformed

    private void toggleCh5_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleCh5_jCheckBoxActionPerformed
        if (impNChannel >= 5) {
            toggleChannels[4] = toggleCh5_jCheckBox.isSelected();
        }
    }//GEN-LAST:event_toggleCh5_jCheckBoxActionPerformed

    private void toggleCh6_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleCh6_jCheckBoxActionPerformed
        if (impNChannel >= 6) {
            toggleChannels[5] = toggleCh6_jCheckBox.isSelected();
        }
    }//GEN-LAST:event_toggleCh6_jCheckBoxActionPerformed

    private void toggleCh7_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleCh7_jCheckBoxActionPerformed
        if (impNChannel >= 7) {
            toggleChannels[6] = toggleCh7_jCheckBox.isSelected();
        }
    }//GEN-LAST:event_toggleCh7_jCheckBoxActionPerformed

    private void toggleCh8_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleCh8_jCheckBoxActionPerformed
        if (impNChannel >= 8) {
            toggleChannels[7] = toggleCh8_jCheckBox.isSelected();
        }
    }//GEN-LAST:event_toggleCh8_jCheckBoxActionPerformed
    
    private void logColorRatio_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logColorRatio_jMenuItemActionPerformed
        analysis.logNeuronColorRatio();
    }//GEN-LAST:event_logColorRatio_jMenuItemActionPerformed

    private void logNormChIntensity_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logNormChIntensity_jMenuItemActionPerformed
        analysis.logNeuronNormChIntensity();
    }//GEN-LAST:event_logNormChIntensity_jMenuItemActionPerformed

    /**
     * Logs the RGB (Red Green Blue) of all traced neurons
     */
    
    public void logNeuronRGB() {
        // log RGB of all traced neurons
        for (int n = 0; n < rootNeuronNode.getChildCount(); n++) {
            ntNeuronNode neuronSomaNode = (ntNeuronNode) rootNeuronNode.getChildAt(n);
            Color color = getNeuronColorFromNode(neuronSomaNode, 0.5f);
            
            String logTag = "Neuron "+neuronSomaNode.getNeuronNumber()+" : color (";

            //+color[0];
            //for (int i = 1; i<color.length; i++){
            //    logTag = logTag + ", " + color[i];
            //}
            
            logTag += color.getRed() + ", ";
            logTag += color.getGreen() + ", ";
            logTag += color.getBlue() + "";
            
            //logTag += color.toString();
            
            logTag = logTag+")";
            IJ.log(logTag);
        }
    }
    
    private void logNeuronConnection_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logNeuronConnection_jMenuItemActionPerformed
        if (neuronList_jTree.getSelectionCount() == 0) {
            YesNoCancelDialog yncDialog = new YesNoCancelDialog(new java.awt.Frame(),
                    "Log neuron connections ...", "Do you want to log all connections of all traced neurons ?");
            if (yncDialog.yesPressed()) {
                analysis.logAllNeuronConnections();
            }
        } else {
            ArrayList<String> neuronNumbers = getSelectedNeuronNumberSortSmall2Large();
            for (String neuronNumber : neuronNumbers) {
                ntNeuronNode neuronSomaNode = getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber);
                analysis.logWholeNeuronConnections(neuronSomaNode);
            }
        }
    }//GEN-LAST:event_logNeuronConnection_jMenuItemActionPerformed

    private void extendSelectedDisplayPoints_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_extendSelectedDisplayPoints_jSpinnerStateChanged
        extendDisplayPoints = (Integer) extendSelectedDisplayPoints_jSpinner.getValue();
        extendAllDisplayPoints_jSpinner.setValue(extendDisplayPoints);
        //IJ.log("displayDepth state changed = "+extendSelectedDisplayPoints);
        updateDisplay();
    }//GEN-LAST:event_extendSelectedDisplayPoints_jSpinnerStateChanged

    private void overlaySelectedName_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlaySelectedName_jCheckBoxActionPerformed
//        overlayAllName_jCheckBox.setSelected(false);
        updateOverlay();
    }//GEN-LAST:event_overlaySelectedName_jCheckBoxActionPerformed

    private void colorThreshold_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_colorThreshold_jSpinnerStateChanged
        colorThreshold = (Float) (colorThreshold_jSpinner.getValue());
    }//GEN-LAST:event_colorThreshold_jSpinnerStateChanged

    private void xyRadius_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_xyRadius_jSpinnerStateChanged
        xyRadius = (Integer) (xyRadius_jSpinner.getValue());
    }//GEN-LAST:event_xyRadius_jSpinnerStateChanged

    private void linkRadius_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_linkRadius_jSpinnerStateChanged
        outLinkXYradius = (Integer) (linkRadius_jSpinner.getValue());
    }//GEN-LAST:event_linkRadius_jSpinnerStateChanged

    private void overlayAllSelectedPoints_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayAllSelectedPoints_jCheckBoxActionPerformed
        if (overlayAllSelectedPoints_jCheckBox.isSelected()) {
            extendSelectedDisplayPoints_jSpinner.setEnabled(false);
        } else {
            extendSelectedDisplayPoints_jSpinner.setEnabled(true);
        }
        updateDisplay();
    }//GEN-LAST:event_overlayAllSelectedPoints_jCheckBoxActionPerformed

    private void skeleton_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_skeleton_jMenuItemActionPerformed
        GenericDialog gd = new GenericDialog("3D Model");
        String[] numberItems = {"All neurons in one image stack", "Each neuronin one image stack"};
        gd.addRadioButtonGroup("Model selected tracing skeleton ...",
                numberItems, 2, 1, numberItems[0]);
        gd.addCheckbox("Crop frame ?", false);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return;
        }
        boolean allSkeletonInOneImage = gd.getNextRadioButton().equals(numberItems[0]);
        boolean cropFrame = gd.getNextBoolean();
        export3DskeletonMultiThread(allSkeletonInOneImage, cropFrame);
    }//GEN-LAST:event_skeleton_jMenuItemActionPerformed

    private void neuronLineWidth_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_neuronLineWidth_jSpinnerStateChanged
        neuronLine = (Integer) neuronLineWidth_jSpinner.getValue() * 0.5f;
        allNeuronLine = (neuronLine - lineWidthOffset > 0.5) ? neuronLine - lineWidthOffset : 0.5f;
        updateAllNeuronTraceOL();
        if (selectedNeuronTraceOL != null) {
            for (int j = 0; j < selectedNeuronTraceOL.size(); j++) {
                selectedNeuronTraceOL.get(j).setStrokeWidth(neuronLine);
            }
        }
        updateOverlay();
    }//GEN-LAST:event_neuronLineWidth_jSpinnerStateChanged
    private void updateAllNeuronTraceOL() {
        if (allNeuronTraceOL != null) {
            for (int j = 0; j < allNeuronTraceOL.size(); j++) {
                allNeuronTraceOL.get(j).setStrokeWidth(allNeuronLine);
            }
        }
    }

    private void intensityThreshold_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_intensityThreshold_jSpinnerStateChanged
        intensityThreshold = (Float) (intensityThreshold_jSpinner.getValue());
    }//GEN-LAST:event_intensityThreshold_jSpinnerStateChanged

    private void zRadius_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zRadius_jSpinnerStateChanged
        zRadius = (Integer) (zRadius_jSpinner.getValue());
    }//GEN-LAST:event_zRadius_jSpinnerStateChanged

    private void overlayAllSynapse_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayAllSynapse_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlayAllSynapse_jCheckBoxActionPerformed

    private int[] getPositionInTracingResultWithSmallestDistanceToPoint(ArrayList<String[]> targetTracingResult, int x, int y, int z) {
        int connectPosition = -1;
        int distance2 = 1000000000;
        for (int i = 0; i < targetTracingResult.size(); i++) {
            String[] targetPt = targetTracingResult.get(i);
            int targetX = Integer.parseInt(targetPt[1]);
            int targetY = Integer.parseInt(targetPt[2]);
            int targetZ = Integer.parseInt(targetPt[3]);
            int targetDistance2 = (targetX - x) * (targetX - x) + (targetY - y) * (targetY - y) + (targetZ - z) * (targetZ - z);
            if (targetDistance2 < distance2) {
                distance2 = targetDistance2;
                connectPosition = i;
            }
        }
        int[] positionAndSmallestDistance2 = {connectPosition, distance2};
        return positionAndSmallestDistance2;
    }

    /**
     * Returns the position of the synapse in the tracingResult array, by comparing names.
     * It will return a value of -1 if the (name of the) synapse is not in the tracingResult array
     * @param tracingResult
     * @param synapseName
     * @return 
     */
    
    protected int getPositionInTracingResultBySynapseName(ArrayList<String[]> tracingResult, String synapseName) {
        // "return -1" means the synapseName is not in the tracingResult
        for (int i = 0; i < tracingResult.size(); i++) {
            String[] tracingPoint = tracingResult.get(i);
            //IJ.log(tracingPoint[6]);
            if (synapseName.equals(tracingPoint[6])) {
                //IJ.log("return "+i);
                return i;
            }
        }
        return -1;
    }

    private void showConnectedNeurons_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showConnectedNeurons_jButtonActionPerformed
        showConnectedNeurons();
    }//GEN-LAST:event_showConnectedNeurons_jButtonActionPerformed
    private void showConnectedNeurons() {
        canUpdateDisplay = false;
        history.saveHistory();
        recordNeuronTreeExpansionStatus();

        TreePath[] selectedNeuronPaths = neuronList_jTree.getSelectionPaths();
        for (TreePath selectedNeuronPath : selectedNeuronPaths) {
            ntNeuronNode selectedNeuronTreeNode = (ntNeuronNode) selectedNeuronPath.getLastPathComponent();
            if (selectedNeuronTreeNode.isTrunckNode()) { // whole neuron selected, add soma first
                addSomaConnectedToSelection(selectedNeuronTreeNode);
            }
            addBranchConnectedToSelection(selectedNeuronTreeNode);
        }

        restoreNeuronTreeExpansionStatus();
        history.saveHistory();
        canUpdateDisplay = true;
        updateDisplay();
    }

    private void addSomaConnectedToSelection(ntNeuronNode selectedNode) {
        // find selectedNode's soma connected neurons
        // add neuronSomaNode of the connected neurons to neuronList_jTree selection
        String selectedNeuronNumber = selectedNode.getNeuronNumber();
        ntNeuronNode selectedSomaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(selectedNeuronNumber);
        for (int i = 0; i < selectedSomaSomaNode.getChildCount(); i++) {
            ntNeuronNode somaSliceNode = (ntNeuronNode) selectedSomaSomaNode.getChildAt(i);
            ArrayList<String[]> tracingResults = somaSliceNode.getTracingResult();
            for (String[] tracingResult : tracingResults) {
                if (!tracingResult[6].equals("0")) { // has connection
                    String connectedNodeName = tracingResult[6].split("#")[1];
                    ntNeuronNode connectedNeuronNode = getNodeFromNeuronTreeByNodeName(connectedNodeName);
                    TreePath connectedNeuronPath = new TreePath(connectedNeuronNode.getPath());
                    neuronList_jTree.addSelectionPath(connectedNeuronPath);
                }
            }
        }
    }

    private void addBranchConnectedToSelection(ntNeuronNode selectedNode) {
        // find selectedNode's primary branch (and all child branches) connected neurons
        // add neuronSomaNode of the connected neurons to neuronList_jTree selection
        for (int i = 0; i < selectedNode.getChildCount(); i++) {
            ntNeuronNode childNode = (ntNeuronNode) selectedNode.getChildAt(i);
            addBranchConnectedToSelection(childNode);
        }
        ArrayList<String[]> tracingResults = selectedNode.getTracingResult();
        for (String[] tracingResult : tracingResults) {
            if (!tracingResult[6].equals("0")) { // has connection
                //IJ.log("selected "+selectedNode.toString());
                String connectedNodeName = tracingResult[6].split("#")[1];
                ntNeuronNode connectedNeuronNode;
                if (connectedNodeName.contains("-")) { // connect to a branch node
                    connectedNeuronNode = getNodeFromNeuronTreeByNodeName(connectedNodeName);
                } else {// connect to a soma node
                    String connectedSomaNumber = connectedNodeName.split(":")[0];
                    connectedNeuronNode = getSomaNodeFromNeuronTreeByNeuronNumber(connectedSomaNumber);
                }
                TreePath connectedNeuronPath = new TreePath(connectedNeuronNode.getPath());
                neuronList_jTree.addSelectionPath(connectedNeuronPath);
                TreePath sourceNeuronPath = new TreePath(selectedNode.getPath());
                neuronList_jTree.addSelectionPath(sourceNeuronPath);
                //IJ.log("connect to "+connectedNeuronNode.toString());
            }
        }
    }

    private void setPrimaryBranch_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setPrimaryBranch_jButtonActionPerformed
        traceHelper.setPrimaryBranch();
    }//GEN-LAST:event_setPrimaryBranch_jButtonActionPerformed
    
    private void breakBranch_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_breakBranch_jButtonActionPerformed
        traceHelper.breakBranch();
    }//GEN-LAST:event_breakBranch_jButtonActionPerformed

    private void joinBranches_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_joinBranches_jButtonActionPerformed
        traceHelper.joinBranches();
    }//GEN-LAST:event_joinBranches_jButtonActionPerformed

    private void deleteOneNeuron_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteOneNeuron_jButtonActionPerformed
        deleteNeuronsFromNeuronTree();
    }//GEN-LAST:event_deleteOneNeuron_jButtonActionPerformed

    private void deletePoints_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deletePoints_jButtonActionPerformed
        deleteSelectedPoints();
    }//GEN-LAST:event_deletePoints_jButtonActionPerformed

    private void clearPoints_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearPoints_jButtonActionPerformed
        clearStartEndPts();
    }//GEN-LAST:event_clearPoints_jButtonActionPerformed

    private void collapseAllNeuron_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_collapseAllNeuron_jButtonActionPerformed
        ArrayList<String> selectedNeuronNumbers = getSelectedNeuronNumberSortSmall2Large();
        //IJ.log("total selected neuron: "+selectedNeuronNumbers.size());
        neuronList_jTree.clearSelection();
        for (int i = 0; i < rootNeuronNode.getChildCount(); i++) {
            neuronList_jTree.collapsePath(new TreePath(((ntNeuronNode) rootNeuronNode.getChildAt(i)).getPath()));
        }
        if (!selectedNeuronNumbers.isEmpty()) {
            TreePath selectionPaths[] = new TreePath[selectedNeuronNumbers.size()];
            int counter = 0;
            for (int i = 0; i < rootNeuronNode.getChildCount(); i++) {
                ntNeuronNode node = (ntNeuronNode) rootNeuronNode.getChildAt(i);
                if ((selectedNeuronNumbers.get(0)).equals(node.getNeuronNumber())) {
                    selectionPaths[counter] = new TreePath(node.getPath());
                    counter++;
                    selectedNeuronNumbers.remove(0);
                    if (selectedNeuronNumbers.isEmpty()) {
                        break;
                    }
                }
            }
            neuronList_jTree.setSelectionPaths(selectionPaths);
        }
        history.saveHistory();
    }//GEN-LAST:event_collapseAllNeuron_jButtonActionPerformed

    private void expanAllNeuron_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_expanAllNeuron_jButtonActionPerformed
        for (int i = 0; i < rootNeuronNode.getChildCount(); i++) {
            neuronList_jTree.expandPath(new TreePath(((ntNeuronNode) rootNeuronNode.getChildAt(i)).getPath()));
        }
        history.saveHistory();
    }//GEN-LAST:event_expanAllNeuron_jButtonActionPerformed


    private void deleteSomaSlice_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSomaSlice_jButtonActionPerformed
        deleteSomaSlices();
    }//GEN-LAST:event_deleteSomaSlice_jButtonActionPerformed
    private void deleteSomaSlices() {
        if (displaySomaList_jTree.getSelectionCount() > 0) {
            //saveHistory();
            recordTreeExpansionSelectionStatus();

            TreePath[] selectedPaths = displaySomaList_jTree.getSelectionPaths();
            String neuronNumber = ((ntNeuronNode) selectedPaths[0].getPathComponent(0)).toString();
            if (neuronNumber.contains("/")) {
                neuronNumber = neuronNumber.split("/")[0];
            }

            //somehow MUST deselecte everyting in neuronList_jTree before deleting! 
            neuronList_jTree.clearSelection();

            // delete selected soma slices one by one 
            for (TreePath selectedPath : selectedPaths) {
                // somaSliceNode needs to be referenced to the one in rootAllSomaNode
                ntNeuronNode selectedNode = (ntNeuronNode) selectedPath.getLastPathComponent();
                deleteOneSomaSliceNodeByName(selectedNode.toString());
            }

            // if the neuron is empty -- delete the leftover somaNode from allSomaTreeModel and neuronTreeModel
            ntNeuronNode neuronSomaNode = getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber);
            ntNeuronNode somaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNumber);
            if (neuronSomaNode.getChildCount() == 0 && somaSomaNode.getChildCount() == 0) {
                allSomaTreeModel.removeNodeFromParent(somaSomaNode);
                neuronTreeModel.removeNodeFromParent(neuronSomaNode);
            }

            updateTrees();
            restoreTreeExpansionSelectionStatus();
            history.saveHistory();
            updateDisplay();
        }
    }

    /**
     * removes the connection between the "selectedNodeName" node and the "selectedSynapseName" by setting it to 0
     * @param selectedNodeName
     * @param selectedSynapseName 
     */
    
    protected void removeConnectionBySelectedNodeAndSynapseName(String selectedNodeName, String selectedSynapseName) {
        String[] connectedNames = selectedSynapseName.split("#");
        String connectedNodeName = connectedNames[1];
        String connectedSynapseName = connectedNames[2] + "#" + selectedNodeName + "#" + connectedNames[0];
        ntNeuronNode connectedNode = getTracingNodeByNodeName(connectedNodeName);
        int connectedPosition = getPositionInTracingResultBySynapseName(connectedNode.getTracingResult(), connectedSynapseName);
        connectedNode.setConnectionTo(connectedPosition, "0");
    }
    
    /**
     * deletes the somaSliceNode from the soma tree model
     * Removes all the connected synapses and required spines
     * @param somaSliceNodeName 
     */
    
    protected void deleteOneSomaSliceNodeByName(String somaSliceNodeName) {
        ntNeuronNode somaSliceNode = getSomaSliceNodeFromAllSomaTreeBySomaSliceName(somaSliceNodeName);
        deleteOneSomaSliceNodeByNode(somaSliceNode);
    }

    private void deleteOneSomaSliceNodeByNode(ntNeuronNode somaSliceNode) {
        String selectedNodeName = somaSliceNode.toString();
        ArrayList<String[]> somaSliceTracingPts = somaSliceNode.getTracingResult();
        // remove all the connected synapses from all soma slice tracing points
        for (int i = 0; i < somaSliceTracingPts.size(); i++) {
            String[] somaSliceTracingPt = somaSliceTracingPts.get(i);
            if (!somaSliceTracingPt[6].equals("0")) {
                removeConnectionBySelectedNodeAndSynapseName(selectedNodeName, somaSliceTracingPt[6]);
            }
            // determine whether a spine needs to be removed
            if (somaSliceTracingPt[0].contains(":Spine#")) {
                traceHelper.removeSpine(somaSliceTracingPt[0]);
                somaSliceNode.setSpine(i, "0");
            }
        }
        allSomaTreeModel.removeNodeFromParent(somaSliceNode);
    }

    private void deleteOneBranch_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteOneBranch_jButtonActionPerformed
        traceHelper.deleteOneBranchFromNeuronTree();
    }//GEN-LAST:event_deleteOneBranch_jButtonActionPerformed
    
    private void overlaySelectedArbor_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlaySelectedArbor_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlaySelectedArbor_jCheckBoxActionPerformed

    private void overlayAllName_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayAllName_jCheckBoxActionPerformed
//        overlaySelectedName_jCheckBox.setSelected(false);
        if (overlayAllName_jCheckBox.isSelected()) {
            if (!overlayAllSoma_jCheckBox.isSelected() && !overlayAllNeuron_jCheckBox.isSelected()) {
                overlayAllSoma_jCheckBox.setSelected(true);
                overlayAllNeuron_jCheckBox.setSelected(true);
            }
        }
        updateOverlay();
    }//GEN-LAST:event_overlayAllName_jCheckBoxActionPerformed

    private void completeSomaSliceRoi_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_completeSomaSliceRoi_jButtonActionPerformed
        completeSomaSliceRoi();
    }//GEN-LAST:event_completeSomaSliceRoi_jButtonActionPerformed
    private void completeSomaSliceRoi() {
        if (membraneLabel_jRadioButton.isSelected()) {
            if (manualTracing_jRadioButton.isSelected()) {
                traceHelper.completeSomaMinCostPath();
            }
        }
    }

    private void comnibeTwoNeuron_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comnibeTwoNeuron_jButtonActionPerformed
        combineTwoNeurons();
    }//GEN-LAST:event_comnibeTwoNeuron_jButtonActionPerformed
    private void combineTwoNeurons() {
        if (neuronList_jTree.getSelectionCount() != 1 || editTargetNodeName.equals("0")) {
            IJ.error("Select one neuron from the tree list and" + "/n"
                    + "get Second Selection neuron from screen to combine!");
            return;
        }
        // check whether selected neuron is the same as the Second Selection
        TreePath selectedNeuronPath = neuronList_jTree.getSelectionPath();
        ntNeuronNode neuronSomaNode0 = (ntNeuronNode) selectedNeuronPath.getPathComponent(1);
        String neuronNumber0 = neuronSomaNode0.getNeuronNumber();
        String neuronNumber1 = editTargetNodeName.contains("-") ? editTargetNodeName.split("-")[0] : editTargetNodeName.split(":")[0];
        ntNeuronNode neuronSomaNode1 = getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber1);
        if (neuronNumber0.equals(neuronNumber1)) {
            IJ.error("Select two different neurons to combine !");
            return;
        }
        // determine sourceNode (larger neuron number) and targetNode (smaller neuron number)
        boolean saveHistory;
        int number0 = Integer.parseInt(neuronNumber0);
        int number1 = Integer.parseInt(neuronNumber1);
        ntNeuronNode sourceNeuonSomaNode = (number0 > number1) ? neuronSomaNode0 : neuronSomaNode1;
        ntNeuronNode targetNeuronSomaNode = (number0 < number1) ? neuronSomaNode0 : neuronSomaNode1;
        String sourceNeuonTag = sourceNeuonSomaNode.toString();
        if (sourceNeuonTag.contains("/")) {
            sourceNeuonTag = sourceNeuonTag.split("/")[1];
        } else {
            sourceNeuonTag = "";
        }
        String targetNeuonTag = targetNeuronSomaNode.toString();
        if (targetNeuonTag.contains("/")) {
            targetNeuonTag = targetNeuonTag.split("/")[1];
        } else {
            targetNeuonTag = "";
        }
        if (!sourceNeuonTag.equals(targetNeuonTag)) {
            YesNoCancelDialog yncDialog = new YesNoCancelDialog(new java.awt.Frame(),
                    "", "Choose Tag for combined neuron: " + "\n\n" + "Press Yes:   '" + sourceNeuonTag + "'\n\n"
                    + "Press No:    '" + targetNeuonTag + "'");
            if (yncDialog.cancelPressed()) {
                return;
            } else if (yncDialog.yesPressed()) {
                if (sourceNeuonTag.equals("")) {
                    targetNeuronSomaNode.setName(targetNeuronSomaNode.getNeuronNumber());
                } else {
                    targetNeuronSomaNode.setName(targetNeuronSomaNode.getNeuronNumber() + "/" + sourceNeuonTag);
                }
                saveHistory = true;
            } else {
                if (targetNeuonTag.equals("")) {
                    sourceNeuonSomaNode.setName(sourceNeuonSomaNode.getNeuronNumber());
                } else {
                    sourceNeuonSomaNode.setName(sourceNeuonSomaNode.getNeuronNumber() + "/" + targetNeuonTag);
                }
                saveHistory = true;
            }
        } else {
            saveHistory = true;
        }
        recordTreeExpansionSelectionStatus();

        // insert unique Roi of sourceNeuonSomaNode into targetNeuronSomaNode
        // add all child (branch tracings) of sourceNeuronSomaNode to targetNeuronSomaNode
        combineSomaAndBranchesOfTwoNeuronTreeSomas(targetNeuronSomaNode, sourceNeuonSomaNode);

        updateTrees();
        restoreTreeExpansionSelectionStatus();
        TreePath targetPath = new TreePath(targetNeuronSomaNode.getPath());
        neuronList_jTree.setSelectionPath(targetPath);
        neuronList_jTree.scrollPathToVisible(targetPath);
        if (saveHistory) {
            history.saveHistory();
        }
    }

    private void jumpToNextSynapse_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jumpToNextSynapse_jButtonActionPerformed
        jumpToNextSynapse();
    }//GEN-LAST:event_jumpToNextSynapse_jButtonActionPerformed
    
    private void jumpToNext(int pointIndex) {
        if (!tablePoints.isEmpty()) {
            int selectRow = 0;
            int selectedRowNumber = pointTable_jTable.getSelectedRowCount();
            if (selectedRowNumber > 0) {
                selectRow = pointTable_jTable.getSelectedRows()[selectedRowNumber - 1];
            }
            boolean found = false;
            for (int i = selectRow + 1; i < tablePoints.size(); i++) {
                String[] point = tablePoints.get(i);
                if (!point[pointIndex].equals("0")) {
                    found = true;
                    selectRow = i;
                    break;
                }
            }
            if (!found) {
                for (int i = 0; i <= selectRow; i++) {
                    String[] point = tablePoints.get(i);
                    if (!point[pointIndex].equals("0")) {
                        found = true;
                        selectRow = i;
                        break;
                    }
                }
            }
            if (!found) {
                // do nothing
            } else {
                pointTable_jTable.setRowSelectionInterval(selectRow, selectRow);
                scroll2pointTableVisible(selectRow, 0);
            }
        }
    }
    
    private void jumpToNextSynapse() {
        jumpToNext(5);
    }

    private void jumpToNextConnected_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jumpToNextConnected_jButtonActionPerformed
        jumpToNextConnected();
    }//GEN-LAST:event_jumpToNextConnected_jButtonActionPerformed
    
    private void jumpToNextConnected() {
        jumpToNext(6);
    }

    private void extendAllDisplayPoints_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_extendAllDisplayPoints_jSpinnerStateChanged
        extendDisplayPoints = (Integer) extendAllDisplayPoints_jSpinner.getValue();
        extendSelectedDisplayPoints_jSpinner.setValue(extendDisplayPoints);
        //IJ.log("displayDepth state changed = "+extendAllDisplayPoints);
        updateDisplay();
    }//GEN-LAST:event_extendAllDisplayPoints_jSpinnerStateChanged

    private void overlayAllPoints_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayAllPoints_jCheckBoxActionPerformed
        if (overlayAllPoints_jCheckBox.isSelected()) {
            extendAllDisplayPoints_jSpinner.setEnabled(false);
        } else {
            extendAllDisplayPoints_jSpinner.setEnabled(true);
        }
        updateDisplay();
    }//GEN-LAST:event_overlayAllPoints_jCheckBoxActionPerformed

    private void somaLineWidth_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_somaLineWidth_jSpinnerStateChanged
        somaLine = (Integer) somaLineWidth_jSpinner.getValue() * 0.5f;
        allSomaLine = (somaLine - lineWidthOffset > 0.5) ? somaLine - lineWidthOffset : 0.5f;
        updateAllSomaTraceOL();
        updateSelectedSomaRoi();
        updateOverlay();
    }//GEN-LAST:event_somaLineWidth_jSpinnerStateChanged
    private void updateSomaTraceOL(boolean updateAll) {
        Overlay[] overlay = (updateAll) ? allSomaTraceOL : selectedSomaTraceOL;
        if (overlay != null) {
            for (Overlay somaTraceOL : overlay) {
                for (int j = 0; j < somaTraceOL.size(); j++) {
                    somaTraceOL.get(j).setStrokeWidth((updateAll) ? allSomaLine : somaLine);
                }
            }
        }
    }
    
    private void updateAllSomaTraceOL() {
        updateSomaTraceOL(true);
    }

    private void updateSelectedSomaRoi() {
        updateSomaTraceOL(false);
    }

    private void arborLineWidth_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_arborLineWidth_jSpinnerStateChanged
        arborLine = (Integer) arborLineWidth_jSpinner.getValue() * 0.5f;
        if (selectedArborTraceOL != null) {
            for (int j = 0; j < selectedArborTraceOL.size(); j++) {
                selectedArborTraceOL.get(j).setStrokeWidth(arborLine);
            }
        }
        updateOverlay();
    }//GEN-LAST:event_arborLineWidth_jSpinnerStateChanged

    private void branchLineWidth_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_branchLineWidth_jSpinnerStateChanged
        branchLine = (Integer) branchLineWidth_jSpinner.getValue() * 0.5f;
        if (selectedBranchTraceOL != null) {
            for (int j = 0; j < selectedBranchTraceOL.size(); j++) {
                selectedBranchTraceOL.get(j).setStrokeWidth(branchLine);
            }
        }
        updateOverlay();
    }//GEN-LAST:event_branchLineWidth_jSpinnerStateChanged

    private void synapseRadius_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_synapseRadius_jSpinnerStateChanged
        double oldSynapseRadius = synapseRadius;
        synapseRadius = ((Integer) synapseRadius_jSpinner.getValue()).doubleValue();
        synapseSize = synapseRadius * 2 + 1;
        double synapseRadiusOffset = oldSynapseRadius - synapseRadius;
        updateSelectedSynapseConnectionRoi(synapseRadiusOffset);

        double oldAllSynapseRadius = allSynapseRadius;
        allSynapseRadius = (synapseRadius - lineWidthOffset / 2 > 0.5) ? synapseRadius - lineWidthOffset / 2 : 0.5;
        allSynapseSize = allSynapseRadius * 2;
        double allSynapseRadiusOffset = oldAllSynapseRadius - allSynapseRadius;
        updateAllSynapseConnectionRoi(allSynapseRadiusOffset);

        updateOverlay();
    }//GEN-LAST:event_synapseRadius_jSpinnerStateChanged
    
    private void updateSynapseConnectionRoiHelper(Overlay ol, double offset, double size, double radius) {
        for (int j = 0; j < ol.size(); j++) {
                OvalRoi oldRoi = (OvalRoi) ol.get(0);
                ol.remove(0);
                OvalRoi newRoi = new OvalRoi(
                        oldRoi.getBounds().x + offset, oldRoi.getBounds().y + offset,
                        size, size);
                newRoi.setName(oldRoi.getName());
                newRoi.setPosition(0, oldRoi.getZPosition(), oldRoi.getTPosition());
                newRoi.setStrokeColor(oldRoi.getStrokeColor());
                newRoi.setStrokeWidth(radius);
                ol.add(newRoi);
            }
    }
    
    private void updateSynapseConnectionRoi(double offset, boolean updateAll) {
        Overlay neuronSynapseOL = (updateAll) ? allNeuronSynapseOL : selectedNeuronSynapseOL;
        Overlay neuronConnectedOL = (updateAll) ? allNeuronConnectedOL : selectedNeuronConnectedOL;
        Overlay somaSynapseOL = (updateAll) ? allSomaSynapseOL : selectedArborSynapseOL;
        Overlay somaConnectedOL = (updateAll) ? allSomaConnectedOL : selectedArborConnectedOL;

        double size = (updateAll) ? allSynapseSize: synapseSize;
        double radius = (updateAll) ? allSynapseRadius: synapseRadius;
        
        if (neuronSynapseOL != null) updateSynapseConnectionRoiHelper(neuronSynapseOL, offset, size, radius);
        if (neuronConnectedOL != null) updateSynapseConnectionRoiHelper(neuronConnectedOL, offset, size, radius);
        if (somaSynapseOL != null) updateSynapseConnectionRoiHelper(somaSynapseOL, offset, size, radius);
        if (somaConnectedOL != null) updateSynapseConnectionRoiHelper(somaConnectedOL, offset, size, radius);
        
        if (updateAll) return;
        
        if (selectedBranchSynapseOL != null) updateSynapseConnectionRoiHelper(selectedBranchSynapseOL, offset, size, radius);
        if (selectedBranchConnectedOL != null) updateSynapseConnectionRoiHelper(selectedBranchConnectedOL, offset, size, radius);
        if (selectedSomaSynapseOL != null) updateSynapseConnectionRoiHelper(selectedSomaSynapseOL, offset, size, radius);
        if (selectedSomaConnectedOL != null) updateSynapseConnectionRoiHelper(selectedSomaConnectedOL, offset, size, radius);
    }
    
    private void updateAllSynapseConnectionRoi(double offset) {
        updateSynapseConnectionRoi(offset, true);
    }

    private void updateSelectedSynapseConnectionRoi(double offset) {
        updateSynapseConnectionRoi(offset, false);
    }

    private void pointBoxRadiu_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_pointBoxRadiu_jSpinnerStateChanged
        pointBoxRadius = (Integer) pointBoxRadiu_jSpinner.getValue();
        updatePointBox();
    }//GEN-LAST:event_pointBoxRadiu_jSpinnerStateChanged

    private void spineLineWidth_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spineLineWidth_jSpinnerStateChanged
        spineLine = (Integer) spineLineWidth_jSpinner.getValue() * 0.5f;
        allSpineLine = (spineLine - lineWidthOffset > 0) ? spineLine - lineWidthOffset : 0.5f;
        updateAllNeuronSpineOL();
        updateAllSomaSpineOL();
        if (selectedNeuronSpineOL != null) {
            for (int j = 0; j < selectedNeuronSpineOL.size(); j++) {
                selectedNeuronSpineOL.get(j).setStrokeWidth(spineLine);
            }
        }
        if (selectedArborSpineOL != null) {
            for (int j = 0; j < selectedArborSpineOL.size(); j++) {
                selectedArborSpineOL.get(j).setStrokeWidth(spineLine);
            }
        }
        if (selectedBranchSpineOL != null) {
            for (int j = 0; j < selectedBranchSpineOL.size(); j++) {
                selectedBranchSpineOL.get(j).setStrokeWidth(spineLine);
            }
        }
        if (selectedSomaSpineOLextPt != null) {
            for (Overlay somaSpineOLextPt : selectedSomaSpineOLextPt) {
                for (int j = 0; j < somaSpineOLextPt.size(); j++) {
                    somaSpineOLextPt.get(j).setStrokeWidth(spineLine);
                }
            }
        }
        updateOverlay();
    }//GEN-LAST:event_spineLineWidth_jSpinnerStateChanged

    private void updateAllNeuronSpineOL() {
        if (allNeuronSpineOL != null) {
            for (int j = 0; j < allNeuronSpineOL.size(); j++) {
                allNeuronSpineOL.get(j).setStrokeWidth(allSpineLine);
            }
        }
    }

    private void updateAllSomaSpineOL() {
        if (allSomaSpineOLextPt != null) {
            for (Overlay somaSpineOLextPt : allSomaSpineOLextPt) {
                for (int j = 0; j < somaSpineOLextPt.size(); j++) {
                    somaSpineOLextPt.get(j).setStrokeWidth(allSpineLine);
                }
            }
        }
    }

    private void pointBoxLineWidth_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_pointBoxLineWidth_jSpinnerStateChanged
        pointBoxLine = (Integer) pointBoxLineWidth_jSpinner.getValue() * 0.5f;
        updatePointBox();
    }//GEN-LAST:event_pointBoxLineWidth_jSpinnerStateChanged

    private void copyToEditTarget_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyToEditTarget_jButtonActionPerformed
        copyToEditTarget();
    }//GEN-LAST:event_copyToEditTarget_jButtonActionPerformed
    private void copyToEditTarget() {
        if (neuronList_jTree.getSelectionCount() == 1) {
            ntNeuronNode neuronTreeNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
            if (neuronTreeNode.isBranchNode()) { // neuronTree selected a branch node
                editTargetNodeName = neuronTreeNode.toString();
                editTargetName_jLabel.setText(editTargetNodeName);
            } else { // selected a soma node
                if (displaySomaList_jTree.getSelectionCount() == 1) {
                    ntNeuronNode somaSliceTreeNode = (ntNeuronNode) displaySomaList_jTree.getLastSelectedPathComponent();
                    editTargetNodeName = somaSliceTreeNode.toString();
                    editTargetName_jLabel.setText(editTargetNodeName);
                }
            }
        }
    }

    private void gotoConnection_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gotoConnection_jButtonActionPerformed
        gotoConnectedSynapse();
    }//GEN-LAST:event_gotoConnection_jButtonActionPerformed
    private void gotoConnectedSynapse() {
        if (pointTable_jTable.getSelectedRowCount() == 1) {
            String connectedSynapseName = (String) pointTableModel.getValueAt(pointTable_jTable.getSelectedRow(), 6);
            neuronList_jTree.getSelectionPath();
            if (!"0".equals(connectedSynapseName)) {
                ntNeuronNode singleSelectedTableNode = getTracingNodeFromPointTableSelection();
                displayConnectedNode(singleSelectedTableNode.toString(), connectedSynapseName);
            }
        }
    }

    private void toggleConnection_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleConnection_jButtonActionPerformed
        toggleConnection();
    }//GEN-LAST:event_toggleConnection_jButtonActionPerformed
    private void toggleConnection() {
        if (pointTable_jTable.getSelectedRowCount() != 1) {
            IJ.error("Select one table point to add or remove connection !");
            return;
        }
        int row = pointTable_jTable.getSelectedRow();
        if (((String) pointTableModel.getValueAt(row, 6)).equals("0")) {
            if (editTargetNodeName.equals("0")) {
                String selectedNeuronNumber = ((ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent()).getNeuronNumber();
                ArrayList<String> containedRoiNames = getContainedRoiNamesOnScreen(selectedNeuronNumber, crossX, crossY, crossZ, roiSearchRange);
                String foundTargetNodeName = getNearestRoiName(crossX, crossY, crossZ, containedRoiNames);
                if (foundTargetNodeName.equals("0")) {
                    IJ.error("No nearby neuron to connect to !");
                    return;
                }
                //IJ.log("connect searched targetNode ");
                formConnectionBetweenSelectedPointAndNodeName(foundTargetNodeName);
            } else {
                //IJ.log("connect selected targetNode");
                formConnectionBetweenSelectedPointAndNodeName(editTargetNodeName);
            }
            //IJ.log("connection fone");
        } else {
            eraseConnectionFromPoint();
        }
        history.saveHistory();
    }

    private boolean formConnectionBetweenSelectedPointAndNodeName(String targetNodeName) {
        // get selected node
        ntNeuronNode selectedNode = getTracingNodeFromPointTableSelection();
        // get target node
        ntNeuronNode targetNode = getTracingNodeByNodeName(targetNodeName);

        // check whether selected neuron is the same as the Second Selection
        String selectedNeuronNumber = selectedNode.getNeuronNumber();
        String targetNeuronNumber = targetNodeName.contains("-") ? targetNodeName.split("-")[0] : targetNodeName.split(":")[0];
        if (selectedNeuronNumber.equals(targetNeuronNumber)) {
            IJ.error("Select two different neurons to form connection !");
            return false;
        }

        // check node types
        String selectedNodeType = selectedNode.getType();
        String selectedNodeName = selectedNode.toString();
        String targetNodeType = targetNode.getType();
        if (selectedNodeType.equals("Neurite")) {
            IJ.error(selectedNodeName + " has unknown type!");
            updateInfo(selectedNodeName + " has unknown type!");
            return false;
        }
        if (targetNodeType.equals("Neurite")) {
            IJ.error(targetNodeName + " has unknown type!");
            updateInfo(targetNodeName + " has unknown type!");
            return false;
        }
        String selectedNodeFlowType = "input";
        String targetNodeFlowType = "input";
        if (!selectedNodeType.equals("Axon")) {
            selectedNodeFlowType = "output";
        }
        if (!targetNodeType.equals("Axon")) {
            targetNodeFlowType = "output";
        }
        if (selectedNodeFlowType.equals(targetNodeFlowType)) {
            IJ.error("Connection CANNOT be set between Neuron "
                    + selectedNeuronNumber + " (" + selectedNodeType
                    + ") and Neuron " + targetNeuronNumber + " (" + targetNodeType + ") !");
            updateInfo("Connection CANNOT be set between " + selectedNodeType + " and " + targetNodeType + " !");
            return false;
        }

        // find the closest point to the pointTable selected point to connect
        int insertPosition = pointTable_jTable.getSelectedRow();
        int x = ((Float) pointTableModel.getValueAt(insertPosition, 1)).intValue();
        int y = ((Float) pointTableModel.getValueAt(insertPosition, 2)).intValue();
        int z = ((Float) pointTableModel.getValueAt(insertPosition, 3)).intValue();
        ArrayList<String[]> targetTracingResult = targetNode.getTracingResult();
        int connectPosition = -1;
        if (hasStartPt && hasEndPt) {
            for (int n = 0; n < targetTracingResult.size(); n++) {
                String[] tracingPt = targetTracingResult.get(n);
                if (endPoint[1] == Integer.parseInt(tracingPt[1])) {
                    if (endPoint[2] == Integer.parseInt(tracingPt[2])) {
                        if (endPoint[3] == Integer.parseInt(tracingPt[3])) {
                            connectPosition = n;
                            break;
                        }
                    }
                }
            }
        } else {
            connectPosition = getPositionInTracingResultWithSmallestDistanceToPoint(targetTracingResult, x, y, z)[0];
        }
        if (connectPosition < 0) {
            IJ.error(targetNodeName + " has NO tracing result to connect!");
            return false;
        }
        // found connection position -- need to check redundancy
        int maxSearch = (connectPosition + 10 < targetTracingResult.size() - 1) ? connectPosition + 10 : targetTracingResult.size() - 1;
        int minSearch = (connectPosition - 10 > 0) ? connectPosition - 10 : 0;
        for (int i = minSearch; i <= maxSearch; i++) {
            String targetConnectedNodeName = ((String[]) targetTracingResult.get(i))[6];
            //IJ.log(targetConnectedNodeName);
            if (!targetConnectedNodeName.equals("0")) {
                targetConnectedNodeName = targetConnectedNodeName.split("#")[1];
                if (targetConnectedNodeName.equals(selectedNodeName)) {
                    IJ.error(targetNodeName + " is already connected to " + selectedNodeName + " at this location !");
                    return false;
                }
            }
        }
//IJ.log("ok1");        
        // if selectedNode is not connected to targetNode yet
        // find its connection position
        boolean hasEmptySpot = false;
        int offset = 0;
//IJ.log("ok2");
        while (connectPosition - offset >= minSearch || connectPosition + offset <= maxSearch) {
            String targetConnectedNodeName = ((String[]) targetTracingResult.get(connectPosition - offset))[6];
            if (targetConnectedNodeName.equals("0")) {
                connectPosition = connectPosition - offset;
                hasEmptySpot = true;
                break;
            }
            targetConnectedNodeName = ((String[]) targetTracingResult.get(connectPosition + offset))[6];
            if (targetConnectedNodeName.equals("0")) {
                connectPosition = connectPosition + offset;
                hasEmptySpot = true;
                break;
            }
            offset++;
        }
//IJ.log("ok3");
        if (hasEmptySpot) {
            //saveHistory();
            recordTreeExpansionSelectionStatus();
//IJ.log("ok4");
            //set connections
            String selectedConnectionNumber = selectedNode.getNextConnectionNumber();
            String targetConnectionNumber = targetNode.getNextConnectionNumber();
            String selectedSynapseName = selectedConnectionNumber + "#" + targetNodeName + "#" + targetConnectionNumber;
            String targetSynapseName = targetConnectionNumber + "#" + selectedNodeName + "#" + selectedConnectionNumber;

            selectedNode.setSynapse(insertPosition, (int) 1);
            selectedNode.setConnectionTo(insertPosition, selectedSynapseName);
            targetNode.setSynapse(connectPosition, (int) 1);
            targetNode.setConnectionTo(connectPosition, targetSynapseName);
//IJ.log("ok5");
            imp.killRoi();
            targetNodeName = "0";
            editTargetName_jLabel.setText(targetNodeName);
            updateTrees();
            restoreTreeExpansionSelectionStatus();
            return true;
        } else {
            IJ.error(targetNodeName + "/n has no more empty spot to form connection close this spot !");
            return false;
        }
    }

    //TODO Trace with new synapse methods
    private boolean eraseConnectionFromPoint() {
        int selectedRow = pointTable_jTable.getSelectedRow();
        String selectedSynapseName = (String) pointTableModel.getValueAt(selectedRow, 6);
        // get selectedNode and connectedNode
        ntNeuronNode selectedNode = getTracingNodeFromPointTableSelection();
        String selectedNodeName = selectedNode.toString();
        String[] synapseNames = selectedSynapseName.split("#");
        String connectedNodeName = synapseNames[1];
        String connectedSynapseName = synapseNames[2] + "#" + selectedNodeName + "#" + synapseNames[0];
        ntNeuronNode connectedNode = getTracingNodeByNodeName(connectedNodeName);

        // remove connection from selectedNode, but keep its synapse
        // remove connection from connectedNode and ask for whether to keep its synapse        
        YesNoCancelDialog yncDialog = new YesNoCancelDialog(new java.awt.Frame(),
                "Break Connection", "Do you want to keep the synapse on " + connectedNodeName + " ?");
        if (!yncDialog.cancelPressed()) {
            boolean returnValue = true;
            //saveHistory();
            recordTreeExpansionSelectionStatus();

            // set synapse, spine at the selectedNode
            Object selectedSpineStatus = pointTableModel.getValueAt(selectedRow, 0);
            String selectedTag = selectedSpineStatus.toString();
            if (selectedTag.contains(":Spine#")) {
                traceHelper.removeSpine(selectedTag);
                selectedNode.setSpine(selectedRow, "0");
                String newTag = selectedTag.split(":")[0];
                if (selectedTag.contains("/")) {
                    newTag = newTag + "/" + selectedTag.split("/")[1];
                } else if (selectedTag.endsWith("*")) {
                    newTag = newTag + "*";
                }
                pointTableModel.setValueAt(newTag, selectedRow, 0);
            } else {
                selectedNode.setSynapse(selectedRow, (int) 0);
            }
            selectedNode.setConnectionTo(selectedRow, "0");

            int connectedPosition = getPositionInTracingResultBySynapseName(connectedNode.getTracingResult(), connectedSynapseName);
            if (connectedPosition >= 0) {
                if (!yncDialog.yesPressed()) {
                    String connectedSpineStatus = connectedNode.getTracingResult().get(connectedPosition)[0];
                    if (connectedSpineStatus.contains(":Spine#")) {
                        traceHelper.removeSpine(connectedSpineStatus);
                        connectedNode.setSpine(connectedPosition, "0");
                    } else {
                        connectedNode.setSynapse(connectedPosition, (int) 0);
                    }
                }
                connectedNode.setConnectionTo(connectedPosition, "0");
            } else {
                IJ.error(connectedSynapseName + " is NOT found in " + connectedNodeName);
                returnValue = false;
            }
            updateTrees();
            restoreTreeExpansionSelectionStatus();
            return returnValue;
        } else {
            return false;
        }
    }

    private void toggleSynapse_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleSynapse_jButtonActionPerformed
        increase_synapse_number();
    }//GEN-LAST:event_toggleSynapse_jButtonActionPerformed

    private void autosaveSetup_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autosaveSetup_jMenuItemActionPerformed
        history.autosaveSetup();
    }//GEN-LAST:event_autosaveSetup_jMenuItemActionPerformed

    private void setAxon_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setAxon_jButtonActionPerformed
        setTracingType("Axon");
    }//GEN-LAST:event_setAxon_jButtonActionPerformed

    private void setBasalDendrite_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setBasalDendrite_jButtonActionPerformed
        setTracingType("Dendrite");
    }//GEN-LAST:event_setBasalDendrite_jButtonActionPerformed

    private void setApicalDendrite_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setApicalDendrite_jButtonActionPerformed
        setTracingType("Apical");
    }//GEN-LAST:event_setApicalDendrite_jButtonActionPerformed

    private void exportSWCfromSelectedNeurons_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportSWCfromSelectedNeurons_jMenuItemActionPerformed
        exportSelectedNeuronSWC();
    }//GEN-LAST:event_exportSWCfromSelectedNeurons_jMenuItemActionPerformed
    private void exportSelectedNeuronSWC() {
        if (imp != null && neuronList_jTree.getSelectionCount() >= 1) {
            String prefixName = imp.getTitle();
            DirectoryChooser dc = new DirectoryChooser("Choose SWC export folder ...");
            String directory = dc.getDirectory();
            if (directory == null) {
                return;
            }
            if (prefixName.endsWith(".tif")) {
                prefixName = prefixName.substring(0, prefixName.length() - 4);
            }
            GenericDialog gd = new GenericDialog("Export SWC ...");
            gd.addMessage("x/y/z resolutions (0 = unknown resolution)");
            gd.addNumericField("x: ", xyzResolutions[0], 3, 6, " um / pixel");
            gd.addNumericField("y: ", xyzResolutions[1], 3, 6, " um / pixel");
            gd.addNumericField("z: ", xyzResolutions[2], 3, 6, " um / pixel");
            gd.addCheckbox("Extended SWC format (mark synapse) ?", false);
            gd.addCheckbox("Export Spine ?", false);
            gd.showDialog();
            if (gd.wasCanceled()) {
                return;
            }
            double[] resolutions = new double[3];
            resolutions[0] = gd.getNextNumber();
            resolutions[1] = gd.getNextNumber();
            resolutions[2] = gd.getNextNumber();
            boolean stdFormat = !gd.getNextBoolean();
            boolean expSpine = gd.getNextBoolean();
            if ((resolutions[0] + "").equals("NaN") || resolutions[0] < 0
                    || (resolutions[1] + "").equals("NaN") || resolutions[1] < 0
                    || (resolutions[2] + "").equals("NaN") || resolutions[2] < 0) {
                resolutions[0] = 0;
                resolutions[1] = 0;
                resolutions[2] = 0;
            }

            try {
                ArrayList<String> selectedNeuronNumbers = getSelectedNeuronNumberSortSmall2Large();
                IO.exportSelectedNeuronSWC(rootNeuronNode, rootAllSomaNode, rootSpineNode,
                        selectedNeuronNumbers, directory, prefixName, resolutions, stdFormat, expSpine);
                updateInfo("SWC exported !");
            } catch (IOException e) {
                IJ.error(e.getMessage());
            }

        } else {
            IJ.error("Selected at least one neuron to export SWC !");
        }
    }

    private void volume_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volume_jMenuItemActionPerformed
        IJ.error("Coming soon ...");
    }//GEN-LAST:event_volume_jMenuItemActionPerformed

    private void brainbowColor_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_brainbowColor_jCheckBoxActionPerformed
        updateDisplay();
    }//GEN-LAST:event_brainbowColor_jCheckBoxActionPerformed

    private void setNeurite_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setNeurite_jButtonActionPerformed
        setTracingType("Neurite");
    }//GEN-LAST:event_setNeurite_jButtonActionPerformed
    
    /**
     * sets the tracing type based on newType
     * @param newType 
     */
    
    protected void setTracingType(String newType) {
        ArrayList<String> selectedPrimaryNodeName = getSelectedPrimaryNodeName();
        if (selectedPrimaryNodeName.size() > 0) {
            recordTreeExpansionSelectionStatus();
            ntNeuronNode primaryNode = getNodeFromNeuronTreeByNodeName(selectedPrimaryNodeName.get(0));
            String selectedType = primaryNode.getType();
            if (neuronList_jTree.getSelectionCount() == 1
                    && ((selectedType.equals("Neurite"))
                    || (selectedType.equals("Dendrite") && newType.equals("Apical"))
                    || (newType.equals("Dendrite") && selectedType.equals("Apical")))) {
                setTracingTypeToPrimaryAndChild(primaryNode, newType, true);
            } else {
                GenericDialog gd = new GenericDialog("Remove All Connections ...");
                gd.addMessage("All connections will be removed if type is changed !");
                gd.addCheckbox("Keep synapses on the selected arbors ?", true);
                gd.addCheckbox("Keep synapses on the previously connected processes ?", false);
                gd.showDialog();
                if (gd.wasCanceled()) {
                    return;
                }
                boolean keepSynapseFromSelectedArbor = gd.getNextBoolean();
                boolean keepSynapseFromTargetProcess = gd.getNextBoolean();
                for (String primaryNodeName : selectedPrimaryNodeName) {
                    primaryNode = getNodeFromNeuronTreeByNodeName(primaryNodeName);
                    if (primaryNode == null) {
                        IJ.error("no node selected");
                        return;
                    }
                    selectedType = primaryNode.getType();
                    if (!selectedType.equals(newType)) {
                        if (!((selectedType.equals("Dendrite") && newType.equals("Apical"))
                                || (newType.equals("Dendrite") && selectedType.equals("Apical")))) {
                            removeAllConnectionsFromPrimaryAndChild(primaryNode,
                                    keepSynapseFromSelectedArbor, keepSynapseFromTargetProcess);
                        }
                        setTracingTypeToPrimaryAndChild(primaryNode, newType,
                                (selectedType.equals("Dendrite") && newType.equals("Apical"))
                                || (newType.equals("Dendrite") && selectedType.equals("Apical")));
                    }
                }

            }
            updateTrees();
            restoreTreeExpansionSelectionStatus();
            history.saveHistory();
        }
    }

    private void removeAllConnectionsFromPrimaryAndChild(ntNeuronNode primaryNode,
            boolean keepPrimaryNodeSynapse, boolean keepTargetNodeSynapse) {
        ArrayList<String[]> result = primaryNode.getTracingResult();
        for (int i = 0; i < result.size(); i++) {
            String[] point = result.get(i);
            if (!point[6].equals("0")) {
                // get connectedNode
                String[] synapseNames = point[6].split("#");
                String connectedNodeName = synapseNames[1];
                String connectedSynapseName = synapseNames[2] + "#" + primaryNode.toString() + "#" + synapseNames[0];
                ntNeuronNode connectedNode = getTracingNodeByNodeName(connectedNodeName);

                // remove connection from selectedNode and connectedNode,
                // and remove synapse if !keepSynapse
                if (!keepPrimaryNodeSynapse) {
                    primaryNode.setSynapse(i, (int) 0);
                }
                primaryNode.setConnectionTo(i, "0");
                int connectedPosition = getPositionInTracingResultBySynapseName(connectedNode.getTracingResult(), connectedSynapseName);
                if (connectedPosition >= 0) {
                    if (!keepTargetNodeSynapse) {
                        connectedNode.setSynapse(connectedPosition, (int) 0);
                    }
                    connectedNode.setConnectionTo(connectedPosition, "0");
                } else {
                    IJ.log(connectedSynapseName + " is NOT found in " + connectedNodeName);
                }
            }
        }
        for (int i = 0; i < primaryNode.getChildCount(); i++) {
            ntNeuronNode childNode = (ntNeuronNode) primaryNode.getChildAt(i);
            removeAllConnectionsFromPrimaryAndChild(childNode, keepPrimaryNodeSynapse, keepTargetNodeSynapse);
        }
    }

    private void setTracingTypeToPrimaryAndChild(ntNeuronNode primaryNode, String type, boolean keepSpine) {
        ArrayList<String[]> result = primaryNode.getTracingResult();
        boolean traceComplete = primaryNode.isComplete();
        if (keepSpine) {
            for (String[] point : result) {
                if (!point[0].contains(":Spine#")) {
                    point[0] = type;
                }
            }
            for (int i = 0; i < primaryNode.getChildCount(); i++) {
                ntNeuronNode childNode = (ntNeuronNode) primaryNode.getChildAt(i);
                setTracingTypeToPrimaryAndChild(childNode, type, keepSpine);
            }
        } else {
            for (int i = 0; i < result.size(); i++) {
                String[] point = result.get(i);
                if (point[0].contains(":Spine#")) {
                    traceHelper.removeSpine(point[0]);
                    primaryNode.setSpine(i, "0");
                }
                point[0] = type;
            }
            for (int i = 0; i < primaryNode.getChildCount(); i++) {
                ntNeuronNode childNode = (ntNeuronNode) primaryNode.getChildAt(i);
                setTracingTypeToPrimaryAndChild(childNode, type, keepSpine);
            }
        }
        if (!traceComplete) {
            primaryNode.toggleComplete();
        }
    }

    private void cropData_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cropData_jMenuItemActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_cropData_jMenuItemActionPerformed

    private void debug_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_debug_jMenuItemActionPerformed
        IJ.log("debug");
        //logAllChildNodeName(ntNeuronNode logNode);
        //logHistory();
    }//GEN-LAST:event_debug_jMenuItemActionPerformed

    private void addLabelToSelection_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addLabelToSelection_jButtonActionPerformed
        addLabelToSelection();
    }//GEN-LAST:event_addLabelToSelection_jButtonActionPerformed
    private void addLabelToSelection() {
        String label = selectionTag_jTextField.getText();
        if (label.contains("/")) {
            IJ.error("No '/' allowed in tag !");
            return;
        }
        if (label.contains("*")) {
            IJ.error("No '*' (Asterisk) allowed in tag !");
            return;
        }
        if (label.contains(" ")) {
            IJ.error("No 'space' allowed in tag !");
            return;
        }
        if (label.contains("\t")) {
            IJ.error("No 'tab' allowed in tag !");
            return;
        }
        if (label.contains("=")) {
            IJ.error("No '=' (logic equal) allowed in tag !");
            return;
        }
        if (label.contains("|")) {
            IJ.error("No '|' (logic OR) allowed in tag !");
            return;
        }
        if (label.contains("&")) {
            IJ.error("No '&' (logic AND) allowed in tag !");
            return;
        }
        if (label.contains("!")) {
            IJ.error("No '!' (logic NOT) allowed in tag !");
            return;
        }
        if (pointTable_jTable.getSelectedRowCount() > 0) {
            addLabelToSelectedPoint(label);
            return;
        }
        if (neuronList_jTree.getSelectionCount() > 0) {
            // add tags to neuron_jTree selection
            addLabelToSelectedNeuron(label, getSelectedNeuronNumberSortSmall2Large());
        }
    }

    private void addLabelToSelectedPoint(String pointLabel) {
        // add tags to pointTable selected points 
        if (pointTable_jTable.getSelectedRowCount() == 0) {
            return;
        }
        if (!pointLabel.equals("")) {
            pointLabel = "/" + pointLabel;
        }
        recordTreeExpansionSelectionStatus();
        ntNeuronNode selectedNode;
        if (displaySomaList_jTree.getSelectionCount() == 1) {
            selectedNode = (ntNeuronNode) displaySomaList_jTree.getLastSelectedPathComponent();
        } else {
            selectedNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
        }
        selectedNode = getTracingNodeByNodeName(selectedNode.toString());
        ArrayList<String[]> tracingResult = selectedNode.getTracingResult();
        for (int selectionRow : pointTable_jTable.getSelectedRows()) {
            String currentTag = tracingResult.get(selectionRow)[0];
            String asterisk = "";
            if (currentTag.contains("*")) {
                currentTag = currentTag.split("\\*")[0];
                asterisk = "*";
            }
            if (currentTag.contains("/")) {
                currentTag = currentTag.split("/")[0];
            }
            String newTag = currentTag + pointLabel + asterisk;
            if (currentTag.contains(":Spine#")) {
                selectedNode.setSpine(selectionRow, newTag.split("#")[1]);
            }
            tracingResult.get(selectionRow)[0] = newTag;
            pointTableModel.setValueAt(newTag, selectionRow, 0);
        }
        selectedNode.setTracingResult(tracingResult);
        updateTrees();
        restoreTreeExpansionSelectionStatus();
        history.saveHistory();
    }

    private void addLabelToSelectedNeuron(String neuronTag, ArrayList<String> treeSelectedNeurons) {
        // add tags to neuron_jTree selection, if not selected, add to neuronNumber_jTextField input
        if (!neuronTag.equals("")) {
            neuronTag = "/" + neuronTag;
        }
        if (!treeSelectedNeurons.isEmpty()) {
            recordTreeExpansionSelectionStatus();
            for (String neuronNumber : treeSelectedNeurons) {
                ntNeuronNode selectedNeuronNode = getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber);
                selectedNeuronNode.setName(neuronNumber + neuronTag);
            }
            updateTrees();
            restoreTreeExpansionSelectionStatus();
            history.saveHistory();
        }
    }

    private void selectNeurons_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectNeurons_jButtonActionPerformed
        String neuronTag = selectionTag_jTextField.getText();
        String selectedNeuronNumbers = selectNeuronNumber_jTextField.getText();
        selectTaggedNeurons(neuronTag, selectedNeuronNumbers);
        updateOverlay();
    }//GEN-LAST:event_selectNeurons_jButtonActionPerformed
    private void selectTaggedNeurons(String neuronTag, String selectedNeuronNumbers) {
        if (neuronTag.contains("/")) {
            IJ.error("No '/' allowed !");
            return;
        }
        if (neuronTag.contains(" ")) {
            IJ.error("No 'space' allowed !");
            return;
        }
        if (neuronTag.contains("\t")) {
            IJ.error("No 'tab' allowed !");
            return;
        }
        if (neuronTag.equals("") && selectedNeuronNumbers.equals("")) {
            IJ.error("Type in a valid Tag and/or Neuron Number to select !");
            return;
        }
        ArrayList<TreePath> selectedPaths = new ArrayList<>();
        ArrayList<ntNeuronNode> selectedSomaNodes = new ArrayList<>();
        ArrayList<ntNeuronNode> selectedPrimaryBranchNodes = new ArrayList<>();
        ArrayList<String> ANDtags = new ArrayList<>();
        ArrayList<String> ORtags = new ArrayList<>();
        ArrayList<String> NOTtags = new ArrayList<>();

        // filter selectedSomaNodes with "selectedNeuronNumbers"
        for (int i = 0; i < rootNeuronNode.getChildCount(); i++) {
            selectedSomaNodes.add((ntNeuronNode) rootNeuronNode.getChildAt(i));
        }
        updateSelectedSomaNodes(selectedSomaNodes, selectedNeuronNumbers);

        // filter neurons that match the selection tag
        if (neuronTag.startsWith("=")) {
            neuronTag = neuronTag.split("=")[1];
            for (int i = selectedSomaNodes.size() - 1; i >= 0; i--) {
                ntNeuronNode node = selectedSomaNodes.get(i);
                String nodeName = node.toString();
                if (nodeName.contains("/")) {
                    nodeName = (nodeName.split("/")[1]);
                    if (!nodeName.equals(neuronTag)) {
                        selectedSomaNodes.remove(i);
                    }
                } else {
                    selectedSomaNodes.remove(i);
                }
            }
        } else {
            if (!neuronTag.equals("")) {
                if (neuronTag.contains("|")) {
                    String[] neuronTagORsplit = neuronTag.split("\\|");
                    for (String ORsplit : neuronTagORsplit) {
                        if (ORsplit.contains("&") && ORsplit.contains("!")) {
                            if (ORsplit.indexOf("&") < ORsplit.indexOf("!")) {
                                String[] neuronTagANDsplit = ORsplit.split("&");
                                if (!neuronTagANDsplit[0].equals("")) {
                                    ORtags.add(neuronTagANDsplit[0]);
                                }
                                for (int i = 1; i < neuronTagANDsplit.length; i++) {
                                    if (neuronTagANDsplit[i].contains("!")) {
                                        String[] neuronTagNOTsplit = neuronTagANDsplit[i].split("!");
                                        ANDtags.add(neuronTagNOTsplit[0]);
                                        for (int j = 1; j < neuronTagNOTsplit.length; j++) {
                                            NOTtags.add(neuronTagNOTsplit[j]);
                                        }
                                    } else {
                                        ANDtags.add(neuronTagANDsplit[i]);
                                    }
                                }
                            } else {
                                String[] neuronTagNOTsplit = ORsplit.split("!");
                                if (!neuronTagNOTsplit[0].equals("")) {
                                    ORtags.add(neuronTagNOTsplit[0]);
                                }
                                for (int i = 1; i < neuronTagNOTsplit.length; i++) {
                                    if (neuronTagNOTsplit[i].contains("&")) {
                                        String[] neuronTagANDsplit = neuronTagNOTsplit[i].split("&");
                                        NOTtags.add(neuronTagANDsplit[0]);
                                        for (int j = 1; j < neuronTagANDsplit.length; j++) {
                                            ANDtags.add(neuronTagANDsplit[j]);
                                        }
                                    } else {
                                        NOTtags.add(neuronTagNOTsplit[i]);
                                    }
                                }
                            }
                        } else if (ORsplit.contains("&")) { // contains only "&"
                            String[] neuronTagANDsplit = ORsplit.split("&");
                            if (!neuronTagANDsplit[0].equals("")) {
                                ORtags.add(neuronTagANDsplit[0]);
                            }
                            for (int i = 1; i < neuronTagANDsplit.length; i++) {
                                ANDtags.add(neuronTagANDsplit[i]);
                            }
                        } else if (ORsplit.contains("!")) { // contains only "!"
                            String[] neuronTagNOTsplit = ORsplit.split("!");
                            if (!neuronTagNOTsplit[0].equals("")) {
                                ORtags.add(neuronTagNOTsplit[0]);
                            }
                            for (int i = 1; i < neuronTagNOTsplit.length; i++) {
                                NOTtags.add(neuronTagNOTsplit[i]);
                            }
                        } else { // do not contain "&" nor "!"
                            if (!ORsplit.equals("")) {
                                ORtags.add(ORsplit);
                            }
                        }
                    }
                } else if (neuronTag.contains("&")) {
                    String[] neuronTagANDsplit = neuronTag.split("&");
                    if (!neuronTagANDsplit[0].equals("")) {
                        if (neuronTagANDsplit[0].contains("!")) {
                            String[] neuronTagNOTsplit = neuronTagANDsplit[0].split("!");
                            if (!neuronTagNOTsplit[0].equals("")) {
                                ORtags.add(neuronTagNOTsplit[0]);
                            }
                            for (int j = 1; j < neuronTagNOTsplit.length; j++) {
                                NOTtags.add(neuronTagNOTsplit[j]);
                            }
                        } else {
                            ORtags.add(neuronTagANDsplit[0]);
                        }
                    }
                    for (int i = 1; i < neuronTagANDsplit.length; i++) {
                        if (neuronTagANDsplit[i].contains("!")) {
                            String[] neuronTagNOTsplit = neuronTagANDsplit[i].split("!");
                            ANDtags.add(neuronTagNOTsplit[0]);
                            for (int j = 1; j < neuronTagNOTsplit.length; j++) {
                                NOTtags.add(neuronTagNOTsplit[j]);
                            }
                        } else {
                            ANDtags.add(neuronTagANDsplit[i]);
                        }
                    }
                } else if (neuronTag.contains("!")) {
                    String[] neuronTagNOTsplit = neuronTag.split("!");
                    if (!neuronTagNOTsplit[0].equals("")) {
                        ORtags.add(neuronTagNOTsplit[0]);
                    }
                    for (int i = 1; i < neuronTagNOTsplit.length; i++) {
                        NOTtags.add(neuronTagNOTsplit[i]);
                    }
                } else { // no operator
                    ORtags.add(neuronTag);
                }
            }
            /*
            IJ.log("ORtags:");
            for (String ORtag: ORtags){
                IJ.log("   "+ORtag);
            }
            IJ.log("ANDtags:");
            for (String ANDtag : ANDtags) {
                IJ.log("   " + ANDtag);
            }
            IJ.log("NOTtags:");
            for (String NOTtag : NOTtags) {
                IJ.log("   " + NOTtag);
            }
             */
            updateSelectedSomaNodes(selectedSomaNodes, ORtags, ANDtags, NOTtags);
        }

        // find primary branch nodes that match the additional selection criteria
        String operation = (String) selectTagOperator_jComboBox.getSelectedItem();
        String criteria = (String) selectTagAdditionalCriteria_jComboBox.getSelectedItem();
        ArrayList<String> criteriaList = new ArrayList<String>();
        if (!criteria.equals("Whole Neuron")) {
            if (operation.equals("+")) {
                if (criteria.equals("Neurite")) {
                    criteriaList.add("Neurite");
                } else if (criteria.equals("Axon")) {
                    criteriaList.add("Axon");
                } else if (criteria.equals("All Dendrite")) {
                    criteriaList.add("Dendrite");
                    criteriaList.add("Apical");
                } else if (criteria.startsWith("(Basal)")) {
                    criteriaList.add("Dendrite");
                } else if (criteria.startsWith("Apical")) {
                    criteriaList.add("Apical");
                }
            } else if (operation.equals("-")) {
                if (criteria.equals("Neurite")) {
                    criteriaList.add("Axon");
                    criteriaList.add("Dendrite");
                    criteriaList.add("Apical");
                } else if (criteria.equals("Axon")) {
                    criteriaList.add("Neurite");
                    criteriaList.add("Dendrite");
                    criteriaList.add("Apical");
                } else if (criteria.equals("All Dendrite")) {
                    criteriaList.add("Neurite");
                    criteriaList.add("Axon");
                } else if (criteria.startsWith("(Basal)")) {
                    criteriaList.add("Neurite");
                    criteriaList.add("Axon");
                    criteriaList.add("Apical");
                } else if (criteria.startsWith("Apical")) {
                    criteriaList.add("Neurite");
                    criteriaList.add("Axon");
                    criteriaList.add("Dendrite");
                }
            }
            for (ntNeuronNode somaNode : selectedSomaNodes) {
                for (int i = 0; i < somaNode.getChildCount(); i++) {
                    ntNeuronNode primaryNode = (ntNeuronNode) somaNode.getChildAt(i);
                    String primryIdentity = primaryNode.getType();
                    for (String c : criteriaList) {
                        if (primryIdentity.equals(c)) {
                            selectedPrimaryBranchNodes.add(primaryNode);
                            break;
                        }
                    }
                }
            }
        }

        // set selection to neuron tree
        if (selectedSomaNodes.size() > 0 && criteria.equals("Whole Neuron")) {
            for (ntNeuronNode node : selectedSomaNodes) {
                TreePath selectionPath = new TreePath(node.getPath());
                selectedPaths.add(selectionPath);
            }
        }
        if (selectedPrimaryBranchNodes.size() > 0 && !criteria.equals("Whole Neuron")) {
            for (ntNeuronNode node : selectedPrimaryBranchNodes) {
                TreePath selectionPath = new TreePath(node.getPath());
                selectedPaths.add(selectionPath);
            }
        }
        if (selectedPaths.size() > 0) {
            TreePath[] selectionPaths = new TreePath[selectedPaths.size()];
            for (int i = 0; i < selectedPaths.size(); i++) {
                selectionPaths[i] = (TreePath) selectedPaths.get(i);
            }
            main_jTabbedPane.setSelectedIndex(2);
            neuronList_jTree.clearSelection();
            neuronList_jTree.setSelectionPaths(selectionPaths);
            neuronList_jTree.scrollPathToVisible(selectionPaths[0]);
            history.saveHistory();
        } else {
            IJ.error("No neuron is selected - check tag typo (case sensitive) !");
        }
    }

    private void updateSelectedSomaNodes(ArrayList<ntNeuronNode> selectedSomaNodes,
            String selectedNeuronNumbers) {
        if (!selectedNeuronNumbers.equals("")) {
            if (selectedNeuronNumbers.contains(";")) {
                String[] selectedNumbers = selectedNeuronNumbers.split(";");
                for (int i = selectedSomaNodes.size() - 1; i >= 0; i--) {
                    ntNeuronNode node = (ntNeuronNode) selectedSomaNodes.get(i);
                    int nodeNumber = Integer.parseInt(node.getNeuronNumber());
                    boolean deleteNode = true;
                    for (String selectedNumber : selectedNumbers) {
                        if (selectedNumber.contains("-")) {
                            String[] selectRange = selectedNumber.split("-");
                            int min = Integer.parseInt(selectRange[0]);
                            int max = Integer.parseInt(selectRange[1]);
                            if (min > max) {
                                min = Integer.parseInt(selectRange[1]);
                                max = Integer.parseInt(selectRange[0]);
                            }
                            if (nodeNumber >= min && nodeNumber <= max) {
                                deleteNode = false;
                                break;
                            }
                        } else {
                            if (nodeNumber == Integer.parseInt(selectedNumber)) {
                                deleteNode = false;
                                break;
                            }
                        }
                    }
                    if (deleteNode) {
                        selectedSomaNodes.remove(i);
                    }
                }
            } else if (selectedNeuronNumbers.contains("-")) {
                for (int i = selectedSomaNodes.size() - 1; i >= 0; i--) {
                    ntNeuronNode node = (ntNeuronNode) selectedSomaNodes.get(i);
                    int nodeNumber = Integer.parseInt(node.getNeuronNumber());
                    String[] selectRange = selectedNeuronNumbers.split("-");
                    int min = Integer.parseInt(selectRange[0]);
                    int max = Integer.parseInt(selectRange[1]);
                    if (min > max) {
                        min = Integer.parseInt(selectRange[1]);
                        max = Integer.parseInt(selectRange[0]);
                    }
                    if (nodeNumber < min || nodeNumber > max) {
                        selectedSomaNodes.remove(i);
                    }
                }
            } else {
                for (int i = selectedSomaNodes.size() - 1; i >= 0; i--) {
                    ntNeuronNode node = (ntNeuronNode) selectedSomaNodes.get(i);
                    int nodeNumber = Integer.parseInt(node.getNeuronNumber());
                    if (nodeNumber != Integer.parseInt(selectedNeuronNumbers)) {
                        selectedSomaNodes.remove(i);
                    }
                }
            }
        }
    }

    private void updateSelectedSomaNodes(ArrayList<ntNeuronNode> selectedSomaNodes,
            ArrayList<String> ORtags, ArrayList<String> ANDtags, ArrayList<String> NOTtags) {
        // filter by ORtags
        if (!ORtags.isEmpty()) {
            for (int i = selectedSomaNodes.size() - 1; i >= 0; i--) {
                ntNeuronNode node = selectedSomaNodes.get(i);
                String nodeName = node.toString();
                boolean removeNode = true;
                if (nodeName.contains("/")) {
                    nodeName = (nodeName.split("/")[1]);
                    String[] nodeTags = nodeName.split(";");
                    for (String nodeTag : nodeTags) {
                        for (String ORtag : ORtags) {
                            if (!nodeTag.equals("")) {
                                if (nodeTag.equals(ORtag)) {
                                    removeNode = false;
                                    break;
                                }
                            }
                        }
                        if (!removeNode) {
                            break;
                        }
                    }
                }
                if (removeNode) {
                    selectedSomaNodes.remove(i);
                }
            }
        }
        // filter by ANTtags
        if (!ANDtags.isEmpty()) {
            for (int i = selectedSomaNodes.size() - 1; i >= 0; i--) {
                ntNeuronNode node = selectedSomaNodes.get(i);
                String nodeName = node.toString();
                boolean removeNode = true;
                if (nodeName.contains("/")) {
                    nodeName = (nodeName.split("/")[1]);
                    String[] nodeTags = nodeName.split(";");
                    for (String ANDtag : ANDtags) {
                        removeNode = true;
                        for (String nodeTag : nodeTags) {
                            if (nodeTag.equals(ANDtag)) {
                                removeNode = false;
                                break;
                            }
                        }
                        if (removeNode) {
                            break;
                        }
                    }
                }
                if (removeNode) {
                    selectedSomaNodes.remove(i);
                }
            }
        }
        // filter by NOTtags
        if (!NOTtags.isEmpty()) {
            for (int i = selectedSomaNodes.size() - 1; i >= 0; i--) {
                ntNeuronNode node = selectedSomaNodes.get(i);
                String nodeName = node.toString();
                boolean removeNode = false;
                if (nodeName.contains("/")) {
                    nodeName = (nodeName.split("/")[1]);
                    String[] nodeTags = nodeName.split(";");
                    for (String nodetag : nodeTags) {
                        for (String NOTtag : NOTtags) {
                            if (nodetag.equals(NOTtag)) {
                                removeNode = true;
                                break;
                            }
                        }
                    }
                }
                if (removeNode) {
                    selectedSomaNodes.remove(i);
                }
            }
        }
    }

    private void allNeuronLineWidthOffset_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_allNeuronLineWidthOffset_jSpinnerStateChanged
        lineWidthOffset = (Integer) allNeuronLineWidthOffset_jSpinner.getValue() * 0.5f;

        allSomaLine = (somaLine - lineWidthOffset > 0.5) ? somaLine - lineWidthOffset : 0.5f;
        updateAllSomaTraceOL();

        allNeuronLine = (neuronLine - lineWidthOffset > 0.5) ? neuronLine - lineWidthOffset : 0.5f;
        updateAllNeuronTraceOL();

        allSpineLine = (spineLine - lineWidthOffset > 0.5) ? spineLine - lineWidthOffset : 0.5f;
        updateAllNeuronSpineOL();
        updateAllSomaSpineOL();

        double oldAllSynapseSize = allSynapseSize;
        allSynapseRadius = (synapseRadius - lineWidthOffset / 2 > 0.5) ? synapseRadius - lineWidthOffset / 2 : 0.5;
        allSynapseSize = allSynapseRadius * 2;
        int offset = (int) (oldAllSynapseSize / 2) - (int) (allSynapseSize / 2);
        updateAllSynapseConnectionRoiLineWidth(offset);

        updateOverlay();
    }//GEN-LAST:event_allNeuronLineWidthOffset_jSpinnerStateChanged
    
    private void updateAllRoiLineWidth(Overlay ol, int offset) {
        for (int j = 0; j < ol.size(); j++) {
            OvalRoi oldRoi = (OvalRoi) ol.get(0);
            ol.remove(0);
            OvalRoi newRoi = new OvalRoi(
                    oldRoi.getBounds().x + offset, oldRoi.getBounds().y + offset,
                    allSynapseSize, allSynapseSize);
            newRoi.setName(oldRoi.getName());
            newRoi.setPosition(0, oldRoi.getZPosition(), oldRoi.getTPosition());
            newRoi.setStrokeColor(oldRoi.getStrokeColor());
            newRoi.setStrokeWidth(allSynapseRadius);
            ol.add(newRoi);
        }
    }
    
    private void updateAllSynapseConnectionRoiLineWidth(int offset) {
        if (allNeuronSynapseOL != null) updateAllRoiLineWidth(allNeuronSynapseOL, offset);
        if (allNeuronConnectedOL != null) updateAllRoiLineWidth(allNeuronConnectedOL, offset);
        if (allSomaSynapseOL != null) updateAllRoiLineWidth(allSomaSynapseOL, offset);
        if (allSomaConnectedOL != null) updateAllRoiLineWidth(allSomaConnectedOL, offset);
    }

    private void copyNeuronTag_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyNeuronTag_jButtonActionPerformed
        if (neuronList_jTree.getSelectionCount() == 1) {
            ntNeuronNode selectedNode = (ntNeuronNode) neuronList_jTree.getSelectionPath().getPathComponent(1);
            String tag = selectedNode.toString();
            if (tag.contains("/")) {
                selectionTag_jTextField.setText(tag.split("/")[1]);
            } else {
                selectionTag_jTextField.setText("");
            }
        } else {
            IJ.error("Select one neuron to copy its tag !");
        }
    }//GEN-LAST:event_copyNeuronTag_jButtonActionPerformed

    private void updateDisplay_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateDisplay_jButtonActionPerformed
        updateDisplay();
    }//GEN-LAST:event_updateDisplay_jButtonActionPerformed

    private void jumpToNextIncompleted_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jumpToNextIncompleted_jButtonActionPerformed
        jumpToNextIncompleteProcess();
    }//GEN-LAST:event_jumpToNextIncompleted_jButtonActionPerformed
    private void jumpToNextIncompleteProcess() {
        recordTreeExpansionSelectionStatus();
        for (int i = 0; i < rootNeuronNode.getChildCount(); i++) {
            neuronList_jTree.expandPath(new TreePath(((ntNeuronNode) rootNeuronNode.getChildAt(i)).getPath()));
        }
        int selectRow = 0, nextRow = 0;
        int totalRows = neuronList_jTree.getRowCount();
        //IJ.log(totalRows+"");
        int[] selectedRows = neuronList_jTree.getSelectionRows();
        if (selectedRows != null) {
            selectRow = selectedRows[selectedRows.length - 1];
            //IJ.log("selected "+selectRow);
        }

        boolean found = false;
        for (int i = selectRow + 1; i < totalRows; i++) {
            ntNeuronNode node = (ntNeuronNode) neuronList_jTree.getPathForRow(i).getLastPathComponent();
            if (!node.isComplete()) {
                found = true;
                nextRow = i;
                break;
            }
        }
        if (!found) {
            for (int i = 0; i <= selectRow; i++) {
                ntNeuronNode node = (ntNeuronNode) neuronList_jTree.getPathForRow(i).getLastPathComponent();
                if (!node.isComplete()) {
                    found = true;
                    nextRow = i;
                    break;
                }
            }
        }
        ntNeuronNode node = (ntNeuronNode) neuronList_jTree.getPathForRow(nextRow).getLastPathComponent();
        updateTrees();
        restoreTreeExpansionSelectionStatus();
        if (found) {
            //IJ.log("found "+node.toString());
            TreePath selectedPath = new TreePath(node.getPath());
            neuronList_jTree.setSelectionPath(selectedPath);
            neuronList_jTree.scrollPathToVisible(selectedPath);
            if (!tablePoints.isEmpty()) {
                int tableSelectRow = tablePoints.size() - 1;
                pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
                scroll2pointTableVisible(tableSelectRow, 0);
            }
        }
    }

    private void toogleTracingCompleteness_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toogleTracingCompleteness_jButtonActionPerformed
        traceHelper.changeTracingCompleteness();
    }//GEN-LAST:event_toogleTracingCompleteness_jButtonActionPerformed


    private void jumpToNextSelected_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jumpToNextSelected_jButtonActionPerformed
        scrollToNextSelectedProcess();
    }//GEN-LAST:event_jumpToNextSelected_jButtonActionPerformed
    private void scrollToNextSelectedProcess() {
        TreePath[] selectedPaths = neuronList_jTree.getSelectionPaths();
        if (selectedPaths != null) {
            Rectangle visibleRect = neuronList_jScrollPane.getViewport().getViewRect();
            int currentVisible = 0;
            for (int i = 0; i < selectedPaths.length; i++) {
                Rectangle selectionRect = neuronList_jTree.getPathBounds(selectedPaths[i]);
                if (selectionRect.intersects(visibleRect)) {
                    currentVisible = i;
                }
            }
            if (currentVisible == selectedPaths.length - 1) {
                neuronList_jTree.scrollPathToVisible(selectedPaths[0]);
            } else {
                neuronList_jTree.scrollPathToVisible(selectedPaths[currentVisible + 1]);
            }
        }
    }

    private void clearData_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearData_jMenuItemActionPerformed
        dataHelper.clearData();
    }//GEN-LAST:event_clearData_jMenuItemActionPerformed

    private void xyzResolutions_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_xyzResolutions_jMenuItemActionPerformed
        initiateCalibration();
    }//GEN-LAST:event_xyzResolutions_jMenuItemActionPerformed

    private void exportSynapseFromSelectedNeurons_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportSynapseFromSelectedNeurons_jMenuItemActionPerformed
        exportSelectedNeuronSynapse();
    }//GEN-LAST:event_exportSynapseFromSelectedNeurons_jMenuItemActionPerformed
    private void exportSelectedNeuronSynapse() {
        if (imp != null && neuronList_jTree.getSelectionCount() >= 1) {
            String prefixName = imp.getTitle();
            DirectoryChooser dc = new DirectoryChooser("Choose Synapse export folder ...");
            String directory = dc.getDirectory();
            if (directory == null) {
                return;
            }
            if (prefixName.endsWith(".tif")) {
                prefixName = prefixName.substring(0, prefixName.length() - 4);
            } else if (prefixName.endsWith(".tiff")) {
                prefixName = prefixName.substring(0, prefixName.length() - 5);
            }
            GenericDialog gd = new GenericDialog("Export Synapse ...");
            gd.addMessage("x/y/z resolutions (0 = unknown resolution)");
            gd.addNumericField("x: ", xyzResolutions[0], 3, 6, " um / pixel");
            gd.addNumericField("y: ", xyzResolutions[1], 3, 6, " um / pixel");
            gd.addNumericField("z: ", xyzResolutions[2], 3, 6, " um / pixel");
            gd.showDialog();
            if (gd.wasCanceled()) {
                return;
            }
            double[] resolutions = new double[3];
            resolutions[0] = gd.getNextNumber();
            resolutions[1] = gd.getNextNumber();
            resolutions[2] = gd.getNextNumber();
            if ((resolutions[0] + "").equals("NaN") || resolutions[0] < 0
                    || (resolutions[1] + "").equals("NaN") || resolutions[1] < 0
                    || (resolutions[2] + "").equals("NaN") || resolutions[2] < 0) {
                resolutions[0] = 0;
                resolutions[1] = 0;
                resolutions[2] = 0;
            }

            try {
                ArrayList<String> selectedNeuronNumbers = getSelectedNeuronNumberSortSmall2Large();
                IO.exportSelectedNeuronSynapse(rootNeuronNode, rootAllSomaNode,
                        selectedNeuronNumbers, directory, prefixName, resolutions);
                updateInfo("Synapse exported !");
            } catch (IOException e) {
                IJ.error(e.getMessage());
            }
        } else {
            IJ.error("Selected at least one neuron to export Synapse !");
        }
    }

    private void undo_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undo_jMenuItemActionPerformed
        history.backwardHistory();
    }//GEN-LAST:event_undo_jMenuItemActionPerformed

    private void redo_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redo_jMenuItemActionPerformed
        history.forwardHistory();
    }//GEN-LAST:event_redo_jMenuItemActionPerformed

    private void selecAllNeuron_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selecAllNeuron_jMenuItemActionPerformed
        selectAllVisibleNeuronTreeNodes();
    }//GEN-LAST:event_selecAllNeuron_jMenuItemActionPerformed

    private void deselectAllNeuon_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deselectAllNeuon_jMenuItemActionPerformed
        clearAllNeuronTreeSelection();
    }//GEN-LAST:event_deselectAllNeuon_jMenuItemActionPerformed

    private void setScale_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setScale_jMenuItemActionPerformed
        initiateCalibration();
    }//GEN-LAST:event_setScale_jMenuItemActionPerformed

    private void overlaySelectedConnection_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlaySelectedConnection_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlaySelectedConnection_jCheckBoxActionPerformed

    private void overlayAllConnection_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayAllConnection_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlayAllConnection_jCheckBoxActionPerformed

    private void overlaySelectedSpine_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlaySelectedSpine_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlaySelectedSpine_jCheckBoxActionPerformed

    private void overlayAllSpine_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overlayAllSpine_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_overlayAllSpine_jCheckBoxActionPerformed

    private void zProjectionInterval_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zProjectionInterval_jSpinnerStateChanged
        zProjInterval = (Integer) zProjectionInterval_jSpinner.getValue();
        updateZprojectionImp();
    }//GEN-LAST:event_zProjectionInterval_jSpinnerStateChanged

    private void xyProjectionArea_jSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_xyProjectionArea_jSpinnerStateChanged
        zProjXY = Integer.parseInt((String) xyProjectionArea_jSpinner.getValue());
        updateZprojectionImp();
    }//GEN-LAST:event_xyProjectionArea_jSpinnerStateChanged

    private void logSomaStatistics_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logSomaStatistics_jMenuItemActionPerformed
        if (neuronList_jTree.getSelectionCount() == 0) {
            YesNoCancelDialog yncDialog = new YesNoCancelDialog(new java.awt.Frame(),
                    "Log soma statistics ...", "Do you want to log the perimeter and volume of all traced somas ?");
            if (yncDialog.yesPressed()) {
                analysis.logAllSomaStatistics();
            }
        } else {
            ArrayList<String> neuronNumbers = getSelectedNeuronNumberSortSmall2Large();
            for (String neuronNumber : neuronNumbers) {
                ntNeuronNode neuronSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNumber);
                analysis.logOneSomaStatistics(neuronSomaNode);
            }
        }    }//GEN-LAST:event_logSomaStatistics_jMenuItemActionPerformed

    private void toggleSynapse_jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleSynapse_jButton1ActionPerformed
        decrease_synapse_number();
    }//GEN-LAST:event_toggleSynapse_jButton1ActionPerformed

    private void traceSpine_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_traceSpine_jButtonActionPerformed
        traceHelper.traceSpine();
    }//GEN-LAST:event_traceSpine_jButtonActionPerformed

    private void traceSoma_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_traceSoma_jButtonActionPerformed
        traceHelper.traceSoma();
    }//GEN-LAST:event_traceSoma_jButtonActionPerformed

    private void traceNeurite_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_traceNeurite_jButtonActionPerformed
        traceHelper.traceNeurite();
    }//GEN-LAST:event_traceNeurite_jButtonActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        loadRoiList();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void loadRoiList() {
        String color_name = jComboBox1.getSelectedItem().toString();
        Color color = null;

        switch (color_name) {
            case "Blue":
                color = Color.blue;
                break;
            case "Red":
                color = Color.red;
                break;
            case "Green":
                color = Color.green;
                break;
        }

        if (color == null) {
            return;
        }

        OpenDialog od = new OpenDialog("Select Points File", "");
        String name = od.getFileName();
        String dir = od.getDirectory();

        if (name == null || name.length() == 0) {
            return;
        }

        java.util.List<SynapsePoint> list = new ArrayList<>();
        File file = new File(dir + name);
        System.out.println("Loading points from: " + dir + name);
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(file));
            String text;

            while ((text = reader.readLine()) != null) {
                //list.add(Integer.parseInt(text));
                String[] parts = text.split("\t");

                int x = parseIntClean(parts[0]);
                int y = parseIntClean(parts[1]);
                int z = (int) Math.round(Float.valueOf(parts[2]));
                float r = Float.valueOf(parts[3]);

                list.add(new SynapsePoint(x, y, z, r));
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

        if (auxOverlay == null) {
            auxOverlay = new ArrayList<Roi>();
        }

        for (SynapsePoint p : list) {
            float r = p.getR();
            Roi toadd = new OvalRoi(p.getY() - r, p.getX() - r, r * 2, r * 2);
            toadd.setPosition(0, p.getZ(), 0);
            toadd.setStrokeColor(color);
            auxOverlay.add(toadd);
        }

        updateOverlay();
    }

    private int parseIntClean(String x) {
        double temp = Double.parseDouble(x);
        temp = Math.round(temp);

        return (int) temp;
    }

    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jComboBox1ActionPerformed

    private void jButton2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton2MouseClicked
        if( auxOverlay == null ) return;
        
        auxOverlay.clear();
        updateOverlay();
    }//GEN-LAST:event_jButton2MouseClicked

    private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
        logNeuronRGB();
    }//GEN-LAST:event_jMenuItem1ActionPerformed

    private void blank_jCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_blank_jCheckBoxActionPerformed
        updateOverlay();
    }//GEN-LAST:event_blank_jCheckBoxActionPerformed

    private void showSkeletonized_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showSkeletonized_jButtonActionPerformed
        showSkeletonizedChannel();
    }//GEN-LAST:event_showSkeletonized_jButtonActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        showSkeletonizedProjection();
    }//GEN-LAST:event_jButton3ActionPerformed

    private void loadSkeletonized_jMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadSkeletonized_jMenuItemActionPerformed
        loadSkeletonizedFile();
    }//GEN-LAST:event_loadSkeletonized_jMenuItemActionPerformed

    private void skeletonizedShowTracedPoints_jButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_skeletonizedShowTracedPoints_jButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_skeletonizedShowTracedPoints_jButtonActionPerformed

    private void combineSomaAndBranchesOfTwoNeuronTreeSomas(ntNeuronNode targetNeuronSomaNode, ntNeuronNode sourceNeuronSomaNode) {
        String targetSNeuronNumber = targetNeuronSomaNode.getNeuronNumber();
        String sourceNeuronNumber = sourceNeuronSomaNode.getNeuronNumber();
        ntNeuronNode targetSomaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(targetSNeuronNumber);
        ntNeuronNode sourceSomaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(sourceNeuronNumber);

        // combine soma Roi - insert unique Roi of sourceNeuonSomaNode into targetNeuronSomaNode
        addAllSomaSliceFromOneSomaTreeSoma2Another(targetSomaSomaNode, sourceSomaSomaNode);

        // add all child (branch tracings) of sourceNeuronSomaNode to targetNeuronSomaNode
        addAllChildBranchFromOneNeuronTreeSoma2Another(targetNeuronSomaNode, sourceNeuronSomaNode);

        // remove sourceSoma from allSomaNode and sourceNeuronSoma from rootNeuronNode  
        allSomaTreeModel.removeNodeFromParent(sourceSomaSomaNode);
        neuronTreeModel.removeNodeFromParent(sourceNeuronSomaNode);
    }

    private void addAllSomaSliceFromOneSomaTreeSoma2Another(ntNeuronNode targetSomaSomaNode, ntNeuronNode sourceSomaSomaNode) {
        boolean add2LastPosition = true;
        // insert unique Roi in sourceNeuonSomaNode into targetNeuronSomaNode
        String targetSomaNumber = targetSomaSomaNode.getNeuronNumber();
        //IJ.log(targetSomaName);
        for (int s = 0; s < sourceSomaSomaNode.getChildCount(); s++) {
            ntNeuronNode sourceSomaSliceNode = (ntNeuronNode) sourceSomaSomaNode.getChildAt(s);
            String[] sourceSomaSliceNames = sourceSomaSliceNode.toString().split(":");
            int sourceSomaSliceNumber = Integer.parseInt(sourceSomaSliceNames[1]);
            String newSomaSliceNodeName = targetSomaNumber + ":" + sourceSomaSliceNames[1];
            for (int t = 0; t < targetSomaSomaNode.getChildCount(); t++) {
                ntNeuronNode targetSomaSliceNode = (ntNeuronNode) targetSomaSomaNode.getChildAt(t);
                String[] targetSomaSliceNames = targetSomaSliceNode.toString().split(":");
                int targetSomaSliceNumber = Integer.parseInt(targetSomaSliceNames[1]);
                if (sourceSomaSliceNumber <= targetSomaSliceNumber) {
                    add2LastPosition = false;
                    if (sourceSomaSliceNumber == targetSomaSliceNumber) {
                        deleteOneSomaSliceNodeByName(sourceSomaSliceNode.toString());
                        break;
                    } else {
                        traceHelper.renameNodeByNewNodeNameAndSetConnection(sourceSomaSliceNode, newSomaSliceNodeName, 0);
                        ntNeuronNode newSomaSliceNode
                                = new ntNeuronNode(newSomaSliceNodeName, sourceSomaSliceNode.getTracingResult());
                        allSomaTreeModel.insertNodeInto(newSomaSliceNode, targetSomaSomaNode, t);
                        break;
                    }
                }
            }
            if (add2LastPosition) {
                traceHelper.renameNodeByNewNodeNameAndSetConnection(sourceSomaSliceNode, newSomaSliceNodeName, 0);
                ntNeuronNode newSomaSliceNode
                        = new ntNeuronNode(newSomaSliceNodeName, sourceSomaSliceNode.getTracingResult());
                allSomaTreeModel.insertNodeInto(newSomaSliceNode, targetSomaSomaNode, targetSomaSomaNode.getChildCount());
            }
            add2LastPosition = true;
        }
    }

    private void addAllChildBranchFromOneNeuronTreeSoma2Another(ntNeuronNode targetNeuronSomaNode, ntNeuronNode sourceNeuronSomaNode) {
        // insert all child branches in sourceNeuonSomaNode into targetNeuronSomaNode
        for (int s = 0; s < sourceNeuronSomaNode.getChildCount(); s++) {
            ntNeuronNode sourceChildNode = (ntNeuronNode) sourceNeuronSomaNode.getChildAt(s);
            int insertPosition = targetNeuronSomaNode.getChildCount();
            if (sourceChildNode.isPrimaryBranchNode()) {
                insertPosition = traceHelper.getNextPrimaryBranchNodePositionINneuronSomaNode(targetNeuronSomaNode);
            }
            String targetNeuronSomaName = targetNeuronSomaNode.toString();
            if (targetNeuronSomaName.contains("/")) {
                targetNeuronSomaName = targetNeuronSomaName.split("/")[0];
            }
            String insertChildNodeName = targetNeuronSomaName + "-" + (insertPosition + 1);
            traceHelper.renameNodeByNewNodeNameAndSetConnection(sourceChildNode, insertChildNodeName, 0);
            ntNeuronNode insertChildNode = new ntNeuronNode(insertChildNodeName, sourceChildNode.getTracingResult());
            neuronTreeModel.insertNodeInto(insertChildNode, targetNeuronSomaNode, insertPosition);
            addAllChildBranchFromOneNeuronTreeSoma2Another(insertChildNode, sourceChildNode);
        }
    }

    private void export3DskeletonMultiThread(boolean allSkeletonInOneImage, boolean cropFrame) {
        if (neuronList_jTree.getSelectionCount() > 0) {
            int totalSlices = imp.getNSlices();
            int totalChannel = imp.getNChannels();
            String imageShortTitle = imp.getShortTitle() + "3Dskeleton-Neuron";
            ArrayList<String> selectedNeurons = getSelectedNeuronNumberSortSmall2Large();
            recordTreeExpansionSelectionStatus();
            neuronList_jTree.clearSelection();

            // create individual neuron overlay
            ArrayList<Overlay> skeletonOverlays = new ArrayList<>();
            for (String neuronNumber : selectedNeurons) {
                ntNeuronNode selectedNeuronNode = getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber);
                TreePath neuronTreeSelectionPath = new TreePath(selectedNeuronNode.getPath());
                neuronList_jTree.setSelectionPath(neuronTreeSelectionPath);
                skeletonOverlays.add(displayOL.duplicate());
            }

            // create output skeleton image(s)
            if (allSkeletonInOneImage) {
                Overlay allSkeletonOL = new Overlay();
                String allNeuronTitle = "";
                for (int i = 0; i < selectedNeurons.size(); i++) {
                    allNeuronTitle = allNeuronTitle + "-" + selectedNeurons.get(i);
                    Overlay skeletonOL = skeletonOverlays.get(i);
                    for (int n = 0; n < skeletonOL.size(); n++) {
                        allSkeletonOL.add((Roi) skeletonOL.get(n));
                    }
                }
                if (selectedNeurons.size() > 10) {
                    allNeuronTitle = "-multiple(" + selectedNeurons.size() + ")";
                }
                // create new black background image
                ImagePlus skeletonImage = createBackgroundImage(impWidth, impHeight, totalSlices, totalChannel);
                skeletonImage.setTitle(imageShortTitle + allNeuronTitle + ".tif");
                skeletonImage = HyperStackConverter.toHyperStack(skeletonImage, totalChannel, totalSlices, 1);
                skeletonImage.setOverlay(allSkeletonOL);
                skeletonImage.show();
                skeletonImage.flattenStack(); // can NOT be optimized by multithreading!!!!!!
                if (cropFrame) {
                    ArrayList<int[]> allPoints = new ArrayList<int[]>();
                    for (String neuronName : selectedNeurons) {
                        addAllPointsOfOneNeuron(allPoints, neuronName);
                    }
                    int[] bounds = getNeuronTracingBound(allPoints, impWidth, impHeight);
                    ImageStack skeletonStack = skeletonImage.getImageStack().crop(
                            bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
                    skeletonImage.setStack(skeletonStack);
                    skeletonImage.updateAndDraw();
                }
                Calibration cal = skeletonImage.getCalibration();
                cal.pixelWidth = xyzResolutions[0];
                cal.pixelHeight = xyzResolutions[1];
                cal.pixelDepth = xyzResolutions[2];
            } else { // each skeleton in seperate image
                for (int i = 0; i < selectedNeurons.size(); i++) {
                    ImagePlus skeletonImage = createBackgroundImage(impWidth, impHeight, totalSlices, totalChannel);
                    skeletonImage.setTitle(imageShortTitle + "-" + selectedNeurons.get(i) + ".tif");
                    skeletonImage = HyperStackConverter.toHyperStack(skeletonImage, totalChannel, totalSlices, 1);
                    skeletonImage.setOverlay(skeletonOverlays.get(i));
                    skeletonImage.show();
                    skeletonImage.flattenStack();
                    if (cropFrame) {
                        ArrayList<int[]> allPoints = new ArrayList<int[]>();
                        addAllPointsOfOneNeuron(allPoints, selectedNeurons.get(i));
                        int[] bounds = getNeuronTracingBound(allPoints, impWidth, impHeight);
                        ImageStack skeletonStack = skeletonImage.getImageStack().crop(
                                bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
                        skeletonImage.setStack(skeletonStack);
                        Calibration cal = skeletonImage.getCalibration();
                        cal.pixelWidth = xyzResolutions[0];
                        cal.pixelHeight = xyzResolutions[1];
                        cal.pixelDepth = xyzResolutions[2];
                        skeletonImage.updateAndDraw();
                    }
                    Calibration cal = skeletonImage.getCalibration();
                    cal.pixelWidth = xyzResolutions[0];
                    cal.pixelHeight = xyzResolutions[1];
                    cal.pixelDepth = xyzResolutions[2];
                }
            }

            restoreTreeExpansionSelectionStatus();
        } else {
            IJ.error("Select at least one neuron !");
        }
    }

    private ImagePlus createBackgroundImage(int width, int height,
            int totalSlices, int totalChannel) {
        byte[] pixels = new byte[width * height];
        for (int i = 0; i < width * height; i++) {
            pixels[i] = 0;
        }
        ImageStack backgroundStack = new ImageStack(width, height);
        for (int i = 0; i < totalSlices * totalChannel; i++) {
            ByteProcessor processor = new ByteProcessor(width, height, pixels);
            backgroundStack.addSlice(processor);
        }
        return new ImagePlus("", backgroundStack);
    }

    private void addAllPointsOfOneNeuron(ArrayList<int[]> allPoints, String neuronNumber) {
        // add soma points
        ntNeuronNode somaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNumber);
        for (int i = 0; i < somaSomaNode.getChildCount(); i++) {
            ntNeuronNode somaSliceNode = (ntNeuronNode) somaSomaNode.getChildAt(i);
            ArrayList<String[]> tracingResult = somaSliceNode.getTracingResult();
            for (String[] stringPt : tracingResult) {
                int[] intPt = {Integer.parseInt(stringPt[1]), Integer.parseInt(stringPt[2]), Integer.parseInt(stringPt[3])};
                allPoints.add(intPt);
            }
        }

        // add neurite points
        ntNeuronNode neuronSomaNode = getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber);
        for (int i = 0; i < neuronSomaNode.getChildCount(); i++) {
            ntNeuronNode primaryBranchNode = (ntNeuronNode) neuronSomaNode.getChildAt(i);
            addAllPointsOfOneBranchAndChildNode(allPoints, primaryBranchNode);
        }
    }

    private void addAllPointsOfOneBranchAndChildNode(ArrayList<int[]> allPoints, ntNeuronNode branchNode) {
        ArrayList<String[]> tracingResult = branchNode.getTracingResult();
        for (String[] stringPt : tracingResult) {
            int[] intPt = {Integer.parseInt(stringPt[1]), Integer.parseInt(stringPt[2]), Integer.parseInt(stringPt[3])};
            allPoints.add(intPt);
        }
        for (int i = 0; i < branchNode.getChildCount(); i++) {
            ntNeuronNode childNode = (ntNeuronNode) branchNode.getChildAt(i);
            addAllPointsOfOneBranchAndChildNode(allPoints, childNode);
        }
    }

    private int[] getNeuronTracingBound(ArrayList<int[]> allPoints, int imgWidth, int imgHeight) {
        int xMin = 1000000000, yMin = 1000000000, zMin = 1000000000;
        int xMax = 0, yMax = 0, zMax = 0;

        for (int[] point : allPoints) {
            if (point[0] < xMin) {
                xMin = point[0];
            }
            if (point[1] < yMin) {
                yMin = point[1];
            }
            if (point[2] < zMin) {
                zMin = point[2];
            }
            if (point[0] > xMax) {
                xMax = point[0];
            }
            if (point[1] > yMax) {
                yMax = point[1];
            }
            if (point[2] > zMax) {
                zMax = point[2];
            }
        }
        zMin = (zMin - 1) < 0 ? 0 : zMin - 1;
        xMin = (xMin - 10) < 0 ? 0 : xMin - 10;
        yMin = (yMin - 10) < 0 ? 0 : yMin - 10;
        int width = (xMax + 11) > imgWidth ? imgWidth - xMin : xMax + 10 - xMin;
        int height = (yMax + 11) > imgHeight ? imgHeight - yMin : yMax + 10 - yMin;
        int depth = zMax - zMin;
        int[] bounds = {xMin, yMin, zMin, width, height, depth};
        return bounds;
    }

    /**
     * toggles the channel so that the ch-1 channel position in activeChannels is set to true, while the
     * others are set to false. It then updates the window and display.
     * @param Ch 
     */
    
    protected void toggleChannel(int Ch) {
        imp.setC(Ch);
        IJ.run(imp, toggleColor, "");
        
        javax.swing.JCheckBox[] toggleCh_jCheckboxes = {toggleCh1_jCheckBox, toggleCh2_jCheckBox, toggleCh3_jCheckBox,
            toggleCh4_jCheckBox, toggleCh5_jCheckBox, toggleCh6_jCheckBox, toggleCh7_jCheckBox, toggleCh8_jCheckBox};
        
        for (int i = 0; i < 8; ++i) {
            if (toggleCh_jCheckboxes[i].isSelected()) {
                activeChannels[i] = false;
            }
        }
        
        activeChannels[Ch - 1] = true;
        imp.updateAndRepaintWindow();
        updateDisplay();
    }
    
    private void loadSkeletonizedFile() {
        ImagePlus newImp = IJ.openImage();
        if (newImp == null || newImp.getNChannels() != 1) {
            IJ.error("Invalid skeletonized image");
        }
        ImagePlus[] splits = ChannelSplitter.split(imp);
        ImagePlus[] newChannels;
        if (impSkeletonizedSource == null) {
            newChannels = new ImagePlus[splits.length + 1];
            for (int i = 0; i < splits.length; ++i) newChannels[i] = splits[i];
        } else {
            newChannels = new ImagePlus[splits.length];
            for (int i = 0; i < splits.length - 1; ++i) newChannels[i] = splits[i];
        }
        impSkeletonizedSource = newImp;
        newChannels[newChannels.length - 1] = impSkeletonizedSource;
        ImagePlus mergedImp = RGBStackMerge.mergeChannels(newChannels, true);
        imp.setImage(mergedImp);
        initImageWithSkeletonized();
    }
    
    private void toggleSkeletonizedChannel() {
        if (impSkeletonizedSource == null) return;
        
        int numChannels = imp.getNChannels();
        boolean[] chActive = ((CompositeImage) imp).getActiveChannels();
        
        String activeChannels = "";
        for (int i = 0; i < numChannels - 1; ++i) {
            if (chActive[0]) {
                activeChannels += "0";
            } else {
                activeChannels += "1";
            }
        }
        activeChannels += "1";
        
        imp.setActiveChannels(activeChannels);
    }
    
    private void showSkeletonizedChannel() {
        if (impSkeletonizedSource == null) return;
        
        impSkeletonized = new ImagePlus("", impSkeletonizedSource.getImageStack());
        impSkeletonized.show();
    }
    
    private void updateSkeletonizedProjection() {
        if (impSkeletonizedSource == null || impSkeletonizedProjection == null) return;
        
        int currentZ = imp.getZ();
        int minZ = Math.max(currentZ - zProjInterval, 0);
        int maxZ = Math.min(currentZ + zProjInterval, imp.getNSlices());
;
        ImagePlus temp = DuplicateProjector.duplicateAndProject(impSkeletonizedSource, impSkeletonizedProjection, 1, 1, minZ, maxZ, imp.getRoi());
        impSkeletonizedProjection.setImage(temp);
        updateSkeletonizedTracing();
        impSkeletonizedProjection.updateAndDraw();
    }
    
    private void showSkeletonizedProjection() {
        if (impSkeletonizedSource == null) return;

        int currentZ = imp.getZ();
        int minZ = Math.max(currentZ - zProjInterval, 0);
        int maxZ = Math.min(currentZ + zProjInterval, imp.getNSlices());
        
        impSkeletonizedProjection = new ImagePlus();
        ImagePlus temp = DuplicateProjector.duplicateAndProject(impSkeletonizedSource, impSkeletonizedProjection, 1, 1, minZ, maxZ, imp.getRoi());
        impSkeletonizedProjection.setImage(temp);
        impSkeletonizedProjection.show();
        updateSkeletonizedTracing();
    }

    // <editor-fold defaultstate="collapsed" desc="ImageWindow, Mouse, Keyboard listeners">
    /**
     * methods implement WindowListener -- deactivate ImagePlus closing by mouse
     * click i.e. must use GUI menu to close loaded image
     *
     * @param windowevent
     */
    @Override
    public void windowActivated(WindowEvent windowevent) {
    }

    /**
     * implement window listener. 
     * Override but no implementation as we do not close any image window here, as it will cause imageJ to crash
     * @param windowevent 
     */
    
    @Override
    public void windowClosed(WindowEvent windowevent) {
        // do NOT close any image window here! Will cause ImageJ crushes!
    }

    /**
     * implement window listener.
     * Gives an error message that says "Close image through menu option!"
     * @param windowevent 
     */
    
    @Override
    public void windowClosing(WindowEvent windowevent) {
        IJ.error("Close image through menu option!");
    }

    /**
     * implement window listener when window is deactivated. 
     * Override but no implementation
     * @param windowevent 
     */
    @Override
    public void windowDeactivated(WindowEvent windowevent) {
    }
    
    /**
     * implement window listener when window is deiconified. 
     * Override but no implementation
     * @param windowevent 
     */
    
    @Override
    public void windowDeiconified(WindowEvent windowevent) {
    }

    /**
     * implement window listener when window is iconified. 
     * Override but no implementation
     * @param windowevent 
     */
    
    @Override
    public void windowIconified(WindowEvent windowevent) {
    }

    /**
     * implement window listener when window is opened.
     * Override but no implementation
     * @param windowevent 
     */
    
    @Override
    public void windowOpened(WindowEvent windowevent) {
    }

    /**
     * methods implement MouseMotionListener - respond to mouse event when mouse is exited
     * override function, but with no implementation given
     * @param e
     */
    @Override
    public void mouseExited(MouseEvent e) {
    }
    
    /**
     * methods implement MouseMotionListener - respond to mouse event when mouse is pressed
     * override function, but with no implementation given
     * @param e 
     */
    
    @Override
    public void mousePressed(MouseEvent e) {
    }

    /**
     * methods implement MouseMotionListener - respond to mouse event when mouse is clicked
     * @param e 
     */
    
    @Override
    public void mouseClicked(MouseEvent e) {
        // first determine click number without repeated executing lower order click actions
        boolean singleClicked = false;
        boolean doubleClicked = false;
        boolean tripleClicked = false;
        if (e.getClickCount() == 3) {
            singleClicked = false;
            doubleClicked = false;
            tripleClicked = true;
        } else if (e.getClickCount() == 2) {
            singleClicked = false;
            doubleClicked = true;
            tripleClicked = false;
        } else if (e.getClickCount() == 1) {
            singleClicked = true;
            doubleClicked = false;
            tripleClicked = false;
        }

        if (e.getSource().equals(neuronList_jTree)) {
            if (doubleClicked) {
                setupNeuronTreeSelection(e);
            }
        }
        if (e.getSource().equals(displaySomaList_jTree)) {
            if (doubleClicked) {
                setupSomaTreeSelection(e);
            }
        }
        if (e.getSource().equals(cns)) {
            if (Toolbar.getToolName().equals("zoom")) {
                if (e.getButton() == LEFT_BUTTON) {
                    cnsZproj.zoomIn(0, 0);
                    updateZprojectionImp();
                }
                if (e.getButton() == RIGHT_BUTTON) {
                    cnsZproj.zoomOut(0, 0);
                    updateZprojectionImp();
                }
            } else if (Toolbar.getToolName().equals(ntToolTrace.toolName) // trace tool
                    || Toolbar.getToolName().equals("freeline")) {
                if (doubleClicked) {
                    clearStartEndPts();
                    imp.killRoi();
                }

                if (e.getButton() == LEFT_BUTTON) {
                    // no modifier
                    if (!e.isControlDown() && !e.isShiftDown() && !e.isAltDown()) {
                        if (tripleClicked) {
                            // Left_Triple-click
                            setSingleTracingNodeInTreeToEndPointFromScreen(e);
                        } else if (doubleClicked) {
                            // Left_Double-click
                            // On trace or soma name: 
                            // Set single selection of a branch or a soma slice in Result Trees; 
                            // Select clicked point in tracing table
                            setSingleTracingNodeInTreeToClickPointFromScreen(e);
                        }
                    } // Ctrl down
                    else if (e.isControlDown() && !e.isShiftDown() && !e.isAltDown()) {
                        if (doubleClicked) {
                            // On trace or soma name: 
                            // add/remove single selection of a branch or a soma in neuronTree
                            toggleTracingNodeInTreeFromScreen(e);
                        } else if (singleClicked) {
                            // Left_Ctrl_Single-click
                            // Get next tracing Point within color and intensity threshold
                            setStartEndPoint3DIntColor(e);
                        }
                    } // Shift down
                    else if (!e.isControlDown() && e.isShiftDown() && !e.isAltDown()) {
                        // Left_Shift_Single-click
                        // On trace or Soma name: Select single branch or soma slice as Edit Target;
                        // On everywhere else, clear Edit Target
                        if (singleClicked) {
                            getEditTargetTraceFromScreen(e);
                        }
                    } // Alt down
                    else if (!e.isControlDown() && !e.isShiftDown() && e.isAltDown()) {
                        if (singleClicked) {
                            // Left_Alt_Single-click
                            // Get next tracing Point ignoring color and intensity threshold
                            setStartEndPoint2DInt(e);
                        }
                    } else if (!e.isControlDown() && e.isShiftDown() && e.isAltDown()) {
                        // Left_Shift-Alt_Single-click
                        // On trace or Soma name: Select single branch or soma slice as Edit Target; Select single point
                        // On everywhere else, clear Edit Target
                        if (singleClicked) {
                            getEditTargetPointFromScreen(e);
                        }
                    }

                } else if (e.getButton() == RIGHT_BUTTON) {
                    // no modifier
                    if (!e.isControlDown() && !e.isShiftDown() && !e.isAltDown()) {
                        // Right_Crtl-Triple-click
                        if (tripleClicked) {
                            // Clear all selections --Prepare for creating a new neuron
                            clearAllNeuronTreeSelection();
                        }
                        if (doubleClicked) {
                            // Right_Double-click
                            // Set all selections in neuronTree to their corresponding neuron roots (neuronSomaNode)
                            // -- Prepare for adding a new primary branch or soma slice
                            setAllToNeuronSomaNodeInTree(e);
                        }
                    }
                }
            }
        }
    }

    /**
     * methods implement MouseMotionListener - respond to mouse event when mouse is dragged
     * it calls the updateZprojectionImp() function
     * @param me
     */
    @Override
    public void mouseDragged(MouseEvent me) {
        updateZprojectionImp();
    }

    /**
     * methods implement MouseMotionListener - respond to mouse event when mouse is moved
     * @param me 
     */
    
    @Override
    public void mouseMoved(MouseEvent me) {
        if (me.getSource() == cns) {
            Point pt = cns.getCursorLoc();
            if (pt == null) {
                return;
            }
            double offsetX = roiXmin + 0.5;
            double offsetY = roiYmin + 0.5;
            //IJ.log("pt.xOut = "+pt.xOut+", cns.offScreenX(0) = "+cns.offScreenX(0)+", cnsZproj.offScreenX(0) = "+cnsZproj.offScreenX(0)+", roiXmin = "+roiXmin);
            double mag = cns.getMagnification();
            double xpSZ = pt.x - offsetX + 8 / mag;
            double xmSZ = pt.x - offsetX - 8 / mag;
            double ypSZ = pt.y - offsetY + 8 / mag;
            double ymSZ = pt.y - offsetY - 8 / mag;
            double xp2 = pt.x - offsetX + 2 / mag;
            double xm2 = pt.x - offsetX - 2 / mag;
            double yp2 = pt.y - offsetY + 2 / mag;
            double ym2 = pt.y - offsetY - 2 / mag;
            GeneralPath path = new GeneralPath();
            path.moveTo(xmSZ, ymSZ);
            path.lineTo(xm2, ym2);
            path.moveTo(xpSZ, ypSZ);
            path.lineTo(xp2, yp2);
            path.moveTo(xpSZ, ymSZ);
            path.lineTo(xp2, ym2);
            path.moveTo(xmSZ, ypSZ);
            path.lineTo(xm2, yp2);
            ShapeRoi cursorRoi = new ShapeRoi(path);

            Overlay cursorOL = new Overlay();
            cursorOL.add(cursorRoi);
            cursorRoi.setStrokeColor(Color.white);
            cursorRoi.setStrokeWidth(3);
            cursorRoi.setNonScalable(true);
            cursorRoi.setIsCursor(true);
            if (impZproj != null) {
                impZproj.setOverlay(cursorOL);
            }
            if (impSkeletonized != null) {
                impSkeletonized.setOverlay(cursorOL);
            }
        }
    }

    private void setupSomaTreeSelection(MouseEvent e) {
        int row = displaySomaList_jTree.getRowForLocation(e.getX(), e.getY());
        if (row == -1) //When user clicks on the "empty surface"  
        {
            if (displaySomaList_jTree.getSelectionCount() > 0) {
                displaySomaList_jTree.clearSelection();
                history.saveHistory();
            }
        } else {
            setDefaultDisplaySomaTreeSelection();
            history.saveHistory();
        }
    }

    private void setDefaultDisplaySomaTreeSelection() {
        if (!tablePoints.isEmpty()) {
            int tableSelectRow = tablePoints.size() - 1;
            pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
            scroll2pointTableVisible(tableSelectRow, 0);
        }
        clearStartEndPts();
    }

    private void displayConnectedNode(String selectedNodeName, String connectedSynapseName) {
        if (connectedSynapseName.contains("#")) {
            String[] names = connectedSynapseName.split("#");
            String targetNodeName = names[1];
            String targetSynapseName = names[2] + "#" + selectedNodeName + "#" + names[0];
            // setup neuronTree and displaySomaTree selection
            ntNeuronNode neuronTreeSelectedNode;
            ntNeuronNode targetNode;
            if (targetNodeName.contains("-")) { // is a branch node
                neuronTreeSelectedNode = getNodeFromNeuronTreeByNodeName(targetNodeName);
                TreePath neuronTreeSelectionPath = new TreePath(neuronTreeSelectedNode.getPath());
                neuronList_jTree.setSelectionPath(neuronTreeSelectionPath);
                neuronList_jTree.scrollPathToVisible(neuronTreeSelectionPath);
                targetNode = neuronTreeSelectedNode;
            } else { // is a somaSlice node
                String[] targetNodeNames = targetNodeName.split(":");
                neuronTreeSelectedNode = getSomaNodeFromNeuronTreeByNeuronNumber(targetNodeNames[0]);
                TreePath neuronTreeSelectionPath = new TreePath(neuronTreeSelectedNode.getPath());
                neuronList_jTree.setSelectionPath(neuronTreeSelectionPath);
                neuronList_jTree.scrollPathToVisible(neuronTreeSelectionPath);
                for (int i = 0; i < rootDisplaySomaNode.getChildCount(); i++) {
                    ntNeuronNode somaSliceNode = (ntNeuronNode) rootDisplaySomaNode.getChildAt(i);
                    if (targetNodeName.equals(somaSliceNode.toString())) {
                        displaySomaList_jTree.setSelectionRow(i);
                        displaySomaList_jTree.scrollRowToVisible(i);
                        break;
                    }
                }
                targetNode = getSomaSliceNodeFromAllSomaTreeBySomaSliceName(targetNodeName);
            }
            // setup pointTable selection
            int tableSelectRow = getPositionInTracingResultBySynapseName(targetNode.getTracingResult(), targetSynapseName);
            pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
            scroll2pointTableVisible(tableSelectRow, 6);
        }
    }

    /**
     * It calls scrollRectToVisible for pointTable_jTable and pointTable_jTable.getParent().
     * It also sets the location of pointTable_jTable.getCellRect based on the parameters
     * @param vRowIndex
     * @param vColIndex 
     */
    
    protected void scroll2pointTableVisible(int vRowIndex, int vColIndex) {
        if (!(pointTable_jTable.getParent() instanceof JViewport)) {
            return;
        }
        JViewport viewport = (JViewport) pointTable_jTable.getParent();

        // This rectangle is relative to the table where the
        // northwest corner of cell (0,0) is always (0,0).
        Rectangle rect = pointTable_jTable.getCellRect(vRowIndex, vColIndex, true);

        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // Translate the cell location so that it is relative
        // to the view, assuming the northwest corner of the
        // view is (0,0)
        rect.setLocation(rect.x - pt.x, rect.y - pt.y);

        //Scroll the area into view
        viewport.scrollRectToVisible(rect);

        pointTable_jTable.scrollRectToVisible(new Rectangle(pointTable_jTable.getCellRect(vRowIndex, vColIndex, true)));
    }

    private void setupNeuronTreeSelection(MouseEvent e) {
        int row = neuronList_jTree.getRowForLocation(e.getX(), e.getY());
        if (row == -1) //When user clicks on the "empty surface"  
        {
            if (neuronList_jTree.getSelectionCount() > 0) {
                neuronList_jTree.clearSelection();
                history.saveHistory();
            }
        } else {
            setDefaultNeuronTreeSelection();
            history.saveHistory();
        }
    }

    private void setDefaultNeuronTreeSelection() {
        ntNeuronNode showNode = (ntNeuronNode) (neuronList_jTree.getLastSelectedPathComponent());
        if (showNode.isTrunckNode()) { // if somaNode is selected
            showNode = getSomaNodeFromAllSomaTreeByNeuronNumber(showNode.getNeuronNumber());
            if (showNode.getChildCount() > 0) {
                showNode = (ntNeuronNode) showNode.getChildAt(0);
                displaySomaList_jTree.setSelectionInterval(0, 0);
            }
        }
        if (!tablePoints.isEmpty()) {
            int tableSelectRow = tablePoints.size() - 1;
            if (((ntNeuronNode) (showNode.getParent())).equals(rootNeuronNode)) {
                tableSelectRow = 0;
            }
            pointTable_jTable.setRowSelectionInterval(tableSelectRow, tableSelectRow);
            scroll2pointTableVisible(tableSelectRow, 0);
        }
        clearStartEndPts();
    }

    private void setStartEndPoint3DIntColor(MouseEvent e) {
        updatePositionInfo(e);
        //IJ.log("("+crossX+", "+crossY+", "+crossZ+")");
        int[] mousePoint = {0, crossX, crossY, crossZ, 0, 0, 0};
        float[] baseIntColor = Functions.getWinMeanIntColor(mousePoint, analysisChannels, imp.getFrame(), 1, 1);
        if (!(e.isControlDown() && e.isAltDown())) {
            int[] newPoint = Functions.meanShift2CentOfInt(mousePoint, imp.getFrame(),
                    xyRadius, zRadius, 1);
            //int[] maskPt1 = {-10, -10, -10};
            //int[] maskPt2 = {-10, -10, -10};
            baseIntColor = Functions.getWinMeanIntColor(newPoint, analysisChannels, imp.getFrame(), 1, 1);
            int maxItreation = 5;
            newPoint = Functions.meanShift2ColoThreshCentOfInt(imp, mousePoint, analysisChannels,
                    imp.getFrame(), baseIntColor, colorThreshold,
                    xyRadius, zRadius, maskRadius, maxItreation);
            crossX = newPoint[1];
            crossY = newPoint[2];
            crossZ = newPoint[3];
            //IJ.log("("+crossX+", "+crossY+", "+crossZ+")");
        }
        if (manualTracing_jRadioButton.isSelected() || semiAutoTracing_jRadioButton.isSelected()) {
            if (!hasStartPt) {
                startPoint = new int[7];
                startPoint[0] = 0;
                startPoint[1] = crossX;
                startPoint[2] = crossY;
                startPoint[3] = crossZ;
                startPoint[4] = 0;
                startPoint[5] = 0;
                startPoint[6] = 0;
                startPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
                String[] startInfo = getPointIntColorInfo(startPoint, analysisChannels);
                startIntensity_jLabel.setText(startInfo[0]);
                startColor_jLabel.setText(startInfo[1]);
                hasStartPt = true;
                endPoint = new int[7];
                endPosition_jLabel.setText("");
                endIntensity_jLabel.setText("");
                endColor_jLabel.setText("");
                hasEndPt = false;
                updateInfo(defaultInfo);
            } else {
                float[] srtIntColor = Functions.getWinMeanIntColor(startPoint, analysisChannels, imp.getFrame(), 1, 1);
                float corDis2 = Functions.getColorDistanceSquare(baseIntColor, srtIntColor);
                if (corDis2 <= colorThreshold * colorThreshold) {
                    endPoint = new int[7];
                    endPoint[0] = 0;
                    endPoint[1] = crossX;
                    endPoint[2] = crossY;
                    endPoint[3] = crossZ;
                    endPoint[4] = 0;
                    endPoint[5] = 0;
                    endPoint[6] = 0;
                    endPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
                    String[] endInfo = getPointIntColorInfo(endPoint, analysisChannels);
                    endIntensity_jLabel.setText(endInfo[0]);
                    endColor_jLabel.setText(endInfo[1]);
                    hasEndPt = true;
                } else {
                    endPoint = new int[7];
                    endPosition_jLabel.setText("");
                    endIntensity_jLabel.setText("");
                    endColor_jLabel.setText("");
                    hasEndPt = false;
                }
                updateInfo("Color distance is " + Math.pow(corDis2, 0.5));
            }
        } else if (autoTracing_jRadioButton.isSelected()) {
            startPoint = new int[7];
            startPoint[0] = 0;
            startPoint[1] = crossX;
            startPoint[2] = crossY;
            startPoint[3] = crossZ;
            startPoint[4] = 0;
            startPoint[5] = 0;
            startPoint[6] = 0;
            startPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
            String[] startInfo = getPointIntColorInfo(startPoint, analysisChannels);
            startIntensity_jLabel.setText(startInfo[0]);
            startColor_jLabel.setText(startInfo[1]);
            hasStartPt = true;
            endPoint = new int[7];
            endPosition_jLabel.setText("");
            endIntensity_jLabel.setText("");
            endColor_jLabel.setText("");
            hasEndPt = false;
            updateInfo(defaultInfo);
        }

        //updatePositionIntColor();                    
        updatePointBox();
        imp.setZ(crossZ);
        imp.killRoi();
    }

    private void setStartEndPoint2DIntColor(MouseEvent e) {
        updatePositionInfo(e);
        //IJ.log("("+crossX+", "+crossY+", "+crossZ+")");
        int[] mousePoint = {0, crossX, crossY, crossZ, 0, 0, 0};
        int[] newPoint = Functions.meanShift2CentOfInt(mousePoint, imp.getFrame(),
                xyRadius, 0, 1);
        //int[] maskPt1 = {-10, -10, -10};
        //int[] maskPt2 = {-10, -10, -10};
        float[] baseIntColor = Functions.getWinMeanIntColor(newPoint, analysisChannels, imp.getFrame(), 1, 1);
        int maxItreation = 5;
        newPoint = Functions.meanShift2ColoThreshCentOfInt(imp, mousePoint, analysisChannels,
                imp.getFrame(), baseIntColor, colorThreshold,
                xyRadius, 0, maskRadius, maxItreation);
        crossX = newPoint[1];
        crossY = newPoint[2];
        crossZ = newPoint[3];
        //IJ.log("("+crossX+", "+crossY+", "+crossZ+")");

        if (manualTracing_jRadioButton.isSelected()) {
            if (!hasStartPt) {
                startPoint = new int[7];
                startPoint[0] = 0;
                startPoint[1] = crossX;
                startPoint[2] = crossY;
                startPoint[3] = crossZ;
                startPoint[4] = 0;
                startPoint[5] = 0;
                startPoint[6] = 0;
                startPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
                String[] startInfo = getPointIntColorInfo(startPoint, analysisChannels);
                startIntensity_jLabel.setText(startInfo[0]);
                startColor_jLabel.setText(startInfo[1]);
                hasStartPt = true;
                endPoint = new int[7];
                endPosition_jLabel.setText("");
                endIntensity_jLabel.setText("");
                endColor_jLabel.setText("");
                hasEndPt = false;
                updateInfo(defaultInfo);
            } else {
                float[] srtIntColor = Functions.getWinMeanIntColor(startPoint, analysisChannels, imp.getFrame(), 1, 1);
                float corDis2 = Functions.getColorDistanceSquare(baseIntColor, srtIntColor);
                if (corDis2 <= colorThreshold * colorThreshold) {
                    endPoint = new int[7];
                    endPoint[0] = 0;
                    endPoint[1] = crossX;
                    endPoint[2] = crossY;
                    endPoint[3] = crossZ;
                    endPoint[4] = 0;
                    endPoint[5] = 0;
                    endPoint[6] = 0;
                    endPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
                    String[] endInfo = getPointIntColorInfo(endPoint, analysisChannels);
                    endIntensity_jLabel.setText(endInfo[0]);
                    endColor_jLabel.setText(endInfo[1]);
                    hasEndPt = true;
                } else {
                    endPoint = new int[7];
                    endPosition_jLabel.setText("");
                    endIntensity_jLabel.setText("");
                    endColor_jLabel.setText("");
                    hasEndPt = false;
                }
                updateInfo("Color distance is " + Math.pow(corDis2, 0.5));
            }
        } else if (semiAutoTracing_jRadioButton.isSelected()) {
            startPoint = new int[7];
            startPoint[0] = 0;
            startPoint[1] = crossX;
            startPoint[2] = crossY;
            startPoint[3] = crossZ;
            startPoint[4] = 0;
            startPoint[5] = 0;
            startPoint[6] = 0;
            startPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
            String[] startInfo = getPointIntColorInfo(startPoint, analysisChannels);
            startIntensity_jLabel.setText(startInfo[0]);
            startColor_jLabel.setText(startInfo[1]);
            hasStartPt = true;
            endPoint = new int[7];
            endPosition_jLabel.setText("");
            endIntensity_jLabel.setText("");
            endColor_jLabel.setText("");
            hasEndPt = false;
            updateInfo(defaultInfo);
        }

        //updatePositionIntColor();                    
        updatePointBox();
        imp.setZ(crossZ);
        imp.killRoi();
    }

    private void setStartEndPoint2DInt(MouseEvent e) {
        updatePositionInfo(e);
        //IJ.log("("+crossX+", "+crossY+", "+crossZ+")");
        int[] mousePoint = {0, crossX, crossY, crossZ, 0, 0, 0};
        int[] newPoint = Functions.meanShift2CentOfInt(mousePoint, imp.getFrame(),
                xyRadius, 0, 1);
        //int[] maskPt1 = {-10, -10, -10};
        //int[] maskPt2 = {-10, -10, -10};
        float[] baseIntColor = Functions.getWinMeanIntColor(newPoint, analysisChannels, imp.getFrame(), 1, 1);
        int maxItreation = 5;
        newPoint = Functions.meanShift2ColoThreshCentOfInt(imp, mousePoint, analysisChannels,
                imp.getFrame(), baseIntColor, colorThreshold,
                xyRadius, 0, maskRadius, maxItreation);
        crossX = newPoint[1];
        crossY = newPoint[2];
        crossZ = newPoint[3];
        //IJ.log("("+crossX+", "+crossY+", "+crossZ+")");

        if (manualTracing_jRadioButton.isSelected() || semiAutoTracing_jRadioButton.isSelected()) {
            if (!hasStartPt) {
                startPoint = new int[7];
                startPoint[0] = 0;
                startPoint[1] = crossX;
                startPoint[2] = crossY;
                startPoint[3] = crossZ;
                startPoint[4] = 0;
                startPoint[5] = 0;
                startPoint[6] = 0;
                startPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
                String[] startInfo = getPointIntColorInfo(startPoint, analysisChannels);
                startIntensity_jLabel.setText(startInfo[0]);
                startColor_jLabel.setText(startInfo[1]);
                hasStartPt = true;
                endPoint = new int[7];
                endPosition_jLabel.setText("");
                endIntensity_jLabel.setText("");
                endColor_jLabel.setText("");
                hasEndPt = false;
                updateInfo(defaultInfo);
            } else {
                float[] srtIntColor = Functions.getWinMeanIntColor(startPoint, analysisChannels, imp.getFrame(), 1, 1);
                float corDis2 = Functions.getColorDistanceSquare(baseIntColor, srtIntColor);
                endPoint = new int[7];
                endPoint[0] = 0;
                endPoint[1] = crossX;
                endPoint[2] = crossY;
                endPoint[3] = crossZ;
                endPoint[4] = 0;
                endPoint[5] = 0;
                endPoint[6] = 0;
                endPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
                String[] endInfo = getPointIntColorInfo(endPoint, analysisChannels);
                endIntensity_jLabel.setText(endInfo[0]);
                endColor_jLabel.setText(endInfo[1]);
                hasEndPt = true;
                updateInfo("Color distance is " + Math.pow(corDis2, 0.5));
            }
        } else if (semiAutoTracing_jRadioButton.isSelected()) {
            startPoint = new int[7];
            startPoint[0] = 0;
            startPoint[1] = crossX;
            startPoint[2] = crossY;
            startPoint[3] = crossZ;
            startPoint[4] = 0;
            startPoint[5] = 0;
            startPoint[6] = 0;
            startPosition_jLabel.setText("(" + crossX + ", " + crossY + ", " + crossZ + ")");
            String[] startInfo = getPointIntColorInfo(startPoint, analysisChannels);
            startIntensity_jLabel.setText(startInfo[0]);
            startColor_jLabel.setText(startInfo[1]);
            hasStartPt = true;
            endPoint = new int[7];
            endPosition_jLabel.setText("");
            endIntensity_jLabel.setText("");
            endColor_jLabel.setText("");
            hasEndPt = false;
            updateInfo(defaultInfo);
        }

        //updatePositionIntColor();                    
        updatePointBox();
        imp.setZ(crossZ);
        imp.killRoi();
    }

    private void getEditTargetTraceFromScreen(MouseEvent e) {
        updatePositionInfo(e);
        ArrayList<String> containedRoiNames = getContainedRoiNamesOnScreen(crossX, crossY, crossZ, roiSearchRange);
        editTargetNodeName = getNearestRoiName(crossX, crossY, crossZ, containedRoiNames);
        if (editTargetNodeName.equals("0")) { // return if still not found
            imp.killRoi();
            updateInfo("Edit target NOT found!");
        } else {
            TextRoi targetNameRoi = new TextRoi(crossX, crossY, editTargetNodeName);
            targetNameRoi.setPosition(0, crossZ, imp.getFrame());
            imp.setRoi(targetNameRoi);
            updateInfo(defaultInfo);
        }
        editTargetName_jLabel.setText(editTargetNodeName);
    }

    private void getEditTargetPointFromScreen(MouseEvent e) {
        updatePositionInfo(e);
        ArrayList<String> containedRoiNames = getContainedRoiNamesOnScreen(crossX, crossY, crossZ, roiSearchRange);
        editTargetNodeName = getNearestRoiName(crossX, crossY, crossZ, containedRoiNames);
        if (editTargetNodeName.equals("0")) { // return if still not found
            imp.killRoi();
            updateInfo("Edit target NOT found!");
            return;
        }
        TextRoi targetNameRoi = new TextRoi(crossX, crossY, editTargetNodeName);
        targetNameRoi.setPosition(0, crossZ, imp.getFrame());
        imp.setRoi(targetNameRoi);
        updateInfo(defaultInfo);
        editTargetName_jLabel.setText(editTargetNodeName);
        int targetPosition = -1;
        String[] targetPt = new String[7];
        if (editTargetNodeName.contains("-")) { // selected branch node
            // setup neuronTree selection
            ntNeuronNode branchNode = getTracingNodeByNodeName(editTargetNodeName);
            ArrayList<String[]> tracingResult = branchNode.getTracingResult();
            targetPosition = getPositionInTracingResultWithSmallestDistanceToPoint(tracingResult,
                    crossX, crossY, crossZ)[0];
            targetPt = tracingResult.get(targetPosition);
        } else { // selected soma slice node
            // setup neuronTree selection
            String neuronNumber = editTargetNodeName.split(":")[0];
            ntNeuronNode somaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNumber);
            for (int i = 0; i < somaSomaNode.getChildCount(); i++) {
                ntNeuronNode somaSliceNode = (ntNeuronNode) somaSomaNode.getChildAt(i);
                if (editTargetNodeName.equals(somaSliceNode.toString())) {
                    ArrayList<String[]> tracingResult = somaSliceNode.getTracingResult();
                    targetPosition = getPositionInTracingResultWithSmallestDistanceToPoint(tracingResult,
                            crossX, crossY, crossZ)[0];
                    targetPt = tracingResult.get(targetPosition);
                    break;
                }
            }
        }
        if (targetPosition > -1) {
            endPoint = new int[7];
            endPoint[0] = 0;
            endPoint[1] = Integer.parseInt(targetPt[1]);
            endPoint[2] = Integer.parseInt(targetPt[2]);
            endPoint[3] = Integer.parseInt(targetPt[3]);
            endPoint[4] = 0;
            endPoint[5] = 0;
            endPoint[6] = 0;
            endPosition_jLabel.setText("(" + endPoint[1] + ", " + endPoint[2] + ", " + endPoint[3] + ")");
            String[] endInfo = getPointIntColorInfo(endPoint, analysisChannels);
            endIntensity_jLabel.setText(endInfo[0]);
            endColor_jLabel.setText(endInfo[1]);
            hasEndPt = true;
            updatePointBox();
            imp.setZ(endPoint[3]);
        }
    }

    private void clearAllNeuronTreeSelection() {
        canUpdateDisplay = false;
        //recallTreeStatusOnly = true;
        recordTreeExpansionSelectionStatus();
        neuronList_jTree.clearSelection();
        canUpdateDisplay = true;
        updateDisplay();
    }

    private void selectAllVisibleNeuronTreeNodes() {
        canUpdateDisplay = false;
        neuronList_jTree.clearSelection();
        for (int r = 0; r < neuronList_jTree.getRowCount(); r++) {
            neuronList_jTree.addSelectionPath(neuronList_jTree.getPathForRow(r));
        }
        canUpdateDisplay = true;
        updateDisplay();
    }

    private void setAllToNeuronSomaNodeInTree(MouseEvent e) {
        if (neuronList_jTree.getSelectionCount() >= 1) {
            //recallTreeStatusOnly = true;
            recordTreeExpansionSelectionStatus();
            TreePath[] selectedPaths = neuronList_jTree.getSelectionPaths();
            neuronList_jTree.clearSelection();
            for (TreePath selectedPath : selectedPaths) {
                ntNeuronNode selectedNeuronSomaNode = (ntNeuronNode) selectedPath.getPathComponent(1);
                TreePath newPath = new TreePath(selectedNeuronSomaNode.getPath());
                neuronList_jTree.addSelectionPath(newPath);
            }
            TreePath lastPath = new TreePath(selectedPaths[selectedPaths.length - 1].getPath());
            neuronList_jTree.scrollPathToVisible(lastPath);
        }
    }

    private void toggleTracingNodeInTreeFromScreen(MouseEvent e) {
        updatePositionInfo(e);
        ArrayList<String> containedRoiNames = getContainedRoiNamesOnScreen(crossX, crossY, crossZ, roiSearchRange);
        String clickedTarget = getNearestRoiName(crossX, crossY, crossZ, containedRoiNames);
        if (clickedTarget.equals("0")) { // return if still not found
            updateInfo(defaultInfo);
            return;
        }

        //recallTreeStatusOnly = true;
        //recordTreeExpansionSelectionStatus();
        TreePath[] selectedPaths = neuronList_jTree.getSelectionPaths();
        boolean inSelection = false;
        for (TreePath TreePath : selectedPaths) {
            ntNeuronNode selectedNode = (ntNeuronNode) TreePath.getLastPathComponent();
            //IJ.log(clickedTarget+" =? "+selectedNode.toString());
            if (clickedTarget.equals(selectedNode.toString())) {
                neuronList_jTree.removeSelectionPath(TreePath);
                inSelection = true;
                break;
            }
        }
        if (!inSelection) {
            ntNeuronNode newlySelectedNode = getTracingNodeByNodeName(clickedTarget);
            TreePath newlySelectedPath = new TreePath(newlySelectedNode.getPath());
            neuronList_jTree.addSelectionPath(newlySelectedPath);
            neuronList_jTree.scrollPathToVisible(newlySelectedPath);
        }
    }

    private void setSingleTracingNodeInTreeToClickPointFromScreen(MouseEvent e) {
        updatePositionInfo(e);
        ArrayList<String> containedRoiNames = getContainedRoiNamesOnScreen(crossX, crossY, crossZ, roiSearchRange);
        String clickedTarget = getNearestRoiName(crossX, crossY, crossZ, containedRoiNames);
        if (clickedTarget.equals("0")) { // return if still not found
            updateInfo(defaultInfo);
            return;
        }
        //recallTreeStatusOnly = true;
        //recordTreeExpansionSelectionStatus();
        // go to the clicked target node
        if (clickedTarget.contains("-")) { // selected branch node
            // setup neuronTree selection
            ntNeuronNode clickedNode = getTracingNodeByNodeName(clickedTarget);
            TreePath clickedPath = new TreePath(clickedNode.getPath());
            neuronList_jTree.setSelectionPath(clickedPath);
            neuronList_jTree.scrollPathToVisible(clickedPath);
            int clickedPosition
                    = getPositionInTracingResultWithSmallestDistanceToPoint(clickedNode.getTracingResult(),
                            crossX, crossY, crossZ)[0];
            if (clickedPosition > -1) {
                pointTable_jTable.setRowSelectionInterval(clickedPosition, clickedPosition);
                scroll2pointTableVisible(clickedPosition, 0);
            }
        } else { // selected soma slice node
            // setup neuronTree selection
            String neuronNumber = clickedTarget.split(":")[0];
            ntNeuronNode neuronSomaNode = getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber);
            TreePath neuronTreePath = new TreePath(neuronSomaNode.getPath());
            neuronList_jTree.setSelectionPath(neuronTreePath);
            neuronList_jTree.scrollPathToVisible(neuronTreePath);
            // setup displaySomaTree selection
            ntNeuronNode somaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNumber);
            for (int i = 0; i < somaSomaNode.getChildCount(); i++) {
                ntNeuronNode somaSliceNode = (ntNeuronNode) somaSomaNode.getChildAt(i);
                if (clickedTarget.equals(somaSliceNode.toString())) {
                    displaySomaList_jTree.setSelectionInterval(i, i);
                    displaySomaList_jTree.scrollRowToVisible(i);
                    int clickedPosition
                            = getPositionInTracingResultWithSmallestDistanceToPoint(somaSliceNode.getTracingResult(),
                                    crossX, crossY, crossZ)[0];
                    if (clickedPosition > -1) {
                        pointTable_jTable.setRowSelectionInterval(clickedPosition, clickedPosition);
                        scroll2pointTableVisible(clickedPosition, 0);
                    }
                    break;
                }
            }
        }
    }

    private void setSingleTracingNodeInTreeToEndPointFromScreen(MouseEvent e) {
        updatePositionInfo(e);
        ArrayList<String> containedRoiNames = getContainedRoiNamesOnScreen(crossX, crossY, crossZ, roiSearchRange);
        String clickedTarget = getNearestRoiName(crossX, crossY, crossZ, containedRoiNames);
        if (clickedTarget.equals("0")) { // return if still not found
            updateInfo(defaultInfo);
            return;
        }
        //recallTreeStatusOnly = true;
        //recordTreeExpansionSelectionStatus();
        // go to the clicked target node
        if (clickedTarget.contains("-")) { // selected branch node
            // setup neuronTree selection
            ntNeuronNode clickedNode = getTracingNodeByNodeName(clickedTarget);
            TreePath clickedPath = new TreePath(clickedNode.getPath());
            neuronList_jTree.setSelectionPath(clickedPath);
            neuronList_jTree.scrollPathToVisible(clickedPath);
            int endPosition = pointTable_jTable.getRowCount() - 1;
            pointTable_jTable.setRowSelectionInterval(endPosition, endPosition);
            scroll2pointTableVisible(endPosition, 0);
        } else { // selected soma slice node
            // setup neuronTree selection
            String neuronNumber = clickedTarget.split(":")[0];
            ntNeuronNode neuronSomaNode = getSomaNodeFromNeuronTreeByNeuronNumber(neuronNumber);
            TreePath neuronTreePath = new TreePath(neuronSomaNode.getPath());
            neuronList_jTree.setSelectionPath(neuronTreePath);
            neuronList_jTree.scrollPathToVisible(neuronTreePath);
            // setup displaySomaTree selection
            ntNeuronNode somaSomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNumber);
            for (int i = 0; i < somaSomaNode.getChildCount(); i++) {
                ntNeuronNode somaSliceNode = (ntNeuronNode) somaSomaNode.getChildAt(i);
                if (clickedTarget.equals(somaSliceNode.toString())) {
                    displaySomaList_jTree.setSelectionInterval(i, i);
                    displaySomaList_jTree.scrollRowToVisible(i);
                    int endPosition = pointTable_jTable.getRowCount() - 1;
                    pointTable_jTable.setRowSelectionInterval(endPosition, endPosition);
                    scroll2pointTableVisible(endPosition, 0);
                    break;
                }
            }
        }
    }

    private ArrayList<String> getPtContainingRoiName(Overlay traceOL, int x, int y, int range) {
        ArrayList<String> containedNames = new ArrayList<String>();
        for (int i = 0; i < traceOL.size(); i++) {
            Roi roi = traceOL.get(i);
            Polygon roiPolygon = roi.getPolygon();
            for (int n = -range; n <= range; n++) {
                for (int m = -range; m <= range; m++) {
                    if (roiPolygon.contains(x + n, y + m)) {
                        containedNames.add(roi.getName());
                    }
                }
            }
        }
        return containedNames;
    }

    private ArrayList<String> getPtContainingRoiName(String avoidNeuronName, Overlay traceOL, int x, int y, int range) {
        ArrayList<String> containedNames = new ArrayList<String>();
        for (int i = 0; i < traceOL.size(); i++) {
            Roi roi = traceOL.get(i);
            String roiNeuronName = roi.getName();
            if (roiNeuronName.contains("-")) {
                roiNeuronName = roiNeuronName.split("-")[0];
            } else {
                roiNeuronName = roiNeuronName.split(":")[0];
            }
            Polygon roiPolygon = roi.getPolygon();
            for (int n = -range; n <= range; n++) {
                for (int m = -range; m <= range; m++) {
                    if (roiPolygon.contains(x + n, y + m)) {
                        if (!roiNeuronName.equals(avoidNeuronName)) {
                            containedNames.add(roi.getName());
                        }
                    }
                }
            }
        }
        return containedNames;
    }

    private ArrayList<String> getContainedRoiNamesOnScreen(int x, int y, int z, int searchRange) {
        int currentZ = z - 1;
        ArrayList<String> name = new ArrayList<String>();
        // search soma trace and soma name first
        if (overlayAllSoma_jCheckBox.isSelected()) { // all somas
            if (allSomaTraceOL[currentZ] != null) {
                name = getPtContainingRoiName(allSomaTraceOL[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        // search all neurons, if checked
        if (overlayAllPoints_jCheckBox.isSelected()) { // search all points
            if (allNeuronTraceOL != null) {
                name = getPtContainingRoiName(allNeuronTraceOL, x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        } else { // search extend points
            if (allNeuronTraceOLextPt[currentZ] != null) {
                name = getPtContainingRoiName(allNeuronTraceOLextPt[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }

        // if not found, search selected neurons
        if (overlayAllSelectedPoints_jCheckBox.isSelected()) { // search all points
            // search selected whole neurons
            if (selectedNeuronTraceOL != null) {
                name = getPtContainingRoiName(selectedNeuronTraceOL, x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            // if not found, search selected arbors   
            if (selectedArborTraceOL != null) {
                name = getPtContainingRoiName(selectedArborTraceOL, x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            // if not found, search selected branches
            if (selectedBranchTraceOL != null) {
                name = getPtContainingRoiName(selectedBranchTraceOL, x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        } else { // search extend points
            // search selected whole neurons
            if (selectedNeuronTraceOLextPt[currentZ] != null) {
                name = getPtContainingRoiName(selectedNeuronTraceOLextPt[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            // if not found, search selected arbors         
            if (selectedArborTraceOLextPt[currentZ] != null) {
                name = getPtContainingRoiName(selectedArborTraceOLextPt[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            // if not found, search selected branches
            if (selectedBranchTraceOLextPt[currentZ] != null) {
                name = getPtContainingRoiName(selectedBranchTraceOLextPt[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return name;
    }

    private ArrayList<String> getContainedRoiNamesOnScreen(String avoidNeuronName, int x, int y, int z, int searchRange) {
        int currentZ = z - 1;
        ArrayList<String> name = new ArrayList<String>();
        // search soma trace and soma name first
        if (overlayAllSoma_jCheckBox.isSelected()) { // all somas
            if (allSomaTraceOL[currentZ] != null) {
                name = getPtContainingRoiName(avoidNeuronName, allSomaTraceOL[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        // search all neurons, if checked
        if (overlayAllPoints_jCheckBox.isSelected()) { // search all points
            if (allNeuronTraceOL != null) {
                name = getPtContainingRoiName(avoidNeuronName, allNeuronTraceOL, x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        } else { // search extend points
            if (allNeuronTraceOLextPt[currentZ] != null) {
                name = getPtContainingRoiName(avoidNeuronName, allNeuronTraceOLextPt[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }

        // if not found, search selected neurons
        if (overlayAllSelectedPoints_jCheckBox.isSelected()) { // search all points
            // search selected whole neurons
            if (selectedNeuronTraceOL != null) {
                name = getPtContainingRoiName(avoidNeuronName, selectedNeuronTraceOL, x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            // if not found, search selected arbors   
            if (selectedArborTraceOL != null) {
                name = getPtContainingRoiName(avoidNeuronName, selectedArborTraceOL, x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            // if not found, search selected branches
            if (selectedBranchTraceOL != null) {
                name = getPtContainingRoiName(avoidNeuronName, selectedBranchTraceOL, x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        } else { // search extend points
            // search selected whole neurons
            if (selectedNeuronTraceOLextPt[currentZ] != null) {
                name = getPtContainingRoiName(avoidNeuronName, selectedNeuronTraceOLextPt[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            // if not found, search selected arbors         
            if (selectedArborTraceOLextPt[currentZ] != null) {
                name = getPtContainingRoiName(avoidNeuronName, selectedArborTraceOLextPt[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            // if not found, search selected branches
            if (selectedBranchTraceOLextPt[currentZ] != null) {
                name = getPtContainingRoiName(avoidNeuronName, selectedBranchTraceOLextPt[currentZ], x, y, searchRange);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return name;
    }

    private String getNearestRoiName(int x, int y, int z, ArrayList<String> containedRoiNames) {
        String nearestRoiName = "0";
        int nearestDistance2 = 1000000000;
        for (String name : containedRoiNames) {
            ntNeuronNode roiNode = getTracingNodeByNodeName(name);
            int roiNodeMinDistance2 = getPositionInTracingResultWithSmallestDistanceToPoint(roiNode.getTracingResult(),
                    x, y, z)[1];
            if (roiNodeMinDistance2 < nearestDistance2) {
                nearestRoiName = name;
                nearestDistance2 = roiNodeMinDistance2;
            }
        }
        return nearestRoiName;
    }

    /**
     * methods implement MouseMotionListener - respond to mouse event when mouse is entered
     * Override function, but no implementation given
     * @param e 
     */
    
    @Override
    public void mouseEntered(MouseEvent e) {
    }

    /**
     * methods implement MouseMotionListener - respond to mouse event when mouse is released
     * Override function, but no implementation given
     * @param e 
     */
    
    @Override
    public void mouseReleased(MouseEvent e) {
    }

    /**
     * methods implement MouseWheelEvent -- response to mouse wheel event
     *
     * @param event
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
        synchronized (this) {
            int slice = imp.getZ() + event.getWheelRotation();

            if (slice < 1) {
                slice = 1;
            } else if (slice > impNSlice) {
                slice = impNSlice;
            }

            imp.setZ(slice);
            updateZprojectionImp();
            if (impSkeletonized != null) {
                impSkeletonized.setZ(slice);
                impSkeletonized.updateAndDraw();
            }
            
            if (impSkeletonizedProjection != null) {
                updateSkeletonizedProjection();
            }
            
            if (blank_jCheckBox.isSelected()) {
                updateOverlay();
            }
        }

    }
    // </editor-fold>

    /**
     * methods implement KeyListener -- response to keyboard event
     * this is when key is pressed
     * @param keyevent
     */
    @Override
    public void keyPressed(KeyEvent keyevent) {
        if ((int) keyevent.getKeyChar() == 32) {
            Toolbar.getInstance().setTool("hand");
        }
    }
    /**
     * methods implement KeyListener -- response to keyboard event
     * this is when key is released
     * @param keyevent 
     */
    @Override
    public void keyReleased(KeyEvent keyevent) {
        if ((int) keyevent.getKeyChar() == 32) {
            IJ.setTool("freeline");
            //Toolbar.getInstance().setTool("freehand");
            //Toolbar.getInstance().setTool(Toolbar.getInstance().getToolId(ntToolTrace.toolName));
        }
    }
    /**
     *  methods implement KeyListener -- response to keyboard event
     * this is when key is type
     * @param keyevent 
     */
    @Override
    public void keyTyped(KeyEvent keyevent) {
        //IJ.log(keyevent.getKeyChar() + " = " + (int) keyevent.getKeyChar());
        int xIn, yIn, xOut, yOut;
        Point locIn, locOut;

        switch ((int) keyevent.getKeyChar()) {
            case 1: // 'ctrl-a'
                break;
            case 2: // 'ctrl-b'
                IJ.run(imp, "Blue", "");
                break;
            case 4: // 'ctrl-d'
                break;
            case 7: // 'ctrl-g'
                IJ.run(imp, "Green", "");
                break;
            case 10: // 'enter'
                if (keyevent.getSource().equals(neuronList_jTree)) {
                    setDefaultNeuronTreeSelection();
                }
                if (keyevent.getSource().equals(displaySomaList_jTree)) {
                    setDefaultDisplaySomaTreeSelection();
                }
                break;
            case 12: // 'ctrl-l'
                dataHelper.loadData();
                break;
            case 17: // 'ctrl-q'
                quit();
                break;
            case 18: // 'ctrl-r'
                IJ.run(imp, "Red", "");
                break;
            case 19: // 'ctrl-s'
                dataHelper.saveData();
                break;
            case 25: // 'ctrl-yIn'
                history.forwardHistory();
                break;
            case 26: // 'ctrl-z'
                history.backwardHistory();
                break;
            case 32: // 'space'
                //IJ.setTool("hand");
                break;
            case 44: // ','
                if (zProjectionInterval_jSpinner.getPreviousValue() != null) {
                    zProjectionInterval_jSpinner.setValue(zProjectionInterval_jSpinner.getPreviousValue());
                    zProjInterval = (Integer) zProjectionInterval_jSpinner.getValue();
                    updateZprojectionImp();
                }
                break;
            case 45: // '-'
                locOut = cns.getCursorLoc();
                if (!cns.cursorOverImage()) {
                    Rectangle srcRect = cns.getSrcRect();
                    locOut.x = srcRect.x + srcRect.width / 2;
                    locOut.y = srcRect.y + srcRect.height / 2;
                }
                xOut = cns.screenX(locOut.x);
                yOut = cns.screenY(locOut.y);
                cns.zoomOut(xOut, yOut);
                if (cns.getMagnification() <= 1.0) {
                    imp.repaintWindow();
                }
                updateZprojectionImp();
                break;
            case 46: // '.'
                if (zProjectionInterval_jSpinner.getNextValue() != null) {
                    zProjectionInterval_jSpinner.setValue(zProjectionInterval_jSpinner.getNextValue());
                    zProjInterval = (Integer) zProjectionInterval_jSpinner.getValue();
                    updateZprojectionImp();
                }
                break;
            case 49: // '1'
                if (impNChannel >= 1 && toggleCh1_jCheckBox.isSelected()) {
                    toggleChannel(1);
                    updateZprojectionImp();
                }
                break;
            case 50: // '2'
                if (impNChannel >= 2 && toggleCh2_jCheckBox.isSelected()) {
                    toggleChannel(2);
                    updateZprojectionImp();
                }
                break;
            case 51: // '3'
                if (impNChannel >= 3 && toggleCh3_jCheckBox.isSelected()) {
                    toggleChannel(3);
                    updateZprojectionImp();
                }
                break;
            case 52: // '4'
                if (impNChannel >= 4 && toggleCh4_jCheckBox.isSelected()) {
                    toggleChannel(4);
                    updateZprojectionImp();
                }
                break;
            case 53: // '5'
                if (impNChannel >= 5 && toggleCh5_jCheckBox.isSelected()) {
                    toggleChannel(5);
                    updateZprojectionImp();
                }
                break;
            case 54: // '6'
                if (impNChannel >= 6 && toggleCh6_jCheckBox.isSelected()) {
                    toggleChannel(6);
                    updateZprojectionImp();
                }
                break;
            case 55: // '7'
                if (impNChannel >= 7 && toggleCh7_jCheckBox.isSelected()) {
                    toggleChannel(7);
                    updateZprojectionImp();
                }
                break;
            case 56: // '8'
                if (impNChannel >= 8 && toggleCh8_jCheckBox.isSelected()) {
                    toggleChannel(8);
                    updateZprojectionImp();
                }
                break;
            case 57: // '9'
                break;
            case 61: // '='
                locIn = cns.getCursorLoc();
                if (!cns.cursorOverImage()) {
                    Rectangle srcRect = cns.getSrcRect();
                    locIn.x = srcRect.x + srcRect.width / 2;
                    locIn.y = srcRect.y + srcRect.height / 2;
                }
                xIn = cns.screenX(locIn.x);
                yIn = cns.screenY(locIn.y);
                cns.zoomIn(xIn, yIn);
                if (cns.getMagnification() <= 1.0) {
                    imp.repaintWindow();
                }
                updateZprojectionImp();
                break;
            case 65: // 'A'
                //if (keyevent.getSource().equals(cns)) {
                //    selectAllVisibleNeuronTreeNodes();
                //}
                break;
            case 68: // 'D'
                //if (keyevent.getSource().equals(cns)) {
                //    clearAllNeuronTreeSelection();
                //}
                break;
            case 97: // 'a' 
                traceHelper.traceNeurite();
                break;
            case 98: // 'b'
                break;
            case 99: // 'c'
                clearStartEndPts();
                break;
            case 100: // 'd'
                traceHelper.traceSpine();
                break;
            case 101: // 'e'
                decrease_synapse_number();
                //toggleSynapse();
                break;
            case 102: // 'f'
                jumpToNextConnected();
                break;
            case 103: // 'g'
                addLabelToSelection();
                break;
            case 104: // 'h'
                IJ.setTool("freeline");
                break;
            case 105: // 'i' 
                jumpToNextIncompleteProcess();
                break;
            case 106: // 'j'
                IJ.setTool("zoom");
                break;
            case 108: // 'l'
                scrollToNextSelectedProcess();
                break;
            case 109: // 'm'
                break;
            case 110: // 'n'
                toggleConnection();
                break;
            case 111: // 'o'
                completeSomaSliceRoi();
                break;
            case 112: // 'p'
                jumpToNextSynapse();
                break;
            case 113: // 'q'
                locOut = cns.getCursorLoc();
                if (!cns.cursorOverImage()) {
                    Rectangle srcRect = cns.getSrcRect();
                    locOut.x = srcRect.x + srcRect.width / 2;
                    locOut.y = srcRect.y + srcRect.height / 2;
                }
                xOut = cns.screenX(locOut.x);
                yOut = cns.screenY(locOut.y);
                cns.zoomOut(xOut, yOut);
                if (cns.getMagnification() <= 1.0) {
                    imp.repaintWindow();
                }
                updateZprojectionImp();
                break;
            case 114: // 'r'
                increase_synapse_number();
                break;
            case 115: // 's'
                traceHelper.traceSoma();
                break;
            case 116: // 't'
                gotoConnectedSynapse();
                break;
            case 117: // 'u'
                updateDisplay();
                break;
            case 118: // 'v'
                toggleSkeletonizedChannel();
                break;
            case 119: // 'w' 
                locIn = cns.getCursorLoc();
                if (!cns.cursorOverImage()) {
                    Rectangle srcRect = cns.getSrcRect();
                    locIn.x = srcRect.x + srcRect.width / 2;
                    locIn.y = srcRect.y + srcRect.height / 2;
                }
                xIn = cns.screenX(locIn.x);
                yIn = cns.screenY(locIn.y);
                cns.zoomIn(xIn, yIn);
                if (cns.getMagnification() <= 1.0) {
                    imp.repaintWindow();
                }
                updateZprojectionImp();
                break;
            case 120: // 'x'
                traceHelper.changeTracingCompleteness();
                break;
            case 121: // 'y'
                break;
            case 122: // 'z'
                break;
            case 127: // 'delete'
                if (neuronList_jTree.getSelectionCount() == 1) {
                    ntNeuronNode delNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
                    if (delNode.isTrunckNode()) {
                        if (displaySomaList_jTree.getSelectionCount() == 0) {
                            deleteNeuronsFromNeuronTree();
                        } else if (displaySomaList_jTree.getSelectionCount() == 1) {
                            deleteSomaSlices();
                        }
                    } else {
                        traceHelper.deleteOneBranchFromNeuronTree();
                    }
                } else {
                    IJ.error("Multiple deletion is not allowd using hotkey");
                }
                break;
            default:
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="retrieve position and color info corresponding to mouse/wheel movement">
    
    /**
     * updates position info based on MouseEvent e
     * @param e 
     */
    protected void updatePositionInfo(MouseEvent e) {
        crossX = cns.offScreenX(e.getX());
        crossY = cns.offScreenY(e.getY());
        crossZ = imp.getZ();
        //updatePositionIntColor();
    }

    private String[] getPointIntColorInfo(int[] point, boolean[] analysisCh) {
        float[] pointIntColor = Functions.getPtIntColor(point, analysisCh, imp.getFrame());
        String[] info = new String[2];
        info[0] = pointIntColor[0] + "";
        info[1] = "(";
        for (int channel = 1; channel <= impNChannel - 1; channel++) {
            info[1] = info[1] + (((float) Math.round(pointIntColor[channel] * 100)) / 100 + ", ");
        }
        info[1] = info[1] + (((float) Math.round(pointIntColor[impNChannel] * 100)) / 100 + ")");
        return info;
    }

    private void updatePositionIntColor() {
        int[] point = {0, crossX, crossY, crossZ, 0, 0, 0};
        ptIntColor = Functions.getPtIntColor(point, analysisChannels, imp.getFrame());
        colorInfo = "Intensity (" + ptIntColor[0] + "); Normalized Color (";
        for (int channel = 1; channel <= impNChannel - 1; channel++) {
            colorInfo = colorInfo + (ptIntColor[channel] + ", ");
        }
        colorInfo = colorInfo + (ptIntColor[impNChannel] + ")");
    }

    /**
     * updates the info so that the info jLabel's text is set to messega
     * @param messega 
     */
    
    protected void updateInfo(String messega) {
        // update information
        info_jLabel.setText(messega);
    }

    Thread z_project_thread = null;

    private void updateZprojectionImp() {
        if (z_project_thread != null && z_project_thread.isAlive()) {
            return;
        }

        z_project_thread = new Thread(new ZProjectionInternal());
        z_project_thread.start();
    }

    private void decrease_synapse_number() {
        // This method will decrese the number of selected synapse

        if (pointTable_jTable.getSelectedRowCount() != 1) {
            IJ.error("Select only one point to add/erase synapse !");
            return;
        }

        int row = pointTable_jTable.getSelectedRow();
        ntNeuronNode selectedNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
        if (!selectedNode.isBranchNode()) { // selected node is a somaSlice node
            selectedNode = (ntNeuronNode) displaySomaList_jTree.getLastSelectedPathComponent();
            selectedNode = getSomaSliceNodeFromAllSomaTreeBySomaSliceName(selectedNode.toString());
        }

        // set synapse
        int synapseStatus = (Integer) pointTableModel.getValueAt(row, 5);
        if (synapseStatus == 0) {
            return;
        }

        synapseStatus--;
        selectedNode.setSynapse(row, synapseStatus);
        pointTableModel.setValueAt(synapseStatus, row, 5);

        if (synapseStatus == 0) {
            String synapseName = (String) pointTableModel.getValueAt(row, 6);

            if (synapseName.equals("0")) { // no connection to break
                Object spineStatus = pointTableModel.getValueAt(row, 0);
                String currentTag = spineStatus.toString();
                if (currentTag.contains(":Spine#")) {
                    traceHelper.removeSpine(currentTag);
                    selectedNode.setSpine(row, "0");
                    String newTag = currentTag.split(":")[0];
                    if (currentTag.contains("/")) {
                        newTag = newTag + "/" + currentTag.split("/")[1];
                    } else if (currentTag.endsWith("*")) {
                        newTag = newTag + "*";
                    }
                    pointTableModel.setValueAt(newTag, row, 0);
                }
            } else {// ask for breaking connection
                eraseConnectionFromPoint(); // MUST INVESTIGATE
            }

        }

        editTargetName_jLabel.setText((String) pointTableModel.getValueAt(row, 6));
        history.saveHistory();
        updateDisplay();
    }

    private void increase_synapse_number() {
        // This method will increase the number of selected synapse

        if (pointTable_jTable.getSelectedRowCount() != 1) {
            IJ.error("Select only one point to add/erase synapse !");
            return;
        }

        int row = pointTable_jTable.getSelectedRow();
        ntNeuronNode selectedNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
        if (!selectedNode.isBranchNode()) { // selected node is a somaSlice node
            selectedNode = (ntNeuronNode) displaySomaList_jTree.getLastSelectedPathComponent();
            selectedNode = getSomaSliceNodeFromAllSomaTreeBySomaSliceName(selectedNode.toString());
        }

        // set synapse
        int synapseStatus = (Integer) pointTableModel.getValueAt(row, 5);

        synapseStatus++;
        selectedNode.setSynapse(row, synapseStatus);
        pointTableModel.setValueAt(synapseStatus, row, 5);

        editTargetName_jLabel.setText((String) pointTableModel.getValueAt(row, 6));
        history.saveHistory();
        updateDisplay();
    }

    private class ZProjectionInternal implements Runnable {

        private ZProjectionInternal() {

        }

        @Override
        public void run() {
            updateZprojectionImp_internal();
        }

        private void updateZprojectionImp_internal() {
            if (!projectionUpdate_jCheckBox.isSelected() || impZproj == null) {
                return;
            }
            
            // Save the active channels for later use
            boolean[] chActive = ((CompositeImage) impZproj).getActiveChannels();
            String chActSetting = "";
            for (int c = 0; c < chActive.length; c++) {
                if (chActive[c]) {
                    chActSetting += "1";
                } else {
                    chActSetting += "0";
                }
            }

            int impZprojC = impZproj.getC();
            int currentZ = imp.getZ();
            
            int minZ = currentZ - zProjInterval;
            int maxZ = currentZ + zProjInterval;

            if (currentZ == last_current_z) {
                return;
            } else {
                last_current_z = currentZ;
            }

            if (minZ < 1) {
                minZ = 1;
            } else if (maxZ > impNSlice) {
                maxZ = impNSlice;
            }

            Roi impRoi = imp.getRoi();

            roiXmin = cns.offScreenX(0);
            int roiXmax = cns.offScreenX(cns.getWidth()) - 1;
            int roiXmid = (roiXmin + roiXmax) / 2;

            roiYmin = cns.offScreenY(0);
            int roiYmax = cns.offScreenY(cns.getHeight()) - 1;
            int roiYmid = (roiYmin + roiYmax) / 2;

            //System.err.println(roiXmax + "," + roiXmid + " - " + roiYmax + "," + roiYmid);
            if (roiXmax - roiXmin > zProjXY) {
                roiXmin = roiXmid - zProjXY / 2 + 1;
                roiXmax = roiXmid + zProjXY / 2 - 1;
            }

            if (roiYmax - roiYmin > zProjXY) {
                roiYmin = roiYmid - zProjXY / 2 + 1;
                roiYmax = roiYmid + zProjXY / 2 - 1;
            }

            imp.setRoi(roiXmin, roiYmin, roiXmax - roiXmin, roiYmax - roiYmin);
            final Roi targetRoi = imp.getRoi();
            imp.setRoi(impRoi);

            ImagePlus temp = DuplicateProjector.duplicateAndProject(imp, impZproj, 1, impNChannel, minZ, maxZ, targetRoi);
            if (temp == null) {
                return;
            }

            impZproj.resetStack();
            impZproj.setImage(temp);
            impZproj.updateAndDraw();

            impZproj.setOverlay(null);
            winZproj.setSize(win.getSize());
            cnsZproj.setMagnification(cns.getMagnification());

            temp.close();

            //boolean[] chActive = cmp.getActiveChannels();
          
            impZproj.setActiveChannels(chActSetting);
            impZproj.setC(impZprojC);
        }

    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="update image display">
    
    /**
     * records the the tree expansions selection status. 
     * Does this by calling the recordNeuronTreeExpansionStatus() and recordTreeSelectionStatus() functions
     */
    protected void recordTreeExpansionSelectionStatus() {
        recordNeuronTreeExpansionStatus();
        recordTreeSelectionStatus();
    }

    /**
     * restores the tree expansions selections status. 
     * Does this by calling the restoreNeuronTreeExpansionStatus() and restoreTreeSelectionStatus() functions
     */
    
    protected void restoreTreeExpansionSelectionStatus() {
        restoreNeuronTreeExpansionStatus();
        restoreTreeSelectionStatus();
    }

    /**
     * records the expansions status of the neuron tree
     */
    
    protected void recordNeuronTreeExpansionStatus() {
        expandedNeuronNames.clear();
        for (int n = 0; n < rootNeuronNode.getChildCount(); n++) {
            ntNeuronNode neuron = (ntNeuronNode) rootNeuronNode.getChildAt(n);
            TreePath neuronPath = new TreePath(neuron.getPath());
            if (neuronList_jTree.isExpanded(neuronPath)) {
                String neuronName = neuron.toString();
                expandedNeuronNames.add(neuronName);
            }
        }
    }

    /**
     * restores the expansions status of the neuron tree
     */
    
    protected void restoreNeuronTreeExpansionStatus() {
        for (int n = 0; n < rootNeuronNode.getChildCount(); n++) {
            ntNeuronNode neuron = (ntNeuronNode) rootNeuronNode.getChildAt(n);
            String neuronNumber = neuron.getNeuronNumber();
            for (String expandedNeuronName : expandedNeuronNames) {
                if (expandedNeuronName.contains("/")) {
                    expandedNeuronName = expandedNeuronName.split("/")[0];
                }
                if (expandedNeuronName.equals(neuronNumber)) {
                    TreePath neuronPath = new TreePath(neuron.getPath());
                    neuronList_jTree.expandPath(neuronPath);
                    break;
                }
            }
        }
    }

    private void recordTreeSelectionStatus() {
        selectedNeuronNames.clear();
        selectedSomaSliceNames.clear();
        selectedTableRows.clear();
        TreePath[] selectedNeuronTreePaths = neuronList_jTree.getSelectionPaths();
        if (selectedNeuronTreePaths != null) {
            for (TreePath selectedNeuronTreePath : selectedNeuronTreePaths) {
                ntNeuronNode selectedNeuronNode = (ntNeuronNode) selectedNeuronTreePath.getLastPathComponent();
                selectedNeuronNames.add(selectedNeuronNode.toString());
            }
        }
        TreePath[] selectedSomaSliceTreePaths = displaySomaList_jTree.getSelectionPaths();
        if (selectedSomaSliceTreePaths != null) {
            for (TreePath selectedSomaSliceTreePath : selectedSomaSliceTreePaths) {
                ntNeuronNode selectedSomaSliceNode = (ntNeuronNode) selectedSomaSliceTreePath.getLastPathComponent();
                selectedSomaSliceNames.add(selectedSomaSliceNode.toString());
            }
        }
        int[] selectedRows = pointTable_jTable.getSelectedRows();
        if (selectedRows != null) {
            for (int selectedRow : selectedRows) {
                selectedTableRows.add(selectedRow);
            }
        }
        // record trees and table's view
        neuronTreeVisibleRect = neuronList_jTree.getVisibleRect();
        displaySomaTreeVisibleRect = displaySomaList_jTree.getVisibleRect();
        pointTableVisibleRect = pointTable_jTable.getVisibleRect();
    }

    private void restoreTreeSelectionStatus() {
        ArrayList<TreePath> selectedPaths = new ArrayList<TreePath>();
        for (String selectedNeuronName : selectedNeuronNames) {
            ntNeuronNode selectedNeuronNode = getNodeFromNeuronTreeByNodeName(selectedNeuronName);
            if (selectedNeuronNode != null) {
                TreePath selectedNeuronPath = new TreePath(selectedNeuronNode.getPath());
                selectedPaths.add(selectedNeuronPath);
            }
        }
        if (selectedPaths.size() > 0) {
            TreePath[] selectionPaths = new TreePath[selectedPaths.size()];
            for (int i = 0; i < selectedPaths.size(); i++) {
                selectionPaths[i] = (TreePath) selectedPaths.get(i);
            }
            neuronList_jTree.setSelectionPaths(selectionPaths);
        }
        for (String selectedSomaSliceName : selectedSomaSliceNames) {
            //IJ.log("input "+selectedSomaSliceName);
            for (int i = 0; i < rootDisplaySomaNode.getChildCount(); i++) {
                ntNeuronNode somaSliceNode = (ntNeuronNode) rootDisplaySomaNode.getChildAt(i);
                //IJ.log("compare selected "+selectedSomaSliceName+" to "+somaSliceNode.toString());
                if (selectedSomaSliceName.equals(somaSliceNode.toString())) {
                    displaySomaList_jTree.addSelectionRow(i);
                    //IJ.log("added "+i);
                    break;
                }
                //IJ.log("not added "+i);
            }
        }
        int totalTableRows = pointTable_jTable.getRowCount();
        if (pointTable_jTable.getRowCount() > 0) {
            for (int selectedTableRow : selectedTableRows) {
                if (selectedTableRow < totalTableRows) {
                    pointTable_jTable.addRowSelectionInterval(selectedTableRow, selectedTableRow);
                }
            }
        }
        // restore trees and table's view
        if (neuronTreeVisibleRect != null) {
            neuronList_jTree.scrollRectToVisible(neuronTreeVisibleRect);
        }
        if (displaySomaTreeVisibleRect != null) {
            displaySomaList_jTree.scrollRectToVisible(displaySomaTreeVisibleRect);
        }
        if (pointTableVisibleRect != null) {
            pointTable_jTable.scrollRectToVisible(pointTableVisibleRect);
        }
    }

    /**
     * updates neuron tree model and all soma tree model
     */
    
    protected void updateTrees() {
        neuronTreeModel.nodeStructureChanged(rootNeuronNode);
        allSomaTreeModel.nodeStructureChanged(rootAllSomaNode);
    }

    private void updatePointBox() {
        if (imp != null) {
            removePointBoxes();

            // overlay points
            if (overlayPointBox_jCheckBox.isSelected()) {
                addPointBoxes(pointBoxRadius);
            }

            cns.setOverlay(displayOL);
        }
    }

    private void add2displayOL(Overlay OL) {
        if (OL != null) {
            if (OL.size() > 0) {
                for (int n = 0; n < OL.size(); n++) {
                    Roi roi = OL.get(n);
                    displayOL.add(roi);
                }
            }
        }
    }

    private void addPositionCross() {
        // add yellow cross bars to show position on image
        xyHL = new Line(0, (double) crossY + 0.5,
                (double) impWidth, (double) crossY + 0.5);
        xyHL.setStrokeColor(Color.yellow);
        displayOL.add(xyHL);
        xyVL = new Line((double) crossX + 0.5, 0,
                (double) crossX + 0.5, (double) impHeight);
        xyVL.setStrokeColor(Color.yellow);
        displayOL.add(xyVL);
    }

    private void addPointBoxes(int boxRadius) {
        int boxDiameter = boxRadius * 2 + 1;
        if (hasStartPt) {
            startBoxXY = new Roi(startPoint[1] - boxRadius, startPoint[2] - boxRadius, boxDiameter, boxDiameter);
            startBoxXY.setStrokeColor(Color.red);
            startBoxXY.setStrokeWidth(pointBoxLine);
            startBoxXY.setName("startBoxXY");
            displayOL.add(startBoxXY);
        }
        if (hasEndPt) {
            endBoxXY = new Roi(endPoint[1] - boxRadius, endPoint[2] - boxRadius, boxDiameter, boxDiameter);
            endBoxXY.setStrokeColor(Color.cyan);
            endBoxXY.setStrokeWidth(pointBoxLine);
            endBoxXY.setName("endBoxXY");
            displayOL.add(endBoxXY);
        }
    }

    private void removePointBoxes() {
        int displayOLsize = displayOL.size();
        if (displayOLsize >= 2) {
            for (int i = displayOLsize - 1; i >= displayOLsize - 2; i--) {
                Roi currentRoi = displayOL.get(i);
                String currentRoiName = currentRoi.getName();
                if (currentRoiName != null) {
                    if (currentRoiName.equals("startBoxXY") || currentRoiName.equals("endBoxXY")) {
                        displayOL.remove(i);
                    }
                }
            }
        } else if (displayOLsize == 1) {
            Roi currentRoi = displayOL.get(0);
            String currentRoiName = currentRoi.getName();
            if (currentRoiName != null) {
                if (currentRoiName.equals("startBoxXY") || currentRoiName.equals("endBoxXY")) {
                    displayOL.remove(0);
                }
            }
        }
    }

    
    /**
     * returns an integer ArrayList of all the primary branch points associate with node
     * @param node
     * @return 
     */
    public static ArrayList<int[]> getAllPrimaryBranchPoints(ntNeuronNode node) {
        ArrayList<int[]> allPoints = new ArrayList<int[]>();

        if (node == null) {
            return allPoints;
        }

        ntNeuronNode somaNode = getSomaNodeFromNeuronTreeByNeuronNumber(node.getNeuronNumber());

        for (int i = 0; i < somaNode.getChildCount(); i++) {
            ntNeuronNode primaryBranchNode = (ntNeuronNode) somaNode.getChildAt(i);
            ArrayList<String[]> tracing = primaryBranchNode.getTracingResult();

            for (String[] point : tracing) {
                int[] intPt = {0, Integer.parseInt(point[1]),
                    Integer.parseInt(point[2]), Integer.parseInt(point[3]), 0, 0, 0};
                allPoints.add(intPt);
            }
        }

        return allPoints;
    }

    private Map<ntNeuronNode, Color> neuronColorTable = new HashMap<>();
    private Lock neuronColorTableLock = new ReentrantLock();

    /**
     * returns the RGB components of a neuron from a node as a color object
     * If the color returned is white, the node or getNeuronColorFromNodeOriginal(node) function may be null
     * @param node
     * @param alpha
     * @return 
     */
    
    public Color getNeuronColorFromNode(ntNeuronNode node, float alpha) {
        if (node == null) {
            return Color.white; // um this is broken?
        }
        Color toreturn = null;

        neuronColorTableLock.lock();
        if (neuronColorTable.containsKey(node)) {
            toreturn = neuronColorTable.get(node);
            neuronColorTableLock.unlock();
            return toreturn;
        }

        neuronColorTableLock.unlock();

        toreturn = getNeuronColorFromNodeOriginal(node);
        if (toreturn == null) {
            toreturn = Color.white;
        }

        neuronColorTableLock.lock();
        neuronColorTable.put(node, toreturn);
        neuronColorTableLock.unlock();

        float[] cc = toreturn.getRGBComponents(null); // decompose to add alpha
        toreturn = new Color(cc[0], cc[1], cc[2], alpha);

        return toreturn;
    }

    /**
     * returns the RGB components of a neuron from a node original as a color object
     * If the color returned is white, the node may not have any primary branch points
     * @param node
     * @return 
     */
    
    public Color getNeuronColorFromNodeOriginal(ntNeuronNode node) {
        ArrayList<int[]> neuronPoints = getAllPrimaryBranchPoints(node);

        if (neuronPoints.isEmpty()) {
            return Color.white;
        }

        System.out.println(node);
        long start = System.currentTimeMillis();

        float[] tempColor = new float[impNChannel];
        this.color_lock.lock();

        for (int channel = 0; channel < impNChannel; channel++) {
            for (int[] neuronPt : neuronPoints) {
                if (!analysisChannels[channel]) {
                    continue;
                }
                int index = imp.getStackIndex(channel + 1, neuronPt[3], imp.getFrame());
                // // retrive color[channel] and calculate total intensity
                //tempColor[channel] += stk.getProcessor(index).get(neuronPt[1], neuronPt[2]);

                coord3D pt = new coord3D(neuronPt[1], neuronPt[2], index);

                if (this.color_buffer.containsKey(pt)) {
                    tempColor[channel] += this.color_buffer.get(pt);
                } else {
                    float newColor = stk.getProcessor(index).get(neuronPt[1], neuronPt[2]);
                    tempColor[channel] += newColor;

                    this.color_buffer.put(pt, newColor);
                }

            }
        }

        this.color_lock.unlock();

        float[][] allChRGBratios = Functions.getAllChRGBratios();
        float[] allChActiveFloat = Functions.getAllChActiveFloat();
        float[] allChAnalysisFloat = Functions.getAllChAnalysisFloat();
        float[] rgbColor = {0, 0, 0};

        // calculate normalized color - normalize to the max intensity channel
        float max = 0;
        for (int color = 0; color < 3; color++) {
            for (int channel = 0; channel < impNChannel; channel++) {
                rgbColor[color] += (tempColor[channel] * allChActiveFloat[channel] * allChAnalysisFloat[channel] * allChRGBratios[channel][color]);
            }
            //IJ.log("color "+color+" = "+rgbColor[color]);
            max = rgbColor[color] > max ? rgbColor[color] : max;
        }
        //IJ.log("max = "+max);

        //System.out.println( "Took " + (System.currentTimeMillis() - start) );
        for (int i = 0; i < rgbColor.length; i++) {
            rgbColor[i] /= max;
        }

        return new Color(rgbColor[0], rgbColor[1], rgbColor[2]);
    }

    private void getOneBranchTraceRoiExtPt(Overlay[] neuriteTraceOL, Overlay[] neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
            Overlay[] neuriteSpineOL, ntNeuronNode neuriteNode, Color lineColor, float lineWidth, float spineLine, double synapseRadius, double synapseSize, int extDispPts) {
        int frameNumber = imp.getFrame();
        String neuriteName = neuriteNode.toString();
        boolean uniqueSelected = false;
        if (neuronList_jTree.getSelectionCount() == 1) {
            ntNeuronNode selectedNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
            if (neuriteNode.getNeuronNumber().equals(selectedNode.getNeuronNumber())) {
                uniqueSelected = true;
            }
        }
        ArrayList<String[]> linkedPoints = neuriteNode.getTracingResult();
        String nodeType = neuriteNode.getType();
        if (lineColor.getRGB() == -16777216) { // Color.black
            if (nodeType.startsWith("Axon")) {
                lineColor = Color.blue;
            } else if (nodeType.startsWith("Dendrite")) {
                lineColor = Color.red;
            } else if (nodeType.startsWith("Apical")) {
                lineColor = Color.magenta;
            } else if (nodeType.startsWith("Neurite")) {
                lineColor = Color.gray;
            }
        }
        int totalTracedPoints = linkedPoints.size();
        int totalRoiPoints = extDispPts * 2 + 1;
        ArrayList<Integer> storedRoiZpositions = new ArrayList<Integer>();
        storedRoiZpositions.add(-1);
        ArrayList<String> spineTags = new ArrayList<String>();
        Boolean addNameRoi = true;
        for (int n = 0; n < totalTracedPoints; n++) {
            int[] xPoints = new int[totalRoiPoints];
            int[] yPoints = new int[totalRoiPoints];
            for (int i = 0; i < totalRoiPoints; i++) {
                int currentPoint = n - extDispPts + i;
                if (currentPoint < 0) {
                    currentPoint = 0;
                }
                if (currentPoint >= totalTracedPoints) {
                    currentPoint = totalTracedPoints - 1;
                }
                String[] linkedPt = linkedPoints.get(currentPoint);
                xPoints[i] = Integer.parseInt(linkedPt[1]);
                yPoints[i] = Integer.parseInt(linkedPt[2]);
                // add neuriteSynapseRoi
                if (!linkedPt[5].equals("0")) {
                    if (linkedPt[0].contains(":Spine#")) {
                        spineTags.add(linkedPt[0]);
                    } else {
                        OvalRoi synapseRoi = new OvalRoi(
                                xPoints[i] - synapseRadius,
                                yPoints[i] - synapseRadius, synapseSize, synapseSize);
                        synapseRoi.setPosition(0, Integer.parseInt(linkedPt[3]), frameNumber);
                        synapseRoi.setStrokeWidth(synapseRadius);
                        //synapseRoi.setStrokeColor(lineColor);
                        synapseRoi.setStrokeColor(mapSynapseColor(linkedPt[5]));
                        neuriteSynapseOL.add(synapseRoi);
                    }
                    if (!linkedPt[6].equals("0")) {
                        OvalRoi connectedRoi = new OvalRoi(
                                xPoints[i] - synapseRadius,
                                yPoints[i] - synapseRadius, synapseSize, synapseSize);
                        connectedRoi.setPosition(0, Integer.parseInt(linkedPt[3]), frameNumber);
                        connectedRoi.setStrokeWidth(synapseRadius);
                        if (uniqueSelected) {
                            String connected = linkedPt[6].split("#")[1];
                            connectedRoi.setStrokeColor(getNeuronColorFromNode(getNodeFromNeuronTreeByNodeName(connected), connectionAlpha));
                        } else {
                            connectedRoi.setStrokeColor(connectionColor);
                        }
                        neuriteConnectedOL.add(connectedRoi);
                    }
                }
            }
            PolygonRoi pointTraceRoi = new PolygonRoi(xPoints, yPoints, totalRoiPoints, Roi.POLYLINE);
            pointTraceRoi.setStrokeColor(lineColor);
            int currentZPosition = Integer.parseInt(linkedPoints.get(n)[3]);
            pointTraceRoi.setPosition(0, currentZPosition, frameNumber);
            pointTraceRoi.setName(neuriteName);
            pointTraceRoi.setStrokeWidth(lineWidth);
            // add neuriteTraceRoi
            neuriteTraceOL[currentZPosition - 1].add(pointTraceRoi);
            // add neuriteNameRoi
            for (Integer storedRoiZposition : storedRoiZpositions) {
                //IJ.log("compare " + storedRoiZposition + " to " + currentZPosition);
                if (storedRoiZposition == currentZPosition) {
                    addNameRoi = false;
                    break;
                }
            }
            if (addNameRoi) {
                //TextRoi nameRoi = new TextRoi(pointTraceRoi.getBounds().getCenterX(),
                //        pointTraceRoi.getBounds().getCenterY(), neuriteName);
                TextRoi nameRoi = new TextRoi(xPoints[(int) (totalRoiPoints / 2)] - nameRoiXoffset,
                        yPoints[(int) (totalRoiPoints / 2)] - nameRoiYoffset, neuriteName);
                nameRoi.setStrokeColor(lineColor);
                nameRoi.setName(neuriteName);
                nameRoi.setPosition(0, currentZPosition, frameNumber);
                neuriteNameOL[currentZPosition - 1].add(nameRoi);
                storedRoiZpositions.add(currentZPosition);
                //IJ.log("added nameRoi at Z="+currentZPosition);
            }
            addNameRoi = true;
        }

        // add spine Roi into overlay
        for (String spineTag : spineTags) {
            ntNeuronNode spineNode = traceHelper.getSpineNode(spineTag);
            ArrayList<String[]> spinePoints = spineNode.getTracingResult();
            int totalSpinePoints = spinePoints.size();
            storedRoiZpositions = new ArrayList<Integer>();
            storedRoiZpositions.add(-1);
            for (int n = 0; n < totalSpinePoints; n++) {
                int[] xPoints = new int[totalRoiPoints];
                int[] yPoints = new int[totalRoiPoints];
                for (int i = 0; i < totalRoiPoints; i++) {
                    int currentPoint = n - extDispPts + i;
                    if (currentPoint < 0) {
                        currentPoint = 0;
                    }
                    if (currentPoint >= totalSpinePoints) {
                        currentPoint = totalSpinePoints - 1;
                    }
                    String[] linkedPt = spinePoints.get(currentPoint);
                    xPoints[i] = Integer.parseInt(linkedPt[1]);
                    yPoints[i] = Integer.parseInt(linkedPt[2]);
                }
                PolygonRoi spineRoi = new PolygonRoi(xPoints, yPoints, totalRoiPoints, Roi.POLYLINE);
                spineRoi.setStrokeColor(lineColor);
                int currentZPosition = Integer.parseInt(spinePoints.get(n)[3]);
                spineRoi.setPosition(0, currentZPosition, frameNumber);
                spineRoi.setName(spineTag);
                spineRoi.setStrokeWidth(spineLine);
                // add neuriteTraceRoi
                neuriteSpineOL[currentZPosition - 1].add(spineRoi);
            }
        }
    }

    private void getOneBranchTraceRoiAllPt(Overlay neuriteTraceOL, Overlay neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
            Overlay neuriteSpineOL, ntNeuronNode neuriteNode, Color lineColor, float lineWidth, float spineLine, double synapseRadius, double synapseSize) {
        int frameNumber = imp.getFrame();
        String neuriteName = neuriteNode.toString();
        boolean uniqueSelected = false;
        if (neuronList_jTree.getSelectionCount() == 1) {
            ntNeuronNode selectedNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
            if (neuriteNode.getNeuronNumber().equals(selectedNode.getNeuronNumber())) {
                uniqueSelected = true;
            }
        }
        ArrayList<String[]> linkedPoints = neuriteNode.getTracingResult();
//        IJ.log("RGB = "+lineColor.getRGB()+" ("+lineColor.getRed()+", "+lineColor.getGreen()+", "+lineColor.getBlue()+")");

        if (lineColor.getRGB() == -16777216) { // Color.black
            String nodeType = neuriteNode.getType();
            if (nodeType.startsWith("Axon")) {
                lineColor = Color.blue;
            } else if (nodeType.startsWith("Dendrite")) {
                lineColor = Color.red;
            } else if (nodeType.startsWith("Apical")) {
                lineColor = Color.magenta;
            } else if (nodeType.startsWith("Neurite")) {
                lineColor = Color.gray;
            }
        }

        int totalRoiPoints = linkedPoints.size();
        int[] xPoints = new int[totalRoiPoints];
        int[] yPoints = new int[totalRoiPoints];
        for (int n = 0; n < totalRoiPoints; n++) {
            String[] linkedPt = linkedPoints.get(n);
            xPoints[n] = Integer.parseInt(linkedPt[1]);
            yPoints[n] = Integer.parseInt(linkedPt[2]);
            // add neuriteSynapseRoi
            if (!linkedPt[5].equals("0")) {
                if (linkedPt[0].contains(":Spine#")) {
                    ntNeuronNode spinNode = traceHelper.getSpineNode(linkedPt[0]);
                    if (spinNode != null) {
                        String spineName = spinNode.toString();
                        ArrayList<String[]> spinePoints = spinNode.getTracingResult();
                        int totalSpinePoints = spinePoints.size();
                        int[] xPts = new int[totalSpinePoints];
                        int[] yPs = new int[totalSpinePoints];
                        for (int i = 0; i < totalSpinePoints; i++) {
                            String[] spinePt = spinePoints.get(i);
                            xPts[i] = Integer.parseInt(spinePt[1]);
                            yPs[i] = Integer.parseInt(spinePt[2]);
                        }
                        PolygonRoi spineRoi = new PolygonRoi(xPts, yPs, totalSpinePoints, Roi.POLYLINE);
                        spineRoi.setName(spineName);
                        spineRoi.setStrokeColor(lineColor);
                        spineRoi.setStrokeWidth(spineLine);
                        spineRoi.setPosition(0, 0, frameNumber);
                        // add spineRoi to neuriteSpineOL,
                        neuriteSpineOL.add(spineRoi);
                    }
                } else {
                    OvalRoi synapseRoi = new OvalRoi(
                            (double) xPoints[n] - synapseRadius,
                            (double) yPoints[n] - synapseRadius, synapseSize, synapseSize);
                    synapseRoi.setPosition(0, 0, frameNumber);
                    synapseRoi.setStrokeWidth(synapseRadius);
                    //synapseRoi.setStrokeColor(lineColor);
                    synapseRoi.setStrokeColor(mapSynapseColor(linkedPt[5]));
                    neuriteSynapseOL.add(synapseRoi);
                }

                if (!linkedPt[6].equals("0")) {
                    OvalRoi connectedRoi = new OvalRoi(
                            (double) xPoints[n] - synapseRadius,
                            (double) yPoints[n] - synapseRadius, synapseSize, synapseSize);
                    connectedRoi.setPosition(0, 0, frameNumber);
                    connectedRoi.setStrokeWidth(synapseRadius);
                    if (uniqueSelected) {
                        String connected = linkedPt[6].split("#")[1];
                        connectedRoi.setStrokeColor(getNeuronColorFromNode(getNodeFromNeuronTreeByNodeName(connected), connectionAlpha));
                    } else {
                        connectedRoi.setStrokeColor(connectionColor);
                    }
                    neuriteConnectedOL.add(connectedRoi);
                }
            }
        }
        PolygonRoi pointTraceRoi = new PolygonRoi(xPoints, yPoints, totalRoiPoints, Roi.POLYLINE);
        pointTraceRoi.setName(neuriteName);
        pointTraceRoi.setStrokeColor(lineColor);
        pointTraceRoi.setStrokeWidth(lineWidth);
        pointTraceRoi.setPosition(0, 0, frameNumber);
        // add neuriteTraceRoi
        neuriteTraceOL.add(pointTraceRoi);
        // add neuriteNameRoi
        TextRoi nameRoi = new TextRoi(xPoints[(int) (totalRoiPoints / 2)] - nameRoiXoffset,
                yPoints[(int) (totalRoiPoints / 2)] - nameRoiYoffset, neuriteName);
        //TextRoi nameRoi = new TextRoi(pointTraceRoi.getBounds().getCenterX(),
        //        pointTraceRoi.getBounds().getCenterY(), neuriteName);
        nameRoi.setName(neuriteName);
        nameRoi.setStrokeColor(lineColor);
        nameRoi.setPosition(0, 0, frameNumber);
        neuriteNameOL.add(nameRoi);
    }

    private static Color mapSynapseColor(String type) {
        return mapSynapseColor((int) Integer.valueOf(type));
    }

    private static Color mapSynapseColor(int type) {
        switch (type) {
            case 0:
                return Color.black; // this shouldn't get called.
            case 1:
                return Color.red;
            case 2:
                return Color.green;
            case 3:
                return Color.blue;
            case 4:
                return Color.magenta;
        }

        return Color.white;
    }

    private void getAllChildNodeRoiExtPt(Overlay[] neuriteTraceOL, Overlay[] neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
            Overlay[] neuronSpineOL, ntNeuronNode currentNode, Color lineColor, float lineWidth, float spineLine, double synapseRadius, double synapseSize, int extDispPts) {
        for (int k = 0; k < currentNode.getChildCount(); k++) {
            ntNeuronNode childNode = (ntNeuronNode) (currentNode.getChildAt(k));
            getOneBranchTraceRoiExtPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuronSpineOL,
                    childNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize, extDispPts);
            getAllChildNodeRoiExtPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuronSpineOL,
                    childNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize, extDispPts);
        }
    }

    private void getAllChildNodeRoiAllPt(Overlay neuriteTraceOL, Overlay neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
            Overlay neuriteSpineOL, ntNeuronNode currentNode, Color lineColor, float lineWidth, float spineLine, double synapseRadius, double synapseSize) {
        for (int k = 0; k < currentNode.getChildCount(); k++) {
            ntNeuronNode childNode = (ntNeuronNode) (currentNode.getChildAt(k));
            getOneBranchTraceRoiAllPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL, childNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize);
            getAllChildNodeRoiAllPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL, childNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize);
        }
    }

    private void getOneWholeArborRoi(Overlay[] neuriteTraceOL, Overlay[] neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
            Overlay[] neuriteSpineOL, ntNeuronNode arborNode, float lineWidth, float spineLine, double synapseRadius, double synapseSize, int extDispPts) {
        // overlay all neurite traces of one whole arbor
        Color lineColor = getNeuronColorFromNode(arborNode, lineAlpha);
        getOneBranchTraceRoiExtPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL, arborNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize, extDispPts);
        if (arborNode.getChildCount() > 0) {
            //retrieve everage color of neuron from primary axon/dendrite branch
            getAllChildNodeRoiExtPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL,
                    arborNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize, extDispPts);
        }
    }

    private void getOneWholeArborRoi(Overlay neuriteTraceOL, Overlay neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
            Overlay neuriteSpineOL, ntNeuronNode arborNode, float lineWidth, float spineLine, double synapseRadius, double synapseSize) {
        // overlay all neurite traces of one whole arbor
        Color lineColor = getNeuronColorFromNode(arborNode, lineAlpha);
        getOneBranchTraceRoiAllPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL, arborNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize);
        if (arborNode.getChildCount() > 0) {
            //retrieve everage color of neuron from primary axon/dendrite branch
            getAllChildNodeRoiAllPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL, arborNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize);
        }
    }

    private void getOneWholeNeuronRoiExtPt(Overlay[] neuriteTraceOL, Overlay[] neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
            Overlay[] neuronSpineOL, ntNeuronNode neuronSomaNode, float lineWidth, float spineLine, double synapseRadius, double synapseSize, int extDispPts) {
        // overlay all axon/dendrite traces (neuronNode is the soma node)
        if (neuronSomaNode.getChildCount() > 0) {
            //retrieve everage color of neuron from primary axon/dendrite branch
            Color lineColor = getNeuronColorFromNode(neuronSomaNode, lineAlpha);
            if (!brainbowColor_jCheckBox.isSelected()) {
                lineColor = Color.black;
            }
            getAllChildNodeRoiExtPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuronSpineOL,
                    neuronSomaNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize, extDispPts);
        }
    }

    private void getOneWholeNeuronRoiAllPt(Overlay neuriteTraceOL, Overlay neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
            Overlay neuriteSpineOL, ntNeuronNode neuronSomaNode, float lineWidth, float spineLine, double synapseRadius, double synapseSize) {
        // overlay all axon/dendrite traces (neuronNode is the soma node)
        if (neuronSomaNode.getChildCount() > 0) {
            //retrieve everage color of neuron from primary axon/dendrite branch
            Color lineColor = getNeuronColorFromNode(neuronSomaNode, lineAlpha);
            if (!brainbowColor_jCheckBox.isSelected()) {
                lineColor = Color.black;
            }
            getAllChildNodeRoiAllPt(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL, neuronSomaNode, lineColor, lineWidth, spineLine, synapseRadius, synapseSize);
        }
    }

    private void getSomaSliceRoi(Overlay[] somaTraceOL, Overlay[] somaNameOL, Overlay somaSynapseOL, Overlay somaConnectedOL, Overlay[] somaSpineOL,
            ntNeuronNode somaSliceNode, Color lineColor, float somaLineWidth, float spineLineWidth, double synapseRadius, double synapseSize, boolean singleSliceSynapse) {
        //OvalRoi.setColor(synapseColor);
        OvalRoi.setColor(lineColor);
        int frameNumber = imp.getT();
        String somaSliceName = somaSliceNode.toString();
        boolean uniqueSelected = false;
        if (neuronList_jTree.getSelectionCount() == 1) {
            ntNeuronNode selectedNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
            if (somaSliceNode.getNeuronNumber().equals(selectedNode.getNeuronNumber())) {
                uniqueSelected = true;
            }
        }
        ArrayList<String[]> linkedPoints = somaSliceNode.getTracingResult();
        int zPosition = Integer.parseInt(linkedPoints.get(0)[3]);
        int totalRoiPoints = linkedPoints.size();
        int[] xPoints = new int[totalRoiPoints];
        int[] yPoints = new int[totalRoiPoints];
        for (int n = 0; n < totalRoiPoints; n++) {
            String[] linkedPt = linkedPoints.get(n);
            xPoints[n] = Integer.parseInt(linkedPt[1]);
            yPoints[n] = Integer.parseInt(linkedPt[2]);
            // add somaSynapseRoi
            if (!linkedPt[5].equals("0")) {
                if (linkedPt[0].contains(":Spine#")) {
                    // add spine Roi into overlay
                    ntNeuronNode spineNode = traceHelper.getSpineNode(linkedPt[0]);
                    ArrayList<String[]> spinePoints = spineNode.getTracingResult();
                    int totalSpinePoints = spinePoints.size();
                    xPoints = new int[2];
                    yPoints = new int[2];
                    for (int i = 0; i < totalSpinePoints - 1; i++) {
                        xPoints[0] = Integer.parseInt(spinePoints.get(i)[1]);
                        yPoints[0] = Integer.parseInt(spinePoints.get(i)[2]);
                        xPoints[1] = Integer.parseInt(spinePoints.get(i + 1)[1]);
                        yPoints[1] = Integer.parseInt(spinePoints.get(i + 1)[2]);
                        PolygonRoi spineRoi = new PolygonRoi(xPoints, yPoints, 2, Roi.POLYLINE);
                        spineRoi.setStrokeColor(lineColor);
                        spineRoi.setStrokeWidth(spineLineWidth);
                        spineRoi.setPosition(0, zPosition, frameNumber);
                        spineRoi.setName(linkedPt[0]);
                        somaSpineOL[zPosition - 1].add(spineRoi);
                    }
                } else {
                    OvalRoi synapseRoi = new OvalRoi(
                            xPoints[n] - synapseRadius,
                            yPoints[n] - synapseRadius, synapseSize, synapseSize);
                    synapseRoi.setStrokeWidth(synapseRadius);
                    if (singleSliceSynapse) {
                        synapseRoi.setPosition(0, zPosition, frameNumber);
                    }
                    somaSynapseOL.add(synapseRoi);
                }
                if (!linkedPt[6].equals("0")) {
                    OvalRoi connectedRoi = new OvalRoi(
                            xPoints[n] - synapseRadius,
                            yPoints[n] - synapseRadius, synapseSize, synapseSize);
                    connectedRoi.setPosition(0, 0, frameNumber);
                    connectedRoi.setStrokeWidth(synapseRadius);
                    if (uniqueSelected) {
                        String connected = linkedPt[6].split("#")[1];
                        connectedRoi.setStrokeColor(getNeuronColorFromNode(getNodeFromNeuronTreeByNodeName(connected), connectionAlpha));
                    } else {
                        connectedRoi.setStrokeColor(connectionColor);
                    }
                    if (singleSliceSynapse) {
                        connectedRoi.setPosition(0, zPosition, frameNumber);
                    }
                    somaConnectedOL.add(connectedRoi);
                }

            }
        }

        PolygonRoi pointTraceRoi = new PolygonRoi(xPoints, yPoints, totalRoiPoints, Roi.POLYLINE);
        pointTraceRoi.setName(somaSliceName);
        pointTraceRoi.setPosition(0, zPosition, frameNumber);
        pointTraceRoi.setStrokeColor(lineColor);
        pointTraceRoi.setStrokeWidth(somaLineWidth);
        // add somaTraceRoi
        somaTraceOL[zPosition - 1].add(pointTraceRoi);
        // add somaNameRoi
        TextRoi nameRoi = new TextRoi(pointTraceRoi.getBounds().getCenterX(),
                pointTraceRoi.getBounds().getCenterY(), somaSliceName);
        nameRoi.setName(somaSliceName);
        nameRoi.setStrokeColor(lineColor);
        nameRoi.setPosition(0, zPosition, frameNumber);
        somaNameOL[zPosition - 1].add(nameRoi);
    }

    private void getOneWholeSomaRoi(Overlay[] somaTraceOL, Overlay[] somaNameOL, Overlay somaSynapseOL, Overlay somaConnectedOL, Overlay[] somaSpineOL,
            ntNeuronNode neuronNode, float somaLine, float spineLine, double synapseRadius, double synapseSize, boolean singleSliceSynapse) {
        ntNeuronNode somaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(neuronNode.getNeuronNumber());
        //IJ.log("show soma "+somaNode.toString());
        if (somaNode.getChildCount() > 0) {
            //retrieve everage color of neuron from primary axon/dendrite branch
            Color lineColor = getNeuronColorFromNode(neuronNode, lineAlpha);
            if (!brainbowColor_jCheckBox.isSelected()) {
                lineColor = Color.lightGray;
            }
            for (int n = 0; n < somaNode.getChildCount(); n++) {
                ntNeuronNode somaSlice = (ntNeuronNode) somaNode.getChildAt(n);
                getSomaSliceRoi(somaTraceOL, somaNameOL, somaSynapseOL, somaConnectedOL, somaSpineOL,
                        somaSlice, lineColor, somaLine, spineLine, synapseRadius, synapseSize, singleSliceSynapse);
            }
        }
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="methods for tracing">
    /**
     * Method to clear start and/or end points.
     */
    protected void clearStartEndPts() {
        if (pointTable_jTable.getSelectedRows().length != 1) {
            startPoint = new int[7];
            hasStartPt = false;
            startPosition_jLabel.setText("     ");
            startIntensity_jLabel.setText("     ");
            startColor_jLabel.setText("     ");
        }
        endPoint = new int[7];
        hasEndPt = false;
        endPosition_jLabel.setText("     ");
        endIntensity_jLabel.setText("     ");
        endColor_jLabel.setText("     ");
        updateInfo(defaultInfo);
        updatePointBox();
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="methods for addion/deletion of manual tracing results">
    
    //incluedes updatePointTable function in update class ad other functions in update class
    
    /**
     * returns the soma node from the soma tree that corresponds to Neuron Number
     * The input parameter must NOT contain ':' nor '-', otherwise, null will be returned
     * returns null is no such soma exists
     * @param NeuronNumber
     * @return ntNeuronNode
     */
    
    public static ntNeuronNode getSomaNodeFromAllSomaTreeByNeuronNumber(String NeuronNumber) {
        if (NeuronNumber.contains(":") || NeuronNumber.contains("-")) {
            IJ.error("Input parameter must be soma name that does NOT contain ':' nor '-' !");
            return null;
        } else {
            for (int i = 0; i < rootAllSomaNode.getChildCount(); i++) {
                ntNeuronNode compareNode = (ntNeuronNode) rootAllSomaNode.getChildAt(i);
                String compareNeuronNumber = compareNode.toString();
                if (compareNeuronNumber.contains("/")) {
                    compareNeuronNumber = compareNeuronNumber.split("/")[0];
                }
                if (NeuronNumber.equals(compareNeuronNumber)) {
                    return compareNode;
                }
            }
            return null;
        }
    }

    /**
     * returns the soma node in the neuron tree that somaName refers to
     * If no such soma node exists, return null
     * @param somaName
     * @return 
     */
    
    protected static ntNeuronNode getSomaNodeFromNeuronTreeByNeuronNumber(String somaName) {
        for (int i = 0; i < rootNeuronNode.getChildCount(); i++) {
            ntNeuronNode compareNode = (ntNeuronNode) rootNeuronNode.getChildAt(i);
            String compareName = compareNode.toString();
            if (compareName.contains("/")) {
                compareName = compareName.split("/")[0];
            }
            if (somaName.equals(compareName)) {
                return compareNode;
            }
        }
        return null;
    }

    private ntNeuronNode getSomaSliceNodeFromAllSomaTreeBySomaSliceName(String somaSliceName) {
        if (somaSliceName.contains(":")) {
            String[] names = somaSliceName.split(":");
            ntNeuronNode somaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(names[0]);
            for (int i = 0; i < somaNode.getChildCount(); i++) {
                ntNeuronNode compareNode = (ntNeuronNode) somaNode.getChildAt(i);
                if (somaSliceName.equals(compareNode.toString())) {
                    return compareNode;
                }
            }
            return null;
        } else {
            IJ.error("Input parameter must be soma slice name that contains ':' !");
            return null;
        }
    }

    /**
     * returns the "nodeName" node in the neuron tree
     * Different implementations will be run depending on whether the node is a neuron root, branch, or soma slice
     * @param nodeName
     * @return 
     */
    
    protected ntNeuronNode getNodeFromNeuronTreeByNodeName(String nodeName) {
        ntNeuronNode node;
        if (nodeName.contains("/")) { // selected neuron root
            node = getSomaNodeFromNeuronTreeByNeuronNumber(nodeName.split("/")[0]);
        } else if (nodeName.contains("-")) { // selected branch
            String[] names = nodeName.split("-");
            node = getSomaNodeFromNeuronTreeByNeuronNumber(names[0]);
            if (node == null) {
                return null;
            }
            for (int p = 0; p < node.getChildCount(); p++) {
                ntNeuronNode primaryBranchNode = (ntNeuronNode) node.getChildAt(p);
                if ((names[0] + "-" + names[1]).equals(primaryBranchNode.toString())) {
                    node = primaryBranchNode;
                    break;
                }
            }
            for (int i = 2; i < names.length; i++) {
                if (node.getChildCount() >= Integer.parseInt(names[i])) {
                    node = (ntNeuronNode) node.getChildAt(Integer.parseInt(names[i]) - 1);
                } else {
                    return null;
                }
            }
        } else { // selected soma slice
            node = getSomaNodeFromNeuronTreeByNeuronNumber(nodeName);
        }
        return node;
    }

    /**
     * return the "nodeName" tracing node
     * calls getNodeFromNeuronTreeByNodeName(nodeName) if it a branch node
     * calls getSomaSliceNodeFromAllSomaTreeBySomaSliceName(nodeName) if it is a soma slice node
     * returns null if it is a trunck node, soma node, or not a node at all
     * @param nodeName
     * @return 
     */
    
    protected ntNeuronNode getTracingNodeByNodeName(String nodeName) {
        if (nodeName.contains("/")) { // a trunck node
            return null;
        } else if (nodeName.contains("-")) { // a branch node
            return getNodeFromNeuronTreeByNodeName(nodeName);
        } else if (nodeName.contains(":")) { // a soma slice node
            return getSomaSliceNodeFromAllSomaTreeBySomaSliceName(nodeName);
        } else { // a soma node or not a node
            return null;
        }
    }

    private ntNeuronNode getTracingNodeFromPointTableSelection() {
        if (pointTable_jTable.getSelectedRowCount() < 1) {
            IJ.error("At least one row needs to be selected in Point Table!");
            return null;
        }
        ntNeuronNode selectedNode = (ntNeuronNode) neuronList_jTree.getSelectionPath().getLastPathComponent();
        if (selectedNode.isTrunckNode()) { // soma node is selected
            String selectedSomaSliceNodeName = ((ntNeuronNode) displaySomaList_jTree.getLastSelectedPathComponent()).toString();
            selectedNode = getSomaSliceNodeFromAllSomaTreeBySomaSliceName(selectedSomaSliceNodeName);
        }
        return selectedNode;
    }

    /**
     * Add child to the currently selected node.
     */
    private void deletePointsFromNodeByPosition(ntNeuronNode node, int[] deletePositions) {
        ArrayList<String[]> selectedNodeTracingResult = node.getTracingResult();
        String selectedNodeName = node.toString();
        if (deletePositions.length > selectedNodeTracingResult.size()) {
            IJ.error("Cannot delete more points than the tracing result has!");
            return;
        }

        // must remove points from the back of an ArrayList,
        // otherwise element order will change
        for (int i = deletePositions.length - 1; i >= 0; i--) {
            // determine whether a connection needs to be removed
            String selectedSynapseName = selectedNodeTracingResult.get(deletePositions[i])[6];
            if (!selectedSynapseName.equals("0")) {
                removeConnectionBySelectedNodeAndSynapseName(selectedNodeName, selectedSynapseName);
            }
            // determine whether a spine needs to be removed
            String spineTag = selectedNodeTracingResult.get(deletePositions[i])[0];
            if (spineTag.contains(":Spine#")) {
                traceHelper.removeSpine(spineTag);
                node.setSpine(i, "0");
            }
            selectedNodeTracingResult.remove(deletePositions[i]);
        }
        node.setTracingResult(selectedNodeTracingResult);
    }

    private void deleteSelectedPoints() {
        if (pointTable_jTable.getSelectedRowCount() >= 1) {
            // get selectedNode from pointTable selection
            ntNeuronNode selectedNode = getTracingNodeFromPointTableSelection();
            // get deletion positions in tracing result
            int[] deletePositions = pointTable_jTable.getSelectedRows();

            recordTreeExpansionSelectionStatus();

            // delete selected points
            deletePointsFromNodeByPosition(selectedNode, deletePositions);
            // remove selectedNode from rootNode if all tracing result points are deleted
            if (selectedNode.getTracingResult().size() < 1) {
                if (selectedNode.toString().contains(":")) {// selectedNode is a somaSlice node
                    allSomaTreeModel.removeNodeFromParent(selectedNode);
                } else {// selectedNode is a branch node
                    neuronTreeModel.removeNodeFromParent(selectedNode);
                }
            }

            updateTrees();
            restoreTreeExpansionSelectionStatus();
            pointTable_jTable.clearSelection();
            history.saveHistory();
            updateDisplay();
        }
    }

    private void deleteNeuronsFromNeuronTree() {
        // this is designed
        if (neuronList_jTree.getSelectionCount() >= 1) {
            ArrayList<String> delNeuronNumbers = getSelectedNeuronNumberSortSmall2Large();
            if (delNeuronNumbers.size() > 0) {
                recordTreeExpansionSelectionStatus();
                //for (String delNeuronSomaNode : delNeuronSomaNames){
                //    IJ.log("delete "+delNeuronSomaNode);
                //}
                //somehow MUST deselecte everyting in neuronList_jTree before deleting! 
                neuronList_jTree.clearSelection();
                // delete neuronNodes from large number to small number from neuronTreeModel and allSomaTreeModel
                for (int n = delNeuronNumbers.size() - 1; n >= 0; n--) {
                    deleteOneWholeNeuronByNumber(delNeuronNumbers.get(n));
                }

                updateTrees();
                restoreTreeExpansionSelectionStatus();
                history.saveHistory();
                updateDisplay();
            }
        }
    }

    private ArrayList<String> getSelectedNeuronNumberSortSmall2Large() {
        TreePath[] selectedPaths = neuronList_jTree.getSelectionPaths();
        ArrayList<String> selectedNeuronNumbers = new ArrayList<String>();
        ArrayList<String> sortedNeuronNumbers = new ArrayList<String>();
        if (selectedPaths != null) {
            // get all the selected node names
            for (TreePath selectedPath : selectedPaths) {
                String neuronNumber = ((ntNeuronNode) selectedPath.getPathComponent(1)).toString();
                if (neuronNumber.contains("/")) {
                    neuronNumber = neuronNumber.split("/")[0];
                }
                selectedNeuronNumbers.add(neuronNumber);
            }
            if (!selectedNeuronNumbers.isEmpty()) {
                // remove redundant neurons
                for (int i = selectedNeuronNumbers.size() - 1; i >= 0; i--) {
                    String selectedNumber = selectedNeuronNumbers.get(i);
                    for (int n = i - 1; n >= 0; n--) {
                        String compareNumber = selectedNeuronNumbers.get(n);
                        if (selectedNumber.equals(compareNumber)) {
                            selectedNeuronNumbers.remove(i);
                            break;
                        }
                    }
                }

                // sort neuronSomaNames from small to large
                for (int i = selectedNeuronNumbers.size() - 1; i >= 0; i--) {
                    int selectedNeuronNumber = Integer.parseInt(selectedNeuronNumbers.get(i));
                    int insertPosition = 0;
                    for (int n = sortedNeuronNumbers.size() - 1; n >= 0; n--) {
                        int compareNodeNumber = Integer.parseInt(sortedNeuronNumbers.get(n));
                        if (selectedNeuronNumber > compareNodeNumber) {
                            insertPosition = n + 1;
                            break;
                        }
                    }
                    sortedNeuronNumbers.add(insertPosition, selectedNeuronNumber + "");
                }
            }
        }
        return sortedNeuronNumbers;
    }

    private ArrayList<String> getSelectedPrimaryNodeName() {
        TreePath[] selectedPaths = neuronList_jTree.getSelectionPaths();
        ArrayList<String> selectedPrimaryNodeNames = new ArrayList<String>();
        // get all the selected node names
        for (TreePath selectedPath : selectedPaths) {
            if (selectedPath.getPathCount() > 2) {
                selectedPrimaryNodeNames.add(((ntNeuronNode) selectedPath.getPathComponent(2)).toString());
            }
        }
        // remove redundant neurons
        for (int i = selectedPrimaryNodeNames.size() - 1; i >= 0; i--) {
            String currentNodeName = selectedPrimaryNodeNames.get(i);
            for (int n = i - 1; n >= 0; n--) {
                String compareNodeName = selectedPrimaryNodeNames.get(n);
                if (currentNodeName.equals(compareNodeName)) {
                    selectedPrimaryNodeNames.remove(i);
                    break;
                }
            }
        }

        return selectedPrimaryNodeNames;
    }

    private void deleteOneWholeNeuronByNumber(String delNeuronNumber) {
        // delete somaNode from rootAllSomaNode
        for (int i = rootAllSomaNode.getChildCount() - 1; i >= 0; i--) {
            ntNeuronNode somaSomaNode = (ntNeuronNode) rootAllSomaNode.getChildAt(i);
            ntNeuronNode neuronSomaNode = (ntNeuronNode) rootNeuronNode.getChildAt(i);
            if (delNeuronNumber.equals(somaSomaNode.getNeuronNumber())) {
                //IJ.log("start deleting "+delNeuronSomaName);                        
                // delete all slices from somaSomaNode
                for (int s = somaSomaNode.getChildCount() - 1; s >= 0; s--) {
                    ntNeuronNode somaSliceNode = (ntNeuronNode) somaSomaNode.getChildAt(s);
                    deleteOneSomaSliceNodeByNode(somaSliceNode);
                    //IJ.log("deleted "+somaSliceNode.toString());
                }
                // delete somaSomaNode from allSomaTreeModel
                allSomaTreeModel.removeNodeFromParent(somaSomaNode);

                //IJ.log("removing branches from " + delNeuronSomaName);
                // delete all arbors from neuronSomaNode
                for (int a = neuronSomaNode.getChildCount() - 1; a >= 0; a--) {
                    ntNeuronNode primaryBranchNode = (ntNeuronNode) neuronSomaNode.getChildAt(a);
                    //IJ.log("deleting "+primaryBranchNode.toString());
                    traceHelper.deleteBranchAndChildNode(primaryBranchNode);
                    //IJ.log("deleted "+primaryBranchNode.toString());
                }
                // delete neuronSomaNode from neuronTreeModel
                neuronTreeModel.removeNodeFromParent(neuronSomaNode);
                break;
            }
        }
        //IJ.log("delete " + delNeuronSomaName);
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Debug">
    private void logAllChildNodeName(ntNeuronNode logNode) {
        for (int i = 0; i < logNode.getChildCount(); i++) {
            ntNeuronNode childNode = (ntNeuronNode) logNode.getChildAt(i);
            IJ.log(childNode.toString());
            if (childNode.getChildCount() > 0) {
                logAllChildNodeName(childNode);
            }
        }
    }

    private void logHistory() {
        // debug for tracking history record
        int[] historyStack = new int[10];
        int number = 0;
        for (int i = 0; i < historyStack.length; i++) {
            number++;
            int newNumber = number;
            historyStack[i] = newNumber;
        }
        for (int i = 0; i < historyStack.length; i++) {
            IJ.log("at " + i + " = " + historyStack[i]);
        }
    }
    // </editor-fold>

    /**
     * creates a new tTracer_() object that is visible
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new nTracer_().setVisible(true);
            }
        });
    }

    // <editor-fold defaultstate="collapsed" desc="GUI variables">
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addLabelToSelection_jButton;
    protected javax.swing.JSpinner allNeuronLineWidthOffset_jSpinner;
    private javax.swing.JLabel allPlusMinus_jLabel;
    private javax.swing.JPanel all_jPanel;
    protected javax.swing.JCheckBox analysisCh1_jCheckBox;
    protected javax.swing.JCheckBox analysisCh2_jCheckBox;
    protected javax.swing.JCheckBox analysisCh3_jCheckBox;
    protected javax.swing.JCheckBox analysisCh4_jCheckBox;
    protected javax.swing.JCheckBox analysisCh5_jCheckBox;
    protected javax.swing.JCheckBox analysisCh6_jCheckBox;
    protected javax.swing.JCheckBox analysisCh7_jCheckBox;
    protected javax.swing.JCheckBox analysisCh8_jCheckBox;
    private javax.swing.JLabel analysisChannel_jLabel;
    private javax.swing.JMenu analysis_jMenu;
    protected javax.swing.JSpinner arborLineWidth_jSpinner;
    protected javax.swing.JRadioButton autoTracing_jRadioButton;
    private javax.swing.JMenuItem autosaveSetup_jMenuItem;
    private javax.swing.JRadioButton b_jRadioButton;
    private javax.swing.JCheckBox blank_jCheckBox;
    private javax.swing.JCheckBox brainbowColor_jCheckBox;
    protected javax.swing.JSpinner branchLineWidth_jSpinner;
    private javax.swing.JButton breakBranch_jButton;
    private javax.swing.ButtonGroup channelColor_buttonGroup;
    private javax.swing.JPanel channel_jPanel;
    private javax.swing.JMenuItem clearData_jMenuItem;
    private javax.swing.JButton clearPoints_jButton;
    private javax.swing.JButton collapseAllNeuron_jButton;
    private javax.swing.JPanel colorSamplingRadius_jPanel;
    private javax.swing.JLabel colorThreshold_jLabel;
    protected javax.swing.JSpinner colorThreshold_jSpinner;
    private javax.swing.JButton comnibeTwoNeuron_jButton;
    private javax.swing.JButton completeSomaSliceRoi_jButton;
    private javax.swing.JLabel connectedSynapse_jLabel;
    private javax.swing.JButton copyNeuronTag_jButton;
    private javax.swing.JButton copyToEditTarget_jButton;
    private javax.swing.JMenuItem cropData_jMenuItem;
    protected javax.swing.JRadioButton cytoplasmLabel_jRadioButton;
    private javax.swing.JMenu data_jMenu;
    private javax.swing.JMenu debug_jMenu;
    private javax.swing.JMenuItem debug_jMenuItem;
    private javax.swing.JButton deleteOneBranch_jButton;
    private javax.swing.JButton deleteOneNeuron_jButton;
    private javax.swing.JButton deletePoints_jButton;
    private javax.swing.JButton deleteSomaSlice_jButton;
    private javax.swing.JPanel delete_jPanel;
    private javax.swing.JMenuItem deselectAllNeuon_jMenuItem;
    protected javax.swing.JTree displaySomaList_jTree;
    private javax.swing.JPanel editBranch_jPanel;
    private javax.swing.JPanel editConnection_jPanel;
    private javax.swing.JPanel editDisplay_jPanel;
    private javax.swing.JPanel editNeuron_jPanel;
    private javax.swing.JPanel editSoma_jPanel;
    private javax.swing.JPanel editSynapse_jPanel;
    private javax.swing.JLabel editTargetName_jLabel;
    protected javax.swing.JLabel endColor_jLabel;
    protected javax.swing.JLabel endIntensity_jLabel;
    protected javax.swing.JLabel endPosition_jLabel;
    private javax.swing.JLabel endPtCol_jLabel;
    private javax.swing.JLabel endPtInt_jLabel;
    private javax.swing.JLabel endPt_jLabel;
    private javax.swing.JButton expanAllNeuron_jButton;
    private javax.swing.JMenuItem exportSWCfromSelectedNeurons_jMenuItem;
    private javax.swing.JMenuItem exportSynapseFromSelectedNeurons_jMenuItem;
    protected javax.swing.JSpinner extendAllDisplayPoints_jSpinner;
    protected javax.swing.JSpinner extendSelectedDisplayPoints_jSpinner;
    private javax.swing.JRadioButton g_jRadioButton;
    private javax.swing.JButton gotoConnection_jButton;
    private javax.swing.JMenu help_jMenu;
    private javax.swing.JMenuItem help_jMenuItem;
    protected javax.swing.JLabel info_jLabel;
    private javax.swing.JLabel intensityThreshold_jLabel;
    protected javax.swing.JSpinner intensityThreshold_jSpinner;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JCheckBox jCheckBox9;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JButton joinBranches_jButton;
    private javax.swing.JButton jumpToNextConnected_jButton;
    private javax.swing.JButton jumpToNextIncompleted_jButton;
    private javax.swing.JButton jumpToNextSelected_jButton;
    private javax.swing.JButton jumpToNextSynapse_jButton;
    private javax.swing.JPanel jumpTo_jPanel;
    private javax.swing.ButtonGroup labelingMethod_buttonGroup;
    private javax.swing.JPanel labeling_jPanel;
    private javax.swing.JLabel lineWidthOffset_jLabel;
    private javax.swing.JLabel linkRadius_jLabel;
    private javax.swing.JSpinner linkRadius_jSpinner;
    private javax.swing.JMenuItem loadData_jMenuItem;
    private javax.swing.JMenuItem loadSkeletonized_jMenuItem;
    private javax.swing.JMenuItem logColorRatio_jMenuItem;
    private javax.swing.JMenuItem logNeuronConnection_jMenuItem;
    private javax.swing.JMenuItem logNormChIntensity_jMenuItem;
    private javax.swing.JMenuItem logSomaStatistics_jMenuItem;
    private javax.swing.JTabbedPane main_jTabbedPane;
    protected javax.swing.JRadioButton manualTracing_jRadioButton;
    protected javax.swing.JRadioButton membraneLabel_jRadioButton;
    private javax.swing.JMenu menu_jMenu;
    private javax.swing.JMenuBar menu_jMenuBar;
    private javax.swing.JMenu model3D_jMenu;
    private javax.swing.JMenu model3D_jMenu1;
    protected javax.swing.JSpinner neuronLineWidth_jSpinner;
    private javax.swing.JScrollPane neuronList_jScrollPane;
    protected javax.swing.JTree neuronList_jTree;
    private javax.swing.JLabel neuronTree_jLabel;
    protected javax.swing.JCheckBox overlayAllConnection_jCheckBox;
    protected javax.swing.JCheckBox overlayAllName_jCheckBox;
    protected javax.swing.JCheckBox overlayAllNeuron_jCheckBox;
    protected javax.swing.JCheckBox overlayAllPoints_jCheckBox;
    protected javax.swing.JCheckBox overlayAllSelectedPoints_jCheckBox;
    protected javax.swing.JCheckBox overlayAllSoma_jCheckBox;
    protected javax.swing.JCheckBox overlayAllSpine_jCheckBox;
    protected javax.swing.JCheckBox overlayAllSynapse_jCheckBox;
    protected javax.swing.JCheckBox overlayPointBox_jCheckBox;
    protected javax.swing.JCheckBox overlaySelectedArbor_jCheckBox;
    protected javax.swing.JCheckBox overlaySelectedBranch_jCheckBox;
    protected javax.swing.JCheckBox overlaySelectedConnection_jCheckBox;
    protected javax.swing.JCheckBox overlaySelectedName_jCheckBox;
    protected javax.swing.JCheckBox overlaySelectedNeuron_jCheckBox;
    protected javax.swing.JCheckBox overlaySelectedSoma_jCheckBox;
    protected javax.swing.JCheckBox overlaySelectedSpine_jCheckBox;
    protected javax.swing.JCheckBox overlaySelectedSynapse_jCheckBox;
    private javax.swing.JPanel overlay_jPanel;
    protected javax.swing.JSpinner pointBoxLineWidth_jSpinner;
    protected javax.swing.JSpinner pointBoxRadiu_jSpinner;
    private javax.swing.JScrollPane pointTable_jScrollPane;
    protected javax.swing.JTable pointTable_jTable;
    private javax.swing.JCheckBox projectionUpdate_jCheckBox;
    private javax.swing.JMenuItem quit_jMenuItem;
    private javax.swing.JRadioButton r_jRadioButton;
    private javax.swing.JMenuItem redo_jMenuItem;
    private javax.swing.JPanel samplingTolerance_jPanel;
    private javax.swing.JMenuItem saveData_jMenuItem;
    private javax.swing.JMenuItem selecAllNeuron_jMenuItem;
    private javax.swing.JTextField selectNeuronNumber_jTextField;
    private javax.swing.JButton selectNeurons_jButton;
    private javax.swing.JComboBox selectTagAdditionalCriteria_jComboBox;
    private javax.swing.JComboBox selectTagOperator_jComboBox;
    private javax.swing.JLabel selectedPlusMinus_jLabel;
    private javax.swing.JPanel selected_jPanel;
    private javax.swing.JTextField selectionTag_jTextField;
    protected javax.swing.JRadioButton semiAutoTracing_jRadioButton;
    private javax.swing.JButton setApicalDendrite_jButton;
    private javax.swing.JButton setAxon_jButton;
    private javax.swing.JButton setBasalDendrite_jButton;
    private javax.swing.JButton setNeurite_jButton;
    private javax.swing.JButton setPrimaryBranch_jButton;
    private javax.swing.JMenuItem setScale_jMenuItem;
    private javax.swing.JButton showConnectedNeurons_jButton;
    private javax.swing.ButtonGroup showConnected_buttonGroup;
    private javax.swing.JButton showSkeletonized_jButton;
    private javax.swing.JMenuItem skeleton_jMenuItem;
    private javax.swing.JCheckBox skeletonizedShowTracedPoints_jButton;
    protected javax.swing.JSpinner somaLineWidth_jSpinner;
    private javax.swing.JScrollPane somaList_jScrollPane;
    protected javax.swing.JSpinner spineLineWidth_jSpinner;
    private javax.swing.JLabel srtPtCol_jLabel;
    private javax.swing.JLabel srtPtInt_jLabel;
    private javax.swing.JLabel startColor_jLabel;
    private javax.swing.JLabel startIntensity_jLabel;
    private javax.swing.JLabel startPosition_jLabel;
    private javax.swing.JLabel startPt_jLabel;
    protected javax.swing.JSpinner synapseRadius_jSpinner;
    protected javax.swing.JCheckBox toggleCh1_jCheckBox;
    protected javax.swing.JCheckBox toggleCh2_jCheckBox;
    protected javax.swing.JCheckBox toggleCh3_jCheckBox;
    protected javax.swing.JCheckBox toggleCh4_jCheckBox;
    protected javax.swing.JCheckBox toggleCh5_jCheckBox;
    protected javax.swing.JCheckBox toggleCh6_jCheckBox;
    protected javax.swing.JCheckBox toggleCh7_jCheckBox;
    protected javax.swing.JCheckBox toggleCh8_jCheckBox;
    private javax.swing.JLabel toggleChannel_jLabel;
    private javax.swing.JLabel toggleColor_jLabel;
    private javax.swing.JButton toggleConnection_jButton;
    private javax.swing.JButton toggleSynapse_jButton;
    private javax.swing.JButton toggleSynapse_jButton1;
    private javax.swing.JButton toogleTracingCompleteness_jButton;
    private javax.swing.JButton traceNeurite_jButton;
    private javax.swing.JButton traceSoma_jButton;
    private javax.swing.JButton traceSpine_jButton;
    private javax.swing.ButtonGroup tracingMethod_buttonGroup;
    private javax.swing.JPanel tracingMethod_jPanel;
    private javax.swing.JPanel tracing_jPanel;
    private javax.swing.JMenuItem undo_jMenuItem;
    private javax.swing.JButton updateDisplay_jButton;
    private javax.swing.JMenuItem volume_jMenuItem;
    private javax.swing.JLabel xyProjectionArea_jLabel;
    private javax.swing.JSpinner xyProjectionArea_jSpinner;
    private javax.swing.JLabel xyRadius_jLabel;
    protected javax.swing.JSpinner xyRadius_jSpinner;
    private javax.swing.JMenuItem xyzResolutions_jMenuItem;
    private javax.swing.JLabel zProjectionInterval_jLabel;
    private javax.swing.JPanel zProjectionInterval_jPanel;
    private javax.swing.JSpinner zProjectionInterval_jSpinner;
    private javax.swing.JLabel zRadius_jLabel;
    protected javax.swing.JSpinner zRadius_jSpinner;
    // End of variables declaration//GEN-END:variables
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="data variables">
    public static double[] xyzResolutions = new double[3];
    private final ntToolbar ntToolTrace = new ntToolbar(1, "Trace"); //new ntToolbar(1, "Trace Neurite");
    //private final ntToolbar ntToolSoma = new ntToolbar(2, "Soma");   //new ntToolbar(2, "Soma");
    //public static int somaChannel = 0;     // image channel that contains soma stain
    private final Color connectionColor = Color.green;
    //private HashMap somaROIs = new HashMap();
    public static String toggleColor;
    protected final ntIO IO;
    private final ntAnalysis analysis;
    protected ntTracing Functions;
    protected History history;
    protected Update update;
    protected TraceHelper traceHelper;
    protected DataHelper dataHelper;
    protected ntDataHandler dataHandler;
    protected static ImagePlus imp, impZproj;
    protected static ImagePlus impSkeletonized = null;
    protected static ImagePlus impSkeletonizedSource = null;
    protected static ImagePlus impSkeletonizedProjection = null;
    protected CompositeImage cmp;
    protected ImageCanvas cns, cnsZproj;
    public static ImageStack stk;
    private ImageWindow win, winZproj;
    public static boolean[] activeChannels;
    public static boolean[] toggleChannels;
    public static boolean[] analysisChannels;
    public static int impNChannel;
    protected int impWidth, impHeight, impNSlice, impNFrame;
    protected int crossX, crossY, crossZ, roiXmin, roiYmin, zProjInterval, zProjXY;
    protected String editTargetNodeName = "0";
    protected ArrayList<String[]> tablePoints;
    protected int[] startPoint, endPoint;
    protected boolean hasStartPt = false, hasEndPt = false;
    private String colorInfo;
    protected final int maskRadius = 1;
    private float[] ptIntColor;
    private final Overlay displayOL = new Overlay();
    protected final Overlay allNeuronTraceOL = new Overlay();
    protected final Overlay allNeuronNameOL = new Overlay();
    protected final Overlay allNeuronSynapseOL = new Overlay();
    protected final Overlay allNeuronConnectedOL = new Overlay();
    protected final Overlay allNeuronSpineOL = new Overlay();
    protected final Overlay selectedNeuronTraceOL = new Overlay();
    protected final Overlay selectedNeuronNameOL = new Overlay();
    protected final Overlay selectedNeuronSynapseOL = new Overlay();
    protected final Overlay selectedNeuronConnectedOL = new Overlay();
    protected final Overlay selectedNeuronSpineOL = new Overlay();
    protected final Overlay selectedArborTraceOL = new Overlay();
    protected final Overlay selectedArborNameOL = new Overlay();
    protected final Overlay selectedArborSynapseOL = new Overlay();
    protected final Overlay selectedArborConnectedOL = new Overlay();
    protected final Overlay selectedArborSpineOL = new Overlay();
    protected final Overlay selectedBranchTraceOL = new Overlay();
    protected final Overlay selectedBranchNameOL = new Overlay();
    protected final Overlay selectedBranchSynapseOL = new Overlay();
    protected final Overlay selectedBranchConnectedOL = new Overlay();
    protected final Overlay selectedBranchSpineOL = new Overlay();
    protected final Overlay allSomaSynapseOL = new Overlay();
    protected final Overlay allSomaConnectedOL = new Overlay();
    protected final Overlay selectedSomaSynapseOL = new Overlay();
    protected final Overlay selectedSomaConnectedOL = new Overlay();
    protected Overlay[] allNeuronTraceOLextPt;
    protected Overlay[] allNeuronNameOLextPt;
    protected Overlay[] allNeuronSpineOLextPt;
    protected Overlay[] selectedNeuronTraceOLextPt;
    protected Overlay[] selectedNeuronNameOLextPt;
    protected Overlay[] selectedNeuronSpineOLextPt;
    protected Overlay[] selectedArborTraceOLextPt;
    protected Overlay[] selectedArborNameOLextPt;
    protected Overlay[] selectedArborSpineOLextPt;
    protected Overlay[] selectedBranchTraceOLextPt;
    protected Overlay[] selectedBranchNameOLextPt;
    protected Overlay[] selectedBranchSpineOLextPt;
    protected Overlay[] allSomaTraceOL;
    protected Overlay[] allSomaNameOL;
    protected Overlay[] allSomaSpineOLextPt;
    protected Overlay[] selectedSomaTraceOL;
    protected Overlay[] selectedSomaNameOL;
    protected Overlay[] selectedSomaSpineOLextPt;

    private final int roiSearchRange = 8;
    private Line xyHL, xyVL;
    private Roi startBoxXY, endBoxXY;
    protected final String[] pointColumnNames = {"Type", "X", "Y", "Z", "Radius", "Synapse?", "Connection"};
    protected DefaultTableModel pointTableModel;
    private ntPointSelectionListener pointSelectionListener;
//    private ntPointTableModelListener pointTableModelListener;
    public static ntNeuronNode rootNeuronNode, rootAllSomaNode, rootDisplaySomaNode, rootSpineNode;
    protected DefaultTreeModel neuronTreeModel, allSomaTreeModel, displaySomaTreeModel, spineTreeModel;
    private JTree spineList_jTree;
    private ntNeuriteTreeSelectionListener neuriteTreeSlectionListener;
    private ntNeuriteTreeExpansionListener neuriteTreeExpansionListener;
    private ntSomaTreeSelectionListener somaTreeSlectionListener;
    protected float colorThreshold;
    protected float intensityThreshold;
    protected int xyRadius, zRadius, outLinkXYradius;
    protected int extendDisplayPoints;
    protected float lineWidthOffset, allSomaLine, somaLine, allNeuronLine, neuronLine, arborLine, branchLine, allSpineLine, spineLine, pointBoxLine;
    protected int pointBoxRadius;
    protected double allSynapseRadius, synapseRadius, allSynapseSize, synapseSize;// = synapseRadius*2+1  
    private final float lineAlpha = 0.5f, connectionAlpha = 1.0f;
    //private boolean manualTrace = true, semiAutoTrace = false, autoTrace = false;
    protected final int xyExtension = 21, zExtension = 7;
    private final double rho = 0.2d;
    private final String defaultInfo = "Information";
    protected final String endPtTooFarError = "No path found. Pick a closer END point!";
    protected final String pickNextEndPt = "Pick next END point to continue tracing!";
    protected final ArrayList<String> expandedNeuronNames = new ArrayList<String>();
    protected final ArrayList<String> selectedNeuronNames = new ArrayList<String>();
    protected final ArrayList<String> selectedSomaSliceNames = new ArrayList<String>();
    protected final ArrayList<Integer> selectedTableRows = new ArrayList<Integer>();
    private Rectangle neuronTreeVisibleRect, displaySomaTreeVisibleRect, pointTableVisibleRect;
    private String tempFolderDirectory;
    private final static int LEFT_BUTTON = 1;
    private final static int WHEEL_BUTTON = 2;
    private final static int RIGHT_BUTTON = 3;
    private final int processorNum = Runtime.getRuntime().availableProcessors();
    private final int threadNum = processorNum - 1;
    protected boolean canUpdateDisplay = true;
    private final int nameRoiXoffset = 0;
    private final int nameRoiYoffset = 0;

    // new display
    private HashMap<String, Roi>[] somaRoiHashMap, synapseRoiHashMap,
            neuriteRoiHashMap, connectionRoiHashMap;

    // </editor-fold>
    // </editor-fold>
    // </editor-fold>
    // </editor-fold>
    
    /**
     * updates the display
     */
    
    protected void updateDisplay() {
        //IJ.log("update");
        if (imp != null) {
            //long startTime = System.currentTimeMillis();
            allNeuronTraceOL.clear();
            allNeuronNameOL.clear();
            allNeuronSynapseOL.clear();
            allNeuronConnectedOL.clear();
            allNeuronSpineOL.clear();
            selectedNeuronTraceOL.clear();
            selectedNeuronNameOL.clear();
            selectedNeuronSynapseOL.clear();
            selectedNeuronConnectedOL.clear();
            selectedNeuronSpineOL.clear();
            selectedArborTraceOL.clear();
            selectedArborNameOL.clear();
            selectedArborSynapseOL.clear();
            selectedArborConnectedOL.clear();
            selectedArborSpineOL.clear();
            selectedBranchTraceOL.clear();
            selectedBranchNameOL.clear();
            selectedBranchSynapseOL.clear();
            selectedBranchConnectedOL.clear();
            selectedBranchSpineOL.clear();
            allSomaSynapseOL.clear();
            allSomaConnectedOL.clear();
            selectedSomaSynapseOL.clear();
            selectedSomaConnectedOL.clear();

            allNeuronTraceOLextPt = new Overlay[impNSlice];
            allNeuronNameOLextPt = new Overlay[impNSlice];
            allNeuronSpineOLextPt = new Overlay[impNSlice];
            selectedNeuronTraceOLextPt = new Overlay[impNSlice];
            selectedNeuronNameOLextPt = new Overlay[impNSlice];
            selectedNeuronSpineOLextPt = new Overlay[impNSlice];
            selectedArborTraceOLextPt = new Overlay[impNSlice];
            selectedArborNameOLextPt = new Overlay[impNSlice];
            selectedArborSpineOLextPt = new Overlay[impNSlice];
            selectedBranchTraceOLextPt = new Overlay[impNSlice];
            selectedBranchNameOLextPt = new Overlay[impNSlice];
            selectedBranchSpineOLextPt = new Overlay[impNSlice];
            allSomaTraceOL = new Overlay[impNSlice];
            allSomaNameOL = new Overlay[impNSlice];
            allSomaSpineOLextPt = new Overlay[impNSlice];
            selectedSomaTraceOL = new Overlay[impNSlice];
            selectedSomaNameOL = new Overlay[impNSlice];
            selectedSomaSpineOLextPt = new Overlay[impNSlice];

            for (int i = 0; i < impNSlice; i++) {
                allNeuronTraceOLextPt[i] = new Overlay();
                allNeuronNameOLextPt[i] = new Overlay();
                allNeuronSpineOLextPt[i] = new Overlay();
                selectedNeuronTraceOLextPt[i] = new Overlay();
                selectedNeuronNameOLextPt[i] = new Overlay();
                selectedNeuronSpineOLextPt[i] = new Overlay();
                selectedArborTraceOLextPt[i] = new Overlay();
                selectedArborNameOLextPt[i] = new Overlay();
                selectedArborSpineOLextPt[i] = new Overlay();
                selectedBranchTraceOLextPt[i] = new Overlay();
                selectedBranchNameOLextPt[i] = new Overlay();
                selectedBranchSpineOLextPt[i] = new Overlay();
                allSomaTraceOL[i] = new Overlay();
                allSomaNameOL[i] = new Overlay();
                allSomaSpineOLextPt[i] = new Overlay();
                selectedSomaTraceOL[i] = new Overlay();
                selectedSomaNameOL[i] = new Overlay();
                selectedSomaSpineOLextPt[i] = new Overlay();
            }

            // overlay all neurons            
            if (overlayAllNeuron_jCheckBox.isSelected()
                    || overlayAllSynapse_jCheckBox.isSelected()) {
                if (overlayAllPoints_jCheckBox.isSelected()) {
                    getAllNeuronAndNameOLMultiThread(allNeuronTraceOL,
                            allNeuronNameOL, allNeuronSynapseOL, allNeuronConnectedOL, allNeuronSpineOL);
                } else {
                    getAllNeuronAndNameOLextPtMultiThread(allNeuronTraceOLextPt,
                            allNeuronNameOLextPt, allNeuronSynapseOL, allNeuronConnectedOL, allNeuronSpineOLextPt, extendDisplayPoints);
                    for (int j = 0; j < impNSlice; j++) {
                        if (allNeuronTraceOLextPt[j] != null) {
                            for (int n = 0; n < allNeuronTraceOLextPt[j].size(); n++) {
                                allNeuronTraceOL.add(allNeuronTraceOLextPt[j].get(n));
                            }
                        }
                        if (allNeuronNameOLextPt[j] != null) {
                            for (int n = 0; n < allNeuronNameOLextPt[j].size(); n++) {
                                allNeuronNameOL.add(allNeuronNameOLextPt[j].get(n));
                            }
                        }
                        if (allNeuronSpineOLextPt[j] != null) {
                            for (int n = 0; n < allNeuronSpineOLextPt[j].size(); n++) {
                                allNeuronSpineOL.add(allNeuronSpineOLextPt[j].get(n));
                            }
                        }
                    }
                }
            }

            // overlay selected neuron(s)
            if (overlaySelectedNeuron_jCheckBox.isSelected()
                    || overlaySelectedSynapse_jCheckBox.isSelected()) {
                if (overlayAllSelectedPoints_jCheckBox.isSelected()) {
                    getSelectedNeuronAndNameOLMultiThread(selectedNeuronTraceOL,
                            selectedNeuronNameOL, selectedNeuronSynapseOL, selectedNeuronConnectedOL, selectedNeuronSpineOL);
                } else {
                    getSelectedNeuronAndNameOLextPtMultiThread(selectedNeuronTraceOLextPt,
                            selectedNeuronNameOLextPt, selectedNeuronSynapseOL, selectedNeuronConnectedOL, selectedNeuronSpineOLextPt, extendDisplayPoints);
                    for (int j = 0; j < impNSlice; j++) {
                        if (selectedNeuronTraceOLextPt[j] != null) {
                            for (int n = 0; n < selectedNeuronTraceOLextPt[j].size(); n++) {
                                selectedNeuronTraceOL.add(selectedNeuronTraceOLextPt[j].get(n));
                            }
                        }
                        if (selectedNeuronNameOLextPt[j] != null) {
                            for (int n = 0; n < selectedNeuronNameOLextPt[j].size(); n++) {
                                selectedNeuronNameOL.add(selectedNeuronNameOLextPt[j].get(n));
                            }
                        }
                        if (selectedNeuronSpineOLextPt[j] != null) {
                            for (int n = 0; n < selectedNeuronSpineOLextPt[j].size(); n++) {
                                selectedNeuronSpineOL.add(selectedNeuronSpineOLextPt[j].get(n));
                            }
                        }
                    }
                }
            }

            // overlay selected arbor(s)
            if (overlaySelectedArbor_jCheckBox.isSelected()
                    || overlaySelectedSynapse_jCheckBox.isSelected()) {
                if (overlayAllSelectedPoints_jCheckBox.isSelected()) {
                    getSelectedArborAndNameOLMultiThread(selectedArborTraceOL,
                            selectedArborNameOL, selectedArborSynapseOL, selectedArborConnectedOL, selectedArborSpineOL);
                } else {
                    getSelectedArborAndNameOLextPtMultiThread(selectedArborTraceOLextPt,
                            selectedArborNameOLextPt, selectedArborSynapseOL, selectedArborConnectedOL, selectedArborSpineOLextPt, extendDisplayPoints);
                    for (int j = 0; j < impNSlice; j++) {
                        if (selectedArborTraceOLextPt[j] != null) {
                            for (int n = 0; n < selectedArborTraceOLextPt[j].size(); n++) {
                                selectedArborTraceOL.add(selectedArborTraceOLextPt[j].get(n));
                            }
                        }
                        if (selectedArborNameOLextPt[j] != null) {
                            for (int n = 0; n < selectedArborNameOLextPt[j].size(); n++) {
                                selectedArborNameOL.add(selectedArborNameOLextPt[j].get(n));
                            }
                        }
                        if (selectedArborSpineOLextPt[j] != null) {
                            for (int n = 0; n < selectedArborSpineOLextPt[j].size(); n++) {
                                selectedArborSpineOL.add(selectedArborSpineOLextPt[j].get(n));
                            }
                        }
                    }
                }
            }

            // overlay selected branch(es)
            if (overlaySelectedBranch_jCheckBox.isSelected()
                    || overlayAllName_jCheckBox.isSelected()
                    || overlaySelectedName_jCheckBox.isSelected()
                    || overlaySelectedSynapse_jCheckBox.isSelected()) {
                if (overlayAllSelectedPoints_jCheckBox.isSelected() || overlayAllPoints_jCheckBox.isSelected()) {
                    getSelectedBranchAndNameOLMultiThread(selectedBranchTraceOL,
                            selectedBranchNameOL, selectedBranchSynapseOL, selectedBranchConnectedOL, selectedBranchSpineOL);
                } else if (!overlayAllSelectedPoints_jCheckBox.isSelected()) {
                    getSelectedBranchAndNameOLextPtMultiThread(selectedBranchTraceOLextPt,
                            selectedBranchNameOLextPt, selectedBranchSynapseOL, selectedBranchConnectedOL, selectedBranchSpineOLextPt, extendDisplayPoints);
                    for (int j = 0; j < impNSlice; j++) {
                        if (selectedBranchTraceOLextPt[j] != null) {
                            for (int n = 0; n < selectedBranchTraceOLextPt[j].size(); n++) {
                                selectedBranchTraceOL.add(selectedBranchTraceOLextPt[j].get(n));
                            }
                        }
                        if (selectedBranchNameOLextPt[j] != null) {
                            for (int n = 0; n < selectedBranchNameOLextPt[j].size(); n++) {
                                selectedBranchNameOL.add(selectedBranchNameOLextPt[j].get(n));
                            }
                        }
                        if (selectedBranchSpineOLextPt[j] != null) {
                            for (int n = 0; n < selectedBranchSpineOLextPt[j].size(); n++) {
                                selectedBranchSpineOL.add(selectedBranchSpineOLextPt[j].get(n));
                            }
                        }
                    }
                }
            }

            // overlay all somas
            if (overlayAllSoma_jCheckBox.isSelected() || overlayAllSynapse_jCheckBox.isSelected()) {
                getAllSomaAndNameOLextPtMultiThread(allSomaTraceOL, allSomaNameOL,
                        allSomaSynapseOL, allSomaConnectedOL, allSomaSpineOLextPt, !overlayAllPoints_jCheckBox.isSelected());
            }

            // overlay selected soma(s)
            if (overlaySelectedSoma_jCheckBox.isSelected()
                    || overlayAllName_jCheckBox.isSelected()
                    || overlaySelectedName_jCheckBox.isSelected()
                    || overlaySelectedSynapse_jCheckBox.isSelected()) {
                getSelectedSomaAndNameOLextPtMultiThread(selectedSomaTraceOL, selectedSomaNameOL,
                        selectedSomaSynapseOL, selectedSomaConnectedOL, selectedSomaSpineOLextPt, !overlayAllSelectedPoints_jCheckBox.isSelected());
            }

            updateOverlay();
            //long endTime = System.currentTimeMillis();
            //IJ.log("total display update time = "+(endTime-startTime));
        }
    }

    public java.util.List<Roi> auxOverlay;

    protected void updateOverlay() {
        displayOL.clear();

        // overlay all neurons
        if (overlayAllNeuron_jCheckBox.isSelected()) {
            add2displayOL(allNeuronTraceOL);
        }
        if (overlayAllName_jCheckBox.isSelected()) {
            add2displayOL(allNeuronNameOL);
        }
        if (overlayAllSpine_jCheckBox.isSelected()) {
            add2displayOL(allNeuronSpineOL);
        }
        if (overlayAllSynapse_jCheckBox.isSelected()) {
            add2displayOL(allNeuronSynapseOL);
        }
        if (overlayAllConnection_jCheckBox.isSelected()) {
            add2displayOL(allNeuronConnectedOL);
        }

        // overlay selected whole neuron(s)
        if (overlaySelectedNeuron_jCheckBox.isSelected()) {
            add2displayOL(selectedNeuronTraceOL);
            if (overlaySelectedSpine_jCheckBox.isSelected()) {
                add2displayOL(selectedNeuronSpineOL);
            }
            if (overlaySelectedSynapse_jCheckBox.isSelected()) {
                add2displayOL(selectedNeuronSynapseOL);
            }
            if (overlaySelectedConnection_jCheckBox.isSelected()) {
                add2displayOL(selectedNeuronConnectedOL);
            }
        }
        if (overlaySelectedName_jCheckBox.isSelected()) {
            add2displayOL(selectedNeuronNameOL);
        }

        // overlay selected arbor(s)
        if (overlaySelectedArbor_jCheckBox.isSelected()) {
            add2displayOL(selectedArborTraceOL);
            if (overlaySelectedSpine_jCheckBox.isSelected()) {
                add2displayOL(selectedArborSpineOL);
            }
            if (overlaySelectedSynapse_jCheckBox.isSelected()) {
                add2displayOL(selectedArborSynapseOL);
            }
            if (overlaySelectedConnection_jCheckBox.isSelected()) {
                add2displayOL(selectedArborConnectedOL);
            }
        }
        if (overlaySelectedName_jCheckBox.isSelected()) {
            add2displayOL(selectedArborNameOL);
        }

        // overlay selected branch(es)
        if (overlaySelectedBranch_jCheckBox.isSelected()) {
            add2displayOL(selectedBranchTraceOL);
            if (overlaySelectedSpine_jCheckBox.isSelected()) {
                add2displayOL(selectedBranchSpineOL);
            }
            if (overlaySelectedSynapse_jCheckBox.isSelected()) {
                add2displayOL(selectedBranchSynapseOL);
            }
            if (overlaySelectedConnection_jCheckBox.isSelected()) {
                add2displayOL(selectedBranchConnectedOL);
            }
        }
        if (overlayAllName_jCheckBox.isSelected()
                || overlaySelectedName_jCheckBox.isSelected()) {
            for (int i = 0; i < 10; i++) {
                add2displayOL(selectedBranchNameOL);
            }
        }

        // overlay all somas
        if (overlayAllSoma_jCheckBox.isSelected()) {
            for (int j = 0; j < impNSlice; j++) {
                if (allSomaTraceOL[j] != null) {
                    add2displayOL(allSomaTraceOL[j]);
                }
            }
        }
        if (overlayAllName_jCheckBox.isSelected()) {
            for (int j = 0; j < impNSlice; j++) {
                if (allSomaNameOL[j] != null) {
                    add2displayOL(allSomaNameOL[j]);
                }
            }
        }
        if (overlayAllSpine_jCheckBox.isSelected()) {
            for (int j = 0; j < impNSlice; j++) {
                if (allSomaSpineOLextPt[j] != null) {
                    add2displayOL(allSomaSpineOLextPt[j]);
                }
            }
        }
        if (overlayAllSynapse_jCheckBox.isSelected()) {
            add2displayOL(allSomaSynapseOL);
        }
        if (overlayAllConnection_jCheckBox.isSelected()) {
            add2displayOL(allSomaConnectedOL);
        }

        // overlay selected soma(s)
        if (overlaySelectedSoma_jCheckBox.isSelected()) {
            for (int j = 0; j < impNSlice; j++) {
                if (selectedSomaTraceOL[j] != null) {
                    add2displayOL(selectedSomaTraceOL[j]);
                }
            }
        }
        if (overlaySelectedName_jCheckBox.isSelected()) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    if (selectedSomaNameOL[j] != null) {
                        add2displayOL(selectedSomaNameOL[j]);
                    }
                }
            }
        }
        if (overlaySelectedSpine_jCheckBox.isSelected()) {
            for (int j = 0; j < impNSlice; j++) {
                if (selectedSomaSpineOLextPt[j] != null) {
                    add2displayOL(selectedSomaSpineOLextPt[j]);
                }
            }
        }
        if (overlaySelectedSynapse_jCheckBox.isSelected()) {
            add2displayOL(selectedSomaSynapseOL);
        }
        if (overlaySelectedConnection_jCheckBox.isSelected()) {
            add2displayOL(selectedSomaConnectedOL);
        }

        // overlay points
        if (overlayPointBox_jCheckBox.isSelected()) {
            addPointBoxes(pointBoxRadius);
        }

        if (this.auxOverlay != null) {
            for (Roi i : auxOverlay) {
                displayOL.add(i);
            }
        }
        
        // Overlay blanks
        updateBlankOverlay();
        cns.setOverlay(displayOL);
    }
    
    protected void updateBlankOverlay() {
        if (blank_jCheckBox.isSelected()) {
            int z = imp.getSlice();
            int BLANK_PADDING = 3;
            
            for (int t = 0; t < rootNeuronNode.getChildCount(); ++t) {
                ntNeuronNode neuron = (ntNeuronNode) rootNeuronNode.getChildAt(t);
                if (neuron.getChildCount() < 1) continue;
                float approxZ = Float.parseFloat(((ntNeuronNode)neuron.getChildAt(0)).getTracingResult().get(0)[3]);
                if(z < approxZ - BLANK_PADDING || z > approxZ + BLANK_PADDING) continue;
                
                ArrayList<ntNeuronNode> branches = new ArrayList<>();
                Stack<ntNeuronNode> st = new Stack<>();
                st.add(neuron);
                while (st.size() > 0) {
                    ntNeuronNode current = st.pop();
                    branches.add(current);
                    for (int b = 0; b < current.getChildCount(); ++b) {
                        st.add((ntNeuronNode) current.getChildAt(b));
                    }
                }
                
                
                for (ntNeuronNode node: branches) {
                    ArrayList<String[]> points = node.getTracingResult();

                    float [] xarr = new float[points.size() * 2 * BLANK_PADDING * 2 * BLANK_PADDING];
                    float [] yarr = new float[points.size() * 2 * BLANK_PADDING * 2* BLANK_PADDING];
                    
                    int arrIndex = 0;
                    for (int i = 0; i < points.size(); ++i) {
                        float pointZ = Float.parseFloat(points.get(i)[3]);
                        if (pointZ == -1 || z > pointZ + BLANK_PADDING || z < pointZ - BLANK_PADDING) continue;
                        
                        for (int xp = -BLANK_PADDING; xp < BLANK_PADDING; ++xp) {
                            for (int yp = -BLANK_PADDING; yp < BLANK_PADDING; ++yp) {
                                xarr[arrIndex] = Math.max(Float.parseFloat(points.get(i)[1]) + xp, 0);
                                yarr[arrIndex] = Math.max(Float.parseFloat(points.get(i)[2]) + yp, 0);
                                ++arrIndex;
                            }
                        }
                    }
                    
                    PolygonRoi roiLine = new PolygonRoi(xarr, yarr, Roi.POLYLINE);
                    roiLine.setStrokeColor(Color.BLACK);
                    roiLine.setStrokeWidth(2);
                    displayOL.add(roiLine);
                }
            }
//            cns.setOverlay(displayOL);
        }
    }
    
    protected void updateSkeletonizedTracing() {
        if (!skeletonizedShowTracedPoints_jButton.isSelected()) return;
        
        int z = imp.getSlice();
        
        Overlay tracingOL = new Overlay();
        
        for (int t = 0; t < rootNeuronNode.getChildCount(); ++t) {
            ntNeuronNode neuron = (ntNeuronNode) rootNeuronNode.getChildAt(t);
            if (neuron.getChildCount() < 1) continue;

            ArrayList<ntNeuronNode> branches = new ArrayList<>();
            Stack<ntNeuronNode> st = new Stack<>();
            st.add(neuron);
            while (st.size() > 0) {
                ntNeuronNode current = st.pop();
                branches.add(current);
                
                for (int b = 0; b < current.getChildCount(); ++b) {
                    st.add((ntNeuronNode) current.getChildAt(b));
                }
            }


            for (ntNeuronNode node: branches) {
                ArrayList<String[]> points = node.getTracingResult();
                
                ArrayList<Float> xList = new ArrayList<>();
                ArrayList<Float> yList = new ArrayList<>();
                boolean pathStarted = false;

                for (int i = 0; i < points.size(); ++i) {
                    float currentZ = Float.parseFloat(points.get(i)[3]);
                    if (z < currentZ - zProjInterval || z > currentZ + zProjInterval) {
                        if (pathStarted) {
                            float[] xArr = new float[xList.size()];
                            float[] yArr = new float[yList.size()];
                            for (int l = 0; l < xArr.length; ++l) {
                                xArr[l] = xList.get(l);
                                yArr[l] = yList.get(l);
                            }

                            PolygonRoi roiLine = new PolygonRoi(xArr, yArr, Roi.POLYLINE);
                            roiLine.setStrokeColor(Color.YELLOW);
                            roiLine.setStrokeWidth(2);
                            tracingOL.add(roiLine);
                            xList = new ArrayList<>();
                            yList = new ArrayList<>();
                            pathStarted = false;
                        }
                        continue;
                    }
                    pathStarted = true;
                    xList.add(Float.parseFloat(points.get(i)[1]));
                    yList.add(Float.parseFloat(points.get(i)[2]));
                }
                if (xList.size() > 0) {
                    float[] xArr = new float[xList.size()];
                    float[] yArr = new float[yList.size()];
                    for (int l = 0; l < xArr.length; ++l) {
                        xArr[l] = xList.get(l);
                        yArr[l] = yList.get(l);
                    }

                    PolygonRoi roiLine = new PolygonRoi(xArr, yArr, Roi.POLYLINE);
                    roiLine.setStrokeColor(Color.YELLOW);
                    roiLine.setStrokeWidth(2);
                    tracingOL.add(roiLine);
                }
            }
        }
        impSkeletonizedProjection.getCanvas().setOverlay(tracingOL);
    }

    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getAllNeuronAndNameOL">
    protected void getAllNeuronAndNameOLMultiThread(
            Overlay neuronTraceOL, Overlay neuronNameOL, Overlay neuronSynapseOL, Overlay neuronConnectedOL, Overlay allNeuronSpineOL) {
        int totalChild = rootNeuronNode.getChildCount();
        if (totalChild == 0) {
            return;
        }
        if (totalChild > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[] neuronTraceOLs = new Overlay[threadNum];
            Overlay[] neuronNameOLs = new Overlay[threadNum];
            Overlay[] neuronSynapseOLs = new Overlay[threadNum];
            Overlay[] neuronConnectedOLs = new Overlay[threadNum];
            Overlay[] allNeuronSpineOLs = new Overlay[threadNum];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalChild / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                neuronTraceOLs[i] = new Overlay();
                neuronNameOLs[i] = new Overlay();
                neuronSynapseOLs[i] = new Overlay();
                neuronConnectedOLs[i] = new Overlay();
                allNeuronSpineOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalChild - 1) {
                    end = totalChild - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetAllNeuronAndNameOLthread getAllNeuronAndNameOL
                        = new GetAllNeuronAndNameOLthread(neuronTraceOLs[i],
                                neuronNameOLs[i], neuronSynapseOLs[i], neuronConnectedOLs[i], allNeuronSpineOLs[i], start, end);
                threads[i] = new Thread(getAllNeuronAndNameOL);
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                //IJ.log("thread "+i+" returns neuriteOL = "+allNeuriteTraceOLs[i].size());
                if (neuronTraceOLs[i] != null) {
                    for (int n = 0; n < neuronTraceOLs[i].size(); n++) {
                        neuronTraceOL.add(neuronTraceOLs[i].get(n));
                    }
                }
                if (neuronNameOLs[i] != null) {
                    for (int n = 0; n < neuronNameOLs[i].size(); n++) {
                        neuronNameOL.add(neuronNameOLs[i].get(n));
                    }
                }
                if (neuronSynapseOLs[i] != null) {
                    for (int n = 0; n < neuronSynapseOLs[i].size(); n++) {
                        neuronSynapseOL.add(neuronSynapseOLs[i].get(n));
                    }
                }
                if (neuronConnectedOLs[i] != null) {
                    for (int n = 0; n < neuronConnectedOLs[i].size(); n++) {
                        neuronConnectedOL.add(neuronConnectedOLs[i].get(n));
                    }
                }
            }
        } else {
            ntNeuronNode childNode = (ntNeuronNode) (rootNeuronNode.getChildAt(0));
            getOneWholeNeuronRoiAllPt(neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, allNeuronSpineOL, childNode, allNeuronLine, allSpineLine, allSynapseRadius, allSynapseSize);
        }
    }

    class GetAllNeuronAndNameOLthread extends Thread {

        Overlay neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, allNeuronSpineOL;
        int startNode, endNode;

        public GetAllNeuronAndNameOLthread(
                Overlay neuronTraceOL, Overlay neuronNameOL, Overlay neuronSynapseOL, Overlay neuronConnectedOL,
                Overlay allNeuronSpineOL, int startNode, int endNode
        ) {
            this.neuronTraceOL = neuronTraceOL;
            this.neuronNameOL = neuronNameOL;
            this.neuronSynapseOL = neuronSynapseOL;
            this.neuronConnectedOL = neuronConnectedOL;
            this.allNeuronSpineOL = allNeuronSpineOL;
            this.startNode = startNode;
            this.endNode = endNode;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                ntNeuronNode neuronNode = (ntNeuronNode) (rootNeuronNode.getChildAt(k));
                getOneWholeNeuronRoiAllPt(neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, allNeuronSpineOL, neuronNode, allNeuronLine, allSpineLine, allSynapseRadius, allSynapseSize);
            }
        }

    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getAllNeuronAndNameOLextPt">
    protected void getAllNeuronAndNameOLextPtMultiThread(
            Overlay[] neuronTraceOL, Overlay[] neuronNameOL, Overlay neuronSynapseOL,
            Overlay neuronConnectedOL, Overlay[] neuronSpineOL, int extendPoints) {
        int totalChild = rootNeuronNode.getChildCount();
        if (totalChild == 0) {
            return;
        }
        if (totalChild > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[][] neuronTraceOLs = new Overlay[threadNum][impNSlice];
            Overlay[][] neuronNameOLs = new Overlay[threadNum][impNSlice];
            Overlay[] neuronSynapseOLs = new Overlay[threadNum];
            Overlay[] neuronConnectedOLs = new Overlay[threadNum];
            Overlay[][] neuronSpineOLs = new Overlay[threadNum][impNSlice];
            Thread[] threads = new Thread[threadNum];
            int increment = (int) Math.ceil((double) totalChild / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    neuronTraceOLs[i][j] = new Overlay();
                    neuronNameOLs[i][j] = new Overlay();
                    neuronSpineOLs[i][j] = new Overlay();
                }
                neuronSynapseOLs[i] = new Overlay();
                neuronConnectedOLs[i] = new Overlay();
                int start = increment * i;
                int end = increment * (i + 1) - 1;
                if (end > totalChild - 1) {
                    end = totalChild - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetAllNeuronAndNameOLExtPtThread getAllNeuronAndNameOLextPt
                        = new GetAllNeuronAndNameOLExtPtThread(neuronTraceOLs[i],
                                neuronNameOLs[i], neuronSynapseOLs[i], neuronConnectedOLs[i], neuronSpineOLs[i], start, end, extendPoints);
                threads[i] = new Thread(getAllNeuronAndNameOLextPt);
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                try {
                    t.join();
                } catch (Exception e) {
                }
            }

            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    if (neuronTraceOLs[i][j] != null) {
                        for (int n = 0; n < neuronTraceOLs[i][j].size(); n++) {
                            neuronTraceOL[j].add(neuronTraceOLs[i][j].get(n));
                        }
                    }
                    if (neuronNameOLs[i][j] != null) {
                        for (int n = 0; n < neuronNameOLs[i][j].size(); n++) {
                            neuronNameOL[j].add(neuronNameOLs[i][j].get(n));
                        }
                    }
                    if (neuronSpineOLs[i][j] != null) {
                        for (int n = 0; n < neuronSpineOLs[i][j].size(); n++) {
                            neuronSpineOL[j].add(neuronSpineOLs[i][j].get(n));
                        }
                    }
                }

                if (neuronSynapseOLs[i] != null) {
                    for (int n = 0; n < neuronSynapseOLs[i].size(); n++) {
                        neuronSynapseOL.add(neuronSynapseOLs[i].get(n));
                    }
                }

                if (neuronConnectedOLs[i] != null) {
                    for (int n = 0; n < neuronConnectedOLs[i].size(); n++) {
                        neuronConnectedOL.add(neuronConnectedOLs[i].get(n));
                    }
                }
            }
        } else {
            ntNeuronNode childNode = (ntNeuronNode) (rootNeuronNode.getChildAt(0));
            getOneWholeNeuronRoiExtPt(neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, neuronSpineOL, childNode, allNeuronLine, allSpineLine, allSynapseRadius, allSynapseSize, extendPoints);
        }
    }

    class GetAllNeuronAndNameOLExtPtThread extends Thread {

        Overlay[] neuronTraceOL, neuronNameOL, neuronSpineOL;
        Overlay neuronSynapseOL, neuronConnectedOL;
        int startNode, endNode, extendPoints;

        public GetAllNeuronAndNameOLExtPtThread(
                Overlay[] neuronTraceOL, Overlay[] neuronNameOL, Overlay neuronSynapseOL, Overlay neuronConnectedOL,
                Overlay[] neuronSpineOL, int startNode, int endNode, int extendPoints
        ) {
            this.neuronTraceOL = neuronTraceOL;
            this.neuronNameOL = neuronNameOL;
            this.neuronSynapseOL = neuronSynapseOL;
            this.neuronConnectedOL = neuronConnectedOL;
            this.neuronSpineOL = neuronSpineOL;
            this.startNode = startNode;
            this.endNode = endNode;
            this.extendPoints = extendPoints;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                ntNeuronNode neuronNode = (ntNeuronNode) (rootNeuronNode.getChildAt(k));
                getOneWholeNeuronRoiExtPt(neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, neuronSpineOL,
                        neuronNode, allNeuronLine, allSpineLine, allSynapseRadius, allSynapseSize, extendPoints);
            }
        }

    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getSelectedNeuronAndNameOL">
    protected void getSelectedNeuronAndNameOLMultiThread(
            Overlay neuronTraceOL, Overlay neuronNameOL, Overlay neuronSynapseOL, Overlay neuronConnectedOL, Overlay neuronSpineOL) {
        // retreive all selected paths to be displayed
        if (neuronList_jTree.getSelectionCount() == 0) {
            return;
        }
        TreePath[] treePaths = neuronList_jTree.getSelectionPaths();
        //IJ.log("total selected = "+treePaths.length);
        ArrayList<ntNeuronNode> selectedNodes = new ArrayList<ntNeuronNode>();
        for (TreePath treePath : treePaths) {
            selectedNodes.add((ntNeuronNode) treePath.getPathComponent(1));
        }
        removeRedundantNode(selectedNodes);
        int totalDisplayNodeNumber = selectedNodes.size();
        //IJ.log("reduce to "+totalDisplayNodeNumber);
        if (totalDisplayNodeNumber > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[] neuronTraceOLs = new Overlay[threadNum];
            Overlay[] neuronNameOLs = new Overlay[threadNum];
            Overlay[] neuronSynapseOLs = new Overlay[threadNum];
            Overlay[] neuronConnectedOLs = new Overlay[threadNum];
            Overlay[] neuronSpineOLs = new Overlay[threadNum];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalDisplayNodeNumber / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                neuronTraceOLs[i] = new Overlay();
                neuronNameOLs[i] = new Overlay();
                neuronSynapseOLs[i] = new Overlay();
                neuronConnectedOLs[i] = new Overlay();
                neuronSpineOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalDisplayNodeNumber - 1) {
                    end = totalDisplayNodeNumber - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetSelectedNeuronAndNameOLthread getSelectedNeuronAndNameOL
                        = new GetSelectedNeuronAndNameOLthread(neuronTraceOLs[i],
                                neuronNameOLs[i], neuronSynapseOLs[i], neuronConnectedOLs[i], neuronSpineOLs[i], selectedNodes, start, end);
                threads[i] = new Thread(getSelectedNeuronAndNameOL);
            }
            for (int i = 0; i < threadNum; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                //IJ.log("thread "+i+" returns neuriteOL = "+allNeuriteTraceOLs[i].size());
                if (neuronTraceOLs[i] != null) {
                    for (int n = 0; n < neuronTraceOLs[i].size(); n++) {
                        neuronTraceOL.add(neuronTraceOLs[i].get(n));
                    }
                }
                if (neuronNameOLs[i] != null) {
                    for (int n = 0; n < neuronNameOLs[i].size(); n++) {
                        neuronNameOL.add(neuronNameOLs[i].get(n));
                    }
                }
                if (neuronSynapseOLs[i] != null) {
                    for (int n = 0; n < neuronSynapseOLs[i].size(); n++) {
                        neuronSynapseOL.add(neuronSynapseOLs[i].get(n));
                    }
                }
                if (neuronConnectedOLs[i] != null) {
                    for (int n = 0; n < neuronConnectedOLs[i].size(); n++) {
                        neuronConnectedOL.add(neuronConnectedOLs[i].get(n));
                    }
                }
                if (neuronSpineOLs[i] != null) {
                    for (int n = 0; n < neuronSpineOLs[i].size(); n++) {
                        neuronSpineOL.add(neuronSpineOLs[i].get(n));
                    }
                }
            }
        } else if (totalDisplayNodeNumber == 1) {
            getOneWholeNeuronRoiAllPt(neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, neuronSpineOL,
                    (ntNeuronNode) selectedNodes.get(0), neuronLine, spineLine, synapseRadius, synapseSize);
        }
    }

    class GetSelectedNeuronAndNameOLthread extends Thread {

        Overlay neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, neuronSpineOL;
        ArrayList<ntNeuronNode> selectedNodes;
        int startNode, endNode;

        public GetSelectedNeuronAndNameOLthread(
                Overlay neuronTraceOL, Overlay neuronNameOL, Overlay neuronSynapseOL, Overlay neuronConnectedOL, Overlay neuronSpineOL,
                ArrayList<ntNeuronNode> selectedNodes, int startNode, int endNode
        ) {
            this.neuronTraceOL = neuronTraceOL;
            this.neuronNameOL = neuronNameOL;
            this.neuronSynapseOL = neuronSynapseOL;
            this.neuronConnectedOL = neuronConnectedOL;
            this.neuronSpineOL = neuronSpineOL;
            this.selectedNodes = selectedNodes;
            this.startNode = startNode;
            this.endNode = endNode;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                getOneWholeNeuronRoiAllPt(neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, neuronSpineOL,
                        (ntNeuronNode) selectedNodes.get(k), neuronLine, spineLine, synapseRadius, synapseSize);
            }
        }

    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getSelectedNeuronAndNameOLextPt">
    protected void getSelectedNeuronAndNameOLextPtMultiThread(
            Overlay[] neuronTraceOL, Overlay[] neuronNameOL, Overlay neuronSynapseOL, Overlay neuronConnectedOL, Overlay[] neuronSpineOL,
            int extendPoints) {
        // retreive all selected paths to be displayed
        if (neuronList_jTree.getSelectionCount() == 0) {
            return;
        }
        TreePath[] treePaths = neuronList_jTree.getSelectionPaths();
        //IJ.log("total selected = "+treePaths.length);
        ArrayList<ntNeuronNode> selectedNodes = new ArrayList<ntNeuronNode>();
        for (TreePath treePath : treePaths) {
            selectedNodes.add((ntNeuronNode) treePath.getPathComponent(1));
        }

        removeRedundantNode(selectedNodes);
        int totalDisplayNodeNumber = selectedNodes.size();
        //IJ.log("reduce to "+totalDisplayNodeNumber);

        if (totalDisplayNodeNumber > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[][] neuronTraceOLs = new Overlay[threadNum][impNSlice];
            Overlay[][] neuronNameOLs = new Overlay[threadNum][impNSlice];
            Overlay[] neuronSynapseOLs = new Overlay[threadNum];
            Overlay[] neuronConnectedOLs = new Overlay[threadNum];
            Overlay[][] neuronSpineOLs = new Overlay[threadNum][impNSlice];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalDisplayNodeNumber / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    neuronTraceOLs[i][j] = new Overlay();
                    neuronNameOLs[i][j] = new Overlay();
                    neuronSpineOLs[i][j] = new Overlay();
                }
                neuronSynapseOLs[i] = new Overlay();
                neuronConnectedOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalDisplayNodeNumber - 1) {
                    end = totalDisplayNodeNumber - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetSelectedNeuronAndNameOLextPtMultiThread getSelectedNeuronAndNameOLextPt
                        = new GetSelectedNeuronAndNameOLextPtMultiThread(neuronTraceOLs[i],
                                neuronNameOLs[i], neuronSynapseOLs[i], neuronConnectedOLs[i], neuronSpineOLs[i],
                                selectedNodes, start, end, extendPoints);
                threads[i] = new Thread(getSelectedNeuronAndNameOLextPt);
            }
            for (int i = 0; i < threadNum; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                //IJ.log("thread "+i+" returns neuriteOL = "+allNeuriteTraceOLs[i].size());
                for (int j = 0; j < impNSlice; j++) {
                    if (neuronTraceOLs[i][j] != null) {
                        for (int n = 0; n < neuronTraceOLs[i][j].size(); n++) {
                            neuronTraceOL[j].add(neuronTraceOLs[i][j].get(n));
                        }
                    }
                    if (neuronNameOLs[i][j] != null) {
                        for (int n = 0; n < neuronNameOLs[i][j].size(); n++) {
                            neuronNameOL[j].add(neuronNameOLs[i][j].get(n));
                        }
                    }
                    if (neuronSpineOLs[i][j] != null) {
                        for (int n = 0; n < neuronSpineOLs[i][j].size(); n++) {
                            neuronSpineOL[j].add(neuronSpineOLs[i][j].get(n));
                        }
                    }
                }
                if (neuronSynapseOLs[i] != null) {
                    for (int n = 0; n < neuronSynapseOLs[i].size(); n++) {
                        neuronSynapseOL.add(neuronSynapseOLs[i].get(n));
                    }
                }
                if (neuronConnectedOLs[i] != null) {
                    for (int n = 0; n < neuronConnectedOLs[i].size(); n++) {
                        neuronConnectedOL.add(neuronConnectedOLs[i].get(n));
                    }
                }
            }
        } else if (totalDisplayNodeNumber == 1) {
            getOneWholeNeuronRoiExtPt(neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, neuronSpineOL,
                    (ntNeuronNode) selectedNodes.get(0), neuronLine, spineLine, synapseRadius, synapseSize, extendPoints);
        }
    }

    class GetSelectedNeuronAndNameOLextPtMultiThread extends Thread {

        Overlay[] neuronTraceOL, neuronNameOL, neuronSpineOL;
        Overlay neuronSynapseOL, neuronConnectedOL;
        ArrayList<ntNeuronNode> selectedNodes;
        int startNode, endNode, extendPoints;

        public GetSelectedNeuronAndNameOLextPtMultiThread(
                Overlay[] neuronTraceOL, Overlay[] neuronNameOL, Overlay neuronSynapseOL, Overlay neuronConnectedOL,
                Overlay[] neuronSpineOL, ArrayList<ntNeuronNode> selectedNodes,
                int startNode, int endNode, int extendPoints
        ) {
            this.neuronTraceOL = neuronTraceOL;
            this.neuronNameOL = neuronNameOL;
            this.neuronSynapseOL = neuronSynapseOL;
            this.neuronConnectedOL = neuronConnectedOL;
            this.neuronSpineOL = neuronSpineOL;
            this.selectedNodes = selectedNodes;
            this.startNode = startNode;
            this.endNode = endNode;
            this.extendPoints = extendPoints;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                getOneWholeNeuronRoiExtPt(neuronTraceOL, neuronNameOL, neuronSynapseOL, neuronConnectedOL, neuronSpineOL,
                        (ntNeuronNode) selectedNodes.get(k), neuronLine, spineLine, synapseRadius, synapseSize, extendPoints);
            }
        }

    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getSelectedArborAndNameOL">
    protected void getSelectedArborAndNameOLMultiThread(
            Overlay arborTraceOL, Overlay arborNameOL, Overlay arborSynapseOL, Overlay arborConnectedOL, Overlay arborSpineOL) {
        // retreive all selected paths to be displayed
        if (neuronList_jTree.getSelectionCount() == 0) {
            return;
        }
        TreePath[] treePaths = neuronList_jTree.getSelectionPaths();
        //IJ.log("total selected = "+treePaths.length);
        ArrayList<ntNeuronNode> selectedNodes = new ArrayList<ntNeuronNode>();
        for (TreePath treePath : treePaths) {
            if (((ntNeuronNode) (treePath.getLastPathComponent())).isBranchNode()) {
                // make sure NOT selected soma to allow adding the arbor primary branch node (2nd level node)
                ntNeuronNode arborNode = (ntNeuronNode) (treePath.getPathComponent(2));
                selectedNodes.add(arborNode);
            }
        }
        removeRedundantNode(selectedNodes);
        int totalDisplayNodeNumber = selectedNodes.size();
        //IJ.log("reduce to "+totalDisplayNodeNumber);
        if (totalDisplayNodeNumber > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[] arborTraceOLs = new Overlay[threadNum];
            Overlay[] arborNameOLs = new Overlay[threadNum];
            Overlay[] arborSynapseOLs = new Overlay[threadNum];
            Overlay[] arborConnectedOLs = new Overlay[threadNum];
            Overlay[] arborTraceSpineOLs = new Overlay[threadNum];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalDisplayNodeNumber / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                arborTraceOLs[i] = new Overlay();
                arborNameOLs[i] = new Overlay();
                arborSynapseOLs[i] = new Overlay();
                arborConnectedOLs[i] = new Overlay();
                arborTraceSpineOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalDisplayNodeNumber - 1) {
                    end = totalDisplayNodeNumber - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetSelectedArborAndNameOLthread getSelectedArborAndNameOL
                        = new GetSelectedArborAndNameOLthread(arborTraceOLs[i], arborNameOLs[i],
                                arborSynapseOLs[i], arborConnectedOLs[i], arborTraceSpineOLs[i], selectedNodes, start, end);
                threads[i] = new Thread(getSelectedArborAndNameOL);
            }
            for (int i = 0; i < threadNum; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                //IJ.log("thread "+i+" returns neuriteOL = "+allNeuriteTraceOLs[i].size());
                if (arborTraceOLs[i] != null) {
                    for (int n = 0; n < arborTraceOLs[i].size(); n++) {
                        arborTraceOL.add(arborTraceOLs[i].get(n));
                    }
                }
                if (arborNameOLs[i] != null) {
                    for (int n = 0; n < arborNameOLs[i].size(); n++) {
                        arborNameOL.add(arborNameOLs[i].get(n));
                    }
                }
                if (arborSynapseOLs[i] != null) {
                    for (int n = 0; n < arborSynapseOLs[i].size(); n++) {
                        arborSynapseOL.add(arborSynapseOLs[i].get(n));
                    }
                }
                if (arborConnectedOLs[i] != null) {
                    for (int n = 0; n < arborConnectedOLs[i].size(); n++) {
                        arborConnectedOL.add(arborConnectedOLs[i].get(n));
                    }
                }
            }
        } else if (totalDisplayNodeNumber == 1) {
            getOneWholeArborRoi(arborTraceOL, arborNameOL, arborSynapseOL, arborConnectedOL, arborSpineOL,
                    (ntNeuronNode) selectedNodes.get(0), arborLine, spineLine, synapseRadius, synapseSize);
        }
    }

    class GetSelectedArborAndNameOLthread extends Thread {

        Overlay neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL;
        ArrayList<ntNeuronNode> selectedNodes;
        int startNode, endNode;

        public GetSelectedArborAndNameOLthread(
                Overlay neuriteTraceOL, Overlay neuriteNameOL, Overlay neuriteSynapseOL, Overlay neuriteConnectedOL,
                Overlay neuriteSpineOL, ArrayList<ntNeuronNode> selectedNodes, int startNode, int endNode
        ) {
            this.neuriteTraceOL = neuriteTraceOL;
            this.neuriteNameOL = neuriteNameOL;
            this.neuriteSynapseOL = neuriteSynapseOL;
            this.neuriteConnectedOL = neuriteConnectedOL;
            this.neuriteSpineOL = neuriteSpineOL;
            this.selectedNodes = selectedNodes;
            this.startNode = startNode;
            this.endNode = endNode;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                getOneWholeArborRoi(neuriteTraceOL, neuriteNameOL, neuriteSynapseOL, neuriteConnectedOL, neuriteSpineOL,
                        (ntNeuronNode) selectedNodes.get(k), arborLine, spineLine, synapseRadius, synapseSize);
            }
        }

    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getSelectedArborAndNameOLextPt">
    protected void getSelectedArborAndNameOLextPtMultiThread(
            Overlay[] arborTraceOL, Overlay[] arborNameOL, Overlay arborSynapseOL, Overlay arborConnectedOL,
            Overlay[] arborSpineOL, int extendPoints) {
        // retreive all selected paths to be displayed
        if (neuronList_jTree.getSelectionCount() == 0) {
            return;
        }
        TreePath[] treePaths = neuronList_jTree.getSelectionPaths();
        //IJ.log("total selected = "+treePaths.length);
        ArrayList<ntNeuronNode> selectedNodes = new ArrayList<ntNeuronNode>();
        for (TreePath treePath : treePaths) {
            if (((ntNeuronNode) (treePath.getLastPathComponent())).isBranchNode()) {
                // make sure NOT selected soma to allow adding the arbor primary branch node (2nd level node)
                ntNeuronNode arborNode = (ntNeuronNode) (treePath.getPathComponent(2));
                selectedNodes.add(arborNode);
            }
        }
        removeRedundantNode(selectedNodes);
        int totalDisplayNodeNumber = selectedNodes.size();
        //IJ.log("reduce to "+totalDisplayNodeNumber);
        if (totalDisplayNodeNumber > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[][] arborTraceOLs = new Overlay[threadNum][impNSlice];
            Overlay[][] arborNameOLs = new Overlay[threadNum][impNSlice];
            Overlay[] arborSynapseOLs = new Overlay[threadNum];
            Overlay[] arborConnectedOLs = new Overlay[threadNum];
            Overlay[][] arborSpineOLs = new Overlay[threadNum][impNSlice];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalDisplayNodeNumber / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    arborTraceOLs[i][j] = new Overlay();
                    arborNameOLs[i][j] = new Overlay();
                    arborSpineOLs[i][j] = new Overlay();
                }
                arborSynapseOLs[i] = new Overlay();
                arborConnectedOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalDisplayNodeNumber - 1) {
                    end = totalDisplayNodeNumber - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetSelectedArborAndNameOLextPtMultiThread getSelectedArborAndNameOLextPt
                        = new GetSelectedArborAndNameOLextPtMultiThread(arborTraceOLs[i],
                                arborNameOLs[i], arborSynapseOLs[i], arborConnectedOLs[i], arborSpineOLs[i],
                                selectedNodes, start, end, extendPoints);
                threads[i] = new Thread(getSelectedArborAndNameOLextPt);
            }
            for (int i = 0; i < threadNum; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                //IJ.log("thread "+i+" returns neuriteOL = "+allNeuriteTraceOLs[i].size());
                for (int j = 0; j < impNSlice; j++) {
                    if (arborTraceOLs[i][j] != null) {
                        for (int n = 0; n < arborTraceOLs[i][j].size(); n++) {
                            arborTraceOL[j].add(arborTraceOLs[i][j].get(n));
                        }
                    }
                    if (arborNameOLs[i][j] != null) {
                        for (int n = 0; n < arborNameOLs[i][j].size(); n++) {
                            arborNameOL[j].add(arborNameOLs[i][j].get(n));
                        }
                    }
                    if (arborSpineOLs[i][j] != null) {
                        for (int n = 0; n < arborSpineOLs[i][j].size(); n++) {
                            arborSpineOL[j].add(arborSpineOLs[i][j].get(n));
                        }
                    }
                }
                if (arborSynapseOLs[i] != null) {
                    for (int n = 0; n < arborSynapseOLs[i].size(); n++) {
                        arborSynapseOL.add(arborSynapseOLs[i].get(n));
                    }
                }
                if (arborConnectedOLs[i] != null) {
                    for (int n = 0; n < arborConnectedOLs[i].size(); n++) {
                        arborConnectedOL.add(arborConnectedOLs[i].get(n));
                    }
                }
            }
        } else if (totalDisplayNodeNumber == 1) {
            getOneWholeArborRoi(arborTraceOL, arborNameOL, arborSynapseOL, arborConnectedOL, arborSpineOL,
                    (ntNeuronNode) selectedNodes.get(0), arborLine, spineLine, synapseRadius, synapseSize, extendPoints);
        }
    }

    class GetSelectedArborAndNameOLextPtMultiThread extends Thread {

        Overlay[] arborTraceOL, arborNameOL, arborSpineOL;
        Overlay arborSynapseOL, arborConnectedOL;
        ArrayList<ntNeuronNode> selectedNodes;
        int startNode, endNode, extendPoints;

        public GetSelectedArborAndNameOLextPtMultiThread(
                Overlay[] arborTraceOL, Overlay[] arborNameOL, Overlay arborSynapseOL, Overlay arborConnectedOL,
                Overlay[] arborSpineOL, ArrayList<ntNeuronNode> selectedNodes,
                int startNode, int endNode, int extendPoints
        ) {
            this.arborTraceOL = arborTraceOL;
            this.arborNameOL = arborNameOL;
            this.arborSynapseOL = arborSynapseOL;
            this.arborConnectedOL = arborConnectedOL;
            this.arborSpineOL = arborSpineOL;
            this.selectedNodes = selectedNodes;
            this.startNode = startNode;
            this.endNode = endNode;
            this.extendPoints = extendPoints;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                getOneWholeArborRoi(arborTraceOL, arborNameOL, arborSynapseOL, arborConnectedOL, arborSpineOL,
                        (ntNeuronNode) selectedNodes.get(k), arborLine, spineLine, synapseRadius, synapseSize, extendPoints);
            }
        }

    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getSelectedBranchAndNameOL">
    protected void getSelectedBranchAndNameOLMultiThread(
            Overlay branchTraceOL, Overlay branchNameOL, Overlay branchSynapseOL, Overlay branchConnectedOL, Overlay branchSpineOL) {
        // retreive all selected paths to be displayed
        if (neuronList_jTree.getSelectionCount() == 0) {
            return;
        }
        TreePath[] treePaths = neuronList_jTree.getSelectionPaths();
        //IJ.log("total selected = "+treePaths.length);
        ArrayList<ntNeuronNode> selectedNodes = new ArrayList<ntNeuronNode>();
        for (TreePath treePath : treePaths) {
            selectedNodes.add((ntNeuronNode) treePath.getLastPathComponent());
        }
        int totalDisplayNodeNumber = selectedNodes.size();
        if (totalDisplayNodeNumber > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[] branchTraceOLs = new Overlay[threadNum];
            Overlay[] branchNameOLs = new Overlay[threadNum];
            Overlay[] branchSynapseOLs = new Overlay[threadNum];
            Overlay[] branchConnectedOLs = new Overlay[threadNum];
            Overlay[] branchSpineOLs = new Overlay[threadNum];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalDisplayNodeNumber / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                branchTraceOLs[i] = new Overlay();
                branchNameOLs[i] = new Overlay();
                branchSynapseOLs[i] = new Overlay();
                branchConnectedOLs[i] = new Overlay();
                branchSpineOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalDisplayNodeNumber - 1) {
                    end = totalDisplayNodeNumber - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetSelectedBranchAndNameOLthread getSelectedBranchAndNameOL
                        = new GetSelectedBranchAndNameOLthread(branchTraceOLs[i],
                                branchNameOLs[i], branchSynapseOLs[i], branchConnectedOLs[i], branchSpineOLs[i], selectedNodes, start, end);
                threads[i] = new Thread(getSelectedBranchAndNameOL);
            }
            for (int i = 0; i < threadNum; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                //IJ.log("thread "+i+" returns neuriteOL = "+allNeuriteTraceOLs[i].size());
                if (branchTraceOLs[i] != null) {
                    for (int n = 0; n < branchTraceOLs[i].size(); n++) {
                        branchTraceOL.add(branchTraceOLs[i].get(n));
                    }
                }
                if (branchNameOLs[i] != null) {
                    for (int n = 0; n < branchNameOLs[i].size(); n++) {
                        branchNameOL.add(branchNameOLs[i].get(n));
                    }
                }
                if (branchSynapseOLs[i] != null) {
                    for (int n = 0; n < branchSynapseOLs[i].size(); n++) {
                        branchSynapseOL.add(branchSynapseOLs[i].get(n));
                    }
                }
                if (branchConnectedOLs[i] != null) {
                    for (int n = 0; n < branchConnectedOLs[i].size(); n++) {
                        branchConnectedOL.add(branchConnectedOLs[i].get(n));
                    }
                }
            }
        } else {
            ntNeuronNode selectedNode = (ntNeuronNode) selectedNodes.get(0);
            if (selectedNode.isBranchNode()) {
                Color lineColor = getNeuronColorFromNode(selectedNode, lineAlpha);
                getOneBranchTraceRoiAllPt(branchTraceOL, branchNameOL, branchSynapseOL, branchConnectedOL, branchSpineOL,
                        selectedNode, lineColor, branchLine, spineLine, synapseRadius, synapseSize);
            }
        }
    }

    class GetSelectedBranchAndNameOLthread extends Thread {

        Overlay branchTraceOL, branchNameOL, branchSynapseOL, branchConnectedOL, branchSpineOL;
        ArrayList<ntNeuronNode> selectedNodes;
        int startNode, endNode;

        public GetSelectedBranchAndNameOLthread(
                Overlay branchTraceOL, Overlay branchNameOL, Overlay branchSynapseOL, Overlay branchConnectedOL,
                Overlay branchSpineOL, ArrayList<ntNeuronNode> selectedNodes, int startNode, int endNode
        ) {
            this.branchTraceOL = branchTraceOL;
            this.branchNameOL = branchNameOL;
            this.branchSynapseOL = branchSynapseOL;
            this.branchConnectedOL = branchConnectedOL;
            this.branchSpineOL = branchSpineOL;
            this.selectedNodes = selectedNodes;
            this.startNode = startNode;
            this.endNode = endNode;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                ntNeuronNode selectedNode = selectedNodes.get(k);
                if (selectedNode.isBranchNode()) {
                    Color lineColor = getNeuronColorFromNode(selectedNode, lineAlpha);
                    getOneBranchTraceRoiAllPt(branchTraceOL, branchNameOL, branchSynapseOL, branchConnectedOL, branchSpineOL,
                            selectedNode, lineColor, branchLine, spineLine, synapseRadius, synapseSize);
                }
            }
        }

    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getSelectedBranchAndNameOLextPt">
    protected void getSelectedBranchAndNameOLextPtMultiThread(
            Overlay[] branchTraceOL, Overlay[] branchNameOL, Overlay branchSynapseOL, Overlay branchConnectedOL,
            Overlay[] branchSpineOL, int extendPoints) {
        // retreive all selected paths to be displayed
        if (neuronList_jTree.getSelectionCount() == 0) {
            return;
        }
        TreePath[] treePaths = neuronList_jTree.getSelectionPaths();
        //IJ.log("total selected = "+treePaths.length);
        ArrayList<ntNeuronNode> selectedNodes = new ArrayList<ntNeuronNode>();
        for (TreePath treePath : treePaths) {
            selectedNodes.add((ntNeuronNode) treePath.getLastPathComponent());
        }
        int totalDisplayNodeNumber = selectedNodes.size();
        if (totalDisplayNodeNumber > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[][] branchTraceOLs = new Overlay[threadNum][impNSlice];
            Overlay[][] branchNameOLs = new Overlay[threadNum][impNSlice];
            Overlay[] branchSynapseOLs = new Overlay[threadNum];
            Overlay[] branchConnectedOLs = new Overlay[threadNum];
            Overlay[][] branchSpineOLs = new Overlay[threadNum][impNSlice];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalDisplayNodeNumber / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    branchTraceOLs[i][j] = new Overlay();
                    branchNameOLs[i][j] = new Overlay();
                    branchSpineOLs[i][j] = new Overlay();
                }
                branchSynapseOLs[i] = new Overlay();
                branchConnectedOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalDisplayNodeNumber - 1) {
                    end = totalDisplayNodeNumber - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetSelectedBranchAndNameOLextPtthread getSelectedBranchAndNameOLextPt
                        = new GetSelectedBranchAndNameOLextPtthread(branchTraceOLs[i],
                                branchNameOLs[i], branchSynapseOLs[i], branchConnectedOLs[i], branchSpineOLs[i],
                                selectedNodes, start, end, extendPoints);
                threads[i] = new Thread(getSelectedBranchAndNameOLextPt);
            }
            for (int i = 0; i < threadNum; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    //IJ.log("thread "+i+" returns neuriteOL = "+allNeuriteTraceOLs[i].size());
                    if (branchTraceOLs[i][j] != null) {
                        for (int n = 0; n < branchTraceOLs[i][j].size(); n++) {
                            branchTraceOL[j].add(branchTraceOLs[i][j].get(n));
                        }
                    }
                    if (branchNameOLs[i][j] != null) {
                        for (int n = 0; n < branchTraceOLs[i][j].size(); n++) {
                            branchNameOL[j].add(branchTraceOLs[i][j].get(n));
                        }
                    }
                    if (branchSpineOLs[i][j] != null) {
                        for (int n = 0; n < branchSpineOLs[i][j].size(); n++) {
                            branchSpineOL[j].add(branchSpineOLs[i][j].get(n));
                        }
                    }
                }
                if (branchSynapseOLs[i] != null) {
                    for (int n = 0; n < branchSynapseOLs[i].size(); n++) {
                        branchSynapseOL.add(branchSynapseOLs[i].get(n));
                    }
                }
                if (branchConnectedOLs[i] != null) {
                    for (int n = 0; n < branchConnectedOLs[i].size(); n++) {
                        branchConnectedOL.add(branchConnectedOLs[i].get(n));
                    }
                }
            }
        } else if (totalDisplayNodeNumber == 1) {
            ntNeuronNode selectedNode = (ntNeuronNode) selectedNodes.get(0);
            if (selectedNode.isBranchNode()) {
                Color lineColor = getNeuronColorFromNode(selectedNode, lineAlpha);
                getOneBranchTraceRoiExtPt(branchTraceOL, branchNameOL, branchSynapseOL, branchConnectedOL, branchSpineOL,
                        selectedNode, lineColor, branchLine, spineLine, synapseRadius, synapseSize, extendPoints);
            }
        }
    }

    class GetSelectedBranchAndNameOLextPtthread extends Thread {

        Overlay[] branchTraceOL, branchNameOL, branchSpineOL;
        Overlay branchSynapseOL, branchConnectedOL;
        ArrayList<ntNeuronNode> selectedNodes;
        int startNode, endNode, extendPoints;

        public GetSelectedBranchAndNameOLextPtthread(
                Overlay[] branchTraceOL, Overlay[] branchNameOL, Overlay branchSynapseOL, Overlay branchConnectedOL,
                Overlay[] branchSpineOL, ArrayList<ntNeuronNode> selectedNodes, int startNode, int endNode, int extendPoints
        ) {
            this.branchTraceOL = branchTraceOL;
            this.branchNameOL = branchNameOL;
            this.branchSynapseOL = branchSynapseOL;
            this.branchConnectedOL = branchConnectedOL;
            this.branchSpineOL = branchSpineOL;
            this.selectedNodes = selectedNodes;
            this.startNode = startNode;
            this.endNode = endNode;
            this.extendPoints = extendPoints;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                ntNeuronNode selectedNode = selectedNodes.get(k);
                if (selectedNode.isBranchNode()) {
                    Color lineColor = getNeuronColorFromNode(selectedNode, lineAlpha);
                    getOneBranchTraceRoiExtPt(branchTraceOL, branchNameOL, branchSynapseOL, branchConnectedOL, branchSpineOL,
                            selectedNode, lineColor, branchLine, spineLine, synapseRadius, synapseSize, extendPoints);
                }
            }
        }

    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getAllSomaAndNameOL">
    protected void getAllSomaAndNameOLextPtMultiThread(
            Overlay[] somaTraceOL, Overlay[] somaNameOL, Overlay somaSynapseOL, Overlay somaConnectedOL,
            Overlay[] somaSpineOL, boolean singleSliceSynapse) {
        int totalChild = rootNeuronNode.getChildCount();
        if (totalChild == 0) {
            return;
        }
        if (totalChild > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[][] somaTraceOLs = new Overlay[threadNum][impNSlice];
            Overlay[][] somaNameOLs = new Overlay[threadNum][impNSlice];
            Overlay[] somaSynapseOLs = new Overlay[threadNum];
            Overlay[] somaConnectedOLs = new Overlay[threadNum];
            Overlay[][] somaSpineOLs = new Overlay[threadNum][impNSlice];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalChild / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    somaTraceOLs[i][j] = new Overlay();
                    somaNameOLs[i][j] = new Overlay();
                    somaSpineOLs[i][j] = new Overlay();
                }
                somaSynapseOLs[i] = new Overlay();
                somaConnectedOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalChild - 1) {
                    end = totalChild - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetAllSomaAndNameOLthread getAllSomaAndNameOL
                        = new GetAllSomaAndNameOLthread(somaTraceOLs[i], somaNameOLs[i], somaSynapseOLs[i],
                                somaConnectedOLs[i], somaSpineOLs[i], start, end, singleSliceSynapse);
                threads[i] = new Thread(getAllSomaAndNameOL);
            }
            for (int i = 0; i < threadNum; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    if (somaTraceOLs[i][j] != null) {
                        for (int n = 0; n < somaTraceOLs[i][j].size(); n++) {
                            somaTraceOL[j].add(somaTraceOLs[i][j].get(n));
                        }
                    }
                    if (somaNameOLs[i][j] != null) {
                        for (int n = 0; n < somaNameOLs[i][j].size(); n++) {
                            somaNameOL[j].add(somaNameOLs[i][j].get(n));
                        }
                    }
                    if (somaSpineOLs[i][j] != null) {
                        for (int n = 0; n < somaSpineOLs[i][j].size(); n++) {
                            somaSpineOL[j].add(somaSpineOLs[i][j].get(n));
                        }
                    }
                }
                if (somaSynapseOLs[i] != null) {
                    for (int n = 0; n < somaSynapseOLs[i].size(); n++) {
                        somaSynapseOL.add(somaSynapseOLs[i].get(n));
                    }
                }
                if (somaConnectedOLs[i] != null) {
                    for (int n = 0; n < somaConnectedOLs[i].size(); n++) {
                        somaConnectedOL.add(somaConnectedOLs[i].get(n));
                    }
                }
            }
        } else {
            ntNeuronNode childNode = (ntNeuronNode) (rootNeuronNode.getChildAt(0));
            getOneWholeSomaRoi(somaTraceOL, somaNameOL, somaSynapseOL, somaConnectedOL, somaSpineOL,
                    childNode, allSomaLine, allSpineLine, allSynapseRadius, allSynapseSize, singleSliceSynapse);
        }
    }

    class GetAllSomaAndNameOLthread extends Thread {

        Overlay[] somaTraceOL, somaNameOL, somaSpineOL;
        Overlay somaSynapseOL, somaConnectedOL;
        int startNode, endNode;
        boolean singleSliceSynapse;

        public GetAllSomaAndNameOLthread(
                Overlay[] somaTraceOL, Overlay[] somaNameOL, Overlay somaSynapseOL, Overlay somaConnectedOL,
                Overlay[] somaSpineOL, int startNode, int endNode, boolean singleSliceSynapse
        ) {
            this.somaTraceOL = somaTraceOL;
            this.somaNameOL = somaNameOL;
            this.somaSynapseOL = somaSynapseOL;
            this.somaConnectedOL = somaConnectedOL;
            this.somaSpineOL = somaSpineOL;
            this.startNode = startNode;
            this.endNode = endNode;
            this.singleSliceSynapse = singleSliceSynapse;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                ntNeuronNode somaNode = (ntNeuronNode) (rootAllSomaNode.getChildAt(k));
                getOneWholeSomaRoi(somaTraceOL, somaNameOL, somaSynapseOL, somaConnectedOL, somaSpineOL,
                        somaNode, allSomaLine, allSpineLine, allSynapseRadius, allSynapseSize, singleSliceSynapse);
            }
        }

    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="inner Class for multi-threading -- getSelectedSomaAndNameOL">
    protected void getSelectedSomaAndNameOLextPtMultiThread(
            Overlay[] somaTraceOL, Overlay[] somaNameOL, Overlay somaSynapseOL,
            Overlay somaConnectedOL, Overlay[] somaSpineOL, boolean singleSliceSynapse) {
        // retreive all selected paths to be displayed
        if (neuronList_jTree.getSelectionCount() == 0) {
            return;
        }
        TreePath[] treePaths = neuronList_jTree.getSelectionPaths();
        //IJ.log("total selected = "+treePaths.length);
        ArrayList<ntNeuronNode> selectedNodes = new ArrayList<ntNeuronNode>();
        for (TreePath treePath : treePaths) {
            selectedNodes.add((ntNeuronNode) treePath.getPathComponent(1));
            //IJ.log(" soma = "+selectedNodes.toString());
        }

        removeRedundantNode(selectedNodes);

        int totalDisplayNodeNumber = selectedNodes.size();
        //IJ.log("reduce to "+totalDisplayNodeNumber);
        if (totalDisplayNodeNumber > 1) {
            //IJ.log("totalChild = "+totalChild+"; threadNum = "+threadNum);
            Overlay[][] somaTraceOLs = new Overlay[threadNum][impNSlice];
            Overlay[][] somaNameOLs = new Overlay[threadNum][impNSlice];
            Overlay[] somaSynapseOLs = new Overlay[threadNum];
            Overlay[] somaConnectedOLs = new Overlay[threadNum];
            Overlay[][] somaSpineOLs = new Overlay[threadNum][impNSlice];
            Thread[] threads = new Thread[threadNum];
            int increament = (int) Math.ceil((double) totalDisplayNodeNumber / (double) threadNum);
            //IJ.log("increament = "+increament);
            for (int i = 0; i < threadNum; i++) {
                for (int j = 0; j < impNSlice; j++) {
                    somaTraceOLs[i][j] = new Overlay();
                    somaNameOLs[i][j] = new Overlay();
                    somaSpineOLs[i][j] = new Overlay();
                }
                somaSynapseOLs[i] = new Overlay();
                somaConnectedOLs[i] = new Overlay();
                int start = increament * i;
                int end = increament * (i + 1) - 1;
                if (end > totalDisplayNodeNumber - 1) {
                    end = totalDisplayNodeNumber - 1;
                }
                //IJ.log("i = "+i+"; start = "+start+" end = "+end);
                GetSelectedSomaAndNameOLthread getSelectedSomaAndNameOL
                        = new GetSelectedSomaAndNameOLthread(somaTraceOLs[i], somaNameOLs[i], somaSynapseOLs[i],
                                somaConnectedOLs[i], somaSpineOLs[i], selectedNodes, start, end, singleSliceSynapse);
                threads[i] = new Thread(getSelectedSomaAndNameOL);
            }
            for (int i = 0; i < threadNum; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadNum; i++) {
                try {
                    threads[i].join();
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < threadNum; i++) {
                //IJ.log("thread "+i+" returns neuriteOL = "+allNeuriteTraceOLs[i].size());
                for (int j = 0; j < impNSlice; j++) {
                    if (somaTraceOLs[i][j] != null) {
                        for (int n = 0; n < somaTraceOLs[i][j].size(); n++) {
                            somaTraceOL[j].add(somaTraceOLs[i][j].get(n));
                        }
                    }
                    if (somaNameOLs[i][j] != null) {
                        for (int n = 0; n < somaNameOLs[i][j].size(); n++) {
                            somaNameOL[j].add(somaNameOLs[i][j].get(n));
                        }
                    }
                    if (somaSpineOLs[i][j] != null) {
                        for (int n = 0; n < somaSpineOLs[i][j].size(); n++) {
                            somaSpineOL[j].add(somaSpineOLs[i][j].get(n));
                        }
                    }
                }
                if (somaSynapseOLs[i] != null) {
                    for (int n = 0; n < somaSynapseOLs[i].size(); n++) {
                        somaSynapseOL.add(somaSynapseOLs[i].get(n));
                    }
                }
                if (somaConnectedOLs[i] != null) {
                    for (int n = 0; n < somaConnectedOLs[i].size(); n++) {
                        somaConnectedOL.add(somaConnectedOLs[i].get(n));
                    }
                }
            }
        } else if (totalDisplayNodeNumber == 1) {
            ntNeuronNode childNode = (ntNeuronNode) (selectedNodes.get(0));
            getOneWholeSomaRoi(somaTraceOL, somaNameOL, somaSynapseOL, somaConnectedOL, somaSpineOL,
                    childNode, somaLine, spineLine, synapseRadius, synapseSize, singleSliceSynapse);
        }
    }

    class GetSelectedSomaAndNameOLthread extends Thread {

        Overlay[] somaTraceOL, somaNameOL, somaSpineOL;
        Overlay somaSynapseOL, somaConnectedOL;
        ArrayList<ntNeuronNode> selectedSomaNodes;
        int startNode, endNode;
        boolean singleSliceSynapse;

        public GetSelectedSomaAndNameOLthread(
                Overlay[] somaTraceOL, Overlay[] somaNameOL, Overlay somaSynapseOL, Overlay somaConnectedOL,
                Overlay[] somaSpineOL, ArrayList<ntNeuronNode> selectedSomaNodes, int startNode, int endNode,
                boolean singleSliceSynapse
        ) {
            this.somaTraceOL = somaTraceOL;
            this.somaNameOL = somaNameOL;
            this.somaSynapseOL = somaSynapseOL;
            this.somaConnectedOL = somaConnectedOL;
            this.somaSpineOL = somaSpineOL;
            this.selectedSomaNodes = selectedSomaNodes;
            this.startNode = startNode;
            this.endNode = endNode;
            this.singleSliceSynapse = singleSliceSynapse;
        }

        @Override
        public void run() {
            // overlay neuron traces (neuronNode is the soma node)
            for (int k = startNode; k <= endNode; k++) {
                ntNeuronNode somaNode = (ntNeuronNode) (selectedSomaNodes.get(k));
                getOneWholeSomaRoi(somaTraceOL, somaNameOL, somaSynapseOL, somaConnectedOL, somaSpineOL,
                        somaNode, somaLine, spineLine, synapseRadius, synapseSize, singleSliceSynapse);
            }
        }

    }
    // </editor-fold>

    private void removeRedundantNode(ArrayList<ntNeuronNode> nodeArray) {
        //long sT = System.currentTimeMillis();
        for (int i = nodeArray.size() - 1; i >= 0; i--) {
            String currentNodeName = ((ntNeuronNode) nodeArray.get(i)).toString();
            for (int n = i - 1; n >= 0; n--) {
                String compareNodeName = ((ntNeuronNode) nodeArray.get(n)).toString();
                if (currentNodeName.equals(compareNodeName)) {
                    //IJ.log("remove "+compareNodeName);
                    nodeArray.remove(i);
                    break;
                }
            }
        }
        //long eT = System.currentTimeMillis();
        //IJ.log("sort time = "+(eT-sT));
    }
    // <editor-fold defaultstate="collapsed" desc="inner Class for neuronTree / somaTree / pointTable response ">

    /**
     * inner class defining PointTable column listener events
     */
    class ntPointSelectionListener implements ListSelectionListener {

        JTable pointTable;
        // It is necessary to keep the table since it is not possible
        // to determine the table from the event's source

        ntPointSelectionListener(JTable pointTable) {
            this.pointTable = pointTable;
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            // If cell selection is enabled, both row and column change events are fired
            // Response to row selection
            if (e.getSource() == pointTable.getSelectionModel()
                    && pointTable.getRowSelectionAllowed()) {
                // get the first selected point
//                int [] traceTableRowIndex = traceTable.getSelectedRows();
                int[] pointTableRowIndex = pointTable.getSelectedRows();
                if (pointTableRowIndex.length == 1) {
                    // cell coordinates in JTable start from (1, 0), not (0, 0) (header takes the 0 row)
                    crossX = ((Float) (pointTable.getValueAt(pointTableRowIndex[0], 1))).intValue();
                    crossY = ((Float) (pointTable.getValueAt(pointTableRowIndex[0], 2))).intValue();
                    crossZ = ((Float) (pointTable.getValueAt(pointTableRowIndex[0], 3))).intValue();
//                    IJ.log("xOut"+crossX+" yIn"+crossY+" z"+impSlice);
                    startPoint[1] = crossX;
                    startPoint[2] = crossY;
                    startPoint[3] = crossZ;
                    startPosition_jLabel.setText("(" + startPoint[1] + ", " + startPoint[2] + ", " + startPoint[3] + ")");
                    String[] startInfo = getPointIntColorInfo(startPoint, analysisChannels);
                    startIntensity_jLabel.setText(startInfo[0]);
                    startColor_jLabel.setText(startInfo[1]);
                    hasStartPt = true;
                    imp.setZ(crossZ);
                    //updateDisplay();
                    updatePointBox();
                    connectedSynapse_jLabel.setText("=> " + (String) pointTable.getValueAt(pointTableRowIndex[0], 6));
                } else {
                    connectedSynapse_jLabel.setText("=> 0");
                }
                clearStartEndPts();
            }

            // OR Response to column selection
            //else if (e.getSource() == table.getColumnModel().getSelectionModel()
            //       && table.getColumnSelectionAllowed() ){}
            // Response to the mouse button has not yet been released
            //if (e.getValueIsAdjusting()) {}
        }
    }

    /**
     * inner class defining neuronList_jTree listener events
     */
    class ntNeuriteTreeSelectionListener implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent e) {
            canUpdateDisplay = false;
            //IJ.log("NeuriteTreeSelection changed");
            String[] point = {"0", "-1", "-1", "-1", "0", "0", "0"};
            ArrayList<String[]> data = new ArrayList<String[]>();
            data.add(point);
            ntNeuronNode displaySomaNode = new ntNeuronNode("Soma", data);
            // if selected more than 1 row, or nothing is selected or root node is selected
            // displaySomaList_jTree is deselected and tablePoints is cleared
            tablePoints = new ArrayList<String[]>();
            if (neuronList_jTree.getSelectionCount() == 1) {
                ntNeuronNode selectedNeuronNode = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
                // select neurite in the neuron tree
                if (selectedNeuronNode.isBranchNode()) {
                    tablePoints = selectedNeuronNode.getTracingResult();
                    //IJ.log(tablePoints.get(0)[1]+" is neurite!");
                } // select soma in the neuron tree  
                else {
                    displaySomaNode = getSomaNodeFromAllSomaTreeByNeuronNumber(selectedNeuronNode.getNeuronNumber());
                }
            }
            /*
             // check whether selected neuron is the same as the Second Selection
             if (!"0".equals(secondSelectionName)) {
             TreePath[] selectedPaths = neuronList_jTree.getSelectionPaths();
             if (selectedPaths.length > 0) {
             String secondSomaName = secondSelectionName.contains("-") ? secondSelectionName.split("-")[0] : secondSelectionName.split(":")[0];
             for (TreePath selectedPath : selectedPaths) {
             ntNeuronNode selectedSomaNode = (ntNeuronNode) selectedPath.getPathComponent(1);
             if (selectedSomaNode.toString().equals(secondSomaName)) {
             secondSelectionName = "0";
             secondSelectedName_jLabel.setText(secondSelectionName);
             break;
             }
             }
             }
             }
             */
            //Make sure update the soma node.
            rootDisplaySomaNode = displaySomaNode;
            displaySomaTreeModel.setRoot(rootDisplaySomaNode);
            update.updatePointTable(tablePoints);
            canUpdateDisplay = true;
            if (canUpdateDisplay) {
                updateDisplay();
            }
            //IJ.log("NeuriteTreeSelection change complete");
        }
    }

    /**
     * inner class defining neuronList_jTree expansion listener events
     */
    class ntNeuriteTreeExpansionListener implements TreeExpansionListener {

        @Override
        public void treeCollapsed(TreeExpansionEvent event) {
        }

        @Override
        public void treeExpanded(TreeExpansionEvent event) {
            expandAll(neuronList_jTree, event.getPath());
        }

        public void collapseAll(JTree tree, TreePath parent) {
            ntNeuronNode node = (ntNeuronNode) parent.getLastPathComponent();
            if (node.getChildCount() >= 0) {
                for (Enumeration e = node.children(); e.hasMoreElements();) {
                    ntNeuronNode n = (ntNeuronNode) e.nextElement();
                    TreePath path = parent.pathByAddingChild(n);
                    collapseAll(tree, path);
                }
            }
            tree.collapsePath(parent);

        }

        public void expandAll(JTree tree, TreePath parent) {
            ntNeuronNode node = (ntNeuronNode) parent.getLastPathComponent();
            if (node.getChildCount() >= 0) {
                for (Enumeration e = node.children(); e.hasMoreElements();) {
                    ntNeuronNode n = (ntNeuronNode) e.nextElement();
                    TreePath path = parent.pathByAddingChild(n);
                    expandAll(tree, path);
                }
            }
            tree.expandPath(parent);
        }
    }

    /**
     * inner class defining somaList_jTree listener events
     */
    class ntSomaTreeSelectionListener implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent e) {
            // if selected more than 1 row, or nothing is selected or root node is selected
            // displaySomaList_jTree is deselected and tablePoints is cleared
            tablePoints = new ArrayList<String[]>();
            if (displaySomaList_jTree.getSelectionCount() == 1) {
                ntNeuronNode somaZ = (ntNeuronNode) displaySomaList_jTree.getLastSelectedPathComponent();
                tablePoints = somaZ.getTracingResult();
            } else if (displaySomaList_jTree.getSelectionCount() == 0) {
                if (neuronList_jTree.getSelectionCount() == 1) {
                    ntNeuronNode node = (ntNeuronNode) neuronList_jTree.getLastSelectedPathComponent();
                    // select neurite in the neuron tree
                    if (node.isBranchNode()) {
                        tablePoints = node.getTracingResult();
                        //IJ.log(tablePoints.get(0)[1]+" is neurite!");
                    }
                }
            }
            update.updatePointTable(tablePoints);
            if (canUpdateDisplay) {
                updateDisplay();
            }
        }
    }
    // </editor-fold>

}
