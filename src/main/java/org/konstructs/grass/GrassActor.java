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
    class CheckForDirtBlock {
        Position position;
        public CheckForDirtBlock(Position position) {
            this.position = position;
        }
    }

    private ArrayList<Position> dirtBlocksCandidate;
    private ArrayList<Position> dirtBlocksToGrow;

    /**
     * The superclass is provided by the API and contains
     * a few convenient methods. This class constructor
     * receives the value provided in props()
     */
    public GrassActor(ActorRef universe) {
        super(universe);
        dirtBlocksCandidate = new ArrayList<Position>();
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
            EventBlockUpdated block = (EventBlockUpdated)message;
            onEventBlockUpdated(block);
            return;
        }

        if (message instanceof ProcessDirtBlock) {
            processDirtBlock();
            return;
        }

        if (message instanceof BlockViewed) {
            BlockViewed block = (BlockViewed)message;
            onBlockViewed(block);
            return;
        }

        // Print out unhandled messages
        //System.out.println(getSelf().path() + " got and unhandled message: " + message);
    }

    /**
     * Events related to block placements/updates.
     *
     * @param blockEvent
     */
    @Override
    public void onEventBlockUpdated(EventBlockUpdated blockEvent) {

        BlockTypeId blockType = blockEvent.block().type();
        if (blockType.namespace().equals("org/konstructs") && blockType.name().equals("dirt")) {
            dirtBlocksCandidate.add(blockEvent.pos());
            askForBlockAt(blockEvent.pos(), 0, 1, 0); // ask server for above block.
        }

        Position under = new Position(
                blockEvent.pos().x(),
                blockEvent.pos().y() - 1,
                blockEvent.pos().z()
        );

        if (dirtBlocksCandidate.contains(under)) {
            dirtBlocksCandidate.remove(under);
        }
    }

    /**
     * This method picks a random item from the dirt block list and replaces
     * it with a grass block. It then queries universe for all surrounding blocks.
     */
    private void processDirtBlock() {
        if (dirtBlocksToGrow.size() > 0) {
            int pos = (int) (Math.random() * dirtBlocksToGrow.size());
            Position blockPos = dirtBlocksToGrow.get(pos);
            getUniverse().tell(
                    new ReplaceBlock(blockPos,
                            Block.create(new BlockTypeId("org/konstructs", "grass-dirt"))),
                    getSelf()
            );
            dirtBlocksToGrow.remove(pos);

            // Send a CheckForDirtBlock message to all surrounding blocks.
            for (int x = -1; x < 2; x++) {
                for (int y = -1; y < 2; y++) {
                    for (int z = -1; z < 2; z++) {
                        askForBlockAt(blockPos, x, y, z);
                    }
                }
            }
        }
        // Schedule another ProcessDirtBlock in 2 seconds
        scheduleSelfOnce(new ProcessDirtBlock(), 2000);

        if (dirtBlocksCandidate.size() > 1000 || dirtBlocksToGrow.size() > 5000) {
            System.out.println("STATS: grass candidates: " + dirtBlocksCandidate.size() + " grow: " + dirtBlocksToGrow.size());
        }
    }

    /**
     * Ask universe for a block at blockPos, with offsets.
     *
     * @param blockPos
     */
    private void askForBlockAt(Position blockPos, int x, int y, int z) {
        getUniverse().tell(
                new ViewBlock(
                        new Position(
                                blockPos.x() + x,
                                blockPos.y() + y,
                                blockPos.z() + z
                        )
                ), getSelf()
        );
    }

    /**
     * When we got the response from universe with a block, we check if it's a candidate
     * and updates the list.
     */
    public void onBlockViewed(BlockViewed block) {
        BlockTypeId blockType = block.block().type();
        if (blockType.namespace().equals("org/konstructs") && blockType.name().equals("vacuum")) {
            for (Position p : dirtBlocksCandidate) {
                if (p.x() == block.pos().x()) {
                    if (p.y() == block.pos().y() - 1) {
                        if (p.z() == block.pos().z()) {
                            dirtBlocksCandidate.remove(p);
                            dirtBlocksToGrow.add(p);
                            return;
                        }
                    }
                }
            }
        } else {
            onEventBlockUpdated(new EventBlockUpdated(block.pos(), block.block()));
        }
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
