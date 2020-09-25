package com.hedera.services.legacy.export;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ServicesState;
import com.hedera.services.state.exports.AccountBalance;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.stream.RecordStream;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Platform;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.utils.EntityIdUtils.readableId;

public class AccountBalanceExport {
  
  static final Logger log = LogManager.getLogger(AccountBalanceExport.class);
  static final String lineSperator = "line.separator";

  private Instant previousTimestamp = null;

  private long exportPeriodSec;

  private String accountBalanceExportDir;

  private HashMap<String, Long> nodeAccounts;

  private Long nodeAccountBalanceValidity;
  private long initialGenesisCoins;

  public AccountBalanceExport(AddressBook addressBook) {
    exportPeriodSec = PropertiesLoader.accountBalanceExportPeriodMinutes() * 60;
    accountBalanceExportDir = PropertiesLoader.getAccountBalanceExportDir();
    nodeAccountBalanceValidity = PropertiesLoader.getNodeAccountBalanceValidity();
    initialGenesisCoins = PropertiesLoader.getInitialGenesisCoins();

    nodeAccounts = new HashMap<>();
    for (long i = 0; i < addressBook.getSize(); i++) {
      Address address = addressBook.getAddress(i);
      //memo contains the node accountID string
      nodeAccounts.put(address.getMemo(), i);
    }
  }

  /**
   * Is used in AccountBalanceExportTest
   * @param accountBalanceExportPeriodMinutes
   * @param nodeAccounts
   * @param nodeAccountBalanceValidity
   */
  AccountBalanceExport(long accountBalanceExportPeriodMinutes, HashMap<String, Long> nodeAccounts, long nodeAccountBalanceValidity) {
    exportPeriodSec = accountBalanceExportPeriodMinutes * 60;
    accountBalanceExportDir = PropertiesLoader.getAccountBalanceExportDir();
    this.nodeAccounts = nodeAccounts;
    this.nodeAccountBalanceValidity = nodeAccountBalanceValidity;
    initialGenesisCoins = PropertiesLoader.getInitialGenesisCoins();
  }

  /**
   * This method is called in HGCAppMain.newSignedState().
   * Return true when previousTimestamp is not null, and previousTimestamp and consensusTimestamp are in different exportPeriod, for example:
   *   If ACCOUNT_BALANCE_EXPORT_PERIOD_MINUTES is set to be 10, and
   *   previousTimestamp is yyyy-MM-ddT12:01:00.0Z,
   *   (1) when consensusTimestamp is yyyy-MM-ddT12:09:00.0Z, return false;
   *   (2) when consensusTimestamp is yyyy-MM-ddT12:10:00.0Z, return true;
   * @param consensusTimestamp
   * @return
   */
  public boolean timeToExport(Instant consensusTimestamp) {
    if (previousTimestamp != null && consensusTimestamp.getEpochSecond() / exportPeriodSec != previousTimestamp.getEpochSecond() / exportPeriodSec) {
      previousTimestamp = consensusTimestamp;
      return true;
    }
    previousTimestamp = consensusTimestamp;
    return false;
  }

  /**
   * This method is invoked during start up and executed based upon the configuration settings. It exports all the existing accounts balance and write it in a file
   */
  public String exportAccountsBalanceCSVFormat(
          ServicesState servicesState,
          Instant consensusTimestamp
  ) {
    // get the export path from Properties
    log.debug("exportAccountsBalanceCSVFormat called. {}", consensusTimestamp);
    FCMap<MerkleEntityId, MerkleAccount> accountMap = servicesState.accounts();
    String nodeAccountID = readableId(servicesState.getNodeAccountId());

    if (!accountBalanceExportDir.endsWith(File.separator)) {
      accountBalanceExportDir += File.separator;
    }

    String dir = accountBalanceExportDir + "balance" + nodeAccountID + File.separator;
    try {
      Files.createDirectories(Paths.get(dir));
    } catch (IOException e) {
      log.error("{} doesn't exist and cannot be created", dir);
      throw new IllegalStateException(e);
    }
    String fileName =  dir + consensusTimestamp + "_Balances.csv";
    fileName = fileName.replace(":", "_");
    List<AccountBalance> acctObjList = new ArrayList<>();
    AccountBalance exAccObj;
    if(log.isDebugEnabled()){
      log.debug("Size of accountMap :: {}", accountMap.size());
    }
    long totalBalance = 0L;

    for (Map.Entry<MerkleEntityId, MerkleAccount> item : accountMap.entrySet()) {
      MerkleEntityId currKey = item.getKey();
      MerkleAccount currMv = item.getValue();
      totalBalance += currMv.getBalance();
      exAccObj = new AccountBalance(currKey.getShard(), currKey.getRealm(), currKey.getNum(), currMv.getBalance());
      acctObjList.add(exAccObj);
      //check if the account is a node account
      long nodeId = nodeAccounts.getOrDefault(currKey.toAbbrevString(), -1l);
      if (nodeId != -1l) {
        //check if its balance is less than nodeAccountBalanceValidity
        long balance = currMv.getBalance();
        if (balance < nodeAccountBalanceValidity) {
          log.warn("Node {} ({}) has insufficient balance {}!", nodeId, currKey.toAbbrevString(), balance);
        }
      }
    }
    //validate that total node balance is equal to initial money supply
    if(totalBalance != initialGenesisCoins) {
      String  errorMessage = "Total balance " + totalBalance + " is different from " + initialGenesisCoins;
      throw new IllegalStateException(errorMessage);
    }
    Collections.sort(acctObjList);
    try (FileWriter file = new FileWriter(fileName)) {
      file.write("TimeStamp:");
      file.write(consensusTimestamp.toString());
      file.write(System.getProperty(lineSperator));
      file.write("shardNum,realmNum,accountNum,balance");
      file.write(System.getProperty(lineSperator));
      for (AccountBalance exAcctObj : acctObjList) {
        file.write(getAccountData(exAcctObj));
      }
      if(log.isDebugEnabled()){
        log.debug("periodic export of account data completed :: {}", fileName);
      }
    } catch (IOException e) {
      log.error("Exception occurred while Exporting Accounts to File.. continuing without saving!! {}", e.getMessage());
      fileName = null;
    }
    return fileName;
  }

  /**
   * method to get single account data in csv format
   * @param exportAcctObj
   * @return
   */
  private static String getAccountData(AccountBalance exportAcctObj) {
    StringBuilder accountData = new StringBuilder();
    accountData.append(exportAcctObj.getShard()).append(",").append(exportAcctObj.getRealm()).append(",")
        .append(exportAcctObj.getNum()).append(",").append(exportAcctObj.getBalance())
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
        log.error("Exception ", e);
      }
      byte[] fileHash = md.digest(array);
      return fileHash;

    } catch (NoSuchAlgorithmException e) {
      log.error("Exception ", e);
      return null;
    }
  }

  public void signAccountBalanceFile(Platform platform, String balanceFileName) {
    byte[] fileHash = getFileHash(balanceFileName);
    //log.info("fileHash of {}: {}", balanceFileName, fileHash);
    byte[] signature = platform.sign(fileHash);
    //log.info("signature of {}: {}", balanceFileName, signature);

    String sigFileName = RecordStream.generateSigFile(balanceFileName, signature, fileHash);
    if (sigFileName != null) {
      if(log.isDebugEnabled()){
        log.debug("Generated signature file for {}", balanceFileName);
      }
    }
  }
}
