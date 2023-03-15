/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.test.factories.txns.CryptoCreateFactory.DEFAULT_ACCOUNT_KT;
import static com.hedera.test.factories.txns.CryptoCreateFactory.ECDSA_KT;
import static com.hedera.test.factories.txns.CryptoCreateFactory.ECDSA_KT_2;
import static com.hedera.test.factories.txns.CryptoCreateFactory.newSignedCryptoCreate;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;

public enum CryptoCreateScenarios implements TxnHandlingScenario {
    CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    newSignedCryptoCreate().receiverSigRequired(false).get());
        }
    },
    CRYPTO_CREATE_RECEIVER_SIG_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedCryptoCreate()
                    .receiverSigRequired(true)
                    .nonPayerKts(DEFAULT_ACCOUNT_KT)
                    .get());
        }
    },
    CRYPTO_CREATE_RECEIVER_SIG_ED_ADMIN_KEY_EVM_ADDRESS_ALIAS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedCryptoCreate()
                    .receiverSigRequired(true)
                    .alias(ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(
                            ECDSA_KT.asKey().getECDSASecp256K1().toByteArray())))
                    .nonPayerKts(DEFAULT_ACCOUNT_KT)
                    .get());
        }
    },
    CRYPTO_CREATE_NO_RECEIVER_SIG_ED_ADMIN_KEY_EVM_ADDRESS_ALIAS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedCryptoCreate()
                    .receiverSigRequired(false)
                    .alias(ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(
                            ECDSA_KT.asKey().getECDSASecp256K1().toByteArray())))
                    .nonPayerKts(DEFAULT_ACCOUNT_KT)
                    .get());
        }
    },
    CRYPTO_CREATE_RECEIVER_SIG_ECDSA_ADMIN_KEY_DIFFERENT_EVM_ADDRESS_ALIAS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedCryptoCreate()
                    .receiverSigRequired(true)
                    .accountKt(ECDSA_KT)
                    .alias(ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(
                            ECDSA_KT_2.asKey().getECDSASecp256K1().toByteArray())))
                    .nonPayerKts(DEFAULT_ACCOUNT_KT)
                    .get());
        }
    },
    CRYPTO_CREATE_NO_RECEIVER_SIG_ECDSA_ADMIN_KEY_DIFFERENT_EVM_ADDRESS_ALIAS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedCryptoCreate()
                    .receiverSigRequired(false)
                    .accountKt(ECDSA_KT)
                    .alias(ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(
                            ECDSA_KT_2.asKey().getECDSASecp256K1().toByteArray())))
                    .nonPayerKts(DEFAULT_ACCOUNT_KT)
                    .get());
        }
    },
    CRYPTO_CREATE_RECEIVER_SIG_ECDSA_ADMIN_KEY_EVM_ADDRESS_ALIAS_FROM_SAME_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedCryptoCreate()
                    .receiverSigRequired(true)
                    .accountKt(ECDSA_KT)
                    .alias(ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(
                            ECDSA_KT.asKey().getECDSASecp256K1().toByteArray())))
                    .nonPayerKts(DEFAULT_ACCOUNT_KT)
                    .get());
        }
    },
    CRYPTO_CREATE_NO_RECEIVER_SIG_ECDSA_ADMIN_KEY_EVM_ADDRESS_ALIAS_FROM_SAME_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedCryptoCreate()
                    .receiverSigRequired(false)
                    .accountKt(ECDSA_KT)
                    .alias(ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(
                            ECDSA_KT.asKey().getECDSASecp256K1().toByteArray())))
                    .nonPayerKts(DEFAULT_ACCOUNT_KT)
                    .get());
        }
    },
    CRYPTO_CREATE_COMPLEX_PAYER_RECEIVER_SIG_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedCryptoCreate()
                    .payer(COMPLEX_KEY_ACCOUNT_ID)
                    .payerKt(COMPLEX_KEY_ACCOUNT_KT)
                    .receiverSigRequired(true)
                    .nonPayerKts(DEFAULT_ACCOUNT_KT)
                    .get());
        }
    }
}
