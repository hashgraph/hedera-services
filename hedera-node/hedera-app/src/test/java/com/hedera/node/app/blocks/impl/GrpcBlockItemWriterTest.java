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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
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
        when(blockStreamConfig.port()).thenReturn(8080);

        final GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);
        assertThat(grpcBlockItemWriter).isNotNull();
        assertThat(grpcBlockItemWriter.getStub()).isNotNull();
    }

    @Test
    public void testOpenBlock() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(8080);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);
        grpcBlockItemWriter.openBlock(1);
        assertThat(grpcBlockItemWriter.getBlockNumber()).isEqualTo(1);
        assertThat(grpcBlockItemWriter.getState()).isEqualTo(GrpcBlockItemWriter.State.OPEN);
    }

    @Test
    public void testOpenBlockCannotInitializeTwice() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(8080);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        grpcBlockItemWriter.openBlock(1);

        assertThatThrownBy(() -> grpcBlockItemWriter.openBlock(1), "Cannot initialize a GrpcBlockItemWriter twice")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testOpenBlockNegativeBlockNumber() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(8080);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        assertThatThrownBy(() -> grpcBlockItemWriter.openBlock(-1), "Block number must be non-negative")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testWriteItem() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(8080);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Open a block
        grpcBlockItemWriter.openBlock(1);

        // Create BlockProof as easiest way to build object from BlockStreams
        Bytes bytes = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});
        final var proof = new BlockProof.Builder().blockSignature(bytes).siblingHashes(new ArrayList<>());
        final var blockProof = BlockItem.PROTOBUF.toBytes(
                BlockItem.newBuilder().blockProof(proof).build());
        grpcBlockItemWriter.writeItem(blockProof);

        // Close the block
        grpcBlockItemWriter.closeBlock();
    }

    @Test
    public void testWriteItemBeforeOpen() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(8080);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Create BlockProof as easiest way to build object from BlockStreams
        Bytes bytes = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});
        final var proof = new BlockProof.Builder().blockSignature(bytes).siblingHashes(new ArrayList<>());
        final var blockProof = BlockItem.PROTOBUF.toBytes(
                BlockItem.newBuilder().blockProof(proof).build());

        assertThatThrownBy(() -> grpcBlockItemWriter.writeItem(blockProof), "Cannot write item before opening a block")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testCloseBlock() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(8080);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Open a block
        grpcBlockItemWriter.openBlock(1);

        // Close the block
        grpcBlockItemWriter.closeBlock();
    }

    @Test
    public void testCloseBlockNotOpen() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(8080);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        assertThatThrownBy(grpcBlockItemWriter::closeBlock, "Cannot close a GrpcBlockItemWriter that is not open")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testCloseBlockAlreadyClosed() {
        when(blockStreamConfig.address()).thenReturn("localhost");
        when(blockStreamConfig.port()).thenReturn(8080);

        GrpcBlockItemWriter grpcBlockItemWriter = new GrpcBlockItemWriter(blockStreamConfig);

        // Open a block
        grpcBlockItemWriter.openBlock(1);

        // Close the block
        grpcBlockItemWriter.closeBlock();

        assertThatThrownBy(grpcBlockItemWriter::closeBlock, "Cannot close a GrpcBlockItemWriter that is already closed")
                .isInstanceOf(IllegalStateException.class);
    }
}
