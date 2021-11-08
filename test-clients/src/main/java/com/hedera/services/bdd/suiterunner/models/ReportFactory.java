package com.hedera.services.bdd.suiterunner.models;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.suiterunner.TypedSuiteRunner.LOG_PATH;

public class ReportFactory {
	private static final String LOG_PATTERN = "([\\d+-]+\\s[\\d+:]+.\\d+)\\s([\\w\\W]+)(%s)([\\w\\W]+)( failed )([\\w\\W]+)";

	public static SuiteReport getReportFor(final HapiApiSuite failedSuite) {
		final var suiteReport = new SuiteReport(failedSuite.getClass().getSimpleName(), "Failed");

		final var specReports = failedSuite.getFinalSpecs()
				.stream()
				.filter(HapiApiSpec::NOT_OK)
				.map(ReportFactory::generateSpecReport)
				.collect(Collectors.toList());

		suiteReport.setFailingSpecs(specReports);

		return suiteReport;
	}

	private static SpecReport generateSpecReport(final HapiApiSpec failedSpec) {
		return new SpecReport(
				failedSpec.getName(),
				failedSpec.getStatus(),
				getReason(failedSpec.getName()));
	}

	/*	Note to the reviewer:
	 *	"Reason can not be extrapolated" kicks in when the test fails with ERROR or when the detailed information
	 *	about the failure reason is logged at INFO level (in order to reduce the output in the buffer log file we
	 * 	log in WARN and above.
	 * 	Logic for capturing the failure reason for tests with ERROR status will be implemented at a later stage
	 * */
	private static String getReason(final String name) {
		String reason = "";
		String pattern = String.format(LOG_PATTERN, name);

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
