package nTracer.Pathfinding.grid;

import nTracer.Pathfinding.core.Location;

public class GridLocation implements Location{
	
	private int x;
	private int y;
        private int z;
	private boolean end;
	
	public GridLocation(int x, int y, int z, boolean end){
		this.x = x;
		this.y = y;
                this.z = z;
		this.end = end;
	}
	
	public int getX(){
		return x;
	}
	
	public int getY(){
		return y;
	}
        
	public int getZ(){
		return z;
	}
	
	public boolean isEnd(){
		return end;
	}

}
