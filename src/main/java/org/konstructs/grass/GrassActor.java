package org.konstructs.grass;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.typesafe.config.ConfigValue;
import konstructs.api.*;
import konstructs.api.messages.BlockUpdateEvent;
import konstructs.api.messages.BoxQueryResult;
import konstructs.api.messages.GlobalConfig;
import konstructs.api.messages.GetBlockFactory;
import konstructs.plugin.Config;
import konstructs.plugin.KonstructsActor;
import konstructs.plugin.PluginConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GrassActor extends KonstructsActor {

    // Message classes sent to the actor
    class ProcessDirtBlock {}

    private ArrayList<QueuedGrassBlock> dirtBlocksToGrow;
    private ArrayList<BlockTypeId> validGrassBlocks;
    private ArrayList<BlockTypeId> growsOn;
    private int change_rate;
    private float default_tick_speed;
    private HashMap<BlockTypeId, BlockConfig> blockConfig;
    private BlockFactory factory = null;
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
                      int change_rate,
                      int default_tick_speed) {
        super(universe);
        universe.tell(GetBlockFactory.MESSAGE, getSelf());

        dirtBlocksToGrow = new ArrayList<>();
        validGrassBlocks = new ArrayList<>();
        growsOn = new ArrayList<>();
        blockConfig = new HashMap<>();

        simulation_speed = 1;

        this.change_rate = change_rate;
        this.default_tick_speed = default_tick_speed;

        for (String k : config.root().keySet()) {
            if (config.getConfig(k) != null) {
                String validBlock = config.getConfig(k).getString("block-type");
                validGrassBlocks.add(BlockTypeId.fromString(validBlock));

                blockConfig.put(BlockTypeId.fromString(validBlock),
                        new BlockConfig(
                                config.getConfig(k).getInt("prefer-height"),
                                (float)config.getConfig(k).getDouble("transition-sharpness") / 100.0f,
                                BlockTypeId.fromString(config.getConfig(k).getString("block-type-under")),
                                config.getConfig(k).getInt("distance-multiplier")
                        )
                );
            }
        }

        for (Map.Entry<String, ConfigValue> e : grow.entrySet()) {
            growsOn.add(BlockTypeId.fromString((String)e.getValue().unwrapped()));
        }

        // Create a block filter used in growDirtBlock(..)
        blockFilter = BlockFilterFactory.NOTHING;
        for (BlockTypeId bt : growsOn) {
            blockFilter = blockFilter.or(BlockFilterFactory.withBlockTypeId(bt));
        }

        // Schedule a ProcessDirtBlock in 2 seconds
        scheduleSelfOnce(new ProcessDirtBlock(),
                (int)(this.default_tick_speed / simulation_speed));
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
        } else if(message instanceof BlockFactory) {
            factory = (BlockFactory)message;
        } else {
            super.onReceive(message); // Handle konstructs messages
        }
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
        BlockConfig blockConfigToGrow = blockConfig.get(blockIdToGrow);
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

                // Found transparent, skip and anything below is ok
                if (factory.getBlockType(typeId).isTransparent()) continue;

                // Found dirt, add to list and stop search
                if (growsOn.contains(typeId)) {
                    if (h < 3) { // Never allow the 1st layer, we requested it to check for vacuum
                        float local_change_rate = (float)(change_rate + blockConfigToGrow.distanceTo(start) * 10) / 10000.0f;
                        if (Math.random() < local_change_rate) {
                            // Get a new random grass block
                            blockIdToGrow = getRandomBlockTypeId(start, blockIdToGrow);
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

    private BlockTypeId getRandomBlockTypeId(Position p, BlockTypeId found) {

        // Calc total range
        float total_weight = 0.0f;
        for(Map.Entry<BlockTypeId, BlockConfig> m : blockConfig.entrySet()) {
            total_weight += m.getValue().inverseDistanceTo(p);
        }

        // Select a random position in the range
        float rval = (float)Math.random() * total_weight;

        float offc = 0.0f;
        for(Map.Entry<BlockTypeId, BlockConfig> m : blockConfig.entrySet()) {
            offc += m.getValue().inverseDistanceTo(p);
            if (rval < offc) {
                return m.getKey();
            }
        }

        return found;
    }

    /**
     * This method picks a random item from the dirt block list and replaces
     * it with a grass block.
     */
    private void processDirtBlock() {

        int process_num_blocks = Math.max(1, (int)(dirtBlocksToGrow.size() * 0.1));
        HashMap<Position, BlockTypeId> blocks = new HashMap<>();

        if (dirtBlocksToGrow.size() > process_num_blocks) {
            for (int i = process_num_blocks; i > 0; i--) {
                int pos = (int) (Math.random() * dirtBlocksToGrow.size());
                QueuedGrassBlock block = dirtBlocksToGrow.get(pos);

                // Put the new block
                blocks.put(block.getPosition(), block.getType());

                // Get and place blocks under the new block
                BlockConfig bc = blockConfig.get(block.getType());
                BlockTypeId blockUnder = bc.getBlockUnder();
                if(!growsOn.contains(blockUnder)) {
                    for (int t = 1; t < 7; t++) {
                        blocks.put(block.getPosition().subtractY(t), blockUnder);
                    }
                }

                for (Iterator<QueuedGrassBlock> it = dirtBlocksToGrow.iterator(); it.hasNext(); ) {
                    QueuedGrassBlock qblock = it.next();
                    if (qblock.getPosition().equals(block.getPosition())) {
                        it.remove();
                    }
                }
            }

        }

        replaceBlocks(blockFilter, blocks);
        scheduleSelfOnce(new ProcessDirtBlock(),
                (int)(this.default_tick_speed / simulation_speed));
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
                              @Config(key = "change-rate") int change_rate,
                              @Config(key = "default-tick-speed") int default_tick_speed){

        Class currentClass = new Object() { }.getClass().getEnclosingClass();
        return Props.create(currentClass, universe, types, grow, change_rate,
                            default_tick_speed);
    }
}
