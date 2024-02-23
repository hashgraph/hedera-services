/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.merkle.dummy;

/**
 * The exact same behavior as a DummyMerkleInternal but with a different class Id.
 */
public class DummyMerkleInternal2 extends DummyMerkleInternal {

    protected final long classId = 0x9876fcbbL;

    public DummyMerkleInternal2() {
        super();
    }

    public DummyMerkleInternal2(String value) {
        super(value);
    }

    @Override
    public long getClassId() {
        return classId;
    }
}
