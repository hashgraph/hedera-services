package com.hedera.services.bdd.suites.perf.token;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

public class TokenAssociationHammerTest extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenAssociationHammerTest.class);

	private int maxTokens = 10_000;
	private String tokenTreasury = "treasury";
	private String user = "user";

	public static void main(String... args) {
		new TokenAssociationHammerTest().runSuiteSync();
	}

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
										"tokens.maxRelsPerInfoQuery", "10")),
						// create a treasury and a user
						cryptoCreate(tokenTreasury),
						cryptoCreate(user)
				)
				.when(
						withOpContext((spec, log) -> {
							Set<Long> associatedTokens = new HashSet<>();
							final AtomicLong currHead = new AtomicLong(0);
							final AtomicLong currTail = new AtomicLong(0);
							var ops = inParallel(IntStream.range(0, maxTokens)
									.mapToObj(i -> tokenCreate("token"+i)
											.treasury(tokenTreasury))
									.toArray(HapiSpecOperation[]::new));
							allRunFor(spec, ops);
							// associate each of them to the user and add to associatedTokens set
							ops = inParallel(IntStream.range(0, maxTokens)
									.mapToObj(i -> {
										associatedTokens.add((long) i);
										currHead.set(i);
										return tokenAssociate(user, "token"+i);
									}).toArray(HapiSpecOperation[]::new));
							allRunFor(spec, ops);
							while (associatedTokens.size() >= 10) {
								allRunFor(spec, inParallel(IntStream.range(0, 20)
										.mapToObj(i -> {
											var val = currHead.getAndDecrement();
											associatedTokens.remove(val);
											return tokenDissociate(user, "token"+ (val))
													.hasKnownStatusFrom(SUCCESS, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
										}).toArray(HapiSpecOperation[]::new)));
								allRunFor(spec, inParallel(IntStream.range(0, 20)
										.mapToObj(i -> {
											var val = currTail.getAndIncrement();
											associatedTokens.remove(val);
											return tokenDissociate(user, "token"+ (val))
													.hasKnownStatusFrom(SUCCESS, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
										}).toArray(HapiSpecOperation[]::new)));
								allRunFor(spec, inParallel(IntStream.range(0, 5)
										.mapToObj(i -> {
											var mid = (currHead.get() + currTail.get()) / 2;
											associatedTokens.remove(mid+i);
											return tokenDissociate(user, "token"+ (mid+i))
													.hasKnownStatusFrom(SUCCESS, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
										}).toArray(HapiSpecOperation[]::new)));
								allRunFor(spec, inParallel(IntStream.range(0, 1)
										.mapToObj(i -> {
											var val = currHead.incrementAndGet();
											associatedTokens.add(val);
											return tokenAssociate(user, "token"+ (val));
										}).toArray(HapiSpecOperation[]::new)));
							}
							// get account info and see what all tokens we are associated to.
							// validate if we have associations from associatedTokens set
							allRunFor(spec, getAccountInfo(user).logged());
							allRunFor(spec, inParallel(associatedTokens.stream()
									.map(i -> {
										log.info("looking for association with " + i);
										return getAccountInfo(user)
												.hasToken(ExpectedTokenRel.relationshipWith("token" + i));
									}).toArray(HapiSpecOperation[]::new)));
						})
				)
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
