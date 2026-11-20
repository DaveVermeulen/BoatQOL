package io.github.davevermeulen.boatqol.net;

final class Sneaky {
    private Sneaky() {
    }

    /** Rethrows a checked exception unchanged, like the vanilla packet code does. */
    @SuppressWarnings("unchecked")
    static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }
}
