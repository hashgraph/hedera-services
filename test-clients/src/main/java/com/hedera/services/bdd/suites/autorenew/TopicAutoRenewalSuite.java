package com.hedera.services.bdd.suites.autorenew;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;

public class TopicAutoRenewalSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TopicAutoRenewalSuite.class);

	public static void main(String... args) {
		new TopicAutoRenewalSuite().runSuiteSync();
	}

	// TODO : just added empty shells for now.

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				topicAutoRemoval(),
				topicAutoRenewal()
		);
	}

	private HapiApiSpec topicAutoRemoval() {
		return defaultHapiSpec("")
				.given()
				.when()
				.then();
	}

	private HapiApiSpec topicAutoRenewal() {
		return defaultHapiSpec("")
				.given()
				.when()
				.then();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
