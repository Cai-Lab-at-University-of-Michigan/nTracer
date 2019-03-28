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


import java.util.ArrayList;
import java.util.Enumeration;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 *
 * @author Dawen Cai <dwcai@umich.edu>
 */
public class ntTreeTableExpansionState {
  protected ArrayList<TreePath> expandedPaths = new ArrayList<TreePath>();
    protected JTree aTree;
    
  public ntTreeTableExpansionState(JTree aTree) {
    this.aTree = aTree;
  }

  public void store() {
    expandedPaths.clear();
    Enumeration expandedDescendants = aTree.getExpandedDescendants(
            new TreePath(aTree.getModel().getRoot()));
    if (expandedDescendants != null) {
      while (expandedDescendants.hasMoreElements()) {
        Object nex = expandedDescendants.nextElement();
        TreePath np = (TreePath) nex;
        if (!(np.getLastPathComponent() == aTree.getModel().getRoot())) {
          expandedPaths.add(np);
        }
      }
    }
  }

  public void restore() {
    for (int r=0; r<=aTree.getRowCount(); r++){
        aTree.collapseRow(r);
    }
    for (TreePath path : expandedPaths) {
      aTree.expandPath(path);
    }
  }
}
