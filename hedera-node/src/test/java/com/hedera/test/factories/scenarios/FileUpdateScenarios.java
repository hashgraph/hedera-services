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

import static com.hedera.test.factories.txns.FileUpdateFactory.MASTER_PAYER_ID;
import static com.hedera.test.factories.txns.FileUpdateFactory.TREASURY_PAYER_ID;
import static com.hedera.test.factories.txns.FileUpdateFactory.newSignedFileUpdate;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum FileUpdateScenarios implements TxnHandlingScenario {
    VANILLA_FILE_UPDATE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedFileUpdate(MISC_FILE_ID).get()));
        }
    },
    TREASURY_SYS_FILE_UPDATE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedFileUpdate(SYS_FILE_ID)
                                    .payer(TREASURY_PAYER_ID)
                                    .newWaclKt(SIMPLE_NEW_WACL_KT)
                                    .get()));
        }
    },
    TREASURY_SYS_FILE_UPDATE_SCENARIO_NO_NEW_KEY {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedFileUpdate(SYS_FILE_ID).payer(TREASURY_PAYER_ID).get()));
        }
    },
    MASTER_SYS_FILE_UPDATE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedFileUpdate(SYS_FILE_ID)
                                    .payer(MASTER_PAYER_ID)
                                    .newWaclKt(SIMPLE_NEW_WACL_KT)
                                    .get()));
        }
    },
    IMMUTABLE_FILE_UPDATE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedFileUpdate(IMMUTABLE_FILE_ID).get()));
        }
    },
    FILE_UPDATE_NEW_WACL_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedFileUpdate(MISC_FILE_ID)
                                    .payer(TREASURY_PAYER_ID)
                                    .newWaclKt(SIMPLE_NEW_WACL_KT)
                                    .get()));
        }
    },
    FILE_UPDATE_MISSING_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedFileUpdate(MISSING_FILE_ID)
                                    .payer(TREASURY_PAYER_ID)
                                    .newWaclKt(SIMPLE_NEW_WACL_KT)
                                    .get()));
        }
    }
}
