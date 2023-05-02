module com.hedera.node.app.service.schedule.impl {
    requires transitive com.hedera.node.app.service.schedule;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.config;
    requires com.swirlds.common;
    requires com.swirlds.config;
    requires com.swirlds.virtualmap;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
    requires com.google.common;

    exports com.hedera.node.app.service.schedule.impl;
    exports com.hedera.node.app.service.schedule.impl.handlers;
    exports com.hedera.node.app.service.schedule.impl.serdes;

    provides com.hedera.node.app.service.schedule.ScheduleService with
            com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
}
