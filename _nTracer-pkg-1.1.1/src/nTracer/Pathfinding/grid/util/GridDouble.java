package nTracer.Pathfinding.grid.util;

import java.util.ArrayList;
import ij.ImagePlus;
import ij.ImageStack;

public class GridDouble {

    protected int sizeX;
    protected int sizeY;
    protected int sizeZ;
    protected ArrayList<ArrayList<ArrayList<Double>>> gridMap;

    public GridDouble(int x, int y, int z) {
        sizeX = x;
        sizeY = y;
        sizeZ = z;
        gridMap = new ArrayList<>();
        for (int i = 0; i < x; i++) {
            gridMap.add(new ArrayList<>());
            for (int j = 0; j < y; j++) {
                gridMap.get(i).add(new ArrayList<>());
            }
        }
        initialize(1);
    }

    public GridDouble(ImagePlus imp) {
        sizeX = imp.getWidth();
        sizeY = imp.getHeight();
        sizeZ = imp.getNSlices();
        ImageStack stk = imp.getImageStack();

        gridMap = new ArrayList<>();
        for (int i = 0; i < sizeX; i++) {
            gridMap.add(new ArrayList<>());
            for (int j = 0; j < sizeY; j++) {
                gridMap.get(i).add(new ArrayList<>());
                for (int k = 1; k <= sizeZ; k++) {
                    gridMap.get(i).get(j).add((double) stk.getProcessor(k).getf(i, j));
                }
            }
        }
    }

    public GridDouble clone() {
        GridDouble newList = new GridDouble(sizeX, sizeY, sizeZ);
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                for (int k = 0; k < sizeZ; k++) {
                    newList.set(i, j, k, get(i, j, k));
                }
            }
        }
        return newList;
    }

    public void set(int x, int y, int z, double value) {
        gridMap.get(x).get(y).set(z, value);
    }

    public double get(int x, int y, int z) {
        return gridMap.get(x).get(y).get(z);
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
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                for (int k = 0; k < sizeZ; k++) {
                    set(i, j, k, value);
                }
            }
        }
    }

    private void initialize(double value) {
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                for (int k = 0; k < sizeZ; k++) {
                    gridMap.get(i).get(j).add(value);
                }
            }
        }
    }
}
