module com.hedera.node.app.service.scheduled.impl {
    requires com.hedera.node.app.service.scheduled;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono;

    provides com.hedera.node.app.service.scheduled.ScheduleService with
            com.hedera.node.app.service.scheduled.impl.ScheduleServiceImpl;

    exports com.hedera.node.app.service.scheduled.impl to
            com.hedera.node.app.service.scheduled.impl.test;
}
