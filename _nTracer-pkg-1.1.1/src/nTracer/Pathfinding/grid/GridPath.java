package nTracer.Pathfinding.grid;

import java.util.ArrayList;

import nTracer.Pathfinding.core.Path;
public class GridPath implements Path{

	private ArrayList<GridLocation> locationList;
	
	public GridPath(ArrayList<GridLocation> locationList){
		this.locationList = locationList;
                //IJ.log("path generated, size="+locationList.size());
	}
	
	public ArrayList<GridLocation> getList(){
            //IJ.log("path generated, size="+locationList.size());
		ArrayList<GridLocation> locList = new ArrayList<GridLocation>();
		for(int i=0; i<locationList.size(); i++){
			locList.add(locationList.get(i));
                        //IJ.log(i+"");
		}
		return locList;
	}
	
	@Override
	public boolean hasNextMove() {
		return locationList.size() > 0;
	}
	
	@Override
	public GridLocation getNextMove(){
		if(locationList.size() > 0){
			return locationList.remove(0);
		}
		return null;
	}
	
	@Override
	public GridPath clone(){
		GridPath path;
		
		ArrayList<GridLocation> locList = new ArrayList<GridLocation>();
		for(int i=0; i<locationList.size(); i++){
			locList.add(locationList.get(i));
		}
		
		path = new GridPath(locList);
		return path;
	}

	
	
	
}
