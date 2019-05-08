package nTracer.Pathfinding.grid.astar;

import nTracer.Pathfinding.grid.GridLocation;
import nTracer.Pathfinding.grid.GridMap;

public class GridHeuristicEuclidean implements GridHeuristic {

    public double getDistance(int x, int y, int z, GridLocation location) {
        double dx = x - location.getX();
        double dy = y - location.getY();
        double dz = z - location.getZ();

        return Math.sqrt( dx*dx + dy*dy + dz*dz );
    }

    public double getDistance(int x, int y, int z, GridLocation location, GridMap map) {
        double result = getDistance(x,y,z,location);
        result *= map.get(x, y, z);
        //IJ.log("todoDistance = "+result);
        
        return result;
    }
}
