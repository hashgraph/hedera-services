/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.factories.txns.TokenDissociateFactory;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public enum TokenDissociateScenarios implements TxnHandlingScenario {
    TOKEN_DISSOCIATE_WITH_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenDissociateFactory.newSignedTokenDissociate()
                    .targeting(MISC_ACCOUNT)
                    .dissociating(KNOWN_TOKEN_WITH_KYC)
                    .dissociating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .nonPayerKts(MISC_ACCOUNT_KT)
                    .get());
        }
    },
    TOKEN_DISSOCIATE_WITH_SELF_PAID_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenDissociateFactory.newSignedTokenDissociate()
                    .targeting(SignedTxnFactory.DEFAULT_PAYER)
                    .dissociating(KNOWN_TOKEN_WITH_KYC)
                    .dissociating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .get());
        }
    },
    TOKEN_DISSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenDissociateFactory.newSignedTokenDissociate()
                    .targeting(CUSTOM_PAYER_ACCOUNT)
                    .dissociating(KNOWN_TOKEN_WITH_KYC)
                    .dissociating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .get());
        }
    },
    TOKEN_DISSOCIATE_WITH_MISSING_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenDissociateFactory.newSignedTokenDissociate()
                    .targeting(MISSING_ACCOUNT)
                    .dissociating(KNOWN_TOKEN_WITH_KYC)
                    .dissociating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .get());
        }
    },
}
