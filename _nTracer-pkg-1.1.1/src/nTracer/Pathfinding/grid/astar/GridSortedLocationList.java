package nTracer.Pathfinding.grid.astar;

import nTracer.Pathfinding.core.Location;
import nTracer.Pathfinding.core.astar.SortedLocationList;

import java.util.ArrayList;

public class GridSortedLocationList implements SortedLocationList{
	
	private ArrayList<GridLocationAstar> locationList;
	
	public GridSortedLocationList(){
		locationList = new ArrayList<GridLocationAstar>();
	}
	
	@Override
	public boolean hasNext() {
		return locationList.size() > 0;
	}
	
	@Override
	public void add(Location loc){
		GridLocationAstar location = (GridLocationAstar)loc;
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
		GridLocationAstar tempLocation;
		if(locationList.size() == 0){
			locationList.add(location);
			return;
		}
		for(int i=0; i<locationList.size(); i++){
			tempLocation = locationList.get(i);
			if(location.getTotalDistance() < tempLocation.getTotalDistance()){
				locationList.add(i, location);
				return;
			}
		}
		locationList.add(location);
	}
	
	public String toString(){
		String result = locationList.size() + "";
		return result;
	}

}
