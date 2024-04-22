module com.hedera.node.app.service.file {
    exports com.hedera.node.app.service.file;

    uses com.hedera.node.app.service.file.FileService;

    requires com.swirlds.state.api;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
