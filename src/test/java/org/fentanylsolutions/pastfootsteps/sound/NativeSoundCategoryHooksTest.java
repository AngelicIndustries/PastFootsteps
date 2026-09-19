package org.fentanylsolutions.pastfootsteps.sound;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Cheap verification of the native bytecode/local capture points used by the category mixin. */
public class NativeSoundCategoryHooksTest {

    @Test
    public void categoryInsertionAndRemovalHaveTheExpectedSoundLocal() throws Exception {
        ClassNode owner = new ClassNode();
        try (InputStream stream = getClass().getResourceAsStream("/net/minecraft/client/audio/SoundManager.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(owner, 0);
        }
        MethodNode play = method(owner, "playSound");
        assertEquals("(Lnet/minecraft/client/audio/ISound;)V", play.desc);
        assertEquals(1, categories(play).size());
        MethodNode update = method(owner, "updateAllSounds");
        List<AbstractInsnNode> lookups = categories(update);
        assertEquals(2, lookups.size());
        int at = update.instructions.indexOf(lookups.get(1));
        long locals = update.localVariables.stream()
            .filter(local -> {
                LocalVariableNode value = (LocalVariableNode) local;
                return value.desc.equals("Lnet/minecraft/client/audio/ISound;")
                    && update.instructions.indexOf(value.start) <= at
                    && update.instructions.indexOf(value.end) > at;
            })
            .count();
        assertEquals("Cleanup injection must capture exactly one ISound local", 1, locals);
        assertEquals(
            "(Lnet/minecraft/client/audio/ISound;Lnet/minecraft/client/audio/SoundPoolEntry;Lnet/minecraft/client/audio/SoundCategory;)F",
            method(owner, "getNormalizedVolume").desc);
    }

    private static MethodNode method(ClassNode owner, String name) {
        for (MethodNode method : owner.methods) if (method.name.equals(name)) return method;
        throw new AssertionError("Missing sound-manager method " + name);
    }

    private static List<AbstractInsnNode> categories(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("net/minecraft/client/audio/SoundEventAccessorComposite")
                    && call.name.equals("getSoundCategory")) result.add(call);
            }
        }
        return result;
    }
}
