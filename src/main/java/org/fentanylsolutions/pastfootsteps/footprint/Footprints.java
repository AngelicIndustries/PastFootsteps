package org.fentanylsolutions.pastfootsteps.footprint;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Iterator;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.fentanylsolutions.pastfootsteps.client.ResolvedContact;
import org.fentanylsolutions.pastfootsteps.config.Config;
import org.fentanylsolutions.pastfootsteps.movement.ContactKind;
import org.fentanylsolutions.pastfootsteps.world.SurfaceResolver.Surface;
import org.lwjgl.opengl.GL11;

/** PF contact consumer. It owns visual lifetime only, never distance, stride or foot alternation. */
public final class Footprints {

    private static final ResourceLocation TEXTURE = new ResourceLocation("pastfootsteps", "textures/footprints.png");
    private final FootprintProfiles profiles = new FootprintProfiles();
    private final ArrayDeque<Print> prints = new ArrayDeque<>();
    private int tick;
    private World owner;

    public void reload(IResourceManager manager, File directory) {
        reset();
        profiles.reload(manager, directory);
    }

    public void reset() {
        prints.clear();
        tick = 0;
        owner = null;
    }

    public int count() {
        return prints.size();
    }

    public void contact(ResolvedContact resolved) {
        Surface surface = resolved.surface;
        ContactKind kind = resolved.contact.kind;
        if (!FootprintSettings.enabled || FootprintSettings.maximum == 0
            || !surface.grounded
            || kind == ContactKind.CLIMB
            || kind == ContactKind.SWIM
            || kind == ContactKind.JUMP
            || kind == ContactKind.STAND
            || kind == ContactKind.SHUFFLE
            || resolved.actor.isInWater()
            || surface.supportEntity != null) return;
        FootprintProfiles.ActorRule actor = profiles.actor(
            resolved.actor instanceof EntityPlayer ? "Player"
                : net.minecraft.entity.EntityList.getEntityString(resolved.actor),
            resolved.actor.isChild());
        if (actor == null || !actor.enabled
            || !DecalGeometry.select(
                resolved.actor.getUniqueID()
                    .getLeastSignificantBits(),
                resolved.contact.sequence,
                FootprintSettings.density * actor.density))
            return;
        World world = resolved.actor.worldObj;
        if (owner != world) {
            reset();
            owner = world;
        }
        if (!world.blockExists(surface.x, surface.y, surface.z)) return;
        Block block = world.getBlock(surface.x, surface.y, surface.z);
        int meta = world.getBlockMetadata(surface.x, surface.y, surface.z);
        String name = (String) Block.blockRegistry.getNameForObject(block);
        FootprintProfiles.SurfaceRule rule = profiles.surface(
            name,
            meta,
            block.stepSound == null ? ""
                : block.stepSound.getStepResourcePath()
                    .replace("step.", ""));
        if (rule == null || !rule.enabled) return;
        double scale = Math.min(4, resolved.actor.width) * FootprintSettings.size;
        double yaw = Math.toRadians(
            resolved.actor instanceof EntityPlayer ? resolved.actor.rotationYaw : resolved.actor.renderYawOffset);
        DecalGeometry.Vertex[] polygon = DecalGeometry.clipped(
            surface.contactX,
            surface.contactZ,
            yaw,
            actor.width * scale,
            actor.length * scale,
            surface.minX,
            surface.maxX,
            surface.minZ,
            surface.maxZ);
        if (polygon.length < 3) return;
        float opacity = (float) (rule.opacity < 0 ? FootprintSettings.opacity : rule.opacity);
        if (opacity <= 0) return;
        int life = rule.lifetime < 0 ? FootprintSettings.lifetime : rule.lifetime;
        if (resolved.rainExposed) life = Math.max(1, (int) (life * FootprintSettings.rainLifetime));
        else if (resolved.snowExposed) life = Math.max(1, (int) (life * FootprintSettings.snowLifetime));
        while (prints.size() >= FootprintSettings.maximum) prints.removeFirst();
        prints.addLast(
            new Print(
                surface,
                block,
                meta,
                polygon,
                tick,
                life,
                opacity,
                actor.style.ordinal(),
                resolved.contact.foot.lateral < 0));
    }

    public void tick(World world) {
        tick++;
        if (!Config.enabled || !FootprintSettings.enabled || world == null) {
            reset();
            return;
        }
        Iterator<Print> iterator = prints.iterator();
        while (iterator.hasNext()) {
            Print print = iterator.next();
            // Stagger substrate validation. No chunk loads and no block-bound mutation while rendering.
            if (tick - print.born >= print.life || prints.size() > FootprintSettings.maximum
                || ((tick + print.born) & 15) == 0 && (!world.blockExists(print.x, print.y, print.z)
                    || world.getBlock(print.x, print.y, print.z) != print.block
                    || world.getBlockMetadata(print.x, print.y, print.z) != print.meta))
                iterator.remove();
        }
    }

    public void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;
        if (!Config.enabled || !FootprintSettings.enabled
            || prints.isEmpty()
            || camera == null
            || owner != mc.theWorld
            || mc.theWorld == null) return;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            mc.getTextureManager()
                .bindTexture(TEXTURE);
            mc.entityRenderer.enableLightmap(partialTicks);
            Tessellator tess = Tessellator.instance;
            tess.startDrawing(GL11.GL_TRIANGLES);
            for (Print print : prints) {
                double dx = print.centerX - x, dz = print.centerZ - z, dy = print.height - y;
                if (dx * dx + dy * dy + dz * dz > FootprintSettings.distance * FootprintSettings.distance
                    || !mc.theWorld.blockExists(print.x, print.y, print.z)) continue;
                float age = tick - print.born + partialTicks;
                float fade = Math.min(1, Math.max(0, (print.life - age) / Math.max(1, print.life * 0.3f)));
                // Each print carries its support cell's brightness rather than one sample for the complete batch.
                tess.setBrightness(
                    mc.theWorld
                        .getLightBrightnessForSkyBlocks(print.x, (int) Math.floor(print.height + 0.01), print.z, 0));
                tess.setColorRGBA_F(1, 1, 1, print.opacity * fade);
                tess.setNormal(0, 1, 0);
                for (int i = 1; i + 1 < print.polygon.length; i++) {
                    vertex(tess, print, print.polygon[0], x, y, z);
                    vertex(tess, print, print.polygon[i], x, y, z);
                    vertex(tess, print, print.polygon[i + 1], x, y, z);
                }
            }
            tess.draw();
        } finally {
            mc.entityRenderer.disableLightmap(partialTicks);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GL11.glPopAttrib();
        }
    }

    private static void vertex(Tessellator tess, Print print, DecalGeometry.Vertex point, double x, double y,
        double z) {
        // The original atlas starts each style one pixel after its nominal 32-pixel boundary.
        // Inset half a pixel within that shifted cell to avoid sampling the neighboring foot/style.
        double u = (print.style * 32 + (print.right ? 16 : 0) + 1.5 + point.u * 15) / 256;
        double v = (0.5 + point.v * 31) / 32;
        tess.addVertexWithUV(point.x - x, print.height + 0.002 - y, point.z - z, u, v);
    }

    private static final class Print {

        final int x, y, z, meta, born, life, style;
        final Block block;
        final double centerX, centerZ, height;
        final float opacity;
        final boolean right;
        final DecalGeometry.Vertex[] polygon;

        Print(Surface surface, Block block, int meta, DecalGeometry.Vertex[] polygon, int born, int life, float opacity,
            int style, boolean right) {
            x = surface.x;
            y = surface.y;
            z = surface.z;
            centerX = surface.contactX;
            centerZ = surface.contactZ;
            height = surface.decalY;
            this.block = block;
            this.meta = meta;
            this.polygon = polygon;
            this.born = born;
            this.life = life;
            this.opacity = opacity;
            this.style = style;
            this.right = right;
        }
    }
}
