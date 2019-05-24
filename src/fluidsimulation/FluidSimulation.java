/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fluidsimulation;

import java.util.ArrayList;
import graphics.Window;
import javax.swing.JFrame;
import java.awt.Dimension;
import java.util.ConcurrentModificationException;
/**
 *
 * @author macle
 */
public class FluidSimulation implements Runnable, graphics.GridProvider {
    private float[] velocX;
    private float[] velocY;
    private float[] density;
    private float[] densityOld;
    private float[] velocXOld;
    private float[] velocYOld;
    private volatile ArrayList<DensityPack> queuedAddDensity;
    private volatile ArrayList<ForcePack> queuedAddForce;
    private float timeStep;
    private float tileSize;
    private int nTiles;
    private int totalNumberTiles;
    private float diffuseFactor;
    private Thread thread;
    Window window;
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
        queuedAddForce = new ArrayList<>();
        this.timeStep = timeStep;
        this.nTiles = nTiles;
        this.tileSize = tileSize;
        this.diffuseFactor = diffuseFactor;
        window = new Window(new Dimension(nTiles, nTiles), this);
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
        //addDensity(nTiles/2, nTiles/2, 50000f);
        while(running) {
            currentTime = System.nanoTime();
            timePassed = (float)((double)(currentTime - pastTime)/Math.pow(10, 9));
            pastTime = currentTime;
            
            queuedTime += timePassed;
            while(queuedTime > timeStep) {
                queuedTime -= timeStep;
                fluidUpdate(timeStep);
            }
            window.render(this);
            try {
                Thread.sleep(50);
            } 
            catch(Exception e) {
                System.out.println("Thread failed to sleep");
            }
        }
    }
    
    public Number provide(int i, int j) {
        float num = density[getTileN(i, j)];
        return (byte) ((num > 255)? 255 : num);
    } 
    
    private void fluidUpdate(float timeStep) {
        addSource();
        addForces();
        density = diffuseField(density, timeStep);
        velocityStep(timeStep);
    }
    
    private int getTileN(int i, int j) {
        if(i > nTiles || j > nTiles || i < 0 || j < 0)
            throw new IllegalArgumentException("Tile outside bounds");
        return nTiles * j + i;
    }
    
    public void addDensity(int i, int j, float dens) {
        queuedAddDensity.add(new DensityPack(getTileN(i, j), dens));
    }
    
    public void applyForce(int i, int j, float forceX, float forceY) {
        queuedAddForce.add(new ForcePack(i, j, forceX, forceY));
    }
    
    private void addForces() {
        try {
            for(ForcePack f : queuedAddForce) {
                velocX[getTileN(f.i, f.j)] += f.forceX;
                velocY[getTileN(f.i, f.j)] += f.forceY;
                velocX[getTileN(f.i + 1, f.j)] += f.forceX;
                velocY[getTileN(f.i + 1, f.j)] += f.forceY;
                velocX[getTileN(f.i - 1, f.j)] += f.forceX;
                velocY[getTileN(f.i - 1, f.j)] += f.forceY;
                velocX[getTileN(f.i, f.j + 1)] += f.forceX;
                velocY[getTileN(f.i, f.j + 1)] += f.forceY;
                velocX[getTileN(f.i, f.j - 1)] += f.forceX;
                velocY[getTileN(f.i, f.j - 1)] += f.forceY;
            }
            queuedAddForce.clear();
        }
        catch(ConcurrentModificationException e) {
            
        }
    }
    
    private void addSource() {
        try {
            for(DensityPack pack : queuedAddDensity) {
                density[pack.n] += pack.density;
            }
            queuedAddDensity.clear();
        }
        catch(ConcurrentModificationException e) {
            
        }
    }
   
    private void velocityStep(float dt) {
        density = advect(density, velocX, velocY, dt);
        velocX = diffuseField(velocX, dt);
        velocY = diffuseField(velocY, dt);
        project();
        velocXOld = velocX.clone();
        velocYOld = velocY.clone();
        velocX = advect(velocX, velocXOld, velocYOld, dt);
        velocY = advect(velocY, velocXOld, velocYOld, dt);
        project();
    }
    
    private float[] diffuseField(float[] field, float dt) {
        float[] fieldOld = field.clone();
        float diffuseRate = diffuseFactor * tileSize * tileSize * dt;
        
//        for(int i = 1; i < nTiles - 1; i++) {
//            for(int j = 1; j < nTiles - 1; j++) {
//                field[getTileN(i, j)] = fieldOld[getTileN(i, j)] + diffuseRate * (
//                        field[getTileN(i + 1, j)] + field[getTileN(i - 1, j)] + 
//                        field[getTileN(i, j + 1)] + field[getTileN(i, j - 1)] - 4 * fieldOld[getTileN(i, j)]);
//            }
//        }
        for(int k = 0; k < 20; k++) {
            for(int i = 1; i < nTiles - 1; i++) {
                for(int j = 1; j < nTiles - 1; j++) {
                    field[getTileN(i, j)] = fieldOld[getTileN(i, j)] + diffuseRate * (
                            field[getTileN(i + 1, j)] + field[getTileN(i - 1, j)] + 
                            field[getTileN(i, j + 1)] + field[getTileN(i, j - 1)])/(1 + 4 * diffuseRate);
                }
            }
        }
        return field;
    }
    
    private float[] advect(float[] advectedValue, float[] velX, float[] velY, float dt) {
        float[] newAdvectedValue = new float[advectedValue.length];
        for(int i = 1; i < nTiles -1; i++) {
            for(int j = 1; j < nTiles - 1; j++) {
                try {
                //center of tile
                float xO = (float)((i + 0.5) * tileSize);
                float yO = (float)((j + 0.5) * tileSize);
                //where velocity vector times -dt lands
                float x = xO - dt * velX[getTileN(i, j)];
                float y = yO - dt * velY[getTileN(i, j)];
                //tile where vector landed
                int sN = getTileN((int) (x / tileSize), (int) (y/tileSize));
                
                newAdvectedValue[getTileN(i, j)] = advectedValue[sN];
                } catch(Exception e) {
                    
                }
            } 
        }
        return newAdvectedValue;
    }
    
    private void project() {
        //adjusts velocty field to make sure that the flow into a tile equals the flow out
        float[] divergance = new float[totalNumberTiles];
        float[] pressure = new float[totalNumberTiles];
        float h = 1/tileSize;
        for(int i = 1; i < nTiles - 1; i++) {
            for(int j = 1; j < nTiles - 1; j++) {
                divergance[getTileN(i, j)] = (float) -(0.5 * h * (velocX[getTileN(i - 1, j)] 
                        - velocX[getTileN(i + 1, j)] + velocY[getTileN(i, j - 1)] - velocY[getTileN(i, j + 1)]));
            }
        }
        for(int k = 0; k < 20; k++) {
            for(int i = 1; i < nTiles - 1; i++) {
                for(int j = 1; j < nTiles - 1; j++) {
                    pressure[getTileN(i, j)] = (float) ((divergance[getTileN(i, j)] + pressure[getTileN(i + 1, j)] + 
                            pressure[getTileN(i - 1, j)] + pressure[getTileN(i, j + 1)] + pressure[getTileN(i, j - 1)])/4.0);
                }
            }
        }
        for(int i = 1; i < nTiles - 1; i++) {
            for(int j = 1; j < nTiles - 1; j++) {
                velocX[getTileN(i, j)] -= 0.5 * (pressure[getTileN(i - 1, j)] - pressure[getTileN(i + 1, j)])/h;
                velocY[getTileN(i, j)] -= 0.5 * (pressure[getTileN(i, j - 1)] - pressure[getTileN(i, j + 1)])/h;
            }
        }   
    }
    
    private void setBound() {
        
    }
    
    private class ForcePack {
        float forceX;
        float forceY;
        int i;
        int j;
        
        public ForcePack(int i, int j, float forceX, float forceY) {
            this.i = i;
            this.j = j;
            this.forceX = forceX;
            this.forceY = forceY;
        }
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
        new FluidSimulation(128, 1, 4f, 0.05f).start();
    }
    
}