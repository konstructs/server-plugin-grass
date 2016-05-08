package org.konstructs.grass;

import konstructs.api.BlockTypeId;
import konstructs.api.Position;

public class BlockConfig {

    private int prefer_height;
    private float fuzzy;
    private BlockTypeId block_under;

    public BlockConfig(int prefer_height, float fuzzy, BlockTypeId block_under) {
        this.prefer_height = prefer_height;
        this.fuzzy = fuzzy;
        this.block_under = block_under;
    }

    public int getPreferHeight() {
        return prefer_height;
    }

    public BlockTypeId getBlockUnder() {
        return block_under;
    }

    public float inverseDistanceTo(Position pos) {
        float dist = 1.0f / ((float)Math.abs(getPreferHeight() - pos.getY()));
        return dist > fuzzy ? dist : 0;
    }

}
