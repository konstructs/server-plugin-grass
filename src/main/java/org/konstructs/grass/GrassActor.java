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

        if (message instanceof EventBlockUpdated) {
            onEventBlockUpdated((EventBlockUpdated)message);
            return;
        }

        if (message instanceof BoxQueryResult) {
            onBoxQueryResult((BoxQueryResult)message);
            return;
        }

        if (message instanceof ProcessDirtBlock) {
            processDirtBlock();
            return;
        }

        // Print out unhandled messages
        //System.out.println(getSelf().path() + " got and unhandled message: " + message);
    }

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
                        new Position(
                                block.getKey().x() - 2,
                                block.getKey().y() - 2,
                                block.getKey().z() - 2
                        ),
                        new Position(
                                block.getKey().x() + 2,
                                block.getKey().y() + 2,
                                block.getKey().z() + 2
                        )
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
        for (int y=1; y <= 2; y++) {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    if (candidateToGrow(result, x, y, z)) {
                        dirtBlocksToGrow.add(getPositionFromResult(result, x, y, z));
                    }
                }
            }
        }
    }

    /**
     * This function validates candidates. A dirt block with a vacuum block above.
     */
    private boolean candidateToGrow(BoxQueryResult result, int x, int y, int z) {
        if (!getBlockTypeIdFromResult(result, x, y+1, z).equals(new BlockTypeId("org/konstructs", "vacuum")))
            return false;
        if (!getBlockTypeIdFromResult(result, x, y, z).equals(new BlockTypeId("org/konstructs", "dirt")))
            return false;
        return true;
    }

    /**
     * This function returns the BlockTypeId from the result.
     */
    private BlockTypeId getBlockTypeIdFromResult(BoxQueryResult result, int x, int y, int z) {
        return result.result().data().get(result.result().box().index(x, y, z));
    }

    /**
     * This function returns the blocks real world Position from the result.
     */
    private Position getPositionFromResult(BoxQueryResult result, int x, int y, int z) {
        return new Position(
                result.result().box().start().x() + x,
                result.result().box().start().y() + y,
                result.result().box().start().z() + z
        );
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

        // Schedule another ProcessDirtBlock in 4 to 30 seconds
        scheduleSelfOnce(new ProcessDirtBlock(), 4000 + (int)(Math.random() * 30000));
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
