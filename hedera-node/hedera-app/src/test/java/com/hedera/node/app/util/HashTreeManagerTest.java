/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class HashTreeManagerTest {

    private final String SHA_384_ROOT_HASH =
            "9ba1f446384813c9416bbedf457c2f1a732c44c9bcbc196017ce3b72767c0cbde0e26652d13032ceb079ee38665c61b1";
    private final String HASHING_ALGO = "SHA-384";
    private MessageDigest digest;

    public HashTreeManagerTest() {}

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        digest = MessageDigest.getInstance(HASHING_ALGO);
    }

    @Test
    @DisplayName("verify the tree has the correct root after inserting a number of elements")
    public void testHashTreeManager() throws NoSuchAlgorithmException {
        HashTreeManager<String> tree = new HashTreeManager<>(new SimpleStringCodec(), digest);
        List<String> elements = Arrays.asList("Event1", "Transaction1", "Transaction2", "Event2", "Transaction3");
        for (String element : elements) {
            tree.addElement(element);
        }

        // Calculate the root hash
        Bytes rootHash = tree.getTreeRoot();
        assertThat(rootHash).isNotNull();
        assertThat(rootHash.toString()).isEqualTo(SHA_384_ROOT_HASH);
    }

    @Test
    public void testIncrementalConstruction() throws Exception {
        HashTreeManager<String> tree = new HashTreeManager<>(new SimpleStringCodec(), digest);

        List<String> actualOrder = new ArrayList<>();

        // Adding elements and asserting the root hash changes as expected
        tree.addElement("leaf1");
        actualOrder.add(tree.getHashList().toString());
        String rootHash = tree.getTreeRootAsString();
        assertThat(rootHash).isNotNull();
        assertThat(tree.getTreeRoot()).isNotNull();

        tree.addElement("leaf2");
        actualOrder.add(tree.getHashList().toString());
        String rootHashAfterAddingLeaf2 = tree.getTreeRootAsString();
        assertThat(rootHashAfterAddingLeaf2).isNotNull();
        assertThat(rootHashAfterAddingLeaf2).isEqualTo(tree.getTreeRootAsString()); // Should remain consistent

        tree.addElement("leaf3");
        actualOrder.add(tree.getHashList().toString());
        String rootHashAfterAddingLeaf3 = tree.getTreeRootAsString();
        assertThat(rootHashAfterAddingLeaf3).isNotNull();
        assertThat(rootHashAfterAddingLeaf3).isEqualTo(tree.getTreeRootAsString()); // Should remain consistent

        tree.addElement("leaf4");
        actualOrder.add(tree.getHashList().toString());
        String newRootHash = tree.getTreeRootAsString();
        assertThat(newRootHash).isNotNull();
        assertThat(newRootHash).isNotEqualTo(rootHashAfterAddingLeaf3); // Should change

        List<String> expectedOrder = Arrays.asList(
                "[38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b]",
                "[82a82763156fb2d6421ef934dd593921ebfebeee2b6626494715dab6664a5a4f1b64c7b83de6e9c93ae5199d898fc0b6]",
                "[347180f5e69054571b00bea816e5937b166dbcb51983882f76af907c6802956294f1b5ea1b317bba15fd14b6d0c73a1f]",
                "[550df2684cb630cbaed45d58fa7b538d0eb099dc31b3164f8468046da8ff03620b593b7058b82186eae060eab292feb9]");
        assertThat(actualOrder).containsExactlyElementsOf(expectedOrder);
        assertThat(rootHash).as("Checking that hashes are not equal").isNotEqualTo(newRootHash);
    }
}

class SimpleStringCodec implements Codec<String> {

    @NotNull
    @Override
    public String parse(@NotNull ReadableSequentialData readableSequentialData, boolean b, int i)
            throws ParseException {
        return null;
    }

    @Override
    public void write(@NotNull String s, @NotNull WritableSequentialData writableSequentialData) throws IOException {}

    @Override
    public int measure(@NotNull ReadableSequentialData readableSequentialData) throws ParseException {
        return 0;
    }

    @Override
    public int measureRecord(String s) {
        return 0;
    }

    @Override
    public boolean fastEquals(@NotNull String s, @NotNull ReadableSequentialData readableSequentialData)
            throws ParseException {
        return false;
    }
}
