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
 * This combines two plugin implementations to reduce data copies when
 * using both in tandem:
 *  - https://imagej.nih.gov/ij/source/ij/plugin/Duplicator.java
 *  - https://github.com/imagej/imagej1/blob/master/ij/plugin/ZProjector.java
 * 
 */
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import ij.measure.Calibration;
import ij.plugin.CanvasResizer;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;

import ij.plugin.frame.Recorder;
import java.lang.*; 
import java.util.Arrays;

public class DuplicateProjector implements PlugIn, TextListener, ItemListener {
	private static boolean staticDuplicateStack;
	private boolean duplicateStack;
	private int first, last;
	private Checkbox checkbox;
	private TextField titleField, rangeField;
	private TextField[] rangeFields;
	private int firstC, lastC, firstZ, lastZ, firstT, lastT;
	private String defaultTitle;
	private String sliceLabel;
	private ImagePlus imp;
	private boolean legacyMacro;
	private boolean titleChanged;
	private GenericDialog gd;

	public void run(String arg) {
		imp = IJ.getImage();
		Roi roiA = imp.getRoi();
		ImagePlus impA = imp;
		boolean isRotatedRect = (roiA!=null &&  roiA instanceof RotatedRectRoi);
		if (isRotatedRect) {
			Rectangle bounds = imp.getRoi().getBounds();
			imp.setRoi(bounds);
		}
		if (roiA!=null) {
			Rectangle r = roiA.getBounds();
			if (r.x>=imp.getWidth() || r.y>=imp.getHeight() || r.x+r.width<=0 || r.y+r.height<=0) {
				IJ.error("Roi is outside image");
				return;
			}
		}
		int stackSize = imp.getStackSize();
		String title = imp.getTitle();
		String newTitle = WindowManager.getUniqueName(title);
		defaultTitle = newTitle;
		duplicateStack = staticDuplicateStack && !IJ.isMacro();
		if (!IJ.altKeyDown()||stackSize>1) {
			if (imp.isHyperStack() || imp.isComposite()) {
				duplicateHyperstack(imp, newTitle);			
				if (isRotatedRect) {
					straightenRotatedRect(impA, roiA, IJ.getImage());	
				}								
				return;
			} else
				newTitle = showDialog(imp, "Duplicate...", "Title: ");
		}
		if (newTitle==null) {
			if (isRotatedRect)
				imp.setRoi(roiA);
			return;
		}
		ImagePlus imp2;
		Roi roi = imp.getRoi();		
			if (duplicateStack && (first>1||last<stackSize))
				imp2 = run(imp, first, last);
			else if (duplicateStack || imp.getStackSize()==1)
				imp2 = run(imp);
			else
				imp2 = crop(imp);
			Calibration cal = imp2.getCalibration();
			if (roi!=null && (cal.xOrigin!=0.0||cal.yOrigin!=0.0)) {
				cal.xOrigin -= roi.getBounds().x;
				cal.yOrigin -= roi.getBounds().y;
			}	
		imp2.setTitle(newTitle);
		if (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE) {
			Roi roi2 = (Roi)cropRoi(imp, roi).clone();
			roi2.setLocation(0, 0);
			imp2.setRoi(roi2);
		}
		imp2.show();
		if (stackSize>1 && imp2.getStackSize()==stackSize)
			imp2.setSlice(imp.getCurrentSlice());
		if (isRotatedRect)
			straightenRotatedRect(impA, roiA, imp2);		
	}
	
 /** Rotates duplicated part of image
	- impA is original image,
	- roiA is orig rotatedRect
	- impB contains duplicated overlapping bounding rectangle	
	processing steps:
	- increase canvas of impB before rotation
	- rotate impB
	- calculate excentricity
	- translate to compensate excentricity 
	- create orthogonal rectangle in center
	- crop to impC	
	Author: N. Vischer
	*/
	private void straightenRotatedRect(ImagePlus impA, Roi roiA, ImagePlus impB) {
		impB.deleteRoi(); //we have it in roiA
		Color colorBack = Toolbar.getBackgroundColor();	
		IJ.setBackgroundColor(0,0,0);
		String title = impB.getTitle();
		if(impB.getOverlay() != null)
			impB.getOverlay().clear();
		int boundLeft = roiA.getBounds().x;
		int boundTop = roiA.getBounds().y;
		int boundWidth = roiA.getBounds().width;
		int boundHeight = roiA.getBounds().height;

		float[] xx = roiA.getFloatPolygon().xpoints;
		float[] yy = roiA.getFloatPolygon().ypoints;

		double dx1 = xx[1] - xx[0];//calc sides and angle
		double dy1 = yy[1] - yy[0];
		double dx2 = xx[2] - xx[1];
		double dy2 = yy[2] - yy[1];

		double rrWidth = Math.sqrt(dx1 * dx1 + dy1 * dy1);//width of rot rect
		double rrHeight = Math.sqrt(dx2 * dx2 + dy2 * dy2);
		double rrDia = Math.sqrt(rrWidth * rrWidth + rrHeight * rrHeight);

		double phi1 = -Math.atan2(dy1, dx1);
		double phi0 = phi1 * 180 / Math.PI;

		double usedL = Math.max(boundLeft, 0); //usedrect is orthogonal rect to be rotated
		double usedR = Math.min(boundLeft + boundWidth, impA.getWidth());
		double usedT = Math.max(boundTop, 0);
		double usedB = Math.min(boundTop + boundHeight, impA.getHeight());
		double usedCX = (usedL + usedR) / 2;
		double usedCY = (usedT + usedB) / 2; //Center of UsedRect

		double boundsCX = boundLeft + boundWidth / 2;//Center of Bound = center of RotRect
		double boundsCY = boundTop + boundHeight / 2;

		double dx3 = boundsCX - usedCX;//calculate excentricity
		double dy3 = boundsCY - usedCY;
		double rad3 = Math.sqrt(dx3 * dx3 + dy3 * dy3);
		double phi3 = Math.atan2(dy3, dx3);
		double phi4 = phi3 + phi1;
		double dx4 = -rad3 * Math.cos(phi4);
		double dy4 = -rad3 * Math.sin(phi4);

		//Increase canvas to a square large enough for rotation
		ImageStack stackOld = impB.getStack();
		int currentSlice = impB.getCurrentSlice();
		double xOff = (rrDia - (usedR - usedL)) / 2;//put img in center
		double yOff = (rrDia - (usedB - usedT)) / 2;

		ImageStack stackNew = (new CanvasResizer()).expandStack(stackOld, (int) rrDia, (int) rrDia, (int) xOff, (int) yOff);
		impB.setStack(stackNew);
		ImageProcessor ip = impB.getProcessor();
		ip.setInterpolationMethod(ImageProcessor.BILINEAR);
		ip.setBackgroundValue(0);

		for (int slc = 0; slc < stackNew.size(); slc++) {
			impB.setSlice(slc+1);
			ip.rotate(phi0); //Rotate
			ip.translate(dx4, dy4); //Translate
		}

		int x = (impB.getWidth() - (int) rrWidth) / 2;
		int y = (impB.getHeight() - (int) rrHeight) / 2;

		impB.setStack(impB.getStack().crop(x, y, 0, (int) rrWidth, (int) rrHeight, impB.getStack().getSize()));//Crop
		impB.setSlice(currentSlice);
		impB.setTitle(title);
		impB.show();
		impB.updateAndDraw();
		impA.setRoi(roiA); //restore rotated rect in source image
		Toolbar.setBackgroundColor(colorBack);
	}	
	                
	/** Returns a copy of the image, stack or hyperstack contained in the specified ImagePlus.
	* @see ij.ImagePlus#duplicate
	*/
	public ImagePlus run(ImagePlus imp) {
		if (imp.getStackSize()==1)
			return crop(imp);
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		Roi roi2 = cropRoi(imp, roi);
		if (roi2!=null && roi2.isArea())
			rect = roi2.getBounds();
		ImageStack stack = imp.getStack();
		boolean virtualStack = stack.isVirtual();
		double min = imp.getDisplayRangeMin();
		double max = imp.getDisplayRangeMax();
		ImageStack stack2 = null;
		int n = stack.getSize();
		boolean showProgress = virtualStack || ((double)n*stack.getWidth()*stack.getHeight()>=209715200.0);
		for (int i=1; i<=n; i++) {
			if (showProgress) {
				IJ.showStatus("Duplicating: "+i+"/"+n);
				IJ.showProgress(i,n);
			}
			ImageProcessor ip2 = stack.getProcessor(i);
			ip2.setRoi(rect);
			ip2 = ip2.crop();
			if (stack2==null)
				stack2 = new ImageStack(ip2.getWidth(), ip2.getHeight(), imp.getProcessor().getColorModel());
			stack2.addSlice(stack.getSliceLabel(i), ip2);
		}
		IJ.showProgress(1.0);
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack("DUP_"+imp.getTitle(), stack2);
		String info = (String)imp.getProperty("Info");
		if (info!=null)
			imp2.setProperty("Info", info);
		int[] dim = imp.getDimensions();
		imp2.setDimensions(dim[2], dim[3], dim[4]);
		if (imp.isComposite()) {
			imp2 = new CompositeImage(imp2, 0);
			((CompositeImage)imp2).copyLuts(imp);
		}
		if (virtualStack)
			imp2.setDisplayRange(min, max);
		if (imp.isHyperStack())
			imp2.setOpenAsHyperStack(true);
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay())
			imp2.setOverlay(overlay.crop(rect));
   		if (Recorder.record) {
   			if (imp.getRoi()==null)
   				Recorder.recordCall("imp2 = imp.duplicate();");
   			else
   				Recorder.recordCall("imp2 = imp.crop(\"stack\");");
   		}
		return imp2;
	}
	
	/** Returns a copy the current stack image, cropped if there is a selection.
	* @see ij.ImagePlus#crop
	*/
	public ImagePlus crop(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor ip2 = ip.crop();
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setProcessor("DUP_"+imp.getTitle(), ip2);
		String info = (String)imp.getProperty("Info");
		if (info!=null)
			imp2.setProperty("Info", info);
		if (imp.isStack()) {
			ImageStack stack = imp.getStack();
			String label = stack.getSliceLabel(imp.getCurrentSlice());
			if (label!=null) {
				if (label.length()>250 && label.indexOf('\n')>0 && label.contains("0002,"))
					imp2.setProperty("Info", label); // DICOM metadata
				else
					imp2.setProperty("Label", label);					
			}
			if (imp.isComposite()) {
				LUT lut = ((CompositeImage)imp).getChannelLut();
				imp2.getProcessor().setColorModel(lut);
			}
		} else {
			String label = (String)imp.getProperty("Label");
			if (label!=null)
				imp2.setProperty("Label", label);
		}
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay()) {
			Overlay overlay2 = overlay.crop(ip.getRoi());
 			if (imp.getStackSize()>1)
 				overlay2.crop(imp.getCurrentSlice(), imp.getCurrentSlice());
 			imp2.setOverlay(overlay2);
 		}
   		if (Recorder.record) {
   			if (imp.getStackSize()==1) {
   				if (imp.getRoi()==null)
   					Recorder.recordCall("imp2 = imp.duplicate();");
   				else
   					Recorder.recordCall("imp2 = imp.crop();");
   			} else
   				Recorder.recordCall("imp2 = imp.crop();");
   		}
		return imp2;
	}
	
	/** Returns a new stack containing a subrange of the specified stack. */
	public ImagePlus run(ImagePlus imp, int firstSlice, int lastSlice) {
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			rect = roi.getBounds();
		ImageStack stack = imp.getStack();
		boolean virtualStack = stack.isVirtual();
		double min = imp.getDisplayRangeMin();
		double max = imp.getDisplayRangeMax();
		ImageStack stack2 = null;	
		int n = lastSlice-firstSlice+1;
		boolean showProgress = virtualStack || ((double)n*stack.getWidth()*stack.getHeight()>=209715200.0);
		for (int i=firstSlice; i<=lastSlice; i++) {
			if (showProgress) {
				IJ.showStatus("Duplicating: "+i+"/"+lastSlice);
				IJ.showProgress(i-firstSlice,n);
			}
			ImageProcessor ip2 = stack.getProcessor(i);
			ip2.setRoi(rect);
			ip2 = ip2.crop();
			if (stack2==null)
				stack2 = new ImageStack(ip2.getWidth(), ip2.getHeight(), imp.getProcessor().getColorModel());
			stack2.addSlice(stack.getSliceLabel(i), ip2);
		}
		IJ.showProgress(1.0);
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack("DUP_"+imp.getTitle(), stack2);
		String info = (String)imp.getProperty("Info");
		if (info!=null)
			imp2.setProperty("Info", info);
		int size = stack2.getSize();
		boolean tseries = imp.getNFrames()==imp.getStackSize();
		if (tseries)
			imp2.setDimensions(1, 1, size);
		else
			imp2.setDimensions(1, size, 1);
		if (virtualStack)
			imp2.setDisplayRange(min, max);
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay()) {
			Overlay overlay2 = overlay.crop(rect);
			overlay2.crop(firstSlice, lastSlice);
			imp2.setOverlay(overlay2);
		}
   		if (Recorder.record)
   			Recorder.recordCall("imp2 = imp.crop(\""+firstSlice+"-"+lastSlice+"\");");
		return imp2;
	}

	/** Returns a new hyperstack containing a possibly reduced version of the input image. */
	public ImagePlus run(ImagePlus imp, int firstC, int lastC, int firstZ, int lastZ, int firstT, int lastT) {
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		Roi roi2 = cropRoi(imp, roi);
		if (roi2!=null && roi2.isArea())
			rect = roi2.getBounds();
		ImageStack stack = imp.getStack();
		ImageStack stack2 = null;
		for (int t=firstT; t<=lastT; t++) {
			for (int z=firstZ; z<=lastZ; z++) {
				for (int c=firstC; c<=lastC; c++) {
					int n1 = imp.getStackIndex(c, z, t);
					ImageProcessor ip = stack.getProcessor(n1);
					String label = stack.getSliceLabel(n1);
					ip.setRoi(rect);
					ip = ip.crop();
					if (stack2==null)
						stack2 = new ImageStack(ip.getWidth(), ip.getHeight(), null);
					stack2.addSlice(label, ip);
				}
			}
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack("DUP_"+imp.getTitle(), stack2);
		imp2.setDimensions(lastC-firstC+1, lastZ-firstZ+1, lastT-firstT+1);
		if (imp.isComposite()) {
			int mode = ((CompositeImage)imp).getMode();
			if (lastC>firstC) {
				imp2 = new CompositeImage(imp2, mode);
				int i2 = 1;
				for (int i=firstC; i<=lastC; i++) {
					LUT lut = ((CompositeImage)imp).getChannelLut(i);
					((CompositeImage)imp2).setChannelLut(lut, i2++);
				}
			} else if (firstC==lastC) {
				LUT lut = ((CompositeImage)imp).getChannelLut(firstC);
				imp2.getProcessor().setColorModel(lut);
				imp2.setDisplayRange(lut.min, lut.max);
			}
        }
		imp2.setOpenAsHyperStack(true);
		Calibration cal = imp2.getCalibration();
		if (roi!=null && (cal.xOrigin!=0.0||cal.yOrigin!=0.0)) {
			cal.xOrigin -= roi.getBounds().x;
			cal.yOrigin -= roi.getBounds().y;
		}
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay()) {
			Overlay overlay2 = overlay.crop(roi2!=null?roi2.getBounds():null);
			overlay2.crop(firstC, lastC, firstZ, lastZ, firstT, lastT);
			imp2.setOverlay(overlay2);
		}
   		if (Recorder.record)
   			Recorder.recordCall("imp2 = new Duplicator().run(imp, "+firstC+", "+lastC+", "+firstZ+", "+lastZ+", "+firstT+", "+lastT+");");
		return imp2;
	}

	String showDialog(ImagePlus imp, String dialogTitle, String prompt) {
		int stackSize = imp.getStackSize();
		String options = Macro.getOptions();
		boolean isMacro = options!=null;
		duplicateStack = stackSize>1 && duplicateStack && !isMacro;
		legacyMacro = options!=null && (options.contains("duplicate")||!options.contains("use"));
		String title = getNewTitle();
		if (title==null) title=defaultTitle;
		GenericDialog gd = new GenericDialog(dialogTitle);
		this.gd = gd;
		gd.addStringField(prompt, title, 15);
		if (stackSize>1) {
			gd.addCheckbox("Duplicate stack", duplicateStack);
			gd.setInsets(2, 30, 3);
			gd.addStringField("Range:", "1-"+stackSize);
			if (!isMacro) {
				checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
				checkbox.addItemListener(this);
				Vector v = gd.getStringFields();
				titleField = (TextField)v.elementAt(0);
				rangeField = (TextField)v.elementAt(1);
				titleField.addTextListener(this);
				rangeField.addTextListener(this);
			}
		}
		gd.setSmartRecording(true);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		title = gd.getNextString();
		if (stackSize>1) {
			duplicateStack = gd.getNextBoolean();
			if (duplicateStack) {
				String[] range = Tools.split(gd.getNextString(), " -");
				double d1 = gd.parseDouble(range[0]);
				double d2 = range.length==2?gd.parseDouble(range[1]):Double.NaN;
				first = Double.isNaN(d1)?1:(int)d1;
				last = Double.isNaN(d2)?stackSize:(int)d2;
				if (first<1) first = 1;
				if (last>stackSize) last = stackSize;
				if (first>last) {first=1; last=stackSize;}
			} else {
				first = 1;
				last = stackSize;
			}
		}
		if (!isMacro)
			staticDuplicateStack = duplicateStack;
		if (Recorder.record && titleField!=null && titleField.getText().equals(sliceLabel))
			Recorder.recordOption("use");
		return title;
	}
	
	private String getNewTitle() {
		if (titleChanged)
			return null;
		String title = defaultTitle;
		if (imp.getStackSize()>1 && !duplicateStack && !legacyMacro && (checkbox==null||!checkbox.getState())) {
			ImageStack stack = imp.getStack();
			String label = stack.getShortSliceLabel(imp.getCurrentSlice());
			if (label!=null && label.length()==0)
				label = null;
			if (label!=null) {
				title = label;
				sliceLabel = label;
			}
		}
		return title;
	}
	
	void duplicateHyperstack(ImagePlus imp, String newTitle) {
		newTitle = showHSDialog(imp, newTitle);
		if (newTitle==null)
			return;
		ImagePlus imp2 = null;
		Roi roi = imp.getRoi();
		if (!duplicateStack) {
			int nChannels = imp.getNChannels();
			boolean singleComposite = imp.isComposite() && nChannels==imp.getStackSize();
			if (!singleComposite && nChannels>1 && imp.isComposite() && ((CompositeImage)imp).getMode()==IJ.COMPOSITE) {
				firstC = 1;
				lastC = nChannels;
			} else
				firstC = lastC = imp.getChannel();
			firstZ = lastZ = imp.getSlice();
			firstT = lastT = imp.getFrame();
		}
		imp2 = run(imp, firstC, lastC, firstZ, lastZ, firstT, lastT);
		if (imp2==null) return;
		imp2.setTitle(newTitle);
		if (imp2.getWidth()==0 || imp2.getHeight()==0) {
			IJ.error("Duplicator", "Selection is outside the image");
			return;
		}
		if (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE) {
			Roi roi2 = (Roi)cropRoi(imp, roi).clone();
			roi2.setLocation(0, 0);
			imp2.setRoi(roi2);
		}
		imp2.show();
		imp2.setPosition(imp.getC(), imp.getZ(), imp.getT());
		if (IJ.isMacro()&&imp2.getWindow()!=null)
			IJ.wait(50);
	}

	String showHSDialog(ImagePlus imp, String newTitle) {
		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int nFrames = imp.getNFrames();
		boolean composite = imp.isComposite() && nChannels==imp.getStackSize();
		String options = Macro.getOptions();
		boolean isMacro = options!=null;
		GenericDialog gd = new GenericDialog("Duplicate");
		gd.addStringField("Title:", newTitle, 15);
		gd.setInsets(12, 20, 8);
		gd.addCheckbox("Duplicate hyperstack", (duplicateStack&&!isMacro)||composite);
		int nRangeFields = 0;
		if (nChannels>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Channels (c):", "1-"+nChannels);
			nRangeFields++;
		}
		if (nSlices>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Slices (z):", "1-"+nSlices);
			nRangeFields++;
		}
		if (nFrames>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Frames (t):", "1-"+nFrames);
			nRangeFields++;
		}
		if (!isMacro) {
			checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
			checkbox.addItemListener(this);
			Vector v = gd.getStringFields();
			rangeFields = new TextField[3];
			for (int i=0; i<nRangeFields; i++) {
				rangeFields[i] = (TextField)v.elementAt(i+1);
				rangeFields[i].addTextListener(this);
			}
		}
		gd.setSmartRecording(true);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		newTitle = gd.getNextString();
		duplicateStack = gd.getNextBoolean();
		if (nChannels>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double c1 = gd.parseDouble(range[0]);
			double c2 = range.length==2?gd.parseDouble(range[1]):Double.NaN;
			firstC = Double.isNaN(c1)?1:(int)c1;
			lastC = Double.isNaN(c2)?firstC:(int)c2;
			if (firstC<1) firstC = 1;
			if (lastC>nChannels) lastC = nChannels;
			if (firstC>lastC) {firstC=1; lastC=nChannels;}
		} else
			firstC = lastC = 1;
		if (nSlices>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double z1 = gd.parseDouble(range[0]);
			double z2 = range.length==2?gd.parseDouble(range[1]):Double.NaN;
			firstZ = Double.isNaN(z1)?1:(int)z1;
			lastZ = Double.isNaN(z2)?firstZ:(int)z2;
			if (firstZ<1) firstZ = 1;
			if (lastZ>nSlices) lastZ = nSlices;
			if (firstZ>lastZ) {firstZ=1; lastZ=nSlices;}
		} else
			firstZ = lastZ = 1;
		if (nFrames>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double t1 = gd.parseDouble(range[0]);
			double t2 = range.length==2?gd.parseDouble(range[1]):Double.NaN;
			firstT= Double.isNaN(t1)?1:(int)t1;
			lastT = Double.isNaN(t2)?firstT:(int)t2;
			if (firstT<1) firstT = 1;
			if (lastT>nFrames) lastT = nFrames;
			if (firstT>lastT) {firstT=1; lastT=nFrames;}
		} else
			firstT = lastT = 1;
		if (!isMacro)
			staticDuplicateStack = duplicateStack;
		return newTitle;
	}
	
	/*
	* Returns the part of 'roi' overlaping 'imp'
	* Author Marcel Boeglin 2013.12.15
	*/
	Roi cropRoi(ImagePlus imp, Roi roi) {
		if (roi==null)
			return null;
		if (imp==null)
			return roi;
		Rectangle b = roi.getBounds();
		int w = imp.getWidth();
		int h = imp.getHeight();
		if (b.x<0 || b.y<0 || b.x+b.width>w || b.y+b.height>h) {
			ShapeRoi shape1 = new ShapeRoi(roi);
			ShapeRoi shape2 = new ShapeRoi(new Roi(0, 0, w, h));
			roi = shape2.and(shape1);
		}
		if (roi.getBounds().width==0 || roi.getBounds().height==0)
			throw new IllegalArgumentException("Selection is outside the image");
		return roi;
	}

	public static Overlay cropOverlay(Overlay overlay, Rectangle bounds) {
		return overlay.crop(bounds);
	}

	public void textValueChanged(TextEvent e) {
		if (IJ.debugMode) IJ.log("Duplicator.textValueChanged: "+e);
		if (e.getSource()==titleField) {
			if (!titleField.getText().equals(getNewTitle()))
				titleChanged = true;
		} else
			checkbox.setState(true);
	}
	
	public void itemStateChanged(ItemEvent e) {
		duplicateStack = checkbox.getState();
		if (titleField!=null) {
			String title = getNewTitle();
			if (title!=null && !title.equals(titleField.getText())) {
				titleField.setText(title);
				if (gd!=null) gd.setDefaultString(0, title);
			}
		}
	}
        

/** This plugin performs a z-projection of the input stack. Type of
    output image is same as type of input image.
    @author Patrick Kelly <phkelly@ucsd.edu>
*/
private class ZProjector implements PlugIn {
    public static final int AVG_METHOD = 0; 
    public static final int MAX_METHOD = 1;
    public static final int MIN_METHOD = 2;
    public static final int SUM_METHOD = 3;
	public static final int SD_METHOD = 4;
	public static final int MEDIAN_METHOD = 5;
	public final String[] METHODS = 
		{"Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices", "Standard Deviation", "Median"}; 
    private static final String METHOD_KEY = "zproject.method";
    private int method = (int)Prefs.get(METHOD_KEY, AVG_METHOD);

    private static final int BYTE_TYPE  = 0; 
    private static final int SHORT_TYPE = 1; 
    private static final int FLOAT_TYPE = 2;
    
    public static final String lutMessage =
    	"Stacks with inverter LUTs may not project correctly.\n"
    	+"To create a standard LUT, invert the stack (Edit/Invert)\n"
    	+"and invert the LUT (Image/Lookup Tables/Invert LUT)."; 

    /** Image to hold z-projection. */
    private ImagePlus projImage = null; 

    /** Image stack to project. */
    private ImagePlus imp = null; 

    /** Projection starts from this slice. */
    private int startSlice = 1;
    /** Projection ends at this slice. */
    private int stopSlice = 1;
    /** Project all time points? */
    private boolean allTimeFrames = true;
    
    private String color = "";
    private boolean isHyperstack;
    private boolean simpleComposite;
    private int increment = 1;
    private int sliceCount;

    public ZProjector() {
    }

    /** Construction of ZProjector with image to be projected. */
    public ZProjector(ImagePlus imp) {
		setImage(imp); 
    }
    
    /** Performs projection on the entire stack using the specified method and returns
    	 the result, where 'method' is "avg", "min", "max", "sum", "sd" or "median".
    	 Add " all" to 'method' to project all hyperstack time points. */
    public ImagePlus run(ImagePlus imp, String method) {
    	return run(imp, method, 1, imp.getStackSize());
    }

	/** Performs projection using the specified method and stack range, and returns
		 the result, where 'method' is "avg", "min", "max", "sum", "sd" or "median".
		Add " all" to 'method' to project all hyperstack time points. <br>
		Example: http://imagej.nih.gov/ij/macros/js/ProjectionDemo.js
	*/
	 public ImagePlus run(ImagePlus imp, String method, int startSlice, int stopSlice) {
    	ZProjector zp = new ZProjector(imp);
    	zp.setStartSlice(startSlice);
    	zp.setStopSlice(stopSlice);
    	zp.isHyperstack = imp.isHyperStack();
    	if (zp.isHyperstack && startSlice==1 && stopSlice==imp.getStackSize())
    		zp.setDefaultBounds();
    	if (method==null) return null;
    	method = method.toLowerCase();
    	int m = -1;
    	if (method.startsWith("av")) m = AVG_METHOD;
    	else if (method.startsWith("max")) m = MAX_METHOD;
    	else if (method.startsWith("min")) m = MIN_METHOD;
    	else if (method.startsWith("sum")) m = SUM_METHOD;
    	else if (method.startsWith("sd")) m = SD_METHOD;
    	else if (method.startsWith("median")) m = MEDIAN_METHOD;
    	if (m<0)
    		throw new IllegalArgumentException("Invalid projection method: "+method);
    	zp.allTimeFrames = method.contains("all");
    	zp.setMethod(m);
    	zp.doProjection(true);
    	return zp.getProjection();
    }

    /** Explicitly set image to be projected. This is useful if
	ZProjection_ object is to be used not as a plugin but as a
	stand alone processing object.  */
    public void setImage(ImagePlus imp) {
    	this.imp = imp; 
		startSlice = 1; 
		stopSlice = imp.getStackSize(); 
    }

    public void setStartSlice(int slice) {
		if (imp==null || slice < 1 || slice > imp.getStackSize())
	    	return; 
		startSlice = slice; 
    }

    public void setStopSlice(int slice) {
		if (imp==null || slice < 1 || slice > imp.getStackSize())
	    	return; 
		stopSlice = slice; 
    }

	public void setMethod(int projMethod){
		method = projMethod;
	}
    
    /** Retrieve results of most recent projection operation.*/
    public ImagePlus getProjection() {
		return projImage; 
    }

    public void run(String arg) {
		imp = IJ.getImage();
		if (imp==null) {
	    	IJ.noImage(); 
	    	return; 
		}

		//  Make sure input image is a stack.
		if(imp.getStackSize()==1) {
	    	IJ.error("Z Project", "Stack required"); 
	    	return; 
		}
	
		//  Check for inverting LUT.
		if (imp.getProcessor().isInvertedLut()) {
	    	if (!IJ.showMessageWithCancel("ZProjection", lutMessage))
	    		return; 
		}

		setDefaultBounds();
			
		// Build control dialog
		GenericDialog gd = buildControlDialog(startSlice,stopSlice);
		gd.showDialog(); 
		if (gd.wasCanceled()) return; 

		if (!imp.lock()) return;   // exit if in use
		long tstart = System.currentTimeMillis();
		gd.setSmartRecording(true);
		int startSlice2 = startSlice;
		int stopSlice2 = stopSlice;
		setStartSlice((int)gd.getNextNumber());
		setStopSlice((int)gd.getNextNumber()); 
		boolean rangeChanged = startSlice!=startSlice2 || stopSlice!=stopSlice2;
		startSlice2 = startSlice;
		stopSlice2 = stopSlice;
		gd.setSmartRecording(false);
		method = gd.getNextChoiceIndex();
		Prefs.set(METHOD_KEY, method);
		if (isHyperstack)
			allTimeFrames = imp.getNFrames()>1&&imp.getNSlices()>1?gd.getNextBoolean():false;
		doProjection(true); 

		if (arg.equals("") && projImage!=null) {
			long tstop = System.currentTimeMillis();
			if (simpleComposite) IJ.run(projImage, "Grays", "");
			projImage.show("ZProjector: " +IJ.d2s((tstop-tstart)/1000.0,2)+" seconds");
		}

		imp.unlock();
		IJ.register(ZProjector.class);
		if (Recorder.scriptMode()) {
			String m = getMethodAsString();
			if (isHyperstack && allTimeFrames)
				m = m + " all";
			String range = "";
			if (rangeChanged)
				range = ","+startSlice2+","+stopSlice2;
			Recorder.recordCall("imp = ZProjector.run(imp,\""+m+"\""+range+");");
		}
		
    }
    
    private String getMethodAsString() {
    	switch (method) {
     		case AVG_METHOD: return "avg";
    		case MAX_METHOD: return "max";
    		case MIN_METHOD: return "min";
    		case SUM_METHOD: return "sum";
    		case SD_METHOD: return "sd";
    		case MEDIAN_METHOD: return "median";
    		default: return "avg";
    	}
    }
    
    private void setDefaultBounds() {
		int stackSize = imp.getStackSize();
    	int channels = imp.getNChannels();
		int frames = imp.getNFrames();
		int slices = imp.getNSlices();
		isHyperstack = imp.isHyperStack()||( ij.macro.Interpreter.isBatchMode()&&((frames>1&&frames<stackSize)||(slices>1&&slices<stackSize)));
		simpleComposite = channels==stackSize;
		if (simpleComposite)
			isHyperstack = false;
		startSlice = 1; 
		if (isHyperstack) {
			int nSlices = imp.getNSlices();
			if (nSlices>1)
				stopSlice = nSlices;
			else
				stopSlice = imp.getNFrames();
		} else
			stopSlice  = stackSize;
    }
    
    public void doRGBProjection() {
		doRGBProjection(imp.getStack());
    }

	//Added by Marcel Boeglin 2013.09.23
	public void doRGBProjection(boolean handleOverlay) {
		doRGBProjection(imp.getStack());
		Overlay overlay = imp.getOverlay();
		if (handleOverlay && overlay!=null)
			projImage.setOverlay(projectRGBHyperStackRois(overlay));
	}

    private void doRGBProjection(ImageStack stack) {
        ImageStack[] channels = ChannelSplitter.splitRGB(stack, true);
        ImagePlus red = new ImagePlus("Red", channels[0]);
        ImagePlus green = new ImagePlus("Green", channels[1]);
        ImagePlus blue = new ImagePlus("Blue", channels[2]);
        imp.unlock();
        ImagePlus saveImp = imp;
        imp = red;
		color = "(red)"; doProjection();
		ImagePlus red2 = projImage;
        imp = green;
		color = "(green)"; doProjection();
		ImagePlus green2 = projImage;
        imp = blue;
		color = "(blue)"; doProjection();
		ImagePlus blue2 = projImage;
        int w = red2.getWidth(), h = red2.getHeight(), d = red2.getStackSize();
        if (method==SD_METHOD) {
        	ImageProcessor r = red2.getProcessor();
        	ImageProcessor g = green2.getProcessor();
        	ImageProcessor b = blue2.getProcessor();
        	double max = 0;
        	double rmax = r.getStats().max; if (rmax>max) max=rmax;
        	double gmax = g.getStats().max; if (gmax>max) max=gmax;
        	double bmax = b.getStats().max; if (bmax>max) max=bmax;
        	double scale = 255/max;
        	r.multiply(scale); g.multiply(scale); b.multiply(scale);
        	red2.setProcessor(r.convertToByte(false));
        	green2.setProcessor(g.convertToByte(false));
        	blue2.setProcessor(b.convertToByte(false));
        }
        RGBStackMerge merge = new RGBStackMerge();
        ImageStack stack2 = merge.mergeStacks(w, h, d, red2.getStack(), green2.getStack(), blue2.getStack(), true);
        imp = saveImp;
        projImage = new ImagePlus(makeTitle(), stack2);
    }

    /** Builds dialog to query users for projection parameters.
	@param start starting slice to display
	@param stop last slice */
    protected GenericDialog buildControlDialog(int start, int stop) {
		GenericDialog gd = new GenericDialog("ZProjection"); 
		gd.addNumericField("Start slice:",startSlice,0/*digits*/); 
		gd.addNumericField("Stop slice:",stopSlice,0/*digits*/);
		gd.addChoice("Projection type", METHODS, METHODS[method]); 
		if (isHyperstack && imp.getNFrames()>1&& imp.getNSlices()>1)
			gd.addCheckbox("All time frames", allTimeFrames); 
		return gd; 
    }

    /** Performs actual projection using specified method. */
    public void doProjection() {
		if (imp==null)
			return;
		if (imp.getBitDepth()==24) {
			doRGBProjection();
			return;
		}
		sliceCount = 0;
		if (method<AVG_METHOD || method>MEDIAN_METHOD)
			method = AVG_METHOD;
    	for (int slice=startSlice; slice<=stopSlice; slice+=increment)
    		sliceCount++;
		if (method==MEDIAN_METHOD) {
			projImage = doMedianProjection();
			return;
		} 
		
		// Create new float processor for projected pixels.
		FloatProcessor fp = new FloatProcessor(imp.getWidth(),imp.getHeight()); 
		ImageStack stack = imp.getStack();
		RayFunction rayFunc = getRayFunction(method, fp);
		if (IJ.debugMode==true) {
	    	IJ.log("\nProjecting stack from: "+startSlice
		     	+" to: "+stopSlice); 
		}

		// Determine type of input image. Explicit determination of
		// processor type is required for subsequent pixel
		// manipulation.  This approach is more efficient than the
		// more general use of ImageProcessor's getPixelValue and
		// putPixel methods.
		int ptype; 
		if (stack.getProcessor(1) instanceof ByteProcessor) ptype = BYTE_TYPE; 
		else if (stack.getProcessor(1) instanceof ShortProcessor) ptype = SHORT_TYPE; 
		else if (stack.getProcessor(1) instanceof FloatProcessor) ptype = FLOAT_TYPE; 
		else {
	    	IJ.error("Z Project", "Non-RGB stack required"); 
	    	return; 
		}

		// Do the projection
		int sliceCount = 0;
		for (int n=startSlice; n<=stopSlice; n+=increment) {
			if (!isHyperstack) {
	    		IJ.showStatus("ZProjection " + color +": " + n + "/" + stopSlice);
	    		IJ.showProgress(n-startSlice, stopSlice-startSlice);
	    	}
	    	projectSlice(stack.getPixels(n), rayFunc, ptype);
	    	sliceCount++;
		}

		// Finish up projection.
		if (method==SUM_METHOD) {
			if (imp.getCalibration().isSigned16Bit())
				fp.subtract(sliceCount*32768.0);
			fp.resetMinAndMax();
			projImage = new ImagePlus(makeTitle(), fp);
		} else if (method==SD_METHOD) {
			rayFunc.postProcess();
			fp.resetMinAndMax();
			projImage = new ImagePlus(makeTitle(), fp); 
		} else {
			rayFunc.postProcess(); 
			projImage = makeOutputImage(imp, fp, ptype);
		}

		if(projImage==null)
	    	IJ.error("Z Project", "Error computing projection.");
    }

	//Added by Marcel Boeglin 2013.09.23
	/** Performs actual projection using specified method.
		If handleOverlay, adds stack overlay 
		elements from startSlice to stopSlice to projection. */
	public void doProjection(boolean handleOverlay) {
		if (isHyperstack)
			doHyperStackProjection(allTimeFrames);
		else if (imp.getType()==ImagePlus.COLOR_RGB)
			doRGBProjection(handleOverlay);
		else {
			doProjection();
			Overlay overlay = imp.getOverlay();
			if (handleOverlay && overlay!=null)
				projImage.setOverlay(projectStackRois(overlay));
		}
		if (projImage!=null)
			projImage.setCalibration(imp.getCalibration());
	}
	
	//Added by Marcel Boeglin 2013.09.23
	private Overlay projectStackRois(Overlay overlay) {
		if (overlay==null) return null;
		Overlay overlay2 = overlay.create();
		Roi roi;
		int s;
		for (Roi r : overlay.toArray()) {
			s = r.getPosition();
			roi = (Roi)r.clone();
			if (s>=startSlice && s<=stopSlice || s==0) {
				roi.setPosition(s);
				overlay2.add(roi);
			}
		}
		return overlay2;
	}

	public void doHyperStackProjection(boolean allTimeFrames) {
		int start = startSlice;
		int stop = stopSlice;
		int firstFrame = 1;
		int lastFrame = imp.getNFrames();
		if (!allTimeFrames)
			firstFrame = lastFrame = imp.getFrame();
		ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		if (slices==1) {
			slices = imp.getNFrames();
			firstFrame = lastFrame = 1;
		}
		int frames = lastFrame-firstFrame+1;
		increment = channels;
		boolean rgb = imp.getBitDepth()==24;
		for (int frame=firstFrame; frame<=lastFrame; frame++) {
			IJ.showStatus(""+ (frame-firstFrame) + "/" + (lastFrame-firstFrame));
			IJ.showProgress(frame-firstFrame, lastFrame-firstFrame);
			for (int channel=1; channel<=channels; channel++) {
				startSlice = (frame-1)*channels*slices + (start-1)*channels + channel;
				stopSlice = (frame-1)*channels*slices + (stop-1)*channels + channel;
				if (rgb)
					doHSRGBProjection(imp);
				else
					doProjection();
				stack.addSlice(null, projImage.getProcessor());
			}
		}
        projImage = new ImagePlus(makeTitle(), stack);
        projImage.setDimensions(channels, 1, frames);
        if (channels>1) {
           	projImage = new CompositeImage(projImage, 0);
        	((CompositeImage)projImage).copyLuts(imp);
      		if (method==SUM_METHOD || method==SD_METHOD)
        			((CompositeImage)projImage).resetDisplayRanges();
        }
        if (frames>1)
        	projImage.setOpenAsHyperStack(true);
		Overlay overlay = imp.getOverlay();
		if (overlay!=null) {
			startSlice = start;
			stopSlice = stop;
			if (imp.getType()==ImagePlus.COLOR_RGB)
				projImage.setOverlay(projectRGBHyperStackRois(overlay));
			else
				projImage.setOverlay(projectHyperStackRois(overlay));
		}
        IJ.showProgress(1, 1);
	}
	
	//Added by Marcel Boeglin 2013.09.22
    private Overlay projectRGBHyperStackRois(Overlay overlay) {
        if (overlay==null) return null;
		int frames = projImage.getNFrames();
		int t1 = imp.getFrame();
        Overlay overlay2 = overlay.create();
        Roi roi;
        int c, z, t;
		for (Roi r : overlay.toArray()) {
			c = r.getCPosition();
			z = r.hasHyperStackPosition()?r.getZPosition():0;
			t = r.getTPosition();
			roi = (Roi)r.clone();
			if (z>=startSlice && z<=stopSlice || z==0 || c==0 || t==0) {
				if (frames==1 && t!=t1 && t!=0)//current time frame
					continue;
				roi.setPosition(t);
				overlay2.add(roi);
			}
		}
		return overlay2;
    }
    
	//Added by Marcel Boeglin 2013.09.22
	private Overlay projectHyperStackRois(Overlay overlay) {
		if (overlay==null) return null;
		int t1 = imp.getFrame();
		int channels = projImage.getNChannels();
		int slices = 1;
		int frames = projImage.getNFrames();
		Overlay overlay2 = overlay.create();
		Roi roi;
		int c, z, t;
		int size = channels * slices * frames;
		for (Roi r : overlay.toArray()) {
			c = r.getCPosition();
			z = r.getZPosition();
			t = r.getTPosition();
			roi = (Roi)r.clone();
			if (size==channels) {//current time frame
				if (z>=startSlice && z<=stopSlice && t==t1 || c==0) {
					roi.setPosition(c);
					overlay2.add(roi);
				}
			}
			else if (size==frames*channels) {//all time frames
				if (z>=startSlice && z<=stopSlice)
					roi.setPosition(c, 1, t);
				else if (z==0)
					roi.setPosition(c, 0, t);
				else continue;
				overlay2.add(roi);
			}
		}
		return overlay2;
	}

	private void doHSRGBProjection(ImagePlus rgbImp) {
		ImageStack stack = rgbImp.getStack();
		ImageStack stack2 = new ImageStack(stack.getWidth(), stack.getHeight());
		for (int i=startSlice; i<=stopSlice; i++)
			stack2.addSlice(null, stack.getProcessor(i));
		startSlice = 1;
		stopSlice = stack2.getSize();
		doRGBProjection(stack2);
	}

 	private RayFunction getRayFunction(int method, FloatProcessor fp) {
 		switch (method) {
 			case AVG_METHOD: case SUM_METHOD:
	    		return new AverageIntensity(fp, sliceCount); 
			case MAX_METHOD:
	    		return new MaxIntensity(fp);
	    	case MIN_METHOD:
	    		return new MinIntensity(fp); 
			case SD_METHOD:
	    		return new StandardDeviation(fp, sliceCount); 
			default:
	    		IJ.error("Z Project", "Unknown method.");
	    		return null;
	    }
	}

    /** Generate output image whose type is same as input image. */
    private ImagePlus makeOutputImage(ImagePlus imp, FloatProcessor fp, int ptype) {
		int width = imp.getWidth(); 
		int height = imp.getHeight(); 
		float[] pixels = (float[])fp.getPixels(); 
		ImageProcessor oip=null; 

		// Create output image consistent w/ type of input image.
		int size = pixels.length;
		switch (ptype) {
			case BYTE_TYPE:
				oip = imp.getProcessor().createProcessor(width,height);
				byte[] pixels8 = (byte[])oip.getPixels(); 
				for(int i=0; i<size; i++)
					pixels8[i] = (byte)pixels[i];
				break;
			case SHORT_TYPE:
				oip = imp.getProcessor().createProcessor(width,height);
				short[] pixels16 = (short[])oip.getPixels(); 
				for(int i=0; i<size; i++)
					pixels16[i] = (short)pixels[i];
				break;
			case FLOAT_TYPE:
				oip = new FloatProcessor(width, height, pixels, null);
				break;
		}
	
		// Adjust for display.
	    // Calling this on non-ByteProcessors ensures image
	    // processor is set up to correctly display image.
	    oip.resetMinAndMax(); 

		// Create new image plus object. Don't use
		// ImagePlus.createImagePlus here because there may be
		// attributes of input image that are not appropriate for
		// projection.
		return new ImagePlus(makeTitle(), oip); 
    }

    /** Handles mechanics of projection by selecting appropriate pixel
	array type. We do this rather than using more general
	ImageProcessor getPixelValue() and putPixel() methods because
	direct manipulation of pixel arrays is much more efficient.  */
	private void projectSlice(Object pixelArray, RayFunction rayFunc, int ptype) {
		switch(ptype) {
			case BYTE_TYPE:
	    		rayFunc.projectSlice((byte[])pixelArray); 
	    		break; 
			case SHORT_TYPE:
	    		rayFunc.projectSlice((short[])pixelArray); 
	    		break; 
			case FLOAT_TYPE:
	    		rayFunc.projectSlice((float[])pixelArray); 
	    		break; 
		}
    }
    
    String makeTitle() {
    	String prefix = "AVG_";
 		switch (method) {
 			case SUM_METHOD: prefix = "SUM_"; break;
			case MAX_METHOD: prefix = "MAX_"; break;
	    	case MIN_METHOD: prefix = "MIN_"; break;
			case SD_METHOD:  prefix = "STD_"; break;
			case MEDIAN_METHOD:  prefix = "MED_"; break;
	    }
    	return WindowManager.makeUniqueName(prefix+imp.getTitle());
    }

	ImagePlus doMedianProjection() {
		IJ.showStatus("Calculating median...");
		ImageStack stack = imp.getStack();
		ImageProcessor[] slices = new ImageProcessor[sliceCount];
		int index = 0;
		for (int slice=startSlice; slice<=stopSlice; slice+=increment)
			slices[index++] = stack.getProcessor(slice);
		ImageProcessor ip2 = slices[0].duplicate();
		ip2 = ip2.convertToFloat();
		float[] values = new float[sliceCount];
		int width = ip2.getWidth();
		int height = ip2.getHeight();
		int inc = Math.max(height/30, 1);
		for (int y=0; y<height; y++) {
			if (y%inc==0) IJ.showProgress(y, height-1);
			for (int x=0; x<width; x++) {
				for (int i=0; i<sliceCount; i++)
				values[i] = slices[i].getPixelValue(x, y);
				ip2.putPixelValue(x, y, median(values));
			}
		}
		if (imp.getBitDepth()==8)
			ip2 = ip2.convertToByte(false);
		IJ.showProgress(1, 1);
		return new ImagePlus(makeTitle(), ip2);
	}

	float median(float[] a) {
		Arrays.sort(a);
		int middle = a.length/2;
		if ((a.length&1)==0) //even
			return (a[middle-1] + a[middle])/2f;
		else
			return a[middle];
	}

     /** Abstract class that specifies structure of ray
	function. Preprocessing should be done in derived class
	constructors.
	*/
    abstract class RayFunction {
		/** Do actual slice projection for specific data types. */
		public abstract void projectSlice(byte[] pixels);
		public abstract void projectSlice(short[] pixels);
		public abstract void projectSlice(float[] pixels);
		
		/** Perform any necessary post processing operations, e.g.
	    	averging values. */
		public void postProcess() {}

    } // end RayFunction


    /** Compute average intensity projection. */
    class AverageIntensity extends RayFunction {
     	private float[] fpixels;
 		private int num, len; 

		/** Constructor requires number of slices to be
	    	projected. This is used to determine average at each
	    	pixel. */
		public AverageIntensity(FloatProcessor fp, int num) {
			fpixels = (float[])fp.getPixels();
			len = fpixels.length;
	    	this.num = num;
		}

		public void projectSlice(byte[] pixels) {
	    	for(int i=0; i<len; i++)
				fpixels[i] += (pixels[i]&0xff); 
		}

		public void projectSlice(short[] pixels) {
	    	for(int i=0; i<len; i++)
				fpixels[i] += pixels[i]&0xffff;
		}

		public void projectSlice(float[] pixels) {
	    	for(int i=0; i<len; i++)
				fpixels[i] += pixels[i]; 
		}

		public void postProcess() {
			float fnum = num;
	    	for(int i=0; i<len; i++)
				fpixels[i] /= fnum;
		}

    } // end AverageIntensity


     /** Compute max intensity projection. */
    class MaxIntensity extends RayFunction {
    	private float[] fpixels;
 		private int len; 

		/** Simple constructor since no preprocessing is necessary. */
		public MaxIntensity(FloatProcessor fp) {
			fpixels = (float[])fp.getPixels();
			len = fpixels.length;
			for (int i=0; i<len; i++)
				fpixels[i] = -Float.MAX_VALUE;
		}

		public void projectSlice(byte[] pixels) {
	    	for(int i=0; i<len; i++) {
				if((pixels[i]&0xff)>fpixels[i])
		    		fpixels[i] = (pixels[i]&0xff); 
	    	}
		}

		public void projectSlice(short[] pixels) {
	    	for(int i=0; i<len; i++) {
				if((pixels[i]&0xffff)>fpixels[i])
		    		fpixels[i] = pixels[i]&0xffff;
	    	}
		}

		public void projectSlice(float[] pixels) {
	    	for(int i=0; i<len; i++) {
				if(pixels[i]>fpixels[i])
		    		fpixels[i] = pixels[i]; 
	    	}
		}
		
    } // end MaxIntensity

     /** Compute min intensity projection. */
    class MinIntensity extends RayFunction {
    	private float[] fpixels;
 		private int len; 

		/** Simple constructor since no preprocessing is necessary. */
		public MinIntensity(FloatProcessor fp) {
			fpixels = (float[])fp.getPixels();
			len = fpixels.length;
			for (int i=0; i<len; i++)
				fpixels[i] = Float.MAX_VALUE;
		}

		public void projectSlice(byte[] pixels) {
	    	for(int i=0; i<len; i++) {
				if((pixels[i]&0xff)<fpixels[i])
		    		fpixels[i] = (pixels[i]&0xff); 
	    	}
		}

		public void projectSlice(short[] pixels) {
	    	for(int i=0; i<len; i++) {
				if((pixels[i]&0xffff)<fpixels[i])
		    		fpixels[i] = pixels[i]&0xffff;
	    	}
		}

		public void projectSlice(float[] pixels) {
	    	for(int i=0; i<len; i++) {
				if(pixels[i]<fpixels[i])
		    		fpixels[i] = pixels[i]; 
	    	}
		}
		
    } // end MaxIntensity


    /** Compute standard deviation projection. */
    class StandardDeviation extends RayFunction {
    	private float[] result;
    	private double[] sum, sum2;
		private int num,len; 

		public StandardDeviation(FloatProcessor fp, int num) {
			result = (float[])fp.getPixels();
			len = result.length;
		    this.num = num;
			sum = new double[len];
			sum2 = new double[len];
		}
	
		public void projectSlice(byte[] pixels) {
			int v;
		    for(int i=0; i<len; i++) {
		    	v = pixels[i]&0xff;
				sum[i] += v;
				sum2[i] += v*v;
			} 
		}
	
		public void projectSlice(short[] pixels) {
			double v;
		    for(int i=0; i<len; i++) {
		    	v = pixels[i]&0xffff;
				sum[i] += v;
				sum2[i] += v*v;
			} 
		}
	
		public void projectSlice(float[] pixels) {
			double v;
		    for(int i=0; i<len; i++) {
		    	v = pixels[i];
				sum[i] += v;
				sum2[i] += v*v;
			} 
		}
	
		public void postProcess() {
			double stdDev;
			double n = num;
		    for(int i=0; i<len; i++) {
				if (num>1) {
					stdDev = (n*sum2[i]-sum[i]*sum[i])/n;
					if (stdDev>0.0)
						result[i] = (float)Math.sqrt(stdDev/(n-1.0));
					else
						result[i] = 0f;
				} else
					result[i] = 0f;
			}
		}

    } // end StandardDeviation

}  // end ZProjection
	
}