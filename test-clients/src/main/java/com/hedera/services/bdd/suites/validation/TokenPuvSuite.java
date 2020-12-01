package com.hedera.services.bdd.suites.validation;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.validation.domain.NetworkInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;

public class TokenPuvSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenPuvSuite.class);

	private final NetworkInfo targetInfo;

	public TokenPuvSuite(NetworkInfo targetInfo) {
		this.targetInfo = targetInfo;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				helloWorld(),
		});
	}

	private HapiApiSpec helloWorld() {
		return HapiApiSpec.customHapiSpec("HelloWorld").withProperties(
				targetInfo.toCustomProperties()
		).given().when().then(
				getAccountInfo("0.0.2")
		);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
