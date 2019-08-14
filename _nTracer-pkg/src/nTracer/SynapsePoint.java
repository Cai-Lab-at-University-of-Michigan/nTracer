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
public class SynapsePoint {
    
        private int x, y, z;
        private float r;

        public SynapsePoint(int x, int y, int z, float r) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
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

        public float getR() {
            return this.r;
        }

        public void rescaleZ(float factor) {
            this.z /= factor;
        }
}
