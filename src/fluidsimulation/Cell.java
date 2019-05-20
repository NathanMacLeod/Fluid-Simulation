package fluidsimulation;

import static java.lang.Math.*;

class Cell {

    private final double cellSize;
    private final int width;
    private final int height;

    double[][] source;
    private double[][] destination;

    private double xOffset;
    private double yOffset;

    Cell(int width, int height, double xOffset, double yOffset, double cellSize) {
        this.width = width;
        this.height = height;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.cellSize = cellSize;

        source = new double[width][height];
        destination = new double[width][height];
    }

    /* Linear intERPolate on grid get coordinates (x, y).
     * Coordinates will be clamped to lie in simulation domain
     */
    private double lerp(double x, double y) {
        x = min(max(x - xOffset, 0.0), width - 1.001);
        y = min(max(y - yOffset, 0.0), height - 1.001);
        int ix = (int) x;
        int iy = (int) y;
        x -= ix;
        y -= iy;

        double x00 = source[ix][ iy], x10 = source[ix + 1][iy];
        double x01 = source[ix][iy + 1], x11 = source[ix + 1][iy + 1];

        return lerp(lerp(x00, x10, x), lerp(x01, x11, x), y);
    }

    /**
     * Linear interpolation
     *
     * @param a start of interpolation
     * @param b end of interpolation
     * @param v value in range
     * @return linear interpolation
     */
    private static double lerp(double a, double b, double v) {
        return a + (b - a) * v;
    }

    /* Advect grid in velocity field u, v with given timestep */
    void advect(double timestep, Cell u, Cell v) {
        for (int iy = 0, idx = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++, idx++) {
                double x = ix + xOffset;
                double y = iy + yOffset;

                double xVel = u.lerp(x, y) / cellSize;
                double yVel = v.lerp(x, y) / cellSize;

                x -= xVel * timestep;
                y -= yVel * timestep;

                /* Second component: Interpolate from grid */
                destination[ix][iy] = lerp(x, y);
            }
        }
    }

    /**
     * Brings up the values in the region to the given value.
     * @param x x coordinate of the region
     * @param y y coordinate of the region
     * @param width width of region
     * @param height height of region
     * @param v value
     */
    void setRegion(double x, double y, double width, double height, double v) {
        int ix0 = (int) (x / cellSize - xOffset);
        int iy0 = (int) (y / cellSize - yOffset);
        int ix1 = (int) (width / cellSize - xOffset);
        int iy1 = (int) (height / cellSize - yOffset);

        for (int iy = max(iy0, 0); iy < min(iy1, this.height); iy++)
            for (int ix = max(ix0, 0); ix < min(ix1, this.height); ix++)
                if (abs(source[ix][iy]) < abs(v))
                    source[ix][iy] = v;
    }

    void flip() {
        double[][] tmp = source;
        source = destination;
        destination = tmp;
    }
}
