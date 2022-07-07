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
package com.hedera.services.grpc.marshalling;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Encapsulates the result of translating a gRPC CryptoTransfer into a list of balance changes (ℏ or
 * token unit), as well as the validity of these changes.
 *
 * <p>Note that if the {@link com.hedera.services.grpc.marshalling.ImpliedTransfersMeta} is not
 * {@code OK}, the list of changes will always be empty.
 */
public class ImpliedTransfers {
    public static final Map<ByteString, EntityNum> NO_ALIASES = Collections.emptyMap();
    public static final List<CustomFeeMeta> NO_CUSTOM_FEE_META = Collections.emptyList();
    public static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();

    private final ImpliedTransfersMeta meta;
    private final List<BalanceChange> changes;
    private final List<FcAssessedCustomFee> assessedCustomFees;

    private ImpliedTransfers(
            ImpliedTransfersMeta meta,
            List<BalanceChange> changes,
            List<FcAssessedCustomFee> assessedCustomFees) {
        this.meta = meta;
        this.changes = changes;
        this.assessedCustomFees = assessedCustomFees;
    }

    public static ImpliedTransfers valid(
            final ImpliedTransfersMeta.ValidationProps validationProps,
            final List<BalanceChange> changes,
            final List<CustomFeeMeta> customFeeMeta,
            final List<FcAssessedCustomFee> assessedCustomFees) {
        return valid(validationProps, changes, customFeeMeta, assessedCustomFees, NO_ALIASES);
    }

    public static ImpliedTransfers valid(
            final ImpliedTransfersMeta.ValidationProps validationProps,
            final List<BalanceChange> changes,
            final List<CustomFeeMeta> customFeeMeta,
            final List<FcAssessedCustomFee> assessedCustomFees,
            final Map<ByteString, EntityNum> aliases) {
        final var meta = new ImpliedTransfersMeta(validationProps, OK, customFeeMeta, aliases);
        return new ImpliedTransfers(meta, changes, assessedCustomFees);
    }

    public static ImpliedTransfers valid(
            final ImpliedTransfersMeta.ValidationProps validationProps,
            final List<BalanceChange> changes,
            final List<CustomFeeMeta> customFeeMeta,
            final List<FcAssessedCustomFee> assessedCustomFees,
            final Map<ByteString, EntityNum> aliases,
            final int numAutoCreations) {
        final var meta =
                new ImpliedTransfersMeta(
                        validationProps, OK, customFeeMeta, aliases, numAutoCreations);
        return new ImpliedTransfers(meta, changes, assessedCustomFees);
    }

    public static ImpliedTransfers invalid(
            final ImpliedTransfersMeta.ValidationProps validationProps,
            final ResponseCodeEnum code) {
        final var meta =
                new ImpliedTransfersMeta(validationProps, code, NO_CUSTOM_FEE_META, NO_ALIASES);
        return new ImpliedTransfers(meta, Collections.emptyList(), Collections.emptyList());
    }

    public static ImpliedTransfers invalid(
            final ImpliedTransfersMeta.ValidationProps validationProps,
            final Map<ByteString, EntityNum> suspectAliases,
            final ResponseCodeEnum code) {
        final var meta =
                new ImpliedTransfersMeta(validationProps, code, NO_CUSTOM_FEE_META, suspectAliases);
        return new ImpliedTransfers(meta, Collections.emptyList(), Collections.emptyList());
    }

    public static ImpliedTransfers invalid(
            ImpliedTransfersMeta.ValidationProps validationProps,
            List<CustomFeeMeta> customFeeMetaTilFailure,
            ResponseCodeEnum code) {
        final var meta =
                new ImpliedTransfersMeta(
                        validationProps, code, customFeeMetaTilFailure, NO_ALIASES);
        return new ImpliedTransfers(meta, Collections.emptyList(), Collections.emptyList());
    }

    public ImpliedTransfersMeta getMeta() {
        return meta;
    }

    public List<BalanceChange> getAllBalanceChanges() {
        return changes;
    }

    public List<FcAssessedCustomFee> getAssessedCustomFees() {
        return assessedCustomFees;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(ImpliedTransfers.class)
                .add("meta", meta)
                .add("changes", changes)
                .add("tokenFeeSchedules", meta.getCustomFeeMeta())
                .add("assessedCustomFees", assessedCustomFees)
                .add("resolvedAliases", meta.getResolutions())
                .add("numAutoCreations", meta.getNumAutoCreations())
                .toString();
    }
}
