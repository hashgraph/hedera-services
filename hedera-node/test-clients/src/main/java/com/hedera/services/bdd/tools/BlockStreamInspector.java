/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.tools;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.FilteredItemHash;
import com.hedera.hapi.block.stream.RecordFileItem;
import com.hedera.hapi.block.stream.input.EventHeader;
import com.hedera.hapi.block.stream.input.RoundHeader;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Command line tool to inspect block stream files.
 * Can read both compressed (.blk.gz) and uncompressed (.blk) files.
 */
public class BlockStreamInspector {
    private static final Logger log = LogManager.getLogger(BlockStreamInspector.class);
    private static final String UNCOMPRESSED_FILE_EXT = ".blk";
    private static final String COMPRESSED_FILE_EXT = UNCOMPRESSED_FILE_EXT + ".gz";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: BlockStreamInspector <path-to-block-file>");
            System.exit(1);
        }

        String filePath = args[0];
        try {
            inspectBlockFile(Paths.get(filePath));
        } catch (Exception e) {
            log.error("Failed to inspect block file {}", filePath, e);
            System.err.println("Error inspecting block file: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Inspects a block file and prints its contents.
     *
     * @param path Path to the block file
     * @throws IOException if there's an error reading the file
     */
    public static void inspectBlockFile(@NonNull final Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Block file not found: " + path);
        }

        String fileName = path.getFileName().toString();
        boolean isCompressed = fileName.endsWith(COMPRESSED_FILE_EXT);

        try (InputStream fileStream = Files.newInputStream(path);
                InputStream inputStream = isCompressed ? new GZIPInputStream(fileStream) : fileStream;
                BufferedInputStream bufferedStream = new BufferedInputStream(inputStream)) {

            Block block = readBlock(bufferedStream);
            printBlockInfo(block);

            // Print each block item
            int itemCount = 0;
            for (BlockItem item : block.items()) {
                itemCount++;
                printBlockItem(itemCount, item);
            }

            System.out.println("\nTotal items in block: " + itemCount);
        }
    }

    private static Block readBlock(InputStream inputStream) throws IOException {
        try {
            byte[] blockBytes = inputStream.readAllBytes();
            return Block.PROTOBUF.parse(Bytes.wrap(blockBytes));
        } catch (ParseException e) {
            throw new IOException("Failed to parse block data", e);
        }
    }

    private static void printBlockInfo(Block block) {
        System.out.println("Block Information:");
        System.out.println("------------------");
        System.out.println("Block Items: " + block.items().size());
        System.out.println("------------------\n");
    }

    private static void printBlockItem(int index, BlockItem item) {
        System.out.println("Item #" + index + ":");
        System.out.println("  Type: " + item.item().kind());
        switch (item.item().kind()) {
            case BLOCK_HEADER:
                BlockHeader blockHeader = item.blockHeader();
                System.out.println("  Block Header:");
                System.out.println("    HAPI Proto Version: " + blockHeader.hapiProtoVersion());
                System.out.println("    Software Version: " + blockHeader.softwareVersion());
                System.out.println("    Block Number: " + blockHeader.number());
                System.out.println("    Previous Block Hash: "
                        + bytesToHex(blockHeader.previousBlockHash().toByteArray()));
                System.out.println("    First Transaction Time: " + blockHeader.firstTransactionConsensusTime());
                System.out.println("    Hash Algorithm: " + blockHeader.hashAlgorithm());
                break;

            case EVENT_HEADER:
                EventHeader eventHeader = item.eventHeader();
                System.out.println("  Event Header:");
                System.out.println("    Event Core:");
                var eventCore = eventHeader.eventCore();
                System.out.println("      Creator Node ID: " + eventCore.creatorNodeId());
                System.out.println("      Birth Round: " + eventCore.birthRound());
                System.out.println("      Time Created: " + eventCore.timeCreated());
                System.out.println("      Parents: " + eventCore.parents().size());
                if (eventCore.hasVersion()) {
                    System.out.println("      Version: " + eventCore.version());
                }
                System.out.println(
                        "    Signature: " + bytesToHex(eventHeader.signature().toByteArray()));
                break;

            case ROUND_HEADER:
                RoundHeader roundHeader = item.roundHeader();
                System.out.println("  Round Header:");
                System.out.println("    Round Number: " + roundHeader.roundNumber());
                break;

            case EVENT_TRANSACTION:
                EventTransaction eventTx = item.eventTransaction();
                System.out.println("  Event Transaction:");
                if (eventTx.hasApplicationTransaction()) {
                    System.out.println("    Application Transaction: "
                            + bytesToHex(eventTx.applicationTransaction().toByteArray()));
                }
                if (eventTx.hasStateSignatureTransaction()) {
                    var stateSignature = eventTx.stateSignatureTransaction();
                    System.out.println("    State Signature Transaction:");
                    System.out.println("      Signature: "
                            + bytesToHex(stateSignature.signature().toByteArray()));
                    System.out.println(
                            "      Hash: " + bytesToHex(stateSignature.hash().toByteArray()));
                }
                break;

            case TRANSACTION_RESULT:
                TransactionResult txResult = item.transactionResult();
                System.out.println("  Transaction Result:");
                System.out.println("    Status: " + txResult.status());
                System.out.println("    Transaction Fee Charged: " + txResult.transactionFeeCharged());
                if (txResult.hasTransferList()) {
                    var transfers = txResult.transferList();
                    System.out.println("    Transfer List:");
                    System.out.println("      Account Amounts: "
                            + transfers.accountAmounts().size());
                }
                if (txResult.tokenTransferLists().size() > 0) {
                    System.out.println("    Token Transfer Lists: "
                            + txResult.tokenTransferLists().size());
                }
                if (txResult.automaticTokenAssociations().size() > 0) {
                    System.out.println("    Automatic Token Associations: "
                            + txResult.automaticTokenAssociations().size());
                }
                if (txResult.paidStakingRewards().size() > 0) {
                    System.out.println("    Paid Staking Rewards: "
                            + txResult.paidStakingRewards().size());
                }
                break;

            case TRANSACTION_OUTPUT:
                TransactionOutput txOutput = item.transactionOutput();
                System.out.println("  Transaction Output:");
                switch (txOutput.transaction().kind()) {
                    case CRYPTO_TRANSFER:
                        System.out.println("    Type: Crypto Transfer");
                        break;
                    case UTIL_PRNG:
                        System.out.println("    Type: Util PRNG");
                        break;
                    case CONTRACT_CALL:
                        System.out.println("    Type: Contract Call");
                        break;
                    case ETHEREUM_CALL:
                        System.out.println("    Type: Ethereum Call");
                        break;
                    case CONTRACT_CREATE:
                        System.out.println("    Type: Contract Create");
                        break;
                    case CREATE_SCHEDULE:
                        System.out.println("    Type: Create Schedule");
                        break;
                    case SIGN_SCHEDULE:
                        System.out.println("    Type: Sign Schedule");
                        break;
                    case TOKEN_AIRDROP:
                        System.out.println("    Type: Token Airdrop");
                        break;
                }
                break;

            case STATE_CHANGES:
                StateChanges stateChanges = item.stateChanges();
                System.out.println("  State Changes:");
                System.out.println("    Consensus Timestamp: " + stateChanges.consensusTimestamp());
                for (var change : stateChanges.stateChanges()) {
                    System.out.println("    State ID: " + change.stateId());
                    System.out.println(
                            "    Change Operation: " + change.changeOperation().kind());
                    switch (change.changeOperation().kind()) {
                        case STATE_ADD:
                            var add = change.stateAdd();
                            System.out.println("      New State Added:");
                            System.out.println("        State Type: " + add.stateType());
                            break;
                        case STATE_REMOVE:
                            System.out.println("      State Removed");
                            break;
                        case SINGLETON_UPDATE:
                            var update = change.singletonUpdate();
                            System.out.println("      Singleton Update:");
                            System.out.println(
                                    "        Value Type: " + update.newValue().kind());
                            break;
                        case MAP_UPDATE:
                            var mapUpdate = change.mapUpdate();
                            System.out.println("      Map Update:");
                            System.out.println("        Key Type: "
                                    + mapUpdate.key().keyChoice().kind());
                            System.out.println("        Value Type: "
                                    + mapUpdate.value().valueChoice().kind());
                            break;
                        case MAP_DELETE:
                            var mapDelete = change.mapDelete();
                            System.out.println("      Map Delete:");
                            System.out.println("        Key Type: "
                                    + mapDelete.key().keyChoice().kind());
                            break;
                        case QUEUE_PUSH:
                            var push = change.queuePush();
                            System.out.println("      Queue Push:");
                            System.out.println(
                                    "        Value Type: " + push.value().kind());
                            break;
                        case QUEUE_POP:
                            System.out.println("      Queue Pop");
                            break;
                    }
                }
                break;

            case FILTERED_ITEM_HASH:
                FilteredItemHash filteredHash = item.filteredItemHash();
                System.out.println("  Filtered Item Hash:");
                System.out.println(
                        "    Item Hash: " + bytesToHex(filteredHash.itemHash().toByteArray()));
                System.out.println("    Filtered Path: " + filteredHash.filteredPath());
                break;

            case BLOCK_PROOF:
                BlockProof blockProof = item.blockProof();
                System.out.println("  Block Proof:");
                System.out.println("    Block: " + blockProof.block());
                System.out.println("    Previous Block Hash: "
                        + bytesToHex(blockProof.previousBlockRootHash().toByteArray()));
                System.out.println("    Start of Block State Root Hash: "
                        + bytesToHex(blockProof.startOfBlockStateRootHash().toByteArray()));
                if (blockProof.siblingHashes().size() > 0) {
                    System.out.println(
                            "    Sibling Hashes: " + blockProof.siblingHashes().size());
                }
                break;

            case RECORD_FILE:
                RecordFileItem recordFile = item.recordFile();
                System.out.println("  Record File:");
                System.out.println("    Creation Time: " + recordFile.creationTime());
                System.out.println("    Record File Contents Size: "
                        + recordFile.recordFileContents().length());
                System.out.println(
                        "    Sidecar Files: " + recordFile.sidecarFileContents().size());
                System.out.println(
                        "    Signatures: " + recordFile.recordFileSignatures().size());
                break;
        }
        System.out.println();
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
