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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import javax.inject.Inject;

public class TokenKeyTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    public static final Function TOKEN_KEY =
            new Function("getTokenKey(address,uint)", ReturnTypes.RESPONSE_CODE_TOKEN_KEY);

    @Inject
    public TokenKeyTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(TOKEN_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        final var args = TOKEN_KEY.decodeCall(attempt.input().toArrayUnsafe());
        final var token = attempt.linkedToken(fromHeadlongAddress(args.get(0)));
        final BigInteger keyType = args.get(1);

        final boolean metadataSupport =
                attempt.configuration().getConfigData(ContractsConfig.class).metadataKeyAndFieldEnabled();
        return new TokenKeyCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.isStaticCall(),
                token,
                getTokenKey(token, keyType.intValue(), metadataSupport));
    }

    public Key getTokenKey(final Token token, final int keyType, final boolean metadataSupport)
            throws InvalidTransactionException {
        if (token == null) {
            return null;
        }
        return switch (keyType) {
            case 1 -> token.adminKey();
            case 2 -> token.kycKey();
            case 4 -> token.freezeKey();
            case 8 -> token.wipeKey();
            case 16 -> token.supplyKey();
            case 32 -> token.feeScheduleKey();
            case 64 -> token.pauseKey();
            case 128 -> metadataSupport ? token.metadataKey() : null;
            default -> null;
        };
    }
}
