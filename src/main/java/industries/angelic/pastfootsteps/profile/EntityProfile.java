package industries.angelic.pastfootsteps.profile;

import java.util.regex.Pattern;

/** Immutable locomotion tuning selected by an entity ID, class, or explicit variant selector. */
public final class EntityProfile {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern MATERIAL_MAP_ID = Pattern.compile("[a-z0-9_-]{1,64}");

    public final String id;
    public final String parentId;
    public final EntitySelector selector;
    public final int priority;
    public final GaitDefinition gait;
    public final double strideMultiplier;
    public final double childScale;
    public final double runSpeed;
    public final float volume;
    /** Adult nominal bounding-box width in blocks; zero leaves size pitch correction unspecified. */
    public final double referenceWidth;
    /** Optional material map identifier; empty means the shared block map. */
    public final String materialMap;

    public EntityProfile(String id, GaitDefinition gait, double strideMultiplier, double childScale, double runSpeed,
        float volume) {
        this(id, gait, strideMultiplier, childScale, runSpeed, volume, 0);
    }

    public EntityProfile(String id, GaitDefinition gait, double strideMultiplier, double childScale, double runSpeed,
        float volume, String materialMap) {
        this(id, gait, strideMultiplier, childScale, runSpeed, volume, 0, materialMap);
    }

    public EntityProfile(String id, GaitDefinition gait, double strideMultiplier, double childScale, double runSpeed,
        float volume, double referenceWidth) {
        this(id, gait, strideMultiplier, childScale, runSpeed, volume, referenceWidth, "");
    }

    public EntityProfile(String id, GaitDefinition gait, double strideMultiplier, double childScale, double runSpeed,
        float volume, double referenceWidth, String materialMap) {
        this(
            id,
            null,
            EntitySelector.exact(id),
            0,
            gait,
            strideMultiplier,
            childScale,
            runSpeed,
            volume,
            referenceWidth,
            materialMap);
    }

    EntityProfile(String id, String parentId, EntitySelector selector, int priority, GaitDefinition gait,
        double strideMultiplier, double childScale, double runSpeed, float volume) {
        this(id, parentId, selector, priority, gait, strideMultiplier, childScale, runSpeed, volume, 0);
    }

    EntityProfile(String id, String parentId, EntitySelector selector, int priority, GaitDefinition gait,
        double strideMultiplier, double childScale, double runSpeed, float volume, String materialMap) {
        this(id, parentId, selector, priority, gait, strideMultiplier, childScale, runSpeed, volume, 0, materialMap);
    }

    EntityProfile(String id, String parentId, EntitySelector selector, int priority, GaitDefinition gait,
        double strideMultiplier, double childScale, double runSpeed, float volume, double referenceWidth) {
        this(
            id,
            parentId,
            selector,
            priority,
            gait,
            strideMultiplier,
            childScale,
            runSpeed,
            volume,
            referenceWidth,
            "");
    }

    EntityProfile(String id, String parentId, EntitySelector selector, int priority, GaitDefinition gait,
        double strideMultiplier, double childScale, double runSpeed, float volume, double referenceWidth,
        String materialMap) {
        if (id == null || !ID.matcher(id)
            .matches()) throw new IllegalArgumentException("id must be a sane ID");
        if (parentId != null && !ID.matcher(parentId)
            .matches()) throw new IllegalArgumentException("parentId must be a sane ID");
        if (gait == null) throw new IllegalArgumentException("gait must not be null");
        if (selector == null) throw new IllegalArgumentException("selector must not be null");
        if (priority < -10000 || priority > 10000)
            throw new IllegalArgumentException("priority must be between -10000 and 10000");
        requireRange(strideMultiplier, 0.1, 8.0, "strideMultiplier");
        requireRange(childScale, 0.1, 2.0, "childScale");
        requireRange(runSpeed, Double.MIN_VALUE, 4.0, "runSpeed");
        requireRange(volume, 0.0, 4.0, "volume");
        requireRange(referenceWidth, 0.0, 64.0, "referenceWidth");
        if (materialMap == null || !materialMap.isEmpty() && !MATERIAL_MAP_ID.matcher(materialMap)
            .matches()) throw new IllegalArgumentException("materialMap must be empty or a lowercase safe ID");
        this.id = id;
        this.parentId = parentId;
        this.selector = selector;
        this.priority = priority;
        this.gait = gait;
        this.strideMultiplier = strideMultiplier;
        this.childScale = childScale;
        this.runSpeed = runSpeed;
        this.volume = volume;
        this.referenceWidth = referenceWidth;
        this.materialMap = materialMap;
    }

    /** Copies resolved profile tuning while substituting a renderer-specific contact layout. */
    public EntityProfile withGait(GaitDefinition replacement) {
        if (gait == replacement) return this;
        return new EntityProfile(
            id,
            parentId,
            selector,
            priority,
            replacement,
            strideMultiplier,
            childScale,
            runSpeed,
            volume,
            referenceWidth,
            materialMap);
    }

    private static void requireRange(double value, double minimum, double maximum, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException(field + " must be finite and between " + minimum + " and " + maximum);
    }
}
