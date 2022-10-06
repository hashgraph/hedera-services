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
package com.hedera.test.factories.scenarios;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.state.virtual.schedule.ScheduleVirtualValueTest.scheduleCreateTxnWith;
import static com.hedera.test.factories.accounts.MerkleAccountFactory.newAccount;
import static com.hedera.test.factories.accounts.MerkleAccountFactory.newContract;
import static com.hedera.test.factories.accounts.MockMMapFactory.newAccounts;
import static com.hedera.test.factories.keys.KeyTree.withRoot;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.keys.NodeFactory.threshold;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_NODE_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.MASTER_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.STAKING_FUND_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.TREASURY_PAYER_ID;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asSchedule;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.asTopic;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.keys.OverlappingKeyGenerator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.codec.DecoderException;

public interface TxnHandlingScenario {
    PlatformTxnAccessor platformTxn() throws Throwable;

    KeyFactory overlapFactory = new KeyFactory(OverlappingKeyGenerator.withDefaultOverlaps());

    default MerkleMap<EntityNum, MerkleAccount> accounts() throws Exception {
        return newAccounts()
                .withAccount(
                        FIRST_TOKEN_SENDER_ID,
                        newAccount().balance(10_000L).accountKeys(FIRST_TOKEN_SENDER_KT).get())
                .withAccount(
                        SECOND_TOKEN_SENDER_ID,
                        newAccount().balance(10_000L).accountKeys(SECOND_TOKEN_SENDER_KT).get())
                .withAccount(TOKEN_RECEIVER_ID, newAccount().balance(0L).get())
                .withAccount(
                        DEFAULT_NODE_ID,
                        newAccount().balance(0L).accountKeys(DEFAULT_PAYER_KT).get())
                .withAccount(
                        DEFAULT_PAYER_ID,
                        newAccount()
                                .balance(DEFAULT_PAYER_BALANCE)
                                .accountKeys(DEFAULT_PAYER_KT)
                                .get())
                .withAccount(STAKING_FUND_ID, newAccount().balance(0).accountKeys(EMPTY_KEY).get())
                .withAccount(
                        MASTER_PAYER_ID,
                        newAccount()
                                .balance(DEFAULT_PAYER_BALANCE)
                                .accountKeys(DEFAULT_PAYER_KT)
                                .get())
                .withAccount(
                        TREASURY_PAYER_ID,
                        newAccount()
                                .balance(DEFAULT_PAYER_BALANCE)
                                .accountKeys(DEFAULT_PAYER_KT)
                                .get())
                .withAccount(
                        NO_RECEIVER_SIG_ID,
                        newAccount()
                                .receiverSigRequired(false)
                                .balance(DEFAULT_BALANCE)
                                .accountKeys(NO_RECEIVER_SIG_KT)
                                .get())
                .withAccount(
                        RECEIVER_SIG_ID,
                        newAccount()
                                .receiverSigRequired(true)
                                .balance(DEFAULT_BALANCE)
                                .accountKeys(RECEIVER_SIG_KT)
                                .get())
                .withAccount(
                        SYS_ACCOUNT_ID,
                        newAccount().balance(DEFAULT_BALANCE).accountKeys(SYS_ACCOUNT_KT).get())
                .withAccount(
                        MISC_ACCOUNT_ID,
                        newAccount().balance(DEFAULT_BALANCE).accountKeys(MISC_ACCOUNT_KT).get())
                .withAccount(
                        CUSTOM_PAYER_ACCOUNT_ID,
                        newAccount()
                                .balance(DEFAULT_BALANCE)
                                .accountKeys(CUSTOM_PAYER_ACCOUNT_KT)
                                .get())
                .withAccount(
                        OWNER_ACCOUNT_ID,
                        newAccount()
                                .balance(DEFAULT_BALANCE)
                                .cryptoAllowances(cryptoAllowances)
                                .fungibleTokenAllowances(fungibleTokenAllowances)
                                .explicitNftAllowances(nftTokenAllowances)
                                .accountKeys(OWNER_ACCOUNT_KT)
                                .get())
                .withAccount(
                        DELEGATING_SPENDER_ID,
                        newAccount()
                                .balance(DEFAULT_BALANCE)
                                .cryptoAllowances(cryptoAllowances)
                                .fungibleTokenAllowances(fungibleTokenAllowances)
                                .explicitNftAllowances(nftTokenAllowances)
                                .accountKeys(DELEGATING_SPENDER_KT)
                                .get())
                .withAccount(
                        COMPLEX_KEY_ACCOUNT_ID,
                        newAccount()
                                .balance(DEFAULT_BALANCE)
                                .accountKeys(COMPLEX_KEY_ACCOUNT_KT)
                                .get())
                .withAccount(
                        TOKEN_TREASURY_ID,
                        newAccount().balance(DEFAULT_BALANCE).accountKeys(TOKEN_TREASURY_KT).get())
                .withAccount(
                        DILIGENT_SIGNING_PAYER_ID,
                        newAccount()
                                .balance(DEFAULT_BALANCE)
                                .accountKeys(DILIGENT_SIGNING_PAYER_KT)
                                .get())
                .withAccount(
                        FROM_OVERLAP_PAYER_ID,
                        newAccount()
                                .balance(DEFAULT_BALANCE)
                                .keyFactory(overlapFactory)
                                .accountKeys(FROM_OVERLAP_PAYER_KT)
                                .get())
                .withContract(
                        MISC_RECIEVER_SIG_CONTRACT_ID,
                        newContract()
                                .receiverSigRequired(true)
                                .balance(DEFAULT_BALANCE)
                                .accountKeys(DILIGENT_SIGNING_PAYER_KT)
                                .get())
                .withContract(IMMUTABLE_CONTRACT_ID, newContract().balance(DEFAULT_BALANCE).get())
                .withContract(
                        MISC_CONTRACT_ID,
                        newContract().balance(DEFAULT_BALANCE).accountKeys(MISC_ADMIN_KT).get())
                .get();
    }

    default HederaFs hfs() throws Exception {
        HederaFs hfs = mock(HederaFs.class);
        given(hfs.exists(MISC_FILE)).willReturn(true);
        given(hfs.exists(SYS_FILE)).willReturn(true);
        given(hfs.getattr(MISC_FILE)).willReturn(convert(MISC_FILE_INFO));
        given(hfs.getattr(SYS_FILE)).willReturn(convert(SYS_FILE_INFO));
        given(hfs.exists(IMMUTABLE_FILE)).willReturn(true);
        given(hfs.getattr(IMMUTABLE_FILE)).willReturn(convert(IMMUTABLE_FILE_INFO));
        return hfs;
    }

    default MerkleMap<EntityNum, MerkleTopic> topics() {
        var topics = (MerkleMap<EntityNum, MerkleTopic>) mock(MerkleMap.class);
        given(topics.get(EXISTING_TOPIC)).willReturn(new MerkleTopic());
        return topics;
    }

    private static HFileMeta convert(final FileGetInfoResponse.FileInfo fi)
            throws DecoderException {
        return new HFileMeta(
                fi.getDeleted(),
                JKey.mapKey(Key.newBuilder().setKeyList(fi.getKeys()).build()),
                fi.getExpirationTime().getSeconds());
    }

    default TokenStore tokenStore() {
        var tokenStore = mock(TokenStore.class);

        var adminKey = TOKEN_ADMIN_KT.asJKeyUnchecked();
        var optionalKycKey = TOKEN_KYC_KT.asJKeyUnchecked();
        var optionalWipeKey = TOKEN_WIPE_KT.asJKeyUnchecked();
        var optionalSupplyKey = TOKEN_SUPPLY_KT.asJKeyUnchecked();
        var optionalFreezeKey = TOKEN_FREEZE_KT.asJKeyUnchecked();
        var optionalFeeScheduleKey = TOKEN_FEE_SCHEDULE_KT.asJKeyUnchecked();
        var optionalPauseKey = TOKEN_PAUSE_KT.asJKeyUnchecked();

        var immutableToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "ImmutableToken",
                        "ImmutableTokenName",
                        false,
                        false,
                        new EntityId(1, 2, 3));
        given(tokenStore.resolve(KNOWN_TOKEN_IMMUTABLE)).willReturn(KNOWN_TOKEN_IMMUTABLE);
        given(tokenStore.get(KNOWN_TOKEN_IMMUTABLE)).willReturn(immutableToken);

        var vanillaToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "VanillaToken",
                        "TOKENNAME",
                        false,
                        false,
                        new EntityId(1, 2, 3));
        vanillaToken.setAdminKey(adminKey);
        given(tokenStore.resolve(KNOWN_TOKEN_NO_SPECIAL_KEYS))
                .willReturn(KNOWN_TOKEN_NO_SPECIAL_KEYS);
        given(tokenStore.get(KNOWN_TOKEN_NO_SPECIAL_KEYS)).willReturn(vanillaToken);

        var pausedToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "PausedToken",
                        "PAUSEDTOKEN",
                        false,
                        false,
                        new EntityId(1, 2, 4));
        pausedToken.setAdminKey(adminKey);
        pausedToken.setPauseKey(optionalPauseKey);
        given(tokenStore.resolve(KNOWN_TOKEN_WITH_PAUSE)).willReturn(KNOWN_TOKEN_WITH_PAUSE);
        given(tokenStore.get(KNOWN_TOKEN_WITH_PAUSE)).willReturn(pausedToken);

        var frozenToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "FrozenToken",
                        "FRZNTKN",
                        true,
                        false,
                        new EntityId(1, 2, 4));
        frozenToken.setAdminKey(adminKey);
        frozenToken.setFreezeKey(optionalFreezeKey);
        given(tokenStore.resolve(KNOWN_TOKEN_WITH_FREEZE)).willReturn(KNOWN_TOKEN_WITH_FREEZE);
        given(tokenStore.get(KNOWN_TOKEN_WITH_FREEZE)).willReturn(frozenToken);

        var kycToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "KycToken",
                        "KYCTOKENNAME",
                        false,
                        true,
                        new EntityId(1, 2, 4));
        kycToken.setAdminKey(adminKey);
        kycToken.setKycKey(optionalKycKey);
        given(tokenStore.resolve(KNOWN_TOKEN_WITH_KYC)).willReturn(KNOWN_TOKEN_WITH_KYC);
        given(tokenStore.get(KNOWN_TOKEN_WITH_KYC)).willReturn(kycToken);

        var feeScheduleToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "FsToken",
                        "FEE_SCHEDULETOKENNAME",
                        false,
                        true,
                        new EntityId(1, 2, 4));
        feeScheduleToken.setFeeScheduleKey(optionalFeeScheduleKey);
        given(tokenStore.resolve(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY))
                .willReturn(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY);
        given(tokenStore.get(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)).willReturn(feeScheduleToken);

        var royaltyFeeWithFallbackToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "ZPHYR",
                        "West Wind Art",
                        false,
                        true,
                        EntityId.fromGrpcAccountId(MISC_ACCOUNT));
        royaltyFeeWithFallbackToken.setFeeScheduleKey(optionalFeeScheduleKey);
        royaltyFeeWithFallbackToken.setTokenType(NON_FUNGIBLE_UNIQUE);
        royaltyFeeWithFallbackToken.setFeeSchedule(
                List.of(
                        FcCustomFee.royaltyFee(
                                1, 2, new FixedFeeSpec(1, null), new EntityId(1, 2, 5), false)));
        given(tokenStore.resolve(KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK))
                .willReturn(KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK);
        given(tokenStore.get(KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK))
                .willReturn(royaltyFeeWithFallbackToken);

        var supplyToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "SupplyToken",
                        "SUPPLYTOKENNAME",
                        false,
                        false,
                        new EntityId(1, 2, 4));
        supplyToken.setAdminKey(adminKey);
        supplyToken.setSupplyKey(optionalSupplyKey);
        given(tokenStore.resolve(KNOWN_TOKEN_WITH_SUPPLY)).willReturn(KNOWN_TOKEN_WITH_SUPPLY);
        given(tokenStore.get(KNOWN_TOKEN_WITH_SUPPLY)).willReturn(supplyToken);

        var wipeToken =
                new MerkleToken(
                        Long.MAX_VALUE,
                        100,
                        1,
                        "WipeToken",
                        "WIPETOKENNAME",
                        false,
                        false,
                        new EntityId(1, 2, 4));
        wipeToken.setAdminKey(adminKey);
        wipeToken.setWipeKey(optionalWipeKey);
        given(tokenStore.resolve(KNOWN_TOKEN_WITH_WIPE)).willReturn(KNOWN_TOKEN_WITH_WIPE);
        given(tokenStore.get(KNOWN_TOKEN_WITH_WIPE)).willReturn(wipeToken);

        given(tokenStore.resolve(MISSING_TOKEN)).willReturn(TokenStore.MISSING_TOKEN);

        return tokenStore;
    }

    default byte[] extantSchedulingBodyBytes() throws Throwable {
        return scheduleCreateTxnWith(
                        Key.getDefaultInstance(),
                        "",
                        MISC_ACCOUNT,
                        MISC_ACCOUNT,
                        MiscUtils.asTimestamp(Instant.ofEpochSecond(1L)))
                .toByteArray();
    }

    default ScheduleStore scheduleStore() {
        var scheduleStore = mock(ScheduleStore.class);

        given(scheduleStore.resolve(KNOWN_SCHEDULE_IMMUTABLE)).willReturn(KNOWN_SCHEDULE_IMMUTABLE);
        given(scheduleStore.get(KNOWN_SCHEDULE_IMMUTABLE))
                .willAnswer(
                        inv -> {
                            var entity =
                                    ScheduleVirtualValue.from(extantSchedulingBodyBytes(), 1801L);
                            entity.setPayer(null);
                            return entity;
                        });

        given(scheduleStore.resolve(KNOWN_SCHEDULE_WITH_ADMIN))
                .willReturn(KNOWN_SCHEDULE_WITH_ADMIN);
        given(scheduleStore.get(KNOWN_SCHEDULE_WITH_ADMIN))
                .willAnswer(
                        inv -> {
                            var adminKey = SCHEDULE_ADMIN_KT.asJKeyUnchecked();
                            var entity =
                                    ScheduleVirtualValue.from(extantSchedulingBodyBytes(), 1801L);
                            entity.setPayer(null);
                            entity.setAdminKey(adminKey);
                            return entity;
                        });

        given(scheduleStore.resolve(KNOWN_SCHEDULE_WITH_EXPLICIT_PAYER))
                .willReturn(KNOWN_SCHEDULE_WITH_EXPLICIT_PAYER);
        given(scheduleStore.get(KNOWN_SCHEDULE_WITH_EXPLICIT_PAYER))
                .willAnswer(
                        inv -> {
                            var entity =
                                    ScheduleVirtualValue.from(extantSchedulingBodyBytes(), 1801L);
                            entity.setPayer(EntityId.fromGrpcAccountId(DILIGENT_SIGNING_PAYER));
                            return entity;
                        });

        given(scheduleStore.resolve(KNOWN_SCHEDULE_WITH_EXPLICIT_PAYER_SELF))
                .willReturn(KNOWN_SCHEDULE_WITH_EXPLICIT_PAYER_SELF);
        given(scheduleStore.get(KNOWN_SCHEDULE_WITH_EXPLICIT_PAYER_SELF))
                .willAnswer(
                        inv -> {
                            var entity =
                                    ScheduleVirtualValue.from(extantSchedulingBodyBytes(), 1801L);
                            entity.setPayer(EntityId.fromGrpcAccountId(DEFAULT_PAYER));
                            return entity;
                        });

        given(scheduleStore.resolve(KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER))
                .willReturn(KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER);
        given(scheduleStore.get(KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER))
                .willAnswer(
                        inv -> {
                            var entity =
                                    ScheduleVirtualValue.from(extantSchedulingBodyBytes(), 1801L);
                            entity.setPayer(EntityId.fromGrpcAccountId(MISSING_ACCOUNT));
                            return entity;
                        });

        given(scheduleStore.resolve(UNKNOWN_SCHEDULE)).willReturn(ScheduleStore.MISSING_SCHEDULE);

        return scheduleStore;
    }

    String MISSING_ACCOUNT_ID = "0.0.321321";
    AccountID MISSING_ACCOUNT = asAccount(MISSING_ACCOUNT_ID);

    String CURRENTLY_UNUSED_ALIAS = "currentlyUnusedAlias";

    String NO_RECEIVER_SIG_ID = "0.0.1337";
    String NO_RECEIVER_SIG_ALIAS = "noReceiverSigReqAlias";
    AccountID NO_RECEIVER_SIG = asAccount(NO_RECEIVER_SIG_ID);
    KeyTree NO_RECEIVER_SIG_KT = withRoot(ed25519());

    String RECEIVER_SIG_ID = "0.0.1338";
    String RECEIVER_SIG_ALIAS = "receiverSigReqAlias";
    AccountID RECEIVER_SIG = asAccount(RECEIVER_SIG_ID);
    KeyTree RECEIVER_SIG_KT = withRoot(list(ed25519(), threshold(1, ed25519(), ed25519())));

    String MISC_ACCOUNT_ID = "0.0.1339";
    AccountID MISC_ACCOUNT = asAccount(MISC_ACCOUNT_ID);
    KeyTree MISC_ACCOUNT_KT = withRoot(ed25519());

    String CUSTOM_PAYER_ACCOUNT_ID = "0.0.1216";
    AccountID CUSTOM_PAYER_ACCOUNT = asAccount(CUSTOM_PAYER_ACCOUNT_ID);
    KeyTree CUSTOM_PAYER_ACCOUNT_KT = withRoot(ed25519());

    String OWNER_ACCOUNT_ID = "0.0.1439";
    AccountID OWNER_ACCOUNT = asAccount(OWNER_ACCOUNT_ID);
    KeyTree OWNER_ACCOUNT_KT = withRoot(ed25519());

    String DELEGATING_SPENDER_ID = "0.0.1539";
    AccountID DELEGATING_SPENDER = asAccount(DELEGATING_SPENDER_ID);
    KeyTree DELEGATING_SPENDER_KT = withRoot(ed25519());

    String SYS_ACCOUNT_ID = "0.0.666";

    String DILIGENT_SIGNING_PAYER_ID = "0.0.1340";
    AccountID DILIGENT_SIGNING_PAYER = asAccount(DILIGENT_SIGNING_PAYER_ID);
    KeyTree DILIGENT_SIGNING_PAYER_KT =
            withRoot(threshold(2, ed25519(true), ed25519(true), ed25519(false)));

    String TOKEN_TREASURY_ID = "0.0.1341";
    AccountID TOKEN_TREASURY = asAccount(TOKEN_TREASURY_ID);
    KeyTree TOKEN_TREASURY_KT =
            withRoot(threshold(2, ed25519(false), ed25519(true), ed25519(false)));

    String COMPLEX_KEY_ACCOUNT_ID = "0.0.1342";
    AccountID COMPLEX_KEY_ACCOUNT = asAccount(COMPLEX_KEY_ACCOUNT_ID);
    KeyTree COMPLEX_KEY_ACCOUNT_KT =
            withRoot(
                    list(
                            ed25519(),
                            threshold(1, list(list(ed25519(), ed25519()), ed25519()), ed25519()),
                            ed25519(),
                            list(threshold(2, ed25519(), ed25519(), ed25519()))));

    String FROM_OVERLAP_PAYER_ID = "0.0.1343";
    KeyTree FROM_OVERLAP_PAYER_KT =
            withRoot(threshold(2, ed25519(true), ed25519(true), ed25519(false)));

    KeyTree NEW_ACCOUNT_KT = withRoot(list(ed25519(), threshold(1, ed25519(), ed25519())));
    KeyTree SYS_ACCOUNT_KT = withRoot(list(ed25519(), threshold(1, ed25519(), ed25519())));
    KeyTree LONG_THRESHOLD_KT = withRoot(threshold(1, ed25519(), ed25519(), ed25519(), ed25519()));

    String MISSING_FILE_ID = "1.2.3";

    String SYS_FILE_ID = "0.0.111";
    FileID SYS_FILE = asFile(SYS_FILE_ID);
    KeyTree SYS_FILE_WACL_KT = withRoot(list(ed25519()));
    FileGetInfoResponse.FileInfo SYS_FILE_INFO =
            FileGetInfoResponse.FileInfo.newBuilder()
                    .setKeys(SYS_FILE_WACL_KT.asKey().getKeyList())
                    .setFileID(SYS_FILE)
                    .build();

    String MISC_FILE_ID = "0.0.2337";
    FileID MISC_FILE = asFile(MISC_FILE_ID);
    KeyTree MISC_FILE_WACL_KT = withRoot(list(ed25519()));
    FileGetInfoResponse.FileInfo MISC_FILE_INFO =
            FileGetInfoResponse.FileInfo.newBuilder()
                    .setKeys(MISC_FILE_WACL_KT.asKey().getKeyList())
                    .setFileID(MISC_FILE)
                    .build();

    String IMMUTABLE_FILE_ID = "0.0.2338";
    FileID IMMUTABLE_FILE = asFile(IMMUTABLE_FILE_ID);
    FileGetInfoResponse.FileInfo IMMUTABLE_FILE_INFO =
            FileGetInfoResponse.FileInfo.newBuilder().setFileID(IMMUTABLE_FILE).build();

    KeyTree SIMPLE_NEW_WACL_KT = withRoot(list(ed25519()));

    String MISSING_CONTRACT_ID = "3.6.9";
    ContractID MISSING_CONTRACT = asContract(MISSING_CONTRACT_ID);

    String MISC_RECIEVER_SIG_CONTRACT_ID = "0.0.7337";
    ContractID MISC_RECIEVER_SIG_CONTRACT = asContract(MISC_RECIEVER_SIG_CONTRACT_ID);

    String IMMUTABLE_CONTRACT_ID = "0.0.9339";

    String MISC_CONTRACT_ID = "0.0.3337";
    KeyTree MISC_ADMIN_KT = withRoot(ed25519());

    KeyTree SIMPLE_NEW_ADMIN_KT = withRoot(ed25519());

    Long DEFAULT_BALANCE = 1_000L;
    Long DEFAULT_PAYER_BALANCE = 1_000_000_000_000L;

    String DEFAULT_MEMO = "This is something else.";
    Duration DEFAULT_PERIOD = Duration.newBuilder().setSeconds(1_000L).build();
    Timestamp DEFAULT_EXPIRY =
            Timestamp.newBuilder()
                    .setSeconds(System.currentTimeMillis() / 1_000L + 86_400L)
                    .build();

    String EXISTING_TOPIC_ID = "0.0.7890";
    TopicID EXISTING_TOPIC = asTopic(EXISTING_TOPIC_ID);

    String MISSING_TOPIC_ID = "0.0.12121";
    TopicID MISSING_TOPIC = asTopic(MISSING_TOPIC_ID);

    String KNOWN_TOKEN_IMMUTABLE_ID = "0.0.534";
    TokenID KNOWN_TOKEN_IMMUTABLE = asToken(KNOWN_TOKEN_IMMUTABLE_ID);
    String KNOWN_TOKEN_NO_SPECIAL_KEYS_ID = "0.0.535";
    TokenID KNOWN_TOKEN_NO_SPECIAL_KEYS = asToken(KNOWN_TOKEN_NO_SPECIAL_KEYS_ID);
    String KNOWN_TOKEN_WITH_FREEZE_ID = "0.0.777";
    TokenID KNOWN_TOKEN_WITH_FREEZE = asToken(KNOWN_TOKEN_WITH_FREEZE_ID);
    String KNOWN_TOKEN_WITH_KYC_ID = "0.0.776";
    TokenID KNOWN_TOKEN_WITH_KYC = asToken(KNOWN_TOKEN_WITH_KYC_ID);
    String KNOWN_TOKEN_WITH_SUPPLY_ID = "0.0.775";
    TokenID KNOWN_TOKEN_WITH_SUPPLY = asToken(KNOWN_TOKEN_WITH_SUPPLY_ID);
    String KNOWN_TOKEN_WITH_WIPE_ID = "0.0.774";
    TokenID KNOWN_TOKEN_WITH_WIPE = asToken(KNOWN_TOKEN_WITH_WIPE_ID);
    String KNOWN_TOKEN_WITH_FEE_SCHEDULE_ID = "0.0.779";
    TokenID KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY = asToken(KNOWN_TOKEN_WITH_FEE_SCHEDULE_ID);
    String KNOWN_TOKEN_WITH_ROYALTY_FEE_ID = "0.0.77977";
    TokenID KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK = asToken(KNOWN_TOKEN_WITH_ROYALTY_FEE_ID);
    String KNOWN_TOKEN_WITH_PAUSE_ID = "0.0.780";
    TokenID KNOWN_TOKEN_WITH_PAUSE = asToken(KNOWN_TOKEN_WITH_PAUSE_ID);

    String FIRST_TOKEN_SENDER_ID = "0.0.888";
    AccountID FIRST_TOKEN_SENDER = asAccount(FIRST_TOKEN_SENDER_ID);
    ByteString FIRST_TOKEN_SENDER_LITERAL_ALIAS = ByteString.copyFromUtf8("firstTokenSender");
    AccountID FIRST_TOKEN_SENDER_ALIAS =
            AccountID.newBuilder().setAlias(FIRST_TOKEN_SENDER_LITERAL_ALIAS).build();
    String SECOND_TOKEN_SENDER_ID = "0.0.999";
    AccountID SECOND_TOKEN_SENDER = asAccount(SECOND_TOKEN_SENDER_ID);
    String TOKEN_RECEIVER_ID = "0.0.1111";
    AccountID TOKEN_RECEIVER = asAccount(TOKEN_RECEIVER_ID);

    NftID KNOWN_TOKEN_NFT =
            NftID.newBuilder().setTokenID(KNOWN_TOKEN_WITH_WIPE).setSerialNumber(1L).build();
    NftID ROYALTY_TOKEN_NFT =
            NftID.newBuilder()
                    .setTokenID(KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK)
                    .setSerialNumber(1L)
                    .build();

    String UNKNOWN_TOKEN_ID = "0.0.666";
    TokenID MISSING_TOKEN = asToken(UNKNOWN_TOKEN_ID);
    NftID MISSING_TOKEN_NFT =
            NftID.newBuilder().setTokenID(MISSING_TOKEN).setSerialNumber(1L).build();

    KeyTree FIRST_TOKEN_SENDER_KT = withRoot(ed25519());
    KeyTree SECOND_TOKEN_SENDER_KT = withRoot(ed25519());
    KeyTree TOKEN_ADMIN_KT = withRoot(ed25519());
    KeyTree TOKEN_FEE_SCHEDULE_KT = withRoot(ed25519());
    KeyTree TOKEN_FREEZE_KT = withRoot(ed25519());
    KeyTree TOKEN_SUPPLY_KT = withRoot(ed25519());
    KeyTree TOKEN_WIPE_KT = withRoot(ed25519());
    KeyTree TOKEN_KYC_KT = withRoot(ed25519());
    KeyTree TOKEN_PAUSE_KT = withRoot(ed25519());
    KeyTree TOKEN_REPLACE_KT = withRoot(ed25519());
    KeyTree MISC_TOPIC_SUBMIT_KT = withRoot(ed25519());
    KeyTree MISC_TOPIC_ADMIN_KT = withRoot(ed25519());
    KeyTree UPDATE_TOPIC_ADMIN_KT = withRoot(ed25519());

    String KNOWN_SCHEDULE_IMMUTABLE_ID = "0.0.789";
    ScheduleID KNOWN_SCHEDULE_IMMUTABLE = asSchedule(KNOWN_SCHEDULE_IMMUTABLE_ID);

    String KNOWN_SCHEDULE_WITH_ADMIN_ID = "0.0.456";
    ScheduleID KNOWN_SCHEDULE_WITH_ADMIN = asSchedule(KNOWN_SCHEDULE_WITH_ADMIN_ID);

    String KNOWN_SCHEDULE_WITH_PAYER_ID = "0.0.456456";
    ScheduleID KNOWN_SCHEDULE_WITH_EXPLICIT_PAYER = asSchedule(KNOWN_SCHEDULE_WITH_PAYER_ID);

    String KNOWN_SCHEDULE_WITH_PAYER_SELF_ID = "0.0.416125";
    ScheduleID KNOWN_SCHEDULE_WITH_EXPLICIT_PAYER_SELF =
            asSchedule(KNOWN_SCHEDULE_WITH_PAYER_SELF_ID);

    String KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER_ID = "0.0.654654";
    ScheduleID KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER =
            asSchedule(KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER_ID);

    String UNKNOWN_SCHEDULE_ID = "0.0.123";
    ScheduleID UNKNOWN_SCHEDULE = asSchedule(UNKNOWN_SCHEDULE_ID);

    KeyTree SCHEDULE_ADMIN_KT = withRoot(ed25519());

    TreeMap<EntityNum, Long> cryptoAllowances =
            new TreeMap<>() {
                {
                    put(EntityNum.fromAccountId(DEFAULT_PAYER), 500L);
                }
            };

    List<CryptoAllowance> cryptoAllowanceList =
            List.of(
                    CryptoAllowance.newBuilder()
                            .setOwner(OWNER_ACCOUNT)
                            .setSpender(DEFAULT_PAYER)
                            .setAmount(500L)
                            .build());

    List<CryptoAllowance> cryptoSelfOwnerAllowanceList =
            List.of(
                    CryptoAllowance.newBuilder()
                            .setOwner(DEFAULT_PAYER)
                            .setSpender(OWNER_ACCOUNT)
                            .setAmount(500L)
                            .build());

    List<CryptoAllowance> cryptoAllowanceMissingOwnerList =
            List.of(
                    CryptoAllowance.newBuilder()
                            .setOwner(MISSING_ACCOUNT)
                            .setSpender(DEFAULT_PAYER)
                            .setAmount(500L)
                            .build());

    List<CryptoAllowance> cryptoAllowanceNoOwnerList =
            List.of(CryptoAllowance.newBuilder().setSpender(DEFAULT_PAYER).setAmount(500L).build());

    TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances =
            new TreeMap<>() {
                {
                    put(
                            FcTokenAllowanceId.from(
                                    EntityNum.fromTokenId(KNOWN_TOKEN_NO_SPECIAL_KEYS),
                                    EntityNum.fromAccountId(DEFAULT_PAYER)),
                            10_000L);
                }
            };

    List<TokenAllowance> tokenAllowanceList =
            List.of(
                    TokenAllowance.newBuilder()
                            .setTokenId(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                            .setOwner(OWNER_ACCOUNT)
                            .setSpender(DEFAULT_PAYER)
                            .setAmount(10_000L)
                            .build());

    List<TokenAllowance> tokenSelfOwnerAllowanceList =
            List.of(
                    TokenAllowance.newBuilder()
                            .setTokenId(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                            .setOwner(DEFAULT_PAYER)
                            .setSpender(OWNER_ACCOUNT)
                            .setAmount(10_000L)
                            .build());

    List<TokenAllowance> tokenAllowanceMissingOwnerList =
            List.of(
                    TokenAllowance.newBuilder()
                            .setTokenId(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                            .setOwner(MISSING_ACCOUNT)
                            .setSpender(DEFAULT_PAYER)
                            .setAmount(10_000L)
                            .build());

    TreeSet<FcTokenAllowanceId> nftTokenAllowances =
            new TreeSet<>() {
                {
                    add(
                            FcTokenAllowanceId.from(
                                    EntityNum.fromTokenId(KNOWN_TOKEN_WITH_WIPE),
                                    EntityNum.fromAccountId(DEFAULT_PAYER)));
                }
            };

    List<NftAllowance> nftAllowanceList =
            List.of(
                    NftAllowance.newBuilder()
                            .setOwner(OWNER_ACCOUNT)
                            .setTokenId(KNOWN_TOKEN_WITH_WIPE)
                            .setSpender(DEFAULT_PAYER)
                            .setApprovedForAll(BoolValue.of(true))
                            .build());

    List<NftAllowance> nftSelfOwnerAllowanceList =
            List.of(
                    NftAllowance.newBuilder()
                            .setOwner(DEFAULT_PAYER)
                            .setTokenId(KNOWN_TOKEN_WITH_WIPE)
                            .setSpender(OWNER_ACCOUNT)
                            .setApprovedForAll(BoolValue.of(true))
                            .build());

    List<NftAllowance> nftAllowanceMissingOwnerList =
            List.of(
                    NftAllowance.newBuilder()
                            .setOwner(MISSING_ACCOUNT)
                            .setTokenId(KNOWN_TOKEN_WITH_WIPE)
                            .setSpender(DEFAULT_PAYER)
                            .setApprovedForAll(BoolValue.of(true))
                            .build());

    List<NftRemoveAllowance> nftDeleteAllowanceList =
            List.of(
                    NftRemoveAllowance.newBuilder()
                            .setOwner(OWNER_ACCOUNT)
                            .setTokenId(KNOWN_TOKEN_WITH_WIPE)
                            .addAllSerialNumbers(List.of(1L))
                            .build());

    List<NftRemoveAllowance> nftDeleteAllowanceListSelf =
            List.of(
                    NftRemoveAllowance.newBuilder()
                            .setOwner(DEFAULT_PAYER)
                            .setTokenId(KNOWN_TOKEN_WITH_WIPE)
                            .addAllSerialNumbers(List.of(1L))
                            .build());

    List<NftRemoveAllowance> nftDeleteAllowanceMissingOwnerList =
            List.of(
                    NftRemoveAllowance.newBuilder()
                            .setOwner(MISSING_ACCOUNT)
                            .setTokenId(KNOWN_TOKEN_WITH_WIPE)
                            .build());

    List<NftAllowance> delegatingNftAllowanceList =
            List.of(
                    NftAllowance.newBuilder()
                            .setOwner(OWNER_ACCOUNT)
                            .setTokenId(KNOWN_TOKEN_WITH_WIPE)
                            .setSpender(DEFAULT_PAYER)
                            .setDelegatingSpender(DELEGATING_SPENDER)
                            .setApprovedForAll(BoolValue.of(false))
                            .addAllSerialNumbers(List.of(1L))
                            .build());

    List<NftAllowance> delegatingNftAllowanceMissingOwnerList =
            List.of(
                    NftAllowance.newBuilder()
                            .setOwner(OWNER_ACCOUNT)
                            .setTokenId(KNOWN_TOKEN_WITH_WIPE)
                            .setSpender(DEFAULT_PAYER)
                            .setDelegatingSpender(MISSING_ACCOUNT)
                            .setApprovedForAll(BoolValue.of(false))
                            .addAllSerialNumbers(List.of(1L))
                            .build());
}
