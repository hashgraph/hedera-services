/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.initialization;

import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.spi.config.PropertyNames.ACCOUNTS_BLOCKLIST_RESOURCE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.config.AccountNumbers;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.function.Supplier;
import org.apache.commons.codec.DecoderException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class BlocklistAccountCreatorTest {
    private static final long GENESIS_ACCOUNT_NUM = 2L;
    private static final long FIRST_UNUSED_ID = 10_000L;
    private EntityIdSource ids;

    @Mock
    private BackingStore<AccountID, HederaAccount> accounts;

    @Mock
    private Supplier<JEd25519Key> genesisKeySource;

    @Mock
    private PropertySource properties;

    @Mock
    private AliasManager aliasManager;

    @Mock
    private AccountNumbers accountNumbers;

    private final JEd25519Key pretendKey = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
    private JKey genesisKey;

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private BlocklistAccountCreator subject;

    @BeforeEach
    void setUp() throws DecoderException {
        ids =
                new EntityIdSource() {
                    long nextId = FIRST_UNUSED_ID;

                    @Override
                    public AccountID newAccountId() {
                        return AccountID.newBuilder().setAccountNum(newAccountNumber()).build();
                    }

                    @Override
                    public long newAccountNumber() {
                        return nextId++;
                    }

                    @Override
                    public TopicID newTopicId(final AccountID sponsor) {
                        return null;
                    }

                    @Override
                    public ContractID newContractId(AccountID newContractSponsor) {
                        return null;
                    }

                    @Override
                    public FileID newFileId(AccountID newFileSponsor) {
                        return null;
                    }

                    @Override
                    public TokenID newTokenId(AccountID sponsor) {
                        return null;
                    }

                    @Override
                    public ScheduleID newScheduleId(AccountID sponsor) {
                        return null;
                    }

                    @Override
                    public void reclaimLastId() {}

                    @Override
                    public void reclaimProvisionalIds() {}

                    @Override
                    public void resetProvisionalIds() {}
                };

        genesisKey = JKey.mapKey(Key.newBuilder()
                .setKeyList(KeyList.newBuilder().addKeys(MiscUtils.asKeyUnchecked(pretendKey)))
                .build());
    }

    @Test
    void successfullyEnsuresBlockedAccounts() {
        // given
        given(genesisKeySource.get()).willReturn(pretendKey);
        given(properties.getStringProperty(ACCOUNTS_BLOCKLIST_RESOURCE))
                .willReturn("evm-addresses-blocklist.csv");
        subject =
                new BlocklistAccountCreator(
                        MerkleAccount::new,
                        ids,
                        accounts,
                        genesisKeySource,
                        properties,
                        aliasManager,
                        accountNumbers);
        given(aliasManager.lookupIdBy(any())).willReturn(MISSING_NUM);

        // when
        subject.createMissingAccounts();

        // then
        final var expectedBlockedAccountsCount = 299;
        final var actualBlockedAccounts = subject.getBlockedAccountsCreated();
        assertEquals(expectedBlockedAccountsCount, actualBlockedAccounts.size());

        for (var i = FIRST_UNUSED_ID; i < FIRST_UNUSED_ID + expectedBlockedAccountsCount; i++) {
            final var blockedAccountId = IdUtils.asAccount("0.0." + i);
            final var blockedAccount = actualBlockedAccounts.get((int) (i - FIRST_UNUSED_ID));
            verify(accounts).put(blockedAccountId, blockedAccount);
            verify(aliasManager).link(blockedAccount.getAlias(), EntityNum.fromAccountId(blockedAccountId));
        }
    }

    @Test
    void forgetCreatedBlockedAccountsWorksAsExpected() {
        // given
        given(genesisKeySource.get()).willReturn(pretendKey);
        given(properties.getStringProperty(ACCOUNTS_BLOCKLIST_RESOURCE)).willReturn("test-blocklist.csv");
        given(aliasManager.lookupIdBy(any())).willReturn(MISSING_NUM);
        subject = new BlocklistAccountCreator(
                MerkleAccount::new, ids, accounts, genesisKeySource, properties, aliasManager, accountNumbers);
        subject.createMissingAccounts();

        // when
        subject.forgetCreatedBlockedAccounts();

        // then
        assertEquals(0, subject.getBlockedAccountsCreated().size());
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "non-existing.csv;Failed to read blocklist resource non-existing.csv",
                "invalid-hex-blocklist.csv;Failed to parse blocklist",
                "invalid-col-count-blocklist.csv;Failed to parse blocklist",
            },
            delimiter = ';')
    void readingblocklistResourceExceptionShouldBeLogged(String blocklistResourceName, String expectedLog) {
        // given
        given(properties.getStringProperty(ACCOUNTS_BLOCKLIST_RESOURCE)).willReturn(blocklistResourceName);
        subject = new BlocklistAccountCreator(
                MerkleAccount::new, ids, accounts, genesisKeySource, properties, aliasManager, accountNumbers);

        // when
        subject.createMissingAccounts();

        // then
        assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith(expectedLog)));
    }
}
