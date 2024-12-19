module com.swirlds.config.extensions {
    exports com.swirlds.config.extensions;
    exports com.swirlds.config.extensions.export;
    exports com.swirlds.config.extensions.reflection;
    exports com.swirlds.config.extensions.sources;
    exports com.swirlds.config.extensions.validators;

    requires transitive com.swirlds.config.api;
    requires com.swirlds.base;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires org.apache.logging.log4j;
    requires org.yaml.snakeyaml;
    requires static transitive com.github.spotbugs.annotations;
}
