/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import com.swirlds.common.merkle.utility.AbstractListLeaf;

public class TransactionCounterList extends AbstractListLeaf<TransactionCounter> {

    public static final long CLASS_ID = 0x791c240dd28d1f1dL;

    public TransactionCounterList() {
        super();
    }

    public TransactionCounterList(int initialSize) {
        super(initialSize);
    }

    public TransactionCounterList(final TransactionCounterList that) {
        super(that);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getMaxSize() {
        return Integer.MAX_VALUE; // TODO what is a sane value for this?
    }

    @Override
    public TransactionCounterList copy() {
        return new TransactionCounterList(this);
    }
}
