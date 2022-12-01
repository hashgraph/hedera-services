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
package com.hedera.test.factories.txns;

import com.swirlds.common.system.transaction.Transaction;

public class PlatformTxnFactory {
    public static Transaction from(final com.hederahashgraph.api.proto.java.Transaction signedTxn) {
        // TODO: Not working anymore with modules (see
        // https://github.com/swirlds/swirlds-platform/issues/6388)
        return null;
    }

    public static Transaction withClearFlag(final Transaction txn) {
        // TODO: Not working anymore with modules (see
        // https://github.com/swirlds/swirlds-platform/issues/6388)
        return null;
    }
}
