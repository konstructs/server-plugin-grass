# server-plugin-grass

This plugin manages grass on a server, basically it grows grass by replacing dirt blocks with grass blocks.

This is a relatively simple plugin so I will document the important part of this plugin if you like to fork it and make something of your own.

## Build system

We uses Gradle to build the project and download the needed dependences (like Konstructs API). There are plenty of resources how to use it on the web, but in it's simplest form a `./gradlew assemble` should output a jar that you can use.

## Configuration

reference.conf is the plugins main configuration file, it will be *merged* with the [global configuration](https://github.com/konstructs/server/blob/master/src/main/resources/reference.conf) file on start.

* [src/main/resources/reference.conf](https://github.com/konstructs/server-plugin-grass/blob/master/src/main/resources/reference.conf)

The layout is as follows:
```
konstructs {
  my/name/space {
    class = my.name.space.MyPluginClass
  }
}
```

`my/name/space` can be anything but try to keep it to the java package namespace to keep thing simple, and to avoid collisions.

## Java

Time for a little code, this is a simple example that prints out hello world on start.

```java
package my.name.space;

import akka.actor.ActorRef;
import akka.actor.Props;
import konstructs.plugin.KonstructsActor;
import konstructs.plugin.PluginConstructor;

public class MyPluginClass extends KonstructsActor {

    public MyPluginClass(ActorRef universe) {
        super(universe);
        System.out.println("Hello World!");
    }

    @PluginConstructor
    public static Props props(String pluginName, ActorRef universe) {
        Class currentClass = new Object() { }.getClass().getEnclosingClass();
        return Props.create(currentClass, universe);
    }
}
```

This plugin is of course much more complex that that, for more info see the plugins source, I have documented the code quite well.
