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

package com.hedera.node.blocknode.core;

import com.hedera.services.stream.v7.proto.*;
import com.swirlds.platform.system.Platform;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNodeFileWatcherService {
    private static final Logger logger = LogManager.getLogger(BlockNodeServer.class);

    private Platform platform;

    private byte[] readCompressedFileBytes(final Path filepath) throws IOException {
        return (new GZIPInputStream(new FileInputStream(filepath.toString()))).readAllBytes();
    }

    private String publicKey =
            "308201a2300d06092a864886f70d01010105000382018f003082018a0282018100c1a0ff5d2372b53d12d12bb87dd03f5e3427e0cee1d3c898bbd320c4b3dd17257944ea39a07f5344d9abfcdd50214072f1bbc12173fe7933d032c7d210734cc92d24be22b44cf50c2aa06f19bcd75180dc3e8dedd5ffcac02bf98721df9c3e79f20e9942cac9328b99160afea44d42c87b0147f3f29567085ed3f841dbe37aba35a2c5446bc638c62c703a6f680fa0601bfe7c6254e9fe2f471670ecdcca26128716a08f4141595ec0c4ac7ae589f37deede17480ecc1500f88335d0e33929725e8e4e775f3e4aa44c867bc86d3bf6d7165a4b766dd4ceb622221634a0a3d82840800b5b3e540640ea2f8c5749c3a6a0e0c474515c3f0ed9aadab8f84423a8954fd7f4e40b73125aeced4f791dba5052e3f5b3191a430f9b2dd30e4071cc54280c830da0d1e0dd54300c243ef08d9f81b3a90373f10910b6f4975bb2d861273993221e42b82b5af823267f79de90a7221129f0423724f9208a4ca15a73458c555e08e015db9d77c884acacaf4971d3854ea7bbdd9cfaf49df852c11473e96fa10203010001";

    private void readSignatureFile(File file) {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            // Assuming the format of the signature file
            // You need to adjust the reading logic based on your actual signature file format
            String signature = String.valueOf(dis.readByte());
            logger.info("Signature: " + signature);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private FileAlterationListenerAdaptor buildFileListener() {
        return new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                final Path newFilePath = file.toPath();
                try {
                    // Check if the file is not a .gz file and ends with _sig
                    if (!file.getName().endsWith(".gz") && file.getName().endsWith("_sig")) {
                        // Read the file contents
                        byte[] content = Files.readAllBytes(newFilePath);
                        // String fileContent = new String(content, StandardCharsets.UTF_8);
                        readSignatureFile(file);
                        // Log the file contents
                        logger.info("Contents of " + file.getName() + ": " + content);

                    }
                    // If it's a .gz file, unzip and parse it as before
                    else if (file.getName().endsWith(".gz")) {
                        byte[] content = readCompressedFileBytes(newFilePath);
                        Block block = Block.parseFrom(content);
                        block.getItemsList().stream().toList().forEach(logger::info);
                        List<BlockStateProof> stateProofList = block.getItemsList().stream()
                                .filter(BlockItem::hasStateProof)
                                .map(BlockItem::getStateProof)
                                .toList();
                        List<SystemTransaction> systemTransaction = block.getItemsList().stream()
                                .filter(BlockItem::hasSystemTransaction)
                                .map(BlockItem::getSystemTransaction)
                                .toList();
                        verifySignatures(stateProofList, systemTransaction);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                } catch (InvalidKeySpecException e) {
                    throw new RuntimeException(e);
                } catch (SignatureException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchProviderException e) {
                    throw new RuntimeException(e);
                } catch (InvalidKeyException e) {
                    throw new RuntimeException(e);
                }
                logger.info("--- file create: " + newFilePath);
            }

            @Override
            public void onFileDelete(File file) {
                // no-op
            }

            @Override
            public void onFileChange(File file) {
                // no-op
            }
        };
    }

    private boolean verifySignatures(List<BlockStateProof> stateProof, List<SystemTransaction> systemTransactions)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, InvalidKeyException,
                    SignatureException {
        byte[] publicKeyByteArray = HexFormat.of().parseHex(publicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyByteArray, "RSA");
        RSAPublicKey restoredPublicKey =
                (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
        Signature sig = Signature.getInstance("SHA384withRSA", "SunRsaSign");
        sig.initVerify(restoredPublicKey);
        sig.update(
                systemTransactions.getFirst().getStateSignature().getStateHash().toByteArray());

        if (!sig.verify(
                stateProof.getFirst().getBlockSignatures(0).getSignature().toByteArray())) {
            return false;
        }
        ;
        return true;
    }

    public BlockNodeFileWatcherService() {
        final Path blocksPath = Path.of("/Users/konstantinablazhukova/Projects/hedera-services/hedera-node/hedera-app/"
                + "build/node/data/block-streams/block0.0.3/");
        final FileAlterationObserver observer = new FileAlterationObserver(blocksPath.toFile());
        observer.addListener(buildFileListener());
        final FileAlterationMonitor monitor = new FileAlterationMonitor(500L);
        monitor.addObserver(observer);
        try {
            monitor.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
