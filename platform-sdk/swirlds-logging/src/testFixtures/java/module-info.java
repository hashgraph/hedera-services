open module com.swirlds.logging.test.fixtures {
    exports com.swirlds.logging.test.fixtures;

    requires com.swirlds.base.test.fixtures;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.logging;
    requires transitive org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
}
