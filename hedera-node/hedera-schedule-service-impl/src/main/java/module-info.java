module com.hedera.node.app.service.schedule.impl {
    requires transitive com.hedera.node.app.hapi.fees;
    // Only ScheduleServiceStateTranslator requires this item, when that is removed, this should also be removed.
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.service.schedule;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.app.hapi.utils;
    // Required for ReadableAccountStore to read payer account details on create, sign, or query
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.config;
    requires com.google.common;
    requires com.swirlds.config.api;
    requires static transitive com.github.spotbugs.annotations;
    requires static java.compiler; // javax.annotation.processing.Generated
    requires org.apache.logging.log4j;
    requires org.eclipse.collections.api;

    exports com.hedera.node.app.service.schedule.impl;
    exports com.hedera.node.app.service.schedule.impl.handlers;
    exports com.hedera.node.app.service.schedule.impl.codec;

    provides com.hedera.node.app.service.schedule.ScheduleService with
            com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
}
