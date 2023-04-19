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
import com.swirlds.common.merkle.utility.SerializableLong;
import java.util.List;

public class NextSeqConsList extends AbstractListLeaf<SerializableLong> {

    public static final long CLASS_ID = 0xe38268806d8e1e8L;

    public NextSeqConsList() {
        super();
    }

    public NextSeqConsList(List<SerializableLong> longList) {
        super(longList);
    }

    public NextSeqConsList(int initialSize) {
        super(initialSize);
    }

    public NextSeqConsList(final NextSeqConsList that) {
        super(that);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    protected int getMaxSize() {
        return 1000; // FUTURE WORK this should match (or exceed) the total number of nodes
    }

    @Override
    public NextSeqConsList copy() {
        return new NextSeqConsList(this);
    }
}
