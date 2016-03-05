package org.konstructs.grass;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.typesafe.config.ConfigValue;
import konstructs.api.*;
import konstructs.plugin.Config;
import konstructs.plugin.KonstructsActor;
import konstructs.plugin.PluginConstructor;

import java.util.ArrayList;
import java.util.Map;

public class GrassActor extends KonstructsActor {

    // Message classes sent to the actor
    class ProcessDirtBlock {}

    private ArrayList<QueuedGrassBlock> dirtBlocksToGrow;
    private ArrayList<BlockTypeId> validGrassBlocks;
    private ArrayList<BlockTypeId> growsOn;
    private ArrayList<BlockTypeId> growsUnder;

    private BlockFilter blockFilter;

    /**
     * The superclass is provided by the API and contains
     * a few convenient methods. This class constructor
     * receives the value provided in props()
     */
    public GrassActor(ActorRef universe,
                      com.typesafe.config.Config config,
                      com.typesafe.config.Config grow,
                      com.typesafe.config.Config under) {
        super(universe);

        dirtBlocksToGrow = new ArrayList<QueuedGrassBlock>();
        validGrassBlocks = new ArrayList<BlockTypeId>();
        growsOn = new ArrayList<BlockTypeId>();
        growsUnder = new ArrayList<BlockTypeId>();

        for (Map.Entry<String, ConfigValue> e : config.entrySet()) {
            validGrassBlocks.add(BlockTypeId.fromString((String)e.getValue().unwrapped()));
        }

        for (Map.Entry<String, ConfigValue> e : grow.entrySet()) {
            growsOn.add(BlockTypeId.fromString((String)e.getValue().unwrapped()));
        }

        for (Map.Entry<String, ConfigValue> e : under.entrySet()) {
            growsUnder.add(BlockTypeId.fromString((String)e.getValue().unwrapped()));
        }

        // Create a block filter used in growDirtBlock(..)
        blockFilter = BlockFilterFactory.empty();
        for (BlockTypeId bt : growsOn) {
            blockFilter = blockFilter.or(BlockFilterFactory.withBlockTypeId(bt));
        }

        // Schedule a ProcessDirtBlock in 2 seconds
        scheduleSelfOnce(new ProcessDirtBlock(), 2000);
    }

    /**
     * All messages to this actor are received here.
     *
     * @param message   A message from another actor
     */
    @Override
    public void onReceive(Object message) {

        if (message instanceof ProcessDirtBlock) {
            processDirtBlock();
            return;
        }

        super.onReceive(message); // Handle konstructs messages
    }

    @Override
    public void onEventBlockRemoved(EventBlockRemoved block) {}

    /**
     * Events related to block placements/updates. We filter out grass and dirt
     * placements and triggers a boxQuery(..) lookup around the placed block.
     */
    @Override
    public void onEventBlockUpdated(EventBlockUpdated blockEvent) {
        for (Map.Entry<Position, BlockTypeId> block : blockEvent.blocks().entrySet()) {

            BlockTypeId blockTypeId = block.getValue();

            // If grass or dirt
            if (growsOn.contains(blockTypeId) || validGrassBlocks.contains(blockTypeId)) {

                boxQuery(
                        block.getKey().dec(new Position(1, 1, 1)), // from
                        block.getKey().inc(new Position(2, 3, 2))  // until
                );
            }
        }
    }

    /**
     * This event is triggered when we get the result from a boxQuery(..).
     * We walk through the result data and looks for candidates to grow.
     */
    @Override
    public void onBoxQueryResult(BoxQueryResult result) {

        // Expect a 3x4x3 box
        if (result.result().data().size() != 3*4*3) {
            System.out.println("onBoxQueryResult: Error: Got a box with size " + result.result().data().size());
            return;
        }

        Position start = result.result().box().start();
        BlockTypeId blockIdToGrow = result.result().get(1, 1, 1); // Get center block

        int[] checkPos = {
                0, 1, // N corner
                1, 2, // E corner
                2, 1, // S corner
                1, 0  // W corner
        };

        // Center is dirt, look around for grass blocks
        if(growsOn.contains(blockIdToGrow)) {

            for (int i=0; i<checkPos.length; i+=2) {
                int x = checkPos[i];
                int y = checkPos[i + 1];

                // From top down
                for (int h = 3; h >= 0; h--) {
                    BlockTypeId typeId = result.result().get(x, h, y);
                    if (validGrassBlocks.contains(typeId)) {
                        dirtBlocksToGrow.add(new QueuedGrassBlock(
                                start.inc(new Position(x, h, y)),
                                typeId)
                        );
                    }
                }
            }

            return;
        }

        // Center is grass, look around for dirt blocks
        for (int i=0; i<checkPos.length; i+=2) {
            int x = checkPos[i];
            int y = checkPos[i+1];

            // From top down
            for(int h=3; h>=0; h--) {

                BlockTypeId typeId = result.result().get(x, h, y);

                // Found vacuum, continue
                if (growsUnder.contains(typeId)) continue;

                // Found dirt, add to list and stop search
                if (growsOn.contains(typeId)) {
                    if (h < 3) { // Never allow the 1st layer, we requested it to check for vacuum

                        if (Math.random() < 0.005) {
                            // Get a new random grass block
                            blockIdToGrow = getRandomBlockTypeId();
                        }

                        dirtBlocksToGrow.add(new QueuedGrassBlock(
                                start.inc(new Position(x, h, y)),
                                blockIdToGrow)
                        );
                    }
                }

                break; // Found non-dirt block, abort
            }
        }

    }

    private BlockTypeId getRandomBlockTypeId() {
        int pos = (int) (Math.random() * validGrassBlocks.size());
        return validGrassBlocks.get(pos);
    }

    /**
     * This method picks a random item from the dirt block list and replaces
     * it with a grass block.
     */
    private void processDirtBlock() {

        if (dirtBlocksToGrow.size() > 0) {
            int pos = (int) (Math.random() * dirtBlocksToGrow.size());
            growDirtBlock(dirtBlocksToGrow.get(pos));
            dirtBlocksToGrow.remove(pos);
        }

        // Schedule another ProcessDirtBlock in 0.5s - queue size seconds (min 10ms delay)
        int next_tick = Math.max(10, 500 - dirtBlocksToGrow.size());
        scheduleSelfOnce(new ProcessDirtBlock(), next_tick);
    }

    /**
     * Ready to grow a block (turn a dirt block to a grass block), use a BlockFilter
     * to make sure that it's a dirt block still there.
     */
    private void growDirtBlock(QueuedGrassBlock block) {
        replaceBlock(block.getPosition(), block.getType(), blockFilter);
    }

    /**
     * This is the plugin constructor called by the actor system to register the
     * actor. This configures what type of data are provided to this actor.
     * The configuration key "types" is stored in reference.conf
     *
     * @param pluginName        The name of our plugin
     * @param universe          A reference to the universe actor.
     */
    @PluginConstructor
    public static Props props(String pluginName,
                              ActorRef universe,
                              @Config(key = "types") com.typesafe.config.Config types,
                              @Config(key = "grows-on") com.typesafe.config.Config grow,
                              @Config(key = "grows-under") com.typesafe.config.Config under){

        Class currentClass = new Object() { }.getClass().getEnclosingClass();
        return Props.create(currentClass, universe, types, grow, under);
    }
}
