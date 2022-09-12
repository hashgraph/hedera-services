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

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.state.merkle.internals.ByteUtils.getHashBytes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MerkleStakingInfo extends PartialMerkleLeaf implements Keyed<EntityNum>, MerkleLeaf {
    private static final Logger log = LogManager.getLogger(MerkleStakingInfo.class);

    static final int RELEASE_0270_VERSION = 1;
    static final int CURRENT_VERSION = RELEASE_0270_VERSION;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xb8b383ccd3caed5bL;

    private int number;
    private long minStake;
    private long maxStake;
    private long stakeToReward;
    private long stakeToNotReward;
    private long stakeRewardStart;
    // Tracks how much stake from stakeRewardStart will have unclaimed rewards due to
    // accounts changing their staking metadata in a way that disqualifies them for the
    // current period; reset at the beginning of every period
    private long unclaimedStakeRewardStart;
    private long stake;
    private long[] rewardSumHistory;
    @Nullable private byte[] historyHash;

    public MerkleStakingInfo() {
        // RuntimeConstructable
    }

    public MerkleStakingInfo(final BootstrapProperties properties) {
        this(properties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS));
    }

    public MerkleStakingInfo(final int numRewardablePeriods) {
        rewardSumHistory = new long[numRewardablePeriods + 1];
    }

    public MerkleStakingInfo(final MerkleStakingInfo that) {
        this.number = that.number;
        this.minStake = that.minStake;
        this.maxStake = that.maxStake;
        this.stakeToReward = that.stakeToReward;
        this.stakeToNotReward = that.stakeToNotReward;
        this.stakeRewardStart = that.stakeRewardStart;
        this.stake = that.stake;
        this.rewardSumHistory = that.rewardSumHistory;
        this.historyHash = that.historyHash;
        this.unclaimedStakeRewardStart = that.unclaimedStakeRewardStart;
    }

    public void removeRewardStake(final long amount, final boolean declinedReward) {
        if (declinedReward) {
            this.stakeToNotReward -= amount;
        } else {
            this.stakeToReward -= amount;
        }
    }

    public void addRewardStake(final long amount, final boolean declinedReward) {
        if (declinedReward) {
            this.stakeToNotReward += amount;
        } else {
            this.stakeToReward += amount;
        }
    }

    public long reviewElectionsAndRecomputeStakes() {
        final var totalStake = stakeToReward + stakeToNotReward;
        if (totalStake > maxStake) {
            setStake(maxStake);
        } else if (totalStake < minStake) {
            setStake(0);
        } else {
            setStake(totalStake);
        }
        stakeRewardStart = stakeToReward;
        return stakeRewardStart;
    }

    public long updateRewardSumHistory(
            final long perHbarRate,
            final long maxPerHbarRate,
            final boolean requireMinStakeToReward) {
        assertMutableRewardSumHistory();
        rewardSumHistory = Arrays.copyOf(rewardSumHistory, rewardSumHistory.length);
        final var droppedRewardSum = rewardSumHistory[rewardSumHistory.length - 1];
        for (int i = rewardSumHistory.length - 1; i > 0; i--) {
            rewardSumHistory[i] = rewardSumHistory[i - 1] - droppedRewardSum;
        }
        rewardSumHistory[0] -= droppedRewardSum;

        long perHbarRateThisNode = 0;
        // If this node was "active"---i.e., node.numRoundsWithJudge / numRoundsInPeriod >=
        // activeThreshold---and
        // it had non-zero stakedReward at the start of the ending staking period, then it should
        // give rewards
        // for this staking period, unless its effective stake was less than minStake, and hence
        // zero here (note
        // the active condition will only be checked in a later release)
        final var rewardableStake =
                requireMinStakeToReward ? Math.min(stakeRewardStart, stake) : stakeRewardStart;
        if (rewardableStake > 0) {
            perHbarRateThisNode = perHbarRate;
            // But if the node had more the maximum stakeRewardStart, "down-scale" its reward rate
            // to
            // ensure the accounts staking to this node will receive a fraction of the total rewards
            // that
            // does not exceed node.stakedRewardStart / totalStakedRewardedStart; use
            // arbitrary-precision
            // arithmetic because there is no inherent bound on (maxStake * perHbarRateThisNode)
            if (stakeRewardStart > maxStake) {
                perHbarRateThisNode =
                        BigInteger.valueOf(perHbarRateThisNode)
                                .multiply(BigInteger.valueOf(maxStake))
                                .divide(BigInteger.valueOf(stakeRewardStart))
                                .longValueExact();
            }
        }
        perHbarRateThisNode = Math.min(perHbarRateThisNode, maxPerHbarRate);
        rewardSumHistory[0] += perHbarRateThisNode;

        log.info(
                "   > Non-zero reward sum history is now {}",
                () -> readableNonZeroHistory(rewardSumHistory));
        // reset the historyHash
        historyHash = null;
        return perHbarRateThisNode;
    }

    public void clearRewardSumHistory() {
        assertMutableRewardSumHistory();
        rewardSumHistory = Arrays.copyOf(rewardSumHistory, rewardSumHistory.length);
        // reset rewardSumHistory array.
        Arrays.fill(rewardSumHistory, 0);
        // reset the historyHash
        historyHash = null;
    }

    public long getUnclaimedStakeRewardStart() {
        return unclaimedStakeRewardStart;
    }

    public void resetUnclaimedStakeRewardStart() {
        unclaimedStakeRewardStart = 0;
    }

    public void increaseUnclaimedStakeRewardStart(final long amount) {
        unclaimedStakeRewardStart += amount;
        if (unclaimedStakeRewardStart > stakeRewardStart) {
            log.warn(
                    "Asked to release {} more rewards for node{} (now {}), but only {} was staked",
                    amount,
                    number,
                    unclaimedStakeRewardStart,
                    stakeRewardStart);
            unclaimedStakeRewardStart = stakeRewardStart;
        }
    }

    public long stakeRewardStartMinusUnclaimed() {
        return stakeRewardStart - unclaimedStakeRewardStart;
    }

    public long getMinStake() {
        return minStake;
    }

    public void setMinStake(final long minStake) {
        assertMutable("minStake");
        this.minStake = minStake;
    }

    public long getMaxStake() {
        return maxStake;
    }

    public void setMaxStake(final long maxStake) {
        assertMutable("maxStake");
        this.maxStake = maxStake;
    }

    public long getStakeToReward() {
        return stakeToReward;
    }

    public void setStakeToReward(final long stakeToReward) {
        assertMutable("stakeToReward");
        this.stakeToReward = stakeToReward;
    }

    public long getStakeToNotReward() {
        return stakeToNotReward;
    }

    public void setStakeToNotReward(final long stakeToNotReward) {
        assertMutable("stakeToNotReward");
        this.stakeToNotReward = stakeToNotReward;
    }

    public long getStakeRewardStart() {
        return stakeRewardStart;
    }

    public void setStakeRewardStart(final long stakeRewardStart) {
        assertMutable("stakeRewardStart");
        this.stakeRewardStart = stakeRewardStart;
    }

    public long getStake() {
        return stake;
    }

    public void setStake(final long stake) {
        assertMutable("stake");
        this.stake = stake;
    }

    public long[] getRewardSumHistory() {
        return rewardSumHistory;
    }

    public void setRewardSumHistory(final long[] rewardSumHistory) {
        assertMutableRewardSumHistory();
        this.rewardSumHistory = rewardSumHistory;
    }

    @Override
    public MerkleStakingInfo copy() {
        setImmutable(true);
        return new MerkleStakingInfo(this);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        number = in.readInt();
        minStake = in.readLong();
        maxStake = in.readLong();
        stakeToReward = in.readLong();
        stakeToNotReward = in.readLong();
        stakeRewardStart = in.readLong();
        unclaimedStakeRewardStart = in.readLong();
        stake = in.readLong();
        rewardSumHistory = in.readLongArray(Integer.MAX_VALUE);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serializeNonHistoryData(out);
        out.writeLongArray(rewardSumHistory);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public EntityNum getKey() {
        return EntityNum.fromInt(number);
    }

    @Override
    public void setKey(final EntityNum entityNum) {
        this.number = entityNum.intValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || MerkleStakingInfo.class != o.getClass()) {
            return false;
        }

        var that = (MerkleStakingInfo) o;
        return this.number == that.number
                && this.minStake == that.minStake
                && this.maxStake == that.maxStake
                && this.stakeToReward == that.stakeToReward
                && this.stakeToNotReward == that.stakeToNotReward
                && this.stakeRewardStart == that.stakeRewardStart
                && this.unclaimedStakeRewardStart == that.unclaimedStakeRewardStart
                && this.stake == that.stake
                && Arrays.equals(this.rewardSumHistory, that.rewardSumHistory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                minStake,
                maxStake,
                stakeToReward,
                stakeToNotReward,
                stakeRewardStart,
                unclaimedStakeRewardStart,
                stake,
                Arrays.hashCode(rewardSumHistory));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleStakingInfo.class)
                .add("id", number)
                .add("minStake", minStake)
                .add("maxStake", maxStake)
                .add("stakeToReward", stakeToReward)
                .add("stakeToNotReward", stakeToNotReward)
                .add("stakeRewardStart", stakeRewardStart)
                .add("unclaimedStakeRewardStart", unclaimedStakeRewardStart)
                .add("stake", stake)
                .add("rewardSumHistory", rewardSumHistory)
                .toString();
    }

    @Override
    public boolean isSelfHashing() {
        return true;
    }

    @Override
    public Hash getHash() {
        final var baos = new ByteArrayOutputStream();
        try (final var out = new SerializableDataOutputStream(baos)) {
            ensureHistoryHashIsKnown();
            serializeNonHistoryData(out);
            out.write(historyHash);
        } catch (IOException | UncheckedIOException e) {
            log.error(String.format("Hash computation failed on node %d", number), e);
            return EMPTY_HASH;
        }
        return new Hash(noThrowSha384HashOf(baos.toByteArray()), DigestType.SHA_384);
    }

    // Internal helpers
    private void assertMutable(String proximalField) {
        if (isImmutable()) {
            throw new MutabilityException(
                    "Cannot set " + proximalField + " on an immutable StakingInfo!");
        }
    }

    private void assertMutableRewardSumHistory() {
        assertMutable("rewardSumHistory");
    }

    private void serializeNonHistoryData(final SerializableDataOutputStream out)
            throws IOException {
        out.writeInt(number);
        out.writeLong(minStake);
        out.writeLong(maxStake);
        out.writeLong(stakeToReward);
        out.writeLong(stakeToNotReward);
        out.writeLong(stakeRewardStart);
        out.writeLong(unclaimedStakeRewardStart);
        out.writeLong(stake);
    }

    private void ensureHistoryHashIsKnown() {
        if (historyHash == null) {
            historyHash = getHashBytes(rewardSumHistory);
        }
    }

    @VisibleForTesting
    static String readableNonZeroHistory(final long[] rewardSumHistory) {
        int firstZero = -1;
        for (int i = 0; i < rewardSumHistory.length; i++) {
            if (rewardSumHistory[i] == 0) {
                firstZero = i;
                break;
            }
        }
        return Arrays.toString(
                (firstZero == -1)
                        ? rewardSumHistory
                        : Arrays.copyOfRange(rewardSumHistory, 0, firstZero));
    }

    @VisibleForTesting
    public MerkleStakingInfo(
            final long minStake,
            final long maxStake,
            final long stakeToReward,
            final long stakeToNotReward,
            final long stakeRewardStart,
            final long unclaimedStakeRewardStart,
            final long stake,
            final long[] rewardSumHistory) {
        this.minStake = minStake;
        this.maxStake = maxStake;
        this.stakeToReward = stakeToReward;
        this.stakeToNotReward = stakeToNotReward;
        this.stakeRewardStart = stakeRewardStart;
        this.unclaimedStakeRewardStart = unclaimedStakeRewardStart;
        this.stake = stake;
        this.rewardSumHistory = rewardSumHistory;
    }

    @Nullable
    @VisibleForTesting
    byte[] getHistoryHash() {
        return historyHash;
    }
}
