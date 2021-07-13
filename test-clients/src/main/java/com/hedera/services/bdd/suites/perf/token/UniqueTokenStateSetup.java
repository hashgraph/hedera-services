package com.hedera.services.bdd.suites.perf.token;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.TokenSupplyType.INFINITE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A client that creates some number of NON_FUNGIBLE_UNIQUE tokens, and then
 * for each token mints some number of NFTs, each w/ the requested number of
 * bytes of metadata. All tokens are created using the dev treasury key as
 * the token supply key.
 *
 * The exact number of entities to create can be configured using the
 * constants at the top of the class definition.
 *
 * <b>IMPORTANT:</b> Please note there is evidence of slow memory leaks
 * hidden somewhere in the EET infrastructure, so you should probably not
 * try to create more than 10M NFTs using a single run of this client; if
 * more NFTs are needed, then please run several instances of this suite
 * in sequence.
 */
public class UniqueTokenStateSetup extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UniqueTokenStateSetup.class);

	final IntFunction<String> treasuryNameFn = i -> "treasury" + i;
	final IntFunction<String> uniqueTokenNameFn = i -> "uniqueToken" + i;
	private static final AtomicReference<String> firstCreatedId = new AtomicReference<>(null);
	private static final AtomicReference<String> lastCreatedId = new AtomicReference<>(null);

	public static void main(String... args) {
		UniqueTokenStateSetup suite = new UniqueTokenStateSetup();
		suite.runSuiteSync();
		System.out.println("Created unique tokens from " + firstCreatedId + " + to " + lastCreatedId);
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						createNfts(),
				}
		);
	}

	private HapiApiSpec createNfts() {
		return defaultHapiSpec("CreateNfts")
				.given(
						customPropsMgmt()
				).when().then(
						runWithProvider(nftFactory())
								.lasting(durationToRunMints::get, mintDurationUnit::get)
								.maxOpsPerSec(mintTps::get)
				);
	}

	private Function<HapiApiSpec, OpProvider> nftFactory() {
		final AtomicInteger uniqueTokensCreated = new AtomicInteger(0);
		final AtomicInteger nftsMintedForCurrentUniqueToken = new AtomicInteger(0);
		final AtomicBoolean done = new AtomicBoolean(false);
		final AtomicReference<String> currentUniqueToken = new AtomicReference<>(uniqueTokenNameFn.apply(0));

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				final var numTreasuries = numUniqTokens.get() / uniqTokensPerTreasury.get()
						+ Math.min(1, numUniqTokens.get() % uniqTokensPerTreasury.get());

				final List<HapiSpecOperation> inits = new ArrayList<>();
				inits.add(
						inParallel(IntStream.range(0, numTreasuries)
								.mapToObj(i -> cryptoCreate(treasuryNameFn.apply(i))
										.payingWith(GENESIS)
										.balance(0L)
										.key(GENESIS)
										.deferStatusResolution())
								.toArray(HapiSpecOperation[]::new)));
				inits.add(sleepFor(5_000L));
				inits.addAll(burstedUniqCreations(
						setupUniqTokensBurstSize.get(),
						numTreasuries,
						setupUniqTokensBurstPauseMs.get()));
				return inits;
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				if (done.get()) {
					return Optional.empty();
				}

				final var currentToken = currentUniqueToken.get();
				if (nftsMintedForCurrentUniqueToken.get() < nftsPerUniqToken.get()) {
					final List<ByteString> allMeta = new ArrayList<>();
					final int noMoreThan = nftsPerUniqToken.get() - nftsMintedForCurrentUniqueToken.get();
					for (int i = 0, n = Math.min(noMoreThan, nftsPerMintOp.get()); i < n; i++) {
						final var nextSerialNo = nftsMintedForCurrentUniqueToken.incrementAndGet();
						allMeta.add(metadataFor(currentToken, nextSerialNo));
					}
					final var op = mintToken(currentToken, allMeta)
							.payingWith(GENESIS)
							.deferStatusResolution()
							.fee(ONE_HBAR)
							.forgettingEverything()
							.noLogging();
					return Optional.of(op);
				} else {
					nftsMintedForCurrentUniqueToken.set(0);
					final var nextUniqTokenNo = uniqueTokensCreated.incrementAndGet();
					currentUniqueToken.set(uniqueTokenNameFn.apply(nextUniqTokenNo));
					if (nextUniqTokenNo >= numUniqTokens.get()) {
						System.out.println("Done creating " + nextUniqTokenNo
								+ " unique tokens w/ at least "
								+ (nftsPerUniqToken.get() * nextUniqTokenNo) + " NFTs");
						done.set(true);
					}
					return Optional.empty();
				}
			}
		};
	}

	private List<HapiSpecOperation> burstedUniqCreations(int perBurst, int numTreasuries, long pauseMs) {
		final var createdSoFar = new AtomicInteger(0);
		List<HapiSpecOperation> ans = new ArrayList<>();
		final var configuredNumUniqTokens = numUniqTokens.get();
		while (createdSoFar.get() < configuredNumUniqTokens) {
			var thisBurst = Math.min(configuredNumUniqTokens - createdSoFar.get(), perBurst);
			final var burst = inParallel(IntStream.range(0, thisBurst)
					.mapToObj(i -> tokenCreate(uniqueTokenNameFn.apply(i + createdSoFar.get()))
							.payingWith(GENESIS)
							.tokenType(NON_FUNGIBLE_UNIQUE)
							.deferStatusResolution()
							.noLogging()
							.supplyType(INFINITE)
							.initialSupply(0)
							.supplyKey(GENESIS)
							.treasury(treasuryNameFn.apply((i + createdSoFar.get()) % numTreasuries))
							.exposingCreatedIdTo(newId -> {
								final var newN = numFrom(newId);
								if (newN < numFrom(firstCreatedId.get())) {
									firstCreatedId.set(newId);
								} else if (lastCreatedId.get() == null || newN > numFrom(lastCreatedId.get())) {
									lastCreatedId.set(newId);
								}
								if (newN % 100 == 0) {
									System.out.println("Resolved creation for " + newId);
								}
							}))
					.toArray(HapiSpecOperation[]::new));
			ans.add(burst);
			ans.add(sleepFor(pauseMs));
			createdSoFar.addAndGet(thisBurst);
		}
		return ans;
	}

	private long numFrom(String id) {
		if (id == null) {
			return Long.MAX_VALUE;
		}
		return Long.parseLong(id.substring(id.lastIndexOf('.') + 1));
	}

	private ByteString metadataFor(String uniqToken, int nftNo) {
		final var base = new StringBuilder(uniqToken).append("-SN").append(nftNo);
		var padding = metadataSize.get() - base.length();
		while (padding-- > 0) {
			base.append("_");
		}
		return ByteString.copyFromUtf8(base.toString());
	}

	private HapiSpecOperation customPropsMgmt() {
		return withOpContext((spec, opLog) -> {
			var ciProps = spec.setup().ciPropertiesMap();
			if (ciProps.has("mintTps")) {
				mintTps.set(ciProps.getInteger("mintTps"));
			}
			if (ciProps.has("numUniqTokens")) {
				numUniqTokens.set(ciProps.getInteger("numUniqTokens"));
			}
			if (ciProps.has("setupUniqTokensBurstSize")) {
				setupUniqTokensBurstSize.set(ciProps.getInteger("setupUniqTokensBurstSize"));
			}
			if (ciProps.has("setupUniqTokensBurstPauseMs")) {
				setupUniqTokensBurstPauseMs.set(ciProps.getInteger("setupUniqTokensBurstPauseMs"));
			}
			if (ciProps.has("nftsPerUniqToken")) {
				nftsPerUniqToken.set(ciProps.getInteger("nftsPerUniqToken"));
			}
			if (ciProps.has("nftsPerMintOp")) {
				nftsPerMintOp.set(ciProps.getInteger("nftsPerMintOp"));
			}
			if (ciProps.has("uniqTokensPerTreasury")) {
				uniqTokensPerTreasury.set(ciProps.getInteger("uniqTokensPerTreasury"));
			}
			if (ciProps.has("metadataSize")) {
				metadataSize.set(ciProps.getInteger("metadataSize"));
			}
			if (ciProps.has("durationToRunMints")) {
				durationToRunMints.set(ciProps.getLong("durationToRunMints"));
			}
			if (ciProps.has("secsToRunPostMintXfers")) {
				durationToRunPostMintXfers.set(ciProps.getLong("secsToRunPostMintXfers"));
			}
			if (ciProps.has("mintDurationUnit")) {
				mintDurationUnit.set(ciProps.getTimeUnit("mintDurationUnit"));
			}
			if (ciProps.has("xferDurationUnit")) {
				xferDurationUnit.set(ciProps.getTimeUnit("xferDurationUnit"));
			}

			opLog.info("Running with configuration: " +
					"\n\t\tnumUniqTokens               =" + numUniqTokens.get() +
					"\n\t\tnftsPerUniqToken            =" + nftsPerUniqToken.get() +
					"\n\t\t  --> TOTAL NFTs            = " + (numUniqTokens.get() * nftsPerUniqToken.get()) +
					"\n\t\tmintTps                     =" + mintTps.get() +
					"\n\t\tmintDurationUnit            =" + mintDurationUnit.get() +
					"\n\t\tdurationToRunMints          =" + durationToRunMints.get() +
					"\n\t\tnftsPerMintOp               =" + nftsPerMintOp.get() +
					"\n\t\tmetadataSize                =" + metadataSize.get() +
					"\n\t\txferTps                     =" + xferTps.get() +
					"\n\t\txferDurationUnit            =" + xferDurationUnit.get() +
					"\n\t\tdurationToRunPostMintXfers  =" + durationToRunPostMintXfers.get() +
					"\n\t\tsetupUniqTokensBurstSize    =" + setupUniqTokensBurstSize.get() +
					"\n\t\tsetupUniqTokensBurstPauseMs =" + setupUniqTokensBurstPauseMs.get() +
					"\n\t\tuniqTokensPerTreasury       =" + uniqTokensPerTreasury.get()
			);
		});
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private static final int DEFAULT_XFER_TPS = 250;
	private final AtomicInteger xferTps = new AtomicInteger(DEFAULT_XFER_TPS);
	private static final int DEFAULT_MINT_TPS = 250;
	private final AtomicInteger mintTps = new AtomicInteger(DEFAULT_MINT_TPS);
	private static final int DEFAULT_NUM_UNIQ_TOKENS = 10_000;
	private final AtomicInteger numUniqTokens = new AtomicInteger(DEFAULT_NUM_UNIQ_TOKENS);
	private static final int DEFAULT_SETUP_UNIQ_TOKENS_BURST_SIZE = 1000;
	private final AtomicInteger setupUniqTokensBurstSize = new AtomicInteger(DEFAULT_SETUP_UNIQ_TOKENS_BURST_SIZE);
	private static final int DEFAULT_SETUP_UNIQ_TOKENS_BURST_PAUSE_MS = 2500;
	private final AtomicInteger setupUniqTokensBurstPauseMs = new AtomicInteger(
			DEFAULT_SETUP_UNIQ_TOKENS_BURST_PAUSE_MS);
	private static final int DEFAULT_NFTS_PER_UNIQ_TOKEN = 1000;
	private final AtomicInteger nftsPerUniqToken = new AtomicInteger(DEFAULT_NFTS_PER_UNIQ_TOKEN);
	private static final int DEFAULT_NFTS_PER_MINT_OP = 10;
	private final AtomicInteger nftsPerMintOp = new AtomicInteger(DEFAULT_NFTS_PER_MINT_OP);
	private static final int DEFAULT_UNIQ_TOKENS_PER_TREASURY = 500;
	private final AtomicInteger uniqTokensPerTreasury = new AtomicInteger(DEFAULT_UNIQ_TOKENS_PER_TREASURY);
	private static final int DEFAULT_METADATA_SIZE = 100;
	private final AtomicInteger metadataSize = new AtomicInteger(DEFAULT_METADATA_SIZE);
	private static final long DEFAULT_SECS_TO_RUN_MINTS =
			(DEFAULT_NUM_UNIQ_TOKENS * DEFAULT_NFTS_PER_UNIQ_TOKEN)
					/ (DEFAULT_MINT_TPS * DEFAULT_NFTS_PER_MINT_OP);
	private final AtomicLong durationToRunMints = new AtomicLong(DEFAULT_SECS_TO_RUN_MINTS);
	private static final long DEFAULT_SEC_TO_RUN_POST_MINT_XFERS = 24 * 3600;
	private final AtomicLong durationToRunPostMintXfers = new AtomicLong(DEFAULT_SEC_TO_RUN_POST_MINT_XFERS);
	private final AtomicReference<TimeUnit> mintDurationUnit = new AtomicReference<>(SECONDS);
	private final AtomicReference<TimeUnit> xferDurationUnit = new AtomicReference<>(SECONDS);
}
