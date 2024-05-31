/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.schemas;

import static com.hedera.node.app.service.mono.state.migration.ContractStateMigrator.bytesFromInts;
import static com.hedera.node.app.service.mono.state.migration.TokenStateTranslator.merkleTokenFromToken;
import static com.hedera.node.app.service.mono.state.virtual.KeyPackingUtils.asPackedInts;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_COMPARATOR;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.DEFAULT_NUM_SYSTEM_ACCOUNTS;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.EVM_ADDRESSES;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.EVM_ADDRESS_0;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.EVM_ADDRESS_1;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.EVM_ADDRESS_2;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.EVM_ADDRESS_3;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.EVM_ADDRESS_4;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.EVM_ADDRESS_5;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.EXPECTED_TREASURY_TINYBARS_BALANCE;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.NUM_RESERVED_SYSTEM_ENTITIES;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.TREASURY_ACCOUNT_NUM;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.buildConfig;
import static com.hedera.node.app.service.token.impl.schemas.SyntheticAccountsData.configBuilder;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.NFTS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKEN_RELS_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_1;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_2;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_3;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_4;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_8;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.STAKING_INFO_1;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.STAKING_INFO_2;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.STAKING_INFO_3;
import static com.swirlds.common.io.utility.LegacyTemporaryFileBuilder.buildTemporaryDirectory;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.ids.schemas.V0490EntityIdSchema;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.AccountStateTranslator;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.OnDiskTokenRelValueSerializer;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValueSerializer;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.info.FakeNetworkInfo;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.state.EmptyReadableStates;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.app.workflows.handle.record.MigrationContextImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.config.api.Configuration;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import com.swirlds.platform.test.fixtures.state.MapWritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
final class V0490TokenSchemaTest {

    private static final long BEGINNING_ENTITY_ID = 3000;

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    private static final AccountID[] ACCT_IDS = new AccountID[1001];

    static {
        IntStream.rangeClosed(1, 1000).forEach(i -> ACCT_IDS[i] = asAccount(i));
    }

    private static final long[] NON_CONTRACT_RESERVED_NUMS =
            V0490TokenSchema.nonContractSystemNums(NUM_RESERVED_SYSTEM_ENTITIES);

    static {
        // Precondition check
        assertThat(NON_CONTRACT_RESERVED_NUMS).hasSize(501);
    }

    @Mock
    private GenesisRecordsBuilder genesisRecordsBuilder;

    @Captor
    private ArgumentCaptor<SortedSet<Account>> blocklistMapCaptor;

    private MapWritableKVState<AccountID, Account> accounts;
    private WritableStates newStates;
    private Configuration config;
    private NetworkInfo networkInfo;
    private WritableEntityIdStore entityIdStore;

    final long minStake = 100L;
    final long maxStake = 800L;
    final long stakeToReward1 = 700L;
    final long stakeToNotReward1 = 300L;
    final long stakedRewardStart1 = 1_000L;
    final long unclaimedStakedRewardStart1 = stakedRewardStart1 / 10;
    final long stake1 = 2_000L;
    final long[] rewardSumHistory1 = new long[] {8, 7, 2};

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    V0490TokenSchema initialModServiceTokenSchema;

    @BeforeEach
    void setUp() {
        accounts = MapWritableKVState.<AccountID, Account>builder(ACCOUNTS_KEY).build();

        newStates = newStatesInstance(
                accounts,
                MapWritableKVState.<Bytes, AccountID>builder(ALIASES_KEY).build(),
                newWritableEntityIdState());

        entityIdStore = new WritableEntityIdStore(newStates);

        networkInfo = new FakeNetworkInfo();

        config = buildConfig(DEFAULT_NUM_SYSTEM_ACCOUNTS, true);
    }

    @CsvSource({
        "abababababababababababababababababababababababababababababababab",
        "abcdef",
        "0123456789",
    })
    @ParameterizedTest
    void keysAreMigratedIdentically(@NonNull final String hexedKey) {
        var unhexedKey = unhex(hexedKey);
        if (unhexedKey.length != 32) {
            final var leftPadded = new byte[32];
            System.arraycopy(unhexedKey, 0, leftPadded, 32 - unhexedKey.length, unhexedKey.length);
            unhexedKey = leftPadded;
        }
        final var onDiskAccount = new OnDiskAccount();
        onDiskAccount.setFirstStorageKey(asPackedInts(unhexedKey));
        final var account = AccountStateTranslator.accountFromOnDiskAccount(onDiskAccount);
        final var expectedKey = bytesFromInts(onDiskAccount.getFirstStorageKey());
        assertEquals(expectedKey, account.firstContractStorageKey());
    }

    @Test
    void nonGenesisDoesntCreate() {
        // To simulate a non-genesis case, we'll add a single account object to the `previousStates` param
        accounts = MapWritableKVState.<AccountID, Account>builder(ACCOUNTS_KEY)
                .value(ACCT_IDS[1], Account.DEFAULT)
                .build();
        final var nonEmptyPrevStates = newStatesInstance(
                accounts,
                MapWritableKVState.<Bytes, AccountID>builder(ALIASES_KEY).build(),
                newWritableEntityIdState());
        final var schema = newSubjectWithAllExpected();
        final var migrationContext = new MigrationContextImpl(
                nonEmptyPrevStates,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                VERSION,
                new HashMap<>());

        schema.migrate(migrationContext);

        final var acctsStateResult = newStates.<AccountID, Account>get(ACCOUNTS_KEY);
        assertThat(acctsStateResult.isModified()).isFalse();
        final var nodeRewardsStateResult = newStates.<NodeInfo>getSingleton(STAKING_NETWORK_REWARDS_KEY);
        assertThat(nodeRewardsStateResult.isModified()).isFalse();
        final var nodeInfoStateResult = newStates.<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY);
        assertThat(nodeInfoStateResult.isModified()).isFalse();
    }

    @Test
    void initializesStakingDataOnGenesisStart() {
        final var schema = newSubjectWithAllExpected();
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>());

        schema.migrate(migrationContext);

        final var nodeRewardsStateResult = newStates.<NodeInfo>getSingleton(STAKING_NETWORK_REWARDS_KEY);
        assertThat(nodeRewardsStateResult.isModified()).isTrue();
        final var nodeInfoStateResult = newStates.<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY);
        assertThat(nodeInfoStateResult.isModified()).isTrue();
    }

    @Test
    void verifyStatesToCreate() {

        var schema = new V0490TokenSchema(
                this::allDefaultSysAccts,
                this::allStakingAccts,
                this::allMiscAccts,
                this::allTreasuryClones,
                this::allBlocklistAccts);

        var sortedResult = schema.statesToCreate().stream()
                .sorted(Comparator.comparing(StateDefinition::stateKey))
                .toList();

        final var firstStateDef = sortedResult.getFirst();
        final var secondStateDef = sortedResult.get(1);
        final var thirdStateDef = sortedResult.get(2);
        assertThat(firstStateDef.stateKey()).isEqualTo("ACCOUNTS");
        assertThat(firstStateDef.keyCodec()).isEqualTo(AccountID.PROTOBUF);
        assertThat(firstStateDef.valueCodec()).isEqualTo(Account.PROTOBUF);
        assertThat(secondStateDef.stateKey()).isEqualTo("ALIASES");
        assertThat(secondStateDef.keyCodec()).isEqualTo(ProtoBytes.PROTOBUF);
        assertThat(secondStateDef.valueCodec()).isEqualTo(AccountID.PROTOBUF);
        assertThat(thirdStateDef.stateKey()).isEqualTo("NFTS");
        assertThat(thirdStateDef.keyCodec()).isEqualTo(NftID.PROTOBUF);
        assertThat(thirdStateDef.valueCodec()).isEqualTo(Nft.PROTOBUF);
    }

    @Test
    void testMigrateForNonNullAcctsFs() throws NoSuchFieldException, IllegalAccessException, IOException {

        var newStatesForMigration = newStatesInstance(
                accounts,
                MapWritableKVState.<Bytes, AccountID>builder(ALIASES_KEY).build(),
                MapWritableKVState.<NftID, Nft>builder(NFTS_KEY).build(),
                MapWritableKVState.<EntityIDPair, TokenRelation>builder(TOKEN_RELS_KEY)
                        .build(),
                MapWritableKVState.<TokenID, Token>builder(TOKENS_KEY).build(),
                MapWritableKVState.<EntityNumber, StakingNodeInfo>builder(STAKING_INFO_KEY)
                        .build(),
                newWritableEntityIdState());

        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStatesForMigration,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>());

        initialModServiceTokenSchema = new V0490TokenSchema(
                this::allDefaultSysAccts,
                this::allStakingAccts,
                this::allMiscAccts,
                this::allTreasuryClones,
                this::allBlocklistAccts);

        Field acctFsField = initialModServiceTokenSchema.getClass().getDeclaredField("acctsFs");
        acctFsField.setAccessible(true);
        accounts = MapWritableKVState.<AccountID, Account>builder(ACCOUNTS_KEY)
                .value(ACCT_IDS[1], Account.DEFAULT)
                .build();

        // --- NFTS
        final var merkleDbTableConfigNfts = new MerkleDbTableConfig<>(
                        (short) 1,
                        DigestType.SHA_384,
                        (short) 1,
                        new UniqueTokenKeySerializer(),
                        (short) 1,
                        new UniqueTokenValueSerializer())
                .maxNumberOfKeys(1_024);

        VirtualMap<UniqueTokenKey, UniqueTokenValue> nftVirtualMap = new VirtualMap<>(
                "NFTVMap",
                new MerkleDbDataSourceBuilder<>(buildTemporaryDirectory("merkleDbNfts"), merkleDbTableConfigNfts));
        acctFsField.set(initialModServiceTokenSchema, nftVirtualMap);
        initialModServiceTokenSchema.setNftsFromState(nftVirtualMap);

        // ---- Token Relations
        final var merkleDbTableConfigTokenRels = new MerkleDbTableConfig<>(
                        (short) 1,
                        DigestType.SHA_384,
                        (short) 1,
                        new EntityNumVirtualKeySerializer(),
                        (short) 1,
                        new OnDiskTokenRelValueSerializer())
                .maxNumberOfKeys(1_024);

        VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> tokenRelsVirtualMap = new VirtualMap<>(
                "TokenRelsVMap",
                new MerkleDbDataSourceBuilder<>(
                        buildTemporaryDirectory("merkleDbTokenRels"), merkleDbTableConfigTokenRels));
        initialModServiceTokenSchema.setTokenRelsFromState(tokenRelsVirtualMap);

        // -- Staking Info
        final MerkleStakingInfo stakingInfo1 = new MerkleStakingInfo(
                minStake,
                maxStake,
                stakeToReward1,
                stakeToNotReward1,
                stakedRewardStart1,
                unclaimedStakedRewardStart1,
                stake1,
                rewardSumHistory1,
                0);

        MerkleMap<EntityNum, MerkleStakingInfo> merkleMapStakingInfo = new MerkleMap<>(1);
        merkleMapStakingInfo.put(new EntityNum(1), stakingInfo1);
        MerkleNetworkContext merkleNetworkContext = new MerkleNetworkContext();
        merkleNetworkContext.setConsensusTimeOfLastHandledTxn(Instant.now());
        initialModServiceTokenSchema.setStakingFs(merkleMapStakingInfo, merkleNetworkContext);

        // --- Tokens
        MerkleMap<EntityNum, MerkleToken> merkleMapTokens = new MerkleMap<>(1);
        final TokenID TOKEN_123 = new TokenID(1L, 2L, 3L);
        final AccountID ACCOUNT_1339 = asAccount(1339);
        final var totalFungibleSupply = 2;
        var token = Token.newBuilder()
                .tokenId(TOKEN_123)
                .tokenType(TokenType.FUNGIBLE_COMMON)
                .treasuryAccountId(ACCOUNT_1339)
                .supplyKey((Key) null)
                .totalSupply(totalFungibleSupply)
                .build();
        merkleMapTokens.put(new EntityNum(1), merkleTokenFromToken(token));
        initialModServiceTokenSchema.setTokensFromState(merkleMapTokens);
        assertThatNoException().isThrownBy(() -> initialModServiceTokenSchema.migrate(migrationContext));
    }

    @Test
    void createsAllAccountsOnGenesisStart() {
        final var schema = newSubjectWithAllExpected();
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>());

        schema.migrate(migrationContext);

        final var acctsStateResult = newStates.<AccountID, Account>get(ACCOUNTS_KEY);
        assertThat(acctsStateResult).isNotNull();

        // Verify created system accounts
        for (int i = 1; i < DEFAULT_NUM_SYSTEM_ACCOUNTS; i++) {
            assertThat(acctsStateResult.get(ACCT_IDS[i])).isNotNull();
        }

        // Verify created staking accounts
        assertThat(acctsStateResult.get(ACCT_IDS[800])).isNotNull();
        assertThat(acctsStateResult.get(ACCT_IDS[801])).isNotNull();

        // Verify created multipurpose accounts
        for (int i = 900; i < 1001; i++) {
            assertThat(acctsStateResult.get(ACCT_IDS[i])).isNotNull();
        }

        // Verify created treasury clones
        for (final long nonContractSysNum : NON_CONTRACT_RESERVED_NUMS) {
            if (nonContractSysNum < DEFAULT_NUM_SYSTEM_ACCOUNTS) {
                // we've already checked that this account is not null (there's some overlap with system accounts), so
                // skip checking this account again
                continue;
            }
            assertThat(acctsStateResult.get(ACCT_IDS[(int) nonContractSysNum])).isNotNull();
        }

        // Verify overwritten blocklist account RECORDS
        verify(genesisRecordsBuilder).blocklistAccounts(blocklistMapCaptor.capture());
        final var blocklistAcctsResult = blocklistMapCaptor.getValue();
        Assertions.assertThat(blocklistAcctsResult).isNotNull().hasSize(6).allSatisfy(account -> {
            assertThat(account).isNotNull();
            assertThat(account.accountId().accountNum())
                    .isBetween(BEGINNING_ENTITY_ID, BEGINNING_ENTITY_ID + EVM_ADDRESSES.length);
        });

        // Verify created blocklist account OBJECTS
        final long expectedBlocklistIndex = BEGINNING_ENTITY_ID + EVM_ADDRESSES.length;
        for (int i = 3001; i <= expectedBlocklistIndex; i++) {
            final var acct =
                    acctsStateResult.get(AccountID.newBuilder().accountNum(i).build());
            assertThat(acct).isNotNull();
            assertThat(acct.alias()).isEqualTo(Bytes.fromHex(EVM_ADDRESSES[i - (int) BEGINNING_ENTITY_ID - 1]));
        }

        // Finally, verify that the size is exactly as expected
        assertThat(acctsStateResult.size())
                .isEqualTo(
                        // All the system accounts
                        DEFAULT_NUM_SYSTEM_ACCOUNTS
                                +
                                // Both of the two staking accounts
                                2
                                +
                                // All the misc accounts
                                101
                                +
                                // All treasury clones (which is 501 - <overlap between treasury clones and sys
                                // accounts)
                                388
                                +
                                // All the blocklist accounts
                                6);
    }

    @Test
    void someAccountsAlreadyExist() {
        // Initializing the schema will happen with _all_ the expected account records, but only some of those accounts
        // should be created in the migration
        final var schema = new V0490TokenSchema(
                () -> allSysAccts(4),
                this::allStakingAccts,
                this::allMiscAccts,
                this::allTreasuryClones,
                this::allBlocklistAccts);

        // We'll only configure 4 system accounts, half of which will already exist
        config = buildConfig(4, true);
        final var accts = new HashMap<AccountID, Account>();
        IntStream.rangeClosed(1, 2).forEach(i -> putNewAccount(i, accts, testMemo()));
        // One of the two staking accounts will already exist
        final var stakingAcctId = ACCT_IDS[800];
        accts.put(
                stakingAcctId,
                Account.newBuilder().accountId(stakingAcctId).memo(testMemo()).build());
        // Half of the multipurpose accounts will already exist
        IntStream.rangeClosed(900, 950).forEach(i -> putNewAccount(i, accts, testMemo()));
        // All but five of the treasury clones will already exist
        IntStream.rangeClosed(200, 745).forEach(i -> {
            if (isRegularAcctNum(i)) {
                putNewAccount(i, accts, testMemo());
            }
        });
        // Half of the blocklist accounts will already exist (simulated by the existence of alias
        // mappings only, not the account objects)
        final var blocklistAccts = Map.of(
                Bytes.fromHex(EVM_ADDRESS_0), asAccount(BEGINNING_ENTITY_ID - 2),
                Bytes.fromHex(EVM_ADDRESS_1), asAccount(BEGINNING_ENTITY_ID - 1),
                Bytes.fromHex(EVM_ADDRESS_2), asAccount(BEGINNING_ENTITY_ID));
        newStates = newStatesInstance(
                new MapWritableKVState<>(ACCOUNTS_KEY, accts),
                new MapWritableKVState<>(ALIASES_KEY, blocklistAccts),
                newWritableEntityIdState());
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>());

        schema.migrate(migrationContext);

        final var acctsStateResult = newStates.get(ACCOUNTS_KEY);
        // Verify the pre-inserted didn't change
        IntStream.rangeClosed(1, 2)
                .forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i])).isEqualTo(accts.get(ACCT_IDS[i])));
        // Only system accts with IDs 3 and 4 should have been created
        IntStream.rangeClosed(3, 4).forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i]))
                .isNotNull()
                .extracting("memo")
                .asString()
                .doesNotStartWith("test-original"));

        // Verify the pre-inserted didn't change
        assertThat(acctsStateResult.get(ACCT_IDS[800])).isEqualTo(accts.get(ACCT_IDS[800]));
        // Only the staking acct with ID 801 should have been created
        assertThat(acctsStateResult.get(ACCT_IDS[801]))
                .isNotNull()
                .extracting("memo")
                .asString()
                .doesNotStartWith("test-original");

        // Verify the pre-inserted didn't change
        IntStream.rangeClosed(900, 950)
                .forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i])).isEqualTo(accts.get(ACCT_IDS[i])));
        // Only multi-use accts with IDs 951-1000 should have been created
        IntStream.rangeClosed(951, 1000).forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i]))
                .isNotNull()
                .extracting("memo")
                .asString()
                .doesNotStartWith("test-original"));

        // Verify the pre-inserted didn't change
        IntStream.rangeClosed(250, 745)
                .forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i])).isEqualTo(accts.get(ACCT_IDS[i])));
        // Only treasury clones with IDs 746-750 should have been created
        IntStream.rangeClosed(746, 750).forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i]))
                .isNotNull()
                .extracting("memo")
                .asString()
                .doesNotStartWith("test-original"));

        final var aliasStateResult = newStates.get(ALIASES_KEY);
        // Verify the pre-inserted didn't change
        assertThat(aliasStateResult.get(Bytes.fromHex(EVM_ADDRESSES[0])))
                .isEqualTo(AccountID.newBuilder()
                        .accountNum(BEGINNING_ENTITY_ID - 2)
                        .build());
        assertThat(aliasStateResult.get(Bytes.fromHex(EVM_ADDRESSES[1])))
                .isEqualTo(AccountID.newBuilder()
                        .accountNum(BEGINNING_ENTITY_ID - 1)
                        .build());
        assertThat(aliasStateResult.get(Bytes.fromHex(EVM_ADDRESSES[2])))
                .isEqualTo(
                        AccountID.newBuilder().accountNum(BEGINNING_ENTITY_ID).build());
        // Only half of the blocklist accts should have been "created" (i.e. added to the alias state)
        assertThat(aliasStateResult.get(Bytes.fromHex(EVM_ADDRESSES[3])))
                .isEqualTo(AccountID.newBuilder()
                        .accountNum(BEGINNING_ENTITY_ID + 1)
                        .build());
        assertThat(aliasStateResult.get(Bytes.fromHex(EVM_ADDRESSES[4])))
                .isEqualTo(AccountID.newBuilder()
                        .accountNum(BEGINNING_ENTITY_ID + 2)
                        .build());
        assertThat(aliasStateResult.get(Bytes.fromHex(EVM_ADDRESSES[5])))
                .isEqualTo(AccountID.newBuilder()
                        .accountNum(BEGINNING_ENTITY_ID + 3)
                        .build());

        final int numModifiedAccts = acctsStateResult.modifiedKeys().size();
        assertThat(numModifiedAccts)
                .isEqualTo(
                        // Half of the four configured system accounts
                        2
                                +
                                // One of the two staking accounts
                                1
                                +
                                // Half of the misc accounts
                                50
                                +
                                // The five remaining treasury accounts
                                5
                                +
                                // Half of the blocklist accounts
                                3);
    }

    @Test
    void allAccountsAlreadyExist() {
        // Initializing the schema will happen with _all_ the expected account records, but none of those accounts
        // should be created in the migration
        final var schema = newSubjectWithAllExpected();

        // All the system accounts will already exist
        final var accts = new HashMap<AccountID, Account>();
        IntStream.rangeClosed(1, DEFAULT_NUM_SYSTEM_ACCOUNTS).forEach(i -> putNewAccount(i, accts, testMemo()));
        // Both of the two staking accounts will already exist
        IntStream.rangeClosed(800, 801).forEach(i -> putNewAccount(i, accts, testMemo()));
        // All the multipurpose accounts will already exist
        IntStream.rangeClosed(900, 1000).forEach(i -> putNewAccount(i, accts, testMemo()));
        // All the treasury clones will already exist
        IntStream.rangeClosed(200, 750).forEach(i -> {
            if (isRegularAcctNum(i)) putNewAccount(i, accts, testMemo());
        });
        // All the blocklist accounts will already exist
        final var blocklistEvmAliasMappings = Map.of(
                Bytes.fromHex(EVM_ADDRESS_0), asAccount(BEGINNING_ENTITY_ID),
                Bytes.fromHex(EVM_ADDRESS_1), asAccount(BEGINNING_ENTITY_ID + 1),
                Bytes.fromHex(EVM_ADDRESS_2), asAccount(BEGINNING_ENTITY_ID + 2),
                Bytes.fromHex(EVM_ADDRESS_3), asAccount(BEGINNING_ENTITY_ID + 3),
                Bytes.fromHex(EVM_ADDRESS_4), asAccount(BEGINNING_ENTITY_ID + 4),
                Bytes.fromHex(EVM_ADDRESS_5), asAccount(BEGINNING_ENTITY_ID + 5));
        newStates = newStatesInstance(
                new MapWritableKVState<>(ACCOUNTS_KEY, accts),
                new MapWritableKVState<>(ALIASES_KEY, blocklistEvmAliasMappings),
                newWritableEntityIdState());
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                VERSION,
                new HashMap<>());

        schema.migrate(migrationContext);

        // Verify that none of the accounts changed
        final var acctsStateResult = newStates.get(ACCOUNTS_KEY);
        IntStream.rangeClosed(1, DEFAULT_NUM_SYSTEM_ACCOUNTS)
                .forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i])).isEqualTo(accts.get(ACCT_IDS[i])));
        IntStream.rangeClosed(800, 801)
                .forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i])).isEqualTo(accts.get(ACCT_IDS[i])));
        // All the multipurpose accounts will already exist
        IntStream.rangeClosed(900, 1000)
                .forEach(i -> assertThat(acctsStateResult.get(ACCT_IDS[i])).isEqualTo(accts.get(ACCT_IDS[i])));
        // All the treasury clones will already exist
        IntStream.rangeClosed(200, (int) NUM_RESERVED_SYSTEM_ENTITIES).forEach(i -> {
            if (isRegularAcctNum(i))
                assertThat(acctsStateResult.get(ACCT_IDS[i])).isEqualTo(accts.get(ACCT_IDS[i]));
            else assertThat(acctsStateResult.get(ACCT_IDS[i])).isNull();
        });
        // All the blocklist accounts will already exist
        IntStream.rangeClosed((int) BEGINNING_ENTITY_ID + 1, (int) BEGINNING_ENTITY_ID + EVM_ADDRESSES.length)
                .forEach(i -> {
                    final var acctId = AccountID.newBuilder().accountNum(i).build();
                    assertThat(acctsStateResult.get(acctId)).isEqualTo(accts.get(acctId));
                });

        // Since all accounts were already created, no state should not have been modified
        assertThat(acctsStateResult.isModified()).isFalse();
        final var aliasStateResult = newStates.<Bytes, AccountID>get(ALIASES_KEY);
        assertThat(aliasStateResult.isModified()).isFalse();
    }

    @Test
    void blocklistNotEnabled() {
        final var schema = newSubjectWithAllExpected();

        // None of the blocklist accounts will exist, but they shouldn't be created since blocklists aren't enabled
        config = buildConfig(DEFAULT_NUM_SYSTEM_ACCOUNTS, false);
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>());

        schema.migrate(migrationContext);

        final var acctsStateResult = newStates.<AccountID, Account>get(ACCOUNTS_KEY);
        final var aliasStateResult = newStates.<Bytes, AccountID>get(ALIASES_KEY);
        for (int i = 0; i < EVM_ADDRESSES.length; i++) {
            assertThat(acctsStateResult.get(AccountID.newBuilder()
                            .accountNum(BEGINNING_ENTITY_ID + 1 + i)
                            .build()))
                    .isNull();
            assertThat(aliasStateResult.get(Bytes.fromHex(EVM_ADDRESSES[i]))).isNull();
        }
    }

    @Test
    void createsSystemAccountsOnlyOnGenesisStart() {
        final var schema = new V0490TokenSchema(
                this::allDefaultSysAccts,
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                Collections::emptySortedSet);
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>()));

        final var acctsStateResult = newStates.<AccountID, Account>get(ACCOUNTS_KEY);
        for (int i = 1; i < DEFAULT_NUM_SYSTEM_ACCOUNTS; i++) {
            assertThat(acctsStateResult.get(ACCT_IDS[i])).isNotNull();
        }

        assertThat(acctsStateResult.size()).isEqualTo(DEFAULT_NUM_SYSTEM_ACCOUNTS);
    }

    @Test
    void createsStakingRewardAccountsOnly() {
        // Since we aren't creating all accounts, we overwrite the config so as not to expect the total ledger balance
        // to be present in the resulting objects
        config = overridingLedgerBalanceWithZero();

        final var schema = new V0490TokenSchema(
                Collections::emptySortedSet,
                this::allStakingAccts,
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                Collections::emptySortedSet);
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>()));

        final var acctsStateResult = newStates.<AccountID, Account>get(ACCOUNTS_KEY);
        final var stakingRewardAccount = acctsStateResult.get(ACCT_IDS[800]);
        assertThat(stakingRewardAccount).isNotNull();
        final var nodeRewardAccount = accounts.get(ACCT_IDS[801]);
        assertThat(nodeRewardAccount).isNotNull();

        // Finally, verify that the size is exactly as expected
        Assertions.assertThat(acctsStateResult.modifiedKeys()).hasSize(2);
    }

    @Test
    void createsTreasuryAccountsOnly() {
        // Since we aren't creating all accounts, we overwrite the config so as not to expect the total ledger balance
        // to be present in the resulting objects
        config = overridingLedgerBalanceWithZero();

        final var schema = new V0490TokenSchema(
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                this::allTreasuryClones,
                Collections::emptySortedSet,
                Collections::emptySortedSet);
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>()));

        final var acctsStateResult = newStates.<AccountID, Account>get(ACCOUNTS_KEY);
        for (final long reservedNum : NON_CONTRACT_RESERVED_NUMS) {
            assertThat(acctsStateResult.get(ACCT_IDS[(int) reservedNum])).isNotNull();
        }

        Assertions.assertThat(acctsStateResult.modifiedKeys()).hasSize(NON_CONTRACT_RESERVED_NUMS.length);
    }

    @Test
    void createsMiscAccountsOnly() {
        // Since we aren't creating all accounts, we overwrite the config so as not to expect the total ledger balance
        // to be present in the resulting objects
        config = overridingLedgerBalanceWithZero();

        final var schema = new V0490TokenSchema(
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                this::allMiscAccts,
                Collections::emptySortedSet);
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>()));

        final var acctsStateResult = newStates.<AccountID, Account>get(ACCOUNTS_KEY);

        for (int i = 900; i <= 1000; i++) {
            assertThat(acctsStateResult.get(ACCT_IDS[i])).isNotNull();
        }
    }

    @Test
    void createsBlocklistAccountsOnly() {
        // Since we aren't creating all accounts, we overwrite the config so as not to expect the total ledger balance
        // to be present in the resulting objects
        config = overridingLedgerBalanceWithZero();

        final var schema = new V0490TokenSchema(
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                Collections::emptySortedSet,
                this::allBlocklistAccts);
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>()));

        // Verify that the assigned account ID matches the expected entity IDs
        for (int i = 0; i < EVM_ADDRESSES.length; i++) {
            final var acctId = newStates.get(ALIASES_KEY).get(Bytes.fromHex(EVM_ADDRESSES[i]));
            assertThat(acctId).isEqualTo(asAccount((int) BEGINNING_ENTITY_ID + i + 1));
        }
    }

    @Test
    void onlyExpectedIdsUsed() {
        final var schema = newSubjectWithAllExpected();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>()));

        // Verify contract entity IDs aren't used
        for (int i = 350; i < 400; i++) {
            assertThat(accounts.contains(ACCT_IDS[i])).isFalse();
        }

        // Verify entity IDs between system and staking reward accounts aren't used
        for (int i = 751; i < 800; i++) {
            assertThat(accounts.contains(asAccount(i))).isFalse();
        }

        // Verify entity IDs between staking rewards and misc accounts are empty
        for (int i = 802; i < 900; i++) {
            assertThat(accounts.contains(asAccount(i))).isFalse();
        }
    }

    @Test
    void marksNonExistingNodesToDeletedInStateAndAddsNewNodesToState() {
        accounts = MapWritableKVState.<AccountID, Account>builder(ACCOUNTS_KEY).build();
        // State has nodeIds 1, 2, 3
        final var stakingInfosState = new MapWritableKVState.Builder<EntityNumber, StakingNodeInfo>(STAKING_INFO_KEY)
                .value(NODE_NUM_1, STAKING_INFO_1)
                .value(NODE_NUM_2, STAKING_INFO_2)
                .value(NODE_NUM_3, STAKING_INFO_3)
                .build();
        final var previousStates = newStatesInstance(
                accounts,
                MapWritableKVState.<Bytes, AccountID>builder(ALIASES_KEY).build(),
                newWritableEntityIdState(),
                stakingInfosState);
        newStates = newStatesInstance(
                accounts,
                MapWritableKVState.<Bytes, AccountID>builder(ALIASES_KEY).build(),
                newWritableEntityIdState(),
                stakingInfosState);
        entityIdStore = new WritableEntityIdStore(newStates);
        // Platform address book has node Ids 2, 4, 8
        networkInfo = new FakeNetworkInfo();
        config = buildConfig(DEFAULT_NUM_SYSTEM_ACCOUNTS, true);

        final var schema = newSubjectWithAllExpected();
        // When we call restart, the state will be updated to mark node 1 and 3 as deleted
        schema.restart(new MigrationContextImpl(
                previousStates,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                entityIdStore,
                null,
                new HashMap<>()));
        final var updatedStates = newStates.get(STAKING_INFO_KEY);
        // marks nodes 1, 2 as deleted
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_1)).deleted()).isTrue();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_2)).deleted()).isFalse();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_3)).deleted()).isTrue();
        // Also adds node 4 to the state
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_4)).deleted()).isFalse();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_4)).weight()).isZero();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_4)).minStake()).isZero();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_4)).maxStake()).isEqualTo(1666666666666666666L);
        // Also adds node 8 to the state
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_8)).deleted()).isFalse();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_8)).weight()).isZero();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_8)).minStake()).isZero();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_8)).maxStake()).isEqualTo(1666666666666666666L);
    }

    private Configuration overridingLedgerBalanceWithZero() {
        return configBuilder(DEFAULT_NUM_SYSTEM_ACCOUNTS, true)
                .withValue("ledger.totalTinyBarFloat", "0")
                .getOrCreateConfig();
    }

    private void putNewAccount(final int num, final HashMap<AccountID, Account> accts, final String memo) {
        final var balance = num == TREASURY_ACCOUNT_NUM ? EXPECTED_TREASURY_TINYBARS_BALANCE : 0L;
        final var acct = Account.newBuilder()
                .accountId(ACCT_IDS[num])
                .tinybarBalance(balance)
                .memo(memo)
                .build();
        accts.put(ACCT_IDS[num], acct);
    }

    private WritableSingletonState<EntityNumber> newWritableEntityIdState() {
        return new WritableSingletonStateBase<>(
                V0490EntityIdSchema.ENTITY_ID_STATE_KEY, () -> new EntityNumber(BEGINNING_ENTITY_ID), c -> {});
    }

    private MapWritableStates newStatesInstance(
            final MapWritableKVState<AccountID, Account> accts,
            final MapWritableKVState<Bytes, AccountID> aliases,
            final MapWritableKVState<NftID, Nft> nfts,
            final MapWritableKVState<EntityIDPair, TokenRelation> tokenRels,
            final MapWritableKVState<TokenID, Token> tokens,
            final MapWritableKVState<EntityNumber, StakingNodeInfo> stakingNodeInfo,
            final WritableSingletonState<EntityNumber> entityIdState) {
        //noinspection ReturnOfNull
        return MapWritableStates.builder()
                .state(accts)
                .state(aliases)
                .state(nfts)
                .state(tokenRels)
                .state(tokens)
                .state(stakingNodeInfo)
                .state(new WritableSingletonStateBase<>(STAKING_NETWORK_REWARDS_KEY, () -> null, c -> {}))
                .state(entityIdState)
                .build();
    }

    private MapWritableStates newStatesInstance(
            final MapWritableKVState<AccountID, Account> accts,
            final MapWritableKVState<Bytes, AccountID> aliases,
            final WritableSingletonState<EntityNumber> entityIdState) {
        //noinspection ReturnOfNull
        return MapWritableStates.builder()
                .state(accts)
                .state(aliases)
                .state(MapWritableKVState.builder(STAKING_INFO_KEY).build())
                .state(new WritableSingletonStateBase<>(STAKING_NETWORK_REWARDS_KEY, () -> null, c -> {}))
                .state(entityIdState)
                .build();
    }

    private MapWritableStates newStatesInstance(
            final MapWritableKVState<AccountID, Account> accts,
            final MapWritableKVState<Bytes, AccountID> aliases,
            final WritableSingletonState<EntityNumber> entityIdState,
            final MapWritableKVState<EntityNumber, StakingNodeInfo> stakingInfo) {
        //noinspection ReturnOfNull
        return MapWritableStates.builder()
                .state(accts)
                .state(aliases)
                .state(stakingInfo)
                .state(new WritableSingletonStateBase<>(STAKING_NETWORK_REWARDS_KEY, () -> null, c -> {}))
                .state(entityIdState)
                .build();
    }

    private SortedSet<Account> allDefaultSysAccts() {
        return allSysAccts(DEFAULT_NUM_SYSTEM_ACCOUNTS);
    }

    private SortedSet<Account> allSysAccts(final int numSysAccts) {
        return newAccounts(1, numSysAccts);
    }

    private SortedSet<Account> allStakingAccts() {
        return newAccounts(800, 801);
    }

    private SortedSet<Account> allMiscAccts() {
        return newAccounts(900, 1000);
    }

    private SortedSet<Account> allTreasuryClones() {
        final var accts = new TreeSet<>(ACCOUNT_COMPARATOR);
        for (final long nonContractSysNum : NON_CONTRACT_RESERVED_NUMS) {
            accts.add(newAcctWithId((int) nonContractSysNum));
        }

        return accts;
    }

    private SortedSet<Account> allBlocklistAccts() {
        final var accts = new TreeSet<>(ACCOUNT_COMPARATOR);
        for (int i = 0; i < EVM_ADDRESSES.length; i++) {
            accts.add(newAcctWithId((int) BEGINNING_ENTITY_ID + i)
                    .copyBuilder()
                    .alias(Bytes.fromHex(EVM_ADDRESSES[i]))
                    .build());
        }

        return accts;
    }

    private V0490TokenSchema newSubjectWithAllExpected() {
        return new V0490TokenSchema(
                this::allDefaultSysAccts,
                this::allStakingAccts,
                this::allMiscAccts,
                this::allTreasuryClones,
                this::allBlocklistAccts);
    }

    /**
     * @return true if the given account number is NOT a staking account number or system contract
     */
    private static boolean isRegularAcctNum(final long i) {
        // Skip the staking account nums
        if (Arrays.contains(new long[] {800, 801}, i)) return false;
        // Skip the system contract account nums
        return i < 350 || i > 399;
    }

    private static String testMemo() {
        return "test-original" + Instant.now();
    }

    private static SortedSet<Account> newAccounts(final int lower, final int higher) {
        final var accts = new TreeSet<>(ACCOUNT_COMPARATOR);
        for (int i = lower; i <= higher; i++) {
            accts.add(newAcctWithId(i));
        }

        return accts;
    }

    private static Account newAcctWithId(final int id) {
        final var acctId = id <= ACCT_IDS.length ? ACCT_IDS[id] : asAccount(id);
        final var balance = id == TREASURY_ACCOUNT_NUM ? EXPECTED_TREASURY_TINYBARS_BALANCE : 0;
        return Account.newBuilder().accountId(acctId).tinybarBalance(balance).build();
    }
}
