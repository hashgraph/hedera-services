/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.services.state.merkle.internals.ByteUtils.getHashBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.merkle.internals.ByteUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LogCaptureExtension.class)
class MerkleStakingInfoTest {
    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private MerkleStakingInfo subject;

    private final int number = 34;
    private final long minStake = 100L;
    private final long maxStake = 10_000L;
    private final long stakeToReward = 345L;
    private final long stakeToNotReward = 155L;
    private final long stakeRewardStart = 1234L;
    private final long unclaimedStakeRewardStart = stakeRewardStart / 10;
    private final long stake = 500L;
    private final long[] rewardSumHistory = new long[] {2L, 1L, 0L};
    private final EntityNum key = EntityNum.fromInt(number);

    @BeforeEach
    void setUp() {
        subject =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        stakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        subject.setKey(key);
    }

    @Test
    void managesUnclaimedStakeAsExpected() {
        assertEquals(unclaimedStakeRewardStart, subject.getUnclaimedStakeRewardStart());
        subject.resetUnclaimedStakeRewardStart();
        assertEquals(0, subject.getUnclaimedStakeRewardStart());
        subject.increaseUnclaimedStakeRewardStart(666);
        assertEquals(666, subject.getUnclaimedStakeRewardStart());
        assertEquals(stakeRewardStart - 666, subject.stakeRewardStartMinusUnclaimed());
    }

    @Test
    void objectContractsWork() {
        final long otherMinStake = 101L;
        final long otherMaxStake = 10_001L;
        final long otherStakeToReward = 344L;
        final long otherStakeToNotReward = 156L;
        final long otherStakeRewardStart = 1235L;
        final long otherUnclaimedStakeRewardStart = otherStakeRewardStart / 10 - 1;
        final long otherStake = 501L;
        final long[] otherRewardSumHistory = new long[] {3L, 2L};
        final var subject2 =
                new MerkleStakingInfo(
                        otherMinStake,
                        maxStake,
                        stakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        subject2.setKey(key);
        final var subject3 =
                new MerkleStakingInfo(
                        minStake,
                        otherMaxStake,
                        stakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        subject3.setKey(key);
        final var subject4 =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        otherStakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        subject4.setKey(key);
        final var subject5 =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        stakeToReward,
                        otherStakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        subject5.setKey(key);
        final var subject6 =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        stakeToReward,
                        stakeToNotReward,
                        otherStakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        subject6.setKey(key);
        final var subject7 =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        stakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        otherStake,
                        rewardSumHistory);
        subject7.setKey(key);
        final var subject8 =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        stakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        otherRewardSumHistory);
        subject8.setKey(key);
        final var subject10 =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        stakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        otherUnclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        subject10.setKey(key);
        final var subject9 =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        stakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        final var identical =
                new MerkleStakingInfo(
                        minStake,
                        maxStake,
                        stakeToReward,
                        stakeToNotReward,
                        stakeRewardStart,
                        unclaimedStakeRewardStart,
                        stake,
                        rewardSumHistory);
        identical.setKey(key);

        assertNotEquals(subject, new Object());
        assertNotEquals(subject, subject2);
        assertNotEquals(subject, subject3);
        assertNotEquals(subject, subject4);
        assertNotEquals(subject, subject5);
        assertNotEquals(subject, subject6);
        assertNotEquals(subject, subject7);
        assertNotEquals(subject, subject8);
        assertNotEquals(subject, subject9);
        assertNotEquals(subject, subject10);
        assertEquals(subject, identical);
        assertEquals(subject, subject);

        assertNotEquals(subject.hashCode(), subject2.hashCode());
        assertEquals(subject.hashCode(), identical.hashCode());
        assertTrue(subject.isSelfHashing());
    }

    @Test
    void toStringWorks() {
        final var expected =
                "MerkleStakingInfo{id=34, minStake=100, maxStake=10000, "
                        + "stakeToReward=345, stakeToNotReward=155, stakeRewardStart=1234, "
                        + "unclaimedStakeRewardStart=123, stake=500, rewardSumHistory=[2, 1, 0]}";

        assertEquals(expected, subject.toString());
    }

    @Test
    void gettersAndSettersWork() {
        final var props = mock(BootstrapProperties.class);
        given(props.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS)).willReturn(2);
        var subject = new MerkleStakingInfo(props);

        subject.setKey(key);
        subject.setMinStake(minStake);
        subject.setMaxStake(maxStake);
        subject.setStakeToReward(stakeToReward);
        subject.setStakeToNotReward(stakeToNotReward);
        subject.setStakeRewardStart(stakeRewardStart);
        subject.setStake(stake);
        subject.setRewardSumHistory(rewardSumHistory);

        assertEquals(number, subject.getKey().intValue());
        assertEquals(minStake, subject.getMinStake());
        assertEquals(maxStake, subject.getMaxStake());
        assertEquals(stakeToReward, subject.getStakeToReward());
        assertEquals(stakeToNotReward, subject.getStakeToNotReward());
        assertEquals(stakeRewardStart, subject.getStakeRewardStart());
        assertEquals(stake, subject.getStake());
        assertArrayEquals(rewardSumHistory, subject.getRewardSumHistory());

        subject.clearRewardSumHistory();
        assertArrayEquals(new long[] {0, 0, 0}, subject.getRewardSumHistory());
    }

    @Test
    void copyWorks() {
        var copy = subject.copy();

        assertTrue(subject.isImmutable());
        assertEquals(subject, copy);
        assertEquals(subject.getHash(), copy.getHash());
    }

    @Test
    void updatesRewardsSumHistoryWithRateLimiting() {
        final var rewardRate = 1_000_000;
        final var maxRewardRate = rewardRate / 2;

        final var pendingRewardRate =
                subject.updateRewardSumHistory(rewardRate, maxRewardRate, true);

        assertArrayEquals(new long[] {maxRewardRate + 2L, 2L, 1L}, subject.getRewardSumHistory());
        assertEquals(maxRewardRate, pendingRewardRate);
    }

    @Test
    void updatesRewardsSumHistoryAsExpectedForNodeWithGreaterThanMinStakeAndNoMoreThanMaxStake() {
        final var rewardRate = 1_000_000;

        final var pendingRewardRate =
                subject.updateRewardSumHistory(rewardRate, Long.MAX_VALUE, true);

        assertArrayEquals(new long[] {1_000_002L, 2L, 1L}, subject.getRewardSumHistory());
        assertEquals(1_000_000L, pendingRewardRate);
    }

    @Test
    void updatesRewardsSumHistoryAsExpectedForNodeWithGreaterThanMaxStake() {
        final var rewardRate = 1_000_000;

        subject.setStakeRewardStart(2 * subject.getMaxStake());
        final var pendingRewardRate =
                subject.updateRewardSumHistory(rewardRate, Long.MAX_VALUE, true);

        assertArrayEquals(new long[] {500_002L, 2L, 1L}, subject.getRewardSumHistory());
        assertEquals(500_000L, pendingRewardRate);
    }

    @Test
    void usesBiArithmeticForRewardRateDownScaling() {
        final var excessStake = 2 * subject.getMaxStake();
        final var rewardRate = Long.MAX_VALUE / (maxStake - 1);
        final var expectedScaledRate =
                BigInteger.valueOf(rewardRate)
                        .multiply(BigInteger.valueOf(maxStake))
                        .divide(BigInteger.valueOf(excessStake))
                        .longValueExact();

        subject.setStakeRewardStart(excessStake);
        final var pendingRewardRate =
                subject.updateRewardSumHistory(rewardRate, Long.MAX_VALUE, true);

        assertArrayEquals(
                new long[] {expectedScaledRate + 2L, 2L, 1L}, subject.getRewardSumHistory());
        assertEquals(expectedScaledRate, pendingRewardRate);
    }

    @Test
    void updatesRewardsSumHistoryAsExpectedForNodeWithLessThanMinStakeWhenMinIsReqForReward() {
        final var rewardRate = 1_000_000_000;

        subject.setStake(0);
        final var pendingRewardRate =
                subject.updateRewardSumHistory(rewardRate, Long.MAX_VALUE, true);

        assertArrayEquals(new long[] {2L, 2L, 1L}, subject.getRewardSumHistory());
        assertEquals(0L, pendingRewardRate);
    }

    @Test
    void updatesRewardsSumHistoryAsExpectedForNodeWithLessThanMinStakeWhenMinIsNotReqForReward() {
        final var rewardRate = 1_000_000_000;

        subject.setStake(0);
        subject.setStakeRewardStart(subject.getMinStake() - 1);
        final var pendingRewardRate =
                subject.updateRewardSumHistory(rewardRate, Long.MAX_VALUE, false);

        assertArrayEquals(new long[] {1000000002L, 2L, 1L}, subject.getRewardSumHistory());
        assertEquals(rewardRate, pendingRewardRate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void logsAtErrorIfSomehowHashComputationFails() {
        final var mockedStatic = mockStatic(ByteUtils.class);
        mockedStatic
                .when(() -> ByteUtils.getHashBytes(rewardSumHistory))
                .thenThrow(UncheckedIOException.class);

        final var hash = subject.getHash();
        assertSame(EMPTY_HASH, hash);

        assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Hash computation failed")));
        mockedStatic.close();
    }

    @Test
    void hashSummarizesAsExpected() throws IOException {
        final var baos = new ByteArrayOutputStream();
        final var out = new SerializableDataOutputStream(baos);
        final var rewardSumHistoryHash = getHashBytes(rewardSumHistory);
        out.writeInt(number);
        out.writeLong(minStake);
        out.writeLong(maxStake);
        out.writeLong(stakeToReward);
        out.writeLong(stakeToNotReward);
        out.writeLong(stakeRewardStart);
        out.writeLong(unclaimedStakeRewardStart);
        out.writeLong(stake);
        out.write(rewardSumHistoryHash);

        final var expected = CommonUtils.noThrowSha384HashOf(baos.toByteArray());
        final var actual = subject.getHash();

        assertNotNull(subject.getHistoryHash());
        assertArrayEquals(rewardSumHistoryHash, subject.getHistoryHash());
        assertArrayEquals(expected, actual.getValue());
    }

    @Test
    void cannotUnclaimMoreThanStakedRewardStart() {
        subject.increaseUnclaimedStakeRewardStart(stakeRewardStart - unclaimedStakeRewardStart + 1);
        assertEquals(stakeRewardStart, subject.getUnclaimedStakeRewardStart());
        assertEquals(
                "Asked to release 1112 more rewards for node34 (now 1235), but only 1234 was"
                        + " staked",
                logCaptor.warnLogs().get(0));
    }

    @Test
    void representsNonZeroHistoryAsExpected() {
        final var historyNoZeros = new long[] {5L, 4L, 3L};
        assertEquals("[5, 4, 3]", MerkleStakingInfo.readableNonZeroHistory(historyNoZeros));

        final var historyWithZeros = new long[] {5L, 4L, 3L, 0L, 0L, 0L};
        assertEquals("[5, 4, 3]", MerkleStakingInfo.readableNonZeroHistory(historyWithZeros));
    }
}
