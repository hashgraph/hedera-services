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

import static com.hedera.test.factories.txns.CryptoApproveAllowanceFactory.newSignedApproveAllowance;
import static com.hedera.test.factories.txns.CryptoDeleteAllowanceFactory.newSignedDeleteAllowance;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum CryptoAllowanceScenarios implements TxnHandlingScenario {
    CRYPTO_APPROVE_ALLOWANCE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedApproveAllowance()
                                    .withCryptoAllowances(cryptoAllowanceList)
                                    .withNftAllowances(nftAllowanceList)
                                    .withTokenAllowances(tokenAllowanceList)
                                    .nonPayerKts(OWNER_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_APPROVE_ALLOWANCE_SELF_OWNER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedApproveAllowance()
                                    .withCryptoAllowances(cryptoSelfOwnerAllowanceList)
                                    .withNftAllowances(nftSelfOwnerAllowanceList)
                                    .withTokenAllowances(tokenSelfOwnerAllowanceList)
                                    .nonPayerKts(OWNER_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_APPROVE_ALLOWANCE_USING_DELEGATING_SPENDER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedApproveAllowance()
                                    .withCryptoAllowances(cryptoAllowanceList)
                                    .withNftAllowances(delegatingNftAllowanceList)
                                    .withTokenAllowances(tokenAllowanceList)
                                    .nonPayerKts(OWNER_ACCOUNT_KT, DELEGATING_SPENDER_KT)
                                    .get()));
        }
    },
    CRYPTO_APPROVE_TOKEN_ALLOWANCE_MISSING_OWNER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedApproveAllowance()
                                    .withCryptoAllowances(cryptoAllowanceList)
                                    .withNftAllowances(nftAllowanceList)
                                    .withTokenAllowances(tokenAllowanceMissingOwnerList)
                                    .get()));
        }
    },
    CRYPTO_APPROVE_CRYPTO_ALLOWANCE_MISSING_OWNER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedApproveAllowance()
                                    .withCryptoAllowances(cryptoAllowanceMissingOwnerList)
                                    .withNftAllowances(nftAllowanceList)
                                    .withTokenAllowances(tokenAllowanceList)
                                    .get()));
        }
    },
    CRYPTO_APPROVE_NFT_ALLOWANCE_MISSING_OWNER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedApproveAllowance()
                                    .withCryptoAllowances(cryptoAllowanceList)
                                    .withNftAllowances(nftAllowanceMissingOwnerList)
                                    .withTokenAllowances(tokenAllowanceList)
                                    .get()));
        }
    },
    CRYPTO_APPROVE_NFT_ALLOWANCE_MISSING_DELEGATING_SPENDER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedApproveAllowance()
                                    .withCryptoAllowances(cryptoAllowanceList)
                                    .withNftAllowances(delegatingNftAllowanceMissingOwnerList)
                                    .withTokenAllowances(tokenAllowanceList)
                                    .get()));
        }
    },
    CRYPTO_APPROVE_ALLOWANCE_NO_OWNER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedApproveAllowance()
                                    .withCryptoAllowances(cryptoAllowanceNoOwnerList)
                                    .withNftAllowances(nftAllowanceList)
                                    .withTokenAllowances(tokenAllowanceList)
                                    .nonPayerKts(OWNER_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_DELETE_ALLOWANCE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedDeleteAllowance()
                                    .withNftAllowances(nftDeleteAllowanceList)
                                    .nonPayerKts(OWNER_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_DELETE_ALLOWANCE_SELF_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedDeleteAllowance()
                                    .withNftAllowances(nftDeleteAllowanceListSelf)
                                    .nonPayerKts(OWNER_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_DELETE_NFT_ALLOWANCE_MISSING_OWNER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedDeleteAllowance()
                                    .withNftAllowances(nftDeleteAllowanceMissingOwnerList)
                                    .get()));
        }
    }
}
