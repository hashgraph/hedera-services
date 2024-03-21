/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites;

import static com.hedera.services.bdd.suites.HapiSuite.FinalOutcome.SUITE_FAILED;
import static com.hedera.services.bdd.suites.HapiSuite.FinalOutcome.SUITE_PASSED;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.Logger;

public abstract class HapiSuite {
    // The first 0 refers to the shard of the target network.
    // The second 0 refers to the realm.
    public static final String DEFAULT_SHARD_REALM = "0.0.";
    public static final String TRUE_VALUE = "true";
    public static final String FALSE_VALUE = "false";
    public static final String TOKEN_UNDER_TEST = "TokenUnderTest";
    protected static String ALICE = "ALICE";
    protected static String BOB = "BOB";
    protected static String CAROL = "CAROL";
    protected static String RED_PARTITION = "RED_PARTITION";
    protected static String BLUE_PARTITION = "BLUE_PARTITION";
    protected static String GREEN_PARTITION = "GREEN_PARTITION";
    protected static String CIVILIAN_PAYER = "CIVILIAN_PAYER";
    public static long FUNGIBLE_INITIAL_SUPPLY = 1_000_000_000L;
    public static long NON_FUNGIBLE_INITIAL_SUPPLY = 10L;
    public static long FUNGIBLE_INITIAL_BALANCE = FUNGIBLE_INITIAL_SUPPLY / 100;
    private static final String STARTING_SUITE = "-------------- STARTING {} SUITE --------------";

    public enum FinalOutcome {
        SUITE_PASSED,
        SUITE_FAILED
    }

    @SuppressWarnings("java:S2245") // using java.util.Random in tests is fine
    private static final Random RANDOM = new Random(16851L);

    protected abstract Logger getResultsLogger();

    public abstract List<HapiSpec> getSpecsInSuite();

    public List<HapiSpec> getSpecsInSuiteWithOverrides() {
        final var specs = getSpecsInSuite();
        if (!overrides.isEmpty()) {
            specs.forEach(spec -> spec.addOverrideProperties(overrides));
        }
        return specs;
    }

    public static final Key EMPTY_KEY =
            Key.newBuilder().setKeyList(KeyList.newBuilder().build()).build();

    public static final Key STANDIN_CONTRACT_ID_KEY = Key.newBuilder()
            .setContractID(ContractID.newBuilder().setContractNum(0).build())
            .build();
    private static final int BYTES_PER_KB = 1024;
    public static final int MAX_CALL_DATA_SIZE = 6 * BYTES_PER_KB;
    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    // Useful for testing overflow scenarios when an ERC-20/721 ABI specifies
    // a uint256, but a valid value on Hedera will be an 8-byte long only
    public static final BigInteger MAX_UINT256_VALUE =
            new BigInteger("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
    public static final long ADEQUATE_FUNDS = 10_000_000_000L;
    public static final long ONE_HBAR = 100_000_000L;
    public static final long TINY_PARTS_PER_WHOLE = 100_000_000L;
    public static final long FIVE_HBARS = 5 * ONE_HBAR;
    public static final long ONE_HUNDRED_HBARS = 100 * ONE_HBAR;
    public static final long THOUSAND_HBAR = 1_000 * ONE_HBAR;
    public static final long ONE_MILLION_HBARS = 1_000_000L * ONE_HBAR;
    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;

    public static final String CHAIN_ID_PROP = "contracts.chainId";
    public static final String CRYPTO_CREATE_WITH_ALIAS_ENABLED = "cryptoCreateWithAlias.enabled";
    public static final Integer CHAIN_ID = 298;
    public static final String ETH_HASH_KEY = "EthHash";
    public static final String ETH_SENDER_ADDRESS = "EthSenderAddress";

    public static final String RELAYER = "RELAYER";
    public static final KeyShape SECP_256K1_SHAPE = KeyShape.SECP256K1;
    public static final String SECP_256K1_SOURCE_KEY = "secp256k1Alias";
    public static final String SECP_256K1_RECEIVER_SOURCE_KEY = "secp256k1ReceiverAlias";
    public static final String TOKEN_TREASURY = "treasury";
    public static final String UPDATED_TREASURY = "NewTreasury";
    public static final String NONSENSE_KEY = "Jabberwocky!";
    public static final String ZERO_BYTE_MEMO = "\u0000kkkk";
    public static final String NODE = HapiSpecSetup.getDefaultInstance().defaultNodeName();
    public static final String HBAR_TOKEN_SENTINEL = "HBAR";
    public static final String SYSTEM_ADMIN = HapiSpecSetup.getDefaultInstance().strongControlName();
    public static final String FREEZE_ADMIN = HapiSpecSetup.getDefaultInstance().freezeAdminName();
    public static final String FUNDING = HapiSpecSetup.getDefaultInstance().fundingAccountName();
    public static final String STAKING_REWARD =
            HapiSpecSetup.getDefaultInstance().stakingRewardAccountName();
    public static final String NODE_REWARD = HapiSpecSetup.getDefaultInstance().nodeRewardAccountName();
    public static final String FEE_COLLECTOR =
            HapiSpecSetup.getDefaultInstance().feeCollectorAccountName();

    public static final String GENESIS = HapiSpecSetup.getDefaultInstance().genesisAccountName();
    public static final String DEFAULT_PAYER =
            HapiSpecSetup.getDefaultInstance().defaultPayerName();
    public static final String DEFAULT_CONTRACT_SENDER = "DEFAULT_CONTRACT_SENDER";
    public static final String DEFAULT_CONTRACT_RECEIVER = "DEFAULT_CONTRACT_RECEIVER";
    public static final String ADDRESS_BOOK_CONTROL =
            HapiSpecSetup.getDefaultInstance().addressBookControlName();
    public static final String FEE_SCHEDULE_CONTROL =
            HapiSpecSetup.getDefaultInstance().feeScheduleControlName();
    public static final String EXCHANGE_RATE_CONTROL =
            HapiSpecSetup.getDefaultInstance().exchangeRatesControlName();
    public static final String SYSTEM_DELETE_ADMIN =
            HapiSpecSetup.getDefaultInstance().systemDeleteAdminName();
    public static final String SYSTEM_UNDELETE_ADMIN =
            HapiSpecSetup.getDefaultInstance().systemUndeleteAdminName();
    public static final String NODE_DETAILS = HapiSpecSetup.getDefaultInstance().nodeDetailsName();
    public static final String ADDRESS_BOOK = HapiSpecSetup.getDefaultInstance().addressBookName();
    public static final String EXCHANGE_RATES =
            HapiSpecSetup.getDefaultInstance().exchangeRatesName();
    public static final String FEE_SCHEDULE = HapiSpecSetup.getDefaultInstance().feeScheduleName();
    public static final String APP_PROPERTIES =
            HapiSpecSetup.getDefaultInstance().appPropertiesFile();
    public static final String API_PERMISSIONS =
            HapiSpecSetup.getDefaultInstance().apiPermissionsFile();
    public static final String UPDATE_ZIP_FILE =
            HapiSpecSetup.getDefaultInstance().updateFeatureName();
    public static final String THROTTLE_DEFS =
            HapiSpecSetup.getDefaultInstance().throttleDefinitionsName();

    public static final HapiSpecSetup DEFAULT_PROPS = HapiSpecSetup.getDefaultInstance();
    public static final String ETH_SUFFIX = "_Eth";

    private boolean deferResultsSummary = false;
    private boolean onlyLogHeader = false;
    private boolean tearDownClientsAfter = true;
    private List<HapiSpec> finalSpecs = Collections.emptyList();

    private Map<String, Object> overrides = Collections.emptyMap();

    public String name() {
        String simpleName = this.getClass().getSimpleName();

        simpleName = !simpleName.endsWith("Suite")
                ? simpleName
                : simpleName.substring(0, simpleName.length() - "Suite".length());
        return simpleName;
    }

    public List<HapiSpec> getFinalSpecs() {
        return finalSpecs;
    }

    public boolean canRunConcurrent() {
        return false;
    }

    public void skipClientTearDown() {
        this.tearDownClientsAfter = false;
    }

    public void deferResultsSummary() {
        this.deferResultsSummary = true;
    }

    public void setOnlyLogHeader() {
        this.onlyLogHeader = true;
    }

    public boolean getDeferResultsSummary() {
        return deferResultsSummary;
    }

    public void summarizeDeferredResults() {
        if (getDeferResultsSummary()) {
            deferResultsSummary = false;
            summarizeResults(getResultsLogger());
        }
    }

    public void runSuiteConcurrentWithOverrides(final Map<String, Object> overrides) {
        this.overrides = overrides;
        runSuiteAsync();
    }

    public void runSuiteSequentialWithOverrides(final Map<String, Object> overrides) {
        this.overrides = overrides;
        runSuiteSync();
    }

    public void setOverrides(final Map<String, Object> overrides) {
        this.overrides = overrides;
    }

    public FinalOutcome runSuiteAsync() {
        return runSuite(HapiSuite::runConcurrentSpecs);
    }

    public FinalOutcome runSuiteSync() {
        return runSuite(HapiSuite::runSequentialSpecs);
    }

    public FinalOutcome runSpecSync(HapiSpec spec, List<HapiTestNode> nodes) {
        if (!overrides.isEmpty()) {
            spec.addOverrideProperties(overrides);
        }

        final var name = name();
        spec.setSuitePrefix(name);
        spec.setNodes(nodes);
        spec.run();
        finalSpecs = List.of(spec);
        //        summarizeResults(getResultsLogger());
        if (tearDownClientsAfter) {
            HapiApiClients.tearDown();
        }

        return finalOutcomeFor(finalSpecs);
    }

    protected FinalOutcome finalOutcomeFor(final List<HapiSpec> completedSpecs) {
        return completedSpecs.stream().allMatch(HapiSpec::ok) ? SUITE_PASSED : SUITE_FAILED;
    }

    @SuppressWarnings("java:S2629")
    private FinalOutcome runSuite(Consumer<List<HapiSpec>> runner) {
        if (!getDeferResultsSummary() || onlyLogHeader) {
            getResultsLogger().info(STARTING_SUITE, name());
        }

        List<HapiSpec> specs = getSpecsInSuite();
        boolean autoSnapshotManagementOn = false;
        for (final var spec : specs) {
            autoSnapshotManagementOn |= spec.setup().autoSnapshotManagement();
            if (!overrides.isEmpty()) {
                spec.addOverrideProperties(overrides);
            }
            if (spec.isOnlySpecToRunInSuite()) {
                specs = List.of(spec);
                break;
            }
        }
        if (autoSnapshotManagementOn) {
            // Coerce to sequential spec runner if auto-snapshot management is on for any spec
            // (concurrent spec execution makes it impossible to match record stream snapshots)
            runner = HapiSuite::runSequentialSpecs;
        }
        final var name = name();
        specs.forEach(spec -> spec.setSuitePrefix(name));
        runner.accept(specs);
        finalSpecs = specs;
        summarizeResults(getResultsLogger());
        if (tearDownClientsAfter) {
            HapiApiClients.tearDown();
        }
        return finalOutcomeFor(finalSpecs);
    }

    @SuppressWarnings({"java:S3358", "java:S3740"})
    public static HapiSpecOperation[] flattened(Object... ops) {
        return Stream.of(ops)
                .map(op -> (op instanceof HapiSpecOperation hapiOp)
                        ? new HapiSpecOperation[] {hapiOp}
                        : ((op instanceof List list)
                                ? list.toArray(new HapiSpecOperation[0])
                                : (HapiSpecOperation[]) op))
                .flatMap(Stream::of)
                .toArray(HapiSpecOperation[]::new);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    protected final List<HapiSpec> allOf(final List<HapiSpec>... specLists) {
        return Arrays.stream(specLists).flatMap(List::stream).toList();
    }

    protected HapiSpecOperation[] asOpArray(int n, IntFunction<HapiSpecOperation> factory) {
        return IntStream.range(0, n).mapToObj(factory).toArray(HapiSpecOperation[]::new);
    }

    @SuppressWarnings("java:S2629")
    private void summarizeResults(Logger log) {
        if (getDeferResultsSummary()) {
            return;
        }
        log.info(STARTING_SUITE, name());
        log.info("-------------- RESULTS OF {} SUITE --------------", name());
        for (HapiSpec spec : finalSpecs) {
            log.info(spec);
        }
    }

    private static void runSequentialSpecs(final List<HapiSpec> specs) {
        specs.forEach(Runnable::run);
    }

    public static void runConcurrentSpecs(final List<HapiSpec> specs) {
        final var futures = specs.stream()
                .map(r -> CompletableFuture.runAsync(r, HapiSpec.getCommonThreadPool()))
                .<CompletableFuture<Void>>toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
    }

    public static String salted(String str) {
        return str + RANDOM.nextInt(1_234_567);
    }
}
