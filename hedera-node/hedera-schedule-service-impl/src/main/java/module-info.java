module com.hedera.node.app.service.schedule.impl {
    requires transitive com.hedera.node.app.service.scheduled;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.virtualmap;
    requires dagger;
    requires javax.inject;
    requires static com.google.auto.service;

    exports com.hedera.node.app.service.schedule.impl to
            com.hedera.node.app;
    exports com.hedera.node.app.service.schedule.impl.handlers to
            com.hedera.node.app;
    exports com.hedera.node.app.service.schedule.impl.components;

    provides com.hedera.node.app.spi.service.ServiceFactory with
            com.hedera.node.app.service.schedule.impl.ScheduleServiceFactory;
}
