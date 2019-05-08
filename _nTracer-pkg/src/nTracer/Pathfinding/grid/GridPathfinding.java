package nTracer.Pathfinding.grid;

import nTracer.Pathfinding.grid.astar.GridHeuristicEuclidean;
import nTracer.Pathfinding.grid.astar.GridHeuristic;
import nTracer.Pathfinding.core.Location;
import nTracer.Pathfinding.core.Map;
import nTracer.Pathfinding.core.Pathfinding;
import nTracer.Pathfinding.grid.astar.GridAstar;

public class GridPathfinding implements Pathfinding{

	GridAstar astar;
	GridHeuristic heuristic;
	
	public GridPathfinding(){
		heuristic = new GridHeuristicEuclidean();
	}
	
	@Override
	public GridPath getPath(Location s, Location e, Map m) {
		GridLocation start = (GridLocation) s;
		GridLocation end = (GridLocation) e;
		GridMap map = (GridMap) m;
		
		astar = new GridAstar(start, end, map, heuristic);
		
		return astar.getPath();
	}

}
