package fluidsimulation;

import static fluidsimulation.FluidSimulation.SCALE_FACTOR;

class FluidSource {
    float forceX;
    float forceY;
    float density;
    int x;
    int y;

    FluidSource(int x, int y, float forceX, float forceY, float density) {
        this.x = (int) (x / SCALE_FACTOR);
        this.y = (int) (y / SCALE_FACTOR);
        this.forceX = forceX;
        this.forceY = forceY;
        this.density = density;
    }
}
