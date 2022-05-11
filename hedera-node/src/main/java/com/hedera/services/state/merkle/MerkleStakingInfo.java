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

import com.hedera.services.utils.EntityNum;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;

import java.io.IOException;

public class MerkleStakingInfo extends AbstractMerkleLeaf implements Keyed<EntityNum> {
	static final int MAX_REWARD_HISTORY = 336;
	static final long[] EMPTY_REWARD_HISTORY = new long[MAX_REWARD_HISTORY];

	static final int RELEASE_0270_VERSION = 1;
	static final int CURRENT_VERSION = RELEASE_0270_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xb8b383ccd3caed5bL;

	private int number;
	private long minStake;
	private long maxStake;
	private long stakeToReward;
	private long stakeToNotReward;
	private long stakeRewardStart;
	private long stake;
	private long[] rewardSumHistory = EMPTY_REWARD_HISTORY;

	public MerkleStakingInfo() {}

	public MerkleStakingInfo(
			final long minStake,
			final long maxStake,
			final long stakeToReward,
			final long stakeToNotReward,
			final long stakeRewardStart,
			final long stake,
			final long[] rewardSumHistory) {
		this.minStake = minStake;
		this.maxStake = maxStake;
		this.stakeToReward = stakeToReward;
		this.stakeToNotReward = stakeToNotReward;
		this.stakeRewardStart = stakeRewardStart;
		this.stake = stake;
		this.rewardSumHistory = rewardSumHistory;
	}

	public MerkleStakingInfo(MerkleStakingInfo that) {
		this.minStake = that.minStake;
		this.maxStake = that.maxStake;
		this.stakeToReward = that.stakeToReward;
		this.stakeToNotReward = that.stakeToNotReward;
		this.stakeRewardStart = that.stakeRewardStart;
		this.stake = that.stake;
		this.rewardSumHistory = that.rewardSumHistory;
	}

	@Override
	public AbstractMerkleLeaf copy() {
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
		stake = in.readLong();
		rewardSumHistory = in.readLongArray(MAX_REWARD_HISTORY);
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeInt(number);
		out.writeLong(minStake);
		out.writeLong(maxStake);
		out.writeLong(stakeToReward);
		out.writeLong(stakeToNotReward);
		out.writeLong(stakeRewardStart);
		out.writeLong(stake);
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

	public long getMinStake() {
		return minStake;
	}

	public void setMinStake(final long minStake) {
		this.minStake = minStake;
	}

	public long getMaxStake() {
		return maxStake;
	}

	public void setMaxStake(final long maxStake) {
		this.maxStake = maxStake;
	}

	public long getStakeToReward() {
		return stakeToReward;
	}

	public void setStakeToReward(final long stakeToReward) {
		this.stakeToReward = stakeToReward;
	}

	public long getStakeToNotReward() {
		return stakeToNotReward;
	}

	public void setStakeToNotReward(final long stakeToNotReward) {
		this.stakeToNotReward = stakeToNotReward;
	}

	public long getStakeRewardStart() {
		return stakeRewardStart;
	}

	public void setStakeRewardStart(final long stakeRewardStart) {
		this.stakeRewardStart = stakeRewardStart;
	}

	public long getStake() {
		return stake;
	}

	public void setStake(final long stake) {
		this.stake = stake;
	}

	public long[] getRewardSumHistory() {
		return rewardSumHistory;
	}

	public void setRewardSumHistory(final long[] rewardSumHistory) {
		this.rewardSumHistory = rewardSumHistory;
	}
}
