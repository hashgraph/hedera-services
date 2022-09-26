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
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.STAKING_FUND;
import static com.hedera.test.factories.txns.TokenAssociateFactory.newSignedTokenAssociate;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum TokenAssociateScenarios implements TxnHandlingScenario {
    TOKEN_ASSOCIATE_WITH_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenAssociate()
                                    .targeting(MISC_ACCOUNT)
                                    .associating(KNOWN_TOKEN_WITH_KYC)
                                    .associating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                                    .nonPayerKts(MISC_ACCOUNT_KT)
                                    .get()));
        }
    },
    TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenAssociate()
                                    .targeting(DEFAULT_PAYER)
                                    .associating(KNOWN_TOKEN_WITH_KYC)
                                    .associating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                                    .get()));
        }
    },
    TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenAssociate()
                                    .targeting(CUSTOM_PAYER_ACCOUNT)
                                    .associating(KNOWN_TOKEN_WITH_KYC)
                                    .associating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                                    .get()));
        }
    },
    TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenAssociate()
                                    .targeting(STAKING_FUND)
                                    .associating(KNOWN_TOKEN_WITH_KYC)
                                    .get()));
        }
    },
    TOKEN_ASSOCIATE_WITH_MISSING_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenAssociate()
                                    .targeting(MISSING_ACCOUNT)
                                    .associating(KNOWN_TOKEN_WITH_KYC)
                                    .associating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                                    .get()));
        }
    },
}
