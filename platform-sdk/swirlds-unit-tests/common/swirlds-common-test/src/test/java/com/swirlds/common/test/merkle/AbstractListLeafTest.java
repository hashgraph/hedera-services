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

package com.swirlds.common.test.merkle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.merkle.utility.AbstractListLeaf;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.test.merkle.dummy.DummyListLeaf;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("AbstractListLeaf Test")
class AbstractListLeafTest {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("AbstractListLeaf Test")
    void abstractListLeafTest() {
        AbstractListLeaf<SerializableLong> leaf = new DummyListLeaf();
        final List<SerializableLong> referenceList = new LinkedList<>();

        assertEquals(referenceList, leaf, "lists should be equal");
        AbstractListLeaf<SerializableLong> copy = leaf.copy();
        assertEquals(referenceList, copy, "lists should be equal");
        leaf.add(new SerializableLong(1234)); // this should not effect the copy
        assertEquals(referenceList, copy, "lists should be equal");

        leaf = new DummyListLeaf();

        for (int i = 0; i < 100; i++) {
            referenceList.add(new SerializableLong(i));
            leaf.add(new SerializableLong(i));
        }

        assertEquals(referenceList, leaf, "lists should be equal");
        copy = leaf.copy();
        assertEquals(referenceList, copy, "lists should be equal");
        leaf.add(new SerializableLong(1234)); // this should not effect the copy
        assertEquals(referenceList, copy, "lists should be equal");
    }
}
