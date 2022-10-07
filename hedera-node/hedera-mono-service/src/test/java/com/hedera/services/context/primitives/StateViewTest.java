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
package com.hedera.services.context.primitives;

import static com.hedera.services.context.primitives.StateView.REMOVED_TOKEN;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hedera.services.state.virtual.schedule.ScheduleVirtualValueTest.scheduleCreateTxnWith;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getCryptoGrantedAllowancesList;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getFungibleGrantedTokenAllowancesList;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getNftGrantedAllowancesList;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHbar;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHts;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fractional;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_PAUSE_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asSchedule;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.PauseNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Paused;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.google.protobuf.ByteString;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.ledger.backing.BackingNfts;
import com.hedera.services.ledger.backing.BackingTokenRels;
import com.hedera.services.ledger.backing.BackingTokens;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.migration.UniqueTokenMapAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.StakingInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class StateViewTest {
    private static final int wellKnownNumKvPairs = 144;
    private final Instant resolutionTime = Instant.ofEpochSecond(123L);
    private final RichInstant now =
            RichInstant.fromGrpc(Timestamp.newBuilder().setNanos(123123213).build());
    private final int maxTokensFprAccountInfo = 10;
    private final long expiry = 2_000_000L;
    private final byte[] data = "SOMETHING".getBytes();
    private final byte[] expectedBytecode = "A Supermarket in California".getBytes();
    private final String tokenMemo = "Goodbye and keep cold";
    private HFileMeta metadata;
    private HFileMeta immutableMetadata;
    private final FileID target = asFile("0.0.123");
    private final TokenID tokenId = asToken("0.0.5");
    private final TokenID nftTokenId = asToken("0.0.3");
    private final EntityNum tokenNum = EntityNum.fromTokenId(tokenId);
    private final TokenID missingTokenId = asToken("0.0.5555");
    private final AccountID payerAccountId = asAccount("0.0.9");
    private final AccountID tokenAccountId = asAccount("0.0.10");
    private final AccountID accountWithAlias = asAccountWithAlias("aaaa");
    private final AccountID treasuryOwnerId = asAccount("0.0.0");
    private final AccountID nftOwnerId = asAccount("0.0.44");
    private final AccountID nftSpenderId = asAccount("0.0.66");
    private final ScheduleID scheduleId = asSchedule("0.0.8");
    private final ScheduleID missingScheduleId = asSchedule("0.0.9");
    private final ContractID cid = asContract("0.0.1");
    private final EntityNumPair tokenAssociationId =
            EntityNumPair.fromLongs(tokenAccountId.getAccountNum(), tokenId.getTokenNum());
    private final EntityNumPair nftAssociationId =
            EntityNumPair.fromLongs(tokenAccountId.getAccountNum(), nftTokenId.getTokenNum());
    private final byte[] cidAddress = asEvmAddress(0, 0, cid.getContractNum());
    private final AccountID autoRenew = asAccount("0.0.6");
    private final AccountID creatorAccountID = asAccount("0.0.7");
    private final long autoRenewPeriod = 1_234_567;
    private final String fileMemo = "Originally she thought";
    private final ByteString create2Address =
            ByteString.copyFrom(unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb"));
    private final ByteString ledgerId = ByteString.copyFromUtf8("0x03");

    private FileGetInfoResponse.FileInfo expected;
    private FileGetInfoResponse.FileInfo expectedImmutable;

    private Map<byte[], byte[]> bytecode;
    private Map<FileID, byte[]> contents;
    private Map<FileID, HFileMeta> attrs;

    private MerkleMap<EntityNum, MerkleToken> tokens;
    private MerkleMap<EntityNum, MerkleTopic> topics;
    private MerkleMap<EntityNum, MerkleAccount> contracts;
    private UniqueTokenMapAdapter uniqueTokens;
    private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;
    private VirtualMap<VirtualBlobKey, VirtualBlobValue> storage;
    private VirtualMap<ContractKey, IterableContractValue> contractStorage;
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
    private MerkleNetworkContext networkContext;
    private ScheduleStore scheduleStore;
    private TransactionBody parentScheduleCreate;
    private NetworkInfo networkInfo;
    private MerkleTokenRelStatus tokenAccountRel;
    private MerkleTokenRelStatus nftAccountRel;

    private MerkleToken token;
    private MerkleToken nft;
    private ScheduleVirtualValue schedule;
    private MerkleAccount contract;
    private MerkleAccount tokenAccount;
    private MerkleSpecialFiles specialFiles;
    private MutableStateChildren children;

    private MockedStatic<StateView> mockedStatic;

    @Mock private AliasManager aliasManager;
    @Mock private RewardCalculator rewardCalculator;

    private StateView subject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() throws Throwable {
        metadata =
                new HFileMeta(
                        false, TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey(), expiry, fileMemo);
        immutableMetadata = new HFileMeta(false, StateView.EMPTY_WACL, expiry);

        expectedImmutable =
                FileGetInfoResponse.FileInfo.newBuilder()
                        .setLedgerId(ledgerId)
                        .setDeleted(false)
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
                        .setFileID(target)
                        .setSize(data.length)
                        .build();
        expected =
                expectedImmutable.toBuilder()
                        .setKeys(TxnHandlingScenario.MISC_FILE_WACL_KT.asKey().getKeyList())
                        .setMemo(fileMemo)
                        .build();

        tokenAccount =
                MerkleAccountFactory.newAccount().isSmartContract(false).tokens(tokenId).get();
        tokenAccount.setKey(EntityNum.fromAccountId(tokenAccountId));
        tokenAccount.setNftsOwned(10);
        tokenAccount.setMaxAutomaticAssociations(123);
        tokenAccount.setAlias(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey().getEd25519());
        tokenAccount.setHeadTokenId(tokenId.getTokenNum());
        tokenAccount.setNumAssociations(1);
        tokenAccount.setStakePeriodStart(1);
        tokenAccount.setNumPositiveBalances(0);
        tokenAccount.setStakedId(10L);
        tokenAccount.setDeclineReward(true);
        contract =
                MerkleAccountFactory.newAccount()
                        .alias(create2Address)
                        .memo("Stay cold...")
                        .numKvPairs(wellKnownNumKvPairs)
                        .isSmartContract(true)
                        .accountKeys(COMPLEX_KEY_ACCOUNT_KT)
                        .proxy(asAccount("0.0.3"))
                        .receiverSigRequired(true)
                        .balance(555L)
                        .autoRenewPeriod(1_000_000L)
                        .deleted(true)
                        .expirationTime(9_999_999L)
                        .autoRenewAccount(asAccount("0.0.4"))
                        .maxAutomaticAssociations(10)
                        .get();
        contracts = (MerkleMap<EntityNum, MerkleAccount>) mock(MerkleMap.class);
        topics = (MerkleMap<EntityNum, MerkleTopic>) mock(MerkleMap.class);
        stakingInfo = (MerkleMap<EntityNum, MerkleStakingInfo>) mock(MerkleMap.class);
        networkContext = mock(MerkleNetworkContext.class);

        tokenAccountRel = new MerkleTokenRelStatus(123L, false, true, true);
        tokenAccountRel.setKey(tokenAssociationId);
        tokenAccountRel.setNext(nftTokenId.getTokenNum());

        nftAccountRel = new MerkleTokenRelStatus(2L, false, true, false);
        tokenAccountRel.setKey(nftAssociationId);
        tokenAccountRel.setPrev(tokenId.getTokenNum());

        tokenRels = new MerkleMap<>();
        tokenRels.put(tokenAssociationId, tokenAccountRel);
        tokenRels.put(nftAssociationId, nftAccountRel);

        tokens = (MerkleMap<EntityNum, MerkleToken>) mock(MerkleMap.class);
        token =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "UnfrozenToken",
                        "UnfrozenTokenName",
                        true,
                        true,
                        EntityId.fromGrpcTokenId(tokenId));
        setUpToken(token);
        token.setKey(tokenNum);
        token.setTokenType(TokenType.FUNGIBLE_COMMON);

        nft =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "UnfrozenToken",
                        "UnfrozenTokenName",
                        true,
                        true,
                        EntityId.fromGrpcTokenId(nftTokenId));
        setUpToken(nft);
        nft.setKey(EntityNum.fromTokenId(nftTokenId));
        nft.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);

        token.setSupplyType(TokenSupplyType.FINITE);
        token.setFeeScheduleFrom(grpcCustomFees);

        scheduleStore = mock(ScheduleStore.class);
        final var scheduleMemo = "For what but eye and ear";
        parentScheduleCreate =
                scheduleCreateTxnWith(
                        SCHEDULE_ADMIN_KT.asKey(),
                        scheduleMemo,
                        payerAccountId,
                        creatorAccountID,
                        MiscUtils.asTimestamp(now.toJava()));
        schedule = ScheduleVirtualValue.from(parentScheduleCreate.toByteArray(), expiry);
        schedule.witnessValidSignature("01234567890123456789012345678901".getBytes());
        schedule.witnessValidSignature("_123456789_123456789_123456789_1".getBytes());
        schedule.witnessValidSignature("_o23456789_o23456789_o23456789_o".getBytes());

        contents = mock(Map.class);
        attrs = mock(Map.class);
        bytecode = mock(Map.class);
        specialFiles = mock(MerkleSpecialFiles.class);

        uniqueTokens = UniqueTokenMapAdapter.wrap(new MerkleMap<>());
        uniqueTokens.put(targetNftKey.asNftNumPair().nftId(), targetNft);
        uniqueTokens.put(treasuryNftKey.asNftNumPair().nftId(), treasuryNft);

        storage = (VirtualMap<VirtualBlobKey, VirtualBlobValue>) mock(VirtualMap.class);
        contractStorage = (VirtualMap<ContractKey, IterableContractValue>) mock(VirtualMap.class);

        children = new MutableStateChildren();
        children.setUniqueTokens(uniqueTokens);
        children.setAccounts(contracts);
        children.setTokens(tokens);
        children.setTokenAssociations(tokenRels);
        children.setSpecialFiles(specialFiles);
        children.setTokens(tokens);
        children.setStakingInfo(stakingInfo);

        networkInfo = mock(NetworkInfo.class);

        subject = new StateView(scheduleStore, children, networkInfo);
        subject.fileAttrs = attrs;
        subject.fileContents = contents;
        subject.contractBytecode = bytecode;
    }

    private void setUpToken(final MerkleToken token) throws DecoderException {
        token.setMemo(tokenMemo);
        token.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey());
        token.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey());
        token.setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asJKey());
        token.setSupplyKey(COMPLEX_KEY_ACCOUNT_KT.asJKey());
        token.setWipeKey(MISC_ACCOUNT_KT.asJKey());
        token.setFeeScheduleKey(MISC_ACCOUNT_KT.asJKey());
        token.setPauseKey(TxnHandlingScenario.TOKEN_PAUSE_KT.asJKey());
        token.setAutoRenewAccount(EntityId.fromGrpcAccountId(autoRenew));
        token.setExpiry(expiry);
        token.setAutoRenewPeriod(autoRenewPeriod);
        token.setDeleted(true);
        token.setPaused(true);
        token.setSupplyType(TokenSupplyType.FINITE);
        token.setFeeScheduleFrom(grpcCustomFees);
    }

    @Test
    void tokenExistsWorks() {
        given(tokens.containsKey(tokenNum)).willReturn(true);
        assertTrue(subject.tokenExists(tokenId));
        assertFalse(subject.tokenExists(missingTokenId));
    }

    @Test
    void nftExistsWorks() {
        assertTrue(subject.nftExists(targetNftId));
        assertFalse(subject.nftExists(missingNftId));
    }

    @Test
    void scheduleExistsWorks() {
        given(scheduleStore.resolve(scheduleId)).willReturn(scheduleId);
        given(scheduleStore.resolve(missingScheduleId)).willReturn(ScheduleStore.MISSING_SCHEDULE);

        assertTrue(subject.scheduleExists(scheduleId));
        assertFalse(subject.scheduleExists(missingScheduleId));
    }

    @Test
    void tokenWithWorks() {
        given(tokens.get(tokenNum)).willReturn(token);
        assertSame(token, subject.tokenWith(tokenId).get());
    }

    @Test
    void tokenWithWorksForMissing() {
        assertTrue(subject.tokenWith(tokenId).isEmpty());
    }

    @Test
    void recognizesMissingSchedule() {
        given(scheduleStore.resolve(missingScheduleId)).willReturn(ScheduleStore.MISSING_SCHEDULE);
        final var info = subject.infoForSchedule(missingScheduleId);
        assertTrue(info.isEmpty());
    }

    @Test
    void infoForScheduleFailsGracefully() {
        given(scheduleStore.get(any())).willThrow(IllegalArgumentException.class);
        final var info = subject.infoForSchedule(scheduleId);
        assertTrue(info.isEmpty());
    }

    @Test
    void getsScheduleInfoForDeleted() {
        given(scheduleStore.resolve(scheduleId)).willReturn(scheduleId);
        given(scheduleStore.get(scheduleId)).willReturn(schedule);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var expectedScheduledTxn =
                parentScheduleCreate.getScheduleCreate().getScheduledTransactionBody();

        schedule.markDeleted(resolutionTime);
        final var gotten = subject.infoForSchedule(scheduleId);
        final var info = gotten.get();

        assertEquals(scheduleId, info.getScheduleID());
        assertEquals(schedule.schedulingAccount().toGrpcAccountId(), info.getCreatorAccountID());
        assertEquals(schedule.payer().toGrpcAccountId(), info.getPayerAccountID());
        assertEquals(Timestamp.newBuilder().setSeconds(expiry).build(), info.getExpirationTime());
        final var expectedSignatoryList = KeyList.newBuilder();
        schedule.signatories()
                .forEach(
                        a ->
                                expectedSignatoryList.addKeys(
                                        Key.newBuilder().setEd25519(ByteString.copyFrom(a))));
        assertArrayEquals(
                expectedSignatoryList.build().getKeysList().toArray(),
                info.getSigners().getKeysList().toArray());
        assertEquals(SCHEDULE_ADMIN_KT.asKey(), info.getAdminKey());
        assertEquals(expectedScheduledTxn, info.getScheduledTransactionBody());
        assertEquals(schedule.scheduledTransactionId(), info.getScheduledTransactionID());
        assertEquals(fromJava(resolutionTime).toGrpc(), info.getDeletionTime());
        assertEquals(ledgerId, info.getLedgerId());
    }

    @Test
    void getsScheduleInfoForExecuted() {
        final var mockEd25519Key = new JEd25519Key("a123456789a123456789a123456789a1".getBytes());
        final var mockSecp256k1Key =
                new JECDSASecp256k1Key("012345678901234567890123456789012".getBytes());
        given(scheduleStore.resolve(scheduleId)).willReturn(scheduleId);
        given(scheduleStore.get(scheduleId)).willReturn(schedule);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        schedule.witnessValidSignature(mockEd25519Key.primitiveKeyIfPresent());
        schedule.witnessValidSignature(mockSecp256k1Key.primitiveKeyIfPresent());

        schedule.markExecuted(resolutionTime);
        final var gotten = subject.infoForSchedule(scheduleId);
        final var info = gotten.get();

        assertEquals(ledgerId, info.getLedgerId());
        assertEquals(fromJava(resolutionTime).toGrpc(), info.getExecutionTime());
        final var signatures = info.getSigners().getKeysList();
        assertEquals(MiscUtils.asKeyUnchecked(mockEd25519Key), signatures.get(3));
        assertEquals(MiscUtils.asKeyUnchecked(mockSecp256k1Key), signatures.get(4));
    }

    @Test
    void recognizesMissingToken() {
        final var info = subject.infoForToken(missingTokenId);

        assertTrue(info.isEmpty());
    }

    @Test
    void infoForTokenFailsGracefully() {
        given(tokens.get(tokenNum)).willThrow(IllegalArgumentException.class);

        final var info = subject.infoForToken(tokenId);

        assertTrue(info.isEmpty());
    }

    @Test
    void getsTokenInfoMinusFreezeIfMissing() {
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(tokens.get(tokenNum)).willReturn(token);

        token.setFreezeKey(MerkleToken.UNUSED_KEY);

        final var info = subject.infoForToken(tokenId).get();

        assertEquals(tokenId, info.getTokenId());
        assertEquals(token.symbol(), info.getSymbol());
        assertEquals(token.name(), info.getName());
        assertEquals(token.treasury().toGrpcAccountId(), info.getTreasury());
        assertEquals(token.totalSupply(), info.getTotalSupply());
        assertEquals(token.decimals(), info.getDecimals());
        assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
        assertEquals(TokenFreezeStatus.FreezeNotApplicable, info.getDefaultFreezeStatus());
        assertFalse(info.hasFreezeKey());
        assertEquals(ledgerId, info.getLedgerId());
    }

    @Test
    void getsTokenInfoMinusPauseIfMissing() {
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(tokens.get(tokenNum)).willReturn(token);

        token.setPauseKey(MerkleToken.UNUSED_KEY);

        final var info = subject.infoForToken(tokenId).get();

        assertEquals(tokenId, info.getTokenId());
        assertEquals(token.symbol(), info.getSymbol());
        assertEquals(token.name(), info.getName());
        assertEquals(token.treasury().toGrpcAccountId(), info.getTreasury());
        assertEquals(token.totalSupply(), info.getTotalSupply());
        assertEquals(token.decimals(), info.getDecimals());
        assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
        assertEquals(PauseNotApplicable, info.getPauseStatus());
        assertFalse(info.hasPauseKey());
        assertEquals(ledgerId, info.getLedgerId());
    }

    @Test
    void getsTokenInfo() {
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(tokens.get(tokenNum)).willReturn(token);
        final var miscKey = MISC_ACCOUNT_KT.asKey();

        final var info = subject.infoForToken(tokenId).get();

        assertTrue(info.getDeleted());
        assertEquals(Paused, info.getPauseStatus());
        assertEquals(token.memo(), info.getMemo());
        assertEquals(tokenId, info.getTokenId());
        assertEquals(token.symbol(), info.getSymbol());
        assertEquals(token.name(), info.getName());
        assertEquals(token.treasury().toGrpcAccountId(), info.getTreasury());
        assertEquals(token.totalSupply(), info.getTotalSupply());
        assertEquals(token.decimals(), info.getDecimals());
        assertEquals(token.grpcFeeSchedule(), info.getCustomFeesList());
        assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
        assertEquals(TOKEN_FREEZE_KT.asKey(), info.getFreezeKey());
        assertEquals(TOKEN_KYC_KT.asKey(), info.getKycKey());
        assertEquals(TOKEN_PAUSE_KT.asKey(), info.getPauseKey());
        assertEquals(miscKey, info.getWipeKey());
        assertEquals(miscKey, info.getFeeScheduleKey());
        assertEquals(autoRenew, info.getAutoRenewAccount());
        assertEquals(
                Duration.newBuilder().setSeconds(autoRenewPeriod).build(),
                info.getAutoRenewPeriod());
        assertEquals(Timestamp.newBuilder().setSeconds(expiry).build(), info.getExpiry());
        assertEquals(TokenFreezeStatus.Frozen, info.getDefaultFreezeStatus());
        assertEquals(TokenKycStatus.Granted, info.getDefaultKycStatus());
        assertEquals(ledgerId, info.getLedgerId());
    }

    @Test
    void getsContractInfo() throws Exception {
        final var target = EntityNum.fromContractId(cid);
        given(contracts.get(EntityNum.fromContractId(cid))).willReturn(contract);
        final var expectedTotalStorage =
                StateView.BYTES_PER_EVM_KEY_VALUE_PAIR * wellKnownNumKvPairs;
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        List<TokenRelationship> rels =
                List.of(
                        TokenRelationship.newBuilder()
                                .setTokenId(TokenID.newBuilder().setTokenNum(123L))
                                .setFreezeStatus(TokenFreezeStatus.FreezeNotApplicable)
                                .setKycStatus(TokenKycStatus.KycNotApplicable)
                                .setBalance(321L)
                                .build());
        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(subject, contract, maxTokensFprAccountInfo))
                .thenReturn(rels);

        final var info =
                subject.infoForContract(
                                cid, aliasManager, maxTokensFprAccountInfo, rewardCalculator)
                        .get();

        assertEquals(cid, info.getContractID());
        assertEquals(asAccount(cid), info.getAccountID());
        assertEquals(JKey.mapJKey(contract.getAccountKey()), info.getAdminKey());
        assertEquals(contract.getMemo(), info.getMemo());
        assertEquals(contract.getAutoRenewSecs(), info.getAutoRenewPeriod().getSeconds());
        assertEquals(contract.getBalance(), info.getBalance());
        assertEquals(CommonUtils.hex(create2Address.toByteArray()), info.getContractAccountID());
        assertEquals(contract.getExpiry(), info.getExpirationTime().getSeconds());
        assertEquals(EntityId.fromIdentityCode(4), contract.getAutoRenewAccount());
        assertEquals(rels, info.getTokenRelationshipsList());
        assertEquals(ledgerId, info.getLedgerId());
        assertTrue(info.getDeleted());
        assertEquals(expectedTotalStorage, info.getStorage());
        assertEquals(
                contract.getMaxAutomaticAssociations(), info.getMaxAutomaticTokenAssociations());
        mockedStatic.close();
    }

    @Test
    void getsContractInfoWithoutCreate2Address() throws Exception {
        final var target = EntityNum.fromContractId(cid);
        given(contracts.get(EntityNum.fromContractId(cid))).willReturn(contract);
        contract.setAlias(ByteString.EMPTY);
        final var expectedTotalStorage =
                StateView.BYTES_PER_EVM_KEY_VALUE_PAIR * wellKnownNumKvPairs;
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        List<TokenRelationship> rels =
                List.of(
                        TokenRelationship.newBuilder()
                                .setTokenId(TokenID.newBuilder().setTokenNum(123L))
                                .setFreezeStatus(TokenFreezeStatus.FreezeNotApplicable)
                                .setKycStatus(TokenKycStatus.KycNotApplicable)
                                .setBalance(321L)
                                .build());
        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(subject, contract, maxTokensFprAccountInfo))
                .thenReturn(rels);

        final var info =
                subject.infoForContract(
                                cid, aliasManager, maxTokensFprAccountInfo, rewardCalculator)
                        .get();

        assertEquals(cid, info.getContractID());
        assertEquals(asAccount(cid), info.getAccountID());
        assertEquals(JKey.mapJKey(contract.getAccountKey()), info.getAdminKey());
        assertEquals(contract.getMemo(), info.getMemo());
        assertEquals(contract.getAutoRenewSecs(), info.getAutoRenewPeriod().getSeconds());
        assertEquals(contract.getBalance(), info.getBalance());
        assertEquals(EntityIdUtils.asHexedEvmAddress(asAccount(cid)), info.getContractAccountID());
        assertEquals(contract.getExpiry(), info.getExpirationTime().getSeconds());
        assertEquals(rels, info.getTokenRelationshipsList());
        assertEquals(ledgerId, info.getLedgerId());
        assertTrue(info.getDeleted());
        assertEquals(expectedTotalStorage, info.getStorage());
        mockedStatic.close();
    }

    @Test
    void getTokenRelationship() {
        given(tokens.getOrDefault(tokenNum, REMOVED_TOKEN)).willReturn(token);
        given(tokens.getOrDefault(EntityNum.fromTokenId(nftTokenId), REMOVED_TOKEN))
                .willReturn(nft);

        List<TokenRelationship> expectedRels =
                List.of(
                        TokenRelationship.newBuilder()
                                .setTokenId(tokenId)
                                .setSymbol("UnfrozenToken")
                                .setBalance(123L)
                                .setKycStatus(TokenKycStatus.Granted)
                                .setFreezeStatus(TokenFreezeStatus.Unfrozen)
                                .setAutomaticAssociation(true)
                                .setDecimals(1)
                                .build(),
                        TokenRelationship.newBuilder()
                                .setTokenId(nftTokenId)
                                .setSymbol("UnfrozenToken")
                                .setBalance(2L)
                                .setKycStatus(TokenKycStatus.Granted)
                                .setFreezeStatus(TokenFreezeStatus.Unfrozen)
                                .setAutomaticAssociation(false)
                                .setDecimals(1)
                                .build());

        final var actualRels = StateView.tokenRels(subject, tokenAccount, maxTokensFprAccountInfo);

        assertEquals(expectedRels, actualRels);
    }

    @Test
    void getInfoForNftMissing() {
        final var nftID = NftID.newBuilder().setTokenID(tokenId).setSerialNumber(123L).build();

        final var actualTokenNftInfo = subject.infoForNft(nftID);

        assertEquals(Optional.empty(), actualTokenNftInfo);
    }

    @Test
    void getTokenType() {
        given(tokens.get(tokenNum)).willReturn(token);
        final var actualTokenType = subject.tokenType(tokenId).get();

        assertEquals(FUNGIBLE_COMMON, actualTokenType);
    }

    @Test
    void getTokenTypeMissing() {
        final var actualTokenType = subject.tokenType(tokenId);

        assertEquals(Optional.empty(), actualTokenType);
    }

    @Test
    void getTokenTypeException() {
        given(tokens.get(tokenId)).willThrow(new RuntimeException());

        final var actualTokenType = subject.tokenType(tokenId);

        assertEquals(Optional.empty(), actualTokenType);
    }

    @Test
    void infoForRegularAccount() {
        given(contracts.get(EntityNum.fromAccountId(tokenAccountId))).willReturn(tokenAccount);

        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(subject, tokenAccount, maxTokensFprAccountInfo))
                .thenReturn(Collections.emptyList());
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var expectedResponse =
                CryptoGetInfoResponse.AccountInfo.newBuilder()
                        .setLedgerId(ledgerId)
                        .setKey(asKeyUnchecked(tokenAccount.getAccountKey()))
                        .setAccountID(tokenAccountId)
                        .setAlias(tokenAccount.getAlias())
                        .setReceiverSigRequired(tokenAccount.isReceiverSigRequired())
                        .setDeleted(tokenAccount.isDeleted())
                        .setMemo(tokenAccount.getMemo())
                        .setAutoRenewPeriod(
                                Duration.newBuilder().setSeconds(tokenAccount.getAutoRenewSecs()))
                        .setBalance(tokenAccount.getBalance())
                        .setExpirationTime(
                                Timestamp.newBuilder().setSeconds(tokenAccount.getExpiry()))
                        .setContractAccountID(EntityIdUtils.asHexedEvmAddress(tokenAccountId))
                        .setOwnedNfts(tokenAccount.getNftsOwned())
                        .setMaxAutomaticTokenAssociations(
                                tokenAccount.getMaxAutomaticAssociations())
                        .setStakingInfo(
                                StakingInfo.newBuilder()
                                        .setDeclineReward(true)
                                        .setStakedAccountId(
                                                AccountID.newBuilder().setAccountNum(10L).build())
                                        .build())
                        .build();

        final var actualResponse =
                subject.infoForAccount(
                        tokenAccountId, aliasManager, maxTokensFprAccountInfo, rewardCalculator);

        assertEquals(expectedResponse, actualResponse.get());
        mockedStatic.close();
    }

    @Test
    void stakingInfoReturnedCorrectly() {
        final var startPeriod = 10000L;
        tokenAccount =
                MerkleAccountFactory.newAccount().isSmartContract(false).tokens(tokenId).get();
        tokenAccount.setStakedId(-10L);
        tokenAccount.setDeclineReward(false);
        tokenAccount.setStakePeriodStart(startPeriod);
        given(rewardCalculator.epochSecondAtStartOfPeriod(startPeriod)).willReturn(1_234_567L);

        given(contracts.get(EntityNum.fromAccountId(tokenAccountId))).willReturn(tokenAccount);

        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(subject, tokenAccount, maxTokensFprAccountInfo))
                .thenReturn(Collections.emptyList());
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var expectedResponse =
                StakingInfo.newBuilder()
                        .setDeclineReward(false)
                        .setStakedNodeId(9L)
                        .setStakePeriodStart(Timestamp.newBuilder().setSeconds(1_234_567L))
                        .build();

        final var actualResponse =
                subject.infoForAccount(
                        tokenAccountId, aliasManager, maxTokensFprAccountInfo, rewardCalculator);

        assertEquals(expectedResponse, actualResponse.get().getStakingInfo());
        mockedStatic.close();
    }

    @Test
    void infoForExternallyOperatedAccount() {
        final byte[] ecdsaKey =
                Hex.decode("033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
        given(contracts.get(EntityNum.fromAccountId(tokenAccountId))).willReturn(tokenAccount);
        tokenAccount.setAccountKey(new JECDSASecp256k1Key(ecdsaKey));
        final var expectedAddress = CommonUtils.hex(EthTxSigs.recoverAddressFromPubKey(ecdsaKey));

        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(subject, tokenAccount, maxTokensFprAccountInfo))
                .thenReturn(Collections.emptyList());
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var expectedResponse =
                CryptoGetInfoResponse.AccountInfo.newBuilder()
                        .setLedgerId(ledgerId)
                        .setKey(asKeyUnchecked(tokenAccount.getAccountKey()))
                        .setAccountID(tokenAccountId)
                        .setAlias(tokenAccount.getAlias())
                        .setReceiverSigRequired(tokenAccount.isReceiverSigRequired())
                        .setDeleted(tokenAccount.isDeleted())
                        .setMemo(tokenAccount.getMemo())
                        .setAutoRenewPeriod(
                                Duration.newBuilder().setSeconds(tokenAccount.getAutoRenewSecs()))
                        .setBalance(tokenAccount.getBalance())
                        .setExpirationTime(
                                Timestamp.newBuilder().setSeconds(tokenAccount.getExpiry()))
                        .setContractAccountID(expectedAddress)
                        .setOwnedNfts(tokenAccount.getNftsOwned())
                        .setMaxAutomaticTokenAssociations(
                                tokenAccount.getMaxAutomaticAssociations())
                        .setStakingInfo(
                                StakingInfo.newBuilder()
                                        .setStakedAccountId(
                                                AccountID.newBuilder().setAccountNum(10).build())
                                        .setDeclineReward(true)
                                        .build())
                        .build();

        final var actualResponse =
                subject.infoForAccount(
                        tokenAccountId, aliasManager, maxTokensFprAccountInfo, rewardCalculator);
        mockedStatic.close();

        assertEquals(expectedResponse, actualResponse.get());
    }

    @Test
    void accountDetails() {
        given(contracts.get(EntityNum.fromAccountId(tokenAccountId))).willReturn(tokenAccount);

        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(subject, tokenAccount, maxTokensFprAccountInfo))
                .thenReturn(Collections.emptyList());
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        final var expectedResponse =
                GetAccountDetailsResponse.AccountDetails.newBuilder()
                        .setLedgerId(ledgerId)
                        .setKey(asKeyUnchecked(tokenAccount.getAccountKey()))
                        .setAccountId(tokenAccountId)
                        .setAlias(tokenAccount.getAlias())
                        .setReceiverSigRequired(tokenAccount.isReceiverSigRequired())
                        .setDeleted(tokenAccount.isDeleted())
                        .setMemo(tokenAccount.getMemo())
                        .setAutoRenewPeriod(
                                Duration.newBuilder().setSeconds(tokenAccount.getAutoRenewSecs()))
                        .setBalance(tokenAccount.getBalance())
                        .setExpirationTime(
                                Timestamp.newBuilder().setSeconds(tokenAccount.getExpiry()))
                        .setContractAccountId(EntityIdUtils.asHexedEvmAddress(tokenAccountId))
                        .setOwnedNfts(tokenAccount.getNftsOwned())
                        .setMaxAutomaticTokenAssociations(
                                tokenAccount.getMaxAutomaticAssociations())
                        .addAllGrantedCryptoAllowances(getCryptoGrantedAllowancesList(tokenAccount))
                        .addAllGrantedTokenAllowances(
                                getFungibleGrantedTokenAllowancesList(tokenAccount))
                        .addAllGrantedNftAllowances(getNftGrantedAllowancesList(tokenAccount))
                        .build();

        final var actualResponse =
                subject.accountDetails(tokenAccountId, aliasManager, maxTokensFprAccountInfo);
        mockedStatic.close();

        assertEquals(expectedResponse, actualResponse.get());
    }

    @Test
    void infoForAccountWithAlias() {
        given(aliasManager.lookupIdBy(any())).willReturn(EntityNum.fromAccountId(tokenAccountId));
        given(contracts.get(EntityNum.fromAccountId(tokenAccountId))).willReturn(tokenAccount);
        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(subject, tokenAccount, maxTokensFprAccountInfo))
                .thenReturn(Collections.emptyList());
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var expectedResponse =
                CryptoGetInfoResponse.AccountInfo.newBuilder()
                        .setLedgerId(ledgerId)
                        .setKey(asKeyUnchecked(tokenAccount.getAccountKey()))
                        .setAccountID(tokenAccountId)
                        .setAlias(tokenAccount.getAlias())
                        .setReceiverSigRequired(tokenAccount.isReceiverSigRequired())
                        .setDeleted(tokenAccount.isDeleted())
                        .setMemo(tokenAccount.getMemo())
                        .setAutoRenewPeriod(
                                Duration.newBuilder().setSeconds(tokenAccount.getAutoRenewSecs()))
                        .setBalance(tokenAccount.getBalance())
                        .setExpirationTime(
                                Timestamp.newBuilder().setSeconds(tokenAccount.getExpiry()))
                        .setContractAccountID(EntityIdUtils.asHexedEvmAddress(tokenAccountId))
                        .setOwnedNfts(tokenAccount.getNftsOwned())
                        .setMaxAutomaticTokenAssociations(
                                tokenAccount.getMaxAutomaticAssociations())
                        .setStakingInfo(
                                StakingInfo.newBuilder()
                                        .setDeclineReward(true)
                                        .setStakedAccountId(
                                                AccountID.newBuilder().setAccountNum(10L).build())
                                        .build())
                        .build();

        final var actualResponse =
                subject.infoForAccount(
                        accountWithAlias, aliasManager, maxTokensFprAccountInfo, rewardCalculator);
        mockedStatic.close();
        assertEquals(expectedResponse, actualResponse.get());
    }

    @Test
    void numNftsOwnedWorksForExisting() {
        given(contracts.get(EntityNum.fromAccountId(tokenAccountId))).willReturn(tokenAccount);

        assertEquals(tokenAccount.getNftsOwned(), subject.numNftsOwnedBy(tokenAccountId));
    }

    @Test
    void infoForMissingAccount() {
        given(contracts.get(EntityNum.fromAccountId(tokenAccountId))).willReturn(null);

        final var actualResponse =
                subject.infoForAccount(
                        tokenAccountId, aliasManager, maxTokensFprAccountInfo, rewardCalculator);

        assertEquals(Optional.empty(), actualResponse);
    }

    @Test
    void infoForMissingAccountWithAlias() {
        EntityNum mockedEntityNum = mock(EntityNum.class);

        given(aliasManager.lookupIdBy(any())).willReturn(mockedEntityNum);
        given(contracts.get(mockedEntityNum)).willReturn(null);

        final var actualResponse =
                subject.infoForAccount(
                        accountWithAlias, aliasManager, maxTokensFprAccountInfo, rewardCalculator);
        assertEquals(Optional.empty(), actualResponse);
    }

    @Test
    void getTopics() {
        final var children = new MutableStateChildren();
        children.setTopics(topics);

        subject = new StateView(null, children, null);

        final var actualTopics = subject.topics();

        assertEquals(topics, actualTopics);
    }

    @Test
    void getStorageAndContractStorage() {
        final var children = new MutableStateChildren();
        children.setContractStorage(contractStorage);
        children.setStorage(storage);

        subject = new StateView(null, children, null);

        final var actualStorage = subject.storage();
        final var actualContractStorage = subject.contractStorage();

        assertEquals(storage, actualStorage);
        assertEquals(contractStorage, actualContractStorage);
    }

    @Test
    void getStakingInfoAndContext() {
        final var children = new MutableStateChildren();
        children.setStakingInfo(stakingInfo);
        children.setNetworkCtx(networkContext);

        subject = new StateView(null, children, null);

        final var actualStakingInfo = subject.stakingInfo();
        final var actualNetworkContext = subject.networkCtx();

        assertEquals(stakingInfo, actualStakingInfo);
        assertEquals(networkContext, actualNetworkContext);
    }

    @Test
    void getAliasesFromChildren() {
        final var children = new MutableStateChildren();
        final var aliases = new HashMap<ByteString, EntityNum>();
        aliases.put(ByteString.copyFromUtf8("test"), EntityNum.fromLong(10L));
        children.setAliases(aliases);
        children.setNetworkCtx(networkContext);

        subject = new StateView(null, children, null);

        final var actualAliases = subject.aliases();

        assertEquals(aliases, actualAliases);
    }

    @Test
    void returnsEmptyOptionalIfContractMissing() {
        given(contracts.get(any())).willReturn(null);

        assertTrue(
                subject.infoForContract(
                                cid, aliasManager, maxTokensFprAccountInfo, rewardCalculator)
                        .isEmpty());
    }

    @Test
    void handlesNullKey() {
        given(contracts.get(EntityNum.fromContractId(cid))).willReturn(contract);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        contract.setAccountKey(null);

        final var info =
                subject.infoForContract(
                                cid, aliasManager, maxTokensFprAccountInfo, rewardCalculator)
                        .get();

        assertFalse(info.hasAdminKey());
        mockedStatic.close();
    }

    @Test
    void handlesNullAutoRenewAccount() {
        given(contracts.get(EntityNum.fromContractId(cid))).willReturn(contract);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        mockedStatic = mockStatic(StateView.class);
        mockedStatic
                .when(() -> StateView.tokenRels(any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        contract.setAutoRenewAccount(null);

        final var info =
                subject.infoForContract(
                                cid, aliasManager, maxTokensFprAccountInfo, rewardCalculator)
                        .get();

        assertFalse(info.hasAutoRenewAccountId());
        mockedStatic.close();
    }

    @Test
    void getsAttrs() {
        given(attrs.get(target)).willReturn(metadata);

        final var stuff = subject.attrOf(target);

        assertEquals(metadata.toString(), stuff.get().toString());
    }

    @Test
    void getsBytecode() {
        given(bytecode.get(argThat((byte[] bytes) -> Arrays.equals(cidAddress, bytes))))
                .willReturn(expectedBytecode);

        final var actual = subject.bytecodeOf(EntityNum.fromContractId(cid));

        assertArrayEquals(expectedBytecode, actual.get());
    }

    @Test
    void getsContents() {
        given(contents.get(target)).willReturn(data);

        final var stuff = subject.contentsOf(target);

        assertTrue(stuff.isPresent());
        assertArrayEquals(data, stuff.get());
    }

    @Test
    void assemblesFileInfo() {
        given(attrs.get(target)).willReturn(metadata);
        given(contents.get(target)).willReturn(data);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var info = subject.infoForFile(target);

        assertTrue(info.isPresent());
        assertEquals(expected, info.get());
    }

    @Test
    void assemblesFileInfoForImmutable() {
        given(attrs.get(target)).willReturn(immutableMetadata);
        given(contents.get(target)).willReturn(data);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var info = subject.infoForFile(target);

        assertTrue(info.isPresent());
        assertEquals(expectedImmutable, info.get());
    }

    @Test
    void assemblesFileInfoForDeleted() {
        expected = expected.toBuilder().setDeleted(true).setSize(0).build();
        metadata.setDeleted(true);

        given(attrs.get(target)).willReturn(metadata);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var info = subject.infoForFile(target);

        assertTrue(info.isPresent());
        assertEquals(expected, info.get());
    }

    @Test
    void returnsEmptyForMissing() {
        final var info = subject.infoForFile(target);

        assertTrue(info.isEmpty());
    }

    @Test
    void returnsEmptyForMissingContent() {
        final var info = subject.contentsOf(target);

        assertTrue(info.isEmpty());
    }

    @Test
    void returnsEmptyForMissingAttr() {
        final var info = subject.attrOf(target);

        assertTrue(info.isEmpty());
    }

    @Test
    void getsSpecialFileContents() {
        FileID file150 = asFile("0.0.150");

        given(specialFiles.get(file150)).willReturn(data);
        given(specialFiles.contains(file150)).willReturn(true);

        final var stuff = subject.contentsOf(file150);

        assertTrue(Arrays.equals(data, stuff.get()));
    }

    @Test
    void specialFileMemoIsHexedHash() {
        FileID file150 = asFile("0.0.150");
        final var expectedMemo =
                CommonUtils.hex(CryptoFactory.getInstance().digestSync(data).getValue());

        given(specialFiles.get(file150)).willReturn(data);
        given(specialFiles.contains(file150)).willReturn(true);
        given(attrs.get(file150)).willReturn(metadata);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var info = subject.infoForFile(file150);
        assertTrue(info.isPresent());
        final var details = info.get();
        assertEquals(expectedMemo, details.getMemo());
    }

    @Test
    void rejectsMissingNft() {
        final var optionalNftInfo = subject.infoForNft(missingNftId);

        assertTrue(optionalNftInfo.isEmpty());
    }

    @Test
    void abortsNftGetWhenMissingTreasuryAsExpected() {
        tokens = mock(MerkleMap.class);
        targetNft.setOwner(MISSING_ENTITY_ID);

        final var optionalNftInfo = subject.infoForNft(targetNftId);

        assertTrue(optionalNftInfo.isEmpty());
    }

    @Test
    void getsSpenderAsExpected() {
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var optionalNftInfo = subject.infoForNft(targetNftId);

        assertTrue(optionalNftInfo.isPresent());
        final var info = optionalNftInfo.get();
        assertEquals(ledgerId, info.getLedgerId());
        assertEquals(targetNftId, info.getNftID());
        assertEquals(nftOwnerId, info.getAccountID());
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcAccountId(info.getSpenderId()));
        assertEquals(fromJava(nftCreation).toGrpc(), info.getCreationTime());
        assertArrayEquals(nftMeta, info.getMetadata().toByteArray());
    }

    @Test
    void interpolatesTreasuryIdOnNftGet() {
        targetNft.setOwner(MISSING_ENTITY_ID);

        final var token = new MerkleToken();
        token.setTreasury(EntityId.fromGrpcAccountId(tokenAccountId));
        given(tokens.get(targetNftKey.getHiOrderAsNum())).willReturn(token);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        final var optionalNftInfo = subject.infoForNft(targetNftId);

        final var info = optionalNftInfo.get();
        assertEquals(ledgerId, info.getLedgerId());
        assertEquals(targetNftId, info.getNftID());
        assertEquals(tokenAccountId, info.getAccountID());
        assertEquals(fromJava(nftCreation).toGrpc(), info.getCreationTime());
        assertArrayEquals(nftMeta, info.getMetadata().toByteArray());
    }

    @Test
    void getNftsAsExpected() {
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        targetNft.setSpender(EntityId.fromGrpcAccountId(nftSpenderId));

        final var optionalNftInfo = subject.infoForNft(targetNftId);

        assertTrue(optionalNftInfo.isPresent());
        final var info = optionalNftInfo.get();
        assertEquals(ledgerId, info.getLedgerId());
        assertEquals(targetNftId, info.getNftID());
        assertEquals(nftOwnerId, info.getAccountID());
        assertEquals(nftSpenderId, info.getSpenderId());
        assertEquals(fromJava(nftCreation).toGrpc(), info.getCreationTime());
        assertArrayEquals(nftMeta, info.getMetadata().toByteArray());
    }

    @Test
    void constructsBackingStores() {
        assertTrue(subject.asReadOnlyAccountStore() instanceof BackingAccounts);
        assertTrue(subject.asReadOnlyTokenStore() instanceof BackingTokens);
        assertTrue(subject.asReadOnlyNftStore() instanceof BackingNfts);
        assertTrue(subject.asReadOnlyAssociationStore() instanceof BackingTokenRels);
        assertEquals(tokens, ((BackingTokens) subject.asReadOnlyTokenStore()).getDelegate().get());
        assertEquals(
                contracts,
                ((BackingAccounts) subject.asReadOnlyAccountStore()).getDelegate().get());
        assertEquals(
                uniqueTokens, ((BackingNfts) subject.asReadOnlyNftStore()).getDelegate().get());
        assertEquals(
                tokenRels,
                ((BackingTokenRels) subject.asReadOnlyAssociationStore()).getDelegate().get());
    }

    @Test
    void tokenCustomFeesWorks() {
        given(tokens.get(tokenNum)).willReturn(token);
        assertEquals(grpcCustomFees, subject.tokenCustomFees(tokenId));
    }

    @Test
    void tokenCustomFeesFailsGracefully() {
        given(tokens.get(tokenNum)).willThrow(IllegalArgumentException.class);
        assertTrue(subject.tokenCustomFees(tokenId).isEmpty());
    }

    @Test
    void tokenCustomFeesMissingTokenIdReturnsEmptyList() {
        assertTrue(subject.tokenCustomFees(missingTokenId).isEmpty());
    }

    @Test
    void tokenCustomFeesWorksForMissing() {
        subject = new StateView(null, null, null);
        assertTrue(subject.tokenCustomFees(tokenId).isEmpty());
    }

    private final Instant nftCreation = Instant.ofEpochSecond(1_234_567L, 8);
    private final byte[] nftMeta = "abcdefgh".getBytes();
    private final NftID targetNftId =
            NftID.newBuilder().setTokenID(IdUtils.asToken("0.0.3")).setSerialNumber(4L).build();
    private final NftID missingNftId =
            NftID.newBuilder().setTokenID(IdUtils.asToken("0.0.9")).setSerialNumber(5L).build();
    private final EntityNumPair targetNftKey = EntityNumPair.fromLongs(3, 4);
    private final EntityNumPair treasuryNftKey = EntityNumPair.fromLongs(3, 5);
    private final UniqueTokenAdapter targetNft =
            UniqueTokenAdapter.wrap(
                    new MerkleUniqueToken(
                            EntityId.fromGrpcAccountId(nftOwnerId),
                            nftMeta,
                            fromJava(nftCreation)));
    private final UniqueTokenAdapter treasuryNft =
            UniqueTokenAdapter.wrap(
                    new MerkleUniqueToken(
                            EntityId.fromGrpcAccountId(treasuryOwnerId),
                            nftMeta,
                            fromJava(nftCreation)));

    private final CustomFeeBuilder builder = new CustomFeeBuilder(payerAccountId);
    private final CustomFee customFixedFeeInHbar = builder.withFixedFee(fixedHbar(100L));
    private final CustomFee customFixedFeeInHts = builder.withFixedFee(fixedHts(tokenId, 100L));
    private final CustomFee customFixedFeeSameToken = builder.withFixedFee(fixedHts(50L));
    private final CustomFee customFractionalFee =
            builder.withFractionalFee(
                    fractional(15L, 100L).setMinimumAmount(10L).setMaximumAmount(50L));
    private final List<CustomFee> grpcCustomFees =
            List.of(
                    customFixedFeeInHbar,
                    customFixedFeeInHts,
                    customFixedFeeSameToken,
                    customFractionalFee);
}
