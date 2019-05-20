package fluidsimulation;

import static java.lang.Math.*;

public class Solver {

    private Cell d;
    private Cell u;
    private Cell v;

    private final int width;
    private final int height;

    /* Grid cell size and fluid density */
    double hx;
    double density;

    /* Arrays for: */
    double[] r; /* Right hand side of pressure solve */
    double[] p; /* Pressure solution */


    public Solver(int width, int height, double density) {
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
                r[idx] = -scale * (u.at(x + 1, y) - u.at(x, y) +
                        v.at(x, y + 1) - v.at(x, y));
            }
        }
    }

    /* Performs the pressure solve using Gauss-Seidel.
     * The solver will run as long as it takes to get the relative error below
     * a threshold, but will never exceed `limit' iterations
     */
    void project(int limit, double timestep) {
        double scale = timestep / (density * hx * hx);

        double maxDelta = 0;
        for (int iter = 0; iter < limit; iter++) {
            maxDelta = 0.0;
            for (int y = 0, idx = 0; y < height; y++) {
                for (int x = 0; x < width; x++, idx++) {
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
    void applyPressure(double timestep) {
        double scale = timestep / (density * hx);

        for (int y = 0, idx = 0; y < height; y++) {
            for (int x = 0; x < width; x++, idx++) {
                u.at(x, y) -= scale * p[idx];
                u.at(x + 1, y) += scale * p[idx];
                v.at(x, y) -= scale * p[idx];
                v.at(x, y + 1) += scale * p[idx];
            }
        }

        for (int y = 0; y < height; y++)
            u.at(0, y) = u -> at(width, y) = 0.0;
        for (int x = 0; x < width; x++)
            v -> at(x, 0) = v -> at(x, height) = 0.0;
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
                /* Average velocity at grid cell center */
                double u = u.lerp(x + 0.5, y + 0.5);
                double v = v.lerp(x + 0.5, y + 0.5);

                double velocity = sqrt(u * u + v * v);
                maxVelocity = max(maxVelocity, velocity);
            }
        }

        /* Fluid should not flow more than two grid cells per iteration */
        double maxTimestep = 2.0 * hx / maxVelocity;

        /* Clamp to sensible maximum value in case of very small velocities */
        return min(maxTimestep, 1.0);
    }
}
