/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.throttling;

import static com.hedera.services.throttling.DeterministicThrottling.DeterministicThrottlingMode.CONSENSUS;
import static com.hedera.services.throttling.DeterministicThrottling.DeterministicThrottlingMode.HAPI;
import static com.hedera.services.throttling.DeterministicThrottling.DeterministicThrottlingMode.SCHEDULE;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttles.BucketThrottle;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.DeterministicThrottling.DeterministicThrottlingMode;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.SerdeUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class DeterministicThrottlingTest {
    private final int n = 2;
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 123);
    private static final ScheduleID scheduleID = IdUtils.asSchedule("0.0.333333");
    private final ThrottleReqOpsScaleFactor nftScaleFactor = ThrottleReqOpsScaleFactor.from("5:2");

    @Mock private TxnAccessor accessor;
    @Mock private ThrottleReqsManager manager;

    @Mock(lenient = true)
    private GlobalDynamicProperties dynamicProperties;

    @Mock private GasLimitDeterministicThrottle gasLimitDeterministicThrottle;
    @Mock private Query query;
    @Mock private ContractCallLocalQuery callLocalQuery;
    @Mock private AliasManager aliasManager;
    @Mock private ScheduleStore scheduleStore;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private DeterministicThrottling subject;

    @BeforeEach
    void setUp() {
        subject =
                new DeterministicThrottling(
                        () -> n, aliasManager, dynamicProperties, CONSENSUS, scheduleStore);
    }

    @Test
    void worksAsExpectedForKnownQueries() throws IOException {
        // setup:
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        // when:
        subject.rebuildFor(defs);
        // and:
        var noAns = subject.shouldThrottleQuery(CryptoGetAccountBalance, consensusNow, query);
        subject.shouldThrottleQuery(GetVersionInfo, consensusNow.plusNanos(1), query);
        var yesAns = subject.shouldThrottleQuery(GetVersionInfo, consensusNow.plusNanos(2), query);
        var throttlesNow = subject.activeThrottlesFor(CryptoGetAccountBalance);
        // and:
        var dNow = throttlesNow.get(0);

        // then:
        assertFalse(noAns);
        assertTrue(yesAns);
        assertEquals(10999999990000L, dNow.used());
    }

    @ParameterizedTest
    @CsvSource({
        "HAPI,true,true",
        "HAPI,false,true",
        "CONSENSUS,true,true",
        "CONSENSUS,false,true",
        "HAPI,true,false",
        "HAPI,false,false",
        "CONSENSUS,true,false",
        "CONSENSUS,false,false"
    })
    void usesScheduleCreateThrottleForSubmitMessage(
            final DeterministicThrottlingMode mode,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        final var scheduledSubmit =
                SchedulableTransactionBody.newBuilder()
                        .setConsensusSubmitMessage(
                                ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                        .build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleCreate(scheduledSubmit, waitForExpiry);
        final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }

        final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
        final var aNow = throttlesNow.get(0);

        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(149999992500000L, aNow.used());

        assertEquals(
                longTermEnabled && mode == HAPI && (!waitForExpiry) ? 149999255000000L : 0,
                subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "HAPI,true,true",
        "HAPI,false,true",
        "CONSENSUS,true,true",
        "CONSENSUS,false,true",
        "HAPI,true,false",
        "HAPI,false,false",
        "CONSENSUS,true,false",
        "CONSENSUS,false,false"
    })
    void usesScheduleCreateThrottleWithNestedThrottleExempt(
            final DeterministicThrottlingMode mode,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        final var scheduledSubmit =
                SchedulableTransactionBody.newBuilder()
                        .setConsensusSubmitMessage(
                                ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                        .build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor =
                scheduleCreate(scheduledSubmit, waitForExpiry, IdUtils.asAccount("0.0.02"));
        final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }

        final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
        final var aNow = throttlesNow.get(0);

        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(149999992500000L, aNow.used());

        assertEquals(0, subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({"HAPI,true", "HAPI,false", "CONSENSUS,true", "CONSENSUS,false"})
    void scheduleCreateAlwaysThrottledWhenNoBody(
            final DeterministicThrottlingMode mode, final boolean longTermEnabled)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        final var scheduledSubmit = SchedulableTransactionBody.newBuilder().build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleCreate(scheduledSubmit);
        final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i)));
        }

        final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
        final var aNow = throttlesNow.get(0);

        assertTrue(firstAns);
        assertEquals(0, aNow.used());

        assertEquals(0, subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({"HAPI,true", "HAPI,false", "CONSENSUS,true", "CONSENSUS,false"})
    void usesScheduleCreateThrottleForCryptoTransferNoAutoCreations(
            final DeterministicThrottlingMode mode, final boolean longTermEnabled)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        final var scheduledXferNoAliases =
                SchedulableTransactionBody.newBuilder()
                        .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                        .build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleCreate(scheduledXferNoAliases);
        final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
        final var aNow = throttlesNow.get(0);

        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());

        assertEquals(
                longTermEnabled && mode == HAPI ? BucketThrottle.capacityUnitsPerTxn() : 0,
                subject.activeThrottlesFor(CryptoTransfer).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({"HAPI,true", "HAPI,false", "CONSENSUS,true", "CONSENSUS,false"})
    void doesntUseCryptoCreateThrottleForCryptoTransferWithAutoCreationIfAutoCreationDisabled(
            final DeterministicThrottlingMode mode, final boolean longTermEnabled)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        final var alias = aPrimitiveKey.toByteString();
        final var scheduledXferWithAutoCreation =
                SchedulableTransactionBody.newBuilder()
                        .setCryptoTransfer(
                                CryptoTransferTransactionBody.newBuilder()
                                        .setTransfers(
                                                TransferList.newBuilder()
                                                        .addAccountAmounts(
                                                                AccountAmount.newBuilder()
                                                                        .setAmount(-1_000_000_000)
                                                                        .setAccountID(
                                                                                IdUtils.asAccount(
                                                                                        "0.0.3333")))
                                                        .addAccountAmounts(
                                                                AccountAmount.newBuilder()
                                                                        .setAmount(+1_000_000_000)
                                                                        .setAccountID(
                                                                                AccountID
                                                                                        .newBuilder()
                                                                                        .setAlias(
                                                                                                alias)))))
                        .build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleCreate(scheduledXferWithAutoCreation);
        final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
        final var aNow = throttlesNow.get(0);

        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());

        assertEquals(
                longTermEnabled && mode == HAPI ? BucketThrottle.capacityUnitsPerTxn() : 0,
                subject.activeThrottlesFor(CryptoTransfer).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({"HAPI,true", "HAPI,false", "CONSENSUS,true", "CONSENSUS,false"})
    void doesntUseCryptoCreateThrottleForCryptoTransferWithNoAliases(
            final DeterministicThrottlingMode mode, final boolean longTermEnabled)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        final var scheduledXferWithAutoCreation =
                SchedulableTransactionBody.newBuilder()
                        .setCryptoTransfer(
                                CryptoTransferTransactionBody.newBuilder()
                                        .setTransfers(
                                                TransferList.newBuilder()
                                                        .addAccountAmounts(
                                                                AccountAmount.newBuilder()
                                                                        .setAmount(-1_000_000_000)
                                                                        .setAccountID(
                                                                                IdUtils.asAccount(
                                                                                        "0.0.3333")))
                                                        .addAccountAmounts(
                                                                AccountAmount.newBuilder()
                                                                        .setAmount(+1_000_000_000)
                                                                        .setAccountID(
                                                                                IdUtils.asAccount(
                                                                                        "0.0.4444")))))
                        .build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleCreate(scheduledXferWithAutoCreation);
        final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
        final var aNow = throttlesNow.get(0);

        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());

        assertEquals(
                longTermEnabled && mode == HAPI ? BucketThrottle.capacityUnitsPerTxn() : 0,
                subject.activeThrottlesFor(CryptoTransfer).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({"HAPI,true", "HAPI,false", "CONSENSUS,true", "CONSENSUS,false"})
    void usesCryptoCreateThrottleForCryptoTransferWithAutoCreationInScheduleCreate(
            final DeterministicThrottlingMode mode, final boolean longTermEnabled)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        final var alias = aPrimitiveKey.toByteString();
        if (!(mode != HAPI && longTermEnabled)) {
            given(aliasManager.lookupIdBy(alias)).willReturn(MISSING_NUM);
        }
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        final var scheduledXferWithAutoCreation =
                SchedulableTransactionBody.newBuilder()
                        .setCryptoTransfer(
                                CryptoTransferTransactionBody.newBuilder()
                                        .setTransfers(
                                                TransferList.newBuilder()
                                                        .addAccountAmounts(
                                                                AccountAmount.newBuilder()
                                                                        .setAmount(-1_000_000_000)
                                                                        .setAccountID(
                                                                                IdUtils.asAccount(
                                                                                        "0.0.3333")))
                                                        .addAccountAmounts(
                                                                AccountAmount.newBuilder()
                                                                        .setAmount(+1_000_000_000)
                                                                        .setAccountID(
                                                                                AccountID
                                                                                        .newBuilder()
                                                                                        .setAlias(
                                                                                                alias)))))
                        .build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleCreate(scheduledXferWithAutoCreation);
        final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
        final var aNow = throttlesNow.get(0);

        assertFalse(ans);
        if (longTermEnabled && mode == HAPI) {
            // with long term enabled, we count the schedule create in addition to the auto
            // creations, which
            // is how it should have been to start with
            assertEquals(51 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        } else if (longTermEnabled) {
            // with long term enabled, consensus throttles do not count the contained txn
            assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        } else {
            assertEquals(50 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        }

        assertEquals(0, subject.activeThrottlesFor(CryptoTransfer).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({"HAPI,true", "HAPI,false", "CONSENSUS,true", "CONSENSUS,false"})
    void usesScheduleCreateThrottleForAliasedCryptoTransferWithNoAutoCreation(
            final DeterministicThrottlingMode mode, final boolean longTermEnabled)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        final var alias = aPrimitiveKey.toByteString();
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        if (!(mode != HAPI && longTermEnabled)) {
            given(aliasManager.lookupIdBy(alias)).willReturn(EntityNum.fromLong(1_234L));
        }
        final var scheduledXferWithAutoCreation =
                SchedulableTransactionBody.newBuilder()
                        .setCryptoTransfer(
                                CryptoTransferTransactionBody.newBuilder()
                                        .setTransfers(
                                                TransferList.newBuilder()
                                                        .addAccountAmounts(
                                                                AccountAmount.newBuilder()
                                                                        .setAmount(+1_000_000_000)
                                                                        .setAccountID(
                                                                                IdUtils.asAccount(
                                                                                        "0.0.3333")))
                                                        .addAccountAmounts(
                                                                AccountAmount.newBuilder()
                                                                        .setAmount(-1_000_000_000)
                                                                        .setAccountID(
                                                                                AccountID
                                                                                        .newBuilder()
                                                                                        .setAlias(
                                                                                                alias)))))
                        .build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleCreate(scheduledXferWithAutoCreation);
        final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
        final var aNow = throttlesNow.get(0);

        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());

        assertEquals(
                longTermEnabled && mode == HAPI ? BucketThrottle.capacityUnitsPerTxn() : 0,
                subject.activeThrottlesFor(CryptoTransfer).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "HAPI,true,true",
        "HAPI,false,true",
        "CONSENSUS,true,true",
        "CONSENSUS,false,true",
        "HAPI,true,false",
        "HAPI,false,false",
        "CONSENSUS,true,false",
        "CONSENSUS,false,false"
    })
    void usesScheduleSignThrottle(
            final DeterministicThrottlingMode mode,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);

        if (longTermEnabled && mode == HAPI) {
            final var scheduledSubmit =
                    SchedulableTransactionBody.newBuilder()
                            .setConsensusSubmitMessage(
                                    ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                            .build();

            final var accessor = scheduleCreate(scheduledSubmit, waitForExpiry);

            given(scheduleStore.getNoError(scheduleID))
                    .willReturn(ScheduleVirtualValue.from(accessor.getTxnBytes(), 0));
        }

        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleSign(scheduleID);
        final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }

        final var throttlesNow = subject.activeThrottlesFor(ScheduleSign);
        final var aNow = throttlesNow.get(0);

        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(149999992500000L, aNow.used());

        assertEquals(
                longTermEnabled && mode == HAPI && (!waitForExpiry) ? 149999255000000L : 0,
                subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "HAPI,true,true",
        "HAPI,false,true",
        "CONSENSUS,true,true",
        "CONSENSUS,false,true",
        "HAPI,true,false",
        "HAPI,false,false",
        "CONSENSUS,true,false",
        "CONSENSUS,false,false"
    })
    void usesScheduleSignThrottleWithNestedThrottleExempt(
            final DeterministicThrottlingMode mode,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);

        if (longTermEnabled && mode == HAPI) {
            final var scheduledSubmit =
                    SchedulableTransactionBody.newBuilder()
                            .setConsensusSubmitMessage(
                                    ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                            .build();

            final var accessor =
                    scheduleCreate(scheduledSubmit, waitForExpiry, IdUtils.asAccount("0.0.02"));

            given(scheduleStore.getNoError(scheduleID))
                    .willReturn(ScheduleVirtualValue.from(accessor.getTxnBytes(), 0));
        }

        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleSign(scheduleID);
        final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }

        final var throttlesNow = subject.activeThrottlesFor(ScheduleSign);
        final var aNow = throttlesNow.get(0);

        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(149999992500000L, aNow.used());

        assertEquals(0, subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());
    }

    @Test
    void scheduleSignAlwaysThrottledWhenNoBody() throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);
        subject.setMode(HAPI);

        final var scheduledSubmit = SchedulableTransactionBody.newBuilder().build();

        final var createAccessor = scheduleCreate(scheduledSubmit);

        given(scheduleStore.getNoError(scheduleID))
                .willReturn(ScheduleVirtualValue.from(createAccessor.getTxnBytes(), 0));

        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleSign(scheduleID);
        final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i)));
        }

        final var throttlesNow = subject.activeThrottlesFor(ScheduleSign);
        final var aNow = throttlesNow.get(0);

        assertTrue(firstAns);
        assertEquals(0L, aNow.used());

        assertEquals(0, subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());
    }

    @Test
    void scheduleSignAlwaysThrottledWhenNotExisting() throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);
        subject.setMode(HAPI);

        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleSign(scheduleID);
        final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i)));
        }

        final var throttlesNow = subject.activeThrottlesFor(ScheduleSign);
        final var aNow = throttlesNow.get(0);

        assertTrue(firstAns);
        assertEquals(0L, aNow.used());

        assertEquals(0, subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({"HAPI,true", "HAPI,false", "CONSENSUS,true", "CONSENSUS,false"})
    void usesCryptoCreateThrottleForCryptoTransferWithAutoCreationInScheduleSign(
            final DeterministicThrottlingMode mode, final boolean longTermEnabled)
            throws IOException {

        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(longTermEnabled);
        subject.setMode(mode);
        final var alias = aPrimitiveKey.toByteString();
        if (mode == HAPI && longTermEnabled) {
            given(aliasManager.lookupIdBy(alias)).willReturn(MISSING_NUM);
        }
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);

        if (longTermEnabled && mode == HAPI) {
            final var scheduledXferWithAutoCreation =
                    SchedulableTransactionBody.newBuilder()
                            .setCryptoTransfer(
                                    CryptoTransferTransactionBody.newBuilder()
                                            .setTransfers(
                                                    TransferList.newBuilder()
                                                            .addAccountAmounts(
                                                                    AccountAmount.newBuilder()
                                                                            .setAmount(
                                                                                    -1_000_000_000)
                                                                            .setAccountID(
                                                                                    IdUtils
                                                                                            .asAccount(
                                                                                                    "0.0.3333")))
                                                            .addAccountAmounts(
                                                                    AccountAmount.newBuilder()
                                                                            .setAmount(
                                                                                    +1_000_000_000)
                                                                            .setAccountID(
                                                                                    AccountID
                                                                                            .newBuilder()
                                                                                            .setAlias(
                                                                                                    alias)))))
                            .build();
            final var accessor = scheduleCreate(scheduledXferWithAutoCreation);
            given(scheduleStore.getNoError(scheduleID))
                    .willReturn(ScheduleVirtualValue.from(accessor.getTxnBytes(), 0));
        }
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleSign(scheduleID);
        final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        final var throttlesNow = subject.activeThrottlesFor(ScheduleSign);
        final var aNow = throttlesNow.get(0);

        assertFalse(ans);
        if (longTermEnabled && mode == HAPI) {
            // with long term enabled, we count the schedule create in addition to the auto
            // creations, which
            // is how it should have been to start with
            assertEquals(51 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        } else {
            // with long term disabled or mode not being HAPI, ScheduleSign is the only part that
            // counts
            assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        }

        assertEquals(0, subject.activeThrottlesFor(CryptoTransfer).get(0).used());
    }

    @Test
    void worksAsExpectedForUnknownQueries() throws IOException {
        // setup:
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        // when:
        subject.rebuildFor(defs);

        // then:
        assertTrue(subject.shouldThrottleQuery(ContractCallLocal, consensusNow, query));
    }

    @Test
    void shouldThrottleByGasAndTotalAllowedGasPerSecNotSetOrZero() {
        // setup:
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        subject.setMode(CONSENSUS);

        // when:
        subject.applyGasConfig();

        // then:
        assertEquals(0L, gasLimitDeterministicThrottle.getCapacity());
        assertThat(
                logCaptor.warnLogs(),
                contains("Consensus gas throttling enabled, but limited to 0 gas/sec"));
    }

    @Test
    void shouldThrottleByGasAndTotalAllowedGasPerSecNotSetOrZeroFrontend() {
        // setup:
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        subject.setMode(HAPI);

        // when:
        subject.applyGasConfig();

        // then:
        assertEquals(0L, gasLimitDeterministicThrottle.getCapacity());
        assertThat(
                logCaptor.warnLogs(),
                contains("Frontend gas throttling enabled, but limited to 0 gas/sec"));
    }

    @Test
    void shouldThrottleByGasAndTotalAllowedGasPerSecNotSetOrZeroSchedule() {
        // setup:
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        subject.setMode(SCHEDULE);

        // when:
        subject.applyGasConfig();

        // then:
        assertEquals(0L, gasLimitDeterministicThrottle.getCapacity());
        assertThat(
                logCaptor.warnLogs(),
                contains("Schedule gas throttling enabled, but limited to 0 gas/sec"));
    }

    @Test
    void managerBehavesAsExpectedForFungibleMint() throws IOException {
        // setup:
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenMintWith(0);

        // when:
        subject.rebuildFor(defs);
        // and:
        var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 3000; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }
        var throttlesNow = subject.activeThrottlesFor(TokenMint);
        var aNow = throttlesNow.get(0);

        // then:
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(29999955000000000L, aNow.used());
    }

    @Test
    void managerBehavesAsExpectedForNftMint() throws IOException {
        // setup:
        final var numNfts = 3;
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenMintWith(numNfts);
        given(dynamicProperties.nftMintScaleFactor()).willReturn(nftScaleFactor);
        // when:
        subject.rebuildFor(defs);
        // and:
        var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 400; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }
        var throttlesNow = subject.activeThrottlesFor(TokenMint);
        // and:
        var aNow = throttlesNow.get(0);

        // then:
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(29999994000000000L, aNow.used());
    }

    @Test
    void managerBehavesAsExpectedForMultiBucketOp() throws IOException {
        // setup:
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenFunction(ContractCall);

        // when:
        subject.rebuildFor(defs);
        // and:
        var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 12; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }
        var throttlesNow = subject.activeThrottlesFor(ContractCall);
        // and:
        var aNow = throttlesNow.get(0);
        var bNow = throttlesNow.get(1);

        // then:
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(24999999820000000L, aNow.used());
        assertEquals(9999999940000L, bNow.used());
    }

    @Test
    void handlesThrottleExemption() throws IOException {
        // setup:
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenFunction(ContractCall);
        given(accessor.throttleExempt()).willReturn(true);

        // when:
        subject.rebuildFor(defs);
        // and:
        var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        for (int i = 1; i <= 12; i++) {
            assertFalse(subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i)));
        }
        var throttlesNow = subject.activeThrottlesFor(ContractCall);
        // and:
        var aNow = throttlesNow.get(0);
        var bNow = throttlesNow.get(1);

        // then:
        assertFalse(firstAns);
        assertEquals(0, aNow.used());
        assertEquals(0, bNow.used());
    }

    @Test
    void computesNumAutoCreationsIfNotAlreadyKnown() throws IOException {
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenFunction(CryptoTransfer);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        given(accessor.getNumAutoCreations()).willReturn(0);
        subject.rebuildFor(defs);

        var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        verify(accessor).countAutoCreationsWith(aliasManager);
        assertFalse(ans);
    }

    @Test
    void reusesNumAutoCreationsIfNotCounted() throws IOException {
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenFunction(CryptoTransfer);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        given(accessor.areAutoCreationsCounted()).willReturn(true);
        given(accessor.getNumAutoCreations()).willReturn(0);
        subject.rebuildFor(defs);

        var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 10000; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }

        verify(accessor, never()).countAutoCreationsWith(aliasManager);
        assertFalse(firstAns);
        assertTrue(subsequentAns);
    }

    @Test
    void cryptoTransfersWithNoAutoAccountCreationsAreThrottledAsExpected() throws IOException {
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenFunction(CryptoTransfer);
        subject.rebuildFor(defs);

        var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        assertFalse(ans);
    }

    @Test
    void managerAllowsCryptoTransfersWithAutoAccountCreationsAsExpected() throws IOException {
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenFunction(CryptoTransfer);
        given(accessor.getNumAutoCreations()).willReturn(1);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        subject.rebuildFor(defs);

        var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        assertFalse(ans);
    }

    @Test
    void managerRejectsCryptoTransfersWithAutoAccountCreationsAsExpected() throws IOException {
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        givenFunction(CryptoTransfer);
        given(accessor.getNumAutoCreations()).willReturn(10);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        subject.rebuildFor(defs);

        var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        assertTrue(ans);
    }

    @Test
    void managerRejectsCryptoTransfersWithMissingCryptoCreateThrottle() throws IOException {
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles-sans-creation.json");

        givenFunction(CryptoTransfer);
        given(accessor.getNumAutoCreations()).willReturn(1);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
        subject.rebuildFor(defs);

        var ans = subject.shouldThrottleTxn(accessor, consensusNow);

        assertTrue(ans);
    }

    @Test
    void logsErrorOnBadBucketButDoesntFail() throws IOException {
        final var ridiculousSplitFactor = 1_000_000;
        subject =
                new DeterministicThrottling(
                        () -> ridiculousSplitFactor,
                        aliasManager,
                        dynamicProperties,
                        CONSENSUS,
                        scheduleStore);

        var defs = SerdeUtils.pojoDefs("bootstrap/insufficient-capacity-throttles.json");

        // expect:
        assertDoesNotThrow(() -> subject.rebuildFor(defs));
        // and:
        assertEquals(1, subject.activeThrottlesFor(CryptoGetAccountBalance).size());
        // and:
        assertThat(
                logCaptor.errorLogs(),
                contains(
                        "When constructing bucket 'A' from state:"
                            + " NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: Bucket A contains an"
                            + " unsatisfiable milliOpsPerSec with 1000000 nodes!"));
    }

    @Test
    void alwaysThrottlesContractCallWhenGasThrottleIsNotDefined() {
        givenFunction(ContractCall);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(0L);
        subject.setMode(CONSENSUS);
        subject.applyGasConfig();
        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysThrottlesContractCallWhenGasThrottleReturnsTrue() {
        givenFunction(ContractCall);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(1L);
        given(accessor.getGasLimitForContractTx()).willReturn(2L);
        subject.setMode(CONSENSUS);
        subject.applyGasConfig();
        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysThrottlesContractCreateWhenGasThrottleIsNotDefined() {
        givenFunction(ContractCreate);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(0L);
        subject.setMode(CONSENSUS);
        subject.applyGasConfig();
        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysThrottlesContractCreateWhenGasThrottleReturnsTrue() {
        givenFunction(ContractCreate);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(1L);
        given(accessor.getGasLimitForContractTx()).willReturn(2L);
        subject.setMode(CONSENSUS);
        subject.applyGasConfig();
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
        assertTrue(subject.wasLastTxnGasThrottled());

        givenFunction(TokenBurn);
        subject.shouldThrottleTxn(accessor, consensusNow.plusSeconds(1));
        assertFalse(subject.wasLastTxnGasThrottled());
    }

    @Test
    void alwaysThrottlesEthereumTxnWhenGasThrottleIsNotDefined() {
        givenFunction(EthereumTransaction);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(0L);
        subject.setMode(CONSENSUS);
        subject.applyGasConfig();
        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysThrottlesEthereumTxnWhenGasThrottleReturnsTrue() {
        givenFunction(EthereumTransaction);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(1L);
        given(accessor.getGasLimitForContractTx()).willReturn(2L);
        subject.setMode(CONSENSUS);
        subject.applyGasConfig();
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
        assertTrue(subject.wasLastTxnGasThrottled());

        givenFunction(TokenBurn);
        subject.shouldThrottleTxn(accessor, consensusNow.plusSeconds(1));
        assertFalse(subject.wasLastTxnGasThrottled());
    }

    @Test
    void gasLimitThrottleReturnsCorrectObject() {
        var capacity = 10L;
        given(dynamicProperties.maxGasPerSec()).willReturn(capacity);
        subject.setMode(CONSENSUS);
        subject.applyGasConfig();
        // expect:
        assertEquals(capacity, subject.gasLimitThrottle().getCapacity());
    }

    @Test
    void gasLimitFrontendThrottleReturnsCorrectObject() {
        long capacity = 3423423423L;
        given(dynamicProperties.maxGasPerSec()).willReturn(capacity);
        subject.setMode(HAPI);
        subject.applyGasConfig();
        // expect:
        assertEquals(capacity, subject.gasLimitThrottle().getCapacity());
    }

    @Test
    void gasLimitScheduleThrottleReturnsCorrectObject() {
        long capacity = 1323223423L;
        given(dynamicProperties.scheduleThrottleMaxGasLimit()).willReturn(capacity);
        subject.setMode(SCHEDULE);
        subject.applyGasConfig();
        // expect:
        assertEquals(capacity, subject.gasLimitThrottle().getCapacity());
    }

    @Test
    void logsAsExpected() throws IOException {
        // setup:
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
        final var desired =
                "Resolved throttles for CONSENSUS (after splitting capacity 2 ways) - \n"
                        + "  ContractCall: min{6.00 tps (A), 5.00 tps (B)}\n"
                        + "  CryptoCreate: min{5000.00 tps (A), 1.00 tps (C)}\n"
                        + "  CryptoGetAccountBalance: min{5.00 tps (D)}\n"
                        + "  CryptoTransfer: min{5000.00 tps (A)}\n"
                        + "  GetVersionInfo: min{0.50 tps (D)}\n"
                        + "  TokenAssociateToAccount: min{50.00 tps (C)}\n"
                        + "  TokenCreate: min{50.00 tps (C)}\n"
                        + "  TokenMint: min{1500.00 tps (A)}\n"
                        + "  TransactionGetReceipt: min{5.00 tps (D)}";

        // when:
        subject.rebuildFor(defs);

        // then:
        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void logsActiveConsensusGasThrottlesAsExpected() {
        var capacity = 1000L;
        // setup:
        given(dynamicProperties.maxGasPerSec()).willReturn(capacity);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);

        final var desired = "Resolved CONSENSUS gas throttle -\n  1000 gas/sec (throttling ON)";

        // when:
        subject.applyGasConfig();

        // then:
        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void logsInertConsensusGasThrottlesAsExpected() {
        var capacity = 1000L;
        // setup:
        given(dynamicProperties.maxGasPerSec()).willReturn(capacity);

        final var desired = "Resolved CONSENSUS gas throttle -\n  1000 gas/sec (throttling OFF)";

        // when:
        subject.applyGasConfig();

        // then:
        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void logsActiveFrontendGasThrottlesAsExpected() {
        subject =
                new DeterministicThrottling(
                        () -> 4, aliasManager, dynamicProperties, HAPI, scheduleStore);

        var capacity = 1000L;
        // setup:
        given(dynamicProperties.maxGasPerSec()).willReturn(capacity);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);

        final var desired = "Resolved HAPI gas throttle -\n  1000 gas/sec (throttling ON)";

        // when:
        subject.applyGasConfig();

        // then:
        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void logsInertFrontendGasThrottlesAsExpected() {
        subject =
                new DeterministicThrottling(
                        () -> 4, aliasManager, dynamicProperties, HAPI, scheduleStore);

        var capacity = 1000L;
        // setup:
        given(dynamicProperties.maxGasPerSec()).willReturn(capacity);

        final var desired = "Resolved HAPI gas throttle -\n  1000 gas/sec (throttling OFF)";

        // when:
        subject.applyGasConfig();

        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void logsActiveScheduleGasThrottlesAsExpected() {
        subject =
                new DeterministicThrottling(
                        () -> 4, aliasManager, dynamicProperties, SCHEDULE, scheduleStore);

        var capacity = 1000L;
        // setup:
        given(dynamicProperties.scheduleThrottleMaxGasLimit()).willReturn(capacity);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);

        final var desired = "Resolved SCHEDULE gas throttle -\n  1000 gas/sec (throttling ON)";

        // when:
        subject.applyGasConfig();

        // then:
        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void logsInertScheduleGasThrottlesAsExpected() {
        subject =
                new DeterministicThrottling(
                        () -> 4, aliasManager, dynamicProperties, SCHEDULE, scheduleStore);

        var capacity = 1000L;
        // setup:
        given(dynamicProperties.scheduleThrottleMaxGasLimit()).willReturn(capacity);

        final var desired = "Resolved SCHEDULE gas throttle -\n  1000 gas/sec (throttling OFF)";

        // when:
        subject.applyGasConfig();

        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void constructsExpectedBucketsFromTestResource() throws IOException {
        // setup:
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        // and:
        var expected =
                List.of(
                        DeterministicThrottle.withMtpsAndBurstPeriod(15_000_000, 2),
                        DeterministicThrottle.withMtpsAndBurstPeriod(5_000, 2),
                        DeterministicThrottle.withMtpsAndBurstPeriod(50_000, 3),
                        DeterministicThrottle.withMtpsAndBurstPeriod(5000, 4));

        // when:
        subject.rebuildFor(defs);
        // and:
        var rebuilt = subject.allActiveThrottles();

        // then:
        assertEquals(expected, rebuilt);
    }

    @Test
    void alwaysRejectsIfNoThrottle() {
        givenFunction(ContractCall);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysRejectsIfNoThrottleForCreate() {
        givenFunction(ContractCreate);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysRejectsIfNoThrottleForEthereumTxn() {
        givenFunction(EthereumTransaction);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysRejectsIfNoThrottleForConsensus() {
        givenFunction(ContractCall);
        subject.setMode(CONSENSUS);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysRejectsIfNoThrottleForCreateForConsensus() {
        givenFunction(ContractCreate);
        subject.setMode(CONSENSUS);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void alwaysRejectsIfNoThrottleForEthereumTxnForConsensus() {
        givenFunction(EthereumTransaction);
        subject.setMode(CONSENSUS);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void returnsNoActiveThrottlesForUnconfiguredOp() {
        Assertions.assertSame(Collections.emptyList(), subject.activeThrottlesFor(ContractCall));
    }

    @Test
    void shouldAllowWithEnoughCapacity() {
        // setup:
        subject.setFunctionReqs(reqsManager());

        givenFunction(CryptoTransfer);
        given(manager.allReqsMetAt(consensusNow)).willReturn(true);

        // then:
        assertFalse(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void shouldRejectWithInsufficientCapacity() {
        subject.setFunctionReqs(reqsManager());

        givenFunction(CryptoTransfer);

        assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
    }

    @Test
    void requiresExplicitTimestamp() {
        // expect:
        assertThrows(
                UnsupportedOperationException.class, () -> subject.shouldThrottleTxn(accessor));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.shouldThrottleQuery(FileGetInfo, query));
    }

    @Test
    void frontEndContractCreateTXCallsFrontendGasThrottle() throws IOException {
        Instant now = Instant.now();
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        // setup:
        givenFunction(ContractCreate);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);

        subject.rebuildFor(defs);

        // when:
        assertTrue(subject.shouldThrottleTxn(accessor, now));
    }

    @Test
    void frontEndContractCallTXCallsFrontendGasThrottle() {
        Instant now = Instant.now();

        // setup:
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        givenFunction(ContractCall);

        // when:
        assertTrue(subject.shouldThrottleTxn(accessor, now));
    }

    @Test
    void frontEndEthereumTxnTXCallsFrontendGasThrottle() {
        Instant now = Instant.now();

        // setup:
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        subject.setMode(CONSENSUS);

        // when:
        assertTrue(subject.shouldThrottleTxn(accessor, now));
    }

    @Test
    void contractCreateTXCallsConsensusGasThrottle() throws IOException {
        Instant now = Instant.now();
        var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

        // setup:
        givenFunction(ContractCreate);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        subject.setMode(CONSENSUS);

        // when:
        subject.rebuildFor(defs);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, now));
    }

    @Test
    void contractCreateTXCallsConsensusGasThrottleWithDefinitions() {
        Instant now = Instant.now();

        // setup:
        givenFunction(ContractCreate);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(10L);
        given(accessor.getTxn())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setContractCreateInstance(
                                        ContractCreateTransactionBody.newBuilder().setGas(11L))
                                .build());
        subject.setMode(CONSENSUS);

        // when:
        subject.applyGasConfig();

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, now));
    }

    @Test
    void contractCallTXCallsConsensusGasThrottleWithDefinitions() {
        Instant now = Instant.now();

        // setup:
        givenFunction(ContractCall);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(10L);
        given(accessor.getTxn())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setContractCall(
                                        ContractCallTransactionBody.newBuilder().setGas(11L))
                                .build());
        subject.setMode(CONSENSUS);

        // when:
        subject.applyGasConfig();

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor, now));
    }

    @Test
    void consensusContractCallTxCallsConsensusThrottle() {
        Instant now = Instant.now();
        var miscUtilsHandle = mockStatic(MiscUtils.class);
        miscUtilsHandle.when(() -> MiscUtils.isGasThrottled(ContractCall)).thenReturn(false);
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        subject.setMode(CONSENSUS);

        subject.applyGasConfig();

        assertTrue(subject.shouldThrottleTxn(accessor, now));
        miscUtilsHandle.close();
    }

    @Test
    void verifyLeakUnusedGas() {
        Instant now = Instant.now();
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(150L);

        subject.applyGasConfig();

        assertTrue(subject.gasLimitThrottle().allow(now, 100));
        assertFalse(subject.gasLimitThrottle().allow(now, 100));

        subject.leakUnusedGasPreviouslyReserved(accessor, 100L);

        assertTrue(subject.gasLimitThrottle().allow(now, 100));
        assertFalse(subject.gasLimitThrottle().allow(now, 100));

        given(accessor.throttleExempt()).willReturn(true);

        subject.leakUnusedGasPreviouslyReserved(accessor, 100L);

        assertFalse(subject.gasLimitThrottle().allow(now, 100));
        assertFalse(subject.gasLimitThrottle().allow(now, 100));
    }

    @Test
    void reclaimsAllUsagesOnThrottledShouldThrottleTxn() throws IOException {
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);
        subject.setMode(HAPI);
        final var scheduledSubmit =
                SchedulableTransactionBody.newBuilder()
                        .setConsensusSubmitMessage(
                                ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                        .build();
        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles-inverted.json");
        subject.rebuildFor(defs);

        final var accessor = scheduleCreate(scheduledSubmit);
        final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
        }

        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(4999250000000L, subject.activeThrottlesFor(ScheduleCreate).get(0).used());

        assertEquals(
                4999999250000L, subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());

        subject.resetUsage();
        assertEquals(0L, subject.activeThrottlesFor(ScheduleCreate).get(0).used());

        assertEquals(0L, subject.activeThrottlesFor(ConsensusSubmitMessage).get(0).used());
    }

    @Test
    void reclaimsAllUsagesOnThrottledShouldThrottleQuery() throws IOException {
        Instant now = Instant.now();
        given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
        given(dynamicProperties.maxGasPerSec()).willReturn(10L);
        given(query.getContractCallLocal()).willReturn(callLocalQuery);
        given(callLocalQuery.getGas()).willReturn(3L);

        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles-inverted.json");
        subject.rebuildFor(defs);

        subject.applyGasConfig();

        assertFalse(subject.shouldThrottleQuery(ContractCallLocal, now, query));
        assertTrue(subject.shouldThrottleQuery(ContractCallLocal, now, query));

        assertEquals(1000000000000L, subject.activeThrottlesFor(ContractCallLocal).get(0).used());
        assertEquals(3L, subject.gasLimitThrottle().getUsed());

        subject.resetUsage();

        assertEquals(0L, subject.activeThrottlesFor(ContractCallLocal).get(0).used());
        assertEquals(0L, subject.gasLimitThrottle().getUsed());
    }

    @Test
    void scheduleThrottlesBuildCorrectly() throws IOException {
        subject =
                new DeterministicThrottling(
                        () -> 1, aliasManager, dynamicProperties, CONSENSUS, scheduleStore);

        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(100L);
        subject.setMode(SCHEDULE);

        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-throttles.json");
        subject.rebuildFor(defs);

        final EnumMap<HederaFunctionality, List<Long>> groups =
                new EnumMap<>(HederaFunctionality.class);

        long sum = 0;
        long grpCount = 0;

        for (var bucket : subject.getActiveDefs().getBuckets()) {
            assertEquals(1000, bucket.getBurstPeriodMs());
            assertEquals(1, bucket.getBurstPeriod());
            for (var group : bucket.getThrottleGroups()) {
                sum += group.impliedMilliOpsPerSec();
                ++grpCount;
                for (var op : group.getOperations()) {
                    var list = groups.computeIfAbsent(op, k -> new ArrayList<>());
                    list.add(group.impliedMilliOpsPerSec());
                }
            }
        }

        assertTrue(sum <= 100000L);
        assertEquals(5, groups.size());
        assertEquals(3, grpCount);

        assertEquals(ImmutableList.of(98000L), groups.get(CryptoTransfer));
        assertEquals(ImmutableList.of(98000L), groups.get(ConsensusSubmitMessage));
        assertEquals(ImmutableList.of(1000L), groups.get(CryptoCreate));
        assertEquals(ImmutableList.of(98000L, 1000L), groups.get(CryptoUpdate));
        assertEquals(ImmutableList.of(1000L), groups.get(CryptoDelete));
    }

    @Test
    void scheduleThrottlesErrorOnNotPossible() throws IOException {
        subject =
                new DeterministicThrottling(
                        () -> 1, aliasManager, dynamicProperties, CONSENSUS, scheduleStore);

        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(1L);
        subject.setMode(SCHEDULE);

        var defs = SerdeUtils.pojoDefs("bootstrap/schedule-throttles.json");

        assertThrows(IllegalStateException.class, () -> subject.rebuildFor(defs));
    }

    private void givenFunction(HederaFunctionality functionality) {
        given(accessor.getFunction()).willReturn(functionality);
    }

    private void givenMintWith(int numNfts) {
        // setup:
        final List<ByteString> meta = new ArrayList<>();
        final var op = TokenMintTransactionBody.newBuilder();
        if (numNfts == 0) {
            op.setAmount(1_234_567L);
        } else {
            while (numNfts-- > 0) {
                op.addMetadata(ByteString.copyFromUtf8("metadata" + numNfts));
            }
        }
        final var txn = TransactionBody.newBuilder().setTokenMint(op).build();

        given(accessor.getFunction()).willReturn(TokenMint);
        given(accessor.getTxn()).willReturn(txn);
    }

    private EnumMap<HederaFunctionality, ThrottleReqsManager> reqsManager() {
        EnumMap<HederaFunctionality, ThrottleReqsManager> opsManagers =
                new EnumMap<>(HederaFunctionality.class);
        opsManagers.put(CryptoTransfer, manager);
        return opsManagers;
    }

    private SignedTxnAccessor scheduleCreate(final SchedulableTransactionBody inner) {
        return scheduleCreate(inner, false);
    }

    private SignedTxnAccessor scheduleCreate(
            final SchedulableTransactionBody inner, boolean waitForExpiry) {
        return scheduleCreate(inner, waitForExpiry, null);
    }

    private SignedTxnAccessor scheduleCreate(
            final SchedulableTransactionBody inner, boolean waitForExpiry, AccountID customPayer) {
        final var schedule =
                ScheduleCreateTransactionBody.newBuilder()
                        .setWaitForExpiry(waitForExpiry)
                        .setScheduledTransactionBody(inner);
        if (customPayer != null) {
            schedule.setPayerAccountID(customPayer);
        }
        final var body = TransactionBody.newBuilder().setScheduleCreate(schedule).build();
        final var signedTxn =
                SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build();
        final var txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(signedTxn.toByteString())
                        .build();
        return SignedTxnAccessor.uncheckedFrom(txn);
    }

    private SignedTxnAccessor scheduleSign(ScheduleID scheduleID) {
        final var schedule = ScheduleSignTransactionBody.newBuilder().setScheduleID(scheduleID);
        final var body = TransactionBody.newBuilder().setScheduleSign(schedule).build();
        final var signedTxn =
                SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build();
        final var txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(signedTxn.toByteString())
                        .build();
        return SignedTxnAccessor.uncheckedFrom(txn);
    }

    private static final Key aPrimitiveKey =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                    .build();
}
