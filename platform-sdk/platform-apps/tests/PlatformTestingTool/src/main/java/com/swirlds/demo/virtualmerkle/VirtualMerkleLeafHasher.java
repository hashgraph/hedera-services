/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.virtualmerkle;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.merkle.iterators.MerkleIterationOrder.BREADTH_FIRST;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKey;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapValue;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

// Note: This class is intended to be used with a human in the loop who is watching standard in and standard err.

/**
 * Validator to read a data source and all its data and check the complete data set is valid.
 */
public class VirtualMerkleLeafHasher<K extends VirtualKey, V extends VirtualValue> {

    private static final Configuration CONFIGURATION = ConfigurationBuilder.create()
            .withConfigDataType(MerkleDbConfig.class)
            .withConfigDataType(VirtualMapConfig.class)
            .withConfigDataType(TemporaryFileConfig.class)
            .withConfigDataType(StateCommonConfig.class)
            .build();

    /** The data source we are validating */
    private final VirtualMap<K, V> virtualMap;

    /**
     * Open the virtual map and validate all its data
     *
     * @param virtualMap
     * 		The virtual map to validate
     */
    public VirtualMerkleLeafHasher(final VirtualMap<K, V> virtualMap) {
        this.virtualMap = virtualMap;
    }

    /**
     * Validate all data in the virtual map
     *
     * @return the rolling hash computed across all leafs in order
     */
    public Hash validate() throws IOException {
        Hash hash = null;

        final Iterator<MerkleNode> iterator = virtualMap.treeIterator().setOrder(BREADTH_FIRST);

        while (iterator.hasNext()) {
            final MerkleNode node = iterator.next();
            if (node != null) {
                if (node instanceof VirtualLeafNode) {
                    final VirtualLeafNode<K, V> leaf = node.cast();
                    hash = computeNextHash(hash, leaf);
                }
            }
        }

        return hash;
    }

    /**
     * computes the rolling hash resulting from the concatenation of the previous hash with the leaf's serialized
     * key and value. Data to be hashed looks like this: [prevHash,leaf.key.serialize,leaf.value.serialize]
     *
     * @param prevHash
     * 		hash result of previous call to this function
     * @param leaf
     * 		value to be serialized and hashed with the previous hash
     * @return rolling hash of [prevHash,leaf.key.serialize,leaf.value.serialize]
     * @throws IOException if an I/O error occurs
     */
    public Hash computeNextHash(final Hash prevHash, final VirtualLeafNode<K, V> leaf) throws IOException {
        try (final ByteArrayOutputStream bout = new ByteArrayOutputStream();
                final SerializableDataOutputStream out = new SerializableDataOutputStream(bout)) {

            if (prevHash != null) {
                // add Previous Hash
                prevHash.getBytes().writeTo(out);
            }
            // add leaf key
            leaf.getKey().serialize(out);
            // add leaf value
            leaf.getValue().serialize(out);

            out.flush();
            return hashOf(bout.toByteArray());
        }
    }

    /**
     * Generates the hash of the provided byte array. Uses the default hash algorithm as specified by {@link
     * com.swirlds.common.crypto.Cryptography#digestSync(byte[])}.
     *
     * @param content
     * 		the content for which the hash is to be computed
     * @return the hash of the content
     */
    public static Hash hashOf(final byte[] content) {
        return new Hash(CryptographyHolder.get().digestSync(content));
    }

    public static void main(final String[] args) throws IOException {
        try {
            final ConstructableRegistry registry = ConstructableRegistry.getInstance();
            registry.registerConstructables("com.swirlds.virtualmap");
            registry.registerConstructables("com.swirlds.merkledb");
            registry.registerConstructables("com.swirlds.demo.virtualmerkle");
            registry.registerConstructables("com.swirlds.common.crypto");
        } catch (final ConstructableRegistryException e) {
            e.printStackTrace();
            return;
        }

        final String accountsName = "accounts.vmap";
        final String scName = "smartContracts.vmap";
        final String scByteCodeName = "smartContractByteCode.vmap";

        final List<Path> roundsFolders;
        final Path classFolder = getAbsolutePath(args[0]);
        try (final Stream<Path> classFolderList = Files.list(classFolder)) {
            final Path nodeFolder = classFolderList.toList().get(0);
            try (final Stream<Path> swirldIdList = Files.list(nodeFolder.resolve("123"))) {
                roundsFolders = swirldIdList.toList();
            }
        }

        // MerkleDbDataSourceBuilder creates files in a temp folder by default. The temp folder may be on a
        // different file system than the file(s) used to deserialize the maps. In such case, builders will fail
        // to create hard file links when constructing new data sources. To fix it, let's override the default
        // temp location to the same file system as the files to load
        LegacyTemporaryFileBuilder.overrideTemporaryFileLocation(classFolder.resolve("tmp"));

        for (final Path roundFolder : roundsFolders) {
            // reset the default instance path to force creation of a new MerkleDB instance
            // https://github.com/hashgraph/hedera-services/pull/8534
            MerkleDb.resetDefaultInstancePath();
            Hash accountsHash;
            Hash scHash;
            Hash byteCodeHash;

            try {
                final VirtualMap<AccountVirtualMapKey, AccountVirtualMapValue> accountsMap =
                        new VirtualMap<>(CONFIGURATION);
                accountsMap.loadFromFile(roundFolder.resolve(accountsName));
                final VirtualMerkleLeafHasher<AccountVirtualMapKey, AccountVirtualMapValue> accountsHasher =
                        new VirtualMerkleLeafHasher<>(accountsMap);
                accountsHash = accountsHasher.validate();
            } catch (final IOException e) {
                accountsHash = null;
            }

            try {
                final VirtualMap<SmartContractMapKey, SmartContractMapValue> scMap = new VirtualMap<>(CONFIGURATION);
                scMap.loadFromFile(roundFolder.resolve(scName));
                final VirtualMerkleLeafHasher<SmartContractMapKey, SmartContractMapValue> scHasher =
                        new VirtualMerkleLeafHasher<>(scMap);
                scHash = scHasher.validate();
            } catch (final IOException e) {
                scHash = null;
            }

            try {
                final VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> byteCodeMap =
                        new VirtualMap<>(CONFIGURATION);
                byteCodeMap.loadFromFile(roundFolder.resolve(scByteCodeName));
                final VirtualMerkleLeafHasher<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue>
                        byteCodeHasher = new VirtualMerkleLeafHasher<>(byteCodeMap);
                byteCodeHash = byteCodeHasher.validate();
            } catch (final IOException e) {
                byteCodeHash = null;
            }

            final Path vmapLogPath = roundFolder.resolve("vmapHashes.json");

            try (final FileOutputStream fos = new FileOutputStream(vmapLogPath.toString())) {
                final ObjectMapper mapper = new ObjectMapper();
                final ObjectNode rootNode = mapper.createObjectNode();

                // add content to json
                if (accountsHash != null) {
                    rootNode.put("accounts", accountsHash.toHex());
                } else {
                    rootNode.put("accounts", "empty");
                }

                if (scHash != null) {
                    rootNode.put("sc", scHash.toHex());
                } else {
                    rootNode.put("sc", "empty");
                }

                if (byteCodeHash != null) {
                    rootNode.put("byteCode", byteCodeHash.toHex());
                } else {
                    rootNode.put("byteCode", "empty");
                }

                // write built json to file
                fos.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rootNode));
            }
        }
    }
}
