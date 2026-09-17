package org.fentanylsolutions.pastfootsteps.movement;

import org.fentanylsolutions.pastfootsteps.profile.GaitDefinition;

/** One limb contact. A group identifies a simultaneous pair or a whole-body landing. */
public final class Contact {

    public final ContactKind kind;
    public final GaitDefinition.Contact foot;
    public final long sequence, group;
    public final int groupSize;
    public final double x, y, z, phase, speed;

    public Contact(ContactKind kind, GaitDefinition.Contact foot, long sequence, long group, int groupSize, double x,
        double y, double z, double phase, double speed) {
        if (kind == null || foot == null
            || groupSize < 1
            || groupSize > 16
            || !Double.isFinite(x)
            || !Double.isFinite(y)
            || !Double.isFinite(z)
            || !Double.isFinite(phase)
            || phase < 0
            || phase > 1
            || !Double.isFinite(speed)
            || speed < 0) throw new IllegalArgumentException("Invalid contact data");
        this.kind = kind;
        this.foot = foot;
        this.sequence = sequence;
        this.group = group;
        this.groupSize = groupSize;
        this.x = x;
        this.y = y;
        this.z = z;
        this.phase = phase;
        this.speed = speed;
    }
}
