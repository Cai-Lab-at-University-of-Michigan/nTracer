package nTracer.Pathfinding.grid.astar;

import nTracer.Pathfinding.grid.GridLocation;
import nTracer.Pathfinding.grid.GridMap;

public class GridHeuristicEuclidean implements GridHeuristic {

    public double getDistance(int x, int y, int z, GridLocation location) {
        double result = 0;

        double xDistance = Math.abs(x - location.getX());
        double yDistance = Math.abs(y - location.getY());
        double zDistance = Math.abs(z - location.getZ());
        result += Math.sqrt(xDistance * xDistance + yDistance * yDistance + zDistance * zDistance);
        return result;
    }

    public double getDistance(int x, int y, int z, GridLocation location, GridMap map) {
        double result = 0;
        double dx = x - location.getX();
        double dy = y - location.getY();
        double dz = z - location.getZ();
        result += Math.sqrt(dx * dx + dy * dy + dz*dz);

        result = result * map.get(x, y, z);
        //IJ.log("todoDistance = "+result);
        return result;
    }
}
