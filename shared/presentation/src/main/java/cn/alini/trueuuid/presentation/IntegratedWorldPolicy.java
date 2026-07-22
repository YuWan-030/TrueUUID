package cn.alini.trueuuid.presentation;

/** Loader-neutral classification shared by client presentation and server feedback. */
public final class IntegratedWorldPolicy {
    /** A local world that has not been published must show only Singleplayer feedback. */
    public static boolean isPrivateSingleplayer(boolean singleplayer, boolean publishedToLan) {
        return singleplayer && !publishedToLan;
    }

    private IntegratedWorldPolicy() {}
}
