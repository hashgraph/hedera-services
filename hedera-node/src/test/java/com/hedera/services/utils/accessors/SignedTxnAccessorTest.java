/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.accessors;

import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE0_PRIVATE_ECDSA_KEY;
import static com.hedera.test.utils.IdUtils.*;
import static com.hedera.test.utils.TxnUtils.buildTransactionFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.google.protobuf.*;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.KeyUtils;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.fee.FeeBuilder;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SignedTxnAccessorTest {
    private static final String memo = "Eternal sunshine of the spotless mind";
    private static final String zeroByteMemo = "Eternal s\u0000nshine of the spotless mind";
    private static final byte[] memoUtf8Bytes = memo.getBytes();
    private static final byte[] zeroByteMemoUtf8Bytes = zeroByteMemo.getBytes();

    private static final AccountID spender1 = asAccount("0.0.1000");
    private static final AccountID owner = asAccount("0.0.1001");
    private static final TokenID token1 = asToken("0.0.2000");
    private static final TokenID token2 = asToken("0.0.3000");
    private static final CryptoAllowance cryptoAllowance1 =
            CryptoAllowance.newBuilder().setSpender(spender1).setAmount(10L).build();
    private static final TokenAllowance tokenAllowance1 =
            TokenAllowance.newBuilder()
                    .setSpender(spender1)
                    .setAmount(10L)
                    .setTokenId(token1)
                    .build();
    private static final NftAllowance nftAllowance1 =
            NftAllowance.newBuilder()
                    .setSpender(spender1)
                    .setTokenId(token2)
                    .setApprovedForAll(BoolValue.of(false))
                    .addAllSerialNumbers(List.of(1L, 10L))
                    .build();

    private static final NftRemoveAllowance nftRemoveAllowance =
            NftRemoveAllowance.newBuilder()
                    .setOwner(owner)
                    .setTokenId(token2)
                    .addAllSerialNumbers(List.of(1L, 10L))
                    .build();

    private static final SignatureMap expectedMap =
            SignatureMap.newBuilder()
                    .addSigPair(
                            SignaturePair.newBuilder()
                                    .setPubKeyPrefix(ByteString.copyFromUtf8("f"))
                                    .setEd25519(ByteString.copyFromUtf8("irst")))
                    .addSigPair(
                            SignaturePair.newBuilder()
                                    .setPubKeyPrefix(ByteString.copyFromUtf8("s"))
                                    .setEd25519(ByteString.copyFromUtf8("econd")))
                    .build();

    @Test
    @SuppressWarnings("uncheckeed")
    void getsCryptoSigMappingFromKnownRationalizedMeta() {
        final var subject = mock(PlatformTxnAccessor.class);
        final RationalizedSigMeta sigMeta = mock(RationalizedSigMeta.class);
        final Function<byte[], TransactionSignature> mockFn = mock(Function.class);
        given(sigMeta.pkToVerifiedSigFn()).willReturn(mockFn);
        given(subject.getSigMeta()).willReturn(sigMeta);

        doCallRealMethod().when(subject).getRationalizedPkToCryptoSigFn();

        assertThrows(IllegalStateException.class, subject::getRationalizedPkToCryptoSigFn);

        given(sigMeta.couldRationalizeOthers()).willReturn(true);
        assertSame(mockFn, subject.getRationalizedPkToCryptoSigFn());
    }

    @Test
    void uncheckedPropagatesIaeOnNonsense() {
        final var nonsenseTxn = buildTransactionFrom(ByteString.copyFromUtf8("NONSENSE"));

        assertThrows(
                IllegalArgumentException.class, () -> SignedTxnAccessor.uncheckedFrom(nonsenseTxn));
    }

    @Test
    void parsesLegacyCorrectly() throws Exception {
        final Key aPrimitiveKey =
                Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                        .build();
        final ByteString aNewAlias = aPrimitiveKey.toByteString();
        final AliasManager aliasManager = mock(AliasManager.class);
        given(aliasManager.lookupIdBy(any())).willReturn(EntityNum.MISSING_NUM);

        final long offeredFee = 100_000_000L;
        var xferNoAliases =
                RequestBuilder.getCryptoTransferRequest(
                        1234l,
                        0l,
                        0l,
                        3l,
                        0l,
                        0l,
                        offeredFee,
                        Timestamp.getDefaultInstance(),
                        Duration.getDefaultInstance(),
                        false,
                        zeroByteMemo,
                        5678l,
                        -70000l,
                        5679l,
                        70000l);
        xferNoAliases = xferNoAliases.toBuilder().setSigMap(expectedMap).build();
        var xferWithAutoCreation =
                RequestBuilder.getHbarCryptoTransferRequestToAlias(
                        1234l,
                        0l,
                        0l,
                        3l,
                        0l,
                        0l,
                        offeredFee,
                        Timestamp.getDefaultInstance(),
                        Duration.getDefaultInstance(),
                        false,
                        zeroByteMemo,
                        5678l,
                        -70000l,
                        aNewAlias,
                        70000l);
        xferWithAutoCreation = xferWithAutoCreation.toBuilder().setSigMap(expectedMap).build();
        var xferWithAliasesNoAutoCreation =
                RequestBuilder.getTokenTransferRequestToAlias(
                        1234l,
                        0l,
                        0l,
                        3l,
                        0l,
                        0l,
                        offeredFee,
                        Timestamp.getDefaultInstance(),
                        Duration.getDefaultInstance(),
                        false,
                        zeroByteMemo,
                        5678l,
                        5555l,
                        -70000l,
                        ByteString.copyFromUtf8("aaaa"),
                        70000l);
        xferWithAliasesNoAutoCreation =
                xferWithAliasesNoAutoCreation.toBuilder().setSigMap(expectedMap).build();
        final var body = CommonUtils.extractTransactionBody(xferNoAliases);

        var accessor = SignedTxnAccessor.uncheckedFrom(xferNoAliases);
        accessor.countAutoCreationsWith(aliasManager);
        final var txnUsageMeta = accessor.baseUsageMeta();

        assertEquals(xferNoAliases, accessor.getSignedTxnWrapper());
        assertArrayEquals(xferNoAliases.toByteArray(), accessor.getSignedTxnWrapperBytes());
        assertEquals(body, accessor.getTxn());
        assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
        assertEquals(body.getTransactionID(), accessor.getTxnId());
        assertEquals(1234l, accessor.getPayer().getAccountNum());
        assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
        assertEquals(offeredFee, accessor.getOfferedFee());
        assertArrayEquals(
                CommonUtils.noThrowSha384HashOf(xferNoAliases.toByteArray()), accessor.getHash());
        assertEquals(expectedMap, accessor.getSigMap());
        assertArrayEquals("irst".getBytes(), accessor.getPkToSigsFn().sigBytesFor("f".getBytes()));
        assertArrayEquals(zeroByteMemoUtf8Bytes, accessor.getMemoUtf8Bytes());
        assertTrue(accessor.memoHasZeroByte());
        assertEquals(
                FeeBuilder.getSignatureCount(accessor.getSignedTxnWrapper()),
                accessor.usageGiven(1).numSigs());
        assertEquals(
                FeeBuilder.getSignatureSize(accessor.getSignedTxnWrapper()),
                accessor.usageGiven(1).sigsSize());
        assertEquals(zeroByteMemo, accessor.getMemo());
        assertEquals(false, accessor.isTriggeredTxn());
        assertEquals(false, accessor.canTriggerTxn());
        assertEquals(0, accessor.getNumAutoCreations());
        assertEquals(memoUtf8Bytes.length, txnUsageMeta.memoUtf8Bytes());

        accessor = SignedTxnAccessor.uncheckedFrom(xferWithAutoCreation);
        accessor.countAutoCreationsWith(aliasManager);
        assertEquals(1, accessor.getNumAutoCreations());

        accessor = SignedTxnAccessor.uncheckedFrom(xferWithAliasesNoAutoCreation);
        accessor.countAutoCreationsWith(aliasManager);
        assertEquals(0, accessor.getNumAutoCreations());
    }

    @Test
    void detectsCommonTokenBurnSubtypeFromGrpcSyntax() {
        final var op = TokenBurnTransactionBody.newBuilder().setAmount(1_234).build();
        final var txn = buildTransactionFrom(TransactionBody.newBuilder().setTokenBurn(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.getSubType());
    }

    @Test
    void usesUnmodifiableFormOfRationalizedSpanMap() {
        final var subject = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

        final var newMap = new HashMap<String, Object>();
        newMap.put("1", new Object());
        subject.setRationalizedSpanMap(newMap);

        final var rationalizedMap = subject.getSpanMap();
        assertThrows(UnsupportedOperationException.class, () -> rationalizedMap.put("2", 3));
    }

    @Test
    void canGetSetNumAutoCreations() {
        final var accessor = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());
        assertFalse(accessor.areAutoCreationsCounted());
        accessor.setNumAutoCreations(2);
        assertEquals(2, accessor.getNumAutoCreations());
        assertTrue(accessor.areAutoCreationsCounted());
        accessor.setNumAutoCreations(2);
    }

    @Test
    void canGetExpandedSigStatus() {
        final var accessor = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());
        assertNull(accessor.getExpandedSigStatus());
        accessor.setExpandedSigStatus(ResponseCodeEnum.ACCOUNT_DELETED);
        assertEquals(ResponseCodeEnum.ACCOUNT_DELETED, accessor.getExpandedSigStatus());
        accessor.setExpandedSigStatus(OK);
        assertEquals(OK, accessor.getExpandedSigStatus());
    }

    @Test
    void detectsUniqueTokenBurnSubtypeFromGrpcSyntax() {
        final var op =
                TokenBurnTransactionBody.newBuilder()
                        .addAllSerialNumbers(List.of(1L, 2L, 3L))
                        .build();
        final var txn = buildTransactionFrom(TransactionBody.newBuilder().setTokenBurn(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());
    }

    @Test
    void detectsCommonTokenMintSubtypeFromGrpcSyntax() {
        final var op = TokenMintTransactionBody.newBuilder().setAmount(1_234).build();
        final var txn = buildTransactionFrom(TransactionBody.newBuilder().setTokenMint(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.getSubType());
    }

    @Test
    void detectsUniqueTokenMintSubtypeFromGrpcSyntax() {
        final var op =
                TokenMintTransactionBody.newBuilder()
                        .addAllMetadata(List.of(ByteString.copyFromUtf8("STANDARD")))
                        .build();
        final var txn = buildTransactionFrom(TransactionBody.newBuilder().setTokenMint(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());
    }

    @Test
    void fetchesSubTypeAsExpected() throws InvalidProtocolBufferException {
        final var nftTransfers =
                TokenTransferList.newBuilder()
                        .setToken(anId)
                        .addNftTransfers(
                                NftTransfer.newBuilder()
                                        .setSenderAccountID(a)
                                        .setReceiverAccountID(b)
                                        .setSerialNumber(1))
                        .build();
        final var fungibleTokenXfers =
                TokenTransferList.newBuilder()
                        .setToken(anotherId)
                        .addAllTransfers(
                                List.of(adjustFrom(a, -50), adjustFrom(b, 25), adjustFrom(c, 25)))
                        .build();

        var txn = buildTokenTransferTxn(nftTransfers);
        var subject = SignedTxnAccessor.from(txn.toByteArray());
        assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, subject.availXferUsageMeta().getSubType());
        assertEquals(subject.availXferUsageMeta().getSubType(), subject.getSubType());

        // set customFee
        var xferUsageMeta = subject.availXferUsageMeta();
        xferUsageMeta.setCustomFeeHbarTransfers(1);
        assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, subject.getSubType());
        xferUsageMeta.setCustomFeeHbarTransfers(0);

        txn = buildTokenTransferTxn(fungibleTokenXfers);
        subject = SignedTxnAccessor.from(txn.toByteArray(), txn);
        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.availXferUsageMeta().getSubType());
        assertEquals(subject.availXferUsageMeta().getSubType(), subject.getSubType());

        // set customFee
        xferUsageMeta = subject.availXferUsageMeta();
        xferUsageMeta.setCustomFeeTokenTransfers(1);
        assertEquals(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, subject.getSubType());
        xferUsageMeta.setCustomFeeTokenTransfers(0);

        txn = buildDefaultCryptoCreateTxn();
        subject = SignedTxnAccessor.from(txn.toByteArray(), txn);
        assertEquals(SubType.DEFAULT, subject.getSubType());
    }

    @Test
    void understandsFullXferUsageIncTokens() {
        final var txn = buildTransactionFrom(tokenXfers());
        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        final var xferMeta = subject.availXferUsageMeta();

        assertEquals(1, xferMeta.getTokenMultiplier());
        assertEquals(3, xferMeta.getNumTokensInvolved());
        assertEquals(7, xferMeta.getNumFungibleTokenTransfers());
    }

    @Test
    void rejectsRequestForMetaIfNotAvail() {
        final var txn = buildDefaultCryptoCreateTxn();

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(SubType.DEFAULT, subject.getSubType());
        assertThrows(IllegalStateException.class, subject::availXferUsageMeta);
        assertThrows(IllegalStateException.class, subject::availSubmitUsageMeta);
    }

    @Test
    void understandsSubmitMessageMeta() {
        final var message = "And after, arranged it in a song";
        final var txnBody =
                TransactionBody.newBuilder()
                        .setConsensusSubmitMessage(
                                ConsensusSubmitMessageTransactionBody.newBuilder()
                                        .setMessage(ByteString.copyFromUtf8(message)))
                        .build();
        final var txn = buildTransactionFrom(txnBody);
        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        final var submitMeta = subject.availSubmitUsageMeta();

        assertEquals(message.length(), submitMeta.numMsgBytes());
    }

    @Test
    void parseNewTransactionCorrectly() throws Exception {
        final var transaction =
                RequestBuilder.getCryptoTransferRequest(
                        1234l,
                        0l,
                        0l,
                        3l,
                        0l,
                        0l,
                        100_000_000l,
                        Timestamp.getDefaultInstance(),
                        Duration.getDefaultInstance(),
                        false,
                        memo,
                        5678l,
                        -70000l,
                        5679l,
                        70000l);
        final var body = CommonUtils.extractTransactionBody(transaction);
        final var signedTransaction = TxnUtils.signedTransactionFrom(body, expectedMap);
        final var newTransaction = buildTransactionFrom(signedTransaction.toByteString());
        final var accessor = SignedTxnAccessor.uncheckedFrom(newTransaction);

        assertEquals(newTransaction, accessor.getSignedTxnWrapper());
        assertArrayEquals(newTransaction.toByteArray(), accessor.getSignedTxnWrapperBytes());
        assertEquals(body, accessor.getTxn());
        assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
        assertEquals(body.getTransactionID(), accessor.getTxnId());
        assertEquals(1234l, accessor.getPayer().getAccountNum());
        assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
        assertArrayEquals(
                CommonUtils.noThrowSha384HashOf(signedTransaction.toByteArray()),
                accessor.getHash());
        assertEquals(expectedMap, accessor.getSigMap());
        assertArrayEquals(memoUtf8Bytes, accessor.getMemoUtf8Bytes());
        assertFalse(accessor.memoHasZeroByte());
        assertEquals(
                FeeBuilder.getSignatureCount(accessor.getSignedTxnWrapper()),
                accessor.usageGiven(1).numSigs());
        assertEquals(
                FeeBuilder.getSignatureSize(accessor.getSignedTxnWrapper()),
                accessor.usageGiven(1).sigsSize());
        assertEquals(memo, accessor.getMemo());
    }

    @Test
    void registersNoneOnMalformedCreation() throws InvalidProtocolBufferException {
        final var xferWithTopLevelBodyBytes =
                RequestBuilder.getCryptoTransferRequest(
                        1234l,
                        0l,
                        0l,
                        3l,
                        0l,
                        0l,
                        100_000_000l,
                        Timestamp.getDefaultInstance(),
                        Duration.getDefaultInstance(),
                        false,
                        "test memo",
                        5678l,
                        -70000l,
                        5679l,
                        70000l);
        final var body = TransactionBody.parseFrom(xferWithTopLevelBodyBytes.getBodyBytes());
        final var confusedTxn = Transaction.parseFrom(body.toByteArray());

        final var confusedAccessor = SignedTxnAccessor.uncheckedFrom(confusedTxn);

        assertEquals(HederaFunctionality.NONE, confusedAccessor.getFunction());
    }

    @Test
    void throwsOnUnsupportedCallToGetScheduleRef() {
        final var subject = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

        assertDoesNotThrow(subject::getScheduleRef);
    }

    @Test
    void setsFeeScheduleUpdateMeta() {
        final var txn = signedFeeScheduleUpdateTxn();
        final var tokenOpsUsage = new TokenOpsUsage();
        final var expectedReprBytes = tokenOpsUsage.bytesNeededToRepr(fees());
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getFeeScheduleUpdateMeta(accessor);

        assertEquals(now, expandedMeta.effConsensusTime());
        assertEquals(expectedReprBytes, expandedMeta.numBytesInNewFeeScheduleRepr());
    }

    @Test
    void setTokenCreateUsageMetaWorks() {
        final var txn = signedTokenCreateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenCreateMeta(accessor);

        assertEquals(0, expandedMeta.getNftsTransfers());
        assertEquals(1, expandedMeta.getFungibleNumTransfers());
        assertEquals(1, expandedMeta.getNumTokens());
        assertEquals(1070, expandedMeta.getBaseSize());
        assertEquals(TOKEN_FUNGIBLE_COMMON, accessor.getSubType());
    }

    @Test
    void setTokenPauseUsageMetaWorks() {
        final var op =
                TokenPauseTransactionBody.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(123).build());
        final var txnBody =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(now)))
                        .setTokenPause(op)
                        .build();
        final var txn = buildTransactionFrom(txnBody);
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenPauseMeta(accessor);

        assertEquals(24, expandedMeta.getBpt());
    }

    @Test
    void setTokenUnpauseUsageMetaWorks() {
        final var op =
                TokenUnpauseTransactionBody.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(123).build());
        final var txnBody =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(now)))
                        .setTokenUnpause(op)
                        .build();
        final var txn = buildTransactionFrom(txnBody);
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenUnpauseMeta(accessor);

        assertEquals(24, expandedMeta.getBpt());
    }

    @Test
    void setCryptoCreateUsageMetaWorks() {
        final var txn = signedCryptoCreateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoCreateMeta(accessor);

        assertEquals(137, expandedMeta.getBaseSize());
        assertEquals(autoRenewPeriod, expandedMeta.getLifeTime());
        assertEquals(10, expandedMeta.getMaxAutomaticAssociations());
    }

    @Test
    void setCryptoUpdateUsageMetaWorks() {
        final var txn = signedCryptoUpdateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoUpdateMeta(accessor);

        assertEquals(100, expandedMeta.getKeyBytesUsed());
        assertEquals(197, expandedMeta.getMsgBytesUsed());
        assertEquals(now, expandedMeta.getEffectiveNow());
        assertEquals(now + autoRenewPeriod, expandedMeta.getExpiry());
        assertEquals(memo.getBytes().length, expandedMeta.getMemoSize());
        assertEquals(25, expandedMeta.getMaxAutomaticAssociations());
        assertTrue(expandedMeta.hasProxy());
    }

    @Test
    void setCryptoApproveUsageMetaWorks() {
        final var txn = signedCryptoApproveTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoApproveMeta(accessor);

        assertEquals(128, expandedMeta.getMsgBytesUsed());
        assertEquals(now, expandedMeta.getEffectiveNow());
    }

    @Test
    void setCryptoDeleteAllowanceUsageMetaWorks() {
        final var txn = signedCryptoDeleteAllowanceTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoDeleteAllowanceMeta(accessor);

        assertEquals(64, expandedMeta.getMsgBytesUsed());
        assertEquals(now, expandedMeta.getEffectiveNow());
    }

    @Test
    void setEthTxDataMetaMetaWorks() {
        final var txn = signedEthereumTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getEthTxDataMeta(accessor);

        assertEquals(98304L, expandedMeta.gasLimit());
    }

    @Test
    void recreatesOpEthTxDataEveryRequest() {
        final var accessor = SignedTxnAccessor.uncheckedFrom(signedEthereumTxn());

        final var firstData = accessor.opEthTxData();
        final var secondData = accessor.opEthTxData();
        assertNotSame(firstData, secondData);
    }

    @Test
    void setPrngMetaWorks() {
        final var op = UtilPrngTransactionBody.newBuilder().setRange(10).build();
        final var txn =
                buildTransactionFrom(
                        TransactionBody.newBuilder()
                                .setTransactionID(
                                        TransactionID.newBuilder()
                                                .setTransactionValidStart(
                                                        Timestamp.newBuilder().setSeconds(now)))
                                .setUtilPrng(op)
                                .build());
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getUtilPrngMeta(accessor);

        assertEquals(4, expandedMeta.getMsgBytesUsed());
    }

    @Test
    void getGasLimitWorksForCreate() {
        final var op = ContractCreateTransactionBody.newBuilder().setGas(123456789L).build();
        final var txn =
                buildTransactionFrom(
                        TransactionBody.newBuilder().setContractCreateInstance(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(123456789L, subject.getGasLimitForContractTx());
    }

    @Test
    void getGasLimitWorksForCall() {
        final var op = ContractCallTransactionBody.newBuilder().setGas(123456789L).build();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(123456789L, subject.getGasLimitForContractTx());
    }

    @Test
    void precheckSupportingFunctionsWork() throws InvalidProtocolBufferException {
        var falseOp = ContractCallTransactionBody.newBuilder().setGas(123456789L).build();
        var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(falseOp).build());

        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(false, accessor.supportsPrecheck());
        assertThrows(UnsupportedOperationException.class, () -> accessor.doPrecheck());

        var trueOp =
                TokenWipeAccountTransactionBody.newBuilder()
                        .setAccount(asAccount("0.0.1000"))
                        .build();
        txn = buildTransactionFrom(TransactionBody.newBuilder().setTokenWipe(trueOp).build());

        final var dynamicProperties = mock(GlobalDynamicProperties.class);
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        given(dynamicProperties.maxBatchSizeWipe()).willReturn(10);

        var subject = new TokenWipeAccessor(txn.toByteArray(), txn, dynamicProperties);

        assertEquals(true, subject.supportsPrecheck());
        assertEquals(INVALID_TOKEN_ID, subject.doPrecheck());
    }

    @Test
    void getGasLimitWorksForEthTxn() {
        final var gasLimit = 1234L;
        final var unsignedTx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.EIP1559,
                        new byte[0],
                        1,
                        null,
                        new byte[0],
                        new byte[0],
                        gasLimit,
                        new byte[0],
                        BigInteger.ZERO,
                        new byte[0],
                        null,
                        0,
                        null,
                        null,
                        null);
        final var ethTxData = EthTxSigs.signMessage(unsignedTx, TRUFFLE0_PRIVATE_ECDSA_KEY);
        final var op =
                EthereumTransactionBody.newBuilder()
                        .setEthereumData(ByteString.copyFrom(ethTxData.encodeTx()))
                        .build();
        final var txn =
                buildTransactionFrom(
                        TransactionBody.newBuilder().setEthereumTransaction(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(gasLimit, subject.getGasLimitForContractTx());
    }

    @Test
    void getGasLimitReturnsZeroByDefault() {
        final var op = TokenCreateTransactionBody.getDefaultInstance();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setTokenCreation(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(0L, subject.getGasLimitForContractTx());
    }

    @Test
    void markThrottleExemptWorks() {
        final var op = ContractCallTransactionBody.newBuilder().build();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        subject.setPayer(asAccount("0.0.2222"));

        assertFalse(subject.throttleExempt());
        subject.markThrottleExempt();
        assertTrue(subject.throttleExempt());
    }

    @Test
    void throttleExemptWorksWithExemptPayer() {
        final var op = ContractCallTransactionBody.newBuilder().build();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        subject.setPayer(asAccount("0.0.2"));

        assertTrue(subject.throttleExempt());
    }

    @Test
    void throttleExemptWorksWithNonExemptPayer() {
        final var op = ContractCallTransactionBody.newBuilder().build();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        subject.setPayer(asAccount("0.0.2222"));

        assertFalse(subject.throttleExempt());
    }

    @Test
    void throttleExemptWorksWithNullPayer() {
        final var op = ContractCallTransactionBody.newBuilder().build();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        subject.setPayer(null);

        assertFalse(subject.throttleExempt());
    }

    @Test
    void markCongestionExemptWorks() {
        final var op = ContractCallTransactionBody.newBuilder().build();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertFalse(subject.congestionExempt());
        subject.markCongestionExempt();
        assertTrue(subject.congestionExempt());
    }

    @Test
    void toLoggableStringWorks() throws InvalidProtocolBufferException {
        TransactionBody someTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
                        .setMemo("Hi!")
                        .setTransactionFee(10L)
                        .setConsensusSubmitMessage(
                                ConsensusSubmitMessageTransactionBody.newBuilder()
                                        .setTopicID(asTopic("0.0.10"))
                                        .build())
                        .build();
        final ByteString canonicalSig =
                ByteString.copyFromUtf8(
                        "0123456789012345678901234567890123456789012345678901234567890123");
        final SignatureMap onePairSigMap =
                SignatureMap.newBuilder()
                        .addSigPair(
                                SignaturePair.newBuilder()
                                        .setPubKeyPrefix(ByteString.copyFromUtf8("a"))
                                        .setEd25519(canonicalSig))
                        .build();
        Transaction signedTxnWithBody =
                Transaction.newBuilder()
                        .setBodyBytes(someTxn.toByteString())
                        .setSigMap(onePairSigMap)
                        .build();
        final var platformTxn = new SwirldTransaction(signedTxnWithBody.toByteArray());

        // when:
        SignedTxnAccessor subject = SignedTxnAccessor.from(platformTxn.getContents());

        final var expectedString =
                "SignedTxnAccessor{sigMapSize=71, numSigPairs=1, numAutoCreations=-1, hash=[111,"
                    + " -123, -70, 79, 75, -80, -114, -49, 88, -76, -82, -23, 43, 103, -21, 52,"
                    + " -31, -60, 98, -55, -26, -18, -101, -108, -51, 24, 49, 72, 18, -69, 21, -84,"
                    + " -68, -118, 31, -53, 91, -61, -71, -56, 100, -52, -104, 87, -85, -33, -73,"
                    + " -124], txnBytes=[10, 4, 18, 2, 24, 2, 24, 10, 50, 3, 72, 105, 33, -38, 1,"
                    + " 4, 10, 2, 24, 10], utf8MemoBytes=[72, 105, 33], memo=Hi!,"
                    + " memoHasZeroByte=false, signedTxnWrapper=sigMap {\n"
                    + "  sigPair {\n"
                    + "    pubKeyPrefix: \"a\"\n"
                    + "    ed25519:"
                    + " \"0123456789012345678901234567890123456789012345678901234567890123\"\n"
                    + "  }\n"
                    + "}\n"
                    + "bodyBytes: \"\\n"
                    + "\\004\\022\\002\\030\\002\\030\\n"
                    + "2\\003Hi!\\332\\001\\004\\n"
                    + "\\002\\030\\n"
                    + "\"\n"
                    + ", hash=[111, -123, -70, 79, 75, -80, -114, -49, 88, -76, -82, -23, 43, 103,"
                    + " -21, 52, -31, -60, 98, -55, -26, -18, -101, -108, -51, 24, 49, 72, 18, -69,"
                    + " 21, -84, -68, -118, 31, -53, 91, -61, -71, -56, 100, -52, -104, 87, -85,"
                    + " -33, -73, -124], txnBytes=[10, 4, 18, 2, 24, 2, 24, 10, 50, 3, 72, 105, 33,"
                    + " -38, 1, 4, 10, 2, 24, 10], sigMap=sigPair {\n"
                    + "  pubKeyPrefix: \"a\"\n"
                    + "  ed25519:"
                    + " \"0123456789012345678901234567890123456789012345678901234567890123\"\n"
                    + "}\n"
                    + ", txnId=accountID {\n"
                    + "  accountNum: 2\n"
                    + "}\n"
                    + ", txn=transactionID {\n"
                    + "  accountID {\n"
                    + "    accountNum: 2\n"
                    + "  }\n"
                    + "}\n"
                    + "transactionFee: 10\n"
                    + "memo: \"Hi!\"\n"
                    + "consensusSubmitMessage {\n"
                    + "  topicID {\n"
                    + "    topicNum: 10\n"
                    + "  }\n"
                    + "}\n"
                    + ", submitMessageMeta=SubmitMessageMeta[numMsgBytes=0], xferUsageMeta=null,"
                    + " txnUsageMeta=BaseTransactionMeta[memoUtf8Bytes=3, numExplicitTransfers=0],"
                    + " function=ConsensusSubmitMessage,"
                    + " pubKeyToSigBytes=PojoSigMapPubKeyToSigBytes{pojoSigMap=PojoSigMap{keyTypes=[ED25519],"
                    + " rawMap=[[[97], [48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52,"
                    + " 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51,"
                    + " 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50,"
                    + " 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51]]]}, used=[false]},"
                    + " payer=accountNum: 2\n"
                    + ", scheduleRef=null, view=null}";

        assertEquals(expectedString, subject.toLoggableString());
    }

    @Test
    void setterAndGetterForStateViewWorks() {
        final var op = ContractCallTransactionBody.newBuilder().build();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);
        final var stateView = mock(StateView.class);

        subject.setStateView(stateView);

        assertEquals(stateView, subject.getStateView());
    }

    @Test
    void looksUpAliases() {
        final var stateView = mock(StateView.class);
        final var alias = ByteString.copyFromUtf8("someString");
        final Map<ByteString, EntityNum> accountsMap = new HashMap<>();
        accountsMap.put(alias, EntityNum.fromLong(10L));
        given(stateView.aliases()).willReturn(accountsMap);

        final var op = ContractCallTransactionBody.newBuilder().build();
        final var txn =
                buildTransactionFrom(TransactionBody.newBuilder().setContractCall(op).build());
        final var subject = SignedTxnAccessor.uncheckedFrom(txn);
        subject.setStateView(stateView);

        assertEquals(EntityNum.fromLong(10L), subject.lookUpAlias(alias));
        assertEquals(EntityNum.fromLong(10L), subject.unaliased(asAliasAccount(alias)));
    }

    private Transaction signedCryptoCreateTxn() {
        return buildTransactionFrom(cryptoCreateOp());
    }

    private Transaction signedCryptoUpdateTxn() {
        return buildTransactionFrom(cryptoUpdateOp());
    }

    private Transaction signedCryptoApproveTxn() {
        return buildTransactionFrom(cryptoApproveOp());
    }

    private Transaction signedCryptoDeleteAllowanceTxn() {
        return buildTransactionFrom(cryptoDeleteAllowanceOp());
    }

    private Transaction signedEthereumTxn() {
        return buildTransactionFrom(TxnUtils.ethereumTransactionOp());
    }

    private TransactionBody cryptoCreateOp() {
        final var op =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(memo)
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setKey(adminKey)
                        .setMaxAutomaticTokenAssociations(10);
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoCreateAccount(op)
                .build();
    }

    private TransactionBody cryptoUpdateOp() {
        final var op =
                CryptoUpdateTransactionBody.newBuilder()
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(now + autoRenewPeriod))
                        .setProxyAccountID(autoRenewAccount)
                        .setMemo(StringValue.newBuilder().setValue(memo))
                        .setMaxAutomaticTokenAssociations(Int32Value.of(25))
                        .setKey(adminKey);
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoUpdateAccount(op)
                .build();
    }

    private TransactionBody cryptoApproveOp() {
        final var op =
                CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(List.of(cryptoAllowance1))
                        .addAllTokenAllowances(List.of(tokenAllowance1))
                        .addAllNftAllowances(List.of(nftAllowance1))
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoApproveAllowance(op)
                .build();
    }

    private TransactionBody cryptoDeleteAllowanceOp() {
        final var op =
                CryptoDeleteAllowanceTransactionBody.newBuilder()
                        .addAllNftAllowances(List.of(nftRemoveAllowance))
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoDeleteAllowance(op)
                .build();
    }

    private Transaction signedFeeScheduleUpdateTxn() {
        return buildTransactionFrom(feeScheduleUpdateTxn());
    }

    private TransactionBody feeScheduleUpdateTxn() {
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setTokenFeeScheduleUpdate(
                        TokenFeeScheduleUpdateTransactionBody.newBuilder().addAllCustomFees(fees()))
                .build();
    }

    private List<CustomFee> fees() {
        final var collector = new EntityId(1, 2, 3);
        final var aDenom = new EntityId(2, 3, 4);
        final var bDenom = new EntityId(3, 4, 5);

        return List.of(
                        fixedFee(1, null, collector, false),
                        fixedFee(2, aDenom, collector, false),
                        fixedFee(2, bDenom, collector, false),
                        fractionalFee(1, 2, 1, 2, false, collector, false),
                        fractionalFee(1, 3, 1, 2, false, collector, false),
                        fractionalFee(1, 4, 1, 2, false, collector, false))
                .stream()
                .map(FcCustomFee::asGrpc)
                .collect(toList());
    }

    private Transaction buildTokenTransferTxn(final TokenTransferList tokenTransferList) {
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(tokenTransferList)
                        .build();
        final var txnBody =
                TransactionBody.newBuilder()
                        .setMemo(memo)
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(now)))
                        .setCryptoTransfer(op)
                        .build();

        return buildTransactionFrom(txnBody);
    }

    private Transaction buildDefaultCryptoCreateTxn() {
        final var txnBody =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
                        .build();

        return buildTransactionFrom(txnBody);
    }

    private TransactionBody tokenXfers() {
        final var hbarAdjusts =
                TransferList.newBuilder()
                        .addAccountAmounts(adjustFrom(a, -100))
                        .addAccountAmounts(adjustFrom(b, 50))
                        .addAccountAmounts(adjustFrom(c, 50))
                        .build();
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(hbarAdjusts)
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(anotherId)
                                        .addAllTransfers(
                                                List.of(
                                                        adjustFrom(a, -50),
                                                        adjustFrom(b, 25),
                                                        adjustFrom(c, 25))))
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(anId)
                                        .addAllTransfers(
                                                List.of(adjustFrom(b, -100), adjustFrom(c, 100))))
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(yetAnotherId)
                                        .addAllTransfers(
                                                List.of(adjustFrom(a, -15), adjustFrom(b, 15))))
                        .build();

        return TransactionBody.newBuilder()
                .setMemo(memo)
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoTransfer(op)
                .build();
    }

    private AccountAmount adjustFrom(final AccountID account, final long amount) {
        return AccountAmount.newBuilder().setAmount(amount).setAccountID(account).build();
    }

    private Transaction signedTokenCreateTxn() {
        return buildTransactionFrom(givenAutoRenewBasedOp());
    }

    private TransactionBody givenAutoRenewBasedOp() {
        final var op =
                TokenCreateTransactionBody.newBuilder()
                        .setAutoRenewAccount(autoRenewAccount)
                        .setMemo(memo)
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setSymbol(symbol)
                        .setName(name)
                        .setKycKey(kycKey)
                        .setAdminKey(adminKey)
                        .setFreezeKey(freezeKey)
                        .setSupplyKey(supplyKey)
                        .setWipeKey(wipeKey)
                        .setInitialSupply(1);
        final var txn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(now)))
                        .setTokenCreation(op)
                        .build();
        return txn;
    }

    private static final Key kycKey = KeyUtils.A_COMPLEX_KEY;
    private static final Key adminKey = KeyUtils.A_THRESHOLD_KEY;
    private static final Key freezeKey = KeyUtils.A_KEY_LIST;
    private static final Key supplyKey = KeyUtils.B_COMPLEX_KEY;
    private static final Key wipeKey = KeyUtils.C_COMPLEX_KEY;
    private static final long autoRenewPeriod = 1_234_567L;
    private static final String symbol = "ABCDEFGH";
    private static final String name = "WhyWhyWHy";
    private static final AccountID autoRenewAccount = IdUtils.asAccount("0.0.75231");

    private static final long now = 1_234_567L;
    private static final AccountID a = asAccount("1.2.3");
    private static final AccountID b = asAccount("2.3.4");
    private static final AccountID c = asAccount("3.4.5");
    private static final TokenID anId = IdUtils.asToken("0.0.75231");
    private static final TokenID anotherId = IdUtils.asToken("0.0.75232");
    private static final TokenID yetAnotherId = IdUtils.asToken("0.0.75233");
}
