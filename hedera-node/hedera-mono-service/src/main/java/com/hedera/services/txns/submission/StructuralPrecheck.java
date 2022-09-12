/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.submission;

import static com.hedera.services.txns.submission.PresolvencyFlaws.WELL_KNOWN_FLAWS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.context.primitives.SignedStateViewFactory;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.txns.submission.annotations.MaxProtoMsgDepth;
import com.hedera.services.txns.submission.annotations.MaxSignedTxnSize;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Tests if the top-level {@code bytes} fields in the {@code Transaction} are set correctly, are
 * within size limits, and contain a parseable gRPC {@code TransactionBody} that requests exactly
 * one function supported by the network.
 *
 * <p>For more details, please see
 * https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
@Singleton
public final class StructuralPrecheck {
    private static final TxnValidityAndFeeReq OK_STRUCTURALLY = new TxnValidityAndFeeReq(OK);
    static final int HISTORICAL_MAX_PROTO_MESSAGE_DEPTH = 50;

    private final int maxSignedTxnSize;
    private final int maxProtoMessageDepth;
    private HapiOpCounters opCounters;
    private final SignedStateViewFactory stateViewFactory;
    private final AccessorFactory accessorFactory;

    @Inject
    public StructuralPrecheck(
            @MaxSignedTxnSize final int maxSignedTxnSize,
            @MaxProtoMsgDepth final int maxProtoMessageDepth,
            final HapiOpCounters counters,
            final SignedStateViewFactory stateViewFactory,
            final AccessorFactory accessorFactory) {
        this.maxSignedTxnSize = maxSignedTxnSize;
        this.maxProtoMessageDepth = maxProtoMessageDepth;
        this.opCounters = counters;
        this.stateViewFactory = stateViewFactory;
        this.accessorFactory = accessorFactory;
    }

    public Pair<TxnValidityAndFeeReq, SignedTxnAccessor> assess(final Transaction signedTxn) {
        final var hasSignedTxnBytes = !signedTxn.getSignedTransactionBytes().isEmpty();
        final var hasDeprecatedSigMap = signedTxn.hasSigMap();
        final var hasDeprecatedBodyBytes = !signedTxn.getBodyBytes().isEmpty();
        final var hasDeprecatedBody = signedTxn.hasBody();
        final var hasDeprecatedSigs = signedTxn.hasSigs();

        if (hasDeprecatedBody
                || hasDeprecatedSigs
                || hasDeprecatedSigMap
                || hasDeprecatedBodyBytes) {
            opCounters.countDeprecatedTxnReceived();
        }

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
            final var accessor = accessorFactory.constructSpecializedAccessor(signedTxn);

            final var signedStateView = stateViewFactory.latestSignedStateView();
            signedStateView.ifPresent(accessor::setStateView);

            if (hasTooManyLayers(signedTxn) || hasTooManyLayers(accessor.getTxn())) {
                return WELL_KNOWN_FLAWS.get(TRANSACTION_TOO_MANY_LAYERS);
            }
            if (accessor.getFunction() == NONE) {
                return WELL_KNOWN_FLAWS.get(INVALID_TRANSACTION_BODY);
            }
            return Pair.of(OK_STRUCTURALLY, accessor);
        } catch (final InvalidProtocolBufferException e) {
            return WELL_KNOWN_FLAWS.get(INVALID_TRANSACTION_BODY);
        }
    }

    int protoDepthOf(final GeneratedMessageV3 msg) {
        int depth = 0;
        for (final var field : msg.getAllFields().values()) {
            if (isProtoMsg(field)) {
                depth = Math.max(depth, 1 + protoDepthOf((GeneratedMessageV3) field));
            } else if (field instanceof List<? extends Object> list) {
                for (final var item : list) {
                    depth =
                            Math.max(
                                    depth,
                                    isProtoMsg(item)
                                            ? 1 + protoDepthOf((GeneratedMessageV3) item)
                                            : 0);
                }
            }
            /* Otherwise the field is a primitive and adds no depth to the message. */
        }
        return depth;
    }

    private boolean hasTooManyLayers(final GeneratedMessageV3 msg) {
        return protoDepthOf(msg) > maxProtoMessageDepth;
    }

    private boolean isProtoMsg(final Object o) {
        return o instanceof GeneratedMessageV3;
    }
}
