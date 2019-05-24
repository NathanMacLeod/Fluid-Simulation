/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphics;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import fluidsimulation.FluidSimulation;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Window extends JFrame {

    private FluidSimulation simulation;
    private volatile BufferedImage buffer = null;

    private final Dimension imageSize;
    private float imageScaleFactor = 5;

    public Window(Dimension size, FluidSimulation simulation) {
        super("Euler Fluid Simulator");
        imageSize = size;
        this.simulation = simulation;
        setSize(size.width * 5, size.height * 5 + 30);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);
        addMouseFunctionality();
    }

    private void addMouseFunctionality() {
        this.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                simulation.applyForce((int)(e.getX()/imageScaleFactor), (int)(e.getY()/imageScaleFactor), 0f, 5.0f);
                simulation.addDensity((int)(e.getX()/imageScaleFactor), (int)(e.getY()/imageScaleFactor), 500);
            }
        });
    }
    
    @Override
    public synchronized void paint(Graphics g) {
        if (buffer == null)
        return;
        super.paint(g);

        g.drawImage(buffer, 0, 30, (int) (imageSize.width * imageScaleFactor), (int) (imageSize.height * imageScaleFactor), null);
    }

    public synchronized void render(GridProvider pixelStream) {
        BufferedImage imageContainer = new BufferedImage(imageSize.width, imageSize.height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] buffer = ((DataBufferByte) imageContainer.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < imageContainer.getHeight(); y++)
            for (int x = 0; x < imageContainer.getWidth(); x++)
                buffer[y * imageSize.width + x] = pixelStream.provide(x, y).byteValue();

        this.buffer = imageContainer;
        repaint();
    }
}

