/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

import ij.IJ;
import java.util.ArrayList;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 *
 * @author Dawen Cai
 */
public class ntNeuronNode extends DefaultMutableTreeNode {

    private ArrayList<String[]> tracingResult;

    ntNeuronNode(String name, ArrayList<String[]> tracingResult) {
        super(name);
        this.tracingResult = tracingResult;
    }
/*
    public ntNeuronNode duplicate(){
        return new ntNeuronNode(this.name, getTracingResult());
    }
    */
    public ArrayList<String[]> getTracingResult() {
        return tracingResult;
    }
    
    public String getType(){
        if (this.isTrunckNode()) {
            return "Neuron";
        } else {
            String nodeType = this.getTracingResult().get(0)[0];
            if (nodeType.contains(":")){
                nodeType = nodeType.split(":")[0];
            } else if (nodeType.contains("/")){
                nodeType = nodeType.split("/")[0];
            } else if (nodeType.contains("*")){
                nodeType = nodeType.split("\\*")[0];
            }
            return nodeType; 
        }
    }
    
    public boolean isComplete (){
        if (this.isTrunckNode()) {
            return true;
        } else {
            return !this.getTracingResult().get(0)[0].endsWith("*");
        } 
    }

    public void toogleComplete() {
        if (!this.isTrunckNode()) {
            String label = this.getTracingResult().get(0)[0];
            if (label.endsWith("*")) {
                this.getTracingResult().get(0)[0] = label.substring(0, label.length()-1);
            } else {
                this.getTracingResult().get(0)[0] = label + "*";
            }
        }
    }

    public ArrayList<String[]> getInvertTracingResult(){
        ArrayList<String[]> originalResult = this.getTracingResult();
        ArrayList<String[]> invertResult = new ArrayList<String[]>();
        for (int i = originalResult.size()-1; i>=0; i--){
            invertResult.add(originalResult.get(i));
        }
        return invertResult;
    }
    
    public ntNeuronNode duplicate() {
        ArrayList<String[]> cloneTracingResult = new ArrayList<String[]>();
        for (String[] result : tracingResult) {
            String[] copyResult = new String[result.length];
            System.arraycopy(result, 0, copyResult, 0, result.length);
            cloneTracingResult.add(copyResult);
        }
        return new ntNeuronNode(this.toString(), cloneTracingResult);
    }
            
    public void setTracingResult(ArrayList<String[]> newResult) {
        this.tracingResult = new ArrayList<String[]>();
        if (!newResult.isEmpty()) {
            for (int i = 0; i < newResult.size(); i++) {
                tracingResult.add(i, newResult.get(i));
            }
        }
    }
    
    public void invertTracingResult() {
        ArrayList<String[]> cloneTracingResult = new ArrayList<String[]>();
        for (int i = this.tracingResult.size()-1; i >= 0; i--) {
            cloneTracingResult.add(this.tracingResult.get(i));
        }
        this.tracingResult = cloneTracingResult;
    }
    
    public void setName(String name) {
        this.setUserObject(name);
    }

    public String getNeuronNumber(){
        String neuronName = this.toString();
        if (neuronName.contains("/")){
            neuronName = neuronName.substring(0, neuronName.indexOf("/"));
        }
        if (neuronName.contains("-")){
            neuronName = neuronName.substring(0, neuronName.indexOf("-"));
        }
        if (neuronName.contains(":")){
            neuronName = neuronName.substring(0, neuronName.indexOf(":"));
        }
        return neuronName;
    }

    public void setSynapse(int inTreePosition, boolean isSynapse) {
        String[] tempPt = tracingResult.get(inTreePosition);
        if (isSynapse) {
            tempPt[5] = "1";
        } else {
            tempPt[5] = "0";
        }
        tracingResult.set(inTreePosition, tempPt);
    }
    
    public void setSpine(int inTreePosition, String spineLabel) {
        String[] tempPt = tracingResult.get(inTreePosition);
        if (!spineLabel.equals("0")) {
            tempPt[0] = this.getType()+":Spine#"+spineLabel;
            tempPt[5] = "1";
        } else {
            tempPt[0] = this.getType();
            tempPt[5] = "0";
        }
        tracingResult.set(inTreePosition, tempPt);
    }

    public boolean isSynapseAt(int inTreePosition) {
        return (tracingResult.get(inTreePosition)[5]).equals("1");
    }
    
    public String getConnectedNeuronName(int inTreePosition){
        return tracingResult.get(inTreePosition)[6];
    }
    
    public void setConnectionTo(int inTreePosition, String name){
        String[] tempPt = tracingResult.get(inTreePosition);
        tempPt[6] = name;
        tracingResult.set(inTreePosition, tempPt);
    }
    
    public void removeConnectionAt(int inTreePosition){
        String[] tempPt = tracingResult.get(inTreePosition);
        tempPt[6] = "0";
        tracingResult.set(inTreePosition, tempPt);
    }

    public boolean isSomaSliceNode() { // which has the format such as "2-3" or "2-1-1"
        String name = this.toString();
        if (name.contains("/")) {
            return false;
        }
        return name.contains(":");
    }
    
    public boolean isBranchNode() { // which has the format such as "2-3" or "2-1-1"
        String name = this.toString();
        if (name.contains("/")) {
            return false;
        }
        return name.contains("-");
    }
    
    public boolean isPrimaryBranchNode() { // which has the format such as "2-3"
        String name = this.toString();
        if (name.contains("/")) {
            return false;
        }
        if (name.contains("-")) {
            String[] names = name.split("-");
            return names.length == 2;
        } else {
            return false;
        }
    }
    
    public boolean isTerminalBranchNode() {
        return ((this.isLeaf() && !this.isTrunckNode())||this.isPrimaryBranchNode());
    }
    
    public boolean isSubBranchNode() { // which has the format such as "2-3-1-x"
        String name = this.toString();
        if (name.contains("/")) {
            return false;
        }
        if (name.contains("-")) {
            String[] names = name.split("-");
            return names.length > 2;
        } else {
            return false;
        }
    }

    public boolean isTrunckNode() { // which has the format such as "2/xxxxx" or "2"
        String name = this.toString();
        if (name.contains("/")) {
            return true;
        } else {
            return !(name.contains("-") || name.contains(":"));
        }
    }
    
    public String getNextConnectionNumber() {
        String nextNumber = "";
        ArrayList<String[]> nodeTracing = this.getTracingResult();
        ArrayList<Integer> existNodeNumbers = new ArrayList<Integer>();

        for (String[] nodePoint : nodeTracing) {
            if (!nodePoint[6].equals("0")) {
                String[] names = nodePoint[6].split("#");
                existNodeNumbers.add(Integer.parseInt(names[0]));
            }
        }

        boolean isNextNumber = true;
        for (int i = 1; i <= nodeTracing.size(); i++) {
            for (int existNodeNumber : existNodeNumbers) {
                if (i == existNodeNumber) {
                    isNextNumber = false;
                    break;
                }
            }
            if (isNextNumber) {
                nextNumber = nextNumber + i;
                break;
            }
            isNextNumber = true;
        }
        return nextNumber;
    }    

}
