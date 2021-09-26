package com.hedera.services.txns.network;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_PREPARED_UPDATE_FILE_NUM;
import static com.hedera.services.state.merkle.MerkleNetworkContext.UPDATE_FILE_HASH_LEN;
import static com.hedera.services.utils.MiscUtils.timestampToInstant;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_FREEZE_IS_SCHEDULED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE;

@Singleton
public class FreezeTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(FreezeTransitionLogic.class);

	private final UpgradeHelper upgradeHelper;
	private final TransactionContext txnCtx;
	private final Supplier<MerkleSpecialFiles> specialFiles;
	private final Supplier<MerkleNetworkContext> networkCtx;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validateBasics;

	@Inject
	public FreezeTransitionLogic(
			final UpgradeHelper upgradeHelper,
			final TransactionContext txnCtx,
			final Supplier<MerkleSpecialFiles> specialFiles,
			final Supplier<MerkleNetworkContext> networkCtx
	) {
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
		this.specialFiles = specialFiles;
		this.upgradeHelper = upgradeHelper;
	}

	@Override
	public void doStateTransition() {
		final var op = txnCtx.accessor().getTxn().getFreeze();

		assertValidityAtCons(op);

		switch (op.getFreezeType()) {
			case PREPARE_UPGRADE:
				final var archiveData = specialFiles.get().get(op.getUpdateFile());
				upgradeHelper.prepareUpgradeNow(archiveData);
				networkCtx.get().recordPreparedUpgrade(op);
				break;
			case FREEZE_ONLY:
				networkCtx.get().rollbackPreparedUpgrade();
			case FREEZE_UPGRADE:
				upgradeHelper.scheduleFreezeAt(timestampToInstant(op.getStartTime()));
				break;
			case FREEZE_ABORT:
				upgradeHelper.abortScheduledFreeze();
				networkCtx.get().rollbackPreparedUpgrade();
				break;
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasFreeze;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	private ResponseCodeEnum validateBasics(TransactionBody freezeTxn) {
		final var op = freezeTxn.getFreeze();

		if (op.getStartHour() != 0 || op.getStartMin() != 0 || op.getEndHour() != 0 || op.getEndMin() != 0) {
			return INVALID_FREEZE_TRANSACTION_BODY;
		}

		switch (op.getFreezeType()) {
			case FREEZE_ONLY:
			case FREEZE_UPGRADE:
				return freezeTimeValidity(
						op.getStartTime(),
						timestampToInstant(freezeTxn.getTransactionID().getTransactionValidStart()));
			case PREPARE_UPGRADE:
				return sanityCheckUpgradeMeta(op.getUpdateFile(), op.getFileHash());
			default:
				return OK;
		}
	}

	private ResponseCodeEnum sanityCheckUpgradeMeta(final FileID updateFile, final ByteString allegedSha384Hash) {
		if (!specialFiles.get().contains(updateFile)) {
			return FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
		}
		if (allegedSha384Hash.size() != UPDATE_FILE_HASH_LEN) {
			return FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
		}
		return OK;
	}

	private ResponseCodeEnum freezeTimeValidity(final Timestamp freezeStartTime, final Instant now) {
		return isInTheFuture(freezeStartTime, now) ? OK : FREEZE_START_TIME_MUST_BE_FUTURE;
	}

	private void assertValidityAtCons(FreezeTransactionBody op) {
		switch (op.getFreezeType()) {
			case FREEZE_UPGRADE:
				assertValidFreezeUpgrade();
			case FREEZE_ONLY:
				final var timeIsValid = isInTheFuture(op.getStartTime(), txnCtx.consensusTime());
				validateTrue(timeIsValid, FREEZE_START_TIME_MUST_BE_FUTURE);
				break;
			case FREEZE_ABORT:
				validateTrue(upgradeHelper.isFreezeScheduled(), NO_FREEZE_IS_SCHEDULED);
				break;
			case PREPARE_UPGRADE:
				assertValidPrepareUpgrade(op);
				break;
		}
	}

	private void assertValidPrepareUpgrade(FreezeTransactionBody op) {
		final var curSpecialFiles = specialFiles.get();
		final boolean isMatch = curSpecialFiles.hashMatches(op.getUpdateFile(), op.getFileHash().toByteArray());
		validateTrue(isMatch, FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
	}

	private void assertValidFreezeUpgrade() {
		final var curNetworkCtx = networkCtx.get();
		final var preparedFileNum = curNetworkCtx.getPreparedUpdateFileNum();
		validateTrue(preparedFileNum != NO_PREPARED_UPDATE_FILE_NUM, NO_UPGRADE_HAS_BEEN_PREPARED);

		final var isHashUnchanged = curNetworkCtx.isPreparedFileHashValidGiven(specialFiles.get());
		validateTrue(isHashUnchanged, UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE);
	}

	private boolean isInTheFuture(final Timestamp freezeStartTime, final Instant now) {
		return Instant.ofEpochSecond(freezeStartTime.getSeconds(), freezeStartTime.getNanos()).isAfter(now);
	}
}
