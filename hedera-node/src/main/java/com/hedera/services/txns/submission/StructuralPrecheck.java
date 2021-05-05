package com.hedera.services.txns.submission;

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

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;

import static com.hedera.services.txns.submission.PresolvencyFlaws.WELL_KNOWN_FLAWS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;

/**
 * Tests if the top-level {@code bytes} fields in the {@code Transaction} are set correctly,
 * are within size limits, and contain a parseable gRPC {@code TransactionBody} that
 * requests exactly one function supported by the network.
 *
 * For more details, please see https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
public class StructuralPrecheck {
	private static final TxnValidityAndFeeReq OK_STRUCTURALLY = new TxnValidityAndFeeReq(OK);
	public static final int HISTORICAL_MAX_PROTO_MESSAGE_DEPTH = 50;

	private final int maxSignedTxnSize;
	private final int maxProtoMessageDepth;

	public StructuralPrecheck(int maxSignedTxnSize, int maxProtoMessageDepth) {
		this.maxSignedTxnSize = maxSignedTxnSize;
		this.maxProtoMessageDepth = maxProtoMessageDepth;
	}

	public Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> assess(Transaction signedTxn) {
		final var hasSignedTxnBytes = !signedTxn.getSignedTransactionBytes().isEmpty();
		final var hasDeprecatedSigMap = signedTxn.hasSigMap();
		final var hasDeprecatedBodyBytes = !signedTxn.getBodyBytes().isEmpty();

		if (hasSignedTxnBytes) {
			if (hasDeprecatedBodyBytes || hasDeprecatedSigMap) {
				return WELL_KNOWN_FLAWS.get(INVALID_TRANSACTION);
			}
		} else if (!hasDeprecatedBodyBytes) {
			return WELL_KNOWN_FLAWS.get(INVALID_TRANSACTION_BODY);
		}

		if (signedTxn.getSerializedSize() > maxSignedTxnSize) {
			return WELL_KNOWN_FLAWS.get(TRANSACTION_OVERSIZE);
		}

		try {
			var accessor = new SignedTxnAccessor(signedTxn);
			if (hasTooManyLayers(signedTxn) || hasTooManyLayers(accessor.getTxn()))	{
				return WELL_KNOWN_FLAWS.get(TRANSACTION_TOO_MANY_LAYERS);
			}
			if (accessor.getFunction() == NONE) {
				return WELL_KNOWN_FLAWS.get(INVALID_TRANSACTION_BODY);
			}
			return Pair.of(OK_STRUCTURALLY, Optional.of(accessor));
		} catch (InvalidProtocolBufferException e) {
			return WELL_KNOWN_FLAWS.get(INVALID_TRANSACTION_BODY);
		}
	}

	int protoDepthOf(GeneratedMessageV3 msg) {
		int depth = 0;
		for (var field : msg.getAllFields().values()) {
			if (isProtoMsg(field)) {
				depth = Math.max(depth, 1 + protoDepthOf((GeneratedMessageV3) field));
			} else if (field instanceof List) {
				for (var item : (List) field) {
					depth = Math.max(depth, isProtoMsg(item) ? 1 + protoDepthOf((GeneratedMessageV3) item) : 0);
				}
			}
			/* Otherwise the field is a primitive and adds no depth to the message. */
		}
		return depth;
	}

	private boolean hasTooManyLayers(GeneratedMessageV3 msg) {
		return protoDepthOf(msg) > maxProtoMessageDepth;
	}

	private boolean isProtoMsg(Object o) {
		return o instanceof GeneratedMessageV3;
	}
}
