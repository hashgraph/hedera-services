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
package com.hedera.services.bdd.spec.infrastructure;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOpFinisher;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;

public class DelegatingOpFinisher implements HapiSpecOpFinisher {
    private final HapiTxnOp<?> delegate;

    public DelegatingOpFinisher(HapiTxnOp<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public long submitTime() {
        return delegate.getSubmitTime();
    }

    @Override
    public void finishFor(HapiSpec spec) throws Throwable {
        delegate.finalizeExecFor(spec);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
