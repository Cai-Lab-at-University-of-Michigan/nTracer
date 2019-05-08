package nTracer.Pathfinding.grid.astar;

import nTracer.Pathfinding.grid.GridLocation;

public class GridLocationAstar extends GridLocation{

	private double doneDist;
	private double todoDist;
	
	public GridLocationAstar(int x, int y, int z, boolean end, double doneDist, double todoDist) {
		super(x, y, z, end);
                
		this.doneDist = doneDist;
		this.todoDist = todoDist;
	}
	
	public double getDoneDistance(){
		return doneDist;
	}
	
	public double getTotalDistance(){
		return doneDist + todoDist;
	}

}
