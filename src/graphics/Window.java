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

public class Window extends JFrame {

    private volatile BufferedImage buffer = null;

    private final Dimension imageSize;
    private float scale;

    public Window(Dimension size, float scale) {
        super("Euler Fluid Simulator");
        this.scale = scale;
        imageSize = size;
        setSize(size.width * 5, size.height * 5 + 30);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);
    }

    @Override
    public synchronized void paint(Graphics g) {
        if (buffer == null)
            return;

        g.drawImage(buffer, 0, 30, (int) (imageSize.width * scale), (int) (imageSize.height * scale), null);
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

