module com.hedera.node.app.service.schedule {
    exports com.hedera.node.app.service.schedule;

    uses com.hedera.node.app.service.schedule.ScheduleService;

    requires com.hedera.node.app.spi;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
