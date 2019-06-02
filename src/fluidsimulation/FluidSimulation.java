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
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

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
    private final List<DensityPack> queuedAddDensity;
    private final List<ForcePack> queuedAddForce;
    private float timeStep;
    private float tileSize;
    private int nTiles;
    private int totalNumberTiles;
    private float diffuseFactor;
    private Window window;
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

        window.addMouseMotionListener(new mouseDragTool());
    }

    private class mouseDragTool extends MouseAdapter {
        private float dragScalar = 10f;
        private double[] previousCoords;

        public void mousePressed(MouseEvent e) {
            previousCoords = new double[]{e.getX(), e.getY()};
        }

        public void mouseDragged(MouseEvent e) {
            float[] velocityVector = new float[]{0, 0};
            if (previousCoords != null)
                velocityVector = new float[]{dragScalar * (float) (e.getX() - previousCoords[0]), dragScalar * (float) (e.getY() - previousCoords[1])};
            previousCoords = new double[]{e.getX(), e.getY()};
            synchronized (queuedAddDensity) {
                queuedAddForce.add(new ForcePack((int) (e.getX() / SCALE_FACTOR), (int) (e.getY() / SCALE_FACTOR), velocityVector[0], velocityVector[1]));
                queuedAddDensity.add(new DensityPack(getTileN((int) (e.getX() / SCALE_FACTOR), (int) (e.getY() / SCALE_FACTOR)), 500));
            }
        }
    }

    private void start() {
        Thread thread = new Thread(this);
        running = true;
        thread.start();
    }

    @Override
    public void run() {
        long pastTime = System.nanoTime();
        float timePassed;
        float queuedTime = 0;
        while (running) {
            long currentTime = System.nanoTime();
            timePassed = (float) ((double) (currentTime - pastTime) / 1e+9);
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

    public Number provide(int i, int j) {
        float num = density[getTileN(i, j)];
        //float num = Math.abs(velocX[getTileN(i, j)]) * 25;
        return (byte) ((num > 255) ? 255 : num);
    }

    private void fluidUpdate(float dt) {
        // Add inflow of density
        addSource();
        addForces();

        // Diffuse velocities and save result to the old version
        diffuseField(velocX, velocXOld, dt, 1);
        diffuseField(velocY, velocYOld, dt, 2);

        // Flip new and old velocity fields
        velocX = flip(velocXOld, velocXOld = velocX);
        velocY = flip(velocYOld, velocYOld = velocY);

        project();

        advect(velocX, velocXOld, velocXOld, velocYOld, dt, 1);
        advect(velocY, velocYOld, velocXOld, velocYOld, dt, 2);

        project();

        diffuseField(density, densityOld, timeStep, 0);
        advect(densityOld, density, velocX, velocY, dt, 0);
    }

    // This should really be a macro, but java doesn't like macros so I'm stuck with this.
    private static <T> T flip(T a, T b) {
        return a;
    }

    private int getTileN(int i, int j) {
        if (i > nTiles || j > nTiles || i < 0 || j < 0)
            throw new IllegalArgumentException("Tile outside bounds");
        return nTiles * j + i;
    }

    private void addForces() {
        synchronized (queuedAddDensity) {
            for (ForcePack f : queuedAddForce) {
                velocX[getTileN(f.x, f.y)] += f.forceX;
                velocY[getTileN(f.x, f.y)] += f.forceY;
                velocX[getTileN(f.x + 1, f.y)] += f.forceX;
                velocY[getTileN(f.x + 1, f.y)] += f.forceY;
                velocX[getTileN(f.x - 1, f.y)] += f.forceX;
                velocY[getTileN(f.x - 1, f.y)] += f.forceY;
                velocX[getTileN(f.x, f.y + 1)] += f.forceX;
                velocY[getTileN(f.x, f.y + 1)] += f.forceY;
                velocX[getTileN(f.x, f.y - 1)] += f.forceX;
                velocY[getTileN(f.x, f.y - 1)] += f.forceY;
            }
            queuedAddForce.clear();
        }
    }

    private void addSource() {
        synchronized (queuedAddDensity) {
            for (DensityPack pack : queuedAddDensity) {
                density[pack.n] += pack.density;
            }
            queuedAddDensity.clear();
        }
    }

    private void diffuseField(float[] src, float[] dst, float dt, int b) {
        float diffuseRate = diffuseFactor * dt;

        for (int k = 0; k < 20; k++) {
            for (int i = 1; i < nTiles - 1; i++) {
                for (int j = 1; j < nTiles - 1; j++) {
                    dst[getTileN(i, j)] = (src[getTileN(i, j)] + diffuseRate * (
                            dst[getTileN(i + 1, j)] + src[getTileN(i - 1, j)] +
                                    dst[getTileN(i, j + 1)] + dst[getTileN(i, j - 1)])) / (1 + 4 * diffuseRate);
                }
            }
            setBounds(b, dst);
        }
    }

    private void advect(float[] src, float[] dst, float[] velX, float[] velY, float dt, int b) {
        for (int ix = 1; ix < nTiles - 1; ix++) {
            for (int iy = 1; iy < nTiles - 1; iy++) {
                //center of tile
                float x = ix - dt * velocX[getTileN(ix, iy)];
                float y = iy - dt * velocY[getTileN(ix, iy)];

                x = (float) min(max(x, 1.5), nTiles - 0.5);
                y = (float) min(max(y, 0.5), nTiles - 0.5);

                int i0 = (int) x;
                int j0 = (int) y;

                float t1 = y - j0;
                float t0 = 1 - t1;

                dst[getTileN(ix, iy)] = (1 + i0 - x) * (t0 * src[getTileN(i0, j0)] + t1 * src[getTileN(i0, j0 + 1)]) +
                        (x - i0) * (t0 * src[getTileN(i0 + 1, j0)] + t1 * src[getTileN(i0 + 1, j0 + 1)]);
            }
        }
        setBounds(b, dst);
    }

    private void project() {
        //adjusts velocty field to make sure that the flow into a tile equals the flow out
        float[] divergance = new float[totalNumberTiles];
        float[] pressure = new float[totalNumberTiles];
        for (int i = 1; i < nTiles - 1; i++) {
            for (int j = 1; j < nTiles - 1; j++) {
                divergance[getTileN(i, j)] = (float) -(0.5 * (velocX[getTileN(i + 1, j)]
                        - velocX[getTileN(i - 1, j)] + velocY[getTileN(i, j + 1)] - velocY[getTileN(i, j - 1)]));
            }
        }
        setBounds(0, divergance);
        setBounds(0, pressure);
        for (int k = 0; k < 20; k++) {
            for (int i = 1; i < nTiles - 1; i++) {
                for (int j = 1; j < nTiles - 1; j++) {
                    pressure[getTileN(i, j)] = (float) ((divergance[getTileN(i, j)] + pressure[getTileN(i + 1, j)] +
                            pressure[getTileN(i - 1, j)] + pressure[getTileN(i, j + 1)] + pressure[getTileN(i, j - 1)]) / 4.0);
                }
            }
            setBounds(0, pressure);
        }
        for (int i = 1; i < nTiles - 1; i++) {
            for (int j = 1; j < nTiles - 1; j++) {
                velocX[getTileN(i, j)] -= 0.5 * (pressure[getTileN(i + 1, j)] - pressure[getTileN(i - 1, j)]);
                velocY[getTileN(i, j)] -= 0.5 * (pressure[getTileN(i, j + 1)] - pressure[getTileN(i, j - 1)]);
            }
        }
        setBounds(1, velocX);
        setBounds(2, velocY);
    }

    private void setBounds(int b, float[] x) {
        for (int i = 1; i < nTiles - 1; i++) {
            x[getTileN(0, i)] = (b == 1) ? -x[getTileN(1, i)] : x[getTileN(1, i)];
            x[getTileN(nTiles - 1, i)] = (b == 1) ? -x[getTileN(nTiles - 2, i)] : x[getTileN(nTiles - 2, i)];
            x[getTileN(i, 0)] = (b == 2) ? -x[getTileN(i, 1)] : x[getTileN(i, 1)];
            x[getTileN(i, nTiles - 1)] = (b == 2) ? -x[getTileN(i, nTiles - 2)] : x[getTileN(i, nTiles - 2)];
        }

        x[getTileN(0, 0)] = (float) (0.5 * (x[getTileN(1, 0)] + x[getTileN(0, 1)]));
        x[getTileN(0, nTiles - 1)] = (float) (0.5 * (x[getTileN(1, nTiles - 1)] + x[getTileN(0, nTiles - 2)]));
        x[getTileN(nTiles - 1, 0)] = (float) (0.5 * (x[getTileN(nTiles - 2, 0)] + x[getTileN(nTiles - 1, 1)]));
        x[getTileN(nTiles - 1, nTiles - 1)] = (float) (0.5 * (x[getTileN(nTiles - 2, nTiles - 1)] + x[getTileN(nTiles - 1, nTiles - 2)]));
    }


    private class ForcePack {
        float forceX;
        float forceY;
        int x;
        int y;

        ForcePack(int x, int y, float forceX, float forceY) {
            this.x = x;
            this.y = y;
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
        new FluidSimulation(128, 1, 4f, 0.06f).start();
    }

}