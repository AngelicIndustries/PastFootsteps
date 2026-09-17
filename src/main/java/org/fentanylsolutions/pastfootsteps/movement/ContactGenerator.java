package org.fentanylsolutions.pastfootsteps.movement;

/**
 * Stateful, simulation-tick contact generation. Implementations receive already-selected movement
 * state and emit cosmetic contacts through the same listener contract as the vanilla tracker.
 */
public interface ContactGenerator {

    double phase();

    ContactKind kind();

    int droppedContacts();

    boolean rebaselined();

    void update(double newX, double newY, double newZ, boolean onGround, boolean onLadder, boolean inLiquid,
        boolean sneaking, boolean sprinting, double cycleDistance, double ladderStride, double runSpeed,
        boolean jumpLand, boolean shuffle, boolean stationary, ContactTracker.Listener listener);
}
