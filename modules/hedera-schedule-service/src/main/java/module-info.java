module com.hedera.node.app.service.scheduled {
    exports com.hedera.node.app.service.scheduled;

    uses com.hedera.node.app.service.scheduled.ScheduleService;

    requires transitive com.hedera.node.app.spi;
    requires transitive org.slf4j;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;
}
