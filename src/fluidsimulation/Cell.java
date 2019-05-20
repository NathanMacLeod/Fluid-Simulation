package fluidsimulation;

import static java.lang.Math.*;

public class Cell {

    /**
     * height
     */
    private final double cellSize;

    /**
     * width
     */
    private final int width;

    /**
     * height
     */
    private final int height;

    /**
     * _src
     */
    private double[] source;

    /**
     * _dst
     */
    private double[] destination;

    private double ox;
    private double oy;

    Cell(int width, int height, double ox, double oy, double cellSize) {
        this.width = width;
        this.height = height;
        this.ox = ox;
        this.oy = oy;
        this.cellSize = cellSize;

        source = new double[width * height];
        destination = new double[width * height];
    }

    /* Linear intERPolate on grid at coordinates (x, y).
     * Coordinates will be clamped to lie in simulation domain
     */
    private double lerp(double x, double y) {
        x = min(max(x - ox, 0.0), width - 1.001);
        y = min(max(y - oy, 0.0), height - 1.001);
        int ix = (int) x;
        int iy = (int) y;
        x -= ix;
        y -= iy;

        double x00 = at(ix, iy), x10 = at(ix + 1, iy);
        double x01 = at(ix, iy + 1), x11 = at(ix + 1, iy + 1);

        return lerp(lerp(x00, x10, x), lerp(x01, x11, x), y);
    }

    /**
     * Linear interpolation
     *
     * @param a start of interpolation
     * @param b end of interpolation
     * @param x value in range
     * @return linear interpolation
     */
    private static double lerp(double a, double b, double x) {
        return a + (b - a) * x;
    }

    // TODO: Simplify
    double at(int x, int y) {
        return source[x + width * y];
    }

    /* Advect grid in velocity field u, v with given timestep */
    void advect(double timestep, Cell u, Cell v) {
        for (int iy = 0, idx = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++, idx++) {
                double x = ix + ox;
                double y = iy + oy;

                double uVel = u.lerp(x, y) / cellSize;
                double vVel = v.lerp(x, y) / cellSize;

                x -= uVel * timestep;
                y -= vVel * timestep;

                /* Second component: Interpolate from grid */
                destination[idx] = lerp(x, y);
            }
        }
    }

    /* Sets fluid quantity inside the given rect to value `v' */
    void addInflow(double x0, double y0, double x1, double y1, double v) {
        int ix0 = (int)(x0/height - ox);
        int iy0 = (int)(y0/height - oy);
        int ix1 = (int)(x1/height - ox);
        int iy1 = (int)(y1/height - oy);

        for (int y = max(iy0, 0); y < min(iy1, height); y++)
            for (int x = max(ix0, 0); x < min(ix1, height); x++)
                if (abs(source[x + y*width]) < abs(v))
                    source[x + y*width] = v;
    }
    
    void flip() {
        double[] tmp = source;
        source = destination;
        destination = tmp;
    }
}
