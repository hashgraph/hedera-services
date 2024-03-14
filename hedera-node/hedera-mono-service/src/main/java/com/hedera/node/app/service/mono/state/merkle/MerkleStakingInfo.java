/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.merkle;

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

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.service.mono.ServicesState.EMPTY_HASH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.node.app.service.mono.state.merkle.internals.ByteUtils.getHashBytes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.base.state.MutabilityException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MerkleStakingInfo extends PartialMerkleLeaf implements Keyed<EntityNum>, MerkleLeaf {

    private static final Logger log = LogManager.getLogger(MerkleStakingInfo.class);

    static final int RELEASE_0270_VERSION = 1;
    static final int RELEASE_0371_VERSION = 2;
    public static final int CURRENT_VERSION = RELEASE_0371_VERSION;
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
    // The consensus weight of this node in the network. This is computed based on the stake of this node
    // at midnight UTC of the current day. If the stake of this node is less than minStake, then the
    // weight is 0. Sum of all weights of nodes in the network should be less than 500.
    // If the stake of this node A is greater than minStake,
    // then A's weight is computed as (node A stake * 500/ total stake of all nodes).
    private int weight;

    @Nullable
    private byte[] historyHash;

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
        this.weight = that.weight;
    }

    /**
     * Given up-to-date values for this node's stake to reward both at the start of this period, and
     * now; and its stake to not reward now, updates this staking info to reflect the new values.
     *
     * <p>Ideally, we would be able to recompute and fix {@code stakeRewardStart} here. But we
     * cannot since an account might have changed its staking metadata since the beginning of the
     * period, meaning the state has "forgotten" everything about its metadata at the start of the
     * period.
     *
     * @param stakeToReward the node's current stake to reward
     * @param stakeToNotReward the node's current stake to not reward
     */
    public void syncRecomputedStakeValues(final long stakeToReward, final long stakeToNotReward) {
        if (stakeToReward != this.stakeToReward) {
            log.warn(
                    "Stake to reward for node {} was recomputed from {} to {}",
                    number,
                    this.stakeToReward,
                    stakeToReward);
        }
        if (stakeToNotReward != this.stakeToNotReward) {
            log.warn(
                    "Stake to not reward for node {} was recomputed from {} to {}",
                    number,
                    this.stakeToNotReward,
                    stakeToNotReward);
        }
        this.stakeToReward = stakeToReward;
        this.stakeToNotReward = stakeToNotReward;
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

    /**
     * Clamps the stake value. If the stake is less than minStake, then it is set to 0. If the stake
     * is greater than maxStake, then it is set to maxStake. Otherwise, it is set to the given
     * value.
     * @return the clamped stake value
     */
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
            final long perHbarRate, final long maxPerHbarRate, final boolean requireMinStakeToReward) {
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
        final var rewardableStake = requireMinStakeToReward ? Math.min(stakeRewardStart, stake) : stakeRewardStart;
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
                perHbarRateThisNode = BigInteger.valueOf(perHbarRateThisNode)
                        .multiply(BigInteger.valueOf(maxStake))
                        .divide(BigInteger.valueOf(stakeRewardStart))
                        .longValueExact();
            }
        }
        perHbarRateThisNode = Math.min(perHbarRateThisNode, maxPerHbarRate);
        rewardSumHistory[0] += perHbarRateThisNode;

        log.info("   > Non-zero reward sum history is now {}", () -> readableNonZeroHistory(rewardSumHistory));
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

    public void setUnclaimedStakeRewardStart(long unclaimedStakeRewardStart) {
        this.unclaimedStakeRewardStart = unclaimedStakeRewardStart;
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

    public int getWeight() {
        return weight;
    }

    public void setWeight(final int weight) {
        assertMutable("weight");
        this.weight = weight;
    }

    @Override
    public MerkleStakingInfo copy() {
        setImmutable(true);
        return new MerkleStakingInfo(this);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        number = in.readInt();
        minStake = in.readLong();
        maxStake = in.readLong();
        stakeToReward = in.readLong();
        stakeToNotReward = in.readLong();
        stakeRewardStart = in.readLong();
        unclaimedStakeRewardStart = in.readLong();
        stake = in.readLong();
        if (version >= RELEASE_0371_VERSION) {
            weight = in.readInt();
        }
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || MerkleStakingInfo.class != o.getClass()) {
            return false;
        }

        final var that = (MerkleStakingInfo) o;
        return this.number == that.number
                && this.minStake == that.minStake
                && this.maxStake == that.maxStake
                && this.stakeToReward == that.stakeToReward
                && this.stakeToNotReward == that.stakeToNotReward
                && this.stakeRewardStart == that.stakeRewardStart
                && this.unclaimedStakeRewardStart == that.unclaimedStakeRewardStart
                && this.stake == that.stake
                && Arrays.equals(this.rewardSumHistory, that.rewardSumHistory)
                && this.weight == that.weight;
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
                Arrays.hashCode(rewardSumHistory),
                weight);
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
                .add("weight", weight)
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
        } catch (final IOException | UncheckedIOException e) {
            log.error(String.format("Hash computation failed on node %d", number), e);
            return EMPTY_HASH;
        }
        return new Hash(noThrowSha384HashOf(baos.toByteArray()), DigestType.SHA_384);
    }

    // Internal helpers
    private void assertMutable(final String proximalField) {
        if (isImmutable()) {
            throw new MutabilityException("Cannot set " + proximalField + " on an immutable StakingInfo!");
        }
    }

    private void assertMutableRewardSumHistory() {
        assertMutable("rewardSumHistory");
    }

    private void serializeNonHistoryData(final SerializableDataOutputStream out) throws IOException {
        out.writeInt(number);
        out.writeLong(minStake);
        out.writeLong(maxStake);
        out.writeLong(stakeToReward);
        out.writeLong(stakeToNotReward);
        out.writeLong(stakeRewardStart);
        out.writeLong(unclaimedStakeRewardStart);
        out.writeLong(stake);
        out.writeInt(weight);
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
                (firstZero == -1) ? rewardSumHistory : Arrays.copyOfRange(rewardSumHistory, 0, firstZero));
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
            final long[] rewardSumHistory,
            final int weight) {
        this.minStake = minStake;
        this.maxStake = maxStake;
        this.stakeToReward = stakeToReward;
        this.stakeToNotReward = stakeToNotReward;
        this.stakeRewardStart = stakeRewardStart;
        this.unclaimedStakeRewardStart = unclaimedStakeRewardStart;
        this.stake = stake;
        this.rewardSumHistory = rewardSumHistory;
        this.weight = weight;
    }

    @Nullable
    @VisibleForTesting
    public byte[] getHistoryHash() {
        return historyHash;
    }

    @VisibleForTesting
    public int numRewardablePeriods() {
        return rewardSumHistory.length;
    }
}
