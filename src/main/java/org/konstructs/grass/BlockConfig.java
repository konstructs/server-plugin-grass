package org.konstructs.grass;

import konstructs.api.Position;

public class BlockConfig {

    private int prefer_height;
    private float fuzzy;

    public BlockConfig(int prefer_height, float fuzzy) {
        this.prefer_height = prefer_height;
        this.fuzzy = fuzzy;
    }

    public int getPreferHeight() {
        return prefer_height;
    }

    public float inverseDistanceTo(Position pos) {
        float dist = 1.0f / ((float)Math.abs(getPreferHeight() - pos.getY()));
        return dist > fuzzy ? dist : 0;
    }

}
