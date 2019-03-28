package nTracer.Pathfinding.grid.astar;

import nTracer.Pathfinding.grid.GridLocation;
import nTracer.Pathfinding.grid.GridMap;

public interface GridHeuristic {
	
	public double getDistance(int x, int y, int z, GridLocation location);

        public double getDistance(int x, int y, int z, GridLocation location, GridMap map);

}
