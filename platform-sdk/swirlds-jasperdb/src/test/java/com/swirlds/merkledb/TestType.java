/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import static com.swirlds.merkledb.MerkleDbTestUtils.hash;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Supports parameterized testing of {@link MerkleDbDataSource} with
 * both fixed- and variable-size data.
 *
 * Used with JUnit's {@link org.junit.jupiter.params.provider.EnumSource} annotation.
 */
public enum TestType {

    /** Parameterizes a test with fixed-size key and fixed-size data. */
    fixed_fixed(),
    /** Parameterizes a test with fixed-size key and variable-size data. */
    fixed_variable(),
    /** Parameterizes a test with fixed-size complex key and fixed-size data. */
    fixedComplex_fixed(),
    /** Parameterizes a test with fixed-size complex key and variable-size data. */
    fixedComplex_variable(),
    /** Parameterizes a test with variable-size key and fixed-size data. */
    variable_fixed(),
    /** Parameterizes a test with variable-size key and variable-size data. */
    variable_variable(),
    /** Parameterizes a test with variable-size complex key and fixed-size data. */
    variableComplex_fixed(),
    /** Parameterizes a test with variable-size complex key and variable-size data. */
    variableComplex_variable();

    public <K extends VirtualKey<? super K>, V extends VirtualValue> DataTypeConfig<K, V> dataType() {
        return new DataTypeConfig<>(this);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "unused"})
    public static class DataTypeConfig<K extends VirtualKey<? super K>, V extends VirtualValue> {

        private final TestType testType;
        private final KeySerializer<? extends VirtualLongKey> keySerializer;
        private final ValueSerializer<? extends ExampleByteArrayVirtualValue> valueSerializer;

        public DataTypeConfig(TestType testType) {
            this.testType = testType;
            this.keySerializer = createKeySerializer();
            this.valueSerializer = createValueSerializer();
        }

        public KeySerializer<? extends VirtualLongKey> getKeySerializer() {
            return keySerializer;
        }

        public ValueSerializer<? extends ExampleByteArrayVirtualValue> getValueSerializer() {
            return valueSerializer;
        }

        private KeySerializer<? extends VirtualLongKey> createKeySerializer() {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixed_variable:
                    return new ExampleLongKeyFixedSize.Serializer();
                case fixedComplex_fixed:
                case fixedComplex_variable:
                    return new ExampleLongLongKeyFixedSize.Serializer();
                case variable_fixed:
                case variable_variable:
                    return new ExampleLongKeyVariableSize.Serializer();
                case variableComplex_fixed:
                case variableComplex_variable:
                    return new ExampleLongLongKeyVariableSize.Serializer();
            }
        }

        private ValueSerializer<? extends ExampleByteArrayVirtualValue> createValueSerializer() {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixedComplex_fixed:
                case variable_fixed:
                case variableComplex_fixed:
                    return new ExampleFixedSizeVirtualValueSerializer();
                case fixed_variable:
                case fixedComplex_variable:
                case variable_variable:
                case variableComplex_variable:
                    return new ExampleVariableSizeVirtualValueSerializer();
            }
        }

        public VirtualLongKey createVirtualLongKey(final int i) {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixed_variable:
                    return new ExampleLongKeyFixedSize(i);
                case fixedComplex_fixed:
                case fixedComplex_variable:
                    return new ExampleLongLongKeyFixedSize(i);
                case variable_fixed:
                case variable_variable:
                    return new ExampleLongKeyVariableSize(i);
                case variableComplex_fixed:
                case variableComplex_variable:
                    return new ExampleLongLongKeyVariableSize(i);
            }
        }

        /**
         * Get the file size for a file created in DataFileLowLevelTest.createFile test. Values here are measured values
         * from a known good test run.
         */
        public long getDataFileLowLevelTestFileSize() {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixed_variable:
                case fixedComplex_fixed:
                case fixedComplex_variable:
                case variable_fixed:
                case variable_variable:
                case variableComplex_fixed:
                case variableComplex_variable:
                    return 24576L;
            }
        }

        public boolean hasKeyToPathStore() {
            return (keySerializer.getSerializedSize() != Long.BYTES);
        }

        public MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> createDataSource(
                final Path dbPath,
                final String name,
                final int size,
                final long internalHashesRamToDiskThreshold,
                final boolean enableMerging,
                boolean preferDiskBasedIndexes)
                throws IOException {
            final MerkleDb database = MerkleDb.getInstance(dbPath);
            final MerkleDbTableConfig<? extends VirtualLongKey, ? extends ExampleByteArrayVirtualValue> tableConfig =
                    new MerkleDbTableConfig<>(
                                    (short) 1, DigestType.SHA_384,
                                    (short) keySerializer.getCurrentDataVersion(), keySerializer,
                                    (short) valueSerializer.getCurrentDataVersion(), valueSerializer)
                            .preferDiskIndices(preferDiskBasedIndexes)
                            .maxNumberOfKeys(size * 10L)
                            .internalHashesRamToDiskThreshold(internalHashesRamToDiskThreshold);
            return database.createDataSource(name, (MerkleDbTableConfig) tableConfig, enableMerging);
        }

        public MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> getDataSource(
                final Path dbPath, final String name, final boolean enableMerging) throws IOException {
            final MerkleDb database = MerkleDb.getInstance(dbPath);
            return database.getDataSource(name, enableMerging);
        }

        public VirtualLeafRecord<VirtualLongKey, ExampleByteArrayVirtualValue> createVirtualLeafRecord(final int i) {
            return createVirtualLeafRecord(i, i, i);
        }

        public VirtualLeafRecord<VirtualLongKey, ExampleByteArrayVirtualValue> createVirtualLeafRecord(
                final long path, final int i, final int valueIndex) {

            switch (testType) {
                default:
                case fixed_fixed:
                case fixedComplex_fixed:
                case variable_fixed:
                case variableComplex_fixed:
                    return new VirtualLeafRecord<>(
                            path, hash(i), createVirtualLongKey(i), new ExampleFixedSizeVirtualValue(valueIndex));
                case fixed_variable:
                case fixedComplex_variable:
                case variable_variable:
                case variableComplex_variable:
                    return new VirtualLeafRecord<>(
                            path, hash(i), createVirtualLongKey(i), new ExampleVariableSizeVirtualValue(valueIndex));
            }
        }
    }
}
