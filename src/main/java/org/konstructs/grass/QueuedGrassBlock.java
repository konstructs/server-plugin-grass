package org.konstructs.grass;

import konstructs.api.BlockTypeId;
import konstructs.api.Position;

/**
 * This is a grass block with position and type
 */
public class QueuedGrassBlock {
    private Position position;
    private BlockTypeId type;

    public QueuedGrassBlock(Position position, BlockTypeId type) {
        this.position = position;
        this.type = type;
    }

    public Position getPosition() {
        return position;
    }

    public BlockTypeId getType() {
        return type;
    }
}
