package org.konstructs.grass;

import konstructs.api.Position;

public class BlockConfig {

    private int prefer_height;

    public BlockConfig(int prefer_height) {
        this.prefer_height = prefer_height;
    }

    public int getPreferHeight() {
        return prefer_height;
    }

    public int weightTo(Position pos) {
        return Math.max(getPreferHeight(), pos.getY()) -
                Math.min(getPreferHeight(), pos.getY());
    }
}
