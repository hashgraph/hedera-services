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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.events.Event;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Encapsulates the validity of a CryptoTransfer transaction, given a choice of two parameters: the
 * maximum allowed number of ℏ adjustments, and the maximum allowed number of token unit
 * adjustments.
 *
 * <p>Note that we need to remember these two parameters in order to safely reuse this validation
 * across "span" between the {@link com.hedera.services.ServicesState#preHandle(Event)} and {@link
 * com.hedera.services.ServicesState#handleConsensusRound(Round, SwirldDualState)} callbacks.
 *
 * <p>This is because either parameter <i>could</i> change due to an update of file 0.0.121 between
 * the two callbacks. So we have to double-check that neither <i>did</i> change before reusing the
 * work captured by this validation result.
 */
public class ImpliedTransfersMeta {
    private static final int NO_AUTO_CREATIONS = 0;
    private static final int NO_LAZY_CREATIONS = 0;

    private final int numAutoCreations;
    private final int numLazyCreations;
    private final ResponseCodeEnum code;
    private final ValidationProps validationProps;
    private final List<CustomFeeMeta> customFeeMeta;
    private final Map<ByteString, EntityNum> resolutions;

    public ImpliedTransfersMeta(
            final ValidationProps validationProps,
            final ResponseCodeEnum code,
            final List<CustomFeeMeta> customFeeMeta,
            final Map<ByteString, EntityNum> resolutions) {
        this(validationProps, code, customFeeMeta, resolutions, NO_AUTO_CREATIONS);
    }

    public ImpliedTransfersMeta(
            final ValidationProps validationProps,
            final ResponseCodeEnum code,
            final List<CustomFeeMeta> customFeeMeta,
            final Map<ByteString, EntityNum> resolutions,
            final int numAutoCreations) {
        this(
                validationProps,
                code,
                customFeeMeta,
                resolutions,
                numAutoCreations,
                NO_LAZY_CREATIONS);
    }

    public ImpliedTransfersMeta(
            final ValidationProps validationProps,
            final ResponseCodeEnum code,
            final List<CustomFeeMeta> customFeeMeta,
            final Map<ByteString, EntityNum> resolutions,
            final int numAutoCreations,
            final int numLazyCreations) {
        this.code = code;
        this.resolutions = resolutions;
        this.customFeeMeta = customFeeMeta;
        this.validationProps = validationProps;
        this.numAutoCreations = numAutoCreations;
        this.numLazyCreations = numLazyCreations;
    }

    public Map<ByteString, EntityNum> getResolutions() {
        return resolutions;
    }

    public int getNumAutoCreations() {
        return numAutoCreations;
    }

    public int getNumLazyCreations() {
        return numLazyCreations;
    }

    public List<CustomFeeMeta> getCustomFeeMeta() {
        return customFeeMeta;
    }

    public boolean wasDerivedFrom(
            final GlobalDynamicProperties dynamicProperties,
            final CustomFeeSchedules customFeeSchedules,
            final AliasManager aliasManager) {
        if (!resolutions.isEmpty()) {
            for (final var entry : resolutions.entrySet()) {
                final var past = entry.getValue();
                final var present = aliasManager.lookupIdBy(entry.getKey());
                if (!past.equals(present)) {
                    return false;
                }
            }
        }
        final var validationParamsMatch =
                (validationProps.maxHbarAdjusts == dynamicProperties.maxTransferListSize())
                        && (validationProps.maxTokenAdjusts
                                == dynamicProperties.maxTokenTransferListSize())
                        && (validationProps.maxOwnershipChanges
                                == dynamicProperties.maxNftTransfersLen())
                        && (validationProps.maxXferBalanceChanges
                                == dynamicProperties.maxXferBalanceChanges())
                        && (validationProps.maxNestedCustomFees
                                == dynamicProperties.maxCustomFeeDepth())
                        && (validationProps.areNftsEnabled == dynamicProperties.areNftsEnabled())
                        && (validationProps.isAutoCreationEnabled
                                == dynamicProperties.isAutoCreationEnabled())
                        && (validationProps.isLazyCreationEnabled
                                == dynamicProperties.isLazyCreationEnabled())
                        && (validationProps.areAllowancesEnabled
                                == dynamicProperties.areAllowancesEnabled());
        if (!validationParamsMatch) {
            return false;
        }
        for (var meta : customFeeMeta) {
            final var tokenId = meta.tokenId();
            var newCustomMeta = customFeeSchedules.lookupMetaFor(tokenId);
            if (!meta.equals(newCustomMeta)) {
                return false;
            }
        }
        return true;
    }

    public ResponseCodeEnum code() {
        return code;
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
        return MoreObjects.toStringHelper(ImpliedTransfersMeta.class)
                .add("code", code)
                .add("maxExplicitHbarAdjusts", validationProps.maxHbarAdjusts)
                .add("maxExplicitTokenAdjusts", validationProps.maxTokenAdjusts)
                .add("maxExplicitOwnershipChanges", validationProps.maxOwnershipChanges)
                .add("maxNestedCustomFees", validationProps.maxNestedCustomFees)
                .add("maxXferBalanceChanges", validationProps.maxXferBalanceChanges)
                .add("areNftsEnabled", validationProps.areNftsEnabled)
                .add("isAutoCreationEnabled", validationProps.isAutoCreationEnabled)
                .add("isLazyCreationEnabled", validationProps.isLazyCreationEnabled)
                .add("tokenFeeSchedules", customFeeMeta)
                .add("areAllowancesEnabled", validationProps.areAllowancesEnabled)
                .toString();
    }

    public record ValidationProps(
            int maxHbarAdjusts,
            int maxTokenAdjusts,
            int maxOwnershipChanges,
            int maxNestedCustomFees,
            int maxXferBalanceChanges,
            boolean areNftsEnabled,
            boolean isAutoCreationEnabled,
            boolean isLazyCreationEnabled,
            boolean areAllowancesEnabled) {}
}
