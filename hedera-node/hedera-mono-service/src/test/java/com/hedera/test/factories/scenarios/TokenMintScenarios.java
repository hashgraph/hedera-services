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
import static com.hedera.test.factories.txns.TokenMintFactory.newSignedTokenMint;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum TokenMintScenarios implements TxnHandlingScenario {
    MINT_WITH_SUPPLY_KEYED_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenMint()
                                    .minting(KNOWN_TOKEN_WITH_SUPPLY)
                                    .nonPayerKts(TOKEN_SUPPLY_KT)
                                    .get()));
        }
    },
    MINT_WITH_MISSING_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedTokenMint().minting(MISSING_TOKEN).get()));
        }
    },
    MINT_FOR_TOKEN_WITHOUT_SUPPLY {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedTokenMint().minting(KNOWN_TOKEN_NO_SPECIAL_KEYS).get()));
        }
    },
}
