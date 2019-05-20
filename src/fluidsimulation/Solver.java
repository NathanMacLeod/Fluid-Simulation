package fluidsimulation;

import graphics.GridProvider;

import static java.lang.Math.*;

public class Solver implements GridProvider<Byte> {

    private Cell densityMap;
    private Cell xVelocity;
    private Cell yVelocity;

    private final int width;
    private final int height;

    private double cellSize;
    private double density;

    /**
     * Right hand side of pressure solving
     */
    private double[] pressureRhs;

    /**
     * Pressure solutions
     */
    private double[] pressureSolution;

    Solver(int width, int height, double density) {
        this.width = width;
        this.height = height;
        this.density = density;

        cellSize = 1.0 / min(width, height);

        densityMap = new Cell(width, height, 0.5, 0.5, cellSize);
        xVelocity = new Cell(width + 1, height, 0.0, 0.5, cellSize);
        yVelocity = new Cell(width, height + 1, 0.5, 0.0, cellSize);

        pressureRhs = new double[width * height];
        pressureSolution = new double[width * height];
    }

    /* Builds the pressure right hand side as the negative divergence */
    private void buildRhs() {
        double scale = 1.0 / cellSize;

        for (int y = 0, idx = 0; y < height; y++) {
            for (int x = 0; x < width; x++, idx++) {
                pressureRhs[idx] = -scale * (xVelocity.source[x + 1][y] - xVelocity.source[x][y] + yVelocity.source[x][y + 1] - yVelocity.source[x][y]);
            }
        }
    }

    /* Performs the pressure solve using Gauss-Seidel.
     * The solver will run as long as it takes to get the relative error below
     * a threshold, but will never exceed `limit' iterations
     */
    private void project(int limit, double timestep) {
        double scale = timestep / (density * cellSize * cellSize);

        double maxDelta = 0;
        for (int iter = 0; iter < limit; iter++) {
            maxDelta = 0.0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = x + y * width;

                    double diag = 0.0, offDiag = 0.0;

                    /* Here we build the matrix implicitly as the five-point
                     * stencil. Grid borders are assumed to be solid, i.e.
                     * there is no fluid outside the simulation domain.
                     */
                    if (x > 0) {
                        diag += scale;
                        offDiag -= scale * pressureSolution[idx - 1];
                    }
                    if (y > 0) {
                        diag += scale;
                        offDiag -= scale * pressureSolution[idx - width];
                    }
                    if (x < width - 1) {
                        diag += scale;
                        offDiag -= scale * pressureSolution[idx + 1];
                    }
                    if (y < height - 1) {
                        diag += scale;
                        offDiag -= scale * pressureSolution[idx + width];
                    }

                    double newPressure = (pressureRhs[idx] - offDiag) / diag;

                    maxDelta = max(maxDelta, abs(pressureSolution[idx] - newPressure));

                    pressureSolution[idx] = newPressure;
                }
            }

            if (maxDelta < 1e-5) {
                System.out.printf("Exiting solver after %dMap iterations, maximum change is %f\n", iter, maxDelta);
                return;
            }
        }

        System.out.printf("Exceeded budget of %dMap iterations, maximum change was %f\n", limit, maxDelta);
    }

    /* Applies the computed pressure to the velocity field */
    private void applyPressure(double timeStep) {
        double scale = timeStep / (density * cellSize);

        for (int y = 0, idx = 0; y < height; y++) {
            for (int x = 0; x < width; x++, idx++) {
                xVelocity.source[x][y] -= scale * pressureSolution[idx];
                xVelocity.source[x + 1][y] += scale * pressureSolution[idx];
                yVelocity.source[x][y] -= scale * pressureSolution[idx];
                yVelocity.source[x][y + 1] += scale * pressureSolution[idx];
            }
        }

        for (int y = 0; y < height; y++) {
            xVelocity.source[0][y] = 0;
            xVelocity.source[width][y] = 0;
        }
        for (int x = 0; x < width; x++) {
            yVelocity.source[x][0]  = 0;
            yVelocity.source[x][height] = 0;
        }
    }

    void update(double timestep) {
        buildRhs();
        project(600, timestep);
        applyPressure(timestep);

        densityMap.advect(timestep, xVelocity, yVelocity);
        xVelocity.advect(timestep, xVelocity, yVelocity);
        yVelocity.advect(timestep, xVelocity, yVelocity);

        /* Make effect of advection visible, since it's not an in-place operation */
        densityMap.flip();
        xVelocity.flip();
        yVelocity.flip();
    }

    /* Set density and x/y velocity in given rectangle to densityMap/xVelocity/yVelocity, respectively */
    void addInflow(double x, double y, double w, double h, double d, double u, double v) {
        this.densityMap.setRegion(x, y, x + w, y + h, d);
        this.xVelocity.setRegion(x, y, x + w, y + h, u);
        this.yVelocity.setRegion(x, y, x + w, y + h, v);
    }

    @Override
    public Byte provide(int x, int y) {
        return (byte) (densityMap.source[x][y] * 128.0 - 256);
    }
}
