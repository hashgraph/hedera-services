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

import static com.hedera.test.factories.txns.ContractUpdateFactory.newSignedContractUpdate;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum ContractUpdateScenarios implements TxnHandlingScenario {
    CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newAdminKt(SIMPLE_NEW_ADMIN_KT)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newDeprecatedAdminKey(true)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newProxyAccount(MISC_ACCOUNT_ID)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newAutoRenewPeriod(DEFAULT_PERIOD)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newFile(MISC_FILE_ID)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newMemo(DEFAULT_MEMO)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newAdminKt(SIMPLE_NEW_ADMIN_KT)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_NEW_AUTO_RENEW_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newAutoRenewAccount(MISC_ACCOUNT_ID)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
    CONTRACT_UPDATE_INVALID_AUTO_RENEW_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractUpdate(MISC_CONTRACT_ID)
                                    .newAutoRenewAccount(MISSING_ACCOUNT_ID)
                                    .newExpiration(DEFAULT_EXPIRY)
                                    .get()));
        }
    },
}
