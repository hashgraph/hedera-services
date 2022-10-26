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
package com.hedera.services.utils;

import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.services.throttling.MapAccessType.ACCOUNTS_GET;
import static com.hedera.services.throttling.MapAccessType.STORAGE_REMOVE;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE0_PRIVATE_ECDSA_KEY;
import static com.hedera.services.utils.MiscUtils.QUERY_FUNCTIONS;
import static com.hedera.services.utils.MiscUtils.SCHEDULE_CREATE_METRIC;
import static com.hedera.services.utils.MiscUtils.SCHEDULE_DELETE_METRIC;
import static com.hedera.services.utils.MiscUtils.SCHEDULE_SIGN_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_ASSOCIATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_BURN_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_CREATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_DELETE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_DISSOCIATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_FEE_SCHEDULE_UPDATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_FREEZE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_GRANT_KYC_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_MINT_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_PAUSE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_REVOKE_KYC_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_UNFREEZE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_UNPAUSE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_UPDATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_WIPE_ACCOUNT_METRIC;
import static com.hedera.services.utils.MiscUtils.UTIL_PRNG_METRIC;
import static com.hedera.services.utils.MiscUtils.activeHeaderFrom;
import static com.hedera.services.utils.MiscUtils.asOrdinary;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hedera.services.utils.MiscUtils.baseStatNameOf;
import static com.hedera.services.utils.MiscUtils.canonicalDiffRepr;
import static com.hedera.services.utils.MiscUtils.canonicalRepr;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hedera.services.utils.MiscUtils.functionalityOfQuery;
import static com.hedera.services.utils.MiscUtils.getTxnStat;
import static com.hedera.services.utils.MiscUtils.isGasThrottled;
import static com.hedera.services.utils.MiscUtils.isSchedulable;
import static com.hedera.services.utils.MiscUtils.nonNegativeNanosOffset;
import static com.hedera.services.utils.MiscUtils.perm64;
import static com.hedera.services.utils.MiscUtils.readableNftTransferList;
import static com.hedera.services.utils.MiscUtils.readableProperty;
import static com.hedera.services.utils.MiscUtils.readableTransferList;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hedera.test.utils.TxnUtils.withNftAdjustments;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetAccountDetails;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetByKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.grpc.controllers.ConsensusController;
import com.hedera.services.grpc.controllers.ContractController;
import com.hedera.services.grpc.controllers.CryptoController;
import com.hedera.services.grpc.controllers.FileController;
import com.hedera.services.grpc.controllers.FreezeController;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.stats.ServicesStatsConfig;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.KeyedMerkleLong;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class MiscUtilsTest {
    @Test
    void canGetListOfAccessTypes() {
        final var expected = List.of(ACCOUNTS_GET, ACCOUNTS_GET, ACCOUNTS_GET, STORAGE_REMOVE);
        final var actual =
                MiscUtils.csvList(
                        "ACCOUNTS_GET,ACCOUNTS_GET,ACCOUNTS_GET,STORAGE_REMOVE",
                        MapAccessType::valueOf);
        assertEquals(expected, actual);
    }

    @Test
    void canRunWithLoggedDuration() {
        final var mockLogger = mock(Logger.class);
        final var desc = "nothing";
        MiscUtils.withLoggedDuration(() -> {}, mockLogger, desc);
        verify(mockLogger).info("Starting {}", desc);
        verify(mockLogger).info("Done with {} in {}ms", desc, 0L);
    }

    @Test
    void canUnpackTime() {
        final long seconds = 1_234_567L;
        final int nanos = 890;
        final var packedTime = BitPackUtils.packedTime(seconds, nanos);
        final var expected = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertEquals(expected, MiscUtils.asTimestamp(packedTime));
    }

    @Test
    void forEachDropInWorksAsExpected() {
        // setup:
        final MerkleMap<FcLong, KeyedMerkleLong<FcLong>> testMm = new MerkleMap<>();
        @SuppressWarnings("unchecked")
        final BiConsumer<FcLong, KeyedMerkleLong<FcLong>> mockConsumer =
                BDDMockito.mock(BiConsumer.class);
        // and:
        final var key1 = new FcLong(1L);
        final var key2 = new FcLong(2L);

        // given:
        putValue(1L, testMm);
        putValue(2L, testMm);

        // when:
        MiscUtils.forEach(testMm, mockConsumer);

        // then:
        verify(mockConsumer).accept(key1, new KeyedMerkleLong<>(key1, 1L));
        verify(mockConsumer).accept(key2, new KeyedMerkleLong<>(key2, 2L));
    }

    private void putValue(long value, MerkleMap<FcLong, KeyedMerkleLong<FcLong>> mm) {
        final var newValue = new KeyedMerkleLong(value);
        mm.put(new FcLong(value), newValue);
    }

    @Test
    void retrievesExpectedStatNames() {
        assertEquals(
                ContractController.CALL_CONTRACT_METRIC, MiscUtils.baseStatNameOf(ContractCall));
        assertEquals(GetByKey.toString(), baseStatNameOf(GetByKey));
    }

    @Test
    void getsNodeAccounts() {
        final var address = mock(Address.class);
        given(address.getMemo()).willReturn("0.0.3");
        final var book = mock(AddressBook.class);
        given(book.getSize()).willReturn(1);
        given(book.getAddress(0)).willReturn(address);

        final var accounts = MiscUtils.getNodeAccounts(book);

        assertEquals(Set.of(IdUtils.asAccount("0.0.3")), accounts);
    }

    @Test
    void asFcKeyUncheckedTranslatesExceptions() {
        final var key = Key.getDefaultInstance();
        assertThrows(IllegalArgumentException.class, () -> MiscUtils.asFcKeyUnchecked(key));
    }

    @Test
    void asFcKeyReturnsEmptyOnUnparseableKey() {
        final var key = Key.getDefaultInstance();
        assertTrue(asUsableFcKey(key).isEmpty());
    }

    @Test
    void asFcKeyReturnsEmptyOnEmptyKey() {
        assertTrue(
                asUsableFcKey(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build())
                        .isEmpty());
    }

    @Test
    void asFcKeyReturnsEmptyOnInvalidKey() {
        assertTrue(
                asUsableFcKey(
                                Key.newBuilder()
                                        .setEd25519(ByteString.copyFrom("1".getBytes()))
                                        .build())
                        .isEmpty());
    }

    @Test
    void asFcKeyReturnsExpected() {
        final var key =
                Key.newBuilder()
                        .setEd25519(
                                ByteString.copyFrom("01234567890123456789012345678901".getBytes()))
                        .build();

        assertTrue(
                JKey.equalUpToDecodability(
                        asUsableFcKey(key).get(), MiscUtils.asFcKeyUnchecked(key)));
    }

    @Test
    void asFcKeyUncheckedWorks() {
        final var fakePrivateKey = "not-really-a-key!".getBytes();
        final var matchingKey =
                Key.newBuilder().setEd25519(ByteString.copyFrom(fakePrivateKey)).build();

        final var expected = new JEd25519Key(fakePrivateKey);

        assertTrue(JKey.equalUpToDecodability(expected, MiscUtils.asFcKeyUnchecked(matchingKey)));
    }

    @Test
    void getsCanonicalDiff() {
        final var a = asAccount("1.2.3");
        final var b = asAccount("2.3.4");
        final var c = asAccount("3.4.5");
        final var canonicalA = List.of(aa(a, 300), aa(b, -500), aa(c, 200));
        final var canonicalB = List.of(aa(a, 150), aa(b, 50), aa(c, -200));

        final var canonicalDiff = canonicalDiffRepr(canonicalA, canonicalB);

        assertThat(canonicalDiff, contains(aa(a, 150), aa(b, -550), aa(c, 400)));
    }

    @Test
    void getsCanonicalRepr() {
        final var a = asAccount("1.2.3");
        final var b = asAccount("2.3.4");
        final var c = asAccount("3.4.5");
        final var adhocRepr = List.of(aa(a, 500), aa(c, 100), aa(a, -500), aa(b, -500), aa(c, 400));

        final var canonicalRepr = canonicalRepr(adhocRepr);

        assertThat(canonicalRepr, contains(aa(b, -500), aa(c, 500)));
    }

    private AccountAmount aa(final AccountID who, final long what) {
        return AccountAmount.newBuilder().setAccountID(who).setAmount(what).build();
    }

    @Test
    void prettyPrintsTransferList() {
        final var transfers =
                withAdjustments(
                        asAccount("0.1.2"), 500L,
                        asAccount("1.0.2"), -250L,
                        asAccount("1.2.0"), Long.MIN_VALUE);

        final var s = readableTransferList(transfers);

        assertEquals("[0.1.2 <- +500, 1.0.2 -> -250, 1.2.0 -> -9223372036854775808]", s);
    }

    @Test
    void prettyPrintsNFTTransferList() {
        final var transfers =
                withNftAdjustments(
                        asToken("0.2.3"),
                        asAccount("0.1.2"),
                        asAccount("0.1.3"),
                        1L,
                        asAccount("1.0.4"),
                        asAccount("1.0.5"),
                        2L,
                        asAccount("1.2.6"),
                        asAccount("1.0.7"),
                        3L);

        final var s = readableNftTransferList(transfers);

        assertEquals("[1 0.1.2 0.1.3, 2 1.0.4 1.0.5, 3 1.2.6 1.0.7]", s);
    }

    @Test
    void prettyPrintsExpirableTransactionRecord() {
        final var grpcRecord1 = recordWith(SUCCESS);
        final var grpcRecord2 = recordWith(INVALID_ACCOUNT_ID);
        final var record1 = fromGprc(grpcRecord1);
        final var record2 = fromGprc(grpcRecord2);
        final FCQueue<ExpirableTxnRecord> recordsFCQ = new FCQueue<>();
        recordsFCQ.add(record1);
        recordsFCQ.add(record2);

        final var expected = List.of(grpcRecord1, grpcRecord2).toString();
        assertEquals(expected, readableProperty(recordsFCQ));

        final var records = List.of(record1, record2);
        assertEquals(records.toString(), readableProperty(records));
    }

    private TransactionRecord recordWith(final ResponseCodeEnum code) {
        return TransactionRecord.newBuilder()
                .setReceipt(TransactionReceipt.newBuilder().setStatus(code))
                .setTransactionID(TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
                .setConsensusTimestamp(Timestamp.getDefaultInstance())
                .build();
    }

    @Test
    void throwsOnUnexpectedFunctionality() {
        assertThrows(
                UnknownHederaFunctionality.class,
                () -> {
                    functionOf(TransactionBody.getDefaultInstance());
                });
    }

    @Test
    void convertsMetadata() {
        final long fee = 123L;
        final String memo = "Hi there!";
        final var scheduledTxn =
                SchedulableTransactionBody.newBuilder()
                        .setTransactionFee(fee)
                        .setMemo(memo)
                        .build();
        final var account = AccountID.newBuilder().setAccountNum(1).build();
        final var start = Timestamp.newBuilder().setSeconds(1).setNanos(2).build();

        final var ordinaryTxn =
                asOrdinary(
                        scheduledTxn,
                        TransactionID.newBuilder()
                                .setAccountID(account)
                                .setTransactionValidStart(start)
                                .setNonce(2)
                                .build());

        assertEquals(memo, ordinaryTxn.getMemo());
        assertEquals(fee, ordinaryTxn.getTransactionFee());
        assertEquals(account, ordinaryTxn.getTransactionID().getAccountID());
        assertEquals(start, ordinaryTxn.getTransactionID().getTransactionValidStart());
        assertEquals(2, ordinaryTxn.getTransactionID().getNonce());
        assertTrue(ordinaryTxn.getTransactionID().getScheduled());
    }

    @Test
    void getExpectedOrdinaryTxn() {
        final Map<
                        String,
                        BodySetter<
                                ? extends GeneratedMessageV3, SchedulableTransactionBody.Builder>>
                setters =
                        new HashMap<>() {
                            {
                                put(
                                        "ContractCall",
                                        new BodySetter<>(ContractCallTransactionBody.class));
                                put(
                                        "ContractCreateInstance",
                                        new BodySetter<>(ContractCreateTransactionBody.class));
                                put(
                                        "ContractUpdateInstance",
                                        new BodySetter<>(ContractUpdateTransactionBody.class));
                                put(
                                        "ContractDeleteInstance",
                                        new BodySetter<>(ContractDeleteTransactionBody.class));
                                put(
                                        "CryptoCreateAccount",
                                        new BodySetter<>(CryptoCreateTransactionBody.class));
                                put(
                                        "CryptoDelete",
                                        new BodySetter<>(CryptoDeleteTransactionBody.class));
                                put(
                                        "CryptoTransfer",
                                        new BodySetter<>(CryptoTransferTransactionBody.class));
                                put(
                                        "CryptoUpdateAccount",
                                        new BodySetter<>(CryptoUpdateTransactionBody.class));
                                put(
                                        "FileAppend",
                                        new BodySetter<>(FileAppendTransactionBody.class));
                                put(
                                        "FileCreate",
                                        new BodySetter<>(FileCreateTransactionBody.class));
                                put(
                                        "FileDelete",
                                        new BodySetter<>(FileDeleteTransactionBody.class));
                                put(
                                        "FileUpdate",
                                        new BodySetter<>(FileUpdateTransactionBody.class));
                                put(
                                        "SystemDelete",
                                        new BodySetter<>(SystemDeleteTransactionBody.class));
                                put(
                                        "SystemUndelete",
                                        new BodySetter<>(SystemUndeleteTransactionBody.class));
                                put("Freeze", new BodySetter<>(FreezeTransactionBody.class));
                                put(
                                        "ConsensusCreateTopic",
                                        new BodySetter<>(
                                                ConsensusCreateTopicTransactionBody.class));
                                put(
                                        "ConsensusUpdateTopic",
                                        new BodySetter<>(
                                                ConsensusUpdateTopicTransactionBody.class));
                                put(
                                        "ConsensusDeleteTopic",
                                        new BodySetter<>(
                                                ConsensusDeleteTopicTransactionBody.class));
                                put(
                                        "ConsensusSubmitMessage",
                                        new BodySetter<>(
                                                ConsensusSubmitMessageTransactionBody.class));
                                put(
                                        "TokenCreation",
                                        new BodySetter<>(TokenCreateTransactionBody.class));
                                put(
                                        "TokenFreeze",
                                        new BodySetter<>(TokenFreezeAccountTransactionBody.class));
                                put(
                                        "TokenUnfreeze",
                                        new BodySetter<>(
                                                TokenUnfreezeAccountTransactionBody.class));
                                put(
                                        "TokenGrantKyc",
                                        new BodySetter<>(TokenGrantKycTransactionBody.class));
                                put(
                                        "TokenRevokeKyc",
                                        new BodySetter<>(TokenRevokeKycTransactionBody.class));
                                put(
                                        "TokenDeletion",
                                        new BodySetter<>(TokenDeleteTransactionBody.class));
                                put(
                                        "TokenUpdate",
                                        new BodySetter<>(TokenUpdateTransactionBody.class));
                                put("TokenMint", new BodySetter<>(TokenMintTransactionBody.class));
                                put("TokenBurn", new BodySetter<>(TokenBurnTransactionBody.class));
                                put(
                                        "TokenWipe",
                                        new BodySetter<>(TokenWipeAccountTransactionBody.class));
                                put(
                                        "TokenAssociate",
                                        new BodySetter<>(TokenAssociateTransactionBody.class));
                                put(
                                        "TokenDissociate",
                                        new BodySetter<>(TokenDissociateTransactionBody.class));
                                put(
                                        "TokenUnpause",
                                        new BodySetter<>(TokenUnpauseTransactionBody.class));
                                put(
                                        "TokenPause",
                                        new BodySetter<>(TokenPauseTransactionBody.class));
                                put(
                                        "ScheduleDelete",
                                        new BodySetter<>(ScheduleDeleteTransactionBody.class));
                                put("UtilPrng", new BodySetter<>(UtilPrngTransactionBody.class));
                                put(
                                        "CryptoApproveAllowance",
                                        new BodySetter<>(
                                                CryptoApproveAllowanceTransactionBody.class));
                            }
                        };

        setters.forEach(
                (bodyType, setter) -> {
                    final var txn = SchedulableTransactionBody.newBuilder();
                    setter.setDefaultInstanceFor(txn);
                    final var ordinary =
                            asOrdinary(txn.build(), TransactionID.getDefaultInstance());
                    assertTrue(
                            txnBodyHas(ordinary, bodyType),
                            ordinary + " doesn't have " + bodyType + " as expected!");
                });
    }

    @Test
    void isSchedulableWorksAsExpected() {
        for (var fun : HederaFunctionality.values()) {
            if (QUERY_FUNCTIONS.contains(fun) || fun == ScheduleCreate || fun == ScheduleSign) {
                assertFalse(isSchedulable(fun));
            } else {
                assertTrue(isSchedulable(fun));
            }
        }
        assertFalse(isSchedulable(null));
    }

    private boolean txnBodyHas(final TransactionBody txn, final String bodyType) {
        try {
            final var method =
                    Stream.of(TransactionBody.class.getDeclaredMethods())
                            .filter(m -> m.getName().equals("has" + bodyType))
                            .findFirst()
                            .get();
            return (boolean) method.invoke(txn);
        } catch (Exception ignore) {
        }
        return false;
    }

    @Test
    void getExpectedTxnStat() {
        final Map<String, BodySetter<? extends GeneratedMessageV3, TransactionBody.Builder>>
                setters =
                        new HashMap<>() {
                            {
                                put(
                                        CryptoController.CRYPTO_CREATE_METRIC,
                                        new BodySetter<>(CryptoCreateTransactionBody.class));
                                put(
                                        CryptoController.CRYPTO_UPDATE_METRIC,
                                        new BodySetter<>(CryptoUpdateTransactionBody.class));
                                put(
                                        CryptoController.CRYPTO_TRANSFER_METRIC,
                                        new BodySetter<>(CryptoTransferTransactionBody.class));
                                put(
                                        CryptoController.CRYPTO_DELETE_METRIC,
                                        new BodySetter<>(CryptoDeleteTransactionBody.class));
                                put(
                                        ContractController.CREATE_CONTRACT_METRIC,
                                        new BodySetter<>(ContractCreateTransactionBody.class));
                                put(
                                        ContractController.CALL_CONTRACT_METRIC,
                                        new BodySetter<>(ContractCallTransactionBody.class));
                                put(
                                        ContractController.UPDATE_CONTRACT_METRIC,
                                        new BodySetter<>(ContractUpdateTransactionBody.class));
                                put(
                                        ContractController.DELETE_CONTRACT_METRIC,
                                        new BodySetter<>(ContractDeleteTransactionBody.class));
                                put(
                                        CryptoController.ADD_LIVE_HASH_METRIC,
                                        new BodySetter<>(CryptoAddLiveHashTransactionBody.class));
                                put(
                                        CryptoController.DELETE_LIVE_HASH_METRIC,
                                        new BodySetter<>(
                                                CryptoDeleteLiveHashTransactionBody.class));
                                put(
                                        FileController.CREATE_FILE_METRIC,
                                        new BodySetter<>(FileCreateTransactionBody.class));
                                put(
                                        FileController.FILE_APPEND_METRIC,
                                        new BodySetter<>(FileAppendTransactionBody.class));
                                put(
                                        FileController.UPDATE_FILE_METRIC,
                                        new BodySetter<>(FileUpdateTransactionBody.class));
                                put(
                                        FileController.DELETE_FILE_METRIC,
                                        new BodySetter<>(FileDeleteTransactionBody.class));
                                put(
                                        FreezeController.FREEZE_METRIC,
                                        new BodySetter<>(FreezeTransactionBody.class));
                                put(
                                        ServicesStatsConfig.SYSTEM_DELETE_METRIC,
                                        new BodySetter<>(SystemDeleteTransactionBody.class));
                                put(
                                        ServicesStatsConfig.SYSTEM_UNDELETE_METRIC,
                                        new BodySetter<>(SystemUndeleteTransactionBody.class));
                                put(
                                        ConsensusController.CREATE_TOPIC_METRIC,
                                        new BodySetter<>(
                                                ConsensusCreateTopicTransactionBody.class));
                                put(
                                        ConsensusController.UPDATE_TOPIC_METRIC,
                                        new BodySetter<>(
                                                ConsensusUpdateTopicTransactionBody.class));
                                put(
                                        ConsensusController.DELETE_TOPIC_METRIC,
                                        new BodySetter<>(
                                                ConsensusDeleteTopicTransactionBody.class));
                                put(
                                        ConsensusController.SUBMIT_MESSAGE_METRIC,
                                        new BodySetter<>(
                                                ConsensusSubmitMessageTransactionBody.class));
                                put(
                                        TOKEN_CREATE_METRIC,
                                        new BodySetter<>(TokenCreateTransactionBody.class));
                                put(
                                        TOKEN_FREEZE_METRIC,
                                        new BodySetter<>(TokenFreezeAccountTransactionBody.class));
                                put(
                                        TOKEN_UNFREEZE_METRIC,
                                        new BodySetter<>(
                                                TokenUnfreezeAccountTransactionBody.class));
                                put(
                                        TOKEN_GRANT_KYC_METRIC,
                                        new BodySetter<>(TokenGrantKycTransactionBody.class));
                                put(
                                        TOKEN_REVOKE_KYC_METRIC,
                                        new BodySetter<>(TokenRevokeKycTransactionBody.class));
                                put(
                                        TOKEN_DELETE_METRIC,
                                        new BodySetter<>(TokenDeleteTransactionBody.class));
                                put(
                                        TOKEN_UPDATE_METRIC,
                                        new BodySetter<>(TokenUpdateTransactionBody.class));
                                put(
                                        TOKEN_MINT_METRIC,
                                        new BodySetter<>(TokenMintTransactionBody.class));
                                put(
                                        TOKEN_BURN_METRIC,
                                        new BodySetter<>(TokenBurnTransactionBody.class));
                                put(
                                        TOKEN_WIPE_ACCOUNT_METRIC,
                                        new BodySetter<>(TokenWipeAccountTransactionBody.class));
                                put(
                                        TOKEN_ASSOCIATE_METRIC,
                                        new BodySetter<>(TokenAssociateTransactionBody.class));
                                put(
                                        TOKEN_DISSOCIATE_METRIC,
                                        new BodySetter<>(TokenDissociateTransactionBody.class));
                                put(
                                        TOKEN_FEE_SCHEDULE_UPDATE_METRIC,
                                        new BodySetter<>(
                                                TokenFeeScheduleUpdateTransactionBody.class));
                                put(
                                        TOKEN_UNPAUSE_METRIC,
                                        new BodySetter<>(TokenUnpauseTransactionBody.class));
                                put(
                                        TOKEN_PAUSE_METRIC,
                                        new BodySetter<>(TokenPauseTransactionBody.class));
                                put(
                                        SCHEDULE_CREATE_METRIC,
                                        new BodySetter<>(ScheduleCreateTransactionBody.class));
                                put(
                                        SCHEDULE_SIGN_METRIC,
                                        new BodySetter<>(ScheduleSignTransactionBody.class));
                                put(
                                        SCHEDULE_DELETE_METRIC,
                                        new BodySetter<>(ScheduleDeleteTransactionBody.class));
                                put(
                                        UTIL_PRNG_METRIC,
                                        new BodySetter<>(UtilPrngTransactionBody.class));
                            }
                        };

        setters.forEach(
                (stat, setter) -> {
                    final var txn = TransactionBody.newBuilder();
                    setter.setDefaultInstanceFor(txn);
                    assertEquals(stat, getTxnStat(txn.build()));
                });

        assertEquals("NotImplemented", getTxnStat(TransactionBody.getDefaultInstance()));
    }

    @Test
    void recognizesMissingQueryCase() {
        assertTrue(functionalityOfQuery(Query.getDefaultInstance()).isEmpty());
    }

    @Test
    void getsExpectedQueryFunctionality() {
        final Map<HederaFunctionality, BodySetter<? extends GeneratedMessageV3, Query.Builder>>
                setters =
                        new HashMap<>() {
                            {
                                put(
                                        GetVersionInfo,
                                        new BodySetter<>(NetworkGetVersionInfoQuery.class));
                                put(GetByKey, new BodySetter<>(GetByKeyQuery.class));
                                put(
                                        ConsensusGetTopicInfo,
                                        new BodySetter<>(ConsensusGetTopicInfoQuery.class));
                                put(GetBySolidityID, new BodySetter<>(GetBySolidityIDQuery.class));
                                put(
                                        ContractCallLocal,
                                        new BodySetter<>(ContractCallLocalQuery.class));
                                put(ContractGetInfo, new BodySetter<>(ContractGetInfoQuery.class));
                                put(
                                        ContractGetBytecode,
                                        new BodySetter<>(ContractGetBytecodeQuery.class));
                                put(
                                        ContractGetRecords,
                                        new BodySetter<>(ContractGetRecordsQuery.class));
                                put(
                                        CryptoGetAccountBalance,
                                        new BodySetter<>(CryptoGetAccountBalanceQuery.class));
                                put(
                                        CryptoGetAccountRecords,
                                        new BodySetter<>(CryptoGetAccountRecordsQuery.class));
                                put(CryptoGetInfo, new BodySetter<>(CryptoGetInfoQuery.class));
                                put(
                                        CryptoGetLiveHash,
                                        new BodySetter<>(CryptoGetLiveHashQuery.class));
                                put(FileGetContents, new BodySetter<>(FileGetContentsQuery.class));
                                put(FileGetInfo, new BodySetter<>(FileGetInfoQuery.class));
                                put(
                                        TransactionGetReceipt,
                                        new BodySetter<>(TransactionGetReceiptQuery.class));
                                put(
                                        TransactionGetRecord,
                                        new BodySetter<>(TransactionGetRecordQuery.class));
                                put(TokenGetInfo, new BodySetter<>(TokenGetInfoQuery.class));
                                put(ScheduleGetInfo, new BodySetter<>(ScheduleGetInfoQuery.class));
                                put(
                                        NetworkGetExecutionTime,
                                        new BodySetter<>(NetworkGetExecutionTimeQuery.class));
                                put(
                                        GetAccountDetails,
                                        new BodySetter<>(GetAccountDetailsQuery.class));
                            }
                        };

        setters.forEach(
                (function, setter) -> {
                    final var query = Query.newBuilder();
                    setter.setDefaultInstanceFor(query);
                    assertEquals(function, functionalityOfQuery(query.build()).get());
                });
    }

    @Test
    void worksForEmpty() {
        assertTrue(activeHeaderFrom(Query.getDefaultInstance()).isEmpty());
    }

    @Test
    void getsExpectedActiveHeader() {
        final Set<BodySetter<? extends GeneratedMessageV3, Query.Builder>> setters =
                new HashSet<>() {
                    {
                        add(new BodySetter<>(TokenGetNftInfoQuery.class));
                        add(new BodySetter<>(TokenGetNftInfosQuery.class));
                        add(new BodySetter<>(TokenGetAccountNftInfosQuery.class));
                        add(new BodySetter<>(TokenGetInfoQuery.class));
                        add(new BodySetter<>(ScheduleGetInfoQuery.class));
                        add(new BodySetter<>(ConsensusGetTopicInfoQuery.class));
                        add(new BodySetter<>(GetBySolidityIDQuery.class));
                        add(new BodySetter<>(ContractCallLocalQuery.class));
                        add(new BodySetter<>(ContractGetInfoQuery.class));
                        add(new BodySetter<>(ContractGetBytecodeQuery.class));
                        add(new BodySetter<>(ContractGetRecordsQuery.class));
                        add(new BodySetter<>(CryptoGetAccountBalanceQuery.class));
                        add(new BodySetter<>(CryptoGetAccountRecordsQuery.class));
                        add(new BodySetter<>(CryptoGetInfoQuery.class));
                        add(new BodySetter<>(CryptoGetLiveHashQuery.class));
                        add(new BodySetter<>(CryptoGetStakersQuery.class));
                        add(new BodySetter<>(FileGetContentsQuery.class));
                        add(new BodySetter<>(FileGetInfoQuery.class));
                        add(new BodySetter<>(TransactionGetReceiptQuery.class));
                        add(new BodySetter<>(TransactionGetRecordQuery.class));
                        add(new BodySetter<>(TransactionGetFastRecordQuery.class));
                        add(new BodySetter<>(NetworkGetVersionInfoQuery.class));
                        add(new BodySetter<>(NetworkGetExecutionTimeQuery.class));
                        add(new BodySetter<>(GetAccountDetailsQuery.class));
                    }
                };

        for (var setter : setters) {
            final var query = Query.newBuilder();
            setter.setActiveHeaderFor(query);
            assertEquals(ANSWER_ONLY, activeHeaderFrom(query.build()).get().getResponseType());
        }
    }

    @Test
    void getsExpectedTxnFunctionality() {
        final Map<
                        HederaFunctionality,
                        BodySetter<? extends GeneratedMessageV3, TransactionBody.Builder>>
                setters =
                        new HashMap<>() {
                            {
                                put(
                                        SystemDelete,
                                        new BodySetter<>(SystemDeleteTransactionBody.class));
                                put(
                                        SystemUndelete,
                                        new BodySetter<>(SystemUndeleteTransactionBody.class));
                                put(
                                        ContractCall,
                                        new BodySetter<>(ContractCallTransactionBody.class));
                                put(
                                        ContractCreate,
                                        new BodySetter<>(ContractCreateTransactionBody.class));
                                put(
                                        EthereumTransaction,
                                        new BodySetter<>(EthereumTransactionBody.class));
                                put(
                                        ContractUpdate,
                                        new BodySetter<>(ContractUpdateTransactionBody.class));
                                put(
                                        CryptoAddLiveHash,
                                        new BodySetter<>(CryptoAddLiveHashTransactionBody.class));
                                put(
                                        CryptoCreate,
                                        new BodySetter<>(CryptoCreateTransactionBody.class));
                                put(
                                        CryptoDelete,
                                        new BodySetter<>(CryptoDeleteTransactionBody.class));
                                put(
                                        CryptoDeleteLiveHash,
                                        new BodySetter<>(
                                                CryptoDeleteLiveHashTransactionBody.class));
                                put(
                                        CryptoTransfer,
                                        new BodySetter<>(CryptoTransferTransactionBody.class));
                                put(
                                        CryptoUpdate,
                                        new BodySetter<>(CryptoUpdateTransactionBody.class));
                                put(FileAppend, new BodySetter<>(FileAppendTransactionBody.class));
                                put(FileCreate, new BodySetter<>(FileCreateTransactionBody.class));
                                put(FileDelete, new BodySetter<>(FileDeleteTransactionBody.class));
                                put(FileUpdate, new BodySetter<>(FileUpdateTransactionBody.class));
                                put(
                                        ContractDelete,
                                        new BodySetter<>(ContractDeleteTransactionBody.class));
                                put(
                                        TokenCreate,
                                        new BodySetter<>(TokenCreateTransactionBody.class));
                                put(
                                        TokenFreezeAccount,
                                        new BodySetter<>(TokenFreezeAccountTransactionBody.class));
                                put(
                                        TokenUnfreezeAccount,
                                        new BodySetter<>(
                                                TokenUnfreezeAccountTransactionBody.class));
                                put(
                                        TokenGrantKycToAccount,
                                        new BodySetter<>(TokenGrantKycTransactionBody.class));
                                put(
                                        TokenRevokeKycFromAccount,
                                        new BodySetter<>(TokenRevokeKycTransactionBody.class));
                                put(
                                        TokenDelete,
                                        new BodySetter<>(TokenDeleteTransactionBody.class));
                                put(
                                        TokenUpdate,
                                        new BodySetter<>(TokenUpdateTransactionBody.class));
                                put(TokenMint, new BodySetter<>(TokenMintTransactionBody.class));
                                put(TokenBurn, new BodySetter<>(TokenBurnTransactionBody.class));
                                put(
                                        TokenAccountWipe,
                                        new BodySetter<>(TokenWipeAccountTransactionBody.class));
                                put(
                                        TokenAssociateToAccount,
                                        new BodySetter<>(TokenAssociateTransactionBody.class));
                                put(
                                        TokenDissociateFromAccount,
                                        new BodySetter<>(TokenDissociateTransactionBody.class));
                                put(
                                        TokenUnpause,
                                        new BodySetter<>(TokenUnpauseTransactionBody.class));
                                put(TokenPause, new BodySetter<>(TokenPauseTransactionBody.class));
                                put(
                                        ScheduleCreate,
                                        new BodySetter<>(ScheduleCreateTransactionBody.class));
                                put(
                                        ScheduleSign,
                                        new BodySetter<>(ScheduleSignTransactionBody.class));
                                put(
                                        ScheduleDelete,
                                        new BodySetter<>(ScheduleDeleteTransactionBody.class));
                                put(Freeze, new BodySetter<>(FreezeTransactionBody.class));
                                put(
                                        ConsensusCreateTopic,
                                        new BodySetter<>(
                                                ConsensusCreateTopicTransactionBody.class));
                                put(
                                        ConsensusUpdateTopic,
                                        new BodySetter<>(
                                                ConsensusUpdateTopicTransactionBody.class));
                                put(
                                        ConsensusDeleteTopic,
                                        new BodySetter<>(
                                                ConsensusDeleteTopicTransactionBody.class));
                                put(
                                        ConsensusSubmitMessage,
                                        new BodySetter<>(
                                                ConsensusSubmitMessageTransactionBody.class));
                                put(UncheckedSubmit, new BodySetter<>(UncheckedSubmitBody.class));
                                put(
                                        TokenFeeScheduleUpdate,
                                        new BodySetter<>(
                                                TokenFeeScheduleUpdateTransactionBody.class));
                                put(UtilPrng, new BodySetter<>(UtilPrngTransactionBody.class));
                                put(
                                        CryptoApproveAllowance,
                                        new BodySetter<>(
                                                CryptoApproveAllowanceTransactionBody.class));
                            }
                        };

        setters.forEach(
                (function, setter) -> {
                    final var txn = TransactionBody.newBuilder();
                    setter.setDefaultInstanceFor(txn);
                    try {
                        assertEquals(function, functionOf(txn.build()));
                    } catch (UnknownHederaFunctionality uhf) {
                        throw new IllegalStateException(uhf);
                    }
                });
    }

    @Test
    void hashCorrectly() throws IllegalArgumentException {
        final var testBytes = "test bytes".getBytes();
        final var expectedHash =
                com.swirlds.common.utility.CommonUtils.unhex(
                        "2ddb907ecf9a8c086521063d6d310d46259437770587b3dbe2814ab17962a4e124a825fdd02cb167ac9fffdd4a5e8120");

        assertArrayEquals(expectedHash, CommonUtils.noThrowSha384HashOf(testBytes));
    }

    @Test
    void asTimestampRichInstantTest() {
        final var instant = RichInstant.fromJava(Instant.now());
        final var timestamp = MiscUtils.asTimestamp(instant);
        assertEquals(instant.toJava(), MiscUtils.timestampToInstant(timestamp));
    }

    @Test
    void asTimestampJavaTest() {
        final var instant = Instant.now();
        final var timestamp = MiscUtils.asTimestamp(instant);
        assertEquals(instant, MiscUtils.timestampToInstant(timestamp));
    }

    @Test
    void perm64Test() {
        assertEquals(0L, perm64(0L));
        assertEquals(-4328535976359616544L, perm64(1L));
        assertEquals(2657016865369639288L, perm64(7L));
    }

    @Test
    void describesCorrectly() throws DecoderException {
        assertEquals("<N/A>", describe(null));

        final var key = Key.newBuilder().setEd25519(ByteString.copyFrom("abcd".getBytes())).build();
        assertEquals(key.toString(), describe(JKey.mapKey(key)));

        final var tooDeep = TxnUtils.nestJKeys(15);
        assertEquals("<N/A>", describe(tooDeep));
    }

    @Test
    void managesOffsetsAsExpected() {
        final var sec = 1_234_567L;
        final Instant wellBeforeBoundary = Instant.ofEpochSecond(sec - 1, 500_000_000);
        final Instant beforeBoundary = Instant.ofEpochSecond(sec - 1, 999_999_999);
        final Instant onBoundary = Instant.ofEpochSecond(sec, 0);
        final Instant inTheMiddle = Instant.ofEpochSecond(sec, 500_000_000);

        assertEquals(beforeBoundary, nonNegativeNanosOffset(onBoundary, -1));
        assertEquals(wellBeforeBoundary, nonNegativeNanosOffset(onBoundary, -500_000_000));
        assertEquals(onBoundary, nonNegativeNanosOffset(beforeBoundary, +1));
        assertEquals(inTheMiddle.minusNanos(1), nonNegativeNanosOffset(inTheMiddle, -1));
        assertEquals(inTheMiddle.plusNanos(1), nonNegativeNanosOffset(inTheMiddle, +1));
    }

    @Test
    void rejectsNonPrimitiveProtoKeys() {
        assertFalse(
                MiscUtils.isSerializedProtoKey(
                        Key.newBuilder()
                                .setKeyList(
                                        KeyList.newBuilder()
                                                .addKeys(
                                                        Key.newBuilder()
                                                                .setEd25519(
                                                                        ByteString.copyFromUtf8(
                                                                                "01234567890123456789012345678901"))))
                                .build()
                                .toByteString()));
        assertFalse(MiscUtils.isSerializedProtoKey(ByteString.copyFromUtf8("NONSENSE")));
    }

    @Test
    void contractCallIsConsensusThrottled() {
        assertTrue(isGasThrottled(ContractCall));
    }

    @Test
    void contractCreateIsConsensusThrottled() {
        assertTrue(isGasThrottled(ContractCreate));
    }

    @Test
    void getGasLimitWorksForCreate() throws UnknownHederaFunctionality {
        final var op = ContractCreateTransactionBody.newBuilder().setGas(123456789L).build();
        final var txn = TransactionBody.newBuilder().setContractCreateInstance(op).build();

        assertEquals(
                123456789L,
                MiscUtils.getGasLimitForContractTx(txn, MiscUtils.functionOf(txn), null));
    }

    @Test
    void getGasLimitWorksForCall() throws UnknownHederaFunctionality {
        final var op = ContractCallTransactionBody.newBuilder().setGas(123456789L).build();
        final var txn = TransactionBody.newBuilder().setContractCall(op).build();

        assertEquals(
                123456789L,
                MiscUtils.getGasLimitForContractTx(txn, MiscUtils.functionOf(txn), null));
    }

    @Test
    void getGasLimitWorksForEthTxn() throws UnknownHederaFunctionality {
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
        final var txn = TransactionBody.newBuilder().setEthereumTransaction(op).build();

        assertEquals(
                gasLimit, MiscUtils.getGasLimitForContractTx(txn, MiscUtils.functionOf(txn), null));

        assertEquals(
                gasLimit,
                MiscUtils.getGasLimitForContractTx(
                        txn, MiscUtils.functionOf(txn), () -> ethTxData));
    }

    @Test
    void getGasLimitReturnsZeroByDefault() throws UnknownHederaFunctionality {
        final var op = TokenCreateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setTokenCreation(op).build();

        assertEquals(0L, MiscUtils.getGasLimitForContractTx(txn, MiscUtils.functionOf(txn), null));
    }

    @Test
    void ethereumTxnIsConsensusThrottled() {
        assertTrue(isGasThrottled(EthereumTransaction));
    }

    @Test
    void convertsByteArrayToBinary() {
        final var hashBytes = new Hash(TxnUtils.randomUtf8Bytes(48)).getValue();
        assertEquals(
                Integer.parseUnsignedInt(byteArrayToBinaryString(hashBytes).substring(0, 32), 2),
                ByteBuffer.wrap(hashBytes, 0, 32).getInt());
    }

    @Test
    void testAsPrimitiveKeyUnchecked() {
        final var ecdsaKeyBytes =
                unhex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
        final var ecdsaKey =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ecdsaKeyBytes)).build();

        assertEquals(ecdsaKey, MiscUtils.asPrimitiveKeyUnchecked(ecdsaKey.toByteString()));
    }

    @Test
    void testAsPrimitiveKeyUncheckedFails() {
        final var ecdsaKeyBytes =
                unhex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
        final var alias = ByteString.copyFrom(ecdsaKeyBytes);

        assertThrows(IllegalStateException.class, () -> MiscUtils.asPrimitiveKeyUnchecked(alias));
    }

    public static String byteArrayToBinaryString(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b1 : bytes) {
            result.append(
                    String.format("%8s", Integer.toBinaryString(b1 & 0xFF)).replace(' ', '0'));
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    public static class BodySetter<T, B> {
        private final Class<T> type;

        public BodySetter(Class<T> type) {
            this.type = type;
        }

        void setDefaultInstanceFor(final B builder) {
            try {
                final var setter = getSetter(builder, type);
                final var defaultGetter = type.getDeclaredMethod("getDefaultInstance");
                final T defaultInstance = (T) defaultGetter.invoke(null);
                setter.invoke(builder, defaultInstance);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        void setActiveHeaderFor(final B builder) {
            try {
                final var newBuilderMethod = type.getDeclaredMethod("newBuilder");
                final var opBuilder = newBuilderMethod.invoke(null);
                final var opBuilderClass = opBuilder.getClass();
                final var setHeaderMethod =
                        opBuilderClass.getDeclaredMethod("setHeader", QueryHeader.Builder.class);
                setHeaderMethod.invoke(
                        opBuilder, QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
                final var setter = getSetter(builder, opBuilderClass);
                setter.invoke(builder, opBuilder);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private Method getSetter(final B builder, final Class type) {
            return Stream.of(builder.getClass().getDeclaredMethods())
                    .filter(
                            m ->
                                    m.getName().startsWith("set")
                                            && m.getParameterTypes()[0].equals(type))
                    .findFirst()
                    .get();
        }
    }
}
