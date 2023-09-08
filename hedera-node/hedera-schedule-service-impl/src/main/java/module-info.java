module com.hedera.node.app.service.schedule.impl {
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.service.schedule;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.config;
    requires com.google.common;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.config.api;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.node.app.service.schedule.impl;
    exports com.hedera.node.app.service.schedule.impl.handlers;
    exports com.hedera.node.app.service.schedule.impl.codec;

    provides com.hedera.node.app.service.schedule.ScheduleService with
            com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
}
