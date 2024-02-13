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

package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.BYTES_4K;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTransactionID;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate.getUpdated121;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.log;
import static com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector.allNodes;
import static com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector.byName;
import static com.hedera.services.bdd.spec.utilops.pauses.HapiSpecWaitUntil.untilJustBeforeStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.pauses.HapiSpecWaitUntil.untilStartOfNextAdhocPeriod;
import static com.hedera.services.bdd.spec.utilops.pauses.HapiSpecWaitUntil.untilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATE_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.FALSE_VALUE;
import static com.hedera.services.bdd.suites.HapiSuite.FEE_SCHEDULE;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.TargetNetworkType.HAPI_TEST_NETWORK;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.traceability.TraceabilitySuite.SIDECARS_PROP;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_ABORT;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_ONLY;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_UPGRADE;
import static com.hederahashgraph.api.proto.java.FreezeType.PREPARE_UPGRADE;
import static com.hederahashgraph.api.proto.java.FreezeType.TELEMETRY_UPGRADE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateStreamFileNameFromInstant;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.consensus.HapiMessageSubmit;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiEthereumCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.transactions.file.HapiFileAppend;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.spec.transactions.file.UploadProgress;
import com.hedera.services.bdd.spec.transactions.system.HapiFreeze;
import com.hedera.services.bdd.spec.utilops.checks.VerifyGetAccountNftInfosNotSupported;
import com.hedera.services.bdd.spec.utilops.checks.VerifyGetBySolidityIdNotSupported;
import com.hedera.services.bdd.spec.utilops.checks.VerifyGetFastRecordNotSupported;
import com.hedera.services.bdd.spec.utilops.checks.VerifyGetLiveHashNotSupported;
import com.hedera.services.bdd.spec.utilops.checks.VerifyGetStakersNotSupported;
import com.hedera.services.bdd.spec.utilops.checks.VerifyGetTokenNftInfosNotSupported;
import com.hedera.services.bdd.spec.utilops.grouping.InBlockingOrder;
import com.hedera.services.bdd.spec.utilops.grouping.ParallelSpecOps;
import com.hedera.services.bdd.spec.utilops.inventory.NewSpecKey;
import com.hedera.services.bdd.spec.utilops.inventory.NewSpecKeyList;
import com.hedera.services.bdd.spec.utilops.inventory.RecordSystemProperty;
import com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromEcdsaFile;
import com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromFile;
import com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromLiteral;
import com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromMnemonic;
import com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromMutation;
import com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromPem;
import com.hedera.services.bdd.spec.utilops.inventory.UsableTxnId;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.ShutDownNodesOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.StartNodesOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.WaitForActiveOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.WaitForBehindOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.WaitForFreezeOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.WaitForReconnectOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.WaitForShutdownOp;
import com.hedera.services.bdd.spec.utilops.pauses.HapiSpecSleep;
import com.hedera.services.bdd.spec.utilops.pauses.HapiSpecWaitUntil;
import com.hedera.services.bdd.spec.utilops.pauses.NodeLivenessTimeout;
import com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode;
import com.hedera.services.bdd.spec.utilops.records.SnapshotMode;
import com.hedera.services.bdd.spec.utilops.records.SnapshotModeOp;
import com.hedera.services.bdd.spec.utilops.streams.RecordAssertions;
import com.hedera.services.bdd.spec.utilops.streams.RecordFileChecker;
import com.hedera.services.bdd.spec.utilops.streams.RecordStreamVerification;
import com.hedera.services.bdd.spec.utilops.streams.assertions.AssertingBiConsumer;
import com.hedera.services.bdd.spec.utilops.streams.assertions.CryptoCreateAssertion;
import com.hedera.services.bdd.spec.utilops.streams.assertions.EventualAssertion;
import com.hedera.services.bdd.spec.utilops.streams.assertions.EventualRecordStreamAssertion;
import com.hedera.services.bdd.spec.utilops.streams.assertions.RecordStreamAssertion;
import com.hedera.services.bdd.spec.utilops.streams.assertions.TransactionBodyAssertion;
import com.hedera.services.bdd.spec.utilops.streams.assertions.ValidContractIdsAssertion;
import com.hedera.services.bdd.spec.utilops.throughput.FinishThroughputObs;
import com.hedera.services.bdd.spec.utilops.throughput.StartThroughputObs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import com.hedera.services.bdd.suites.perf.topic.HCSChunkingRealisticPerfSuite;
import com.hedera.services.bdd.suites.utils.RecordStreamType;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.FeesJsonToGrpcBytes;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.SysFileSerde;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.junit.jupiter.api.Assertions;

public class UtilVerbs {

    /**
     * Private constructor to prevent instantiation.
     *
     * @throws UnsupportedOperationException if invoked by reflection or other means.
     */
    private UtilVerbs() {
        throw new UnsupportedOperationException();
    }

    public static HapiFreeze freeze() {
        return new HapiFreeze();
    }

    public static HapiFreeze prepareUpgrade() {
        return new HapiFreeze(PREPARE_UPGRADE);
    }

    public static HapiFreeze telemetryUpgrade() {
        return new HapiFreeze(TELEMETRY_UPGRADE);
    }

    public static HapiFreeze freezeOnly() {
        return new HapiFreeze(FREEZE_ONLY);
    }

    public static HapiFreeze freezeUpgrade() {
        return new HapiFreeze(FREEZE_UPGRADE);
    }

    public static HapiFreeze freezeAbort() {
        return new HapiFreeze(FREEZE_ABORT);
    }

    /* Some fairly simple utility ops */
    public static InBlockingOrder blockingOrder(HapiSpecOperation... ops) {
        return new InBlockingOrder(ops);
    }

    public static NodeLivenessTimeout withLiveNode(String node) {
        return new NodeLivenessTimeout(node);
    }

    public static <T> RecordSystemProperty<T> recordSystemProperty(
            String property, Function<String, T> converter, Consumer<T> historian) {
        return new RecordSystemProperty<>(property, converter, historian);
    }

    public static NetworkTypeFilterOp ifHapiTest(@NonNull final HapiSpecOperation... ops) {
        return new NetworkTypeFilterOp(EnumSet.of(HAPI_TEST_NETWORK), ops);
    }

    public static NetworkTypeFilterOp ifNotHapiTest(@NonNull final HapiSpecOperation... ops) {
        return new NetworkTypeFilterOp(EnumSet.complementOf(EnumSet.of(HAPI_TEST_NETWORK)), ops);
    }

    public static EnvFilterOp ifCi(@NonNull final HapiSpecOperation... ops) {
        requireNonNull(ops);
        return new EnvFilterOp(EnvFilterOp.EnvType.CI, ops);
    }

    public static EnvFilterOp ifNotCi(@NonNull final HapiSpecOperation... ops) {
        requireNonNull(ops);
        return new EnvFilterOp(EnvFilterOp.EnvType.NOT_CI, ops);
    }

    public static SourcedOp sourcing(Supplier<HapiSpecOperation> source) {
        return new SourcedOp(source);
    }

    public static ContextualSourcedOp sourcingContextual(Function<HapiSpec, HapiSpecOperation> source) {
        return new ContextualSourcedOp(source);
    }

    public static ContextualActionOp doingContextual(Consumer<HapiSpec> action) {
        return new ContextualActionOp(action);
    }

    public static WaitForActiveOp waitForNodeToBecomeActive(String name, int waitSeconds) {
        return new WaitForActiveOp(byName(name), waitSeconds);
    }

    public static WaitForActiveOp waitForNodesToBecomeActive(int waitSeconds) {
        return new WaitForActiveOp(allNodes(), waitSeconds);
    }

    public static WaitForActiveOp waitForNodeToBecomeBehind(String name, int waitSeconds) {
        return new WaitForActiveOp(byName(name), waitSeconds);
    }

    public static WaitForFreezeOp waitForNodeToFreeze(String name, int waitSeconds) {
        return new WaitForFreezeOp(byName(name), waitSeconds);
    }

    public static StartNodesOp startAllNodes() {
        return new StartNodesOp(allNodes());
    }

    public static StartNodesOp startNode(String name) {
        return new StartNodesOp(byName(name));
    }

    public static ShutDownNodesOp shutDownAllNodes() {
        return new ShutDownNodesOp(allNodes());
    }

    public static ShutDownNodesOp shutDownNode(String name) {
        return new ShutDownNodesOp(byName(name));
    }

    public static WaitForFreezeOp waitForNodesToFreeze(int waitSeconds) {
        return new WaitForFreezeOp(allNodes(), waitSeconds);
    }

    public static WaitForBehindOp waitForNodeToBeBehind(String name, int waitSeconds) {
        return new WaitForBehindOp(byName(name), waitSeconds);
    }

    public static WaitForReconnectOp waitForNodeToFinishReconnect(String name, int waitSeconds) {
        return new WaitForReconnectOp(byName(name), waitSeconds);
    }

    public static WaitForShutdownOp waitForNodeToShutDown(String name, int waitSeconds) {
        return new WaitForShutdownOp(byName(name), waitSeconds);
    }

    public static WaitForShutdownOp waitForNodesToShutDown(int waitSeconds) {
        return new WaitForShutdownOp(allNodes(), waitSeconds);
    }

    public static HapiSpecSleep sleepFor(long timeMs) {
        return new HapiSpecSleep(timeMs);
    }

    public static HapiSpecWaitUntil waitUntil(String timeOfDay) throws ParseException {
        return new HapiSpecWaitUntil(timeOfDay);
    }

    public static HapiSpecWaitUntil waitUntilStartOfNextStakingPeriod(final long stakePeriodMins) {
        return untilStartOfNextStakingPeriod(stakePeriodMins);
    }

    /**
     * Returns a {@link HapiSpecOperation} that sleeps until the beginning of the next period
     * of the given length since the UTC epoch in clock time.
     *
     * <p>This is not the same thing as sleeping until the next <i>consensus</i> period, of
     * course; but since consensus time will track clock time very closely in practice, this
     * operation can let us be almost certain we have e.g. moved into a new staking period
     * or a new block period by the time the sleep ends.
     *
     * @param periodMs the length of the period in milliseconds
     * @return the operation that sleeps until the beginning of the next period
     */
    public static HapiSpecWaitUntil waitUntilStartOfNextAdhocPeriod(final long periodMs) {
        return untilStartOfNextAdhocPeriod(periodMs);
    }

    public static HapiSpecWaitUntil waitUntilJustBeforeNextStakingPeriod(
            final long stakePeriodMins, final long secondsBefore) {
        return untilJustBeforeStakingPeriod(stakePeriodMins, secondsBefore);
    }

    public static UsableTxnId usableTxnIdNamed(String txnId) {
        return new UsableTxnId(txnId);
    }

    public static SpecKeyFromMnemonic keyFromMnemonic(String name, String mnemonic) {
        return new SpecKeyFromMnemonic(name, mnemonic);
    }

    public static SpecKeyFromMutation keyFromMutation(String name, String mutated) {
        return new SpecKeyFromMutation(name, mutated);
    }

    public static SpecKeyFromLiteral keyFromLiteral(String name, String hexEncodedPrivateKey) {
        return new SpecKeyFromLiteral(name, hexEncodedPrivateKey);
    }

    public static HapiSpecOperation expectedEntitiesExist() {
        return withOpContext((spec, opLog) -> spec.persistentEntities().runExistenceChecks());
    }

    public static SpecKeyFromEcdsaFile keyFromEcdsaFile(String loc, String name) {
        return new SpecKeyFromEcdsaFile(loc, name);
    }

    public static SpecKeyFromEcdsaFile keyFromEcdsaFile(String loc) {
        return new SpecKeyFromEcdsaFile(loc, loc);
    }

    public static SpecKeyFromFile keyFromFile(String name, String flexLoc) {
        return new SpecKeyFromFile(name, flexLoc);
    }

    public static SpecKeyFromPem keyFromPem(String pemLoc) {
        return new SpecKeyFromPem(pemLoc);
    }

    public static SpecKeyFromPem keyFromPem(Supplier<String> pemLocFn) {
        return new SpecKeyFromPem(pemLocFn);
    }

    public static NewSpecKey newKeyNamed(String key) {
        return new NewSpecKey(key);
    }

    public static NewSpecKeyList newKeyListNamed(String key, List<String> childKeys) {
        return new NewSpecKeyList(key, childKeys);
    }

    public static ParallelSpecOps inParallel(HapiSpecOperation... subs) {
        return new ParallelSpecOps(subs);
    }

    public static CustomSpecAssert assertionsHold(CustomSpecAssert.ThrowingConsumer custom) {
        return new CustomSpecAssert(custom);
    }

    public static CustomSpecAssert addLogInfo(CustomSpecAssert.ThrowingConsumer custom) {
        return new CustomSpecAssert(custom);
    }

    public static CustomSpecAssert withOpContext(CustomSpecAssert.ThrowingConsumer custom) {
        return new CustomSpecAssert(custom);
    }

    private static final ByteString MAINNET_LEDGER_ID = ByteString.copyFrom(new byte[] {0x00});
    private static final ByteString TESTNET_LEDGER_ID = ByteString.copyFrom(new byte[] {0x01});
    private static final ByteString PREVIEWNET_LEDGER_ID = ByteString.copyFrom(new byte[] {0x02});
    private static final ByteString DEVNET_LEDGER_ID = ByteString.copyFrom(new byte[] {0x03});

    private static final Set<ByteString> RECOGNIZED_LEDGER_IDS =
            Set.of(MAINNET_LEDGER_ID, TESTNET_LEDGER_ID, PREVIEWNET_LEDGER_ID, DEVNET_LEDGER_ID);

    /**
     * Returns an operation that uses a {@link com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo} query
     * against the {@code 0.0.2} account to look up the ledger id of the target network; and then passes the ledger
     * id to the given callback.
     *
     * @param ledgerIdConsumer the callback to pass the ledger id to
     * @return the operation exposing the ledger id to the callback
     */
    public static HapiSpecOperation exposeTargetLedgerIdTo(@NonNull final Consumer<ByteString> ledgerIdConsumer) {
        return getAccountInfo(GENESIS).payingWith(GENESIS).exposingLedgerIdTo(ledgerId -> {
            if (!RECOGNIZED_LEDGER_IDS.contains(ledgerId)) {
                Assertions.fail(
                        "Target network is claiming unrecognized ledger id " + CommonUtils.hex(ledgerId.toByteArray()));
            }
            ledgerIdConsumer.accept(ledgerId);
        });
    }

    /**
     * A convenience operation that accepts a factory mapping the target ledger id into a {@link HapiSpecOperation}
     * (for example, a query that asserts something about the ledger id); and then,
     * <ol>
     *     <Li>Looks up the ledger id via {@link UtilVerbs#exposeTargetLedgerIdTo(Consumer)}; and,</Li>
     *     <Li>Calls the given factory with this id, and runs the resulting {@link HapiSpecOperation}.</Li>
     * </ol>
     *
     * @param opFn the factory mapping the ledger id into a {@link HapiSpecOperation}
     * @return the operation that looks up the ledger id and runs the resulting {@link HapiSpecOperation}
     */
    public static HapiSpecOperation withTargetLedgerId(@NonNull final Function<ByteString, HapiSpecOperation> opFn) {
        final AtomicReference<ByteString> targetLedgerId = new AtomicReference<>();
        return blockingOrder(
                exposeTargetLedgerIdTo(targetLedgerId::set), sourcing(() -> opFn.apply(targetLedgerId.get())));
    }

    public static BalanceSnapshot balanceSnapshot(String name, String forAccount) {
        return new BalanceSnapshot(forAccount, name);
    }

    public static BalanceSnapshot balanceSnapshot(Function<HapiSpec, String> nameFn, String forAccount) {
        return new BalanceSnapshot(forAccount, nameFn);
    }

    public static StartThroughputObs startThroughputObs(String name) {
        return new StartThroughputObs(name);
    }

    public static FinishThroughputObs finishThroughputObs(String name) {
        return new FinishThroughputObs(name);
    }

    public static VerifyGetLiveHashNotSupported getClaimNotSupported() {
        return new VerifyGetLiveHashNotSupported();
    }

    public static VerifyGetStakersNotSupported getStakersNotSupported() {
        return new VerifyGetStakersNotSupported();
    }

    public static VerifyGetFastRecordNotSupported getFastRecordNotSupported() {
        return new VerifyGetFastRecordNotSupported();
    }

    public static VerifyGetBySolidityIdNotSupported getBySolidityIdNotSupported() {
        return new VerifyGetBySolidityIdNotSupported();
    }

    public static VerifyGetAccountNftInfosNotSupported getAccountNftInfosNotSupported() {
        return new VerifyGetAccountNftInfosNotSupported();
    }

    public static VerifyGetTokenNftInfosNotSupported getTokenNftInfosNotSupported() {
        return new VerifyGetTokenNftInfosNotSupported();
    }

    public static RunLoadTest runLoadTest(Supplier<HapiSpecOperation[]> opSource) {
        return new RunLoadTest(opSource);
    }

    public static NoOp noOp() {
        return new NoOp();
    }

    /**
     * A different name for {@link NoOp} that express the intent of a spec author to end by letting
     * {@link HapiSpec} automatically waits for the streamMustInclude() assertions to pass, fail, or
     * time out while ensuring enough background traffic to keep closing record stream files
     *
     * @return a {@link NoOp} instance
     */
    public static NoOp awaitStreamAssertions() {
        return new NoOp();
    }

    public static LogMessage logIt(String msg) {
        return new LogMessage(msg);
    }

    public static LogMessage logIt(Function<HapiSpec, String> messageFn) {
        return new LogMessage(messageFn);
    }

    public static ProviderRun runWithProvider(Function<HapiSpec, OpProvider> provider) {
        return new ProviderRun(provider);
    }

    public static HapiSpecOperation overriding(String property, String value) {
        return overridingAllOf(Map.of(property, value));
    }

    public static HapiSpecOperation overridingAllOfDeferred(Supplier<Map<String, String>> explicit) {
        return sourcing(() -> overridingAllOf(explicit.get()));
    }

    public static HapiSpecOperation resetToDefault(String... properties) {
        var defaultNodeProps = HapiSpecSetup.getDefaultNodeProps();
        final Map<String, String> defaultValues = new HashMap<>();
        for (final var prop : properties) {
            final var defaultValue = defaultNodeProps.get(prop);
            defaultValues.put(prop, defaultValue);
        }
        return overridingAllOf(defaultValues);
    }

    public static HapiSpecOperation enableAllFeatureFlagsAndDisableContractThrottles(
            @NonNull final String... exceptFeatures) {
        final Map<String, String> allOverrides = new HashMap<>(FeatureFlags.FEATURE_FLAGS.allEnabled(exceptFeatures));
        allOverrides.putAll(Map.of(
                "contracts.throttle.throttleByGas",
                FALSE_VALUE,
                "contracts.enforceCreationThrottle",
                FALSE_VALUE,
                SIDECARS_PROP,
                "CONTRACT_STATE_CHANGE,CONTRACT_ACTION,CONTRACT_BYTECODE"));
        return overridingAllOf(allOverrides);
    }

    /**
     * Returns an operation that computes and executes a list of {@link HapiSpecOperation}s
     * returned by a function whose input is a map from the names of requested registry entities
     * (accounts or tokens) to their EVM addresses.
     *
     * @param accountOrTokens the names of the requested registry entities
     * @param opFn the function that computes the list of operations
     * @return the operation that computes and executes the list of operations
     */
    public static HapiSpecOperation withHeadlongAddressesFor(
            @NonNull final List<String> accountOrTokens,
            @NonNull final Function<Map<String, Address>, List<HapiSpecOperation>> opFn) {
        return withOpContext((spec, opLog) -> {
            final Map<String, Address> addresses = new HashMap<>();
            // FUTURE - populate this map
            allRunFor(spec, opFn.apply(addresses));
        });
    }

    /**
     * Returns an operation that computes and executes a list of {@link HapiSpecOperation}s
     * returned by a function whose input is a map from the names of requested registry keys
     * to their encoded {@code KeyValue} forms.
     *
     * @param keys the names of the requested registry keys
     * @param opFn the function that computes the list of operations
     * @return the operation that computes and executes the list of operations
     */
    public static HapiSpecOperation withKeyValuesFor(
            @NonNull final List<String> keys,
            @NonNull final Function<Map<String, Tuple>, List<HapiSpecOperation>> opFn) {
        return withOpContext((spec, opLog) -> {
            final Map<String, Tuple> keyValues = new HashMap<>();
            // FUTURE - populate this map
            allRunFor(spec, opFn.apply(keyValues));
        });
    }

    public static HapiSpecOperation overridingTwo(
            final String aProperty, final String aValue, final String bProperty, final String bValue) {
        return overridingAllOf(Map.of(
                aProperty, aValue,
                bProperty, bValue));
    }

    public static HapiSpecOperation overridingThree(
            final String aProperty,
            final String aValue,
            final String bProperty,
            final String bValue,
            final String cProperty,
            final String cValue) {
        return overridingAllOf(Map.of(
                aProperty, aValue,
                bProperty, bValue,
                cProperty, cValue));
    }

    public static HapiSpecOperation overridingAllOf(@NonNull final Map<String, String> explicit) {
        return withOpContext((spec, opLog) -> {
            final var updated121 = getUpdated121(spec, explicit);
            final var multiStepUpdate = updateLargeFile(
                    GENESIS, APP_PROPERTIES, ByteString.copyFrom(updated121), true, OptionalLong.of(0L));
            allRunFor(spec, multiStepUpdate);
        });
    }

    public static HapiSpecOperation remembering(final Map<String, String> props, final String... ofInterest) {
        return remembering(props, Arrays.asList(ofInterest));
    }

    public static HapiSpecOperation remembering(final Map<String, String> props, final List<String> ofInterest) {
        final var defaultNodeProps = HapiSpecSetup.getDefaultNodeProps();
        final Predicate<String> filter = new HashSet<>(ofInterest)::contains;
        return blockingOrder(
                getFileContents(APP_PROPERTIES)
                        .payingWith(GENESIS)
                        .nodePayment(ONE_HBAR)
                        .fee(ONE_HBAR)
                        .addingFilteredConfigListTo(props, filter),
                sourcing(() -> {
                    ofInterest.forEach(prop -> props.computeIfAbsent(prop, defaultNodeProps::get));
                    return logIt("Remembered props: " + props);
                }));
    }

    public static CustomSpecAssert exportAccountBalances(Supplier<String> acctBalanceFile) {
        return new CustomSpecAssert((spec, log) -> spec.exportAccountBalances(acctBalanceFile));
    }

    /* Stream validation. */
    public static RecordStreamVerification verifyRecordStreams(Supplier<String> baseDir) {
        return new RecordStreamVerification(baseDir);
    }

    public static HapiSpecOperation assertEventuallyPasses(
            final RecordStreamValidator validator, final Duration timeout) {
        return new RecordAssertions(timeout, validator);
    }

    public static EventualAssertion streamMustInclude(final Function<HapiSpec, RecordStreamAssertion> assertion) {
        return new EventualRecordStreamAssertion(assertion);
    }

    public static EventualAssertion streamMustIncludeNoFailuresFrom(
            final Function<HapiSpec, RecordStreamAssertion> assertion) {
        return EventualRecordStreamAssertion.eventuallyAssertingNoFailures(assertion);
    }

    public static Function<HapiSpec, RecordStreamAssertion> recordedCryptoCreate(final String name) {
        return recordedCryptoCreate(name, assertion -> {});
    }

    public static Function<HapiSpec, RecordStreamAssertion> recordedCryptoCreate(
            final String name, final Consumer<CryptoCreateAssertion> config) {
        return spec -> {
            final var assertion = new CryptoCreateAssertion(spec, name);
            config.accept(assertion);
            return assertion;
        };
    }

    public static Function<HapiSpec, RecordStreamAssertion> sidecarIdValidator() {
        return spec -> new ValidContractIdsAssertion();
    }

    public static Function<HapiSpec, RecordStreamAssertion> recordedChildBodyWithId(
            final String specTxnId, final int nonce, final AssertingBiConsumer<HapiSpec, TransactionBody> assertion) {
        return spec -> new TransactionBodyAssertion(specTxnId, spec, txnId -> txnId.getNonce() == nonce, assertion);
    }

    public static RecordFileChecker verifyRecordFile(
            Timestamp timestamp, List<Transaction> transactions, TransactionRecord... transactionRecord) {
        var recordFileName = generateStreamFileNameFromInstant(
                Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()), new RecordStreamType());

        return new RecordFileChecker(recordFileName, transactions, transactionRecord);
    }

    /* Some more complicated ops built from primitive sub-ops */
    public static CustomSpecAssert recordFeeAmount(String forTxn, String byName) {
        return new CustomSpecAssert((spec, workLog) -> {
            HapiGetTxnRecord subOp = getTxnRecord(forTxn);
            allRunFor(spec, subOp);
            TransactionRecord rcd = subOp.getResponseRecord();
            long fee = rcd.getTransactionFee();
            spec.registry().saveAmount(byName, fee);
        });
    }

    public static HapiSpecOperation fundAnAccount(String account) {
        return withOpContext((spec, ctxLog) -> {
            if (!asId(account, spec).equals(asId(GENESIS, spec))) {
                HapiCryptoTransfer subOp = cryptoTransfer(tinyBarsFromTo(GENESIS, account, HapiSuite.ADEQUATE_FUNDS));
                CustomSpecAssert.allRunFor(spec, subOp);
            }
        });
    }

    public static HapiSpecOperation emptyChildRecordsCheck(String parentTxnId, ResponseCodeEnum parentalStatus) {
        return childRecordsCheck(parentTxnId, parentalStatus);
    }

    public static HapiSpecOperation childRecordsCheck(
            final String parentTxnId,
            final ResponseCodeEnum parentalStatus,
            final TransactionRecordAsserts... childRecordAsserts) {
        return childRecordsCheck(parentTxnId, parentalStatus, parentRecordAsserts -> {}, childRecordAsserts);
    }

    public static HapiSpecOperation childRecordsCheck(
            final String parentTxnId,
            final ResponseCodeEnum parentalStatus,
            final Consumer<TransactionRecordAsserts> parentRecordAssertsSpec,
            final TransactionRecordAsserts... childRecordAsserts) {
        return withOpContext((spec, opLog) -> {
            final var lookup = getTxnRecord(parentTxnId);
            allRunFor(spec, lookup);
            final var parentId = lookup.getResponseRecord().getTransactionID();
            final var parentRecordAsserts = recordWith().status(parentalStatus).txnId(parentId);
            parentRecordAssertsSpec.accept(parentRecordAsserts);
            allRunFor(
                    spec,
                    getTxnRecord(parentTxnId)
                            .andAllChildRecords()
                            .hasPriority(parentRecordAsserts)
                            .hasChildRecords(parentId, childRecordAsserts)
                            .logged());
        });
    }

    public static Setting from(String name, String value) {
        return Setting.newBuilder().setName(name).setValue(value).build();
    }

    public static HapiSpecOperation chunkAFile(String filePath, int chunkSize, String payer, String topic) {
        return chunkAFile(filePath, chunkSize, payer, topic, new AtomicLong(-1));
    }

    public static HapiSpecOperation chunkAFile(
            String filePath, int chunkSize, String payer, String topic, AtomicLong count) {
        return withOpContext((spec, ctxLog) -> {
            List<HapiSpecOperation> opsList = new ArrayList<>();
            String overriddenFile = filePath;
            int overriddenChunkSize = chunkSize;
            String overriddenTopic = topic;
            boolean validateRunningHash = false;

            long currentCount = count.getAndIncrement();
            if (currentCount >= 0) {
                var ciProperties = spec.setup().ciPropertiesMap();
                if (null != ciProperties) {
                    if (ciProperties.has("file")) {
                        overriddenFile = ciProperties.get("file");
                    }
                    if (ciProperties.has("chunkSize")) {
                        overriddenChunkSize = ciProperties.getInteger("chunkSize");
                    }
                    if (ciProperties.has("validateRunningHash")) {
                        validateRunningHash = ciProperties.getBoolean("validateRunningHash");
                    }
                    int threads = PerfTestLoadSettings.DEFAULT_THREADS;
                    if (ciProperties.has("threads")) {
                        threads = ciProperties.getInteger("threads");
                    }
                    int factor = HCSChunkingRealisticPerfSuite.DEFAULT_COLLISION_AVOIDANCE_FACTOR;
                    if (ciProperties.has("collisionAvoidanceFactor")) {
                        factor = ciProperties.getInteger("collisionAvoidanceFactor");
                    }
                    overriddenTopic += currentCount % (threads * factor);
                }
            }
            ByteString msg = ByteString.copyFrom(Files.readAllBytes(Paths.get(overriddenFile)));
            int size = msg.size();
            int totalChunks = (size + overriddenChunkSize - 1) / overriddenChunkSize;
            int position = 0;
            int currentChunk = 0;
            var initialTransactionID = asTransactionID(spec, Optional.of(payer));

            while (position < size) {
                ++currentChunk;
                int newPosition = Math.min(size, position + overriddenChunkSize);
                ByteString subMsg = msg.substring(position, newPosition);
                HapiMessageSubmit subOp = submitMessageTo(overriddenTopic)
                        .message(subMsg)
                        .chunkInfo(totalChunks, currentChunk, initialTransactionID)
                        .payingWith(payer)
                        .hasKnownStatus(SUCCESS)
                        .hasRetryPrecheckFrom(
                                BUSY,
                                DUPLICATE_TRANSACTION,
                                PLATFORM_TRANSACTION_NOT_CREATED,
                                INSUFFICIENT_PAYER_BALANCE)
                        .noLogging()
                        .suppressStats(true);
                if (1 == currentChunk) {
                    subOp = subOp.usePresetTimestamp();
                }
                if (validateRunningHash) {
                    String txnName = "submitMessage-" + overriddenTopic + "-" + currentChunk;
                    HapiGetTxnRecord validateOp = getTxnRecord(txnName)
                            .hasCorrectRunningHash(overriddenTopic, subMsg.toByteArray())
                            .payingWith(payer)
                            .noLogging();
                    opsList.add(subOp.via(txnName));
                    opsList.add(validateOp);
                } else {
                    opsList.add(subOp.deferStatusResolution());
                }
                position = newPosition;
            }

            CustomSpecAssert.allRunFor(spec, opsList);
        });
    }

    public static HapiSpecOperation ensureDissociated(String account, List<String> tokens) {
        return withOpContext((spec, opLog) -> {
            var query = getAccountBalance(account);
            allRunFor(spec, query);
            var answer = query.getResponse().getCryptogetAccountBalance().getTokenBalancesList();
            for (String token : tokens) {
                var tid = spec.registry().getTokenID(token);
                var match = answer.stream()
                        .filter(tb -> tb.getTokenId().equals(tid))
                        .findAny();
                if (match.isPresent()) {
                    var tb = match.get();
                    opLog.info(
                            "Account '{}' is associated to token '{}' ({})",
                            account,
                            token,
                            HapiPropertySource.asTokenString(tid));
                    if (tb.getBalance() > 0) {
                        opLog.info("  -->> Balance is {}, transferring to treasury", tb.getBalance());
                        var treasury = spec.registry().getTreasury(token);
                        var xfer = cryptoTransfer(moving(tb.getBalance(), token).between(account, treasury));
                        allRunFor(spec, xfer);
                    }
                    opLog.info("  -->> Dissociating '{}' from '{}' now", account, token);
                    var dis = tokenDissociate(account, token);
                    allRunFor(spec, dis);
                }
            }
        });
    }

    public static HapiSpecOperation makeFree(HederaFunctionality function) {
        return reduceFeeFor(function, 0L, 0L, 0L);
    }

    public static HapiSpecOperation reduceFeeFor(
            HederaFunctionality function,
            long tinyBarMaxNodeFee,
            long tinyBarMaxNetworkFee,
            long tinyBarMaxServiceFee) {
        return reduceFeeFor(List.of(function), tinyBarMaxNodeFee, tinyBarMaxNetworkFee, tinyBarMaxServiceFee);
    }

    public static HapiSpecOperation reduceFeeFor(
            List<HederaFunctionality> functions,
            long tinyBarMaxNodeFee,
            long tinyBarMaxNetworkFee,
            long tinyBarMaxServiceFee) {
        return withOpContext((spec, opLog) -> {
            if (!spec.setup().defaultNode().equals(asAccount("0.0.3"))) {
                opLog.info("Sleeping to wait for fee reduction...");
                Thread.sleep(20000);
                return;
            }
            opLog.info("Sleeping so not to spoil/fail the fee initializations on other" + " clients...");
            Thread.sleep(10000);
            opLog.info("Reducing fee for {}...", functions);
            var query = getFileContents(FEE_SCHEDULE).payingWith(GENESIS);
            allRunFor(spec, query);
            byte[] rawSchedules = query.getResponse()
                    .getFileGetContents()
                    .getFileContents()
                    .getContents()
                    .toByteArray();

            // Convert from tinyBar to one-thousandth of a tinyCent, the unit of max field
            // in FeeComponents
            long centEquiv = spec.ratesProvider().rates().getCentEquiv();
            long hbarEquiv = spec.ratesProvider().rates().getHbarEquiv();
            long maxNodeFee = tinyBarMaxNodeFee * centEquiv * 1000L / hbarEquiv;
            long maxNetworkFee = tinyBarMaxNetworkFee * centEquiv * 1000L / hbarEquiv;
            long maxServiceFee = tinyBarMaxServiceFee * centEquiv * 1000L / hbarEquiv;

            var perturbedSchedules = CurrentAndNextFeeSchedule.parseFrom(rawSchedules).toBuilder();
            for (final var function : functions) {
                reduceFeeComponentsFor(
                        perturbedSchedules.getCurrentFeeScheduleBuilder(),
                        function,
                        maxNodeFee,
                        maxNetworkFee,
                        maxServiceFee);
                reduceFeeComponentsFor(
                        perturbedSchedules.getNextFeeScheduleBuilder(),
                        function,
                        maxNodeFee,
                        maxNetworkFee,
                        maxServiceFee);
            }
            var rawPerturbedSchedules = perturbedSchedules.build().toByteString();
            allRunFor(spec, updateLargeFile(GENESIS, FEE_SCHEDULE, rawPerturbedSchedules));
        });
    }

    private static void reduceFeeComponentsFor(
            FeeSchedule.Builder feeSchedule,
            HederaFunctionality function,
            long maxNodeFee,
            long maxNetworkFee,
            long maxServiceFee) {
        var feesList = feeSchedule.getTransactionFeeScheduleBuilderList().stream()
                .filter(tfs -> tfs.getHederaFunctionality() == function)
                .findAny()
                .orElseThrow()
                .getFeesBuilderList();

        for (FeeData.Builder builder : feesList) {
            builder.getNodedataBuilder().setMax(maxNodeFee);
            builder.getNetworkdataBuilder().setMax(maxNetworkFee);
            builder.getServicedataBuilder().setMax(maxServiceFee);
        }
    }

    public static HapiSpecOperation uploadDefaultFeeSchedules(String payer) {
        return withOpContext((spec, opLog) -> {
            allRunFor(spec, updateLargeFile(payer, FEE_SCHEDULE, defaultFeeSchedules()));
            if (!spec.tryReinitializingFees()) {
                throw new IllegalStateException("New fee schedules won't be available, dying!");
            }
        });
    }

    private static ByteString defaultFeeSchedules() {
        SysFileSerde<String> serde = new FeesJsonToGrpcBytes();
        var baos = new ByteArrayOutputStream();
        try {
            var schedulesIn = HapiFileCreate.class.getClassLoader().getResourceAsStream("FeeSchedule.json");
            if (schedulesIn == null) {
                throw new IllegalStateException("No FeeSchedule.json resource available!");
            }
            schedulesIn.transferTo(baos);
            baos.close();
            baos.flush();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        var stylized = new String(baos.toByteArray());
        return ByteString.copyFrom(serde.toRawFile(stylized, null));
    }

    public static HapiSpecOperation createLargeFile(String payer, String fileName, ByteString byteString) {
        return blockingOrder(
                fileCreate(fileName).payingWith(payer).contents(new byte[0]),
                updateLargeFile(payer, fileName, byteString, false, OptionalLong.empty()));
    }

    public static HapiSpecOperation updateLargeFile(String payer, String fileName, ByteString byteString) {
        return updateLargeFile(payer, fileName, byteString, false, OptionalLong.empty());
    }

    public static HapiSpecOperation updateLargeFile(
            String payer,
            String fileName,
            ByteString byteString,
            boolean signOnlyWithPayer,
            OptionalLong tinyBarsToOffer) {
        return updateLargeFile(
                payer, fileName, byteString, signOnlyWithPayer, tinyBarsToOffer, op -> {}, (op, i) -> {});
    }

    public static HapiSpecOperation updateSpecialFile(
            final String payer,
            final String fileName,
            final ByteString contents,
            final int bytesPerOp,
            final int appendsPerBurst) {
        return updateSpecialFile(payer, fileName, contents, bytesPerOp, appendsPerBurst, 0);
    }

    public static HapiSpecOperation updateSpecialFile(
            final String payer,
            final String fileName,
            final ByteString contents,
            final int bytesPerOp,
            final int appendsPerBurst,
            final int appendsToSkip) {
        return withOpContext((spec, opLog) -> {
            final var bytesToUpload = contents.size();
            final var bytesToAppend = bytesToUpload - Math.min(bytesToUpload, bytesPerOp);
            var appendsRequired = bytesToAppend / bytesPerOp + Math.min(1, bytesToAppend % bytesPerOp);
            final var uploadProgress = new UploadProgress();
            uploadProgress.initializeFor(appendsRequired);

            if (appendsToSkip == 0) {
                System.out.println(
                        ".i. Beginning upload for " + fileName + " (" + appendsRequired + " appends required)");
            } else {
                System.out.println(".i. Continuing upload for "
                        + fileName
                        + " with "
                        + appendsToSkip
                        + " appends already finished (out of "
                        + appendsRequired
                        + " appends required)");
            }
            final var numBursts = (appendsRequired - appendsToSkip) / appendsPerBurst
                    + Math.min(1, appendsRequired % appendsPerBurst);

            int position =
                    (appendsToSkip == 0) ? Math.min(bytesPerOp, bytesToUpload) : bytesPerOp * (1 + appendsToSkip);
            if (appendsToSkip == 0) {
                final var updateSubOp = fileUpdate(fileName)
                        .fee(ONE_HUNDRED_HBARS)
                        .contents(contents.substring(0, position))
                        .alertingPre(fid -> System.out.println(
                                ".i. Submitting initial update for file" + " 0.0." + fid.getFileNum()))
                        .alertingPost(code -> System.out.println(".i. Finished initial update with " + code))
                        .noLogging()
                        .payingWith(payer)
                        .signedBy(payer);
                allRunFor(spec, updateSubOp);
            }

            try {
                finishAppendsFor(
                        contents,
                        position,
                        bytesPerOp,
                        appendsPerBurst,
                        numBursts,
                        fileName,
                        payer,
                        spec,
                        uploadProgress,
                        appendsToSkip,
                        opLog);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                final var finished = uploadProgress.finishedAppendPrefixLength();
                if (finished != -1) {
                    log.error(
                            "Upload failed, but at least {} appends appear to have"
                                    + " finished; please re-run with --restart-from-failure",
                            finished,
                            e);
                } else {
                    log.error("Upload failed without any reusable work; please try again", e);
                }
                throw new IllegalStateException(e);
            }
        });
    }

    private static void finishAppendsFor(
            final ByteString contents,
            int position,
            final int bytesPerOp,
            final int appendsPerBurst,
            final int numBursts,
            final String fileName,
            final String payer,
            final HapiSpec spec,
            final UploadProgress uploadProgress,
            final int appendsSkipped,
            final Logger opLog)
            throws InterruptedException {
        final var bytesToUpload = contents.size();
        final AtomicInteger burstNo = new AtomicInteger(1);
        final AtomicInteger nextAppendNo = new AtomicInteger(appendsSkipped);
        while (position < bytesToUpload) {
            final var totalBytesLeft = bytesToUpload - position;
            final var appendsLeft = totalBytesLeft / bytesPerOp + Math.min(1, totalBytesLeft % bytesPerOp);
            final var appendsHere = new AtomicInteger(Math.min(appendsPerBurst, appendsLeft));
            boolean isFirstAppend = true;
            final List<HapiSpecOperation> theBurst = new ArrayList<>();
            final CountDownLatch burstLatch = new CountDownLatch(1);
            final AtomicReference<Instant> burstStart = new AtomicReference<>();
            while (appendsHere.getAndDecrement() > 0) {
                final var bytesLeft = bytesToUpload - position;
                final var bytesThisAppend = Math.min(bytesLeft, bytesPerOp);
                final var newPosition = position + bytesThisAppend;
                final var appendNoToTrack = nextAppendNo.getAndIncrement();
                opLog.info("Constructing append #{} ({} bytes)", appendNoToTrack, bytesThisAppend);
                final var appendSubOp = fileAppend(fileName)
                        .content(contents.substring(position, newPosition).toByteArray())
                        .fee(ONE_HUNDRED_HBARS)
                        .noLogging()
                        .payingWith(payer)
                        .signedBy(payer)
                        .deferStatusResolution()
                        .trackingProgressIn(uploadProgress, appendNoToTrack);
                if (isFirstAppend) {
                    final var fixedBurstNo = burstNo.get();
                    final var fixedAppendsHere = appendsHere.get() + 1;
                    appendSubOp.alertingPre(fid -> {
                        burstStart.set(Instant.now());
                        System.out.println(".i. Starting burst " + fixedBurstNo + "/" + numBursts + " ("
                                + fixedAppendsHere + " ops)");
                    });
                    isFirstAppend = false;
                }
                if (appendsHere.get() == 0) {
                    final var fixedBurstNo = burstNo.get();
                    appendSubOp.alertingPost(code -> {
                        final var burstSecs = Duration.between(burstStart.get(), Instant.now())
                                .getSeconds();
                        System.out.println(".i. Completed burst #"
                                + fixedBurstNo
                                + "/"
                                + numBursts
                                + " in "
                                + burstSecs
                                + "s with "
                                + code);
                        burstLatch.countDown();
                    });
                }
                theBurst.add(appendSubOp);
                position = newPosition;
            }
            allRunFor(spec, theBurst);
            burstLatch.await();
            burstNo.getAndIncrement();
        }
    }

    /**
     * Returns a {@link SnapshotModeOp} that either takes or fuzzy-matches a snapshot of generated records
     * from the current spec.
     *
     * <p><b>IMPORTANT:</b> If multiple {@link SnapshotModeOp} operations are used in a single spec, all
     * but the last will be a no-op.
     *
     * @param mode the snapshot mode to use
     * @return a {@link SnapshotModeOp} that either takes or fuzzy-matches a snapshot of generated records
     */
    public static SnapshotModeOp snapshotMode(
            @NonNull final SnapshotMode mode, @NonNull final SnapshotMatchMode... matchModes) {
        return new SnapshotModeOp(mode, matchModes);
    }

    public static HapiSpecOperation updateLargeFile(
            String payer,
            String fileName,
            ByteString byteString,
            boolean signOnlyWithPayer,
            OptionalLong tinyBarsToOffer,
            Consumer<HapiFileUpdate> updateCustomizer,
            ObjIntConsumer<HapiFileAppend> appendCustomizer) {
        return withOpContext((spec, ctxLog) -> {
            List<HapiSpecOperation> opsList = new ArrayList<>();

            int fileSize = byteString.size();
            int position = Math.min(BYTES_4K, fileSize);

            HapiFileUpdate updateSubOp = fileUpdate(fileName)
                    .contents(byteString.substring(0, position))
                    .hasKnownStatusFrom(
                            SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED, SUCCESS_BUT_MISSING_EXPECTED_OPERATION)
                    .noLogging()
                    .payingWith(payer);
            updateCustomizer.accept(updateSubOp);
            if (tinyBarsToOffer.isPresent()) {
                updateSubOp = updateSubOp.fee(tinyBarsToOffer.getAsLong());
            }
            if (signOnlyWithPayer) {
                updateSubOp = updateSubOp.signedBy(payer);
            }
            opsList.add(updateSubOp);

            final int bytesLeft = fileSize - position;
            final int totalAppendsRequired = bytesLeft / BYTES_4K + Math.min(1, bytesLeft % BYTES_4K);
            int numAppends = 0;
            while (position < fileSize) {
                int newPosition = Math.min(fileSize, position + BYTES_4K);
                var appendSubOp = fileAppend(fileName)
                        .content(byteString.substring(position, newPosition).toByteArray())
                        .hasKnownStatusFrom(SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED)
                        .noLogging()
                        .payingWith(payer);
                appendCustomizer.accept(appendSubOp, totalAppendsRequired - numAppends);
                if (tinyBarsToOffer.isPresent()) {
                    appendSubOp = appendSubOp.fee(tinyBarsToOffer.getAsLong());
                }
                if (signOnlyWithPayer) {
                    appendSubOp = appendSubOp.signedBy(payer);
                }
                opsList.add(appendSubOp);
                position = newPosition;
                numAppends++;
            }

            CustomSpecAssert.allRunFor(spec, opsList);
        });
    }

    public static HapiSpecOperation updateLargeFile(String payer, String fileName, String registryEntry) {
        return withOpContext((spec, ctxLog) -> {
            ByteString bt = ByteString.copyFrom(spec.registry().getBytes(registryEntry));
            CustomSpecAssert.allRunFor(spec, updateLargeFile(payer, fileName, bt));
        });
    }

    public static HapiSpecOperation saveFileToRegistry(String fileName, String registryEntry) {
        return getFileContents(fileName).payingWith(GENESIS).saveToRegistry(registryEntry);
    }

    public static HapiSpecOperation restoreFileFromRegistry(String fileName, String registryEntry) {
        return updateLargeFile(GENESIS, fileName, registryEntry);
    }

    @SuppressWarnings("java:S5960")
    public static HapiSpecOperation contractListWithPropertiesInheritedFrom(
            final String contractList, final long expectedSize, final String parent) {
        return withOpContext((spec, ctxLog) -> {
            List<HapiSpecOperation> opsList = new ArrayList<>();
            long contractListSize = spec.registry().getAmount(contractList + "Size");
            Assertions.assertEquals(expectedSize, contractListSize, contractList + " has bad size!");
            if (contractListSize > 1) {
                ContractID currentID = spec.registry().getContractId(contractList + "0");
                long nextIndex = 1;
                while (nextIndex < contractListSize) {
                    ContractID nextID = spec.registry().getContractId(contractList + nextIndex);
                    Assertions.assertEquals(currentID.getShardNum(), nextID.getShardNum());
                    Assertions.assertEquals(currentID.getRealmNum(), nextID.getRealmNum());
                    assertTrue(currentID.getContractNum() < nextID.getContractNum());
                    currentID = nextID;
                    nextIndex++;
                }
            }
            for (long i = 0; i < contractListSize; i++) {
                HapiSpecOperation op = getContractInfo(contractList + i)
                        .has(contractWith().propertiesInheritedFrom(parent))
                        .logged();
                opsList.add(op);
            }
            CustomSpecAssert.allRunFor(spec, opsList);
        });
    }

    /**
     * Validates that fee charged for a transaction is within +/- 0.0001$ of expected fee (taken
     * from pricing calculator)
     *
     * @param txn transaction to be validated
     * @param expectedUsd expected fee in USD
     * @return assertion for the validation
     */
    public static CustomSpecAssert validateChargedUsd(String txn, double expectedUsd) {
        return validateChargedUsdWithin(txn, expectedUsd, 1.0);
    }

    public static CustomSpecAssert validateChargedUsd(String txn, double expectedUsd, double allowedPercentDiff) {
        return validateChargedUsdWithin(txn, expectedUsd, allowedPercentDiff);
    }

    public static CustomSpecAssert validateChargedUsdWithin(String txn, double expectedUsd, double allowedPercentDiff) {
        return assertionsHold((spec, assertLog) -> {
            final var actualUsdCharged = getChargedUsed(spec, txn);
            assertEquals(
                    expectedUsd,
                    actualUsdCharged,
                    (allowedPercentDiff / 100.0) * expectedUsd,
                    String.format(
                            "%s fee (%s) more than %.2f percent different than expected!",
                            CryptoTransferSuite.sdec(actualUsdCharged, 4), txn, allowedPercentDiff));
        });
    }

    public static CustomSpecAssert validateChargedUsdExceeds(String txn, double amount) {
        return validateChargedUsd(txn, actualUsdCharged -> {
            assertTrue(
                    actualUsdCharged > amount,
                    String.format(
                            "%s fee (%s) is not greater than %s!",
                            CryptoTransferSuite.sdec(actualUsdCharged, 4), txn, amount));
        });
    }

    public static CustomSpecAssert validateChargedUsd(String txn, DoubleConsumer validator) {
        return assertionsHold((spec, assertLog) -> {
            final var actualUsdCharged = getChargedUsed(spec, txn);
            validator.accept(actualUsdCharged);
        });
    }

    public static CustomSpecAssert getTransactionFee(String txn, StringBuilder feeTableBuilder, String operation) {
        return assertionsHold((spec, asertLog) -> {
            var subOp = getTxnRecord(txn);
            allRunFor(spec, subOp);

            var rcd = subOp.getResponseRecord();
            double actualUsdCharged = (1.0 * rcd.getTransactionFee())
                    / ONE_HBAR
                    / rcd.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
                    * rcd.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
                    / 100;

            feeTableBuilder.append(String.format("%30s | %1.5f \t |%n", operation, actualUsdCharged));
        });
    }

    public static HapiSpecOperation[] takeBalanceSnapshots(String... entities) {
        return HapiSuite.flattened(
                cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, 1_000_000_000L))
                        .noLogging(),
                Stream.of(entities)
                        .map(account -> balanceSnapshot(
                                        spec -> asAccountString(spec.registry().getAccountID(account)) + "Snapshot",
                                        account)
                                .payingWith(EXCHANGE_RATE_CONTROL))
                        .toArray(n -> new HapiSpecOperation[n]));
    }

    public static HapiSpecOperation validateRecordTransactionFees(String txn) {
        return validateRecordTransactionFees(
                txn,
                Set.of(
                        HapiPropertySource.asAccount("0.0.3"),
                        HapiPropertySource.asAccount("0.0.98"),
                        HapiPropertySource.asAccount("0.0.800"),
                        HapiPropertySource.asAccount("0.0.801")));
    }

    public static HapiSpecOperation validateRecordTransactionFees(String txn, Set<AccountID> feeRecipients) {
        return assertionsHold((spec, assertLog) -> {
            HapiGetTxnRecord subOp =
                    getTxnRecord(txn).logged().payingWith(EXCHANGE_RATE_CONTROL).expectStrictCostAnswer();
            allRunFor(spec, subOp);
            TransactionRecord rcd =
                    subOp.getResponse().getTransactionGetRecord().getTransactionRecord();
            long realFee = rcd.getTransferList().getAccountAmountsList().stream()
                    .filter(aa -> feeRecipients.contains(aa.getAccountID()))
                    .mapToLong(AccountAmount::getAmount)
                    .sum();
            Assertions.assertEquals(realFee, rcd.getTransactionFee(), "Inconsistent transactionFee field!");
        });
    }

    public static HapiSpecOperation validateTransferListForBalances(String txn, List<String> accounts) {
        return validateTransferListForBalances(List.of(txn), accounts);
    }

    public static HapiSpecOperation validateTransferListForBalances(
            String txn, List<String> accounts, Set<String> wereDeleted) {
        return validateTransferListForBalances(List.of(txn), accounts, wereDeleted);
    }

    public static HapiSpecOperation validateTransferListForBalances(List<String> txns, List<String> accounts) {
        return validateTransferListForBalances(txns, accounts, Collections.emptySet());
    }

    public static HapiSpecOperation validateTransferListForBalances(
            List<String> txns, List<String> accounts, Set<String> wereDeleted) {
        return assertionsHold((spec, assertLog) -> {
            Map<String, Long> actualBalances = accounts.stream()
                    .collect(Collectors.toMap(
                            (String account) -> asAccountString(spec.registry().getAccountID(account)),
                            (String account) -> {
                                if (wereDeleted.contains(account)) {
                                    return 0L;
                                }
                                long balance = -1L;
                                try {
                                    BalanceSnapshot preOp = balanceSnapshot("x", account);
                                    allRunFor(spec, preOp);
                                    balance = spec.registry().getBalanceSnapshot("x");
                                } catch (Exception ignore) {
                                    // Intentionally ignored
                                }
                                return balance;
                            }));

            List<AccountAmount> transfers = new ArrayList<>();

            for (String txn : txns) {
                HapiGetTxnRecord subOp = getTxnRecord(txn).logged().payingWith(EXCHANGE_RATE_CONTROL);
                allRunFor(spec, subOp);
                TransactionRecord rcd =
                        subOp.getResponse().getTransactionGetRecord().getTransactionRecord();
                transfers.addAll(rcd.getTransferList().getAccountAmountsList());
            }

            Map<String, Long> changes = changesAccordingTo(transfers);
            assertLog.info("Balance changes according to transfer list: {}", changes);
            changes.entrySet().forEach(change -> {
                String account = change.getKey();
                long oldBalance = -1L;
                /* The account/contract may have just been created, no snapshot was taken. */
                try {
                    oldBalance = spec.registry().getBalanceSnapshot(account + "Snapshot");
                } catch (Exception ignored) {
                    // Intentionally ignored
                }
                long expectedBalance = change.getValue() + Math.max(0L, oldBalance);
                long actualBalance = actualBalances.getOrDefault(account, -1L);
                assertLog.info(
                        "Balance of {} was expected to be {}, is actually" + " {}...",
                        account,
                        expectedBalance,
                        actualBalance);
                Assertions.assertEquals(
                        expectedBalance,
                        actualBalance,
                        "New balance for " + account + " should be " + expectedBalance + " tinyBars.");
            });
        });
    }

    private static Map<String, Long> changesAccordingTo(List<AccountAmount> transfers) {
        return transfers.stream()
                .map(aa -> new AbstractMap.SimpleEntry<>(asAccountString(aa.getAccountID()), aa.getAmount()))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingLong(Map.Entry::getValue)));
    }

    public static Tuple[] wrapIntoTupleArray(Tuple tuple) {
        return new Tuple[] {tuple};
    }

    public static TransferListBuilder transferList() {
        return new TransferListBuilder();
    }

    /**
     * Returns an operation that attempts to execute the given transaction passing the
     * provided name to {@link HapiTxnOp#via(String)}; and accepting either
     * {@link com.hedera.hapi.node.base.ResponseCodeEnum#SUCCESS} or
     * {@link com.hedera.hapi.node.base.ResponseCodeEnum#MAX_CHILD_RECORDS_EXCEEDED}
     * as the final status.
     *
     * <p>On success, executes the remaining operations. This lets us stabilize operations
     * in CI that need to use all preceding child records to succeed; and hence fail if
     * their transaction triggers an end-of-day staking record.
     *
     * @param txnRequiringMaxChildRecords the transaction requiring all child records
     * @param name the transaction name to use
     * @param onSuccess the operations to run on success
     * @return the operation doing this conditional execution
     */
    public static HapiSpecOperation assumingNoStakingChildRecordCausesMaxChildRecordsExceeded(
            @NonNull final HapiTxnOp<?> txnRequiringMaxChildRecords,
            @NonNull final String name,
            @NonNull final HapiSpecOperation... onSuccess) {
        return blockingOrder(
                txnRequiringMaxChildRecords
                        .via(name)
                        // In CI this could fail due to an end-of-staking period record already
                        // being added as a child to this transaction before its auto-creations
                        .hasKnownStatusFrom(SUCCESS, MAX_CHILD_RECORDS_EXCEEDED),
                withOpContext((spec, opLog) -> {
                    final var lookup = getTxnRecord(name);
                    allRunFor(spec, lookup);
                    final var actualStatus =
                            lookup.getResponseRecord().getReceipt().getStatus();
                    // Continue with more assertions given the normal case the preceding transfer succeeded
                    if (actualStatus == SUCCESS) {
                        allRunFor(spec, onSuccess);
                    }
                }));
    }

    public static class TransferListBuilder {
        private Tuple transferList;

        public TransferListBuilder withAccountAmounts(final Tuple... accountAmounts) {
            this.transferList = Tuple.singleton(accountAmounts);
            return this;
        }

        public Tuple build() {
            return transferList;
        }
    }

    public static TokenTransferListBuilder tokenTransferList() {
        return new TokenTransferListBuilder();
    }

    public static TokenTransferListsBuilder tokenTransferLists() {
        return new TokenTransferListsBuilder();
    }

    public static class TokenTransferListBuilder {
        private Tuple tokenTransferList;
        private Address token;

        public TokenTransferListBuilder forToken(final TokenID token) {
            this.token = HapiParserUtil.asHeadlongAddress(asAddress(token));
            return this;
        }

        public TokenTransferListBuilder forTokenAddress(final Address token) {
            this.token = token;
            return this;
        }

        public TokenTransferListBuilder withAccountAmounts(final Tuple... accountAmounts) {
            this.tokenTransferList = Tuple.of(token, accountAmounts, new Tuple[] {});
            return this;
        }

        public TokenTransferListBuilder withNftTransfers(final Tuple... nftTransfers) {
            this.tokenTransferList = Tuple.of(token, new Tuple[] {}, nftTransfers);
            return this;
        }

        public Tuple build() {
            return tokenTransferList;
        }
    }

    public static class TokenTransferListsBuilder {
        private Tuple[] tokenTransferLists;

        public TokenTransferListsBuilder withTokenTransferList(final Tuple... tokenTransferLists) {
            this.tokenTransferLists = tokenTransferLists;
            return this;
        }

        public Object build() {
            return tokenTransferLists;
        }
    }

    public static Tuple accountAmount(final AccountID account, final Long amount) {
        return Tuple.of(HapiParserUtil.asHeadlongAddress(asAddress(account)), amount);
    }

    public static Tuple addressedAccountAmount(final Address address, final Long amount) {
        return Tuple.of(address, amount);
    }

    public static Tuple accountAmount(final AccountID account, final Long amount, final boolean isApproval) {
        return Tuple.of(HapiParserUtil.asHeadlongAddress(asAddress(account)), amount, isApproval);
    }

    public static Tuple accountAmountAlias(final byte[] alias, final Long amount) {
        return Tuple.of(HapiParserUtil.asHeadlongAddress(alias), amount);
    }

    public static Tuple accountAmountAlias(final byte[] alias, final Long amount, final boolean isApproval) {
        return Tuple.of(HapiParserUtil.asHeadlongAddress(alias), amount, isApproval);
    }

    public static Tuple nftTransfer(final AccountID sender, final AccountID receiver, final Long serialNumber) {

        return Tuple.of(
                HapiParserUtil.asHeadlongAddress(asAddress(sender)),
                HapiParserUtil.asHeadlongAddress(asAddress(receiver)),
                serialNumber);
    }

    public static Tuple nftTransferToAlias(final AccountID sender, final byte[] alias, final Long serialNumber) {
        return Tuple.of(
                HapiParserUtil.asHeadlongAddress(asAddress(sender)),
                HapiParserUtil.asHeadlongAddress(alias),
                serialNumber);
    }

    public static Tuple nftTransferToAlias(
            final AccountID sender, final byte[] alias, final Long serialNumber, final boolean isApproval) {
        return Tuple.of(
                HapiParserUtil.asHeadlongAddress(asAddress(sender)),
                HapiParserUtil.asHeadlongAddress(alias),
                serialNumber,
                isApproval);
    }

    public static Tuple nftTransfer(
            final AccountID sender, final AccountID receiver, final Long serialNumber, final boolean isApproval) {
        return Tuple.of(
                HapiParserUtil.asHeadlongAddress(asAddress(sender)),
                HapiParserUtil.asHeadlongAddress(asAddress(receiver)),
                serialNumber,
                isApproval);
    }

    public static List<HapiSpecOperation> convertHapiCallsToEthereumCalls(final List<HapiSpecOperation> ops) {
        final var convertedOps = new ArrayList<HapiSpecOperation>(ops.size());
        for (final var op : ops) {
            if (op instanceof HapiContractCall callOp && callOp.isConvertableToEthCall()) {
                convertedOps.add(new HapiEthereumCall(callOp));
            } else {
                convertedOps.add(op);
            }
        }
        return convertedOps;
    }

    public static byte[] getPrivateKeyFromSpec(final HapiSpec spec, final String privateKeyRef) {
        var key = spec.registry().getKey(privateKeyRef);
        final var privateKey = spec.keys()
                .getPrivateKey(com.swirlds.common.utility.CommonUtils.hex(
                        key.getECDSASecp256K1().toByteArray()));

        byte[] privateKeyByteArray;
        byte[] dByteArray = ((BCECPrivateKey) privateKey).getD().toByteArray();
        if (dByteArray.length < 32) {
            privateKeyByteArray = new byte[32];
            System.arraycopy(dByteArray, 0, privateKeyByteArray, 32 - dByteArray.length, dByteArray.length);
        } else if (dByteArray.length == 32) {
            privateKeyByteArray = dByteArray;
        } else {
            privateKeyByteArray = new byte[32];
            System.arraycopy(dByteArray, dByteArray.length - 32, privateKeyByteArray, 0, 32);
        }

        return privateKeyByteArray;
    }

    private static double getChargedUsed(@NonNull final HapiSpec spec, @NonNull final String txn) {
        requireNonNull(spec);
        requireNonNull(txn);
        var subOp = getTxnRecord(txn).logged();
        allRunFor(spec, subOp);
        final var rcd = subOp.getResponseRecord();
        return (1.0 * rcd.getTransactionFee())
                / ONE_HBAR
                / rcd.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
                * rcd.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
                / 100;
    }
}
