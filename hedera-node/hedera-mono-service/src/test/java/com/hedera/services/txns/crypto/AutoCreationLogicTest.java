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
package com.hedera.services.txns.crypto;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.txns.crypto.AutoCreationLogic.AUTO_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.LAZY_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoCreationLogicTest {
    @Mock private UsageLimits usageLimits;
    @Mock private StateView currentView;
    @Mock private EntityIdSource ids;
    @Mock private EntityCreator creator;
    @Mock private TransactionContext txnCtx;
    @Mock private AliasManager aliasManager;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private FeeCalculator feeCalculator;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private GlobalDynamicProperties properties;

    private AutoCreationLogic subject;
    private final HashMap<ByteString, Integer> tokenAliasMap = new HashMap<>();

    @BeforeEach
    void setUp() {
        subject =
                new AutoCreationLogic(
                        usageLimits,
                        syntheticTxnFactory,
                        creator,
                        ids,
                        aliasManager,
                        sigImpactHistorian,
                        () -> currentView,
                        txnCtx,
                        properties);

        subject.setFeeCalculator(feeCalculator);
        tokenAliasMap.put(edKeyAlias, 1);
    }

    @Test
    void doesntAutoCreateWhenTokenTransferToAliasFeatureDisabled() {
        given(usageLimits.areCreatableAccounts(anyInt())).willReturn(true);
        given(properties.areTokenAutoCreationsEnabled()).willReturn(false);

        final var input = wellKnownTokenChange();
        final var changes = List.of(input);

        final var result = subject.create(input, accountsLedger, changes);
        assertEquals(NOT_SUPPORTED, result.getLeft());
    }

    @Test
    void refusesToCreateBeyondMaxNumber() {
        final var input = wellKnownChange(edKeyAlias);
        final var changes = List.of(input);

        final var result = subject.create(input, accountsLedger, changes);
        assertEquals(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, result.getLeft());
    }

    @Test
    void cannotCreateAccountFromUnaliasedChange() {
        given(usageLimits.areCreatableAccounts(anyInt())).willReturn(true);
        final var input =
                BalanceChange.changingHbar(
                        AccountAmount.newBuilder()
                                .setAmount(initialTransfer)
                                .setAccountID(payer)
                                .build(),
                        payer);
        final var changes = List.of(input);

        final var result =
                assertThrows(
                        IllegalStateException.class,
                        () -> subject.create(input, accountsLedger, changes));
        assertTrue(
                result.getMessage()
                        .contains("Cannot auto-create an account from unaliased change"));
    }

    @Test
    void happyPathWithHbarChangeWorks() {
        givenCollaborators(mockBuilder, AUTO_MEMO);
        given(syntheticTxnFactory.createAccount(aPrimitiveKey, 0L, 0))
                .willReturn(mockSyntheticCreation);

        final var input = wellKnownChange(edKeyAlias);
        final var expectedExpiry = consensusNow.getEpochSecond() + THREE_MONTHS_IN_SECONDS;
        final var changes = List.of(input);

        final var result = subject.create(input, accountsLedger, changes);
        subject.submitRecordsTo(recordsHistorian);

        assertEquals(initialTransfer, input.getAggregatedUnits());
        assertEquals(initialTransfer, input.getNewBalance());
        verify(aliasManager).link(edKeyAlias, createdNum);
        verify(sigImpactHistorian).markAliasChanged(edKeyAlias);
        verify(sigImpactHistorian).markEntityChanged(createdNum.longValue());
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.EXPIRY, expectedExpiry);
        verify(accountsLedger, never())
                .set(createdNum.toGrpcAccountId(), AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS, 1);
        verify(recordsHistorian)
                .trackPrecedingChildRecord(DEFAULT_SOURCE_ID, mockSyntheticCreation, mockBuilder);
        assertEquals(totalFee, mockBuilder.getFee());
        assertEquals(Pair.of(OK, totalFee), result);
        assertTrue(subject.getTokenAliasMap().isEmpty());
    }

    @Test
    void hollowAccountWithHbarChangeWorks()
            throws InvalidProtocolBufferException, DecoderException {
        final var jKey = JKey.mapKey(Key.parseFrom(ecdsaKeyBytes));
        final var evmAddressAlias =
                ByteString.copyFrom(
                        EthTxSigs.recoverAddressFromPubKey(jKey.getECDSASecp256k1Key()));

        final var mockBuilderWithEVMAlias =
                ExpirableTxnRecord.newBuilder()
                        .setAlias(evmAddressAlias)
                        .setReceiptBuilder(
                                TxnReceipt.newBuilder()
                                        .setAccountId(new EntityId(0, 0, createdNum.longValue())));

        givenCollaborators(mockBuilderWithEVMAlias, LAZY_MEMO);
        given(syntheticTxnFactory.createHollowAccount(evmAddressAlias, 0L))
                .willReturn(mockSyntheticCreation);

        final var input = wellKnownChange(evmAddressAlias);
        final var expectedExpiry = consensusNow.getEpochSecond() + THREE_MONTHS_IN_SECONDS;
        final var changes = List.of(input);

        final var result = subject.create(input, accountsLedger, changes);
        subject.submitRecordsTo(recordsHistorian);

        assertEquals(initialTransfer, input.getAggregatedUnits());
        assertEquals(initialTransfer, input.getNewBalance());
        verify(aliasManager).link(evmAddressAlias, createdNum);
        verify(sigImpactHistorian).markAliasChanged(evmAddressAlias);
        verify(sigImpactHistorian).markEntityChanged(createdNum.longValue());
        verify(accountsLedger, never())
                .set(createdNum.toGrpcAccountId(), AccountProperty.KEY, null);
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.ALIAS, evmAddressAlias);
        assertEquals(EntityIdUtils.EVM_ADDRESS_SIZE, evmAddressAlias.size());
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.EXPIRY, expectedExpiry);
        verify(accountsLedger, never())
                .set(createdNum.toGrpcAccountId(), AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS, 1);
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        DEFAULT_SOURCE_ID, mockSyntheticCreation, mockBuilderWithEVMAlias);
        assertEquals(totalFee, mockBuilderWithEVMAlias.getFee());
        assertEquals(Pair.of(OK, totalFee), result);
        assertTrue(subject.getTokenAliasMap().isEmpty());
    }

    @Test
    void happyPathWithFungibleTokenChangeWorks() {
        givenCollaborators(mockBuilder, AUTO_MEMO);
        given(properties.areTokenAutoCreationsEnabled()).willReturn(true);
        given(syntheticTxnFactory.createAccount(aPrimitiveKey, 0L, 1))
                .willReturn(mockSyntheticCreation);

        final var input = wellKnownTokenChange();
        final var expectedExpiry = consensusNow.getEpochSecond() + THREE_MONTHS_IN_SECONDS;
        final var changes = List.of(input);

        final var result = subject.create(input, accountsLedger, changes);
        subject.submitRecordsTo(recordsHistorian);

        assertEquals(initialTransfer, input.getAggregatedUnits());

        verify(aliasManager).link(edKeyAlias, createdNum);
        verify(sigImpactHistorian).markAliasChanged(edKeyAlias);
        verify(sigImpactHistorian).markEntityChanged(createdNum.longValue());
        verify(accountsLedger).create(createdNum.toGrpcAccountId());
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.IS_RECEIVER_SIG_REQUIRED, false);
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.IS_SMART_CONTRACT, false);
        verify(accountsLedger)
                .set(
                        createdNum.toGrpcAccountId(),
                        AccountProperty.AUTO_RENEW_PERIOD,
                        THREE_MONTHS_IN_SECONDS);
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.EXPIRY, expectedExpiry);
        verify(accountsLedger).set(createdNum.toGrpcAccountId(), AccountProperty.MEMO, AUTO_MEMO);
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS, 1);

        verify(recordsHistorian)
                .trackPrecedingChildRecord(DEFAULT_SOURCE_ID, mockSyntheticCreation, mockBuilder);
        assertEquals(totalFee, mockBuilder.getFee());
        assertEquals(Pair.of(OK, totalFee), result);
    }

    @Test
    void happyPathWithNonFungibleTokenChangeWorks() {
        givenCollaborators(mockBuilder, AUTO_MEMO);
        given(properties.areTokenAutoCreationsEnabled()).willReturn(true);
        given(syntheticTxnFactory.createAccount(aPrimitiveKey, 0L, 1))
                .willReturn(mockSyntheticCreation);

        final var input = wellKnownNftChange();
        final var expectedExpiry = consensusNow.getEpochSecond() + THREE_MONTHS_IN_SECONDS;
        final var changes = List.of(input);

        final var result = subject.create(input, accountsLedger, changes);
        subject.submitRecordsTo(recordsHistorian);

        assertEquals(20L, input.getAggregatedUnits());

        verify(aliasManager).link(edKeyAlias, createdNum);
        verify(sigImpactHistorian).markAliasChanged(edKeyAlias);
        verify(sigImpactHistorian).markEntityChanged(createdNum.longValue());
        verify(accountsLedger).create(createdNum.toGrpcAccountId());
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.IS_RECEIVER_SIG_REQUIRED, false);
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.IS_SMART_CONTRACT, false);
        verify(accountsLedger)
                .set(
                        createdNum.toGrpcAccountId(),
                        AccountProperty.AUTO_RENEW_PERIOD,
                        THREE_MONTHS_IN_SECONDS);
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.EXPIRY, expectedExpiry);
        verify(accountsLedger).set(createdNum.toGrpcAccountId(), AccountProperty.MEMO, AUTO_MEMO);
        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS, 1);

        verify(recordsHistorian)
                .trackPrecedingChildRecord(DEFAULT_SOURCE_ID, mockSyntheticCreation, mockBuilder);
        assertEquals(totalFee, mockBuilder.getFee());
        assertEquals(Pair.of(OK, totalFee), result);
        assertEquals(1, subject.getPendingCreations().size());

        /* ---- clear pending creations */
        assertTrue(subject.reclaimPendingAliases());
        verify(aliasManager).unlink(edKeyAlias);
    }

    @Test
    void analyzesTokenTransfersInChangesForAutoCreation() {
        givenCollaborators(mockBuilder, AUTO_MEMO);
        given(properties.areTokenAutoCreationsEnabled()).willReturn(true);
        given(syntheticTxnFactory.createAccount(aPrimitiveKey, 0L, 2))
                .willReturn(mockSyntheticCreation);

        final var input1 = wellKnownTokenChange();
        final var input2 = anotherTokenChange();
        final var changes = List.of(input1, input2);

        final var result = subject.create(input1, accountsLedger, changes);
        subject.submitRecordsTo(recordsHistorian);

        assertEquals(16L, input1.getAggregatedUnits());
        assertEquals(1, subject.getTokenAliasMap().size());
        assertEquals(2, subject.getTokenAliasMap().get(edKeyAlias).size());

        verify(aliasManager).link(edKeyAlias, createdNum);
        verify(sigImpactHistorian).markAliasChanged(edKeyAlias);
        verify(sigImpactHistorian).markEntityChanged(createdNum.longValue());
        verify(accountsLedger).create(createdNum.toGrpcAccountId());

        verify(accountsLedger)
                .set(createdNum.toGrpcAccountId(), AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS, 2);

        verify(recordsHistorian)
                .trackPrecedingChildRecord(DEFAULT_SOURCE_ID, mockSyntheticCreation, mockBuilder);
        assertEquals(totalFee, mockBuilder.getFee());
        assertEquals(Pair.of(OK, totalFee), result);

        /* ---- clear tokenAliasMap */
        assertEquals(1, subject.getPendingCreations().size());
        subject.reset();
        assertEquals(0, subject.getPendingCreations().size());
        assertEquals(0, subject.getTokenAliasMap().size());

        assertFalse(subject.reclaimPendingAliases());
    }

    private void givenCollaborators(ExpirableTxnRecord.Builder mockBuilder, String memo) {
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(ids.newAccountId(any())).willReturn(created);
        given(feeCalculator.computeFee(any(), eq(EMPTY_KEY), eq(currentView), eq(consensusNow)))
                .willReturn(fees);
        given(creator.createSuccessfulSyntheticRecord(eq(Collections.emptyList()), any(), eq(memo)))
                .willReturn(mockBuilder);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);
    }

    private BalanceChange wellKnownChange(ByteString alias) {
        return BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAmount(initialTransfer)
                        .setAccountID(AccountID.newBuilder().setAlias(alias).build())
                        .build(),
                payer);
    }

    private BalanceChange wellKnownTokenChange() {
        return BalanceChange.changingFtUnits(
                Id.fromGrpcToken(token),
                token,
                AccountAmount.newBuilder()
                        .setAmount(initialTransfer)
                        .setAccountID(AccountID.newBuilder().setAlias(edKeyAlias).build())
                        .build(),
                payer);
    }

    private BalanceChange wellKnownNftChange() {
        return BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(token),
                token,
                NftTransfer.newBuilder()
                        .setSenderAccountID(payer)
                        .setReceiverAccountID(AccountID.newBuilder().setAlias(edKeyAlias).build())
                        .setSerialNumber(20L)
                        .build(),
                payer);
    }

    private BalanceChange anotherTokenChange() {
        return BalanceChange.changingFtUnits(
                Id.fromGrpcToken(token1),
                token1,
                AccountAmount.newBuilder()
                        .setAmount(initialTransfer)
                        .setAccountID(AccountID.newBuilder().setAlias(edKeyAlias).build())
                        .build(),
                payer);
    }

    private static final TransactionBody.Builder mockSyntheticCreation =
            TransactionBody.newBuilder();
    private static final long initialTransfer = 16L;
    private static final Key aPrimitiveKey =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                    .build();
    private static final ByteString edKeyAlias = aPrimitiveKey.toByteString();
    private static final byte[] ecdsaKeyBytes =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    private static final AccountID created = IdUtils.asAccount("0.0.1234");
    public static final AccountID payer = IdUtils.asAccount("0.0.12345");
    private static final EntityNum createdNum = EntityNum.fromAccountId(created);
    private static final FeeObject fees = new FeeObject(1L, 2L, 3L);
    private static final long totalFee = 6L;
    private static final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
    private static final ExpirableTxnRecord.Builder mockBuilder =
            ExpirableTxnRecord.newBuilder()
                    .setAlias(edKeyAlias)
                    .setReceiptBuilder(
                            TxnReceipt.newBuilder()
                                    .setAccountId(new EntityId(0, 0, createdNum.longValue())));
    public static final TokenID token = IdUtils.asToken("0.0.23456");
    public static final TokenID token1 = IdUtils.asToken("0.0.123456");
}
