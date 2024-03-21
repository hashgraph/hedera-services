open module com.swirlds.config.extensions.test.fixtures {
    exports com.swirlds.config.extensions.test.fixtures;

    requires transitive com.swirlds.config.api;
    requires com.swirlds.common;
    requires com.swirlds.config.extensions;
    requires static transitive com.github.spotbugs.annotations;
}
