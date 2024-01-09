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
import com.hedera.test.factories.txns.ScheduleDeleteFactory;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public enum ScheduleDeleteScenarios implements TxnHandlingScenario {
    SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleDeleteFactory.newSignedScheduleDelete()
                    .deleting(KNOWN_SCHEDULE_WITH_ADMIN)
                    .get());
        }
    },
    SCHEDULE_DELETE_WITH_MISSING_SCHEDULE {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleDeleteFactory.newSignedScheduleDelete()
                    .deleting(UNKNOWN_SCHEDULE)
                    .get());
        }
    },
    SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleDeleteFactory.newSignedScheduleDelete()
                    .deleting(KNOWN_SCHEDULE_IMMUTABLE)
                    .get());
        }
    }
}
