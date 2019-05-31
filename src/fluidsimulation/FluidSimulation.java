/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fluidsimulation;

import graphics.Window;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
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
    private volatile Window window;
    private boolean running;

    private final static float SCALE_FACTOR = 5;

    private FluidSimulation(int nTiles, int tileSize, float diffuseFactor, float timeStep) {
        totalNumberTiles = nTiles * nTiles;
        //Area is a square with n by n tiles, tiles go in order from left to right, top to bottum, starting in top left corner
        velocX = new float[totalNumberTiles];
        velocY = new float[totalNumberTiles];
        density = new float[totalNumberTiles];
        densityOld = new float[totalNumberTiles];
        velocXOld = new float[totalNumberTiles];
        velocYOld = new float[totalNumberTiles];
        queuedAddDensity = new ArrayList<>();
        queuedAddForce = new ArrayList<>();
        this.timeStep = timeStep;
        this.nTiles = nTiles;
        this.tileSize = tileSize;
        this.diffuseFactor = diffuseFactor;
        window = new Window(new Dimension(nTiles, nTiles), SCALE_FACTOR);

        window.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                queuedAddForce.add(new ForcePack((int) (e.getX() / SCALE_FACTOR), (int) (e.getY() / SCALE_FACTOR), 0f, 5f));
                queuedAddDensity.add(new DensityPack(getTileN((int) (e.getX() / SCALE_FACTOR), (int) (e.getY() / SCALE_FACTOR)), 500));
            }
        });
    }

    private void start() {
        Thread thread = new Thread(this);
        running = true;
        thread.start();
    }

    @Override
    public void run() {
        long currentTime;
        long pastTime = System.nanoTime();
        float timePassed = 0;
        float queuedTime = 0;
        while (running) {
            currentTime = System.nanoTime();
            timePassed = (float) ((double) (currentTime - pastTime) / 1e+10);
            pastTime = currentTime;

            queuedTime += timePassed;
            while (queuedTime > timeStep) {
                queuedTime -= timeStep;
                fluidUpdate(timeStep);
            }

            window.render(this);

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                System.err.println("Program thread was interrupted");
            }
        }
    }

    public Byte provide(int i, int j) {
        float num = density[getTileN(i, j)];
        return (byte) ((num > 255) ? 255 : num);
    }

    private void fluidUpdate(float dt) {

        addSource();
        addForces();

        velocX = diffuseField(velocX, dt, 1);
        velocY = diffuseField(velocY, dt, 2);
        project();
        velocXOld = velocX.clone();
        velocYOld = velocY.clone();
        velocX = advect(velocX, velocXOld, velocYOld, dt, 1);
        velocY = advect(velocY, velocXOld, velocYOld, dt, 2);
        project();
        density = diffuseField(density, timeStep, 0);
        density = advect(density, velocX, velocY, dt, 0);
    }

    private int getTileN(int i, int j) {
        if (i > nTiles || j > nTiles || i < 0 || j < 0)
            throw new IllegalArgumentException("Tile outside bounds");
        return nTiles * j + i;
    }

    private synchronized void addForces() {
        for (ForcePack f : queuedAddForce) {
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

    private synchronized void addSource() {
        for (DensityPack pack : queuedAddDensity) {
            density[pack.n] += pack.density;
        }
        queuedAddDensity.clear();
    }

    private float[] diffuseField(float[] field, float dt, int b) {
        float[] fieldOld = field.clone();
        field = new float[fieldOld.length];
        float diffuseRate = diffuseFactor * dt;

        for (int k = 0; k < 20; k++) {
            for (int i = 1; i < nTiles - 1; i++) {
                for (int j = 1; j < nTiles - 1; j++) {
                    field[getTileN(i, j)] = (fieldOld[getTileN(i, j)] + diffuseRate * (
                            field[getTileN(i + 1, j)] + fieldOld[getTileN(i - 1, j)] +
                                    field[getTileN(i, j + 1)] + field[getTileN(i, j - 1)])) / (1 + 4 * diffuseRate);
                }
            }
            setBounds(nTiles, b, field);
        }

        return field;
    }

    private float[] advect(float[] advectedValue, float[] velX, float[] velY, float dt, int b) {
        float[] newAdvectedValue = new float[advectedValue.length];
        for (int i = 1; i < nTiles - 1; i++) {
            for (int j = 1; j < nTiles - 1; j++) {
                //center of tile
                float xO = (float) ((i + 0.5) * tileSize);
                float yO = (float) ((j + 0.5) * tileSize);
                //where velocity vector times -dt lands
                float x = xO - dt * velX[getTileN(i, j)];
                float y = yO - dt * velY[getTileN(i, j)];
                //tile where vector landed
                int sN = getTileN((int) (x / tileSize), (int) (y / tileSize));

                newAdvectedValue[getTileN(i, j)] = advectedValue[sN];
            }
        }
        setBounds(nTiles, b, advectedValue);
        return newAdvectedValue;
    }

    private void project() {
        //adjusts velocty field to make sure that the flow into a tile equals the flow out
        float[] divergence = new float[totalNumberTiles];
        float[] pressure = new float[totalNumberTiles];
        float h = 1 / tileSize;
        for (int i = 1; i < nTiles - 1; i++) {
            for (int j = 1; j < nTiles - 1; j++) {
                divergence[getTileN(i, j)] = (float) -(0.5 * h * (velocX[getTileN(i - 1, j)]
                        - velocX[getTileN(i + 1, j)] + velocY[getTileN(i, j - 1)] - velocY[getTileN(i, j + 1)]));
            }
        }
        setBounds(nTiles, 0, divergence);
        setBounds(nTiles, 0, pressure);
        for (int k = 0; k < 20; k++) {
            for (int i = 1; i < nTiles - 1; i++) {
                for (int j = 1; j < nTiles - 1; j++) {
                    pressure[getTileN(i, j)] = (float) ((divergence[getTileN(i, j)] + pressure[getTileN(i + 1, j)] +
                            pressure[getTileN(i - 1, j)] + pressure[getTileN(i, j + 1)] + pressure[getTileN(i, j - 1)]) / 4.0);
                }
            }
            setBounds(nTiles, 0, pressure);
        }
        for (int i = 1; i < nTiles - 1; i++) {
            for (int j = 1; j < nTiles - 1; j++) {
                velocX[getTileN(i, j)] -= 0.5 * (pressure[getTileN(i - 1, j)] - pressure[getTileN(i + 1, j)]) / h;
                velocY[getTileN(i, j)] -= 0.5 * (pressure[getTileN(i, j - 1)] - pressure[getTileN(i, j + 1)]) / h;
            }
        }
        setBounds(nTiles, 1, velocX);
        setBounds(nTiles, 2, velocY);
    }

    private void setBounds(int N, int b, float[] x) {
        for (int i = 1; i < N - 1; i++) {
            x[getTileN(0, i)] = (b == 1) ? -x[getTileN(1, i)] : x[getTileN(1, i)];
            x[getTileN(N - 1, i)] = (b == 1) ? -x[getTileN(N - 2, i)] : x[getTileN(N - 2, i)];
            x[getTileN(i, 0)] = (b == 2) ? -x[getTileN(i, 1)] : x[getTileN(i, 1)];
            x[getTileN(i, N - 1)] = (b == 2) ? -x[getTileN(i, N - 2)] : x[getTileN(i, N - 2)];
        }

        x[getTileN(0, 0)] = (float) (0.5 * (x[getTileN(1, 0)] + x[getTileN(0, 1)]));
        x[getTileN(0, N - 1)] = (float) (0.5 * (x[getTileN(1, N - 1)] + x[getTileN(0, N - 2)]));
        x[getTileN(N - 1, 0)] = (float) (0.5 * (x[getTileN(N - 2, 0)] + x[getTileN(N - 1, 1)]));
        x[getTileN(N - 1, N - 1)] = (float) (0.5 * (x[getTileN(N - 2, N - 1)] + x[getTileN(N - 1, N - 2)]));
    }


    private class ForcePack {
        float forceX;
        float forceY;
        int i;
        int j;

        ForcePack(int i, int j, float forceX, float forceY) {
            this.i = i;
            this.j = j;
            this.forceX = forceX;
            this.forceY = forceY;
        }
    }

    private class DensityPack {
        int n;
        float density;

        DensityPack(int n, float density) {
            this.n = n;
            this.density = density;
        }
    }

    public static void main(String[] args) {
        new FluidSimulation(128, 1, 4f, 0.03f).start();
    }

}