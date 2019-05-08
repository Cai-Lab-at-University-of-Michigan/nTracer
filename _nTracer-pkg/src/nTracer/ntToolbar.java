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
import ij.ImagePlus;
import ij.gui.*;
import ij.plugin.tool.PlugInTool;

import java.awt.event.*;


/**
 *
 * @author Dawen
 */
public class ntToolbar extends PlugInTool {

        int toolNumber;
        String toolName;

        ntToolbar(int toolNumber, String toolName) {
            this.toolNumber = toolNumber;
            this.toolName = toolName;
        }

        @Override
        public void mousePressed(ImagePlus imp, MouseEvent e) {
            //show(imp, e, "clicked");
        }

        @Override
        public void mouseDragged(ImagePlus imp, MouseEvent e) {
            //show(imp, e, "dragged");
        }

        @Override
        public void showOptionsDialog() {
            //IJ.log("User double clicked on the tool icon");
        }

        void show(ImagePlus imp, MouseEvent e, String msg) {
            ImageCanvas ic = imp.getCanvas();
            int x = ic.offScreenX(e.getX());
            int y = ic.offScreenY(e.getY());
            IJ.log("Tool " + toolNumber + " " + msg + " at (" + x + "," + y + ") on " + imp.getTitle());
        }

        @Override
        public String getToolName() {
            return toolName;
            //return "Custom ntToolbar " + toolNumber;
        }

        @Override
        public String getToolIcon() {
            return "C00aT0f18" + toolName;
            //return "C00aT0f18" + toolNumber;
        }
    }

