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

import static com.hedera.test.factories.txns.CryptoUpdateFactory.newSignedCryptoUpdate;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.MASTER_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.STAKING_FUND_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.TREASURY_PAYER_ID;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum CryptoUpdateScenarios implements TxnHandlingScenario {
    CRYPTO_UPDATE_NO_NEW_KEY_SELF_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedCryptoUpdate(DEFAULT_PAYER_ID).get()));
        }
    },
    CRYPTO_UPDATE_NO_NEW_KEY_CUSTOM_PAYER_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoUpdate(CUSTOM_PAYER_ACCOUNT_ID).get()));
        }
    },
    CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedCryptoUpdate(MISC_ACCOUNT_ID).get()));
        }
    },
    CRYPTO_UPDATE_IMMUTABLE_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedCryptoUpdate(STAKING_FUND_ID).get()));
        }
    },
    CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedCryptoUpdate(MISSING_ACCOUNT_ID).get()));
        }
    },
    CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoUpdate(COMPLEX_KEY_ACCOUNT_ID)
                                    .nonPayerKts(COMPLEX_KEY_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_ADD_NEW_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoUpdate(COMPLEX_KEY_ACCOUNT_ID)
                                    .newAccountKt(NEW_ACCOUNT_KT)
                                    .nonPayerKts(COMPLEX_KEY_ACCOUNT_KT, NEW_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_UPDATE_WITH_NEW_KEY_SELF_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoUpdate(DEFAULT_PAYER_ID)
                                    .newAccountKt(NEW_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_UPDATE_WITH_NEW_KEY_CUSTOM_PAYER_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoUpdate(CUSTOM_PAYER_ACCOUNT_ID)
                                    .newAccountKt(NEW_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoUpdate(MISC_ACCOUNT_ID)
                                    .newAccountKt(NEW_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NEW_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoUpdate(SYS_ACCOUNT_ID).newAccountKt(NEW_ACCOUNT_KT).get()));
        }
    },
    CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NO_NEW_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedCryptoUpdate(SYS_ACCOUNT_ID).get()));
        }
    },
    CRYPTO_UPDATE_SYS_ACCOUNT_WITH_PRIVILEGED_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoUpdate(SYS_ACCOUNT_ID)
                                    .payer(MASTER_PAYER_ID)
                                    .newAccountKt(NEW_ACCOUNT_KT)
                                    .get()));
        }
    },
    CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NO_NEW_KEY {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoUpdate(TREASURY_PAYER_ID).payer(TREASURY_PAYER_ID).get()));
        }
    },
    CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NEW_KEY {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoUpdate(TREASURY_PAYER_ID)
                                    .payer(TREASURY_PAYER_ID)
                                    .newAccountKt(NEW_ACCOUNT_KT)
                                    .get()));
        }
    },
}
