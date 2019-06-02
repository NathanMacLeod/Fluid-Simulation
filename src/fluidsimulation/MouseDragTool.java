package fluidsimulation;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.Math.hypot;

class MouseDragTool extends MouseAdapter {
    private float[] previousCoords;

    private final List<FluidSource> sourceQueue;

    private Instant startAdd;
    private boolean mouseHeld = false;

    MouseDragTool() {
        sourceQueue = new LinkedList<>();
        startAdd = Instant.now();
    }

    public void mousePressed(MouseEvent e) {
        previousCoords = new float[]{e.getX(), e.getY()};
        mouseHeld = true;
        startAdd = Instant.now();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        mouseHeld = false;
    }

    public synchronized void mouseDragged(MouseEvent e) {
        float velocityX = 0;
        float velocityY = 0;

        float dragScalar = 10f;

        if (previousCoords != null) {
            velocityX = dragScalar * (e.getX() - previousCoords[0]);
            velocityY = dragScalar * (e.getY() - previousCoords[1]);
        }

        startAdd = Instant.now();
        previousCoords = new float[]{e.getX(), e.getY()};
        sourceQueue.add(new FluidSource(e.getX(), e.getY() - 30, velocityX, velocityY, 6f * (float) hypot(velocityX, velocityY)));
    }

    synchronized void consumeSources(Consumer<FluidSource> sourceConsumer) {
        if (mouseHeld) {
            long timeHeld = Duration.between(startAdd, Instant.now()).toMillis();
            startAdd = Instant.now();
            sourceQueue.add(new FluidSource((int) previousCoords[0], (int) previousCoords[1] - 30, 0, 0, (float) timeHeld * 20f));
        }

        for (FluidSource source : sourceQueue)
            sourceConsumer.accept(source);
        sourceQueue.clear();
    }
}