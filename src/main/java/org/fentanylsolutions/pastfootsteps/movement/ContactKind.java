package org.fentanylsolutions.pastfootsteps.movement;

/** Typed movement events, with the existing acoustic-pack keys at the sound boundary. */
public enum ContactKind {

    WALK("walk"),
    RUN("run"),
    UP("up"),
    UP_RUN("up_run"),
    DOWN("down"),
    DOWN_RUN("down_run"),
    CLIMB("walk"),
    SWIM("walk"),
    JUMP("jump"),
    LAND("land"),
    LAND_SOFT("climb"),
    SHUFFLE("wander"),
    STAND("stand");

    public final String acousticKey;

    ContactKind(String acousticKey) {
        this.acousticKey = acousticKey;
    }

    public boolean isLanding() {
        return this == LAND || this == LAND_SOFT;
    }
}
