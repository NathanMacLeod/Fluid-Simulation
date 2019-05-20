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
