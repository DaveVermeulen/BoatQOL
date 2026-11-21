package io.github.davevermeulen.boatqol;

/** Extra state added to EntityBoat by EntityBoatMixin. */
public interface BoatState {
    /** Server: the rider's client reports a new position. */
    void boatqol$acceptClientMove(double x, double y, double z, float yRot);

    /** This game instance runs the boat with this player's own input (singleplayer, or our client on a modded server). */
    boolean boatqol$isSteeredHere();
}
