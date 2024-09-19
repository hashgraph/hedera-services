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

package com.hedera.node.app.blocks.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GrpcBlockItemWriterTest {

    @Mock
    private BlockStreamConfig blockStreamConfig;

    @Test
    public void testGrpcBlockItemWriterConstructor() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        new GrpcBlockItemWriter(blockStreamConfig);
    }

    @Test
    public void testOpenBlock() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Assertion to check if the directory is created

        grpcBlockItemWriter.openBlock(1);
    }

    @Test
    public void testOpenBlockCannotInitializeTwice() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Assertion to check if the directory is created

        grpcBlockItemWriter.openBlock(1);

        assertThatThrownBy(() -> grpcBlockItemWriter.openBlock(1), "Cannot initialize a GrpcBlockItemWriter twice")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testOpenBlockNegativeBlockNumber() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Assertion to check if the directory is created

        assertThatThrownBy(() -> grpcBlockItemWriter.openBlock(-1), "Block number must be non-negative")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testWriteItem() throws IOException {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Open a block
        grpcBlockItemWriter.openBlock(1);

        // Create a Bytes object and write it
        Bytes bytes = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});
        byte[] expectedBytes = {10, 5, 1, 2, 3, 4, 5};
        grpcBlockItemWriter.writeItem(bytes);

        // Close the block
        grpcBlockItemWriter.closeBlock();
    }

    @Test
    public void testWriteItemBeforeOpen() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Create a Bytes object and write it
        Bytes bytes = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});

        assertThatThrownBy(() -> grpcBlockItemWriter.writeItem(bytes), "Cannot write item before opening a block")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testCloseBlock() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Open a block
        grpcBlockItemWriter.openBlock(1);

        // Close the block
        grpcBlockItemWriter.closeBlock();
    }

    @Test
    public void testCloseBlockNotOpen() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        assertThatThrownBy(grpcBlockItemWriter::closeBlock, "Cannot close a GrpcBlockItemWriter that is not open")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testCloseBlockAlreadyClosed() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(9090);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Open a block
        grpcBlockItemWriter.openBlock(1);

        // Close the block
        grpcBlockItemWriter.closeBlock();

        assertThatThrownBy(grpcBlockItemWriter::closeBlock, "Cannot close a GrpcBlockItemWriter that is already closed")
                .isInstanceOf(IllegalStateException.class);
    }
}
