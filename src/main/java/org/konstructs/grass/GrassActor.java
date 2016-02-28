package org.konstructs.grass;

import akka.actor.ActorRef;
import akka.actor.Props;
import konstructs.api.*;
import konstructs.plugin.KonstructsActor;
import konstructs.plugin.PluginConstructor;

import java.util.ArrayList;
import java.util.Map;

public class GrassActor extends KonstructsActor {

    // Message classes sent to the actor
    class ProcessDirtBlock {}

    private ArrayList<Position> dirtBlocksToGrow;

    /**
     * The superclass is provided by the API and contains
     * a few convenient methods. This class constructor
     * receives the value provided in props()
     */
    public GrassActor(ActorRef universe) {
        super(universe);
        dirtBlocksToGrow = new ArrayList<Position>();

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
     * Events related to block placements/updates. We filter out grass placements
     * and triggers a boxQuery(..) lookup around the placed block.
     */
    @Override
    public void onEventBlockUpdated(EventBlockUpdated blockEvent) {
        for (Map.Entry<Position, BlockTypeId> block : blockEvent.blocks().entrySet()) {
            if (!block.getValue().namespace().equals("org/konstructs"))
                return;

            if (block.getValue().name().equals("grass-dirt")) {
                boxQuery(
                        block.getKey().dec(new Position(1, 10, 1)),
                        block.getKey().inc(new Position(2, 10, 2))
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

        Map<Position, BlockTypeId> placed = result.result().toPlaced();

        for(Map.Entry<Position, BlockTypeId> p: placed.entrySet()) {

            BlockTypeId top = placed.get(p.getKey().incY(1));
            if (top == null || !top.equals(BlockTypeId.vacuum()))
                continue;
            if (!p.getValue().equals(new BlockTypeId("org/konstructs", "dirt")))
                continue;

            dirtBlocksToGrow.add(p.getKey());
        }

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

        // Schedule another ProcessDirtBlock in 2s - queue size seconds (min 10ms delay)
        int next_tick = Math.max(10, 2000 - dirtBlocksToGrow.size());
        scheduleSelfOnce(new ProcessDirtBlock(), next_tick);
    }

    /**
     * Ready to grow a block (turn a dirt block to a grass block), use a BlockFilter
     * to make sure that it's a dirt block still there.
     */
    private void growDirtBlock(Position pos) {
        replaceBlock(pos,
                new BlockTypeId("org/konstructs", "grass-dirt"),
                BlockFilterFactory
                        .withNamespace("org/konstructs")
                        .withName("dirt")
        );
    }

    /**
     * This is the plugin constructor called by the actor system to register the
     * actor. This configures what type of data are provided to this actor. In this
     * case only the default parameters are provided, the plugin name and universe.
     *
     * @param pluginName        The name of our plugin
     * @param universe          A reference to the universe actor.
     */
    @PluginConstructor
    public static Props props(String pluginName, ActorRef universe) {
        Class currentClass = new Object() { }.getClass().getEnclosingClass();
        return Props.create(currentClass, universe);
    }
}
