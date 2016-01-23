package org.konstructs.grass;

import akka.actor.ActorRef;
import akka.actor.Props;
import konstructs.api.*;
import konstructs.plugin.KonstructsActor;
import konstructs.plugin.PluginConstructor;

import java.util.ArrayList;

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
     * Events related to block placements/updates.
     */
    @Override
    public void onEventBlockUpdated(EventBlockUpdated blockEvent) {
        if (blockEvent.block().type().fullName().equals("org/konstructs/dirt")) {
            boxQuery(
                    new Position(
                            blockEvent.pos().x() - 2,
                            blockEvent.pos().y() - 2,
                            blockEvent.pos().z() - 2
                    ),
                    new Position(
                            blockEvent.pos().x() + 2,
                            blockEvent.pos().y() + 2,
                            blockEvent.pos().z() + 2
                    )
            );
        }
    }

    @Override
    public void onBoxQueryResult(BoxQueryResult result) {

        // Make sure that the center block is dirt
        if(!getBlockTypeIdFromResult(result, 0, 0, 0).fullName().equals("org/konstructs/dirt")) {
            System.out.println("[Grass] Error, the center block is not dirt, abort.");
            System.out.println("[Grass] I got a " + getBlockTypeIdFromResult(result, 0, 0, 0).fullName());
            Position pos = getPositionFromResult(result, 0, 0, 0);
            System.out.println("[Grass] I have marked the block with a brick at " + pos);
            putBlock(pos, Block.create(new BlockTypeId("org/konstructs", "brick")));

            return;
        }

        // Check if center is a candidate, check for air vacuum center
        if (getBlockTypeIdFromResult(result, 0, 1, 0).fullName().equals("org/konstructs/vacuum")) {
            dirtBlocksToGrow.add(getPositionFromResult(result, 0, 1, 0));
        }

        // The x side
        boolean vacuumAbove = false;
        for (int h = 1; h > -1; h--) {
            if (getBlockTypeIdFromResult(result, 1, h, 0).fullName().equals("org/konstructs/vacuum")) {
                vacuumAbove = true;
            } else {
                if (vacuumAbove && getBlockTypeIdFromResult(result, 1, h, 0).fullName().equals("org/konstructs/dirt")) {
                    Position pos = getPositionFromResult(result, 1, h, 0);
                    putBlock(pos, Block.create(new BlockTypeId("org/konstructs", "brick")));
                }
                vacuumAbove = false;
            }
        }


    }

    private BlockTypeId getBlockTypeIdFromResult(BoxQueryResult result, int x, int y, int z) {
        return result.result().data().get(result.result().box().index(x + 2, y + 2, z + 2));
    }

    private Position getPositionFromResult(BoxQueryResult result, int x, int y, int z) {

        Position middle = new Position(
                result.result().box().start().x() + 2,
                result.result().box().start().y() + 2,
                result.result().box().start().z() + 2
        );

        return new Position(
                middle.x() + x,
                middle.y() + y,
                middle.z() + z
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

        // Schedule another ProcessDirtBlock in 2 seconds
        scheduleSelfOnce(new ProcessDirtBlock(), 2000);
    }

    /**
     * Ready to grow a block (turn a dirt block to a grass block), use the ReplaceBlockIf
     * massage to make sure the location still contains a dirt block. If this fails, we will
     * get a message back, for our use case that do not matter so we will ignore it.
     */
    private void growDirtBlock(Position pos) {
        Block target_type = Block.create(new BlockTypeId("org/konstructs", "grass-dirt"));
        Block source_type = Block.create(new BlockTypeId("org/konstructs", "dirt"));
        getUniverse().tell(new ReplaceBlockIf(pos, target_type, source_type), getSelf());
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
