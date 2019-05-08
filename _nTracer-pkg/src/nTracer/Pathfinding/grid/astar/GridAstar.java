package nTracer.Pathfinding.grid.astar;

import nTracer.Pathfinding.grid.GridLocation;
import nTracer.Pathfinding.grid.GridMap;
import nTracer.Pathfinding.grid.GridPath;
import nTracer.Pathfinding.grid.util.GridDouble;
import nTracer.Pathfinding.core.astar.Astar;
import ij.IJ;

import java.util.ArrayList;


public class GridAstar implements Astar {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int unvisited = 0;
    private final int visited = 1;
    private final GridDouble visitedMap; //0 mean not visited, 1 mean visited
    private final GridDouble distanceMap; //all value start at Integer.MAX_VALUE;
    private final GridSortedLocationList sortedLocationList;
    private final GridLocation start;
    private final GridLocation end;
    private final GridMap map;
    private final GridHeuristic heuristic;

    public GridAstar(GridLocation start, GridLocation end, GridMap map, GridHeuristic heuristic) {
        this.start = start;
        this.end = end;
        this.map = map;
        this.heuristic = heuristic;
        
        sizeX = map.getSizeX();
        sizeY = map.getSizeY();
        sizeZ = map.getSizeZ();
        
        visitedMap = new GridDouble(sizeX, sizeY, sizeZ);
        visitedMap.reset(unvisited);
        distanceMap = new GridDouble(sizeX, sizeY, sizeZ);
        distanceMap.reset(Integer.MAX_VALUE);
        
        sortedLocationList = new GridSortedLocationList();
    }

    @Override
    public GridPath getPath() {
        System.out.print("Running GetPath");
        long startTime = System.nanoTime();
        
        GridPath path = null;

        if ( !isValid3D(start.getX(), start.getY(), start.getZ()) || !isValid3D(end.getX(), end.getY(), end.getZ()) ) {
            IJ.error( "Start or ending location is NOT valid!" );
            return path;
        }
        
        GridLocationAstar startingLocation = new GridLocationAstar(start.getX(), start.getY(), start.getZ(), start.isEnd(), 0, -1);
        sortedLocationList.add(startingLocation);
        distanceMap.set(start.getX(), start.getY(), start.getZ(), startingLocation.getDoneDistance());
        
        GridLocationAstar location;
        boolean endIsFound = false;
        int count = 0, MAX_COUNT = 5000;

        //Find a path...
        while (sortedLocationList.hasNext() && count<MAX_COUNT) {
            location = sortedLocationList.getNext();
//IJ.log("location ("+location.getX()+", "+location.getY()+", "+location.getZ()+")");
            if (visitedMap.get(location.getX(), location.getY(), location.getZ()) == visited) {
                continue;
            }
            visitedMap.set(location.getX(), location.getY(), location.getZ(), visited);
            if (location.isEnd()) {
                endIsFound = true;
                break;
            }
            addAdjacent(location);
            count++;
        }

        if (count >= MAX_COUNT){
            return null;
        }

        //Resolve path...
        if (endIsFound) {
            path = traceBackThePath();
            System.out.println(path);
        }
        //IJ.log("whole path length = "+path.getList().size());
        
        double diff = System.nanoTime()-startTime;
        diff /= 1000000000;
        System.out.println( "Time Delta: " + diff );

        return path;
    }
    
    /* ---------------------- HELPING METHODS -----------------------------*/
    /* ------------------ RESOLVE A PATH HELPER ------------------------------*/
    @SuppressWarnings("unused")
    private void printDistanceMap() {
        for (int k = 0; k < distanceMap.getSizeZ(); k++) {
            for (int j = 0; j < distanceMap.getSizeY(); j++) {
                String s = "";
                for (int i = 0; i < distanceMap.getSizeX(); i++) {
                    int n = (int) distanceMap.get(i, j, k);
                    int n2 = (int) (distanceMap.get(i, j, k) * 10);
                    double d = n2 / 10.0;
                    if (n == Integer.MAX_VALUE) {
                        s += "-1";
                    } else {
                        s += d;
                    }
                    s += " : ";
                }
                System.out.println(s);
            }
        }
    }

    private GridPath traceBackThePath() {
//        IJ.log("start trace back path");
        GridPath path;
        GridLocation currentLocation;

        ArrayList<GridLocation> locationList = new ArrayList<>();

        currentLocation = end;
        int x = currentLocation.getX();
        int y = currentLocation.getY();
        int z = currentLocation.getZ();
        locationList.add(currentLocation);
//IJ.log("end added ("+locationList.get(0).getX()+", "+locationList.get(0).getY()+", "+locationList.get(0).getZ()+")");
        while (!(x == start.getX() && y == start.getY() && z == start.getZ())) {
            currentLocation = findNextLocation(x, y, z);
            x = currentLocation.getX();
            y = currentLocation.getY();
            z = currentLocation.getZ();
//IJ.log("try to add point ("+x+", "+y+", "+z+")");
            locationList.add(currentLocation);
//IJ.log("added");
        }
//IJ.log("got path");
        path = new GridPath(locationList);
        return path;
    }

    private GridLocation findNextLocation(int startX, int startY, int startZ) {

        int bestX=startX;
        int bestY=startY;
        int bestZ=startZ;
        double bestValue = Integer.MAX_VALUE;
//IJ.log("finding next: start from ("+startX+", "+startY+", "+startZ+") -- "+distanceMap.get(startX, startY, startZ));
        for (int x = startX - 1; x <= startX + 1; x++) {
            for (int y = startY - 1; y <= startY + 1; y++) {
                for (int z = startZ - 1; z <= startZ + 1; z++) {
                    if (getValue(x, y, z) < bestValue && isValid3D(x, y, z)) {
                        bestX = x;
                        bestY = y;
                        bestZ = z;
                        bestValue = distanceMap.get(x, y, z);
                    }
                }
            }
        }
//.log("finding next: found ("+bestX+", "+bestY+", "+bestZ+") -- "+distanceMap.get(bestX, bestY, bestZ));
        return createLocation(bestX, bestY, bestZ);
    }

    private double getValue(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            return Integer.MAX_VALUE;
        }
        return distanceMap.get(x, y, z);
    }

    private GridLocation createLocation(int x, int y, int z) {
        boolean isEnd = (x == end.getX() && y == end.getY() && z == end.getZ());
        return new GridLocation(x, y, z, isEnd);
    }

    /* --------------------- FIND A PATH HELPER ------------------------------*/
    private void addAdjacent(GridLocationAstar previousLocation) {
        int currentX = previousLocation.getX();
        int currentY = previousLocation.getY();
        int currentZ = previousLocation.getZ();

        for (int x = currentX - 1; x <= currentX + 1; x++) {
            for (int y = currentY - 1; y <= currentY + 1; y++) {
                for (int z = currentZ - 1; z <= currentZ + 1; z++) {
                    if (unvisited3D(x, y, z) && isValid3D(x, y, z)) {
                        addLocation(x, y, z, previousLocation);
                    }
                }
            }
        }
    }

    private GridLocationAstar createLocation(int x, int y, int z, GridLocationAstar previousLocation) {
        GridLocationAstar newLocation;
        boolean isEnd = end.getX() == x && end.getY() == y && end.getZ() == z;
        double doneDist = previousLocation.getDoneDistance() + getNeighborDist(x, y, z);
        double todoDist = heuristic.getDistance(x, y, z, end, map);
        newLocation = new GridLocationAstar(x, y, z, isEnd, doneDist, todoDist);
        //IJ.log("new location ("+newLocation.getX()+", "+newLocation.getY()+", "+
        //        newLocation.getZ()+"); isEnd= "+isEnd+"; doneDist="+newLocation.getDoneDistance()+
        //        "; todoDist="+newLocation.getTotalDistance());
        return newLocation;
    }

    private void addLocation(int x, int y, int z, GridLocationAstar previousLocation) {
        GridLocationAstar location = createLocation(x, y, z, previousLocation);
        sortedLocationList.add(location);
        
        double dist = Math.min(location.getDoneDistance(), distanceMap.get(x, y, z));
        distanceMap.set(x, y, z, dist);
    }

    private boolean isValid3D(int x, int y, int z) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
    }

    private boolean unvisited3D(int x, int y, int z) {
        return isValid3D(x, y, z) && visitedMap.get(x, y, z) == unvisited;
    }

    private double getNeighborDist(int x, int y, int z) {
        return map.get(x, y, z);
    }
}
