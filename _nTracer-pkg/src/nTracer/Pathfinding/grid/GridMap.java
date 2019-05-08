package nTracer.Pathfinding.grid;

import nTracer.Pathfinding.grid.util.GridDouble;
import nTracer.Pathfinding.core.Map;

import ij.ImagePlus;

public class GridMap extends GridDouble implements Map {

    private final double minimumValue = 0.000001; // for 12-bit images

    public GridMap(int x, int y, int z) {
        super(x, y, z);
    }

    public GridMap(ImagePlus imp) {
        super(imp);
    }

    @Override
    public void set(int x, int y, int z, double value) {
            if (value < minimumValue) {
                value = minimumValue;
            }
            super.set(x, y, z, value);
    }

    @Override
    public void reset(double value) {
            if (value < minimumValue) {
                value = minimumValue;
            }
            super.reset(value);
    }
}
