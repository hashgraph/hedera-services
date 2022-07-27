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

import static com.hedera.test.factories.txns.CryptoDeleteFactory.newSignedCryptoDelete;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum CryptoDeleteScenarios implements TxnHandlingScenario {
    CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SELF_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(DEFAULT_PAYER_ID, NO_RECEIVER_SIG_ID).get()));
        }
    },
    CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_CUSTOM_PAYER_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(CUSTOM_PAYER_ACCOUNT_ID, NO_RECEIVER_SIG_ID).get()));
        }
    },
    CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(MISC_ACCOUNT_ID, NO_RECEIVER_SIG_ID).get()));
        }
    },
    CRYPTO_DELETE_TARGET_RECEIVER_SIG_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(MISC_ACCOUNT_ID, RECEIVER_SIG_ID).get()));
        }
    },
    CRYPTO_DELETE_TARGET_RECEIVER_SIG_RECEIVER_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(MISC_ACCOUNT_ID, DEFAULT_PAYER_ID).get()));
        }
    },
    CRYPTO_DELETE_TARGET_RECEIVER_SIG_SELF_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(DEFAULT_PAYER_ID, RECEIVER_SIG_ID).get()));
        }
    },
    CRYPTO_DELETE_TARGET_RECEIVER_SIG_CUSTOM_PAYER_PAID_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(CUSTOM_PAYER_ACCOUNT_ID, RECEIVER_SIG_ID).get()));
        }
    },
    CRYPTO_DELETE_MISSING_RECEIVER_SIG_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(MISC_ACCOUNT_ID, MISSING_ACCOUNT_ID).get()));
        }
    },
    CRYPTO_DELETE_MISSING_TARGET {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedCryptoDelete(MISSING_ACCOUNT_ID, NO_RECEIVER_SIG_ID).get()));
        }
    },
}
