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

package com.hedera.node.app.service.mono.utils.replay;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters.orderedFungibleAllowances;
import static com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters.orderedFungibleAllowancesFrom;
import static com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters.orderedHbarAllowances;
import static com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters.orderedHbarAllowancesFrom;
import static com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters.orderedOperatorApprovals;
import static com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters.orderedOperatorApprovalsFrom;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.fcqueue.FCQueue;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

class PbjLeafConvertersTest {
    private static final long AUTO_RENEW_DURATION_SECONDS = 7776000L;
    private static final EntityNum SOME_NUMBER = EntityNum.fromLong(666L);
    private static final long SOME_SEQ_NO = 789L;
    private static final byte[] SOME_RUNNING_HASH = "abcdefghabcdefghabcdefghabcdefghabcdefghabcdefgh".getBytes();
    private static final RichInstant EXPIRY = new RichInstant(1_234_567, 890);

    @Test
    void canConvertTopicWithNoOptionalFields() {
        final var topic = new MerkleTopic();
        setRequiredFieldsOnTopic(topic);

        final var pbjTopic = PbjLeafConverters.topicFromMerkle(topic);

        assertRequiredFieldsAsExpected(pbjTopic);

        assertTrue(pbjTopic.memo().isEmpty());
        assertFalse(pbjTopic.hasAdminKey());
        assertFalse(pbjTopic.hasSubmitKey());
        assertEquals(ExpiryMeta.NA, pbjTopic.autoRenewAccountNumber());
    }

    @Test
    void canConvertTopicWithAllOptionalFields() {
        final var memo = "Some memo text";
        final var autoRenewId = new EntityId(0, 0, 1234);
        final var adminKey = new JEd25519Key("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes());
        final var submitKey = new JEd25519Key("cccccccccccccccccccccccccccccccc".getBytes());

        final var topic = new MerkleTopic();
        setRequiredFieldsOnTopic(topic);
        topic.setMemo(memo);
        topic.setAdminKey(adminKey);
        topic.setSubmitKey(submitKey);
        topic.setAutoRenewAccountId(autoRenewId);

        final var pbjTopic = PbjLeafConverters.topicFromMerkle(topic);
        assertRequiredFieldsAsExpected(pbjTopic);
        assertEquals(memo, pbjTopic.memo());
        assertEquals(toPbj(adminKey), pbjTopic.adminKey());
        assertEquals(toPbj(submitKey), pbjTopic.submitKey());
        assertEquals(autoRenewId.num(), pbjTopic.autoRenewAccountNumber());
    }

    @Test
    void canConvertMerkleAccounts() {
        final var r = new SplittableRandom(1_234_567L);
        final var source = new SeededPropertySource(r);
        final var n = 10;

        for (int i = 0; i < n; i++) {
            final var accountState = source.nextAccountState();
            final var merkleAccount = new MerkleAccount(List.of(accountState, new FCQueue<>()));
            final var pbjAccount = PbjLeafConverters.accountFromMerkle(merkleAccount);
            assertAccountsMatch(merkleAccount, pbjAccount);
        }
    }

    private void assertAccountsMatch(
            @NonNull final MerkleAccount merkleAccount,
            @NonNull final Account account) {
        assertEquals(merkleAccount.getKey().longValue(), account.accountNumber());
        assertEquals(merkleAccount.getNftsOwned(), account.numberOwnedNfts());
        assertEquals(merkleAccount.getNumTreasuryTitles(), account.numberTreasuryTitles());
        assertEquals(merkleAccount.getMemo(), account.memo());
        assertEquals(merkleAccount.isSmartContract(), account.smartContract());
        assertPbjBytesEqual(merkleAccount.getAlias().toByteArray(), account.alias());
        assertEquals(merkleAccount.getEthereumNonce(), account.ethereumNonce());
        assertEquals(merkleAccount.getNumAssociations(), account.numberAssociations());
        assertEquals(merkleAccount.getNumPositiveBalances(), account.numberPositiveBalances());
        assertEquals(merkleAccount.getHeadTokenId(), account.headTokenNumber());
        assertEquals(merkleAccount.getHeadNftSerialNum(), account.headNftSerialNumber());
        assertEquals(merkleAccount.getBalance(), account.tinybarBalance());
        assertEquals(merkleAccount.isReceiverSigRequired(), account.receiverSigRequired());
        assertEquals(toPbj(merkleAccount.getAccountKey()), account.key());
        assertEquals(merkleAccount.getAutoRenewSecs(), account.autoRenewSecs());
        assertEquals(merkleAccount.isDeleted(), account.deleted());
        assertEquals(merkleAccount.getExpiry(), account.expiry());
        assertEquals(merkleAccount.getMaxAutomaticAssociations(), account.maxAutoAssociations());
        assertEquals(merkleAccount.getUsedAutoAssociations(), account.usedAutoAssociations());
        assertEquals(merkleAccount.getNumContractKvPairs(), account.contractKvPairsNumber());
        // TODO - represent and match merkleAccount.getFirstUint256Key()
        assertEquals(
                orderedHbarAllowancesFrom(merkleAccount),
                orderedHbarAllowances(account.cryptoAllowancesOrThrow()));
        assertEquals(
                orderedOperatorApprovalsFrom(merkleAccount),
                orderedOperatorApprovals(account.approveForAllNftAllowancesOrThrow()));
        assertEquals(
                orderedFungibleAllowancesFrom(merkleAccount),
                orderedFungibleAllowances(account.tokenAllowancesOrThrow()));
        assertEquals(merkleAccount.isDeclinedReward(), account.declineReward());
        assertEquals(merkleAccount.totalStakeAtStartOfLastRewardedPeriod(), account.stakeAtStartOfLastRewardedPeriod());
        assertEquals(merkleAccount.getStakedToMe(), account.stakedToMe());
        assertEquals(merkleAccount.getStakePeriodStart(), account.stakePeriodStart());
        assertEquals(merkleAccount.getStakedId(), account.stakedNumber());
        assertEquals(merkleAccount.getAutoRenewAccount().num(), account.autoRenewAccountNumber());
        assertEquals(merkleAccount.isExpiredAndPendingRemoval(), account.expiredAndPendingRemoval());
    }

    private void assertRequiredFieldsAsExpected(final Topic topic) {
        assertEquals(AUTO_RENEW_DURATION_SECONDS, topic.autoRenewPeriod());
        assertPbjBytesEqual(SOME_RUNNING_HASH, topic.runningHash());
        assertEquals(SOME_NUMBER.longValue(), topic.topicNumber());
        assertEquals(SOME_SEQ_NO, topic.sequenceNumber());
        assertEquals(EXPIRY.getSeconds(), topic.expiry());
        assertTrue(topic.deleted());
    }

    private void setRequiredFieldsOnTopic(final MerkleTopic topic) {
        topic.setKey(SOME_NUMBER);
        topic.setAutoRenewDurationSeconds(AUTO_RENEW_DURATION_SECONDS);
        topic.setExpirationTimestamp(EXPIRY);
        topic.setDeleted(true);
        topic.setSequenceNumber(SOME_SEQ_NO);
        topic.setRunningHash(SOME_RUNNING_HASH);
    }

    public static void assertPbjBytesEqual(final byte[] expected, final Bytes actual) {
        assertArrayEquals(expected, PbjConverter.asBytes(actual));
    }
}
