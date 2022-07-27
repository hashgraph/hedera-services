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

import static com.hedera.test.factories.txns.FileDeleteFactory.newSignedFileDelete;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum FileDeleteScenarios implements TxnHandlingScenario {
    VANILLA_FILE_DELETE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedFileDelete(MISC_FILE_ID).get()));
        }
    },
    IMMUTABLE_FILE_DELETE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedFileDelete(IMMUTABLE_FILE_ID).get()));
        }
    },
    MISSING_FILE_DELETE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedFileDelete(MISSING_FILE_ID).get()));
        }
    }
}
