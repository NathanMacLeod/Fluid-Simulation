/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fluidsimulation;

import graphics.Window;

import java.awt.*;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * @author macle, meggitt
 */
public class FluidSimulation implements Runnable, graphics.GridProvider {
    private float[] velocX;
    private float[] velocY;
    private float[] density;
    private float[] densityOld;
    private float[] velocXOld;
    private float[] velocYOld;
    private float timeStep;
    private int nTiles;
    private int totalNumberTiles;
    private float diffuseFactor;
    private Window window;
    private final MouseDragTool dragTool;
    private boolean running;

    final static float SCALE_FACTOR = 5;

    private FluidSimulation(int nTiles, float diffuseFactor, float timeStep) {
        totalNumberTiles = nTiles * nTiles;
        //Area is a square with n by n tiles, tiles go in order from left to right, top to bottum, starting in top left corner
        velocX = new float[totalNumberTiles];
        velocY = new float[totalNumberTiles];
        density = new float[totalNumberTiles];
        densityOld = new float[totalNumberTiles];
        velocXOld = new float[totalNumberTiles];
        velocYOld = new float[totalNumberTiles];
        this.timeStep = timeStep;
        this.nTiles = nTiles;
        this.diffuseFactor = diffuseFactor;

        dragTool = new MouseDragTool();
        window = new Window(new Dimension(nTiles, nTiles), SCALE_FACTOR);
        window.addMouseListener(dragTool);
        window.addMouseMotionListener(dragTool);
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
        return (byte) ((num > 255) ? 255 : num);
    }

    private void fluidUpdate(float dt) {
        // Add inflow of density
        addSources();

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
    private static <T> T flip(T a, @SuppressWarnings("unused") T b) {
        return a;
    }

    private int getTileN(int i, int j) {
        if (i >= nTiles || j >= nTiles || i < 0 || j < 0)
            throw new IllegalArgumentException("Tile outside bounds: (" + i + ", " + j + ")");
        return nTiles * j + i;
    }

    private void addSources() {
        dragTool.consumeSources(pack -> {
            velocX[getTileN(pack.x, pack.y)] += pack.forceX;
            velocY[getTileN(pack.x, pack.y)] += pack.forceY;
            velocX[getTileN(pack.x + 1, pack.y)] += pack.forceX;
            velocY[getTileN(pack.x + 1, pack.y)] += pack.forceY;
            velocX[getTileN(pack.x - 1, pack.y)] += pack.forceX;
            velocY[getTileN(pack.x - 1, pack.y)] += pack.forceY;
            velocX[getTileN(pack.x, pack.y + 1)] += pack.forceX;
            velocY[getTileN(pack.x, pack.y + 1)] += pack.forceY;
            velocX[getTileN(pack.x, pack.y - 1)] += pack.forceX;
            velocY[getTileN(pack.x, pack.y - 1)] += pack.forceY;
            density[getTileN(pack.x, pack.y)] += pack.density;
        });
    }

    private void diffuseField(float[] src, float[] dst, float dt, int b) {
        float diffuseRate = diffuseFactor * dt;

        for (int k = 0; k < 20; k++) {
            for (int i = 1; i < nTiles - 1; i++) {
                for (int j = 1; j < nTiles - 1; j++) {
                    dst[getTileN(i, j)] = (src[getTileN(i, j)] + diffuseRate * (
                            dst[getTileN(i + 1, j)] + dst[getTileN(i - 1, j)] +
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
                float x = ix - dt * velX[getTileN(ix, iy)];
                float y = iy - dt * velY[getTileN(ix, iy)];

                x = (float) min(max(x, 1.5), nTiles - 0.5);
                y = (float) min(max(y, 0.5), nTiles - 0.5);

                int i0 = (int) min(max(x, 1), nTiles - 2);
                int j0 = (int) min(max(y, 1), nTiles - 2);

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

    private void setBounds(int b, float[] arr) {
        for (int i = 1; i < nTiles - 1; i++) {
            arr[getTileN(0, i)] = (b == 1) ? -arr[getTileN(1, i)] : arr[getTileN(1, i)];
            arr[getTileN(nTiles - 1, i)] = (b == 1) ? -arr[getTileN(nTiles - 2, i)] : arr[getTileN(nTiles - 2, i)];
            arr[getTileN(i, 0)] = (b == 2) ? -arr[getTileN(i, 1)] : arr[getTileN(i, 1)];
            arr[getTileN(i, nTiles - 1)] = (b == 2) ? -arr[getTileN(i, nTiles - 2)] : arr[getTileN(i, nTiles - 2)];
        }

        arr[getTileN(0, 0)] = (float) (0.5 * (arr[getTileN(1, 0)] + arr[getTileN(0, 1)]));
        arr[getTileN(0, nTiles - 1)] = (float) (0.5 * (arr[getTileN(1, nTiles - 1)] + arr[getTileN(0, nTiles - 2)]));
        arr[getTileN(nTiles - 1, 0)] = (float) (0.5 * (arr[getTileN(nTiles - 2, 0)] + arr[getTileN(nTiles - 1, 1)]));
        arr[getTileN(nTiles - 1, nTiles - 1)] = (float) (0.5 * (arr[getTileN(nTiles - 2, nTiles - 1)] + arr[getTileN(nTiles - 1, nTiles - 2)]));
    }

    public static void main(String[] args) {
        new FluidSimulation(128, 4f, 0.06f).start();
    }

}