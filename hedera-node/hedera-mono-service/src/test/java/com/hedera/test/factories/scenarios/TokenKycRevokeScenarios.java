/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.factories.scenarios;

import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.TokenRevokeKycFactory.newSignedTokenRevokeKyc;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum TokenKycRevokeScenarios implements TxnHandlingScenario {
    VALID_REVOKE_WITH_EXTANT_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenRevokeKyc()
                                    .revoking(KNOWN_TOKEN_WITH_KYC, MISC_ACCOUNT)
                                    .nonPayerKts(TOKEN_KYC_KT)
                                    .get()));
        }
    },
    REVOKE_WITH_MISSING_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenRevokeKyc()
                                    .revoking(MISSING_TOKEN, MISC_ACCOUNT)
                                    .nonPayerKts(TOKEN_KYC_KT)
                                    .get()));
        }
    },
    REVOKE_FOR_TOKEN_WITHOUT_KYC {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenRevokeKyc()
                                    .revoking(KNOWN_TOKEN_WITH_FREEZE, MISC_ACCOUNT)
                                    .nonPayerKts(TOKEN_KYC_KT)
                                    .get()));
        }
    },
}
