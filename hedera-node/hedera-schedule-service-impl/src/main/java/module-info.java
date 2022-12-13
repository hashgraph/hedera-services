import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;

module com.hedera.node.app.service.schedule.impl {
    requires com.hedera.node.app.service.scheduled;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.virtualmap;

    exports com.hedera.node.app.service.schedule.impl to
            com.hedera.node.app.service.schedule.impl.test;

    provides com.hedera.node.app.service.schedule.ScheduleService with
            ScheduleServiceImpl;
}
