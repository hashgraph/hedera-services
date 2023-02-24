module com.swirlds.logging {
    exports com.swirlds.logging;
    exports com.swirlds.logging.json;
    exports com.swirlds.logging.payloads;

    /* Logging Libraries */
    requires org.apache.logging.log4j;

    /* Utilities */
    requires org.apache.commons.lang3;

    /* Jackson JSON */
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
}
