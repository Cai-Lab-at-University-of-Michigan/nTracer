/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

/*
 * @author santhosh kumar T - santhosh@in.fiorano.com
 * http://www.javalobby.org/java/forums/t19857.html
 */



import java.util.StringTokenizer;
import java.util.Enumeration;
import javax.swing.tree.*;
import javax.swing.JTree;

/**
 *
 * @author Dawen
 */
public class ntTreeUtil {

    // is path1 descendant of path2
    public boolean isDescendant(TreePath path1, TreePath path2){
        int count1 = path1.getPathCount();
        int count2 = path2.getPathCount();
        if(count1<=count2)
            return false;
        while(count1!=count2){
            path1 = path1.getParentPath();
            count1--;
        }
        return path1.equals(path2);
    }

    public String getExpansionState(JTree tree, int row){
        TreePath rowPath = tree.getPathForRow(row);
        StringBuilder buf = new StringBuilder();
        int rowCount = tree.getRowCount();
        for(int i=row; i<rowCount; i++){
            TreePath path = tree.getPathForRow(i);
            if(i==row || isDescendant(path, rowPath)){
                if(tree.isExpanded(path))
                    buf.append(',');
                    buf.append(i-row);
            }else
                break;
        }
        return buf.toString();
    }

    public void restoreExpanstionState(JTree tree, int row, String expansionState){
        StringTokenizer stok = new StringTokenizer(expansionState, ",");
        while(stok.hasMoreTokens()){
            int token = row + Integer.parseInt(stok.nextToken());
            tree.expandRow(token);
        }
    }

    // the expansion state can also be saved as tree paths

    public Enumeration saveExpansionState(JTree tree) {

        return tree.getExpandedDescendants(new TreePath(tree.getModel().getRoot()));

    }


    /**

     * Restore the expansion state of a JTree.

     *

     * @param tree

     * @param enumeration an Enumeration of expansion state. You can get it using {@link #saveExpansionState(javax.swing.JTree)}.

     */

    public void loadExpansionState(JTree tree, Enumeration enumeration) {

        if (enumeration != null) {

            while (enumeration.hasMoreElements()) {

                TreePath treePath = (TreePath) enumeration.nextElement();

                tree.expandPath(treePath);

            }

        }

    }
}
