package org.fentanylsolutions.pastfootsteps.sound;

import java.io.File;

import net.minecraft.client.resources.IResourceManager;

/** Public facade used by the movement and material solvers. */
public final class SoundEngine {

    public interface Sink extends AcousticEngine.Player {
    }

    private final AcousticEngine delegate;

    public SoundEngine(Sink sink) {
        delegate = new AcousticEngine(sink);
    }

    public void reload(IResourceManager manager, Iterable<String> acousticIds) {
        delegate.reload(manager, acousticIds);
    }

    /** Applies optional local acoustic files after resource-pack definitions. */
    public void reload(IResourceManager manager, Iterable<String> acousticIds, File directory) {
        delegate.reload(manager, acousticIds, directory);
    }

    public void load(String id, com.google.gson.JsonElement json) {
        delegate.load(id, json);
    }

    public boolean ready() {
        return delegate.ready();
    }

    public boolean canPlay(String material) {
        return delegate.has(material);
    }

    public boolean canPlay(String material, String event) {
        return delegate.has(material, event);
    }

    public void play(String material, String event, AcousticContext context) {
        delegate.play(material, event, context);
    }

    public void tick() {
        delegate.tick();
    }

    public void beginTick() {
        delegate.beginTick();
    }

    public void clear() {
        delegate.clear();
    }
}
