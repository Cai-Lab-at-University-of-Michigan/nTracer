/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import java.util.ArrayList;

/**
 *
 * @author Dawen
 */
public class ntDataHandler {

    public ntDataHandler() {
    }

    public Object [][] getPointTableData (ArrayList<String[]> traceResult){
        // because Java passes Object by reference not by value,
        // Object needs to be assigned and returned seperately in condition loops
        Object [][] pointTableData = new Object [][] {};
        if (!traceResult.isEmpty()) {
            int rowNumber = traceResult.size();
            //IJ.log("total traced points = "+rowNumber);
            int columnNumber = 7;
            pointTableData = new Object [rowNumber][columnNumber];
            for (int r = 0; r < rowNumber; r++) {
                String [] tempPoint = (String [])traceResult.get(r);
                //IJ.log("data point length = "+tempPoint.length);
                pointTableData[r][0] = tempPoint[0];
                for (int s = 1; s <= 4; s++){
                    //IJ.log("data "+tempPoint[s]);
                    pointTableData[r][s] = (Float)(Float.parseFloat(tempPoint[s]));
                }
                pointTableData[r][5] = tempPoint[5].equals("1");
                pointTableData[r][6] = tempPoint[6];
            }            
        }
        return pointTableData;
    }
    
    public ntNeuronNode replicateNodeAndChild(ntNeuronNode node){
        ntNeuronNode newNode = node.duplicate();
        replicateWhoelChildTree(newNode, node);
        return newNode;
    }
    public void replicateWhoelChildTree(ntNeuronNode newNode, ntNeuronNode node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            ntNeuronNode childNode = (ntNeuronNode) node.getChildAt(i);
            ntNeuronNode newChildNode = childNode.duplicate();
            newNode.add(newChildNode);
            replicateWhoelChildTree(newChildNode, childNode);
        }
    }
      // variables

}
