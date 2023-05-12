module com.swirlds.platform.swirlds.logging.test {
    requires com.swirlds.logging;
    requires com.swirlds.test.framework;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    opens com.swirlds.logging.payloads.test;

    exports com.swirlds.logging.payloads.test;

    opens com.swirlds.logging.test;

    exports com.swirlds.logging.test;
}
