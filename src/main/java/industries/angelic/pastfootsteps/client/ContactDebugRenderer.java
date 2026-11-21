package industries.angelic.pastfootsteps.client;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import industries.angelic.pastfootsteps.config.Config;

/** Bounded, opt-in contact markers. These use the same resolved points as footstep audio. */
final class ContactDebugRenderer {

    private final Deque<Marker> markers = new ArrayDeque<>();
    private int ticks;

    void clear() {
        markers.clear();
        ticks = 0;
    }

    void tick() {
        ticks++;
        if (!Config.debugContacts) markers.clear();
        while (!markers.isEmpty() && ticks - markers.peekFirst().tick >= 20) markers.removeFirst();
    }

    void add(ResolvedContact resolved) {
        if (!Config.debugContacts) return;
        if (markers.size() >= 128) markers.removeFirst();
        markers.addLast(new Marker(resolved, ticks));
    }

    void render(float partialTicks) {
        Entity camera = Minecraft.getMinecraft().renderViewEntity;
        if (!Config.enabled || !Config.debugContacts || camera == null || markers.isEmpty()) return;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-x, -y, -z);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_LINES);
            for (Marker marker : markers) {
                float alpha = 1 - (ticks - marker.tick + partialTicks) / 20;
                tessellator.setColorRGBA_F(
                    marker.right ? 1 : 0.1f,
                    marker.right ? 0.45f : 0.9f,
                    marker.right ? 0.1f : 1,
                    Math.max(0, alpha));
                double atY = marker.y + 0.035;
                tessellator.addVertex(marker.x - 0.08, atY, marker.z);
                tessellator.addVertex(marker.x + 0.08, atY, marker.z);
                tessellator.addVertex(marker.x, atY, marker.z - 0.08);
                tessellator.addVertex(marker.x, atY, marker.z + 0.08);
                tessellator.addVertex(marker.x, atY, marker.z);
                tessellator.addVertex(marker.x, atY + 0.1, marker.z);
            }
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static final class Marker {

        final double x, y, z;
        final boolean right;
        final int tick;

        Marker(ResolvedContact resolved, int tick) {
            x = resolved.surface.contactX;
            y = resolved.surface.contactY;
            z = resolved.surface.contactZ;
            right = resolved.contact.foot.footId.contains("right");
            this.tick = tick;
        }
    }
}
