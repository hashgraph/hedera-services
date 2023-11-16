module com.swirlds.config.extensions {
    exports com.swirlds.config.extensions.sources;

    requires transitive com.swirlds.config.api;
    requires com.swirlds.base;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
