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
import com.hedera.test.factories.sigs.SigMapGenerator;
import com.hedera.test.factories.txns.SystemDeleteFactory;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Set;

public enum SystemDeleteScenarios implements TxnHandlingScenario {
    SYSTEM_DELETE_FILE_SCENARIO {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(SystemDeleteFactory.newSignedSystemDelete()
                    .file(MISC_FILE_ID)
                    .get());
        }
    },
    FULL_PAYER_SIGS_VIA_MAP_SCENARIO {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(SystemDeleteFactory.newSignedSystemDelete()
                    .payer(DILIGENT_SIGNING_PAYER_ID)
                    .payerKt(DILIGENT_SIGNING_PAYER_KT)
                    .nonPayerKts(MISC_FILE_WACL_KT)
                    .file(MISC_FILE_ID)
                    .get());
        }
    },
    MISSING_PAYER_SIGS_VIA_MAP_SCENARIO {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(SystemDeleteFactory.newSignedSystemDelete()
                    .payer(TOKEN_TREASURY_ID)
                    .payerKt(TOKEN_TREASURY_KT)
                    .nonPayerKts(MISC_FILE_WACL_KT)
                    .file(MISC_FILE_ID)
                    .get());
        }
    },
    INVALID_PAYER_SIGS_VIA_MAP_SCENARIO {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            SigMapGenerator buggySigMapGen = SigMapGenerator.withUniquePrefixes();
            buggySigMapGen.setInvalidEntries(Set.of(1));

            return PlatformTxnAccessor.from(SystemDeleteFactory.newSignedSystemDelete()
                    .fee(1_234L)
                    .sigMapGen(buggySigMapGen)
                    .payer(DILIGENT_SIGNING_PAYER_ID)
                    .payerKt(DILIGENT_SIGNING_PAYER_KT)
                    .nonPayerKts(MISC_FILE_WACL_KT)
                    .file(MISC_FILE_ID)
                    .get());
        }
    },
    AMBIGUOUS_SIG_MAP_SCENARIO {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            SigMapGenerator ambigSigMapGen = SigMapGenerator.withAmbiguousPrefixes();

            return PlatformTxnAccessor.from(SystemDeleteFactory.newSignedSystemDelete()
                    .fee(1_234L)
                    .keyFactory(overlapFactory)
                    .sigMapGen(ambigSigMapGen)
                    .payer(FROM_OVERLAP_PAYER_ID)
                    .payerKt(FROM_OVERLAP_PAYER_KT)
                    .nonPayerKts(MISC_FILE_WACL_KT)
                    .file(MISC_FILE_ID)
                    .get());
        }
    }
}
