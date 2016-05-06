package org.konstructs.grass;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.typesafe.config.ConfigValue;
import konstructs.api.*;
import konstructs.api.messages.BlockUpdateEvent;
import konstructs.api.messages.BoxQueryResult;
import konstructs.api.messages.GlobalConfig;
import konstructs.plugin.Config;
import konstructs.plugin.KonstructsActor;
import konstructs.plugin.PluginConstructor;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GrassActor extends KonstructsActor {

    // Message classes sent to the actor
    class ProcessDirtBlock {}

    private ArrayList<QueuedGrassBlock> dirtBlocksToGrow;
    private ArrayList<BlockTypeId> validGrassBlocks;
    private ArrayList<BlockTypeId> growsOn;
    private ArrayList<BlockTypeId> growsUnder;

    private BlockFilter blockFilter;

    private float simulation_speed;

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

        dirtBlocksToGrow = new ArrayList<>();
        validGrassBlocks = new ArrayList<>();
        growsOn = new ArrayList<>();
        growsUnder = new ArrayList<>();

        simulation_speed = 1;

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
        blockFilter = BlockFilterFactory.NOTHING;
        for (BlockTypeId bt : growsOn) {
            blockFilter = blockFilter.or(BlockFilterFactory.withBlockTypeId(bt));
        }

        // Schedule a ProcessDirtBlock in 2 seconds
        scheduleSelfOnce(new ProcessDirtBlock(), (int)(2000 / simulation_speed));
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

    /**
     * Set tick speed
     */
    @Override
    public void onGlobalConfig(GlobalConfig config) {
        simulation_speed = config.getSimulationSpeed();
    }

    /**
     * Events related to block placements/updates. We filter out grass and dirt
     * placements and triggers a boxQuery(..) lookup around the placed block.
     */
    @Override
    public void onBlockUpdateEvent(BlockUpdateEvent blockEvent) {
        for (Map.Entry<Position, BlockUpdate> block : blockEvent.getUpdatedBlocks().entrySet()) {
            BlockUpdate blockUpdate = block.getValue();
            BlockTypeId blockTypeId = blockUpdate.getAfter().getType();

            // If grass or dirt
            if (growsOn.contains(blockTypeId) || validGrassBlocks.contains(blockTypeId)) {
                Position from = block.getKey().subtract(new Position(1, 1, 1));
                Box reqBox = Box.createWithSize(from, new Position(3, 4, 3));
                boxQuery(reqBox);
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
        if (result.getBlocks().length != 3*4*3) {
            System.out.println("onBoxQueryResult: Error: Got a box with size " + result.getBlocks().length);
            return;
        }

        Position start = result.getBox().getFrom();
        BlockTypeId blockIdToGrow = result.getLocal(new Position(1, 1, 1)); // Get center block

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
                    BlockTypeId typeId = result.getLocal(new Position(x, h, y));
                    if (validGrassBlocks.contains(typeId)) {
                        dirtBlocksToGrow.add(new QueuedGrassBlock(
                                start.add(new Position(x, h, y)),
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

                BlockTypeId typeId = result.getLocal(new Position(x, h, y));

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
                                start.add(new Position(x, h, y)),
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

        int process_num_blocks = (int)(dirtBlocksToGrow.size() * 0.1 + 1 * simulation_speed);

        if (dirtBlocksToGrow.size() > process_num_blocks) {
            for (int i = process_num_blocks; i > 0; i--) {
                int pos = (int) (Math.random() * dirtBlocksToGrow.size());
                QueuedGrassBlock block = dirtBlocksToGrow.get(pos);
                growDirtBlock(block);

                for (Iterator<QueuedGrassBlock> it = dirtBlocksToGrow.iterator(); it.hasNext(); ) {
                    QueuedGrassBlock qblock = it.next();
                    if (qblock.getPosition().equals(block.getPosition())) {
                        it.remove();
                    }
                }
            }

        }

        // Schedule another ProcessDirtBlock in 0.5s - queue size seconds (min 1ms delay)
        int next_tick = Math.max(1, (int)(500000 / simulation_speed) - dirtBlocksToGrow.size() * 1000);

        getContext().system().scheduler().scheduleOnce(
                Duration.create(next_tick, TimeUnit.MICROSECONDS),
                getSelf(), new ProcessDirtBlock(), getContext().system().dispatcher(), null);
    }

    /**
     * Ready to grow a block (turn a dirt block to a grass block), use a BlockFilter
     * to make sure that it's a dirt block still there.
     */
    private void growDirtBlock(QueuedGrassBlock block) {
        Map<Position, BlockTypeId> blocks = new HashMap<>();
        blocks.put(block.getPosition(), block.getType());
        replaceBlocks(blockFilter, blocks);
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
