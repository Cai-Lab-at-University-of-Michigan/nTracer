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
import ij.Prefs;
import ij.WindowManager;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.plugin.HyperStackConverter;
import java.awt.Rectangle;

import java.io.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.tree.DefaultTreeModel;
import static nTracer.nTracer_.imp;

/**
 *
 * @author Dawen
 */
public class ntIO {

    public ntIO() {
        Functions = new ntTracing();
        analysis = new ntAnalysis();
    }

    public ImagePlus loadImage() {
        IJ.open();
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.error("No Image Opened!");
            return null;
        } 
        //else if (!imp.isComposite()) {
        //    IJ.error("Requires Composite Image!");
        //    imp.close();
        //    return null;
        //}
        Prefs.requireControlKey = true;
        return imp;
    }

    public ImagePlus importSequence() {
        IJ.run("Image Sequence...");
        IJ.run("Stack to Hyperstack...");
        ImagePlus imp = WindowManager.getCurrentImage();

        if (imp == null) {
            IJ.error("No Image Opened!");
            return null;
        } else if (!imp.isComposite()) {
            IJ.error("Requires Composite Image!");
            imp.close();
            return null;
        }
        Prefs.requireControlKey = true;
        return imp;
    }

    public ImagePlus importChannels() {
        File dir = null;
        JFileChooser fc;
        try {
            fc = new JFileChooser();
        } catch (Throwable e) {
            IJ.error("This plugin requires Java 2 or Swing.");
            return null;
        }
        fc.setMultiSelectionEnabled(true);
        if (dir == null) {
            String sdir = OpenDialog.getDefaultDirectory();
            if (sdir != null) {
                dir = new File(sdir);
            }
        }
        if (dir != null) {
            fc.setCurrentDirectory(dir);
        }
        int returnVal = fc.showOpenDialog(IJ.getInstance());
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File[] files = fc.getSelectedFiles();
        if (files.length == 0) { // getSelectedFiles does not work on some JVMs
            files = new File[1];
            files[0] = fc.getSelectedFile();
        }
        String path = fc.getCurrentDirectory().getPath() + Prefs.getFileSeparator();
        Opener opener = new Opener();
        for (File file : files) {
            ImagePlus img = opener.openImage(path, file.getName());
            if (img != null) {
                img.show();
            }
        }
        IJ.run("Merge Channels...", "");
        ImagePlus imp = WindowManager.getCurrentImage();
        return imp;
    }

    public void saveFormatedNeuriteData(ntNeuronNode rootNeuriteNode,
            String directory, String fileName, String fileExtension,
            double x_spacing, double y_spacing, double z_spacing,
            String spacing_units, int width, int height, int depth
    ) {
        try { // write tracing results to file by recursion
            writeAllResults2txt(directory, fileName + ".txt", rootNeuriteNode);
            if (fileExtension.equals("xml")) {
                writeAllResults2xml(directory + fileName + ".xml", rootNeuriteNode,
                        x_spacing, y_spacing, z_spacing, spacing_units,
                        width, height, depth);
            }
        } catch (IOException e) {
            IJ.error("Error in saving data: " + e.getMessage());
        }
    }

    private void writeAllPoints2txt(BufferedWriter bufferedWriter,
            ntNeuronNode rootNeuriteNode) throws IOException {
        for (int k = 0; k < rootNeuriteNode.getChildCount(); k++) {
            ntNeuronNode childNode = (ntNeuronNode) (rootNeuriteNode.getChildAt(k));

            // write tracing and display image parameters
            bufferedWriter.write("TotalChannel: " + nTracer_.impNChannel);
            bufferedWriter.newLine();
            bufferedWriter.write("ToggleColor: " + nTracer_.toggleColor);
            bufferedWriter.newLine();
            bufferedWriter.write("ToggleChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                bufferedWriter.write(nTracer_.toggleChannels[ch] + " ");
            }
            bufferedWriter.newLine();
            bufferedWriter.write("AnalysisChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                bufferedWriter.write(nTracer_.analysisChannels[ch] + " ");
            }
            bufferedWriter.newLine();
            bufferedWriter.write("ActiveChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                bufferedWriter.write(nTracer_.activeChannels[ch] + " ");
            }
            bufferedWriter.newLine();

            // write node name
            bufferedWriter.write("Neuron " + childNode.toString());
            bufferedWriter.newLine();
            // write node data
            ArrayList<String[]> data = childNode.getTracingResult();
            for (String[] point : data) {
                bufferedWriter.write("POINT: ");
                bufferedWriter.write(point[0] + " ");
                bufferedWriter.write(point[1] + " ");
                bufferedWriter.write(point[2] + " ");
                bufferedWriter.write(point[3] + " ");
                bufferedWriter.write(point[4] + " ");
            }
            bufferedWriter.write("END");
            bufferedWriter.newLine();
            writeAllPoints2txt(bufferedWriter, childNode);
        }
    }

    private void writeAllResults2txt(String directory, String fileName,
            ntNeuronNode rootNeuriteNode) throws IOException {
        File dataFile = new File(directory, fileName);
        FileWriter fileWriter = new FileWriter(dataFile);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        try {
            writeAllPoints2txt(bufferedWriter, rootNeuriteNode);
        } finally {
            bufferedWriter.close();
            fileWriter.close();
        }
    }

    private void writeAllPoints2XML(PrintWriter pw, ntNeuronNode rootNeuriteNode,
            double x_spacing, double y_spacing, double z_spacing) {
        for (int k = 0; k < rootNeuriteNode.getChildCount(); k++) {
            ntNeuronNode childNode = (ntNeuronNode) (rootNeuriteNode.getChildAt(k));
            ArrayList<int[]> data = Functions.convertStringArray2IntArray(childNode.getTracingResult());

            pw.print("  <path id=\"" + childNode.toString() + "\"");
            pw.print(" swctype=\"" + "branch" + "\"");
            String startsString = "";
            String endsString = "";

            ntNeuronNode parentNode = (ntNeuronNode) childNode.getParent();
            if (parentNode.isRoot()) {
                pw.print(" primary=\"true\"");
            } else {

                int[] startJoinsPoint = data.get(0);
                startsString = " startson=\"" + parentNode.toString() + "\""
                        + " startx=\"" + startJoinsPoint[0] + "\""
                        + " starty=\"" + startJoinsPoint[1] + "\""
                        + " startz=\"" + startJoinsPoint[2] + "\"";
            }
            pw.print(startsString);
            if (!childNode.isLeaf()) {
                for (int l = 0; l < 2; l++) {
                    ntNeuronNode grandchildNode = (ntNeuronNode) (rootNeuriteNode.getChildAt(l));
                    String[] endJoinsPoint = grandchildNode.getTracingResult().get(0);
                    endsString = " endson=\"" + grandchildNode.toString() + "\""
                            + " endsx=\"" + endJoinsPoint[0] + "\""
                            + " endsy=\"" + endJoinsPoint[1] + "\""
                            + " endsz=\"" + endJoinsPoint[2] + "\"";
                    if (l == 0) {
                        endsString += "\n";
                    }
                }
            }
            pw.print(endsString);
            pw.println(">");

            // write node data
            for (int[] point : data) {
                double pxd = (double) point[0] * x_spacing;
                double pyd = (double) point[1] * y_spacing;
                double pzd = (double) point[2] * z_spacing;
                String attributes = "x=\"" + point[0] + "\" " + "y=\"" + point[1] + "\" z=\"" + point[2] + "\" "
                        + "xd=\"" + pxd + "\" yd=\"" + pyd + "\" zd=\"" + pzd + "\"";
                pw.println("    <point " + attributes + "/>");
            }
            pw.println("  </path>");

            writeAllPoints2XML(pw, childNode, x_spacing, y_spacing, z_spacing);
        }
    }

    private void writeAllResults2xml(String filePath, ntNeuronNode rootNeuriteNode,
            double x_spacing, double y_spacing, double z_spacing,
            String spacing_units, int width, int height, int depth) throws IOException {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(filePath), "UTF-8"));

            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<!DOCTYPE tracings [");
            pw.println("  <!ELEMENT tracings       (samplespacing,imagesize,path*,fill*)>");
            pw.println("  <!ELEMENT imagesize      EMPTY>");
            pw.println("  <!ELEMENT samplespacing  EMPTY>");
            pw.println("  <!ELEMENT path           (point+)>");
            pw.println("  <!ELEMENT point          EMPTY>");
            pw.println("  <!ELEMENT fill           (node*)>");
            pw.println("  <!ELEMENT node           EMPTY>");
            pw.println("  <!ATTLIST samplespacing  x                 CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST samplespacing  y                 CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST samplespacing  z                 CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST samplespacing  units             CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST imagesize      width             CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST imagesize      height            CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST imagesize      depth             CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST path           id                CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST path           primary           CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           name              CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           startson          CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           startsindex       CDATA           #IMPLIED>"); // deprecated
            pw.println("  <!ATTLIST path           startsx           CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           startsy           CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           startsz           CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           endson            CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           endsindex         CDATA           #IMPLIED>"); // deprecated
            pw.println("  <!ATTLIST path           endsx             CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           endsy             CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           endsz             CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           reallength        CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           usefitted         (true|false)    #IMPLIED>");
            pw.println("  <!ATTLIST path           fitted            CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           fittedversionof   CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST path           swctype           CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST point          x                 CDATA           #REQUIRED>"); // deprecated
            pw.println("  <!ATTLIST point          y                 CDATA           #REQUIRED>"); // deprecated
            pw.println("  <!ATTLIST point          z                 CDATA           #REQUIRED>"); // deprecated
            pw.println("  <!ATTLIST point          xd                CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST point          yd                CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST point          zd                CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST point          tx                CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST point          ty                CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST point          tz                CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST point          r                 CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST fill           id                CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST fill           frompaths         CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST fill           metric            CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST fill           threshold         CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST fill           volume            CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST node           id                CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST node           x                 CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST node           y                 CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST node           z                 CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST node           previousid        CDATA           #IMPLIED>");
            pw.println("  <!ATTLIST node           distance          CDATA           #REQUIRED>");
            pw.println("  <!ATTLIST node           status            (open|closed)   #REQUIRED>");
            pw.println("]>");
            pw.println("");

            pw.println("<tracings>");
            pw.println("  <samplespacing x=\"" + x_spacing + "\" "
                    + "y=\"" + y_spacing + "\" "
                    + "z=\"" + z_spacing + "\" "
                    + "units=\"" + spacing_units + "\"/>");
            pw.println("  <imagesize width=\"" + width + "\" height=\"" + height + "\" depth=\"" + depth + "\"/>");
            writeAllPoints2XML(pw, rootNeuriteNode,
                    x_spacing, y_spacing, z_spacing);
            pw.println("</tracings>");
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }

    public ArrayList[] loadExpansionSelectionPositionParameter(InputStream stream) throws IOException {
        ArrayList<String> neuronExpansion = new ArrayList<String>();
        ArrayList<String> neuriteSelection = new ArrayList<String>();
        ArrayList<String> neuriteVisibleRectangle = new ArrayList<String>();
        ArrayList<String> somaSelection = new ArrayList<String>();
        ArrayList<String> somaVisibleRectangle = new ArrayList<String>();
        ArrayList<String> pointSelection = new ArrayList<String>();
        ArrayList<String> pointVisibleRectangle = new ArrayList<String>();
        ArrayList<String> imagePosition = new ArrayList<String>();
        ArrayList<String> nTracerParameter = new ArrayList<String>();
        ArrayList[] status = {neuronExpansion, neuriteSelection, neuriteVisibleRectangle,
            somaSelection, somaVisibleRectangle, pointSelection, pointVisibleRectangle,
            imagePosition, nTracerParameter};

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        Scanner loadScanner = new Scanner(bufferedReader);
        try {
            if ((loadScanner.next()).equals("NeuriteExpansion:")) {
                while (!(loadScanner.next()).equals("END")) {
                    neuronExpansion.add(loadScanner.next());
                }
            }
            if ((loadScanner.next()).equals("NeuriteSelection:")) {
                while (!(loadScanner.next()).equals("END")) {
                    neuriteSelection.add(loadScanner.next());
                }
            }
            if ((loadScanner.next()).equals("NeuriteVisibleRectangle:")) {
                while (!(loadScanner.next()).equals("END")) {
                    neuriteVisibleRectangle.add(loadScanner.next());
                }
            }
            if ((loadScanner.next()).equals("SomaSelection:")) {
                while (!(loadScanner.next()).equals("END")) {
                    somaSelection.add(loadScanner.next());
                }
            }
            if ((loadScanner.next()).equals("SomaVisibleRectangle:")) {
                while (!(loadScanner.next()).equals("END")) {
                    somaVisibleRectangle.add(loadScanner.next());
                }
            }
            if ((loadScanner.next()).equals("PointSelection:")) {
                while (!(loadScanner.next()).equals("END")) {
                    pointSelection.add(loadScanner.next());
                }
            }
            if ((loadScanner.next()).equals("PointVisibleRectangle:")) {
                while (!(loadScanner.next()).equals("END")) {
                    pointVisibleRectangle.add(loadScanner.next());
                }
            }
            if ((loadScanner.next()).equals("Positions:")) {
                while (!(loadScanner.next()).equals("END")) {
                    imagePosition.add(loadScanner.next());
                }
            }
            if ((loadScanner.next()).equals("Parameters:")) {
                while (!(loadScanner.next()).equals("END")) {
                    nTracerParameter.add(loadScanner.next());
                }
            }
            return status;
        } finally {
            loadScanner.close();
        }
    }

    public DefaultTreeModel[] loadTracingParametersAndNeurons(InputStream stream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        Scanner loadScanner = new Scanner(bufferedReader);
        ntNeuronNode rootSomaNode = new ntNeuronNode("Traced Soma", new ArrayList<String[]>());
        DefaultTreeModel somaTreeModel = new DefaultTreeModel(rootSomaNode);
        ntNeuronNode rootNeuronNode = new ntNeuronNode("Traced Neuron", new ArrayList<String[]>());
        DefaultTreeModel neuronTreeModel = new DefaultTreeModel(rootNeuronNode);
        ntNeuronNode rootSpineNode = new ntNeuronNode("Traced Spine", new ArrayList<String[]>());
        DefaultTreeModel spineTreeModel = new DefaultTreeModel(rootSpineNode);

        try {
            int savedTotalCh = 0, loadTotalCh = 0;
            while (loadScanner.hasNext()) {
                String nextString = loadScanner.next();
                if (nextString.equals("TotalChannel:")) {
                    savedTotalCh = Integer.parseInt(loadScanner.next());
                    if (savedTotalCh != nTracer_.impNChannel) {
                        IJ.error("The opened image has " + nTracer_.impNChannel
                                + " channels, while the result file recorded " + savedTotalCh + "channels !");
                        loadTotalCh = nTracer_.impNChannel < savedTotalCh ? nTracer_.impNChannel : savedTotalCh;
                    } else {
                        loadTotalCh = savedTotalCh;
                        //IJ.log("The opened image has " + nTracer_.impNChannel
                        //        + " channels, while the result file recorded " + savedTotalCh + "channels !");
                    }
                }
                if (nextString.equals("ToggleColor:")) {
                    nTracer_.toggleColor = loadScanner.next();
                    //IJ.log("ToggleColor: "+nTracer_.toggleColor);
                }
                if (nextString.equals("ToggleChannels:")) {
                    //IJ.log("ToggleChannels: ");
                    for (int ch = 0; ch < loadTotalCh; ch++) {
                        nTracer_.toggleChannels[ch] = Boolean.parseBoolean(loadScanner.next());
                        //IJ.log(nTracer_.toggleChannels[ch]+"");
                    }
                    for (int ch = loadTotalCh; ch < nTracer_.impNChannel; ch++) {
                        nTracer_.toggleChannels[ch] = Boolean.parseBoolean(loadScanner.next());
                        //IJ.log(nTracer_.toggleChannels[ch]+"");
                    }
                }
                if (nextString.equals("AnalysisChannels:")) {
                    //IJ.log("AnalysisChannels: ");
                    for (int ch = 0; ch < loadTotalCh; ch++) {
                        nTracer_.analysisChannels[ch] = Boolean.parseBoolean(loadScanner.next());
                        //IJ.log(nTracer_.analysisChannels[ch]+"");
                    }
                    for (int ch = loadTotalCh; ch < nTracer_.impNChannel; ch++) {
                        nTracer_.analysisChannels[ch] = Boolean.parseBoolean(loadScanner.next());
                        //IJ.log(nTracer_.analysisChannels[ch]+"");
                    }
                }
                if (nextString.equals("ActiveChannels:")) {
                    //IJ.log("ActiveChannels: ");
                    for (int ch = 0; ch < loadTotalCh; ch++) {
                        nTracer_.activeChannels[ch] = Boolean.parseBoolean(loadScanner.next());
                        //IJ.log(nTracer_.activeChannels[ch]+"");
                    }
                    for (int ch = loadTotalCh; ch < nTracer_.impNChannel; ch++) {
                        nTracer_.activeChannels[ch] = Boolean.parseBoolean(loadScanner.next());
                        //IJ.log(nTracer_.activeChannels[ch]+"");
                    }
                }
                if (nextString.equals("xyzResolutions:")) {
                    nTracer_.xyzResolutions[0] = Double.parseDouble(loadScanner.next());
                    nTracer_.xyzResolutions[1] = Double.parseDouble(loadScanner.next());
                    nTracer_.xyzResolutions[2] = Double.parseDouble(loadScanner.next());
                    //IJ.log("xyzResolutions: "+nTracer_.xyzResolutions[0]+", "+nTracer_.xyzResolutions[1]+", "+nTracer_.xyzResolutions[2]);
                }
                if (nextString.equals("Neuron")) {
                    String neuronName = loadScanner.next();
                    //IJ.log("Neuron "+neuronName);
                    // create a new soma and insert into rootNeuronNode
                    String[] somaNeuronPoint = {"0", "-1", "-1", "-1", "0", "0", "0"};
                    ArrayList<String[]> somaNuronData = new ArrayList<String[]>();
                    somaNuronData.add(somaNeuronPoint);
                    ntNeuronNode somaNeuronNode = new ntNeuronNode(neuronName, somaNuronData);
                    neuronTreeModel.insertNodeInto(somaNeuronNode, rootNeuronNode, rootNeuronNode.getChildCount());
                    // icreate a new soma and insert into rootSomaNode
                    String[] somaSomaPoint = {"0", "-1", "-1", "-1", "0", "0", "0"};
                    ArrayList<String[]> somaSomaData = new ArrayList<String[]>();
                    somaSomaData.add(somaSomaPoint);
                    ntNeuronNode somaSomaNode = new ntNeuronNode(neuronName, somaSomaData);
                    somaTreeModel.insertNodeInto(somaSomaNode, rootSomaNode, rootSomaNode.getChildCount());
                    //IJ.log("created new soma " + neuronName);

                    // continue loading data for the whole neuron
                    String type = loadScanner.next();
                    while (!(type.equals("ENDneuron"))) {
                        String nodeName = loadScanner.next();
                        if (nodeName.contains("/")) {
                            nodeName = nodeName.split("/")[0];
                        }
                        // load soma data
                        if (nodeName.contains(":")) {
                            String[] parentNames = nodeName.split(":"); // parentName[0] = soma name; parentName[1] = soma Z
                            //IJ.log("load soma "+parentNames[0] + " : " + parentNames[1]);
                            int zPlane = Integer.parseInt(parentNames[1]);
                            if (zPlane >= 0) {
                                // insert somaChildNode into somaSomaNode
                                ArrayList<String[]> somaNodeData = new ArrayList<String[]>();
                                while (!(loadScanner.next()).equals("END")) {
                                    String[] nodePoint = new String[7];
                                    nodePoint[0] = loadScanner.next();
                                    nodePoint[1] = loadScanner.next();
                                    nodePoint[2] = loadScanner.next();
                                    nodePoint[3] = loadScanner.next();
                                    nodePoint[4] = loadScanner.next();
                                    nodePoint[5] = loadScanner.next();
                                    nodePoint[6] = loadScanner.next();
                                    somaNodeData.add(nodePoint);
                                }
                                ntNeuronNode somaChildNode = new ntNeuronNode(nodeName, somaNodeData);
                                somaTreeModel.insertNodeInto(somaChildNode, somaSomaNode, somaSomaNode.getChildCount());
                                //IJ.log("soma "+nodeName + " inserted");
                            }
                        } // load neurite sub-branch data
                        else if (nodeName.contains("-")) {
                            String[] nodeNames = nodeName.split("-");
                            String branchIndexName = nodeNames[nodeNames.length - 1];
                            String parentName = nodeName.substring(0, nodeName.length() - branchIndexName.length() - 1);
                            //IJ.log("nodeName = "+nodeName+"-"+branchIndexName);
                            //IJ.log("parentName = "+parentName);
                            ArrayList<String[]> neuriteNodeData = new ArrayList<String[]>();
                            while (!(loadScanner.next()).equals("END")) {
                                String[] nodePoint = new String[7];
                                nodePoint[0] = loadScanner.next();
                                nodePoint[1] = loadScanner.next();
                                nodePoint[2] = loadScanner.next();
                                nodePoint[3] = loadScanner.next();
                                nodePoint[4] = loadScanner.next();
                                nodePoint[5] = loadScanner.next();
                                nodePoint[6] = loadScanner.next();
                                neuriteNodeData.add(nodePoint);
                            }
                            ntNeuronNode neuriteChildNode = new ntNeuronNode(nodeName, neuriteNodeData);
                            TreePath neuriteParentPath = findPath2Name(rootNeuronNode, parentName);
                            ntNeuronNode neuriteParentNode = (ntNeuronNode) neuriteParentPath.getLastPathComponent();
                            neuronTreeModel.insertNodeInto(neuriteChildNode, neuriteParentNode, neuriteParentNode.getChildCount());
                            //IJ.log("neurite "+nodeName+" inserted");
                        }
                        type = loadScanner.next();
                    }
                }
                if (nextString.equals("Spine")) {
                        String spineName = loadScanner.next();
                        ArrayList<String[]> spineNodeData = new ArrayList<String[]>();
                        while (!(loadScanner.next()).equals("ENDspine")) {
                            String[] nodePoint = new String[7];
                            nodePoint[0] = loadScanner.next();
                            nodePoint[1] = loadScanner.next();
                            nodePoint[2] = loadScanner.next();
                            nodePoint[3] = loadScanner.next();
                            nodePoint[4] = loadScanner.next();
                            nodePoint[5] = loadScanner.next();
                            nodePoint[6] = loadScanner.next();
                            spineNodeData.add(nodePoint);
                        }
                        ntNeuronNode spineNode = new ntNeuronNode(spineName, spineNodeData);
                        spineTreeModel.insertNodeInto(spineNode, rootSpineNode, rootSpineNode.getChildCount());
                }
            }
            somaTreeModel.nodeStructureChanged(rootSomaNode);
            neuronTreeModel.nodeStructureChanged(rootNeuronNode);
            spineTreeModel.nodeStructureChanged(rootSpineNode);
            DefaultTreeModel[] treeModels = {somaTreeModel, neuronTreeModel, spineTreeModel};
            return treeModels;
        } finally {
            loadScanner.close();
        }
    }

    public DefaultTreeModel[] loadResolutionsAndNeuronTreeModels(InputStream stream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        Scanner loadScanner = new Scanner(bufferedReader);
        ntNeuronNode rootSomaNode = new ntNeuronNode("Traced Soma", new ArrayList<String[]>());
        DefaultTreeModel somaTreeModel = new DefaultTreeModel(rootSomaNode);
        ntNeuronNode rootNeuronNode = new ntNeuronNode("Traced Neuron", new ArrayList<String[]>());
        DefaultTreeModel neuronTreeModel = new DefaultTreeModel(rootNeuronNode);
        ntNeuronNode rootSpineNode = new ntNeuronNode("Traced Spine", new ArrayList<String[]>());
        DefaultTreeModel spineTreeModel = new DefaultTreeModel(rootSpineNode);
        
        try {
            while (loadScanner.hasNext()) {
                String nextString = loadScanner.next();
                if (nextString.equals("xyzResolutions:")) {
                    nTracer_.xyzResolutions[0] = Double.parseDouble(loadScanner.next());
                    nTracer_.xyzResolutions[1] = Double.parseDouble(loadScanner.next());
                    nTracer_.xyzResolutions[2] = Double.parseDouble(loadScanner.next());
                    //IJ.log("xyzResolutions: "+nTracer_.xyzResolutions[0]+", "+nTracer_.xyzResolutions[1]+", "+nTracer_.xyzResolutions[2]);
                }
                if (nextString.equals("Neuron")) {
                    String neuronName = loadScanner.next();
                    //IJ.log("Neuron "+neuronName);
                    // create a new soma and insert into rootNeuronNode
                    String[] somaNeuronPoint = {"0", "-1", "-1", "-1", "0", "0", "0"};
                    ArrayList<String[]> somaNuronData = new ArrayList<String[]>();
                    somaNuronData.add(somaNeuronPoint);
                    ntNeuronNode somaNeuronNode = new ntNeuronNode(neuronName, somaNuronData);
                    neuronTreeModel.insertNodeInto(somaNeuronNode, rootNeuronNode, rootNeuronNode.getChildCount());
                    // icreate a new soma and insert into rootSomaNode
                    String[] somaSomaPoint = {"0", "-1", "-1", "-1", "0", "0", "0"};
                    ArrayList<String[]> somaSomaData = new ArrayList<String[]>();
                    somaSomaData.add(somaSomaPoint);
                    ntNeuronNode somaSomaNode = new ntNeuronNode(neuronName, somaSomaData);
                    somaTreeModel.insertNodeInto(somaSomaNode, rootSomaNode, rootSomaNode.getChildCount());
                    //IJ.log("created new soma " + neuronName);

                    // continue loading data for the whole neuron
                    String type = loadScanner.next();
                    while (!(type.equals("ENDneuron"))) {
                        String nodeName = loadScanner.next();
                        if (nodeName.contains("/")) {
                            nodeName = nodeName.split("/")[0];
                        }
                        // load soma data
                        if (nodeName.contains(":")) {
                            String[] parentNames = nodeName.split(":"); // parentName[0] = soma name; parentName[1] = soma Z
                            //IJ.log("load soma "+parentNames[0] + " : " + parentNames[1]);
                            int zPlane = Integer.parseInt(parentNames[1]);
                            if (zPlane >= 0) {
                                // insert somaChildNode into somaSomaNode
                                ArrayList<String[]> somaNodeData = new ArrayList<String[]>();
                                while (!(loadScanner.next()).equals("END")) {
                                    String[] nodePoint = new String[7];
                                    nodePoint[0] = loadScanner.next();
                                    nodePoint[1] = loadScanner.next();
                                    nodePoint[2] = loadScanner.next();
                                    nodePoint[3] = loadScanner.next();
                                    nodePoint[4] = loadScanner.next();
                                    nodePoint[5] = loadScanner.next();
                                    nodePoint[6] = loadScanner.next();
                                    somaNodeData.add(nodePoint);
                                }
                                ntNeuronNode somaChildNode = new ntNeuronNode(nodeName, somaNodeData);
                                somaTreeModel.insertNodeInto(somaChildNode, somaSomaNode, somaSomaNode.getChildCount());
                                //IJ.log("soma "+nodeName + " inserted");
                            }
                        } // load neurite sub-branch data
                        else if (nodeName.contains("-")) {
                            String[] nodeNames = nodeName.split("-");
                            String branchIndexName = nodeNames[nodeNames.length - 1];
                            String parentName = nodeName.substring(0, nodeName.length() - branchIndexName.length() - 1);
                            //IJ.log("nodeName = "+nodeName+"-"+branchIndexName);
                            //IJ.log("parentName = "+parentName);
                            ArrayList<String[]> neuriteNodeData = new ArrayList<String[]>();
                            while (!(loadScanner.next()).equals("END")) {
                                String[] nodePoint = new String[7];
                                nodePoint[0] = loadScanner.next();
                                nodePoint[1] = loadScanner.next();
                                nodePoint[2] = loadScanner.next();
                                nodePoint[3] = loadScanner.next();
                                nodePoint[4] = loadScanner.next();
                                nodePoint[5] = loadScanner.next();
                                nodePoint[6] = loadScanner.next();
                                neuriteNodeData.add(nodePoint);
                            }
                            ntNeuronNode neuriteChildNode = new ntNeuronNode(nodeName, neuriteNodeData);
                            TreePath neuriteParentPath = findPath2Name(rootNeuronNode, parentName);
                            ntNeuronNode neuriteParentNode = (ntNeuronNode) neuriteParentPath.getLastPathComponent();
                            neuronTreeModel.insertNodeInto(neuriteChildNode, neuriteParentNode, neuriteParentNode.getChildCount());
                            //IJ.log("neurite "+nodeName+" inserted");
                        }
                        type = loadScanner.next();
                    }
                }
                if (nextString.equals("Spine")) {
                        String spineName = loadScanner.next();
                        ArrayList<String[]> spineNodeData = new ArrayList<String[]>();
                        while (!(loadScanner.next()).equals("ENDspine")) {
                            String[] nodePoint = new String[7];
                            nodePoint[0] = loadScanner.next();
                            nodePoint[1] = loadScanner.next();
                            nodePoint[2] = loadScanner.next();
                            nodePoint[3] = loadScanner.next();
                            nodePoint[4] = loadScanner.next();
                            nodePoint[5] = loadScanner.next();
                            nodePoint[6] = loadScanner.next();
                            spineNodeData.add(nodePoint);
                        }
                        ntNeuronNode spineNode = new ntNeuronNode(spineName, spineNodeData);
                        spineTreeModel.insertNodeInto(spineNode, rootSpineNode, rootSpineNode.getChildCount());
                }
            }      
            somaTreeModel.nodeStructureChanged(rootSomaNode);
            neuronTreeModel.nodeStructureChanged(rootNeuronNode);
            spineTreeModel.nodeStructureChanged(rootSpineNode);
            DefaultTreeModel[] treeModels = {somaTreeModel, neuronTreeModel, spineTreeModel};
            return treeModels;
        } finally {
            loadScanner.close();
        }
    }

    
    private TreePath findPath2Name(ntNeuronNode root, String s) {
        @SuppressWarnings("unchecked")
        Enumeration<DefaultMutableTreeNode> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = e.nextElement();
            String nodeName = node.toString();
            if (nodeName.contains("/")){
                nodeName = nodeName.split("/")[0];
            }
            if (nodeName.equals(s)) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }

    // recursive delete in a directory
    public void delete(File file)
            throws IOException {
        if (file.isDirectory()) {
            //directory is empty, then delete it
            if (file.list().length == 0) {
                file.delete();
                //IJ.log("delete directory: "+file.getName());
            } else {
                //list all the directory contents
                String files[] = file.list();
                for (String temp : files) {
                    //construct the file structure
                    File fileDelete = new File(file, temp);
                    //recursive delete
                    delete(fileDelete);
                }
                //check the directory again, if empty then delete it
                if (file.list().length == 0) {
                    file.delete();
                    //IJ.log("delete empty directory: "+file.getName());
                }
            }
        } else {
            //if file, then delete it
            file.delete();
            //IJ.log("delete: "+file.getName());
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    public InputStream loadPackagedParameterAndNeuron(File selectedFile) throws IOException {
        String zipFilePath = selectedFile.getAbsolutePath();
        ZipFile zipFile = new ZipFile(zipFilePath);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        ZipEntry entry = null;
        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            if (entry.toString().endsWith("-data.txt")) {
                break;
            }
        }
        if (entry == null) {
            IJ.error("No neurite tracing data file found !");
            return null;
        }
        InputStream stream = zipFile.getInputStream(entry);
        return stream;
    }

    public InputStream loadPackagedExpansionAndSelection(File selectedFile) throws IOException {
        String zipFilePath = selectedFile.getAbsolutePath();
        ZipFile zipFile = new ZipFile(zipFilePath);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        ZipEntry entry = null;
        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            if (entry.toString().endsWith("-status.txt")) {
                break;
            }
        }
        if (entry == null) {
            IJ.error("No tree status file found !");
            return null;
        }
        InputStream stream = zipFile.getInputStream(entry);
        return stream;
    }

    public void exportSelectedNeuronSWC(ntNeuronNode rootNeuriteNode, ntNeuronNode rootAllSomaNode,
            ntNeuronNode spineParentNode, ArrayList<String> selectedNeuronNumbers, String directory, 
            String prefixName, double[] xyzResolutions, boolean stdSWC, boolean expSpine) throws IOException {
        // write selected neuron tracig data
        for (String neuronNumber : selectedNeuronNumbers) {
            ntNeuronNode somaParentNode = (ntNeuronNode) rootAllSomaNode.getChildAt(0);
            ntNeuronNode neuriteParentNode = (ntNeuronNode) rootNeuriteNode.getChildAt(0);
            for (int n = 0; n< rootAllSomaNode.getChildCount(); n++){
                if (neuronNumber.equals(((ntNeuronNode) rootAllSomaNode.getChildAt(n)).getNeuronNumber())){
                    somaParentNode = (ntNeuronNode) rootAllSomaNode.getChildAt(n);
                    neuriteParentNode = (ntNeuronNode) rootNeuriteNode.getChildAt(n);
                    break;
                }
            }
            exportSingleNeuronSWC(neuriteParentNode, somaParentNode, spineParentNode, 
                    neuronNumber, directory, prefixName, xyzResolutions, stdSWC, expSpine);
        }
    }
    private void exportSingleNeuronSWC(ntNeuronNode neuriteParentNode, ntNeuronNode somaParentNode, ntNeuronNode spineParentNode,
            String neuronNumber, String directory, String prefixName, double[] xyzResolutions, boolean stdSWC, boolean expSpine) throws IOException {
        String path = directory + prefixName + "_Neuron-" + neuronNumber;
        if (stdSWC){
            path = path + "_stdSWC.swc";
        } else {
            path = path + "_extSWC.swc";
        }
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
        SimpleDateFormat fileFormatter = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        int nCount = 1;
        String typeIdentifyer = "1";
        ArrayList<double[]> allSomaPoints = new ArrayList<double[]>();
        try {
            // write SWC header
            out.writeBytes("# CREATION_DATE "+fileFormatter.format(now)+"\n");
            if (stdSWC){
                out.writeBytes("# "+prefixName + "_Neuron-" + neuronNumber+"_stdSWC.swc\n");
            } else {
                out.writeBytes("# "+prefixName + "_Neuron-" + neuronNumber+"_extSWC.swc\n");
            }
            out.writeBytes("# CREATED BY nTracer1.0\n");
            out.writeBytes("# Dawen Cai, University of Michigan (dwcai@umich.edu)\n");
            out.writeBytes("# \n");
            double xScale = xyzResolutions[0];
            double yScale = xyzResolutions[1];
            double zScale = xyzResolutions[2];
            double rScale = Math.sqrt(xScale * xScale + yScale * yScale + zScale * zScale);
            if (xScale == 0 || yScale == 0 || zScale == 0) {
                xScale = 1;
                yScale = 1;
                zScale = 1;
                rScale = 1;
                out.writeBytes("# (x, y, z) resolutions = 0 um/pixel \n");
                if (stdSWC) {
                    out.writeBytes("# standard SWC format (n T x y z R P)\n");
                } else {
                    out.writeBytes("# extended SWC format (n T x y z R P S)\n");
                }
                out.writeBytes("# n = point indentifier\n");
                out.writeBytes("# T = type indentifier:\n");
                out.writeBytes("#  0 = undefined; 1 = soma; 2 = axon; 3 = (basal) dendrite;\n");
                out.writeBytes("#  4 = apical dendrite; 5 = fork point; 6 = end point; 7 = spine\n");
                out.writeBytes("# x, y, z = cartesian coordinates (pixel)\n");
                out.writeBytes("# R = radius at the point (pixel)\n");
                out.writeBytes("# P = parent point; P = -1 indicates the origin point\n");
                if (!stdSWC) {
                    out.writeBytes("# S = synapse (0 or 1)\n");
                }
                out.writeBytes("\n");
            } else {
                out.writeBytes("# (x, y, z) resolutions = (" + xScale + ", " + yScale + ", " + zScale + ") um/pixel \n");
                if (stdSWC) {
                    out.writeBytes("# standard SWC format (n T x y z R P)\n");
                } else {
                    out.writeBytes("# extended SWC format (n T x y z R P S)\n");
                }
                out.writeBytes("# n = point indentifier\n");
                out.writeBytes("# T = type indentifier:\n");
                out.writeBytes("#  0 = undefined; 1 = soma; 2 = axon; 3 = (basal) dendrite;\n");
                out.writeBytes("#  4 = apical dendrite; 5 = fork point; 6 = end point; 7 = spine\n");
                out.writeBytes("# x, y, z = cartesian coordinates (um)\n");
                out.writeBytes("# R = radius at the point (um)\n");
                out.writeBytes("# P = parent point; P = -1 indicates the origin point\n");
                if (!stdSWC) {
                    out.writeBytes("# S = synapse (0 or 1)\n");
                }
                out.writeBytes("\n");
            }

            double[] xyzrScales = {xScale, yScale, zScale, rScale};
            ArrayList<String[]> spineNumbers = new ArrayList<String[]>();

            // write soma data
            for (int k=0; k<somaParentNode.getChildCount(); k++){
                ntNeuronNode somaChildNode = (ntNeuronNode) (somaParentNode.getChildAt(k));
                ArrayList<String[]> data = somaChildNode.getTracingResult();                
                // write soma point data   
                if (stdSWC) {
                    for (String[] point : data) {
                        double[] somaPt = {scaleCordinate(point[1], xyzrScales[0]),
                            scaleCordinate(point[2], xyzrScales[1]),
                            scaleCordinate(point[3], xyzrScales[2])};
                        allSomaPoints.add(somaPt);
                        if (expSpine && point[0].contains("#")) {
                            String[] spine = {point[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                                + somaPt[0] + " " + somaPt[1] + " " + somaPt[2] + " "
                                + scaleCordinate(point[4], xyzrScales[3]) + " -1 " + "\n");
                        nCount++;
                    }
                } else {
                    for (String[] point : data) {
                        double[] somaPt = {scaleCordinate(point[1], xyzrScales[0]), 
                            scaleCordinate(point[2], xyzrScales[1]),
                            scaleCordinate(point[3], xyzrScales[2])};
                        allSomaPoints.add(somaPt);
                        if (expSpine && point[0].contains("#")) {
                            String[] spine = {point[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                                + somaPt[0] + " " + somaPt[1] + " " + somaPt[2] + " "
                                + scaleCordinate(point[4], xyzrScales[3]) + " -1 " + point[5] + "\n");
                        nCount++;
                    }
                }
            }
            
            if (nCount>1){ // soma has tracing data
                exportNeuriteWithSomaData(out, neuriteParentNode, allSomaPoints, 
                        spineParentNode, spineNumbers, nCount, typeIdentifyer, xyzrScales, stdSWC, expSpine);
            } else { // soma has no tracing data
                exportNeuriteWithNoSomaData(out, neuriteParentNode, spineParentNode, 
                        spineNumbers, nCount, typeIdentifyer, xyzrScales, stdSWC, expSpine);
            }
            
            out.flush();
        } catch (IOException e) {
            IJ.error("SWC export error: " + e);
        }
        out.close();
    }
    private double scaleCordinate(String stringCoordinate, double scaleFactor){
        double doubleCoordinate = Double.parseDouble(stringCoordinate);
        return ((double)(Math.round(doubleCoordinate * scaleFactor * 100000))) / 100000;
    }
    private String searchFirstParentCount(double[] primaryFirstPt, ArrayList<double[]> allSomaPoints){
        double minDistance2 = 1000000000;
        int minDistancePt = 0;
        for (int i=0; i<allSomaPoints.size(); i++){
            double[] somaPt = allSomaPoints.get(i);
            double tempDistance2 = (primaryFirstPt[0]-somaPt[0])*(primaryFirstPt[0]-somaPt[0]) 
                    + (primaryFirstPt[1]-somaPt[1])*(primaryFirstPt[1]-somaPt[1])
                    + (primaryFirstPt[2]-somaPt[2])*(primaryFirstPt[2]-somaPt[2]);
            if (tempDistance2<minDistance2){
                minDistance2 = tempDistance2;
                minDistancePt = i;
            }
        }
        return (minDistancePt+1)+"";
    }
    private void exportNeuriteWithSomaData(DataOutputStream out, ntNeuronNode neuriteParentNode, 
            ArrayList<double[]> allSomaPoints, ntNeuronNode spineParentNode, ArrayList<String[]> spineNumbers,
            int nCount, String typeIdentifyer, double[] xyzrScales, boolean stdSWC, boolean expSpine) throws IOException {
        try {
            for (int k = 0; k < neuriteParentNode.getChildCount(); k++) {
                // get primary branch note and set its type identifier
                ntNeuronNode primaryBranchNode = (ntNeuronNode) (neuriteParentNode.getChildAt(k));
                ArrayList<String[]> data = primaryBranchNode.getTracingResult();
                String[] firstPoint = data.get(0);
                if (firstPoint[0].equals("Axon")) {
                    typeIdentifyer = "2";
                }
                if (firstPoint[0].equals("Dendrite")) {
                    typeIdentifyer = "3";
                }
                if (firstPoint[0].equals("Apical")) {
                    typeIdentifyer = "4";
                }
                // write SWC
                if (stdSWC) {
                    // write first point
                    double[] primaryFirstPt = {scaleCordinate(firstPoint[1], xyzrScales[0]),
                        scaleCordinate(firstPoint[2], xyzrScales[1]),
                        scaleCordinate(firstPoint[3], xyzrScales[2])};
                    String somaParentPointIdentifyer = searchFirstParentCount(primaryFirstPt, allSomaPoints);
                    if (expSpine && firstPoint[0].contains("#")) {
                        String[] spine = {firstPoint[0].split("#")[1], nCount + ""};
                        spineNumbers.add(spine);
                    }
                    out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(firstPoint[1], xyzrScales[0]) + " "
                            + scaleCordinate(firstPoint[2], xyzrScales[1]) + " "
                            + scaleCordinate(firstPoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(firstPoint[4], xyzrScales[3]) + " "
                            + somaParentPointIdentifyer + "\n");
                    nCount++;

                    // write middle points
                    for (int i = 1; i < data.size() - 1; i++) {
                        String[] primaryMiddlePt = data.get(i);
                        if (expSpine && firstPoint[0].contains("#")) {
                            String[] spine = {primaryMiddlePt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                                + scaleCordinate(primaryMiddlePt[1], xyzrScales[0]) + " "
                                + scaleCordinate(primaryMiddlePt[2], xyzrScales[1]) + " "
                                + scaleCordinate(primaryMiddlePt[3], xyzrScales[2]) + " "
                                + scaleCordinate(primaryMiddlePt[4], xyzrScales[3]) + " "
                                + (nCount - 1) + " " + "\n");
                        nCount++;
                    }

                    // write last point
                    if (data.size() > 1) {
                        String[] primaryLastPt = data.get(data.size() - 1);
                        if (expSpine && firstPoint[0].contains("#")) {
                            String[] spine = {primaryLastPt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        if (primaryBranchNode.isLeaf()) {
                            out.writeBytes(nCount + " " + "6" + " "
                                    + scaleCordinate(primaryLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(primaryLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(primaryLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(primaryLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + "\n");
                        } else {
                            out.writeBytes(nCount + " " + "5" + " "
                                    + scaleCordinate(primaryLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(primaryLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(primaryLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(primaryLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + "\n");
                        }
                        nCount++;
                    }
                } else {
                    // write first point
                    double[] primaryFirstPt = {Float.parseFloat(firstPoint[1]),
                        Float.parseFloat(firstPoint[2]), Float.parseFloat(firstPoint[3])};
                    String somaParentPointIdentifyer = searchFirstParentCount(primaryFirstPt, allSomaPoints);
                    if (expSpine && firstPoint[0].contains("#")) {
                        String[] spine = {firstPoint[0].split("#")[1], nCount + ""};
                        spineNumbers.add(spine);
                    }
                    out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(firstPoint[1], xyzrScales[0]) + " " 
                            + scaleCordinate(firstPoint[2], xyzrScales[1]) + " " 
                            + scaleCordinate(firstPoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(firstPoint[4], xyzrScales[3]) + " " 
                            + somaParentPointIdentifyer + " " + firstPoint[5] + "\n");
                    nCount++;

                    // write middle points
                    for (int i = 1; i < data.size() - 1; i++) {
                        String[] primaryMiddlePt = data.get(i);
                        if (expSpine && primaryMiddlePt[0].contains("#")) {
                            String[] spine = {primaryMiddlePt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                                + scaleCordinate(primaryMiddlePt[1], xyzrScales[0]) + " "
                                + scaleCordinate(primaryMiddlePt[2], xyzrScales[1]) + " " 
                            + scaleCordinate(primaryMiddlePt[3], xyzrScales[2]) + " "
                            + scaleCordinate(primaryMiddlePt[4], xyzrScales[3]) + " "
                                + (nCount - 1) + " " + primaryMiddlePt[5] + "\n");
                        nCount++;
                    }

                    // write last point
                    if (data.size() > 1) {
                        String[] primaryLastPt = data.get(data.size() - 1);
                        if (expSpine && primaryLastPt[0].contains("#")) {
                            String[] spine = {primaryLastPt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        if (primaryBranchNode.isLeaf()) {
                            out.writeBytes(nCount + " " + "6" + " "
                                    + scaleCordinate(primaryLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(primaryLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(primaryLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(primaryLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + primaryLastPt[5] + "\n");
                        } else {
                            out.writeBytes(nCount + " " + "5" + " "
                                    + scaleCordinate(primaryLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(primaryLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(primaryLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(primaryLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + primaryLastPt[5] + "\n");
                        }
                        nCount++;
                    }
                }

                // write all child branches
                int primayLastCount = nCount - 1;
                nCount = exportChildBranchSWC(out, primaryBranchNode, spineNumbers, 
                        primayLastCount, nCount, typeIdentifyer, xyzrScales, stdSWC, expSpine);
                
                //IJ.log(spineNumbers.size()+" spines");
                if (spineNumbers.size()>0){
                    exportSpineSWC(out, spineParentNode, spineNumbers, nCount, "7", xyzrScales, stdSWC);
                }
            }
        } catch (IOException e) {
            IJ.error("SWC export error: " + e);
        }
    }
    private void exportNeuriteWithNoSomaData(DataOutputStream out,
            ntNeuronNode neuriteParentNode, ntNeuronNode spineParentNode, ArrayList<String[]> spineNumbers, int nCount, 
            String typeIdentifyer, double[] xyzrScales, boolean stdSWC, boolean expSpine) throws IOException {
        try {
            for (int k = 0; k < neuriteParentNode.getChildCount(); k++) {
                // get primary branch note and set its type identifier
                ntNeuronNode primaryBranchNode = (ntNeuronNode) (neuriteParentNode.getChildAt(k));
                ArrayList<String[]> data = primaryBranchNode.getTracingResult();
                String[] firstPoint = data.get(0);
                if (firstPoint[0].equals("Axon")) {
                    typeIdentifyer = "2";
                }
                if (firstPoint[0].equals("Dendrite")) {
                    typeIdentifyer = "3";
                }
                if (firstPoint[0].equals("Apical")) {
                    typeIdentifyer = "4";
                }
                // write SWC
                if (stdSWC) {
                    // write first point
                    if (expSpine && firstPoint[0].contains("#")) {
                        String[] spine = {firstPoint[0].split("#")[1], nCount + ""};
                        spineNumbers.add(spine);
                    }
                    out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(firstPoint[1], xyzrScales[0]) + " " 
                            + scaleCordinate(firstPoint[2], xyzrScales[1]) + " " 
                            + scaleCordinate(firstPoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(firstPoint[4], xyzrScales[3]) + " "
                            + "-1" + "\n");
                    nCount++;

                    // write middle points
                    for (int i = 1; i < data.size() - 1; i++) {
                        String[] primaryMiddlePt = data.get(i);
                        if (expSpine && primaryMiddlePt[0].contains("#")) {
                            String[] spine = {primaryMiddlePt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(primaryMiddlePt[1], xyzrScales[0]) + " " 
                            + scaleCordinate(primaryMiddlePt[2], xyzrScales[1]) + " " 
                            + scaleCordinate(primaryMiddlePt[3], xyzrScales[2]) + " "
                            + scaleCordinate(primaryMiddlePt[4], xyzrScales[3]) + " "
                                + (nCount - 1) + "\n");
                        nCount++;
                    }

                    // write last point
                    if (data.size() > 1) {
                        String[] primaryLastPt = data.get(data.size() - 1);
                        if (expSpine && primaryLastPt[0].contains("#")) {
                            String[] spine = {primaryLastPt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        if (primaryBranchNode.isLeaf()) {
                            out.writeBytes(nCount + " " + "6" + " "
                                    + scaleCordinate(primaryLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(primaryLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(primaryLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(primaryLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + "\n");
                        } else {
                            out.writeBytes(nCount + " " + "5" + " "
                                    + scaleCordinate(primaryLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(primaryLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(primaryLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(primaryLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + "\n");
                        }
                        nCount++;
                    }
                } else {
                    // write first point
                    if (expSpine && firstPoint[0].contains("#")) {
                        String[] spine = {firstPoint[0].split("#")[1], nCount + ""};
                        spineNumbers.add(spine);
                    }
                    out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(firstPoint[1], xyzrScales[0]) + " " 
                            + scaleCordinate(firstPoint[2], xyzrScales[1]) + " " 
                            + scaleCordinate(firstPoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(firstPoint[4], xyzrScales[3]) + " " 
                            + "-1" + " " + firstPoint[5] + "\n");
                    nCount++;

                    // write middle points
                    for (int i = 1; i < data.size() - 1; i++) {
                        String[] primaryMiddlePt = data.get(i);
                        if (expSpine && primaryMiddlePt[0].contains("#")) {
                            String[] spine = {primaryMiddlePt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(primaryMiddlePt[1], xyzrScales[0]) + " " 
                            + scaleCordinate(primaryMiddlePt[2], xyzrScales[1]) + " " 
                            + scaleCordinate(primaryMiddlePt[3], xyzrScales[2]) + " "
                            + scaleCordinate(primaryMiddlePt[4], xyzrScales[3]) + " "
                                + (nCount - 1) + " " + primaryMiddlePt[5] + "\n");
                        nCount++;
                    }

                    // write last point
                    if (data.size() > 1) {
                        String[] primaryLastPt = data.get(data.size() - 1);
                        if (expSpine && primaryLastPt[0].contains("#")) {
                            String[] spine = {primaryLastPt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        if (primaryBranchNode.isLeaf()) {
                            out.writeBytes(nCount + " " + "6" + " "
                                    + scaleCordinate(primaryLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(primaryLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(primaryLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(primaryLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + primaryLastPt[5] + "\n");
                        } else {
                            out.writeBytes(nCount + " " + "5" + " "
                                    + scaleCordinate(primaryLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(primaryLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(primaryLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(primaryLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + primaryLastPt[5] + "\n");
                        }
                        nCount++;
                    }
                }
                
                // write all child branches
                int primayLastCount = nCount-1;
                nCount = exportChildBranchSWC(out, primaryBranchNode, spineNumbers, primayLastCount, 
                        nCount, typeIdentifyer, xyzrScales, stdSWC, expSpine);
                //IJ.log(spineNumbers.size()+" spines");
                if (spineNumbers.size()>0){
                    exportSpineSWC(out, spineParentNode, spineNumbers, nCount, "7", xyzrScales, stdSWC);
                }
            }
        } catch (IOException e) {
            IJ.error("SWC export error: " + e);
        }
    }
    private int exportChildBranchSWC(DataOutputStream out,
            ntNeuronNode primaryBranchNode, ArrayList<String[]> spineNumbers,
            int parentCount, int nCount, String typeIdentifyer, 
            double[] xyzrScales, boolean stdSWC, boolean expSpine) throws IOException{
        try {
            for (int k = 0; k < primaryBranchNode.getChildCount(); k++) {
                ntNeuronNode childBranchNode = (ntNeuronNode) (primaryBranchNode.getChildAt(k));
                ArrayList<String[]> data = childBranchNode.getTracingResult();

                if (stdSWC) {
                    // write first child point
                    String[] childFirstPoint = data.get(0);
                    if (expSpine && childFirstPoint[0].contains("#")) {
                        String[] spine = {childFirstPoint[0].split("#")[1], nCount + ""};
                        spineNumbers.add(spine);
                    }
                    out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(childFirstPoint[1], xyzrScales[0]) + " " 
                            + scaleCordinate(childFirstPoint[2], xyzrScales[1]) + " " 
                            + scaleCordinate(childFirstPoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(childFirstPoint[4], xyzrScales[3]) + " " 
                            + parentCount + "\n");
                    nCount++;
                    // write middle child points
                    for (int i = 1; i < data.size() - 1; i++) {
                        String[] childMiddlePoint = data.get(i);
                        if (expSpine && childMiddlePoint[0].contains("#")) {
                            String[] spine = {childMiddlePoint[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(childMiddlePoint[1], xyzrScales[0]) + " " 
                            + scaleCordinate(childMiddlePoint[2], xyzrScales[1]) + " " 
                            + scaleCordinate(childMiddlePoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(childMiddlePoint[4], xyzrScales[3]) + " "  
                                + (nCount - 1) + "\n");
                        nCount++;
                    }
                    // write last child point
                    if (data.size() > 1) {
                        String[] childLastPt = data.get(data.size() - 1);
                        if (expSpine && childLastPt[0].contains("#")) {
                            String[] spine = {childLastPt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        if (childBranchNode.isLeaf()) {
                            out.writeBytes(nCount + " " + "6" + " "
                            + scaleCordinate(childLastPt[1], xyzrScales[0]) + " " 
                            + scaleCordinate(childLastPt[2], xyzrScales[1]) + " " 
                            + scaleCordinate(childLastPt[3], xyzrScales[2]) + " "
                            + scaleCordinate(childLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + "\n");
                        } else {
                            out.writeBytes(nCount + " " + "5" + " "
                            + scaleCordinate(childLastPt[1], xyzrScales[0]) + " " 
                            + scaleCordinate(childLastPt[2], xyzrScales[1]) + " " 
                            + scaleCordinate(childLastPt[3], xyzrScales[2]) + " "
                            + scaleCordinate(childLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + "\n");
                        }
                        nCount++;
                    }
                } else {
                    // write first child point
                    String[] childFirstPoint = data.get(0);
                    if (expSpine && childFirstPoint[0].contains("#")) {
                        String[] spine = {childFirstPoint[0].split("#")[1], nCount + ""};
                        spineNumbers.add(spine);
                    }
                    out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(childFirstPoint[1], xyzrScales[0]) + " " 
                            + scaleCordinate(childFirstPoint[2], xyzrScales[1]) + " " 
                            + scaleCordinate(childFirstPoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(childFirstPoint[4], xyzrScales[3]) + " " 
                            + parentCount + " " + childFirstPoint[5] + "\n");
                    nCount++;
                    // write middle child points
                    for (int i = 1; i < data.size() - 1; i++) {
                        String[] childMiddlePoint = data.get(i);
                        if (expSpine && childMiddlePoint[0].contains("#")) {
                            String[] spine = {childMiddlePoint[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(childMiddlePoint[1], xyzrScales[0]) + " " 
                            + scaleCordinate(childMiddlePoint[2], xyzrScales[1]) + " " 
                            + scaleCordinate(childMiddlePoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(childMiddlePoint[4], xyzrScales[3]) + " " 
                                + (nCount - 1) + " " + childMiddlePoint[5] + "\n");
                        nCount++;
                    }
                    // write last child point
                    if (data.size() > 1) {
                        String[] childLastPt = data.get(data.size() - 1);
                        if (expSpine && childLastPt[0].contains("#")) {
                            String[] spine = {childLastPt[0].split("#")[1], nCount + ""};
                            spineNumbers.add(spine);
                        }
                        if (childBranchNode.isLeaf()) {
                            out.writeBytes(nCount + " " + "6" + " "
                            + scaleCordinate(childLastPt[1], xyzrScales[0]) + " " 
                            + scaleCordinate(childLastPt[2], xyzrScales[1]) + " " 
                            + scaleCordinate(childLastPt[3], xyzrScales[2]) + " "
                            + scaleCordinate(childLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + childLastPt[5] + "\n");
                        } else {
                            out.writeBytes(nCount + " " + "5" + " "
                            + scaleCordinate(childLastPt[1], xyzrScales[0]) + " " 
                            + scaleCordinate(childLastPt[2], xyzrScales[1]) + " " 
                            + scaleCordinate(childLastPt[3], xyzrScales[2]) + " "
                            + scaleCordinate(childLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + childLastPt[5] + "\n");
                        }
                        nCount++;
                    }
                }
                
                // write child's child
                int childLastCount = nCount-1;
                nCount =  exportChildBranchSWC(out, childBranchNode, spineNumbers,
                        childLastCount, nCount, typeIdentifyer, xyzrScales, stdSWC, expSpine);
            }            
        } catch (IOException e) {
            IJ.error("SWC export error: " + e);
        }
        return nCount;
    }

    private void exportSpineSWC(DataOutputStream out,
            ntNeuronNode spineParentNode, ArrayList<String[]> exportSpines,
            int nCount, String typeIdentifyer, double[] xyzrScales, boolean stdSWC) throws IOException {
        try {
            for (String[] exportSpine : exportSpines) {
                String spineNumber = exportSpine[0];
                String parentCount = exportSpine[1];
                ntNeuronNode spineNode = getSpineNode(spineParentNode, spineNumber);
                ArrayList<String[]> data = spineNode.getTracingResult();
                if (stdSWC) {
                    // write first child point
                    String[] childFirstPoint = data.get(0);
                    out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(childFirstPoint[1], xyzrScales[0]) + " "
                            + scaleCordinate(childFirstPoint[2], xyzrScales[1]) + " "
                            + scaleCordinate(childFirstPoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(childFirstPoint[4], xyzrScales[3]) + " "
                            + parentCount + "\n");
                    nCount++;
                    // write middle child points
                    for (int i = 1; i < data.size() - 1; i++) {
                        String[] childMiddlePoint = data.get(i);
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                                + scaleCordinate(childMiddlePoint[1], xyzrScales[0]) + " "
                                + scaleCordinate(childMiddlePoint[2], xyzrScales[1]) + " "
                                + scaleCordinate(childMiddlePoint[3], xyzrScales[2]) + " "
                                + scaleCordinate(childMiddlePoint[4], xyzrScales[3]) + " "
                                + (nCount - 1) + "\n");
                        nCount++;
                    }
                    // write last child point
                    if (data.size() > 1) {
                        String[] childLastPt = data.get(data.size() - 1);
                        if (spineNode.isLeaf()) {
                            out.writeBytes(nCount + " " + "6" + " "
                                    + scaleCordinate(childLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(childLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(childLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(childLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + "\n");
                        } else {
                            out.writeBytes(nCount + " " + "5" + " "
                                    + scaleCordinate(childLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(childLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(childLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(childLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + "\n");
                        }
                        nCount++;
                    }
                } else {
                    // write first child point
                    String[] childFirstPoint = data.get(0);
                    out.writeBytes(nCount + " " + typeIdentifyer + " "
                            + scaleCordinate(childFirstPoint[1], xyzrScales[0]) + " "
                            + scaleCordinate(childFirstPoint[2], xyzrScales[1]) + " "
                            + scaleCordinate(childFirstPoint[3], xyzrScales[2]) + " "
                            + scaleCordinate(childFirstPoint[4], xyzrScales[3]) + " "
                            + parentCount + " " + childFirstPoint[5] + "\n");
                    nCount++;
                    // write middle child points
                    for (int i = 1; i < data.size() - 1; i++) {
                        String[] childMiddlePoint = data.get(i);
                        out.writeBytes(nCount + " " + typeIdentifyer + " "
                                + scaleCordinate(childMiddlePoint[1], xyzrScales[0]) + " "
                                + scaleCordinate(childMiddlePoint[2], xyzrScales[1]) + " "
                                + scaleCordinate(childMiddlePoint[3], xyzrScales[2]) + " "
                                + scaleCordinate(childMiddlePoint[4], xyzrScales[3]) + " "
                                + (nCount - 1) + " " + childMiddlePoint[5] + "\n");
                        nCount++;
                    }
                    // write last child point
                    if (data.size() > 1) {
                        String[] childLastPt = data.get(data.size() - 1);
                        if (spineNode.isLeaf()) {
                            out.writeBytes(nCount + " " + "6" + " "
                                    + scaleCordinate(childLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(childLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(childLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(childLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + childLastPt[5] + "\n");
                        } else {
                            out.writeBytes(nCount + " " + "5" + " "
                                    + scaleCordinate(childLastPt[1], xyzrScales[0]) + " "
                                    + scaleCordinate(childLastPt[2], xyzrScales[1]) + " "
                                    + scaleCordinate(childLastPt[3], xyzrScales[2]) + " "
                                    + scaleCordinate(childLastPt[4], xyzrScales[3]) + " "
                                    + (nCount - 1) + " " + childLastPt[5] + "\n");
                        }
                        nCount++;
                    }
                }
            }
        } catch (IOException e) {
            IJ.error("SWC export error: " + e);
        }
    }

    private ntNeuronNode getSpineNode(ntNeuronNode spineParentNode, String spineNumber) {
        int totalSpine = spineParentNode.getChildCount();
        if (Integer.parseInt(spineNumber) >= totalSpine) {
            for (int i = totalSpine - 1; i >= 0; i--) {
                ntNeuronNode spineNode = (ntNeuronNode) spineParentNode.getChildAt(i);
                if (spineNumber.equals(spineNode.toString())) {
                    return spineNode;
                }
            }
        } else {
            for (int i = Integer.parseInt(spineNumber) - 1; i >= 0; i--) {
                ntNeuronNode spineNode = (ntNeuronNode) spineParentNode.getChildAt(i);
                if (spineNumber.equals(spineNode.toString())) {
                    return spineNode;
                }
            }
        }
        return null;
    }
    public void exportSelectedNeuronSynapse(ntNeuronNode rootNeuriteNode, ntNeuronNode rootAllSomaNode,
            ArrayList<String> selectedNeuronNumbers, String directory, 
            String prefixName, double[] xyzResolutions) throws IOException {
        String path = directory + prefixName + "_Synapse.xls";
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
        SimpleDateFormat fileFormatter = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        try {
            // write SWC header
            out.writeBytes("# CREATION_DATE "+fileFormatter.format(now)+"\n");
            out.writeBytes("# "+prefixName + "_Synapse.xls\n");
            out.writeBytes("# CREATED BY nTracer1.0\n");
            out.writeBytes("# Dawen Cai, University of Michigan (dwcai@umich.edu)\n");
            out.writeBytes("# \n");
            double xScale = xyzResolutions[0];
            double yScale = xyzResolutions[1];
            double zScale = xyzResolutions[2];
            double rScale = Math.sqrt(xScale * xScale + yScale * yScale + zScale * zScale);
            if (xScale == 0 || yScale == 0 || zScale == 0) {
                xScale = 1;
                yScale = 1;
                zScale = 1;
                rScale = 1;
                out.writeBytes("# (x, y, z) resolutions = 0 um/pixel \n");
                out.writeBytes("\n");
                out.writeBytes("Neuron#\tType\tx(pixel)\ty(pixel)\tz(pixel)\n");
            } else {
                out.writeBytes("# (x, y, z) resolutions = (" + xScale + ", " + yScale + ", " + zScale + ") um/pixel \n");
                out.writeBytes("\n");
                out.writeBytes("Neuron#\tType\tx(um)\ty(um)\tz(um)\n");
            }

            double[] xyzrScales = {xScale, yScale, zScale, rScale};

            for (String neuronNumber : selectedNeuronNumbers) {
                for (int n = 0; n < rootAllSomaNode.getChildCount(); n++) {
                    if (neuronNumber.equals(((ntNeuronNode) rootAllSomaNode.getChildAt(n)).getNeuronNumber())) {
                        ntNeuronNode somaParentNode = (ntNeuronNode) rootAllSomaNode.getChildAt(n);
                        ntNeuronNode neuriteParentNode = (ntNeuronNode) rootNeuriteNode.getChildAt(n);
                        for (int i = 0; i < somaParentNode.getChildCount(); i++) {
                            exportNodeSynapse(out, (ntNeuronNode) (somaParentNode.getChildAt(i)), xyzrScales);
                        }
                        for (int i = 0; i < neuriteParentNode.getChildCount(); i++) {
                            exportNodeSynapse(out, (ntNeuronNode) (neuriteParentNode.getChildAt(i)), xyzrScales);
                        }
                        break;
                    }
                }
            }           
            out.flush();
        } catch (IOException e) {
            IJ.error("Synapse export error: " + e);
        }
        out.close();
    }
    public void exportAllNeuronSynapse(ntNeuronNode rootNeuriteNode, ntNeuronNode rootAllSomaNode,
            String directory, String prefixName, double[] xyzResolutions) throws IOException {
        String path = directory + prefixName + "_Synapse.xls";
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
        SimpleDateFormat fileFormatter = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        try {
            // write SWC header
            out.writeBytes("# CREATION_DATE "+fileFormatter.format(now)+"\n");
            out.writeBytes("# "+prefixName + "_Synapse.xls\n");
            out.writeBytes("# CREATED BY nTracer1.0\n");
            out.writeBytes("# Dawen Cai, University of Michigan (dwcai@umich.edu)\n");
            out.writeBytes("# \n");
            double xScale = xyzResolutions[0];
            double yScale = xyzResolutions[1];
            double zScale = xyzResolutions[2];
            double rScale = Math.sqrt(xScale * xScale + yScale * yScale + zScale * zScale);
            if (xScale == 0 || yScale == 0 || zScale == 0) {
                xScale = 1;
                yScale = 1;
                zScale = 1;
                rScale = 1;
                out.writeBytes("# (x, y, z) resolutions = 0 um/pixel \n");
                out.writeBytes("\n");
                out.writeBytes("Neuron#\tx(pixel)\ty(pixel)\tz(pixel)\n");
            } else {
                out.writeBytes("# (x, y, z) resolutions = (" + xScale + ", " + yScale + ", " + zScale + ") um/pixel \n");
                out.writeBytes("\n");
                out.writeBytes("Neuron#\tx(um)\ty(um)\tz(um)\n");
            }

            double[] xyzrScales = {xScale, yScale, zScale, rScale};

            for (int k=0; k<rootAllSomaNode.getChildCount(); k++){
                ntNeuronNode somaParentNode = (ntNeuronNode) (rootAllSomaNode.getChildAt(k));
                ntNeuronNode neuriteParentNode = (ntNeuronNode) (rootNeuriteNode.getChildAt(k));
                for (int i=0; i<somaParentNode.getChildCount(); i++){
                    exportNodeSynapse(out, (ntNeuronNode) (somaParentNode.getChildAt(i)), xyzrScales);
                } 
                for (int i=0; i<neuriteParentNode.getChildCount(); i++){
                    exportNodeSynapse(out, (ntNeuronNode) (neuriteParentNode.getChildAt(i)), xyzrScales);
                }
            }            
            out.flush();
        } catch (IOException e) {
            IJ.error("Synapse export error: " + e);
        }
        out.close();
    }
    private void exportNodeSynapse(DataOutputStream out, ntNeuronNode node, double[] xyzrScales) throws IOException {
        String neuronNumber = node.getNeuronNumber();
        try {
            ArrayList<String[]> data = node.getTracingResult();
            for (String[] point : data) {
                if (point[5].equals("1")) {
                    out.writeBytes(neuronNumber + "\t" 
                            + point[0].split("#")[0] + "\t"
                            + scaleCordinate(point[1], xyzrScales[0]) + "\t"
                            + scaleCordinate(point[2], xyzrScales[1]) + "\t"
                            + scaleCordinate(point[3], xyzrScales[2]) + "\n");
                }
            }
            for (int k = 0; k < node.getChildCount(); k++) {
                ntNeuronNode childNode = (ntNeuronNode) (node.getChildAt(k));                
                exportNodeSynapse(out, childNode, xyzrScales);
            }
        } catch (IOException e) {}
    }

    public void exportAllNeuronConnection(ntNeuronNode rootNeuriteNode, ntNeuronNode rootAllSomaNode,
            String directory, String prefixName) throws IOException {
        String path = directory + prefixName + "_Connection.txt";
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
        SimpleDateFormat fileFormatter = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        try {
            // write SWC header
            out.writeBytes("# CREATION_DATE "+fileFormatter.format(now)+"\n");
            out.writeBytes("# "+prefixName + "_Connection.txt\n");
            out.writeBytes("# CREATED BY nTracer-Batch Process 1.0.6\n");
            out.writeBytes("# Dawen Cai, University of Michigan (dwcai@umich.edu)\n");
            out.writeBytes("# \n\n");

            for (int k=0; k<rootNeuriteNode.getChildCount(); k++){
                ntNeuronNode neuriteParentNode = (ntNeuronNode) (rootNeuriteNode.getChildAt(k));
                ntNeuronNode somaParentNode = (ntNeuronNode) (rootAllSomaNode.getChildAt(k));
                exportNodeConnection(out, neuriteParentNode, somaParentNode);
            }            
            out.flush();
        } catch (IOException e) {
            IJ.error("Connection export error: " + e);
        }
        out.close();
    }
    private void exportNodeConnection(DataOutputStream out, 
            ntNeuronNode neuriteParentNode, ntNeuronNode somaParentNode) throws IOException {
        ArrayList<String> allInputNeuronNumber = new ArrayList<String>();
        ArrayList<String> somaConnectedNeuronNumber = new ArrayList<String>();
        ArrayList<String> neuriteConnectedNeuronNumber = new ArrayList<String>();
        ArrayList<String> dendriteConnectedNeuronNumber = new ArrayList<String>();
        ArrayList<String> axonConnectedNeuronNumber = new ArrayList<String>();
        
        String neuronNumber = neuriteParentNode.getNeuronNumber();
        out.writeBytes("Neuron "+neuronNumber+"\n");

        try {
        exportAllSomaConnections(out, somaParentNode, somaConnectedNeuronNumber);
        exportAllBranchConnections(out, neuriteParentNode, neuriteConnectedNeuronNumber, 
                dendriteConnectedNeuronNumber, axonConnectedNeuronNumber);
        for (String connected : somaConnectedNeuronNumber) {
            allInputNeuronNumber.add(connected);
        }
        for (String connected : dendriteConnectedNeuronNumber) {
            allInputNeuronNumber.add(connected);
        }

        if (allInputNeuronNumber.isEmpty() && neuriteConnectedNeuronNumber.isEmpty() && axonConnectedNeuronNumber.isEmpty()){
            out.writeBytes("has no connections\n");
        } else {
            out.writeBytes("\n");
            out.writeBytes("Soma receives "+somaConnectedNeuronNumber.size()+" connections to "
                    +analysis.sortUniqueNeuronCountConnections(somaConnectedNeuronNumber).size()+" neurons.\n");     
            out.writeBytes("Dendrites receive "+dendriteConnectedNeuronNumber.size()+" connections to "
                    +analysis.sortUniqueNeuronCountConnections(dendriteConnectedNeuronNumber).size()+" neurons.\n"); 
            out.writeBytes("Axon sends out " + axonConnectedNeuronNumber.size() + " connections to "
                    + analysis.sortUniqueNeuronCountConnections(axonConnectedNeuronNumber).size() + " neurons.\n");
            if (neuriteConnectedNeuronNumber.size() > 0) {
                out.writeBytes("*** Check connection ERROR *** Neurite made " + neuriteConnectedNeuronNumber.size() + " connections to "
                        + analysis.sortUniqueNeuronCountConnections(neuriteConnectedNeuronNumber).size() + " neurons.\n");
            }
            out.writeBytes("\n");
            ArrayList<int[]> sortedUniqueInputConnection = analysis.sortUniqueNeuronCountConnections(allInputNeuronNumber);
            out.writeBytes("Neuron "+neuronNumber+" receive "+allInputNeuronNumber.size()+" total somatic and dendritic inputs from "
                    +sortedUniqueInputConnection.size()+" other neurons.\n");
            String[][] binInputNeurons = analysis.sortNeuronsWithSameConnectionNumber(sortedUniqueInputConnection);
            for (int i = 1; i< binInputNeurons.length; i++){
                if (!binInputNeurons[i][0].equals("0")){
                    out.writeBytes(binInputNeurons[i][0] + " other neurons, each of which contributes "+i+" inputs ( "+binInputNeurons[i][1]+")\n");
                }
            }
            out.writeBytes("\n");
            ArrayList<int[]> sortedUniqueOutputConnection = analysis.sortUniqueNeuronCountConnections(axonConnectedNeuronNumber);
            out.writeBytes("Neuron "+neuronNumber+" send out "+axonConnectedNeuronNumber.size()+" total axonal outputs to "
                    +sortedUniqueOutputConnection.size()+" other neurons.\n");
            String[][] binOutputNeurons = analysis.sortNeuronsWithSameConnectionNumber(sortedUniqueOutputConnection);
            for (int i = 1; i< binOutputNeurons.length; i++){
                if (!binOutputNeurons[i][0].equals("0")){
                    out.writeBytes(binOutputNeurons[i][0]+ " other neurons, each of which receives "+i+" outputs ( "+binOutputNeurons[i][1]+")\n");
                }
            }
        }
        out.writeBytes("\n----------------------------------------------------------\n\n");
        } catch (IOException e) {}
    }
    // export (and return) connections of selected soma(s)
    private void exportAllSomaConnections(DataOutputStream out, ntNeuronNode somaSomaNode, 
            ArrayList<String> somaConnectedNeuronNumber) throws IOException {
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
                out.writeBytes(logTag+"\n");
            }
        }
    }
    // log (and return) connections of selected branch(es)
    public void exportAllBranchConnections(DataOutputStream out,ntNeuronNode parentBranchNode,
                ArrayList<String> neuriteConnectedNeuronNumber, 
                ArrayList<String> dendriteConnectedNeuronNumber,
                ArrayList<String> axonConnectedNeuronNumber) throws IOException {
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
            out.writeBytes(logTag+"\n");
        }
            
        for (int i = 0; i < parentBranchNode.getChildCount(); i++) {            
                exportAllBranchConnections(out, (ntNeuronNode)parentBranchNode.getChildAt(i),
                    neuriteConnectedNeuronNumber, dendriteConnectedNeuronNumber, axonConnectedNeuronNumber);
        }
    }
 
    public void savePackagedData(ntNeuronNode autosaveNeuronNode, ntNeuronNode autosaveAllSomaNode, ntNeuronNode autosaveSpineNode,
            ArrayList<String> autosaveExpandedNeuronNames, ArrayList<String> autosaveSelectedNeuronNames,
            ArrayList<String> autosaveSelectedSomaSliceNames, ArrayList<Integer> autosaveSelectedTableRows,
            Rectangle autosaveNeuronTreeVisibleRect, Rectangle autosaveDisplaySomaTreeVisibleRect,
            Rectangle autosavePointTableVisibleRect, String folder, String fileName,
            int[] impPosition, int xShift, int yShift, int zShift, String[] nTracerParameters) throws IOException {
        String path = folder + fileName + ".zip";
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));

        //write soma and neurite data
        try {
            String label = fileName + "-data.txt";
            zos.putNextEntry(new ZipEntry(label));

            // write tracing and display image parameters
            out.writeBytes("TotalChannel: " + nTracer_.impNChannel);
            out.writeBytes("\n");
            out.writeBytes("ToggleColor: " + nTracer_.toggleColor);
            out.writeBytes("\n");
            out.writeBytes("ToggleChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                out.writeBytes(nTracer_.toggleChannels[ch] + " ");
            }
            out.writeBytes("\n");
            out.writeBytes("AnalysisChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                out.writeBytes(nTracer_.analysisChannels[ch] + " ");
            }
            out.writeBytes("\n");
            out.writeBytes("ActiveChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                out.writeBytes(nTracer_.activeChannels[ch] + " ");
            }
            out.writeBytes("\n");
            out.writeBytes("xyzResolutions: ");
            for (int i = 0; i < nTracer_.xyzResolutions.length; i++) {
                out.writeBytes(nTracer_.xyzResolutions[i] + " ");
            }
            out.writeBytes("\n");
            
            // write all soma and neurite tracig data
            writeNeuronData(out, autosaveAllSomaNode, autosaveNeuronNode, autosaveSpineNode);

            out.flush();
        } catch (IOException e) {
            IJ.error("Neurite save error: " + e);
        }

        //write neurite expansion and selestion; soma selection; image position; nTracer parameter
        try {
            String label = fileName + "-status.txt";
            //IJ.log(label);
            zos.putNextEntry(new ZipEntry(label));

            // write neurite expansion
            out.writeBytes("NeuriteExpansion: ");
            out.writeBytes("\n");
            for (String autosaveExpandedNeuronName : autosaveExpandedNeuronNames) {
                out.writeBytes("Neuron " + autosaveExpandedNeuronName);
                out.writeBytes("\n");
            }
            out.writeBytes("END");
            out.writeBytes("\n");

            // write neurite selection
            out.writeBytes("NeuriteSelection: ");
            out.writeBytes("\n");
            for (String autosaveSelectedNeuronName : autosaveSelectedNeuronNames) {
                out.writeBytes("Neurite " + autosaveSelectedNeuronName);
                out.writeBytes("\n");
            }
            out.writeBytes("END");
            out.writeBytes("\n");

            // write neuriteTree visible rectangle
            out.writeBytes("NeuriteVisibleRectangle: ");
            out.writeBytes("\n");
            out.writeBytes("X: " + autosaveNeuronTreeVisibleRect.x);
            out.writeBytes("\n");
            out.writeBytes("Y: " + autosaveNeuronTreeVisibleRect.y);
            out.writeBytes("\n");
            out.writeBytes("Width: " + autosaveNeuronTreeVisibleRect.width);
            out.writeBytes("\n");
            out.writeBytes("Height: " + autosaveNeuronTreeVisibleRect.height);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            // write soma selection
            out.writeBytes("SomaSelection: ");
            out.writeBytes("\n");
            for (String autosaveSelectedSomaSliceName : autosaveSelectedSomaSliceNames) {
                out.writeBytes("Soma " + autosaveSelectedSomaSliceName);
                out.writeBytes("\n");
            }

            out.writeBytes("END");
            out.writeBytes("\n");

            // write somaTree visible rectangle
            out.writeBytes("SomaVisibleRectangle: ");
            out.writeBytes("\n");
            out.writeBytes("X: " + autosaveDisplaySomaTreeVisibleRect.x);
            out.writeBytes("\n");
            out.writeBytes("Y: " + autosaveDisplaySomaTreeVisibleRect.y);
            out.writeBytes("\n");
            out.writeBytes("Width: " + autosaveDisplaySomaTreeVisibleRect.width);
            out.writeBytes("\n");
            out.writeBytes("Height: " + autosaveDisplaySomaTreeVisibleRect.height);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            // write trace point selection
            out.writeBytes("PointSelection: ");
            out.writeBytes("\n");
            for (int autosaveSelectedTableRow : autosaveSelectedTableRows) {
                out.writeBytes("Row " + autosaveSelectedTableRow);
                out.writeBytes("\n");
            }
            out.writeBytes("END");
            out.writeBytes("\n");

            // write pointTable_jTable visible rectangle
            out.writeBytes("PointVisibleRectangle: ");
            out.writeBytes("\n");
            out.writeBytes("X: " + autosavePointTableVisibleRect.x);
            out.writeBytes("\n");
            out.writeBytes("Y: " + autosavePointTableVisibleRect.y);
            out.writeBytes("\n");
            out.writeBytes("Width: " + autosavePointTableVisibleRect.width);
            out.writeBytes("\n");
            out.writeBytes("Height: " + autosavePointTableVisibleRect.height);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            // write image channel, z, frame position
            out.writeBytes("Positions: ");
            out.writeBytes("\n");
            out.writeBytes("Channel: " + impPosition[0]);
            out.writeBytes("\n");
            out.writeBytes("zPosition: " + impPosition[1]);
            out.writeBytes("\n");
            out.writeBytes("Frame: " + impPosition[2]);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            // write nTracer control panel parameters
            out.writeBytes("Parameters: ");
            out.writeBytes("\n");
            out.writeBytes("xyRadius: " + nTracerParameters[0]);
            out.writeBytes("\n");
            out.writeBytes("zRadius: " + nTracerParameters[1]);
            out.writeBytes("\n");
            out.writeBytes("colorThreshold: " + nTracerParameters[2]);
            out.writeBytes("\n");
            out.writeBytes("intensityThreshold: " + nTracerParameters[3]);
            out.writeBytes("\n");
            out.writeBytes("extendDisplayPoints: " + nTracerParameters[4]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllPoints: " + nTracerParameters[5]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllName: " + nTracerParameters[6]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllSoma: " + nTracerParameters[7]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllNeuron: " + nTracerParameters[8]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllSpine: " + nTracerParameters[9]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllSynapse: " + nTracerParameters[10]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllConnection: " + nTracerParameters[11]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllSelectedPoints: " + nTracerParameters[12]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedName: " + nTracerParameters[13]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedSoma: " + nTracerParameters[14]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedNeuron: " + nTracerParameters[15]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedArbor: " + nTracerParameters[16]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedBranch: " + nTracerParameters[17]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedSpine: " + nTracerParameters[18]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedSynapse: " + nTracerParameters[19]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedConnection: " + nTracerParameters[20]);
            out.writeBytes("\n");
            out.writeBytes("overlayPointBox: " + nTracerParameters[21]);
            out.writeBytes("\n");
            out.writeBytes("somaLine: " + nTracerParameters[22]);
            out.writeBytes("\n");
            out.writeBytes("neuronLine: " + nTracerParameters[23]);
            out.writeBytes("\n");
            out.writeBytes("arborLine: " + nTracerParameters[24]);
            out.writeBytes("\n");
            out.writeBytes("branchLine: " + nTracerParameters[25]);
            out.writeBytes("\n");
            out.writeBytes("synapseLine: " + nTracerParameters[26]);
            out.writeBytes("\n");
            out.writeBytes("pointBoxLine: " + nTracerParameters[27]);
            out.writeBytes("\n");
            out.writeBytes("synapseRadius: " + nTracerParameters[28]);
            out.writeBytes("\n");
            out.writeBytes("pointBoxRadius: " + nTracerParameters[29]);
            out.writeBytes("\n");
            out.writeBytes("lineWidthOffset: " + nTracerParameters[30]);
            out.writeBytes("\n");
            out.writeBytes("autosaveIntervalMin: " + nTracerParameters[31]);
            out.writeBytes("\n");
            out.writeBytes("delAutosaved: " + nTracerParameters[32]);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            out.flush();
        } catch (IOException e) {
            IJ.error("Expansion and selection save error: " + e);
        }

        out.close();
    }

    public void savePackagedData(ntNeuronNode rootNeuriteNode, ntNeuronNode rootAllSomaNode, ntNeuronNode rootSpineNode,
            JTree neuriteList_jTree, JTree somaList_jTree, JTable pointTable_jTable,
            File selectedFile, int channel, int zPosition, int frame, String[] nTracerParameters) throws IOException {
        String path = selectedFile.getPath();
        String fileName = selectedFile.getName();
        if (fileName.endsWith(".zip")) {
            fileName = fileName.split(".zip")[0];
        }
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));

        //write soma and neurite data
        try {
            String label = fileName + "-data.txt";
            zos.putNextEntry(new ZipEntry(label));

            // write tracing and display image parameters
            out.writeBytes("TotalChannel: " + nTracer_.impNChannel);
            out.writeBytes("\n");
            out.writeBytes("ToggleColor: " + nTracer_.toggleColor);
            out.writeBytes("\n");
            out.writeBytes("ToggleChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                out.writeBytes(nTracer_.toggleChannels[ch] + " ");
            }
            out.writeBytes("\n");
            out.writeBytes("AnalysisChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                out.writeBytes(nTracer_.analysisChannels[ch] + " ");
            }
            out.writeBytes("\n");
            out.writeBytes("ActiveChannels: ");
            for (int ch = 0; ch < nTracer_.impNChannel; ch++) {
                out.writeBytes(nTracer_.activeChannels[ch] + " ");
            }
            out.writeBytes("\n");
            out.writeBytes("xyzResolutions: ");
            for (int i = 0; i < nTracer_.xyzResolutions.length; i++) {
                out.writeBytes(nTracer_.xyzResolutions[i] + " ");
            }
            out.writeBytes("\n");

            // write all soma, neurite and spine tracig data
            writeNeuronData(out, rootAllSomaNode, rootNeuriteNode, rootSpineNode);

            out.flush();
        } catch (IOException e) {
            IJ.error("Neurite save error: " + e);
        }

        //write neurite expansion and selestion; soma selection; image position; nTracer parameter
        try {
            String label = fileName + "-status.txt";
            //IJ.log(label);
            zos.putNextEntry(new ZipEntry(label));

            // write neurite expansion
            out.writeBytes("NeuriteExpansion: ");
            out.writeBytes("\n");
            for (int n = 0; n < rootNeuriteNode.getChildCount(); n++) {
                ntNeuronNode neuron = (ntNeuronNode) rootNeuriteNode.getChildAt(n);
                TreePath connectedNeuritePath = new TreePath(neuron.getPath());
                if (neuriteList_jTree.isExpanded(connectedNeuritePath)) {
                    out.writeBytes("Neuron " + neuron.toString());
                    out.writeBytes("\n");
                }
            }
            out.writeBytes("END");
            out.writeBytes("\n");

            // write neurite selection
            out.writeBytes("NeuriteSelection: ");
            out.writeBytes("\n");
            TreePath[] selectedNeuritePaths = neuriteList_jTree.getSelectionPaths();
            if (selectedNeuritePaths != null) {
                for (TreePath selectedPath : selectedNeuritePaths) {
                    ntNeuronNode neurite = (ntNeuronNode) selectedPath.getLastPathComponent();
                    out.writeBytes("Neurite " + neurite.toString());
                    out.writeBytes("\n");
                }
            }
            out.writeBytes("END");
            out.writeBytes("\n");

            // write neuriteTree visible rectangle
            Rectangle neuriteTreeVisibleRect = neuriteList_jTree.getVisibleRect();
            out.writeBytes("NeuriteVisibleRectangle: ");
            out.writeBytes("\n");
            out.writeBytes("X: " + neuriteTreeVisibleRect.x);
            out.writeBytes("\n");
            out.writeBytes("Y: " + neuriteTreeVisibleRect.y);
            out.writeBytes("\n");
            out.writeBytes("Width: " + neuriteTreeVisibleRect.width);
            out.writeBytes("\n");
            out.writeBytes("Height: " + neuriteTreeVisibleRect.height);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            // write soma selection
            out.writeBytes("SomaSelection: ");
            out.writeBytes("\n");
            TreePath[] selectedSomaPaths = somaList_jTree.getSelectionPaths();
            if (selectedSomaPaths != null) {
                for (TreePath selectedPath : selectedSomaPaths) {
                    ntNeuronNode soma = (ntNeuronNode) selectedPath.getLastPathComponent();
                    out.writeBytes("Soma " + soma.toString());
                    out.writeBytes("\n");
                }
            }
            out.writeBytes("END");
            out.writeBytes("\n");

            // write somaTree visible rectangle
            Rectangle somaTreeVisibleRect = somaList_jTree.getVisibleRect();
            out.writeBytes("SomaVisibleRectangle: ");
            out.writeBytes("\n");
            out.writeBytes("X: " + somaTreeVisibleRect.x);
            out.writeBytes("\n");
            out.writeBytes("Y: " + somaTreeVisibleRect.y);
            out.writeBytes("\n");
            out.writeBytes("Width: " + somaTreeVisibleRect.width);
            out.writeBytes("\n");
            out.writeBytes("Height: " + somaTreeVisibleRect.height);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            // write trace point selection
            out.writeBytes("PointSelection: ");
            out.writeBytes("\n");
            int[] selectedPointRows = pointTable_jTable.getSelectedRows();
            if (selectedPointRows != null) {
                for (int selectedRow : selectedPointRows) {
                    out.writeBytes("Row " + selectedRow);
                    out.writeBytes("\n");
                }
            }
            out.writeBytes("END");
            out.writeBytes("\n");

            // write pointTable_jTable visible rectangle
            Rectangle pointTableVisibleRect = pointTable_jTable.getVisibleRect();
            out.writeBytes("PointVisibleRectangle: ");
            out.writeBytes("\n");
            out.writeBytes("X: " + pointTableVisibleRect.x);
            out.writeBytes("\n");
            out.writeBytes("Y: " + pointTableVisibleRect.y);
            out.writeBytes("\n");
            out.writeBytes("Width: " + pointTableVisibleRect.width);
            out.writeBytes("\n");
            out.writeBytes("Height: " + pointTableVisibleRect.height);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            // write image channel, z, frame position
            out.writeBytes("Positions: ");
            out.writeBytes("\n");
            out.writeBytes("Channel: " + channel);
            out.writeBytes("\n");
            out.writeBytes("zPosition: " + zPosition);
            out.writeBytes("\n");
            out.writeBytes("Frame: " + frame);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            // write nTracer control panel parameters
            out.writeBytes("Parameters: ");
            out.writeBytes("\n");
            out.writeBytes("xyRadius: " + nTracerParameters[0]);
            out.writeBytes("\n");
            out.writeBytes("zRadius: " + nTracerParameters[1]);
            out.writeBytes("\n");
            out.writeBytes("colorThreshold: " + nTracerParameters[2]);
            out.writeBytes("\n");
            out.writeBytes("intensityThreshold: " + nTracerParameters[3]);
            out.writeBytes("\n");
            out.writeBytes("extendDisplayPoints: " + nTracerParameters[4]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllPoints: " + nTracerParameters[5]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllName: " + nTracerParameters[6]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllSoma: " + nTracerParameters[7]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllNeuron: " + nTracerParameters[8]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllSpine: " + nTracerParameters[9]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllSynapse: " + nTracerParameters[10]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllConnection: " + nTracerParameters[11]);
            out.writeBytes("\n");
            out.writeBytes("overlayAllSelectedPoints: " + nTracerParameters[12]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedName: " + nTracerParameters[13]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedSoma: " + nTracerParameters[14]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedNeuron: " + nTracerParameters[15]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedArbor: " + nTracerParameters[16]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedBranch: " + nTracerParameters[17]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedSpine: " + nTracerParameters[18]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedSynapse: " + nTracerParameters[19]);
            out.writeBytes("\n");
            out.writeBytes("overlaySelectedConnection: " + nTracerParameters[20]);
            out.writeBytes("\n");
            out.writeBytes("overlayPointBox: " + nTracerParameters[21]);
            out.writeBytes("\n");
            out.writeBytes("somaLine: " + nTracerParameters[22]);
            out.writeBytes("\n");
            out.writeBytes("neuronLine: " + nTracerParameters[23]);
            out.writeBytes("\n");
            out.writeBytes("arborLine: " + nTracerParameters[24]);
            out.writeBytes("\n");
            out.writeBytes("branchLine: " + nTracerParameters[25]);
            out.writeBytes("\n");
            out.writeBytes("synapseLine: " + nTracerParameters[26]);
            out.writeBytes("\n");
            out.writeBytes("pointBoxLine: " + nTracerParameters[27]);
            out.writeBytes("\n");
            out.writeBytes("synapseRadius: " + nTracerParameters[28]);
            out.writeBytes("\n");
            out.writeBytes("pointBoxRadius: " + nTracerParameters[29]);
            out.writeBytes("\n");
            out.writeBytes("lineWidthOffset: " + nTracerParameters[30]);
            out.writeBytes("\n");
            out.writeBytes("autosaveIntervalMin: " + nTracerParameters[31]);
            out.writeBytes("\n");
            out.writeBytes("delAutosaved: " + nTracerParameters[32]);
            out.writeBytes("\n");
            out.writeBytes("END");
            out.writeBytes("\n");

            out.flush();
        } catch (IOException e) {
            IJ.error("Expansion and selection save error: " + e);
        }

        out.close();
    }

    private void writeNeuronData(DataOutputStream out,
            ntNeuronNode allSomaNode, ntNeuronNode allNeuronNode, ntNeuronNode allSpineNode) throws IOException {
        for (int k = 0; k < allNeuronNode.getChildCount(); k++) {
            ntNeuronNode childNeuriteNode = (ntNeuronNode) (allNeuronNode.getChildAt(k));
            ntNeuronNode childSomaNode = (ntNeuronNode) (allSomaNode.getChildAt(k));
            // write neuron name
            out.writeBytes("Neuron " + childNeuriteNode.toString());
            out.writeBytes("\n");
            // write soma data
            writeSomaData(out, childSomaNode);
            writeNeuriteData(out, childNeuriteNode);
            out.writeBytes("ENDneuron");
            out.writeBytes("\n");
        }
        for (int i = 0; i< allSpineNode.getChildCount(); i++){
            ntNeuronNode spineNode = (ntNeuronNode) (allSpineNode.getChildAt(i));
            // write sppine name
            out.writeBytes("Spine " + spineNode.toString());
            out.writeBytes("\n");
            // write spine data
            writeSpineData(out, spineNode);
        }
    }

    private void writeSomaData(DataOutputStream out,
            ntNeuronNode parentSomaNode) throws IOException {
        if (parentSomaNode.getChildCount() == 0) {
            String parentSomaName = parentSomaNode.toString();
            if (parentSomaName.contains("/")){
                parentSomaName = parentSomaName.split("/")[0];
            }
            out.writeBytes("Soma " + parentSomaName + ":-1");
            out.writeBytes("\n");
            return;
        }
        for (int k = 0; k < parentSomaNode.getChildCount(); k++) {
            ntNeuronNode childNode = (ntNeuronNode) (parentSomaNode.getChildAt(k));
            // write soma name
            out.writeBytes("Soma " + childNode.toString());
            out.writeBytes("\n");
            // write node data
            ArrayList<String[]> data = childNode.getTracingResult();
            for (String[] point : data) {
                out.writeBytes("POINT: ");
                for (int i = 0; i < 7; i++) {
                    out.writeBytes(point[i] + " ");
                }
            }
            out.writeBytes("END");
            out.writeBytes("\n");
        }
    }

    private void writeNeuriteData(DataOutputStream out,
            ntNeuronNode parentNeuriteNode) throws IOException {
        for (int k = 1; k <= parentNeuriteNode.getChildCount(); k++) {
            ntNeuronNode childNode = (ntNeuronNode) (parentNeuriteNode.getChildAt(k - 1));
            // write node name
            out.writeBytes("Neurite " + childNode.toString());
            out.writeBytes("\n");
            // write node data
            ArrayList<String[]> data = childNode.getTracingResult();
            for (String[] point : data) {
                out.writeBytes("POINT: ");
                for (int i = 0; i < 7; i++) {
                    out.writeBytes(point[i] + " ");
                }
            }
            out.writeBytes("END");
            out.writeBytes("\n");
            writeNeuriteData(out, childNode);
        }
    }

    private void writeSpineData(DataOutputStream out,
            ntNeuronNode spineNode) throws IOException {
        // write node data
        ArrayList<String[]> data = spineNode.getTracingResult();
        for (String[] point : data) {
            out.writeBytes("POINT: ");
            for (int i = 0; i < 7; i++) {
                out.writeBytes(point[i] + " ");
            }
        }
        out.writeBytes("ENDspine");
        out.writeBytes("\n");
    }

    private final ntTracing Functions;
    private final ntAnalysis analysis;
}
