/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.node.app.service.file.impl.schemas.V0490FileSchema.parseFeeSchedules;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.base.Setting;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.records.ReadableBlockRecordStore;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.token.impl.comparator.TokenComparators;
import com.hedera.node.app.service.token.impl.schemas.SyntheticAccountCreator;
import com.hedera.node.app.service.token.records.GenesisAccountStreamBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.SystemContext;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.record.SystemSetup;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class SystemSetupTest {
    private static final AccountID SYS_ADMIN_ID =
            AccountID.newBuilder().accountNum(50L).build();

    private static final AccountID ACCOUNT_ID_1 =
            AccountID.newBuilder().accountNum(1).build();
    private static final AccountID ACCOUNT_ID_2 =
            AccountID.newBuilder().accountNum(2).build();
    private static final int ACCT_1_BALANCE = 25;
    private static final Account ACCOUNT_1 = Account.newBuilder()
            .accountId(ACCOUNT_ID_1)
            .tinybarBalance(ACCT_1_BALANCE)
            .build();
    private static final Account ACCOUNT_2 =
            Account.newBuilder().accountId(ACCOUNT_ID_2).build();
    private static final Instant CONSENSUS_NOW = Instant.parse("2023-08-10T00:00:00Z");

    private static final String EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO = "Synthetic system creation";
    private static final String EXPECTED_STAKING_MEMO = "Release 0.24.1 migration record";
    private static final String EXPECTED_TREASURY_CLONE_MEMO = "Synthetic zero-balance treasury clone";

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TokenContext context;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableBlockRecordStore blockStore;

    @Mock
    private SyntheticAccountCreator syntheticAccountCreator;

    @Mock
    private FileServiceImpl fileService;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private GenesisAccountStreamBuilder genesisAccountRecordBuilder;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private ReadableNodeStore readableNodeStore;

    @Mock
    private HandleContext handleContext;

    @Mock
    private Dispatch dispatch;

    @Mock
    private StreamBuilder streamBuilder;

    @LoggingSubject
    private SystemSetup subject;

    @LoggingTarget
    private LogCaptor logCaptor;

    @TempDir
    java.nio.file.Path tempDir;

    @BeforeEach
    void setup() {
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);
        given(context.consensusTime()).willReturn(CONSENSUS_NOW);
        given(context.addPrecedingChildRecordBuilder(GenesisAccountStreamBuilder.class))
                .willReturn(genesisAccountRecordBuilder);
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);

        given(blockStore.getLastBlockInfo()).willReturn(defaultStartupBlockInfo());

        subject = new SystemSetup(fileService, syntheticAccountCreator);
    }

    @Test
    void successfulAutoUpdatesAreDispatchedWithFilesAvailable() throws IOException {
        final var config = HederaTestConfigBuilder.create()
                .withValue("networkAdmin.upgradeSysFilesLoc", tempDir.toString())
                .getOrCreateConfig();
        final var adminConfig = config.getConfigData(NetworkAdminConfig.class);
        Files.writeString(tempDir.resolve(adminConfig.upgradePropertyOverridesFile()), validPropertyOverrides());
        Files.writeString(tempDir.resolve(adminConfig.upgradePermissionOverridesFile()), validPermissionOverrides());
        Files.writeString(tempDir.resolve(adminConfig.upgradeThrottlesFile()), validThrottleOverrides());
        Files.writeString(tempDir.resolve(adminConfig.upgradeFeeSchedulesFile()), validFeeScheduleOverrides());
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.config()).willReturn(config);
        given(dispatch.consensusNow()).willReturn(CONSENSUS_NOW);
        given(dispatch.handleContext()).willReturn(handleContext);
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableNodeStore.class)).willReturn(readableNodeStore);
        given(handleContext.dispatchPrecedingTransaction(any(), any(), any(), any()))
                .willReturn(streamBuilder);

        subject.doPostUpgradeSetup(dispatch);

        final var filesConfig = config.getConfigData(FilesConfig.class);
        verify(fileService).updateAddressBookAndNodeDetailsAfterFreeze(any(SystemContext.class), eq(readableNodeStore));
        verifyUpdateDispatch(filesConfig.networkProperties(), serializedPropertyOverrides());
        verifyUpdateDispatch(filesConfig.hapiPermissions(), serializedPermissionOverrides());
        verifyUpdateDispatch(filesConfig.throttleDefinitions(), serializedThrottleOverrides());
        verifyUpdateDispatch(filesConfig.feeSchedules(), serializedFeeSchedules());
        verify(stack, times(5)).commitFullStack();
    }

    @Test
    void onlyAddressBookAndNodeDetailsAutoUpdateIsDispatchedWithNoFilesAvailable() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("networkAdmin.upgradeSysFilesLoc", tempDir.toString())
                .getOrCreateConfig();
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.config()).willReturn(config);
        given(dispatch.handleContext()).willReturn(handleContext);
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableNodeStore.class)).willReturn(readableNodeStore);

        subject.doPostUpgradeSetup(dispatch);

        verify(fileService).updateAddressBookAndNodeDetailsAfterFreeze(any(SystemContext.class), eq(readableNodeStore));
        verify(stack, times(1)).commitFullStack();

        final var infoLogs = logCaptor.infoLogs();
        assertThat(infoLogs.size()).isEqualTo(4);
        assertThat(infoLogs.getFirst()).startsWith("No post-upgrade file for feeSchedules.json");
        assertThat(infoLogs.get(1)).startsWith("No post-upgrade file for throttles.json");
        assertThat(infoLogs.get(2)).startsWith("No post-upgrade file for application-override.properties");
        assertThat(infoLogs.getLast()).startsWith("No post-upgrade file for api-permission-override.properties");
    }

    @Test
    void onlyAddressBookAndNodeDetailsAutoUpdateIsDispatchedWithInvalidFilesAvailable() throws IOException {
        final var config = HederaTestConfigBuilder.create()
                .withValue("networkAdmin.upgradeSysFilesLoc", tempDir.toString())
                .getOrCreateConfig();
        final var adminConfig = config.getConfigData(NetworkAdminConfig.class);
        Files.writeString(tempDir.resolve(adminConfig.upgradePropertyOverridesFile()), invalidPropertyOverrides());
        Files.writeString(tempDir.resolve(adminConfig.upgradePermissionOverridesFile()), invalidPermissionOverrides());
        Files.writeString(tempDir.resolve(adminConfig.upgradeThrottlesFile()), invalidThrottleOverrides());
        Files.writeString(tempDir.resolve(adminConfig.upgradeFeeSchedulesFile()), invalidFeeScheduleOverrides());
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.config()).willReturn(config);
        given(dispatch.handleContext()).willReturn(handleContext);
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableNodeStore.class)).willReturn(readableNodeStore);

        subject.doPostUpgradeSetup(dispatch);

        verify(fileService).updateAddressBookAndNodeDetailsAfterFreeze(any(SystemContext.class), eq(readableNodeStore));
        verify(stack, times(1)).commitFullStack();

        final var errorLogs = logCaptor.errorLogs();
        assertThat(errorLogs.size()).isEqualTo(4);
        assertThat(errorLogs.getFirst()).startsWith("Failed to parse upgrade file for feeSchedules.json");
        assertThat(errorLogs.get(1)).startsWith("Failed to parse upgrade file for throttles.json");
        assertThat(errorLogs.get(2)).startsWith("Failed to parse upgrade file for application-override.properties");
        assertThat(errorLogs.getLast())
                .startsWith("Failed to parse upgrade file for api-permission-override.properties");
    }

    @Test
    @SuppressWarnings("unchecked")
    void externalizeInitSideEffectsCreatesAllRecords() {
        final var acctId3 = ACCOUNT_ID_1.copyBuilder().accountNum(3).build();
        final var acct3 = ACCOUNT_1.copyBuilder().accountId(acctId3).build();
        final var acctId4 = ACCOUNT_ID_1.copyBuilder().accountNum(4).build();
        final var acct4 = ACCOUNT_1.copyBuilder().accountId(acctId4).build();
        final var acctId5 = ACCOUNT_ID_1.copyBuilder().accountNum(5).build();
        final var acct5 = ACCOUNT_1.copyBuilder().accountId(acctId5).build();
        final var sysAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        sysAccts.add(ACCOUNT_1);
        final var stakingAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        stakingAccts.add(ACCOUNT_2);
        final var miscAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        miscAccts.add(acct3);
        final var treasuryAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        treasuryAccts.add(acct4);
        final var blocklistAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        blocklistAccts.add(acct5);
        doAnswer(invocationOnMock -> {
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(1)).accept(sysAccts);
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(2)).accept(stakingAccts);
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(3)).accept(treasuryAccts);
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(4)).accept(miscAccts);
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(5)).accept(blocklistAccts);
                    return null;
                })
                .when(syntheticAccountCreator)
                .generateSyntheticAccounts(any(), any(), any(), any(), any(), any());
        given(genesisAccountRecordBuilder.accountID(any())).willReturn(genesisAccountRecordBuilder);

        // Call the first time to make sure records are generated
        subject.externalizeInitSideEffects(context, ExchangeRateSet.DEFAULT);

        verifyBuilderInvoked(ACCOUNT_ID_1, EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO, ACCT_1_BALANCE);
        verifyBuilderInvoked(ACCOUNT_ID_2, EXPECTED_STAKING_MEMO);
        verifyBuilderInvoked(acctId3, null);
        verifyBuilderInvoked(acctId4, EXPECTED_TREASURY_CLONE_MEMO);
        verifyBuilderInvoked(acctId5, null);

        // Call externalizeInitSideEffects() a second time to make sure no other records are created
        Mockito.clearInvocations(genesisAccountRecordBuilder);
        assertThatThrownBy(() -> subject.externalizeInitSideEffects(context, ExchangeRateSet.DEFAULT))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(genesisAccountRecordBuilder);
    }

    @Test
    void externalizeInitSideEffectsCreatesNoRecordsWhenEmpty() {
        subject.externalizeInitSideEffects(context, ExchangeRateSet.DEFAULT);
        verifyNoInteractions(genesisAccountRecordBuilder);
    }

    private void verifyBuilderInvoked(final AccountID acctId, final String expectedMemo) {
        verifyBuilderInvoked(acctId, expectedMemo, 0);
    }

    private void verifyBuilderInvoked(final AccountID acctId, final String expectedMemo, final long expectedBalance) {
        verify(genesisAccountRecordBuilder).accountID(acctId);

        if (expectedMemo != null)
            verify(genesisAccountRecordBuilder, atLeastOnce()).memo(expectedMemo);

        //noinspection DataFlowIssue
        verify(genesisAccountRecordBuilder, Mockito.never()).memo(null);

        if (expectedBalance != 0) {
            verify(genesisAccountRecordBuilder)
                    .transferList(eq(TransferList.newBuilder()
                            .accountAmounts(AccountAmount.newBuilder()
                                    .accountID(acctId)
                                    .amount(expectedBalance)
                                    .build())
                            .build()));
        }
    }

    private static BlockInfo defaultStartupBlockInfo() {
        return BlockInfo.newBuilder()
                .consTimeOfLastHandledTxn((Timestamp) null)
                .migrationRecordsStreamed(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void verifyUpdateDispatch(final long fileNum, final Bytes contents) {
        verify(handleContext)
                .dispatchPrecedingTransaction(
                        argThat(body -> {
                            final var fileUpdate = body.fileUpdateOrThrow();
                            return fileUpdate.fileIDOrThrow().fileNum() == fileNum
                                    && fileUpdate.contents().equals(contents);
                        }),
                        eq(StreamBuilder.class),
                        any(Predicate.class),
                        eq(SYS_ADMIN_ID));
    }

    private String validPropertyOverrides() {
        return "tokens.nfts.maxBatchSizeMint=2";
    }

    private String validPermissionOverrides() {
        return "tokenMint=0-1";
    }

    private String validThrottleOverrides() {
        return """
{
  "buckets": [
    {
      "name": "ThroughputLimits",
      "burstPeriod": 1,
      "throttleGroups": [
        {
          "opsPerSec": 1,
          "operations": [ "TokenMint" ]
        }
      ]
    }
  ]
}""";
    }

    private String validFeeScheduleOverrides() {
        return """
[
  {
    "currentFeeSchedule": [
      {
        "expiryTime": 1630800000
      }
    ]
  },
  {
    "nextFeeSchedule": [
      {
        "expiryTime": 1633392000
      }
    ]
  }
]""";
    }

    private Bytes serializedFeeSchedules() {
        return CurrentAndNextFeeSchedule.PROTOBUF.toBytes(
                parseFeeSchedules(validFeeScheduleOverrides().getBytes(StandardCharsets.UTF_8)));
    }

    private Bytes serializedThrottleOverrides() {
        return Bytes.wrap(V0490FileSchema.parseThrottleDefinitions(validThrottleOverrides()));
    }

    private Bytes serializedPropertyOverrides() {
        return ServicesConfigurationList.PROTOBUF.toBytes(ServicesConfigurationList.newBuilder()
                .nameValue(Setting.newBuilder()
                        .name("tokens.nfts.maxBatchSizeMint")
                        .value("2")
                        .build())
                .build());
    }

    private Bytes serializedPermissionOverrides() {
        return ServicesConfigurationList.PROTOBUF.toBytes(ServicesConfigurationList.newBuilder()
                .nameValue(Setting.newBuilder().name("tokenMint").value("0-1").build())
                .build());
    }

    private String invalidPropertyOverrides() {
        return "tokens.nfts.maxBatchSizeM\\u12G4";
    }

    private String invalidPermissionOverrides() {
        return "tokenM\\u12G4";
    }

    private String invalidThrottleOverrides() {
        return """
{{
  "buckets": [
    {
      "name": "ThroughputLimits",
      "burstPeriod": 1,
      "throttleGroups": [
        {
          "opsPerSec": 1,
          "operations": [ "TokenMint" ]
        }
      ]
    }
  ]
}""";
    }

    private String invalidFeeScheduleOverrides() {
        return """
[[
  {
    "currentFeeSchedule": [
      {
        "expiryTime": 1630800000
      }
    ]
  },
  {
    "nextFeeSchedule": [
      {
        "expiryTime": 1633392000
      }
    ]
  }
]""";
    }
}
