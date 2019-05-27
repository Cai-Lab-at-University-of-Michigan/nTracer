/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nTracer;

/**
 *
 * @author loganaw
 */
public class coord3D {
    private int x, y, z;
    
    public coord3D(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public coord3D( int[] pt ) {
        this.x = pt[0];
        this.y = pt[1];
        this.z = pt[2];
    }
    
    public int getX() {
        return this.x;
    }
    
    public int getY() {
        return this.y;
    }
    
    public int getZ() {
        return this.z;
    }
    
    public int[] getCoords() {
        return new int[]{ x, y, z};
    }

    @Override
    public int hashCode() {
        return x^y^z;
    }

    @Override
    public boolean equals(Object other) {
        if ( !(other instanceof coord3D) ) {
            return false;
        }
        
        coord3D o = (coord3D) other;
        
        return this.x == o.getX() && this.y == o.getY() && this.z == o.getZ();
    }

}
