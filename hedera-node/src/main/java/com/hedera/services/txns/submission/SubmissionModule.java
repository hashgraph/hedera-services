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
package com.hedera.services.txns.submission;

import static com.hedera.services.txns.submission.StructuralPrecheck.HISTORICAL_MAX_PROTO_MESSAGE_DEPTH;

import com.hedera.services.txns.submission.annotations.MaxProtoMsgDepth;
import com.hedera.services.txns.submission.annotations.MaxSignedTxnSize;
import com.swirlds.common.system.Platform;
import dagger.Module;
import dagger.Provides;

@Module
public final class SubmissionModule {
    @Provides
    @MaxSignedTxnSize
    static int provideMaxSignedTxnSize() {
        return Platform.getTransactionMaxBytes();
    }

    @Provides
    @MaxProtoMsgDepth
    static int provideMaxProtoMsgDepth() {
        return HISTORICAL_MAX_PROTO_MESSAGE_DEPTH;
    }

    private SubmissionModule() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
