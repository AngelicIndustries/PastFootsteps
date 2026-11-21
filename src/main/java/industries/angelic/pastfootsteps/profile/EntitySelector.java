package industries.angelic.pastfootsteps.profile;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.passive.EntityHorse;

/** Immutable, precompiled selector for one entity profile. */
public final class EntitySelector {

    public enum Age {
        ANY,
        ADULT,
        CHILD
    }

    public enum HorseType {
        ANY,
        HORSE,
        DONKEY,
        MULE,
        ZOMBIE,
        SKELETON
    }

    public enum SkeletonType {
        ANY,
        NORMAL,
        WITHER
    }

    public final String entityId;
    public final String className;
    public final Age age;
    public final HorseType horseType;
    public final SkeletonType skeletonType;

    private final Class<? extends EntityLivingBase> entityClass;

    EntitySelector(String entityId, String className, Class<? extends EntityLivingBase> entityClass, Age age,
        HorseType horseType, SkeletonType skeletonType) {
        Age resolvedAge = age == null ? Age.ANY : age;
        HorseType resolvedHorseType = horseType == null ? HorseType.ANY : horseType;
        SkeletonType resolvedSkeletonType = skeletonType == null ? SkeletonType.ANY : skeletonType;
        if (entityId == null && className == null)
            throw new IllegalArgumentException("selector requires entityId or class");
        if (className == null && entityClass != null)
            throw new IllegalArgumentException("resolved class requires a class name");
        if (resolvedHorseType != HorseType.ANY && resolvedSkeletonType != SkeletonType.ANY)
            throw new IllegalArgumentException("horseType and skeletonType cannot be combined");
        this.entityId = entityId;
        this.className = className;
        this.entityClass = entityClass;
        this.age = resolvedAge;
        this.horseType = resolvedHorseType;
        this.skeletonType = resolvedSkeletonType;
    }

    static EntitySelector exact(String entityId) {
        return new EntitySelector(entityId, null, null, Age.ANY, HorseType.ANY, SkeletonType.ANY);
    }

    boolean matches(EntityLivingBase actor, String exactEntityId) {
        if (actor == null) return false;
        if (entityId != null && !entityId.equals(exactEntityId)) return false;
        // A named but unavailable optional class remains dormant.
        if (className != null && (entityClass == null || !entityClass.isInstance(actor))) return false;
        if (age == Age.CHILD && !actor.isChild()) return false;
        if (age == Age.ADULT && actor.isChild()) return false;
        if (horseType != HorseType.ANY) {
            if (!(actor instanceof EntityHorse)) return false;
            if (((EntityHorse) actor).getHorseType() != horseType.ordinal() - 1) return false;
        }
        if (skeletonType != SkeletonType.ANY) {
            if (!(actor instanceof EntitySkeleton)) return false;
            int expected = skeletonType == SkeletonType.WITHER ? 1 : 0;
            if (((EntitySkeleton) actor).getSkeletonType() != expected) return false;
        }
        return true;
    }

    boolean resolvesByIdOnly() {
        return entityId != null && className == null
            && age == Age.ANY
            && horseType == HorseType.ANY
            && skeletonType == SkeletonType.ANY;
    }

    boolean hasExactEntityId() {
        return entityId != null;
    }

    int conditionCount() {
        int result = className == null ? 0 : 1;
        if (age != Age.ANY) result++;
        if (horseType != HorseType.ANY) result++;
        if (skeletonType != SkeletonType.ANY) result++;
        return result;
    }

    int classDistance(Class<?> actualClass) {
        if (entityClass == null) return className == null ? Integer.MAX_VALUE - 1 : Integer.MAX_VALUE;
        if (actualClass == entityClass) return 0;
        int distance = 0;
        Class<?> current = actualClass;
        while (current != null) {
            if (current == entityClass) return distance;
            current = current.getSuperclass();
            distance++;
        }
        // Interfaces and unusual hierarchies still match through isInstance, after concrete superclass matches.
        return Integer.MAX_VALUE - 2;
    }
}
