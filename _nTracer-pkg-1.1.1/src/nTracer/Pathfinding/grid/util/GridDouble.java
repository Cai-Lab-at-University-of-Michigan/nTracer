package nTracer.Pathfinding.grid.util;

import java.util.Arrays;
import ij.ImagePlus;
import ij.ImageStack;

public class GridDouble {

    private int sizeX;
    private int sizeY;
    private int sizeZ;
    public double[][][] gridMap;

    public GridDouble(int x, int y, int z) {
        sizeX = x;
        sizeY = y;
        sizeZ = z;
        
        gridMap = new double[ getSizeX() ][ getSizeY() ][ getSizeZ() ];
        initialize(1);
    }

    public GridDouble(ImagePlus imp) {
        sizeX = imp.getWidth();
        sizeY = imp.getHeight();
        sizeZ = imp.getNSlices();
        ImageStack stk = imp.getImageStack();

        gridMap = new double[ getSizeX() ][ getSizeY() ][ getSizeZ() ];
        initialize(1);
        
        for (int k = 0; k < sizeZ; k++) {
            float[][] ipData = stk.getProcessor(k+1).getFloatArray();
            for (int i = 0; i < sizeX; i++) {
                for (int j = 0; j < sizeY; j++) {
                    gridMap[i][j][k] = (double) ipData[i][j];
                }
            }
        }
    }

    @Override
    public GridDouble clone() {
        GridDouble newList = new GridDouble(sizeX, sizeY, sizeZ);
        newList.gridMap = gridMap.clone();
        return newList;
    }

    public void set(int x, int y, int z, double value) {
        gridMap[x][y][z] = value;
    }

    public double get(int x, int y, int z) {
        return gridMap[x][y][z];
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public void reset(double value) {
        for( double[][] row : gridMap ) {
            for( double[] rowCol : row ) {
                Arrays.fill( rowCol, value );
            }
        }
    }

    private void initialize(double value) {
        reset( value );
    }
}
