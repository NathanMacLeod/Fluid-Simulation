package fluidsimulation;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.Math.hypot;

class MouseDragTool extends MouseAdapter {
    private float[] previousCoords;

    private final List<FluidSource> sourceQueue;

    MouseDragTool() {
        sourceQueue = new LinkedList<>();
    }

    public void mousePressed(MouseEvent e) {
        previousCoords = new float[]{e.getX(), e.getY()};
    }

    public synchronized void mouseDragged(MouseEvent e) {
        float velocityX = 0;
        float velocityY = 0;

        float dragScalar = 10f;

        if (previousCoords != null) {
            velocityX = dragScalar * (e.getX() - previousCoords[0]);
            velocityY = dragScalar * (e.getY() - previousCoords[1]);
        }

        previousCoords = new float[]{e.getX(), e.getY()};
        sourceQueue.add(new FluidSource(e.getX(), e.getY(), velocityX, velocityY, 6f * (float) hypot(velocityX, velocityY)));
    }

    synchronized void consumeSources(Consumer<FluidSource> sourceConsumer) {
        for (FluidSource source : sourceQueue)
            sourceConsumer.accept(source);
        sourceQueue.clear();
    }
}