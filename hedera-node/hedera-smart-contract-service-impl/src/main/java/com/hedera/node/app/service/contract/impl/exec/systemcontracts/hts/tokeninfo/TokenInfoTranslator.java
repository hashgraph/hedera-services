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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokeninfo;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

public class TokenInfoTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    public static final Function TOKEN_INFO =
            new Function("getTokenInfo(address)", ReturnTypes.RESPONSE_CODE_TOKEN_INFO);

    public static final Function TOKEN_INFO_V2 =
            new Function("getTokenInfoV2(address)", ReturnTypes.RESPONSE_CODE_TOKEN_INFO_V2);

    @Inject
    public TokenInfoTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        requireNonNull(attempt);
        final var v2Enabled =
                attempt.configuration().getConfigData(ContractsConfig.class).systemContractTokenInfoV2Enabled();
        return attempt.isSelector(TOKEN_INFO) || attempt.isSelectorIfConfigEnabled(TOKEN_INFO_V2, v2Enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        requireNonNull(attempt);
        final var function = attempt.isSelector(TOKEN_INFO) ? TOKEN_INFO : TOKEN_INFO_V2;
        final var args = function.decodeCall(attempt.input().toArrayUnsafe());
        final var token = attempt.linkedToken(fromHeadlongAddress(args.get(0)));
        return new TokenInfoCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.isStaticCall(),
                token,
                attempt.configuration(),
                function);
    }
}
