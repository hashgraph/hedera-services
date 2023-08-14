/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.impl.validators.StakingValidator;
import com.hedera.node.app.spi.api.ServiceApiProvider;
import com.hedera.node.app.spi.state.WritableStates;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides {@link TokenServiceApi} instances.
 */
public enum TokenServiceApiProvider implements ServiceApiProvider<TokenServiceApi> {
    TOKEN_SERVICE_API_PROVIDER;

    private final StakingValidator stakingValidator = new StakingValidator();

    @Override
    public String serviceName() {
        return TokenService.NAME;
    }

    @Override
    public TokenServiceApi newInstance(
            @NonNull final Configuration configuration, @NonNull final WritableStates writableStates) {
        return new TokenServiceApiImpl(configuration, stakingValidator, writableStates);
    }
}
