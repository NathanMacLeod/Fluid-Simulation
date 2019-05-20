/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fluidsimulation;

import graphics.Window;

import java.awt.*;

/**
 *
 * @author macle
 */
public class FluidSimulation {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Solver solver = new Solver(128, 128, 0.1);
        Window window = new Window(new Dimension(128, 128));

        while (true) {

            for (int i = 0; i < 4; i++) {
                solver.addInflow(0.45, 0.2, 0.1, 0.01, 1.0, 0.0, 3.0);
                solver.update(0.005);
            }

            window.render(solver);
        }
    }
    
}
