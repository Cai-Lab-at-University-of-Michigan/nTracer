package nTracer.Pathfinding.grid.astar;

import nTracer.Pathfinding.core.Location;
import nTracer.Pathfinding.core.astar.SortedLocationList;

import java.util.ArrayList;

public class GridSortedLocationList implements SortedLocationList{
	
	private ArrayList<GridLocationAstar> locationList;
	
	public GridSortedLocationList(){
		locationList = new ArrayList<>();
	}
	
	@Override
	public boolean hasNext() {
		return locationList.size() > 0;
	}
	
	@Override
	public void add(Location loc){
		GridLocationAstar location = (GridLocationAstar) loc;
		addInOrder(location);
	}

	@Override
	public GridLocationAstar getNext() {
		if(locationList.size() > 0){
			return locationList.remove(0);
		}
		return null; //TODO throw end of list exception
	}
	
	private void addInOrder(GridLocationAstar location){                
		for(int i=0; i<locationList.size(); i++){ //TODO This should be replaced with a binary search
			if(location.getTotalDistance() < locationList.get(i).getTotalDistance()){
				locationList.add(i, location);
				return;
			}
		}
                
		locationList.add(location);
	}
	
	public String toString(){
		return locationList.size() + "";
	}

}
