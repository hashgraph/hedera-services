package com.hedera.services.pricing;

/*-
 * ‌
 * Hedera Services API Fees
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
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.consensus.ConsensusOpsUsage;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.fee.FeeBuilder;

import java.util.List;

/**
 * Provides the resource usage of the "base configuration" for each Hedera operation.
 *
 * The base configuration of an operation is usually the cheapest version of the
 * operation that still does something useful. (For example, the base CryptoTransfer
 * adjusts only two ℏ accounts using one signature, the base TokenFeeScheduleUpdate
 * adds a single custom HTS fee to a token, etc.)
 */
class BaseOperationUsage {
	private static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	private static final ByteString CANONICAL_SIG = ByteString.copyFromUtf8(
			"0123456789012345678901234567890123456789012345678901234567890123");
	private static final SignatureMap ONE_PAIR_SIG_MAP = SignatureMap.newBuilder()
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("a"))
					.setEd25519(CANONICAL_SIG))
			.build();
	private static final SigUsage SINGLE_SIG_USAGE = new SigUsage(
			1, ONE_PAIR_SIG_MAP.getSerializedSize(), 1
	);
	private static final BaseTransactionMeta NO_MEMO_AND_NO_EXPLICIT_XFERS = new BaseTransactionMeta(0, 0);

	private static final TokenOpsUsage TOKEN_OPS_USAGE = new TokenOpsUsage();
	private static final ConsensusOpsUsage CONSENSUS_OPS_USAGE = new ConsensusOpsUsage();
	private static final String NOT_IMPLEMENTED = "Not implemented!";

	/**
	 * Returns the total resource usage for the base configuration of the given
	 * type of the given operation.
	 *
	 * @param function
	 * 		the operation of interest
	 * @param type
	 * 		the type of interest
	 * @return the total resource usage of the base configuration
	 */
	UsageAccumulator baseUsageFor(HederaFunctionality function, SubType type) {
		switch (function) {
			case CryptoTransfer:
				switch (type) {
					case DEFAULT:
						return hbarCryptoTransfer();
					case TOKEN_FUNGIBLE_COMMON:
						return htsCryptoTransfer();
					case TOKEN_NON_FUNGIBLE_UNIQUE:
						return nftCryptoTransfer();
				}
				break;
			case ConsensusSubmitMessage:
				return submitMessage();
			case TokenFeeScheduleUpdate:
				return feeScheduleUpdate();
			default:
				throw new IllegalArgumentException("Canonical usage unknown");

		}

		throw new IllegalArgumentException("Canonical usage unknown");
	}

	private UsageAccumulator submitMessage() {
		final var baseMeta = new BaseTransactionMeta(0, 0);
		final var opMeta = new SubmitMessageMeta(100);
		final var into = new UsageAccumulator();
		CONSENSUS_OPS_USAGE.submitMessageUsage(SINGLE_SIG_USAGE, opMeta, baseMeta, into);
		return into;
	}

	private UsageAccumulator feeScheduleUpdate() {
		/* A canonical op */
		final var target = TokenID.newBuilder().setShardNum(1).setRealmNum(2).setTokenNum(3).build();
		final List<CustomFee> theNewSchedule = List.of(
				CustomFee.newBuilder().setFixedFee(FixedFee.newBuilder()
						.setAmount(123L)
						.setDenominatingTokenId(target))
						.build());
		final var op = TokenFeeScheduleUpdateTransactionBody.newBuilder()
				.setTokenId(target)
				.addAllCustomFees(theNewSchedule)
				.build();

		/* The canonical usage and context */
		final var newReprBytes = TOKEN_OPS_USAGE.bytesNeededToRepr(theNewSchedule);
		final var grpcReprBytes = op.getSerializedSize() - FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var opMeta = new FeeScheduleUpdateMeta(0L, newReprBytes, grpcReprBytes);
		final var feeScheduleCtx = new ExtantFeeScheduleContext(THREE_MONTHS_IN_SECONDS, 0);

		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.feeScheduleUpdateUsage(
				SINGLE_SIG_USAGE,
				NO_MEMO_AND_NO_EXPLICIT_XFERS,
				opMeta,
				feeScheduleCtx,
				into);
		return into;
	}

	private UsageAccumulator hbarCryptoTransfer() {
		throw new AssertionError(NOT_IMPLEMENTED);
	}

	private UsageAccumulator htsCryptoTransfer() {
		throw new AssertionError(NOT_IMPLEMENTED);
	}

	private UsageAccumulator nftCryptoTransfer() {
		throw new AssertionError(NOT_IMPLEMENTED);
	}
}
