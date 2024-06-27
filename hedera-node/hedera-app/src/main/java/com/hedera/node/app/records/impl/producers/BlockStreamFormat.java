/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.hedera.node.app.records.impl.producers;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.EventMetadata;
import com.hedera.hapi.block.stream.FilteredBlockItem;
import com.hedera.hapi.block.stream.input.SystemTransaction;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import com.hedera.hapi.block.stream.BlockItem;
import java.util.stream.Stream;

/**
 * Defines API for computing running hashes, and converting {@link BlockItem}s into
 * {@link Bytes}. These operations are dependent on the block stream version (v7, etc),
 * but are not otherwise related to the construction of the record file or writing the bytes to a destination such as
 * disk or a socket. Thus, an implementation of this interface for a particular version of the block record format can
 * be reused across the different implementations of {@link BlockStreamWriter}.
 */
public interface BlockStreamFormat {

    Bytes serializeBlockItem(@NonNull final BlockItem blockItem);

    Bytes serializeBlockHeader(@NonNull final BlockHeader blockHeader);

    Bytes serializeEventMetadata(@NonNull final EventMetadata eventMetadata);

    Bytes serializeSystemTransaction(@NonNull final SystemTransaction systemTransaction);

    Bytes serializeTransaction(@NonNull final Transaction transaction);

    Bytes serializeTransactionResult(@NonNull final TransactionResult transactionResult);

    Bytes serializeTransactionOutput(@NonNull final TransactionOutput transactionOutput);

    Bytes serializeStateChanges(@NonNull final StateChanges stateChanges);

    Bytes serializeBlockProof(@NonNull final BlockProof blockProof);

    Bytes serializeFilteredBlockItem(@NonNull final FilteredBlockItem filteredBlockItem);
}