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

package com.hedera.node.app.service.token.impl.api;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.CustomFeeAssessmentStep;
import com.hedera.node.app.spi.api.ServiceApiProvider;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides {@link TokenServiceApi} instances.
 */
public enum TokenServiceApiProvider implements ServiceApiProvider<TokenServiceApi> {
    /** The singleton instance. */
    TOKEN_SERVICE_API_PROVIDER;

    @Override
    public String serviceName() {
        return TokenService.NAME;
    }

    @Override
    public TokenServiceApi newInstance(
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final WritableStates writableStates) {
        return new TokenServiceApiImpl(configuration, storeMetricsService, writableStates, op -> {
            final var assessor = new CustomFeeAssessmentStep(op);
            try {
                final var result = assessor.assessFees(
                        new ReadableTokenStoreImpl(writableStates),
                        new ReadableTokenRelationStoreImpl(writableStates),
                        configuration,
                        new ReadableAccountStoreImpl(writableStates),
                        AccountID::hasAlias);
                return !result.assessedCustomFees().isEmpty();
            } catch (Exception ignore) {
                return false;
            }
        });
    }
}
