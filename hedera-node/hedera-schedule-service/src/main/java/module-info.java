module com.hedera.node.app.service.scheduled {
	exports com.hedera.node.app.service.scheduled;

	uses com.hedera.node.app.service.scheduled.ScheduleService;

	requires transitive com.hedera.node.app.spi;
}
