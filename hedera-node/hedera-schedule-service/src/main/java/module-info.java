module com.hedera.node.app.service.scheduled {
    exports com.hedera.node.app.service.schedule;

    uses com.hedera.node.app.service.schedule.ScheduleService;

    requires transitive com.hedera.node.app.spi;
    requires static com.github.spotbugs.annotations;
}
