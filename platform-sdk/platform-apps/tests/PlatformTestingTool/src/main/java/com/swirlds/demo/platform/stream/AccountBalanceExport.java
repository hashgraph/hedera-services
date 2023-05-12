/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.stream;

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.demo.merkle.map.MapValueData;
import com.swirlds.demo.platform.PlatformTestingToolState;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.map.test.pta.MapKey;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountBalanceExport {

    static final byte TYPE_SIGNATURE = 3; // the file content signature, should not be hashed
    static final byte TYPE_FILE_HASH = 4; // next 48 bytes are hash384 of content of the file to be signed

    private static final Logger logger = LogManager.getLogger(AccountBalanceExport.class);
    private static final String lineSperator = "line.separator";

    private final long exportPeriodSec;
    private final HashMap<String, Long> nodeAccounts;

    private Instant previousTimestamp = null;

    public AccountBalanceExport(final AddressBook addressBook, final long exportPeriodSec) {
        this.exportPeriodSec = exportPeriodSec;

        this.nodeAccounts = new HashMap<>();
        for (long i = 0; i < addressBook.getSize(); i++) {
            Address address = addressBook.getAddress(i);
            // memo contains the node accountID string
            this.nodeAccounts.put(address.getMemo(), i);
        }
    }

    /**
     * This method is called in HGCAppMain.newSignedState().
     * Return true when previousTimestamp is not null, and previousTimestamp and consensusTimestamp are in different
     * exportPeriod, for example:
     * If ACCOUNT_BALANCE_EXPORT_PERIOD_MINUTES is set to be 10, and
     * previousTimestamp is yyyy-MM-ddT12:01:00.0Z,
     * (1) when consensusTimestamp is yyyy-MM-ddT12:09:00.0Z, return false;
     * (2) when consensusTimestamp is yyyy-MM-ddT12:10:00.0Z, return true;
     *
     * @param consensusTimestamp
     * @return
     */
    public boolean timeToExport(final Instant consensusTimestamp) {
        if (previousTimestamp != null
                && consensusTimestamp.getEpochSecond() / exportPeriodSec
                        != previousTimestamp.getEpochSecond() / exportPeriodSec) {
            previousTimestamp = consensusTimestamp;
            return true;
        }
        previousTimestamp = consensusTimestamp;
        return false;
    }

    /**
     * This method is invoked during start up and executed based upon the configuration settings. It exports all the
     * existing accounts balance and write it in a file
     */
    public String exportAccountsBalanceCSVFormat(
            final PlatformTestingToolState state, final Instant consensusTimestamp, final Path exportDir) {
        // get the export path from Properties
        logger.debug("exportAccountsBalanceCSVFormat called. {}", consensusTimestamp);
        final MerkleMap<MapKey, MapValueData> accountMap = state.getStateMap().getMap();

        try {
            Files.createDirectories(exportDir);
        } catch (IOException ex) {
            logger.error("{} doesn't exist and cannot be created", exportDir);
            throw new IllegalStateException(ex);
        }

        String fileName = consensusTimestamp + "_Balances.csv";
        fileName = fileName.replace(":", "_");

        final Path exportFile = exportDir.resolve(fileName);
        final List<ExportAccountObject> acctObjList = new ArrayList<>();

        if (logger.isDebugEnabled()) {
            logger.debug("Size of accountMap :: {}", accountMap.size());
        }

        accountMap.forEachNode((final MerkleNode node) -> {
            if (node != null && node.getClassId() == MapValueData.CLASS_ID) {

                final MapValueData currMv = node.cast();
                final MapKey currKey = currMv.getKey();

                final ExportAccountObject exAccObj = new ExportAccountObject(
                        currKey.getShardId(), currKey.getRealmId(), currKey.getAccountId(), currMv.getBalance());

                acctObjList.add(exAccObj);
            }
        });

        Collections.sort(acctObjList, new Comparator<ExportAccountObject>() {
            @Override
            public int compare(ExportAccountObject o1, ExportAccountObject o2) {
                return (int) (o1.getAccountNum() - o2.getAccountNum());
            }
        });

        try (FileWriter file = new FileWriter(exportFile.toFile())) {
            file.write("TimeStamp:");
            file.write(consensusTimestamp.toString());
            file.write(System.getProperty(lineSperator));
            file.write("shardNum,realmNum,accountNum,balance");
            file.write(System.getProperty(lineSperator));

            for (ExportAccountObject exAcctObj : acctObjList) {
                file.write(getAccountData(exAcctObj));
            }

            if (logger.isDebugEnabled()) {
                logger.debug("periodic export of account data completed :: {}", fileName);
            }
        } catch (IOException e) {
            logger.error(
                    "Exception occurred while Exporting Accounts to File.. continuing without saving!! {}",
                    e.getMessage());
        }

        return exportFile.toAbsolutePath().toString();
    }

    /**
     * method to get single account data in csv format
     *
     * @param exportAcctObj
     * @return
     */
    private static String getAccountData(ExportAccountObject exportAcctObj) {
        StringBuilder accountData = new StringBuilder();
        accountData
                .append(exportAcctObj.getShardNum())
                .append(",")
                .append(exportAcctObj.getRealmNum())
                .append(",")
                .append(exportAcctObj.getAccountNum())
                .append(",")
                .append(exportAcctObj.getBalance())
                .append(System.getProperty(lineSperator));
        return accountData.toString();
    }

    /**
     * Calculate SHA384 hash of a binary file
     *
     * @param fileName
     * 		file name
     * @return byte array of hash value
     */
    public static byte[] getFileHash(String fileName) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-384");

            byte[] array = new byte[0];
            try {
                array = Files.readAllBytes(Paths.get(fileName));
            } catch (IOException e) {
                logger.error("Exception ", e);
            }
            byte[] fileHash = md.digest(array);
            return fileHash;

        } catch (NoSuchAlgorithmException e) {
            logger.error("Exception ", e);
            return null;
        }
    }

    public void signAccountBalanceFile(final Platform platform, final String balanceFileName) {
        byte[] fileHash = getFileHash(balanceFileName);
        Signature signature = platform.sign(fileHash);

        String sigFileName = generateSigFile(balanceFileName, signature.getSignatureBytes(), fileHash);
        if (sigFileName != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Generated signature file for {}", balanceFileName);
            }
        }
    }

    /**
     * Create a signature file for a RecordStream/AccountBalance file;
     * This signature file contains the Hash of the file to be signed, and a signature signed by the node's Key
     *
     * @param fileName
     * @param signature
     * @param fileHash
     */
    public static String generateSigFile(String fileName, byte[] signature, byte[] fileHash) {
        try {
            String newFileName = fileName + "_sig";

            // append signature
            try (FileOutputStream output = new FileOutputStream(newFileName, false)) {
                output.write(TYPE_FILE_HASH);
                output.write(fileHash);
                output.write(TYPE_SIGNATURE);
                output.write(ByteBuffer.allocate(4).putInt(signature.length).array());
                output.write(signature);
                return newFileName;
            }
        } catch (IOException e) {
            logger.error(
                    "generateSigFile :: Fail to generate signature file for {}. Exception: {}",
                    fileName,
                    ExceptionUtils.getStackTrace(e));
            return null;
        }
    }
}
