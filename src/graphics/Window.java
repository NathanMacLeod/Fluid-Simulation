package graphics;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class Window extends JFrame {

    public Window(Dimension size) {
        super("Euler Fluid Simulator");
        setSize(size);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
    }

    public static BufferedImage render(Dimension imageSize, GridProvider pixelStream) {
        BufferedImage imageContainer = new BufferedImage(imageSize.width, imageSize.height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] buffer = ((DataBufferByte) imageContainer.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < imageContainer.getHeight(); y++)
            for (int x = 0; x < imageContainer.getWidth(); x++)
                buffer[y * imageSize.width + x] = pixelStream.provide(x, y).byteValue();

        return imageContainer;
    }
}
