import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;

module com.hedera.node.app.service.schedule.impl {
	requires transitive com.hedera.node.app.service.scheduled;
	requires org.apache.commons.lang3;
	requires com.hedera.node.app.service.mono;

	exports com.hedera.node.app.service.schedule.impl to
			com.hedera.node.app.service.schedule.impl.test,
			com.hedera.node.app.service.scheduled.impl.test;

	provides com.hedera.node.app.service.schedule.ScheduleService with
			ScheduleServiceImpl;
}
