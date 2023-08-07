module com.swirlds.logging {
    exports com.swirlds.logging;
    exports com.swirlds.logging.json;
    exports com.swirlds.logging.payloads;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
    requires com.swirlds.base;
}
