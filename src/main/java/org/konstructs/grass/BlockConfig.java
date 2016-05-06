package org.konstructs.grass;

import konstructs.api.BlockTypeId;

public class BlockConfig {

    private int max;
    private int min;

    public BlockConfig(int min_height, int max_height) {
        this.min = min_height;
        this.max = max_height;
    }

    public int getMax() {
        return max;
    }

    public int getMin() {
        return min;
    }
}
