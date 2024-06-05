/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.validation;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.amh.config.ConfigProvider;
import com.hedera.node.config.data.AutoRenewConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validations related to entity expiry
 */
@Singleton
public class ExpiryValidation {

    private final ConfigProvider configProvider;

    @Inject
    public ExpiryValidation(ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
    }

    /**
     * Checks if an account is expired. Returns {@code false} if auto-renew is disabled
     *
     * @param account the {@link Account} to check
     * @throws PreCheckException if the account is expired
     */
    public void checkAccountExpiry(@NonNull final Account account) throws PreCheckException {
        if (account.tinybarBalance() > 0 || !account.expiredAndPendingRemoval()) {
            return;
        }

        final var config = configProvider.getConfiguration().getConfigData(AutoRenewConfig.class);
        if (account.smartContract()) {
            if (config.expireContracts()) {
                throw new PreCheckException(CONTRACT_EXPIRED_AND_PENDING_REMOVAL);
            }
        } else {
            if (config.expireAccounts()) {
                throw new PreCheckException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
            }
        }
    }
}
