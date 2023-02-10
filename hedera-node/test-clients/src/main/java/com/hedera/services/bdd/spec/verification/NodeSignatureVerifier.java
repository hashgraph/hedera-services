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
package com.hedera.services.bdd.spec.verification;

import static java.util.stream.Collectors.toList;

import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.swirlds.common.utility.CommonUtils;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class NodeSignatureVerifier {
    private static final Logger log = LogManager.getLogger(NodeSignatureVerifier.class);
    private static final Marker MARKER = MarkerManager.getMarker("NodeSignatureVerifier");

    static final byte TYPE_SIGNATURE = 3; // the file content signature, should not be hashed
    static final byte TYPE_FILE_HASH =
            4; // next 48 bytes are hash384 of content of corresponding RecordFile

    Map<String, PublicKey> accountKeys = new HashMap<>();

    public NodeSignatureVerifier(NodeAddressBook addressBook) {
        initKeysFrom(addressBook);
    }

    private void initKeysFrom(NodeAddressBook addressBook) {
        for (NodeAddress nodeAddress : addressBook.getNodeAddressList()) {
            String account = new String(nodeAddress.getMemo().toByteArray());
            try {
                accountKeys.put(account, loadPublicKey(nodeAddress.getRSAPubKey()));
                log.info("Discovered node " + account);
            } catch (IllegalArgumentException ex) {
                log.warn(
                        "Malformed address key {} for node {}",
                        nodeAddress.getRSAPubKey(),
                        account);
                throw new IllegalArgumentException("Malformed public key!");
            }
        }
    }

    public List<String> nodes() {
        return accountKeys.entrySet().stream().map(Map.Entry::getKey).sorted().collect(toList());
    }

    private PublicKey loadPublicKey(String rsaPubKeyString) throws IllegalArgumentException {
        return bytesToPublicKey(CommonUtils.unhex(rsaPubKeyString));
    }

    private static PublicKey bytesToPublicKey(byte[] bytes) {
        PublicKey publicKey;
        try {
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            publicKey = keyFactory.generatePublic(publicKeySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.warn("Malformed record key!", e);
            throw new IllegalArgumentException("Malformed public key!");
        }
        return publicKey;
    }

    /* The input list is assumed to contain multiple signatures for THE SAME RECORD FILE. */
    public List<String> verifySignatureFiles(List<File> sigFiles) {
        Map<String, Set<String>> hashToNodeAccountIDs = new HashMap<>();
        for (File sigFile : sigFiles) {
            String nodeAccountID = getAccountIDStringFromFilePath(sigFile.getPath());
            if (verifySignatureFile(sigFile)) {
                byte[] hash = extractHashAndSigFromFile(sigFile).getLeft();
                String hashString = CommonUtils.hex(hash);
                Set<String> nodeAccountIDs =
                        hashToNodeAccountIDs.getOrDefault(hashString, new HashSet<>());
                nodeAccountIDs.add(nodeAccountID);
                hashToNodeAccountIDs.put(hashString, nodeAccountIDs);
            } else {
                log.info(
                        MARKER,
                        "Node{} has invalid signature file {}",
                        nodeAccountID,
                        sigFile.getName());
            }
        }

        double majorityCounts = accountKeys.size() * 2 / 3.0;
        for (String key : hashToNodeAccountIDs.keySet()) {
            Set<String> nodes = hashToNodeAccountIDs.get(key);
            if (nodes.size() > majorityCounts) {
                return new ArrayList<>(nodes);
            }
        }
        return null;
    }

    private boolean verifySignatureFile(File sigFile) {
        Pair<byte[], byte[]> hashAndSig = extractHashAndSigFromFile(sigFile);

        byte[] signedData = hashAndSig.getLeft();
        String nodeAccountID = getAccountIDStringFromFilePath(sigFile.getPath());
        byte[] signature = hashAndSig.getRight();

        return verifySignature(signedData, signature, nodeAccountID);
    }

    private Pair<byte[], byte[]> extractHashAndSigFromFile(File file) {
        FileInputStream stream = null;
        byte[] sig = null;

        if (!file.exists()) {
            log.warn("Signature file {} does not exist!", file);
            return null;
        }

        try {
            stream = new FileInputStream(file);
            DataInputStream dis = new DataInputStream(stream);
            byte[] fileHash = new byte[48];

            while (dis.available() != 0) {
                try {
                    byte typeDelimiter = dis.readByte();

                    switch (typeDelimiter) {
                        case TYPE_FILE_HASH:
                            dis.read(fileHash);
                            break;
                        case TYPE_SIGNATURE:
                            int sigLength = dis.readInt();
                            byte[] sigBytes = new byte[sigLength];
                            dis.readFully(sigBytes);
                            sig = sigBytes;
                            break;
                        default:
                            log.warn("Unrecognized record file delimiter '{}'", typeDelimiter);
                    }
                } catch (Exception e) {
                    log.warn("Problem parsing record signature file {}!", file, e);
                    break;
                }
            }

            return Pair.of(fileHash, sig);
        } catch (FileNotFoundException e) {
            log.warn("File '{}' not found!", file, e);
        } catch (IOException e) {
            log.error("IOException reading '{}'", file, e);
        } catch (Exception e) {
            log.error("Problem reading '{}'", file, e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException ex) {
                log.warn("Problem closing the stream for '{}'", file, ex);
            }
        }

        return null;
    }

    private boolean verifySignature(byte[] data, byte[] signature, String account) {
        PublicKey key = accountKeys.get(account);
        if (key == null) {
            return false;
        }
        try {
            Signature sig = Signature.getInstance("SHA384withRSA", "SunRsaSign");
            sig.initVerify(key);
            sig.update(data);
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidKeyException
                | SignatureException e) {
            log.warn(
                    " Problem verifying signature {}, PublicKey: {}, NodeID: {}, Exception: {}",
                    signature,
                    key,
                    account,
                    e.getStackTrace());
        }
        return false;
    }

    private static String getAccountIDStringFromFilePath(String path) {
        Matcher matcher = NODE_PATTERN.matcher(path);

        String match = null;
        while (matcher.find()) {
            match = matcher.group(1);
        }
        return match;
    }

    private static final Pattern NODE_PATTERN = Pattern.compile("record([\\d]+[.][\\d]+[.][\\d]+)");
}
