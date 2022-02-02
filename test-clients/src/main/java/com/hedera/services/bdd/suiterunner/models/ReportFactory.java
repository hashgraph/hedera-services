package com.hedera.services.bdd.suiterunner.models;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.hedera.services.bdd.suiterunner.ReflectiveSuitesRunner.LOG_PATH;
import static java.util.stream.Collectors.toList;

public class ReportFactory {
	private static final String LOG_PATTERN = "([\\d+-]+\\s[\\d+:]+.\\d+)\\s([\\w\\W]+)(%s)([\\w\\W]+)( failed )([\\w\\W]+)";

	public static SuiteReport generateFailedSuiteReport(final HapiApiSuite failedSuite) {
		final var suiteReport = new SuiteReport(failedSuite.getClass().getSimpleName(), "Failed");

		final var failedSpecs = failedSuite.getFinalSpecs()
				.stream()
				.filter(HapiApiSpec::NOT_OK)
				.map(ReportFactory::generateFailedSpecReport)
				.collect(toList());

		suiteReport.setFailedSpecs(failedSpecs);

		return suiteReport;
	}

	private static SpecReport generateFailedSpecReport(final HapiApiSpec failedSpec) {
		return new SpecReport(
				failedSpec.getName(),
				failedSpec.getStatus(),
				getFailReason(failedSpec.getName()));
	}

	private static String getFailReason(final String name) {
		final var pattern = String.format(LOG_PATTERN, name);
		var reason = "";

		try {
			final var stream = Files.lines(Paths.get(LOG_PATH)).filter(line -> line.matches(pattern));
			reason = stream.findFirst().orElse("Reason can not be extrapolated");
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return reason;
	}
}
