package com.hedera.services.bdd.suites.perf.token;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;

public class TokenAssociationLoadTest  extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenAssociationLoadTest.class);

	private AtomicInteger maxTokens = new AtomicInteger(500);
	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runTokenAssociationLoadTest(),
				}
		);
	}

	private HapiApiSpec runTokenAssociationLoadTest() {
		return HapiApiSpec.defaultHapiSpec("RunTokenAssociationLoadTest")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"accounts.limitTokenAssociations", "false",
										"tokens.maxPerAccount", "10",
										"tokens.maxRelsPerInfoQuery", "10"))
				)
				.when()
				.then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"accounts.limitTokenAssociations", "true",
										"tokens.maxPerAccount", "1000",
										"tokens.maxRelsPerInfoQuery", "1000"))
				);
	}
}
