package org.fentanylsolutions.pastfootsteps.world;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.material.Material;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.fentanylsolutions.pastfootsteps.api.BlockAppearanceProviders;
import org.fentanylsolutions.pastfootsteps.api.BlockMaterial;
import org.fentanylsolutions.pastfootsteps.api.SurfaceSupportProviders;

/** Collision-aware substrate selection adapted from the MIT Presence Footsteps PFSolver. */
public final class SurfaceResolver {

    private static final double SUPPORT_HORIZONTAL_REACH = 0.35D;
    private static final double SUPPORT_BELOW_REACH = 0.12D;
    private static final double SUPPORT_ABOVE_REACH = 0.05D;
    private static final int FOLIAGE_HORIZONTAL_SAMPLES = 3;
    private static final int FOLIAGE_VERTICAL_SAMPLES = 5;
    private static final int MAX_SUPPORT_CANDIDATES = 16;
    private static final double OVERLAY_EPSILON = 0.001D;
    private static final double MAX_THIN_OVERLAY_HEIGHT = 0.3D;

    private final MaterialLibrary materials;
    private final SupportProfiles supports = new SupportProfiles();
    private final List<AxisAlignedBB> collisionBoxes = new ArrayList<AxisAlignedBB>(8);

    public SurfaceResolver(MaterialLibrary materials) {
        this.materials = materials;
    }

    /** Reloads exact built-in and local entity support mappings. */
    public void reloadSupports(IResourceManager manager, File directory) {
        supports.reload(manager, directory);
    }

    /** Compatibility overload retaining the original alternating player-foot placement. */
    public Surface resolve(EntityLivingBase actor, boolean rightFoot) {
        if (actor == null) return null;
        double lateralOffset = Math.min(0.2D, Math.max(0.0D, actor.width * 0.33D));
        return resolve(actor, rightFoot ? -lateralOffset : lateralOffset, 0.0D);
    }

    /**
     * Resolves a contact relative to the actor's body yaw. Positive lateral offsets are to the
     * actor's left and positive forward offsets are in the facing direction.
     */
    public Surface resolve(EntityLivingBase actor, double lateralOffset, double forwardOffset) {
        if (actor == null || actor.boundingBox == null) return null;
        return resolveAt(actor, actor.posX, actor.boundingBox.minY, actor.posZ, lateralOffset, forwardOffset);
    }

    /** Bounded, loaded-chunk-only lava check for movement state sampling. */
    public boolean inLava(EntityLivingBase actor) {
        if (actor == null || actor.worldObj == null
            || actor.boundingBox == null
            || !validWorldCoordinate(actor.posX)
            || !validWorldCoordinate(actor.posZ)
            || !validWorldCoordinate(actor.boundingBox.minY)) return false;
        AxisAlignedBB probe = contactProbe(actor.posX, actor.boundingBox.minY, actor.posZ);
        return containsMaterial(actor.worldObj, probe, Material.lava);
    }

    /** Resolves a contact around an explicit, possibly interpolated, actor position. */
    public Surface resolveAt(EntityLivingBase actor, double centerX, double feetY, double centerZ, double lateralOffset,
        double forwardOffset) {
        return resolveAt(actor, centerX, feetY, centerZ, lateralOffset, forwardOffset, "");
    }

    /** Resolves a contact using the material map selected by the actor's profile. */
    public Surface resolveAt(EntityLivingBase actor, double centerX, double feetY, double centerZ, double lateralOffset,
        double forwardOffset, String materialMap) {
        if (actor == null || actor.worldObj == null
            || actor.boundingBox == null
            || !finite(centerX)
            || !validWorldCoordinate(feetY)
            || !finite(centerZ)
            || !finite(lateralOffset)
            || !finite(forwardOffset)) return null;

        double angle = Math.toRadians(actor instanceof EntityPlayer ? actor.rotationYaw : actor.renderYawOffset);
        double footX = centerX + Math.cos(angle) * lateralOffset - Math.sin(angle) * forwardOffset;
        double footZ = centerZ + Math.sin(angle) * lateralOffset + Math.cos(angle) * forwardOffset;
        if (!validWorldCoordinate(footX) || !validWorldCoordinate(footZ)) return null;

        World world = actor.worldObj;
        AxisAlignedBB probe = contactProbe(footX, feetY, footZ);
        Surface best = null;
        double bestScore = Double.MAX_VALUE;
        int minX = floor(probe.minX), maxX = floor(probe.maxX);
        int minZ = floor(probe.minZ), maxZ = floor(probe.maxZ);
        int minY = floor(feetY) - 2, maxY = floor(feetY);
        boolean waterAtContact = containsMaterial(world, probe, Material.water);
        boolean submerged = actor.isInsideOfMaterial(Material.water);

        // The horizontal window is always smaller than one block, and the vertical range is always
        // three rows. Cost therefore stays bounded even for entities with enormous bounding boxes.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!world.blockExists(x, y, z)) continue;
                    Block block = world.getBlock(x, y, z);
                    collisionBoxes.clear();
                    float oldMinX = (float) block.getBlockBoundsMinX(), oldMinY = (float) block.getBlockBoundsMinY(),
                        oldMinZ = (float) block.getBlockBoundsMinZ(), oldMaxX = (float) block.getBlockBoundsMaxX(),
                        oldMaxY = (float) block.getBlockBoundsMaxY(), oldMaxZ = (float) block.getBlockBoundsMaxZ();
                    try {
                        block.addCollisionBoxesToList(world, x, y, z, probe, collisionBoxes, actor);
                    } catch (RuntimeException ignored) {
                        // A broken modded collision provider should not prevent native sound fallback.
                        continue;
                    } finally {
                        block.setBlockBounds(oldMinX, oldMinY, oldMinZ, oldMaxX, oldMaxY, oldMaxZ);
                    }
                    for (AxisAlignedBB box : collisionBoxes) {
                        if (box == null || !finite(box.minX)
                            || !finite(box.maxX)
                            || !finite(box.minZ)
                            || !finite(box.maxZ)
                            || !finite(box.maxY)
                            || box.minX > box.maxX
                            || box.minZ > box.maxZ) continue;
                        double verticalOffset = box.maxY - feetY;
                        if (verticalOffset < -SUPPORT_BELOW_REACH || verticalOffset > SUPPORT_ABOVE_REACH) continue;
                        double verticalDistance = Math.abs(verticalOffset);
                        double contactX = clamp(footX, box.minX, box.maxX);
                        double contactZ = clamp(footZ, box.minZ, box.maxZ);
                        double dx = contactX - footX;
                        double dz = contactZ - footZ;
                        double score = dx * dx + dz * dz + verticalDistance;
                        if (score >= bestScore) continue;

                        int meta = world.getBlockMetadata(x, y, z);
                        BlockMaterial appearance = BlockAppearanceProviders.resolve(world, x, y, z, block, meta);
                        MaterialLibrary.Association association = materials
                            .resolve(appearance.block, appearance.metadata, "carpet", materialMap);
                        if (!emitter(association.name)) {
                            association = materials.resolve(
                                appearance.block,
                                appearance.metadata,
                                box.maxY > y + 1.01D ? "bigger" : "",
                                materialMap);
                            if (association.name == null)
                                association = materials.resolve(appearance.block, appearance.metadata, "", materialMap);
                        }
                        String name = association.name;
                        String wet = materials.lookup(appearance.block, appearance.metadata, "wet", materialMap);
                        if (wet == null) wet = "mudfine,splash";
                        boolean exposed = !submerged && world.isRaining()
                            && world.canBlockSeeTheSky(x, floor(feetY + 0.1D), z)
                            && world.getBiomeGenForCoords(x, z)
                                .canSpawnLightningBolt();
                        Block.SoundType step = association.sound;
                        best = new Surface(
                            name,
                            wet,
                            step == null ? null : step.getStepResourcePath(),
                            step == null ? 0.0F : step.getVolume() * 0.15F,
                            step == null ? 1.0F : step.getPitch(),
                            x,
                            y,
                            z,
                            true,
                            exposed || waterAtContact && !submerged,
                            contactX,
                            box.maxY,
                            contactZ,
                            box.minX,
                            box.maxX,
                            box.minZ,
                            box.maxZ);
                        bestScore = score;
                    }
                }
            }
        }

        if (validWorldCoordinate(actor.posX) && validWorldCoordinate(actor.posZ) && actor.isOnLadder()) {
            int x = floor(actor.posX), y = floor(actor.boundingBox.minY), z = floor(actor.posZ);
            if (world.blockExists(x, y, z)) {
                Block block = world.getBlock(x, y, z);
                BlockMaterial appearance = BlockAppearanceProviders
                    .resolve(world, x, y, z, block, world.getBlockMetadata(x, y, z));
                String name = materials.lookup(appearance.block, appearance.metadata, "", materialMap);
                if (name == null) name = "ladder";
                return new Surface(
                    name,
                    null,
                    "step.ladder",
                    0.15F,
                    1.0F,
                    x,
                    y,
                    z,
                    best != null,
                    false,
                    clamp(footX, x, x + 1.0D),
                    clamp(feetY, y, y + 1.0D),
                    clamp(footZ, z, z + 1.0D));
            }
        }
        if (best != null) return overlaySurface(world, footX, footZ, best, materialMap);

        Surface entitySupport = resolveEntitySupport(actor, probe, footX, feetY, footZ);
        if (entitySupport != null) return entitySupport;

        boolean water = waterAtContact || actor.isInWater();
        boolean lava = !water && containsMaterial(world, probe, Material.lava);
        if (water || lava) {
            return new Surface(
                water ? "waterfine" : "lavafine",
                null,
                water ? "game.player.swim" : "liquid.lava",
                0.15F,
                1.0F,
                floor(footX),
                floor(feetY),
                floor(footZ),
                false,
                false,
                footX,
                feetY,
                footZ);
        }
        return null;
    }

    /** Mapped thin coverings select audio and decal geometry without changing physical grounding. */
    private Surface overlaySurface(World world, double footX, double footZ, Surface physical, String materialMap) {
        int x = floor(footX), z = floor(footZ);
        int y = floor(physical.contactY + OVERLAY_EPSILON);
        if (!world.blockExists(x, y, z)) return physical;
        Block overlay = world.getBlock(x, y, z);
        boolean snow = overlay instanceof BlockSnow;
        boolean carpet = overlay instanceof BlockCarpet;
        int meta = world.getBlockMetadata(x, y, z);
        BlockMaterial appearance = BlockAppearanceProviders.resolve(world, x, y, z, overlay, meta);
        MaterialLibrary.Association association = materials
            .resolve(appearance.block, appearance.metadata, snow ? "" : "carpet", materialMap);
        // Only the established vanilla cases can opt in through an ordinary material mapping.
        if ((snow || carpet) && !emitter(association.name))
            association = materials.resolve(appearance.block, appearance.metadata, "", materialMap);
        if (!emitter(association.name)) return physical;

        AxisAlignedBB visual;
        if (snow || carpet) {
            double visualHeight = snow ? ((meta & 7) + 1) * 0.125D : 0.0625D;
            double collisionTop = snow ? y + (meta & 7) * 0.125D : y;
            if (Math.abs(physical.contactY - collisionTop) > OVERLAY_EPSILON
                && Math.abs(physical.contactY - y) > OVERLAY_EPSILON) return physical;
            visual = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + visualHeight, z + 1);
        } else {
            visual = thinOverlayBounds(world, overlay, x, y, z);
            if (visual == null) return physical;
            boolean isSupport = physical.x == x && physical.y == y && physical.z == z;
            // A separate covering must rest on the selected support. A collidable thin block may
            // itself support the foot, with its visible surface at or above that collision contact.
            if (!isSupport && Math.abs(physical.contactY - y) > OVERLAY_EPSILON) return physical;
            if (physical.contactY < visual.minY - OVERLAY_EPSILON || physical.contactY > visual.maxY + OVERLAY_EPSILON)
                return physical;
        }
        double minX = Math.max(physical.minX, visual.minX), maxX = Math.min(physical.maxX, visual.maxX);
        double minZ = Math.max(physical.minZ, visual.minZ), maxZ = Math.min(physical.maxZ, visual.maxZ);
        if (minX >= maxX || minZ >= maxZ
            || footX < minX
            || footX > maxX
            || footZ < minZ
            || footZ > maxZ
            || physical.contactX < minX
            || physical.contactX > maxX
            || physical.contactZ < minZ
            || physical.contactZ > maxZ) return physical;

        String wet = materials.lookup(appearance.block, appearance.metadata, "wet", materialMap);
        Block.SoundType step = association.sound;
        return new Surface(
            association.name,
            wet == null ? physical.wetMaterial : wet,
            step == null ? physical.vanillaSound : step.getStepResourcePath(),
            step == null ? physical.vanillaVolume : step.getVolume() * 0.15F,
            step == null ? physical.vanillaPitch : step.getPitch(),
            x,
            y,
            z,
            physical.grounded,
            physical.wet,
            physical.contactX,
            physical.contactY,
            physical.contactZ,
            minX,
            maxX,
            minZ,
            maxZ,
            visual.maxY,
            physical.supportEntity);
    }

    /** 1.7 has no generic visual-shape API; accept only bounded, floor-aligned state bounds. */
    private static AxisAlignedBB thinOverlayBounds(World world, Block block, int x, int y, int z) {
        float oldMinX = (float) block.getBlockBoundsMinX(), oldMinY = (float) block.getBlockBoundsMinY(),
            oldMinZ = (float) block.getBlockBoundsMinZ(), oldMaxX = (float) block.getBlockBoundsMaxX(),
            oldMaxY = (float) block.getBlockBoundsMaxY(), oldMaxZ = (float) block.getBlockBoundsMaxZ();
        try {
            block.setBlockBoundsBasedOnState(new LoadedBlockAccess(world), x, y, z);
            double minX = block.getBlockBoundsMinX(), minY = block.getBlockBoundsMinY(),
                minZ = block.getBlockBoundsMinZ(), maxX = block.getBlockBoundsMaxX(), maxY = block.getBlockBoundsMaxY(),
                maxZ = block.getBlockBoundsMaxZ();
            if (!finite(minX) || !finite(minY)
                || !finite(minZ)
                || !finite(maxX)
                || !finite(maxY)
                || !finite(maxZ)
                || minX < 0
                || minZ < 0
                || maxX > 1
                || maxZ > 1
                || minX >= maxX
                || minZ >= maxZ
                || Math.abs(minY) > OVERLAY_EPSILON
                || maxY <= minY
                || maxY >= MAX_THIN_OVERLAY_HEIGHT) return null;
            return AxisAlignedBB.getBoundingBox(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
        } catch (RuntimeException | LinkageError ignored) {
            // Incompatible bounds code or an unloaded neighbor cannot replace the physical surface.
            return null;
        } finally {
            block.setBlockBounds(oldMinX, oldMinY, oldMinZ, oldMaxX, oldMaxY, oldMaxZ);
        }
    }

    /** Resolves only explicit entity support rules after block collision found no support. */
    private Surface resolveEntitySupport(EntityLivingBase actor, AxisAlignedBB probe, double footX, double feetY,
        double footZ) {
        List<?> candidates;
        try {
            candidates = actor.worldObj.getEntitiesWithinAABBExcludingEntity(actor, probe);
        } catch (RuntimeException ignored) {
            return null;
        }
        Surface best = null;
        double bestScore = Double.MAX_VALUE;
        int inspected = 0;
        for (Object value : candidates) {
            if (++inspected > MAX_SUPPORT_CANDIDATES) break;
            if (!(value instanceof Entity)) continue;
            Entity support = (Entity) value;
            if (support.isDead || !support.canBeCollidedWith() || support.boundingBox == null) continue;
            AxisAlignedBB box = support.boundingBox;
            if (!validSupportBox(box) || !reachesSupportTop(probe, box)) continue;
            String entityId = EntityList.getEntityString(support);
            SupportProfiles.Rule rule = supports.rule(entityId);
            boolean adapted = SurfaceSupportProviders.handles(support);
            if (rule == null && !adapted) continue;
            double contactX = clamp(footX, box.minX, box.maxX);
            double contactZ = clamp(footZ, box.minZ, box.maxZ);
            double score = (contactX - footX) * (contactX - footX) + (contactZ - footZ) * (contactZ - footZ)
                + Math.abs(box.maxY - feetY);
            if (score >= bestScore) continue;

            Surface provided = null;
            if (adapted) try {
                provided = SurfaceSupportProviders.resolve(actor, support, footX, feetY, footZ);
            } catch (RuntimeException ignored) {
                // A third-party provider cannot suppress the built-in or native path.
            }
            if (provided != null && validProvidedSurface(provided, box, feetY)) {
                best = supportSurface(
                    provided.material,
                    provided.wetMaterial,
                    provided.vanillaSound,
                    provided.vanillaVolume,
                    provided.vanillaPitch,
                    provided.wet,
                    provided.contactX,
                    provided.contactZ,
                    box,
                    support);
                bestScore = score;
            } else if (rule != null) {
                best = supportSurface(
                    rule.material,
                    null,
                    rule.vanillaSound,
                    rule.volume,
                    rule.pitch,
                    false,
                    contactX,
                    contactZ,
                    box,
                    support);
                bestScore = score;
            }
        }
        return best;
    }

    private static Surface supportSurface(String material, String wetMaterial, String vanillaSound, float volume,
        float pitch, boolean wet, double contactX, double contactZ, AxisAlignedBB box, Entity support) {
        return new Surface(
            material,
            wetMaterial,
            vanillaSound,
            volume,
            pitch,
            floor(contactX),
            floor(box.maxY - OVERLAY_EPSILON),
            floor(contactZ),
            true,
            wet,
            contactX,
            box.maxY,
            contactZ,
            box.minX,
            box.maxX,
            box.minZ,
            box.maxZ).withSupportEntity(support);
    }

    private static boolean validSupportBox(AxisAlignedBB box) {
        return finite(box.minX) && finite(box.maxX)
            && finite(box.minY)
            && finite(box.maxY)
            && finite(box.minZ)
            && finite(box.maxZ)
            && box.minX < box.maxX
            && box.minY < box.maxY
            && box.minZ < box.maxZ;
    }

    private static boolean reachesSupportTop(AxisAlignedBB probe, AxisAlignedBB box) {
        return box.maxX >= probe.minX && box.minX <= probe.maxX
            && box.maxZ >= probe.minZ
            && box.minZ <= probe.maxZ
            && box.maxY >= probe.minY
            && box.maxY <= probe.maxY;
    }

    private static boolean validProvidedSurface(Surface surface, AxisAlignedBB box, double feetY) {
        return surface.grounded && finite(surface.contactX)
            && finite(surface.contactY)
            && finite(surface.contactZ)
            && Math.abs(surface.contactY - box.maxY) <= OVERLAY_EPSILON
            && Math.abs(surface.contactY - feetY) <= SUPPORT_BELOW_REACH
            && surface.contactX >= box.minX - OVERLAY_EPSILON
            && surface.contactX <= box.maxX + OVERLAY_EPSILON
            && surface.contactZ >= box.minZ - OVERLAY_EPSILON
            && surface.contactZ <= box.maxZ + OVERLAY_EPSILON;
    }

    /** Ordinary foliage belongs to the plant at a resolved foot contact, not arbitrary blocks above the head. */
    public String contactFoliage(EntityLivingBase actor, Surface surface, String materialMap) {
        if (actor == null || actor.worldObj == null
            || surface == null
            || surface.material != null && !emitter(surface.material)
            || !validWorldCoordinate(surface.contactX)
            || !validWorldCoordinate(surface.decalY)
            || !validWorldCoordinate(surface.contactZ)) return null;
        int x = floor(surface.contactX), z = floor(surface.contactZ);
        // Farmland is 1/16 block below its crop. Use the block above the support even when the feet
        // remain just inside the support's cell; tall supports can extend into that upper cell.
        int y = Math.max(surface.y + 1, floor(surface.decalY + OVERLAY_EPSILON));
        World world = actor.worldObj;
        if (world.blockExists(surface.x, surface.y, surface.z)) {
            Block support = world.getBlock(surface.x, surface.y, surface.z);
            if (support instanceof BlockCarpet || support instanceof BlockSnow) return null;
        }
        if (!world.blockExists(x, y, z) || x == surface.x && y == surface.y && z == surface.z) return null;
        Block block = world.getBlock(x, y, z);
        BlockMaterial appearance = BlockAppearanceProviders
            .resolve(world, x, y, z, block, world.getBlockMetadata(x, y, z));
        String name = materials.lookup(appearance.block, appearance.metadata, "foliage", materialMap);
        if (!emitter(name)) return null;
        float minX = (float) block.getBlockBoundsMinX(), minY = (float) block.getBlockBoundsMinY(),
            minZ = (float) block.getBlockBoundsMinZ(), maxX = (float) block.getBlockBoundsMaxX(),
            maxY = (float) block.getBlockBoundsMaxY(), maxZ = (float) block.getBlockBoundsMaxZ();
        collisionBoxes.clear();
        try {
            block.addCollisionBoxesToList(
                world,
                x,
                y,
                z,
                AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1),
                collisionBoxes,
                actor);
            return collisionBoxes.isEmpty() ? name : null;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            block.setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /** Only vegetation explicitly marked MESSY_GROUND can produce a between-contact entry rustle. */
    public String messyFoliage(EntityLivingBase actor, String materialMap) {
        return scanFoliage(actor, materialMap, true);
    }

    public String foliage(EntityLivingBase actor) {
        return foliage(actor, "");
    }

    public String foliage(EntityLivingBase actor, String materialMap) {
        return scanFoliage(actor, materialMap, false);
    }

    private String scanFoliage(EntityLivingBase actor, String materialMap, boolean messyOnly) {
        if (actor == null || actor.worldObj == null
            || actor.boundingBox == null
            || !validWorldCoordinate(actor.boundingBox.minX)
            || !validWorldCoordinate(actor.boundingBox.maxX)
            || !validWorldCoordinate(actor.boundingBox.minY)
            || !validWorldCoordinate(actor.boundingBox.maxY)
            || !validWorldCoordinate(actor.boundingBox.minZ)
            || !validWorldCoordinate(actor.boundingBox.maxZ)) return null;
        Set<String> names = new LinkedHashSet<String>();
        AxisAlignedBB body = actor.boundingBox;
        World world = actor.worldObj;
        int[] xs = sampleCells(body.minX + 0.01D, body.maxX - 0.01D, FOLIAGE_HORIZONTAL_SAMPLES);
        int feetCell = floor(body.minY + 0.01D), aboveSupport = floor(body.minY - 0.1D) + 1;
        int[] ys = messyOnly ? feetCell == aboveSupport ? new int[] { feetCell } : new int[] { feetCell, aboveSupport }
            : sampleCells(body.minY + 0.01D, body.maxY - 0.01D, FOLIAGE_VERTICAL_SAMPLES);
        int[] zs = sampleCells(body.minZ + 0.01D, body.maxZ - 0.01D, FOLIAGE_HORIZONTAL_SAMPLES);
        for (int x : xs) {
            for (int z : zs) {
                for (int y : ys) {
                    if (x < -30000000 || x >= 30000000 || z < -30000000 || z >= 30000000) continue;
                    if (!world.blockExists(x, y, z)) continue;
                    Block block = world.getBlock(x, y, z);
                    int meta = world.getBlockMetadata(x, y, z);
                    BlockMaterial appearance = BlockAppearanceProviders.resolve(world, x, y, z, block, meta);
                    if (messyOnly && !"MESSY_GROUND"
                        .equals(materials.lookup(appearance.block, appearance.metadata, "messy", materialMap)))
                        continue;
                    String name = materials.lookup(appearance.block, appearance.metadata, "foliage", materialMap);
                    if (emitter(name)) names.add(name);
                }
            }
        }
        return names.isEmpty() ? null : String.join(",", names);
    }

    public static boolean emitter(String name) {
        return name != null && !name.isEmpty()
            && !"NOT_EMITTER".equals(name)
            && !"NON_EMITTER".equals(name)
            && !"MESSY_GROUND".equals(name);
    }

    private static boolean containsMaterial(World world, AxisAlignedBB box, Material material) {
        return world.checkChunksExist(
            floor(box.minX),
            floor(box.minY),
            floor(box.minZ),
            floor(box.maxX),
            floor(box.maxY),
            floor(box.maxZ)) && world.isMaterialInBB(box, material);
    }

    private static AxisAlignedBB contactProbe(double x, double feetY, double z) {
        return AxisAlignedBB.getBoundingBox(
            x - SUPPORT_HORIZONTAL_REACH,
            feetY - SUPPORT_BELOW_REACH,
            z - SUPPORT_HORIZONTAL_REACH,
            x + SUPPORT_HORIZONTAL_REACH,
            feetY + SUPPORT_ABOVE_REACH,
            z + SUPPORT_HORIZONTAL_REACH);
    }

    private static int[] sampleCells(double min, double max, int limit) {
        if (!finite(min) || !finite(max)) return new int[0];
        if (max < min) max = min;
        int first = floor(min), last = floor(max);
        long count = (long) last - first + 1L;
        if (count <= limit) {
            int[] result = new int[(int) count];
            for (int i = 0; i < result.length; i++) result[i] = first + i;
            return result;
        }
        int[] result = new int[limit];
        for (int i = 0; i < limit; i++) {
            result[i] = first + (int) ((((long) last - first) * i) / (limit - 1));
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean validWorldCoordinate(double value) {
        return finite(value) && value >= -30000000.0D && value < 30000000.0D;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static int floor(double value) {
        return MathHelper.floor_double(value);
    }

    public static final class Surface {

        public final String material, wetMaterial, vanillaSound;
        public final float vanillaVolume, vanillaPitch;
        public final int x, y, z;
        public final boolean grounded, wet;
        /** Physical collision contact used by the movement tracker and sound positioning. */
        public final double contactX, contactY, contactZ;
        /** Rendered top for decals. Thin overlays may be visibly above their collision top. */
        public final double decalY;
        /** Non-null only for an explicitly mapped collidable entity support. */
        public final Entity supportEntity;
        public final double minX, maxX, minZ, maxZ;

        public Surface(String material, String wetMaterial, String vanillaSound, float vanillaVolume,
            float vanillaPitch, int x, int y, int z, boolean grounded, boolean wet) {
            this(
                material,
                wetMaterial,
                vanillaSound,
                vanillaVolume,
                vanillaPitch,
                x,
                y,
                z,
                grounded,
                wet,
                x + 0.5D,
                y + 1.0D,
                z + 0.5D);
        }

        public Surface(String material, String wetMaterial, String vanillaSound, float vanillaVolume,
            float vanillaPitch, int x, int y, int z, boolean grounded, boolean wet, double contactX, double contactY,
            double contactZ) {
            this(
                material,
                wetMaterial,
                vanillaSound,
                vanillaVolume,
                vanillaPitch,
                x,
                y,
                z,
                grounded,
                wet,
                contactX,
                contactY,
                contactZ,
                x,
                x + 1,
                z,
                z + 1);
        }

        public Surface(String material, String wetMaterial, String vanillaSound, float vanillaVolume,
            float vanillaPitch, int x, int y, int z, boolean grounded, boolean wet, double contactX, double contactY,
            double contactZ, double minX, double maxX, double minZ, double maxZ) {
            this(
                material,
                wetMaterial,
                vanillaSound,
                vanillaVolume,
                vanillaPitch,
                x,
                y,
                z,
                grounded,
                wet,
                contactX,
                contactY,
                contactZ,
                minX,
                maxX,
                minZ,
                maxZ,
                contactY,
                null);
        }

        private Surface(String material, String wetMaterial, String vanillaSound, float vanillaVolume,
            float vanillaPitch, int x, int y, int z, boolean grounded, boolean wet, double contactX, double contactY,
            double contactZ, double minX, double maxX, double minZ, double maxZ, double decalY, Entity supportEntity) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.material = material;
            this.wetMaterial = wetMaterial;
            this.vanillaSound = vanillaSound;
            this.vanillaVolume = vanillaVolume;
            this.vanillaPitch = vanillaPitch;
            this.x = x;
            this.y = y;
            this.z = z;
            this.grounded = grounded;
            this.wet = wet;
            this.contactX = contactX;
            this.contactY = contactY;
            this.contactZ = contactZ;
            this.decalY = decalY;
            this.supportEntity = supportEntity;
        }

        public Surface withSupportEntity(Entity entity) {
            return new Surface(
                material,
                wetMaterial,
                vanillaSound,
                vanillaVolume,
                vanillaPitch,
                x,
                y,
                z,
                grounded,
                wet,
                contactX,
                contactY,
                contactZ,
                minX,
                maxX,
                minZ,
                maxZ,
                decalY,
                entity);
        }
    }
}
