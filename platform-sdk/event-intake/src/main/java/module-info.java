module org.hiero.event.intake {
    requires transitive com.swirlds.common;
    requires static com.github.spotbugs.annotations;

    exports org.hiero.event.intake;
    exports org.hiero.event.intake.pces;
}
