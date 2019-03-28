package nTracer.Pathfinding.core;

public interface Path {
	
	public boolean hasNextMove();
	
	public Location getNextMove();
	
	public Path clone();

}
