/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphics;

public interface GridProvider<N extends Number> {

    /**
     * Provide a pixel at some coordinates
     * @param x
     * @param y
     * @return
     */
    N provide(int x, int y);

}
