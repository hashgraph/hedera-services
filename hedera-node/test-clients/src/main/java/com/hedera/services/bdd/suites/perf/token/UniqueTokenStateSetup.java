/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.services.bdd.suites.perf.token;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.mgmtOfIntProp;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.mgmtOfLongProp;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static com.hederahashgraph.api.proto.java.TokenSupplyType.INFINITE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A client that creates some number of NON_FUNGIBLE_UNIQUE tokens, and then for each token mints
 * some number of NFTs, each w/ the requested number of bytes of metadata. All tokens are created
 * using the dev treasury key as the token supply key.
 *
 * <p>The exact number of entities to create can be configured using the constants at the top of the
 * class definition.
 *
 * <p><b>IMPORTANT:</b> Please note the following two items:
 *
 * <ol>
 *   <li>If creating a large number of NFTs, e.g. 1M+, it is essential to comment out the body of
 *       the {@link
 *       com.hedera.services.bdd.spec.transactions.token.HapiTokenMint#updateStateOf(HapiSpec)}
 *       method, since it adds the minted token's creation time to the registry and the client will
 *       run OOM fairly quickly with a 1GB heap.
 *   <li>There is evidence of slower memory leaks hidden elsewhere in the EET infrastructure, so you
 *       should probably not try to create more than 10M NFTs using a single run of this client; if
 *       more NFTs are needed, then please run several instances in sequence.
 *       <p>Also for creating even more NFTs, it will be easier to run the JRS workflow {@code
 *       GCP-Create-Large-Volume-NFTs-SavedState.json} scheduled with CircleCi and modify the
 *       parameters here and the parameters in the JRS workflow and its referenced test config as
 *       well.
 * </ol>
 */
public class UniqueTokenStateSetup extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UniqueTokenStateSetup.class);

    private final IntFunction<String> treasuryNameFn = i -> "treasury" + i;
    private final IntFunction<String> uniqueTokenNameFn = i -> "uniqueToken" + i;

    private final AtomicLong duration = new AtomicLong(SECS_TO_RUN);
    private final AtomicLong prepDuration = new AtomicLong(PREP_SECS_TO_RUN);
    private final AtomicInteger maxOpsPerSec = new AtomicInteger(MINT_TPS);
    private final AtomicInteger maxPrepOpsPerSecs = new AtomicInteger(CRYPTO_CREATE_TPS);
    private final AtomicInteger numXferPrepAccounts = new AtomicInteger(NUM_PREPPED_XFER_ACCOUNTS);
    private final AtomicInteger numTokens = new AtomicInteger(NUM_UNIQ_TOKENS);
    private final AtomicInteger numNftsPerToken = new AtomicInteger(NFTS_PER_UNIQ_TOKEN);
    private final AtomicInteger numNftsPerMintOp = new AtomicInteger(NEW_NFTS_PER_MINT_OP);
    private final AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);

    public static void main(String... args) {
        new UniqueTokenStateSetup().runSuiteSync();
        System.out.println("Created unique tokens from " + firstCreatedId + " + to " + lastCreatedId);
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            createNfts(),
        });
    }

    private HapiSpec createNfts() {
        return defaultHapiSpec("CreateNfts")
                .given(
                        stdMgmtOf(duration, unit, maxOpsPerSec, "mint_"),
                        mgmtOfIntProp(numTokens, "mint_numTokens"),
                        mgmtOfIntProp(numNftsPerToken, "mint_numNftsPerToken"),
                        mgmtOfIntProp(numNftsPerMintOp, "mint_numNftsPerMintOp"),
                        mgmtOfIntProp(maxPrepOpsPerSecs, "mint_maxPrepOpsPerSecs"),
                        mgmtOfIntProp(numXferPrepAccounts, "mint_numXferPrepAccounts"),
                        mgmtOfLongProp(prepDuration, "mint_prepDuration"),
                        withOpContext((spec, opLog) -> opLog.info(
                                "Resolved configuration:\n  "
                                        + "mint_prepDuration={}\n  "
                                        + "mint_numXferPrepAccounts={}\n  "
                                        + "mint_maxPrepOpsPerSecs={}\n  "
                                        + "mint_duration={}\n  "
                                        + "mint_maxOpsPerSec={}\n  "
                                        + "mint_numTokens={}\n  "
                                        + "mint_numNftsPerToken={}\n  "
                                        + "mint_numNftsPerMintOp={}",
                                prepDuration.get(),
                                numXferPrepAccounts.get(),
                                maxPrepOpsPerSecs.get(),
                                duration.get(),
                                maxPrepOpsPerSecs.get(),
                                numTokens.get(),
                                numNftsPerToken.get(),
                                numNftsPerMintOp.get())))
                .when(
                        runWithProvider(xferPrepAccountFactory())
                                .lasting(prepDuration::get, unit::get)
                                .maxOpsPerSec(maxPrepOpsPerSecs::get),
                        sleepFor(20_000L))
                .then(runWithProvider(nftFactory())
                        .lasting(duration::get, unit::get)
                        .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> xferPrepAccountFactory() {
        final AtomicInteger xferAccountsCreated = new AtomicInteger(0);

        return spec -> (OpProvider) () -> {
            if (xferAccountsCreated.get() >= numXferPrepAccounts.get()) {
                return Optional.empty();
            }
            final var op = cryptoCreate("xferPrep" + xferAccountsCreated.getAndIncrement())
                    .payingWith(GENESIS)
                    .key(GENESIS)
                    .balance(10 * ONE_HUNDRED_HBARS)
                    .hasPrecheckFrom(OK, DUPLICATE_TRANSACTION)
                    .hasKnownStatusFrom(SUCCESS, UNKNOWN, TRANSACTION_EXPIRED)
                    .noLogging()
                    .rememberingNothing()
                    .deferStatusResolution();
            return Optional.of(op);
        };
    }

    private Function<HapiSpec, OpProvider> nftFactory() {
        final AtomicInteger uniqueTokensCreated = new AtomicInteger(0);
        final AtomicInteger nftsMintedForCurrentUniqueToken = new AtomicInteger(0);
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicReference<String> currentUniqueToken = new AtomicReference<>(uniqueTokenNameFn.apply(0));

        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                final var numTreasuries = numTokens.get() / UNIQ_TOKENS_PER_TREASURY
                        + Math.min(1, numTokens.get() % UNIQ_TOKENS_PER_TREASURY);
                final List<HapiSpecOperation> inits = new ArrayList<>();
                inits.add(inParallel(IntStream.range(0, numTreasuries)
                        .mapToObj(i -> cryptoCreate(treasuryNameFn.apply(i))
                                .payingWith(GENESIS)
                                .balance(0L)
                                .noLogging()
                                .key(GENESIS)
                                .hasRetryPrecheckFrom(DUPLICATE_TRANSACTION)
                                .hasKnownStatusFrom(SUCCESS, UNKNOWN, TRANSACTION_EXPIRED)
                                .deferStatusResolution())
                        .toArray(HapiSpecOperation[]::new)));
                inits.add(sleepFor(5_000L));
                inits.addAll(
                        burstedUniqCreations(UNIQ_TOKENS_BURST_SIZE, numTreasuries, UNIQ_TOKENS_POST_BURST_PAUSE_MS));
                return inits;
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                if (done.get()) {
                    return Optional.empty();
                }

                final var currentToken = currentUniqueToken.get();
                if (nftsMintedForCurrentUniqueToken.get() < numNftsPerToken.get()) {
                    final List<ByteString> allMeta = new ArrayList<>();
                    final int noMoreThan = numNftsPerToken.get() - nftsMintedForCurrentUniqueToken.get();
                    for (int i = 0, n = Math.min(noMoreThan, numNftsPerMintOp.get()); i < n; i++) {
                        final var nextSerialNo = nftsMintedForCurrentUniqueToken.incrementAndGet();
                        allMeta.add(metadataFor(currentToken, nextSerialNo));
                    }
                    final var op = mintToken(currentToken, allMeta)
                            .payingWith(GENESIS)
                            .rememberingNothing()
                            .deferStatusResolution()
                            .fee(ONE_HBAR)
                            .hasPrecheckFrom(OK, DUPLICATE_TRANSACTION)
                            .hasKnownStatusFrom(SUCCESS, UNKNOWN, OK, TRANSACTION_EXPIRED, INVALID_TOKEN_ID)
                            .noLogging();
                    return Optional.of(op);
                } else {
                    nftsMintedForCurrentUniqueToken.set(0);
                    final var nextUniqTokenNo = uniqueTokensCreated.incrementAndGet();
                    currentUniqueToken.set(uniqueTokenNameFn.apply(nextUniqTokenNo));
                    if (nextUniqTokenNo >= numTokens.get()) {
                        System.out.println("Done creating "
                                + nextUniqTokenNo
                                + " unique tokens w/ approximately "
                                + (numNftsPerToken.get() * nextUniqTokenNo)
                                + " NFTs");
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
        while (createdSoFar.get() < numTokens.get()) {
            var thisBurst = Math.min(numTokens.get() - createdSoFar.get(), perBurst);
            final var burst = inParallel(IntStream.range(0, thisBurst)
                    .mapToObj(i -> tokenCreate(uniqueTokenNameFn.apply(i + createdSoFar.get()))
                            .payingWith(GENESIS)
                            .tokenType(NON_FUNGIBLE_UNIQUE)
                            .deferStatusResolution()
                            .noLogging()
                            .supplyType(INFINITE)
                            .initialSupply(0)
                            .supplyKey(GENESIS)
                            .hasPrecheckFrom(OK, DUPLICATE_TRANSACTION)
                            .hasKnownStatusFrom(SUCCESS, UNKNOWN, TRANSACTION_EXPIRED)
                            .treasury(treasuryNameFn.apply((i + createdSoFar.get()) % numTreasuries))
                            .exposingCreatedIdTo(newId -> {
                                final var newN = numFrom(newId);
                                if (newN < numFrom(firstCreatedId.get())) {
                                    firstCreatedId.set(newId);
                                    lastCreatedId.set(newId);
                                } else if (lastCreatedId.get() == null || newN > numFrom(lastCreatedId.get())) {
                                    lastCreatedId.set(newId);
                                }
                                if (newN % 100 == 0) {
                                    System.out.println("Resolved" + " creation" + " for " + newId);
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
        var padding = METADATA_SIZE - base.length();
        while (padding-- > 0) {
            base.append("_");
        }
        return ByteString.copyFromUtf8(base.toString());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private static final AtomicReference<String> firstCreatedId = new AtomicReference<>(null);
    private static final AtomicReference<String> lastCreatedId = new AtomicReference<>(null);

    private static final long SECS_TO_RUN = 4050;
    private static final long PREP_SECS_TO_RUN = 25;
    private static final int MINT_TPS = 250;
    private static final int CRYPTO_CREATE_TPS = 1000;
    private static final int NUM_PREPPED_XFER_ACCOUNTS = 10_000;
    private static final int NUM_UNIQ_TOKENS = 10_000;
    private static final int UNIQ_TOKENS_BURST_SIZE = 1000;
    private static final int UNIQ_TOKENS_POST_BURST_PAUSE_MS = 10_000;
    private static final int NFTS_PER_UNIQ_TOKEN = 1000;
    private static final int NEW_NFTS_PER_MINT_OP = 10;
    private static final int UNIQ_TOKENS_PER_TREASURY = 500;
    private static final int METADATA_SIZE = 100;
}
