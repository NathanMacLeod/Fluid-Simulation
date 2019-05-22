/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fluidsimulation;

import java.util.ArrayList;
/**
 *
 * @author macle
 */
public class FluidSimulation implements Runnable {
    private float[] velocX;
    private float[] velocY;
    private float[] density;
    private float[] velocXOld;
    private float[] velocYOld;
    private ArrayList<DensityPack> queuedAddDensity ;
    private float timeStep;
    private float tileSize;
    private int nTiles;
    private int totalNumberTiles;
    private float diffuseFactor;
    private Thread thread;
    private boolean running;
    
    public FluidSimulation(int nTiles, int tileSize, float diffuseFactor, float timeStep) {
        totalNumberTiles = nTiles * nTiles;
        //Area is a square with n by n tiles, tiles go in order from left to right, top to bottum, starting in top left corner
        velocX = new float[totalNumberTiles];
        velocY = new float[totalNumberTiles];
        density = new float[totalNumberTiles];
        velocXOld = new float[totalNumberTiles];
        velocYOld = new float[totalNumberTiles];
        queuedAddDensity = new ArrayList<>();
        this.nTiles = nTiles;
        this.tileSize = tileSize;
        this.diffuseFactor = diffuseFactor;
    }
    
    public void start() {
        thread = new Thread(this);
        running = true;
        thread.start();
        
    }
    
    public void run() {
        long currentTime;
        long pastTime = System.nanoTime();
        float timePassed = 0;
        float queuedTime = 0;
        while(running) {
            currentTime = System.nanoTime();
            timePassed = (float)((double)(currentTime - pastTime)/Math.pow(10, 9));
            pastTime = currentTime;
            
            queuedTime += timePassed;
            while(timePassed > timeStep) {
                queuedTime -= timeStep;
                fluidUpdate(timeStep);
            }
        }
    }
    
    private void fluidUpdate(float timeStep) {
        addSource();
        diffuse(timeStep);
    }
    
    private int getTileN(int i, int j) {
        if(i >= nTiles - 1 || j >= nTiles - 1 || i < 1 || j < 1)
            throw new IllegalArgumentException("Tile outside bounds");
        return nTiles * j + i;
    }
    
    public void addDensity(int i, int j, float dens) {
        queuedAddDensity.add(new DensityPack(getTileN(i, j), dens));
    }
    
    private void addSource() {
        for(DensityPack pack : queuedAddDensity) {
            density[pack.n] += pack.density;
        }
    }
   
    private void diffuse(float dt) {
        velocXOld = velocX;
        float diffuseRate = diffuseFactor * tileSize * tileSize * dt;
        
        for(int k = 0; k < 20; k++) {
            for(int i = 1; i < nTiles - 1; i++) {
                for(int j = 1; j < nTiles - 1; j++) {
                    velocX[getTileN(i, j)] = velocXOld[getTileN(i, j)] + diffuseRate * (
                            velocX[getTileN(i + 1, j)] + velocX[getTileN(i - 1, j)] + 
                            velocX[getTileN(i, j + 1)] + velocX[getTileN(i, j - 1)])/(1 + 4*diffuseRate);
                }
            }
        }
    }
    
    private float[] advect(float[] advectedValue, float[] velX, float[] velY, float dt) {
        float[] newAdvectedValue = new float[advectedValue.length];
        for(int i = 1; i < nTiles -1; i++) {
            for(int j = 1; j < nTiles - 1; j++) {
                //center of tile
                float xO = (float)((i - 0.5) * tileSize);
                float yO = (float)((j - 0.5) * tileSize);
                //where velocity vector times -dt lands
                float x = xO - dt * velX[getTileN(i, j)];
                float y = yO - dt * velY[getTileN(i, j)];
                //tile where vector landed
                int sN = getTileN((int) (x / tileSize), (int) (y/tileSize));
                newAdvectedValue[getTileN(i, j)] = advectedValue[sN];
            }
        }
        return newAdvectedValue;
    }
    
    private void project(float dt) {
        //adjusts velocty field to make sure that the flow into a tile equals the flow out
        float[] divergance = new float[totalNumberTiles];
    }
    
    private void setBound() {
        
    }
    
    private class DensityPack {
        int n;
        float density;
        
        public DensityPack(int n, float density) {
            this.n = n;
            this.density = density;
        }
    }
    
    public static void main(String[] args) {
        // TODO code application logic here
    }
    
}