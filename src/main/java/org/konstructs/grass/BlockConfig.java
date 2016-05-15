package org.konstructs.grass;

import konstructs.api.BlockTypeId;
import konstructs.api.Position;

public class BlockConfig {

    private final int prefer_height;
    private final float fuzzy;
    private final BlockTypeId block_under;
    private final int distance_multiplier;

    public BlockConfig(int prefer_height, float fuzzy, BlockTypeId block_under, int distance_multiplier) {
        this.prefer_height = prefer_height;
        this.fuzzy = fuzzy;
        this.block_under = block_under;
        this.distance_multiplier = distance_multiplier;
    }

    public int getPreferHeight() {
        return prefer_height;
    }

    public BlockTypeId getBlockUnder() {
        return block_under;
    }

    public int getDistanceMultiplier() {
        return distance_multiplier;
    }

    public int distanceTo(Position pos) {
        return Math.abs(getPreferHeight() - pos.getY());
    }

    public float inverseDistanceTo(Position pos) {
        float dist = 1.0f / (float)distanceTo(pos);
        return dist > fuzzy ? dist : 0;
    }

}
