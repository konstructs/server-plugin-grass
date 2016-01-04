package org.konstructs.grass;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActorWithStash;
import konstructs.plugin.PluginConstructor;

public class GrassActor extends UntypedActorWithStash {

    ActorRef universe;

    public GrassActor(ActorRef universe) {
        this.universe = universe;
        System.out.println("Grass actor loaded");
    }

    public void onReceive(Object message) {
        System.out.println("got message: " + message);
    }

    @PluginConstructor
    public static Props props(String pluginName, ActorRef universe) {
        return Props.create(GrassActor.class, universe);
    }
}
