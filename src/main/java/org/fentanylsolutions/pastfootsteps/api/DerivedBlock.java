package org.fentanylsolutions.pastfootsteps.api;

/** Optional static base-material relationship. Position-dependent covers use BlockAppearanceProviders instead. */
public interface DerivedBlock {

    /** Return null when this variant has no known base. Base metadata must describe the base, not the wrapper. */
    BlockMaterial getBaseMaterial(int metadata);
}
