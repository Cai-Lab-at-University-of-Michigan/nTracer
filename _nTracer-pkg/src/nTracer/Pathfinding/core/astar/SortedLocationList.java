package nTracer.Pathfinding.core.astar;

import nTracer.Pathfinding.core.Location;

public interface SortedLocationList {
	
	public void add(Location location);
	
	public Location getNext();
	
	public boolean hasNext();

}
