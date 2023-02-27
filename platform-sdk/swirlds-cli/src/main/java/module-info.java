module com.swirlds.cli {
    exports com.swirlds.cli;
    exports com.swirlds.cli.commands;
    exports com.swirlds.cli.utility;

    opens com.swirlds.cli to
            info.picocli;
    opens com.swirlds.cli.utility to
            info.picocli;
    opens com.swirlds.cli.commands to
            info.picocli;

    requires com.swirlds.common;

    /* Utilities */
    requires info.picocli;
    requires io.github.classgraph;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
}
