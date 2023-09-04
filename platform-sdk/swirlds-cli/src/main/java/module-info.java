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

    requires transitive com.swirlds.common;
    requires transitive info.picocli;
    requires transitive org.apache.logging.log4j;
    requires io.github.classgraph;
    requires static com.github.spotbugs.annotations;
}
