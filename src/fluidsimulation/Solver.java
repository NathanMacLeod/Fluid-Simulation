package fluidsimulation;

import graphics.GridProvider;

import static java.lang.Math.*;

public class Solver implements GridProvider<Byte> {

    private Cell d;
    private Cell u;
    private Cell v;

    private final int width;
    private final int height;

    /* Grid cell size and fluid density */
    private double hx;
    private double density;

    /* Arrays for: */
    private double[] r; /* Right hand side of pressure solve */
    private double[] p; /* Pressure solution */

    Solver(int width, int height, double density) {
        this.width = width;
        this.height = height;
        this.density = density;

        hx = 1. / min(width, height);

        d = new Cell(width, height, 0.5, 0.5, hx);
        u = new Cell(width + 1, height, 0.0, 0.5, hx);
        v = new Cell(width, height + 1, 0.5, 0.0, hx);

        r = new double[width * height];
        p = new double[width * height];
    }

    /* Builds the pressure right hand side as the negative divergence */
    private void buildRhs() {
        double scale = 1.0 / hx;

        for (int y = 0, idx = 0; y < height; y++) {
            for (int x = 0; x < width; x++, idx++) {
                r[idx] = -scale * (u.get(x + 1, y) - u.get(x, y) + v.get(x, y + 1) - v.get(x, y));
            }
        }
    }

    /* Performs the pressure solve using Gauss-Seidel.
     * The solver will run as long as it takes to get the relative error below
     * a threshold, but will never exceed `limit' iterations
     */
    private void project(int limit, double timestep) {
        double scale = timestep / (density * hx * hx);

        double maxDelta = 0;
        for (int iter = 0; iter < limit; iter++) {
            maxDelta = 0.0;
            for (int y = 0, idx = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    idx = x + y * width;

                    double diag = 0.0, offDiag = 0.0;

                    /* Here we build the matrix implicitly as the five-point
                     * stencil. Grid borders are assumed to be solid, i.e.
                     * there is no fluid outside the simulation domain.
                     */
                    if (x > 0) {
                        diag += scale;
                        offDiag -= scale * p[idx - 1];
                    }
                    if (y > 0) {
                        diag += scale;
                        offDiag -= scale * p[idx - width];
                    }
                    if (x < width - 1) {
                        diag += scale;
                        offDiag -= scale * p[idx + 1];
                    }
                    if (y < height - 1) {
                        diag += scale;
                        offDiag -= scale * p[idx + width];
                    }

                    double newP = (r[idx] - offDiag) / diag;

                    maxDelta = max(maxDelta, abs(p[idx] - newP));

                    p[idx] = newP;
                }
            }

            if (maxDelta < 1e-5) {
                System.out.printf("Exiting solver after %d iterations, maximum change is %f\n", iter, maxDelta);
                return;
            }
        }

        System.out.printf("Exceeded budget of %d iterations, maximum change was %f\n", limit, maxDelta);
    }

    /* Applies the computed pressure to the velocity field */
    private void applyPressure(double timestep) {
        double scale = timestep / (density * hx);

        for (int y = 0, idx = 0; y < height; y++) {
            for (int x = 0; x < width; x++, idx++) {
                u.set(x, y, u.get(x, y) - scale * p[idx]);
                u.set(x + 1, y, u.get(x + 1, y) + scale * p[idx]);
                v.set(x, y, v.get(x, y) - scale * p[idx]);
                v.set(x, y + 1, v.get(x, y + 1) + scale * p[idx]);
            }
        }

        for (int y = 0; y < height; y++) {
            u.set(0, y, 0);
            u.set(width, y, 0);
        }
        for (int x = 0; x < width; x++) {
            v.set(x, 0, 0);
            v.set(x, height, 0);
        }
    }

    void update(double timestep) {
        buildRhs();
        project(600, timestep);
        applyPressure(timestep);

        d.advect(timestep, u, v);
        u.advect(timestep, u, v);
        v.advect(timestep, u, v);

        /* Make effect of advection visible, since it's not an in-place operation */
        d.flip();
        u.flip();
        v.flip();
    }

    /* Set density and x/y velocity in given rectangle to d/u/v, respectively */
    void addInflow(double x, double y, double w, double h, double d, double u, double v) {
        this.d.addInflow(x, y, x + w, y + h, d);
        this.u.addInflow(x, y, x + w, y + h, u);
        this.v.addInflow(x, y, x + w, y + h, v);
    }

    /* Returns the maximum allowed timestep. Note that the actual timestep
     * taken should usually be much below this to ensure accurate
     * simulation - just never above.
     */
    double maxTimestep() {
        double maxVelocity = 0.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                /* Average velocity get grid cell center */
                double a = u.lerp(x + 0.5, y + 0.5);
                double b = v.lerp(x + 0.5, y + 0.5);

                double velocity = sqrt(a * a + b * b);
                maxVelocity = max(maxVelocity, velocity);
            }
        }

        /* Fluid should not flow more than two grid cells per iteration */
        double maxTimestep = 2.0 * hx / maxVelocity;

        /* Clamp to sensible maximum value in case of very small velocities */
        return min(maxTimestep, 1.0);
    }

    @Override
    public Byte provide(int x, int y) {
        int shade = (int) ((1.0 - d.get(x, y)) * 128.0);
        return (byte) (128 - shade);
    }
}
