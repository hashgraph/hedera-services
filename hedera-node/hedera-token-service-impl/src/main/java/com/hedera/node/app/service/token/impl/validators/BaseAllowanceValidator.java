/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.token.ReadableUniqueTokenStore;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;

public class BaseAllowanceValidator {
    final ConfigProvider configProvider;

    @Inject
    public BaseAllowanceValidator(final ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * Check if the allowance feature is enabled
     *
     * @return true if the feature is enabled in {@link HederaConfig}
     */
    public boolean isEnabled() {
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        return hederaConfig.allowancesIsEnabled();
    }

    protected void validateTotalAllowances(final int totalAllowances) {
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        validateFalse(
                exceedsTxnLimit(totalAllowances, hederaConfig.allowancesMaxTransactionLimit()),
                MAX_ALLOWANCES_EXCEEDED);
        validateFalse(emptyAllowances(totalAllowances), EMPTY_ALLOWANCES);
    }

    protected void validateSerialNums(
            final List<Long> serialNums, final TokenID tokenId, final ReadableUniqueTokenStore nftStore) {
        final var serialsSet = new HashSet<>(serialNums);
        for (var serial : serialsSet) {
            validateTrue(serial > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
            final var nft = nftStore.get(tokenId, serial);
            validateTrue(nft != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        }
    }

    protected boolean exceedsTxnLimit(final int totalAllowances, final int maxLimit) {
        return totalAllowances > maxLimit;
    }

    boolean emptyAllowances(final int totalAllowances) {
        return totalAllowances == 0;
    }
}
