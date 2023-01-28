import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;

module com.hedera.node.app.service.schedule.impl {
    requires transitive com.hedera.node.app.service.scheduled;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.virtualmap;

    exports com.hedera.node.app.service.schedule.impl to
            com.hedera.node.app.service.schedule.impl.test,
            com.hedera.node.app;
    exports com.hedera.node.app.service.schedule.impl.handlers to
            com.hedera.node.app.service.schedule.impl.test,
            com.hedera.node.app;

    provides com.hedera.node.app.service.schedule.ScheduleService with
            ScheduleServiceImpl;
}
