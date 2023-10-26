module com.swirlds.logging {
    exports com.swirlds.logging;
    exports com.swirlds.logging.json;
    exports com.swirlds.logging.payloads;

    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive org.apache.logging.log4j;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.swirlds.base;
    requires static com.github.spotbugs.annotations;
}
