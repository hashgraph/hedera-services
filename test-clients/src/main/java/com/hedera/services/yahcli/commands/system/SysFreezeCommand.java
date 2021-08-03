package com.hedera.services.yahcli.commands.system;

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.suites.FreezeSuite;
import picocli.CommandLine;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

@CommandLine.Command(
		name = "freeze",
		subcommands = { picocli.CommandLine.HelpCommand.class },
		description = "Freeze system at given start time")
public class SysFreezeCommand implements Callable<Integer> {

	@CommandLine.ParentCommand
	private Yahcli yahcli;

	@CommandLine.Option(names = { "-s", "--start-time"},
			paramLabel = "Freeze start time",
			defaultValue = "")
	private String freezeStartTimeStr;

	private Instant freezeStartTime;
	@Override
	public Integer call() throws Exception {
		var config = configFrom(yahcli);

		freezeStartTime = ensureFreezeStartTime(freezeStartTimeStr);

		var delegate = new FreezeSuite(config.asSpecConfig(), freezeStartTime);
		delegate.runSuiteSync();

		return 0;
	}


	private Instant ensureFreezeStartTime(String timeStampInStr) {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

		Instant startTime = Instant.from(dtf.parse(timeStampInStr));

		return startTime;

	}

}