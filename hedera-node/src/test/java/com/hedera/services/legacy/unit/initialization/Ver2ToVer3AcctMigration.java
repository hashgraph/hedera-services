package com.hedera.services.legacy.unit.initialization;

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

import com.google.protobuf.TextFormat;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.legacy.config.PropertiesLoader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import static com.hedera.services.legacy.core.jproto.JAccountID.convert;
import static com.hedera.services.legacy.logic.ApplicationConstants.INITIAL_GENESIS_COINS;


/**
 * This class migrates the underlying existing HGCApp data to newer version of datastructures in
 * HGcApp. It updates data in modified datastructures MapValue and deletes legacy directories in
 * FCFS filesystem
 */
public class Ver2ToVer3AcctMigration {
  private static final Logger log = LogManager.getLogger(Ver2ToVer3AcctMigration.class);
  private static final String HEADING =
      "AccountID, Balance, ReceiverThreshold, SenderThreshold, ReceiverSigRequired, AccountKeys"
          + System.getProperty("line.separator");

  private static final String HEADING_UPDATED =
          "AccountID, ExpirationTime"
                  + System.getProperty("line.separator");

  private static final String HEADING_CREATED =
          "AccountID, Balance, ReceiverThreshold, SenderThreshold, ReceiverSigRequired, AccountKeys, ExpirationTime"
                  + System.getProperty("line.separator");

  //The following two files contain MapKey and MapValue fields which should not be updated during migration; Their Hashes should be the same
  public static final String FILE_BEFORE_MIG = "data/onboard/beforeMigration.csv";
  public static final String FILE_AFTER_MIG = "data/onboard/afterMigration.csv";

  //This file contains MapKey and MapValue fields which are updated during migration (e.g., ExpirationTime);
  public static final String FILE_AFTER_MIG_UPDATED = "data/onboard/afterMigration_updated.csv";

  //This file contains MapKey and MapValue of accounts which are created during migration (900-1000);
  public static final String FILE_AFTER_MIG_CREATED = "data/onboard/afterMigration_created.csv";

  private static final int INITIAL_ACCOUNTS = 100;
  private static final int HEDERA_START_ACCOUNT_NUM = 1001;
  private static final long DEFAULT_SHARD = 0;
  private static final long DEFAULT_REALM = 0;
  private static final int CREATED_ACC_START_NUM = 900;
  private static final int CREATED_ACC_END_NUM = 1000;

  /**
   * This method migrates the data from older MapValue version to new MapValue version.
   * It also set 'expirationTime' for each migrated account and update in newer version
   * of MapValue.
   * It creates two files 'beforeMigration.csv' and 'afterMigration.csv' which contains MapKeys and unchanged fields in MapValues before and after migration
   * and compares the hash of data in these files.
   * If hash matches, it proceeds ahead , if not, it exits application with error message
   *
   * It also creates a file 'afterMigration_expTime.csv' which contains MapKeys and expirationTime after migration. This file is for validation
   *
   * Then, it creates accounts id 900-1000. These accounts will have the default keys;
   * balance: 0;
   * threshold:  50_000_000_000__00_000_000 tiny bars
   * receiverSigRequired: false
   *
   * It creates a file 'afterMigration_900-1000.csv' which contains MapKeys and MapValues of accounts 900-1000. This file is for validation.
   *
   * @param map
   * @param selfId
   * @throws Exception
   */

  public synchronized static void migrate(FCMap<MapKey, HederaAccount> map, long selfId, Key defaultKey)
      throws Exception {

    long maxAccountNum = getMaxAccountNum(map);
    log.info("The size of Account Map before Migration>>>>>>>>>>> ::: " + map.size() + "\n"
            + "Maximum AccountNum in Account Map >>>>>>>>>>> ::: " + maxAccountNum  + "\n" +
            "Inside migrate method     ::::::::  Self ID   " + selfId);

    writeLedgerData(map, maxAccountNum, FILE_BEFORE_MIG, null, null);
    for (MapKey currKey : map.keySet()) {
      // if accountNum is equal to one of systemFile num, we don't migrate it
      if (isSystemFileNum(currKey.getAccountNum())) {
        continue;
      }
      HederaAccount currMv = map.get(currKey);
      HederaAccount copyVal = new HederaAccount(currMv);
      copyVal.setExpirationTime(getExpirationTimeForMigratedAccount(currKey.getAccountNum()));
      map.replace(currKey, copyVal);
    }

    log.info("creating accounts 900-1000 >>>>>>>>>>> ::: ");
    for (int i = CREATED_ACC_START_NUM; i <= CREATED_ACC_END_NUM; i++) {
      MapKey mapKey = new MapKey(0, 0, i);
      if (!map.containsKey(mapKey)) {
        HederaAccount mapValue = getMapValueForCreatedAccount(defaultKey);
        map.put(mapKey, mapValue);
      }
    }
    log.info("creating accounts 900-1000 Completed >>>>>>>>>>> ::: " + "\n"
    + "The size of Account Map after Migration>>>>>>>>>>> ::: " + map.size());

    writeLedgerData(map, maxAccountNum, FILE_AFTER_MIG, FILE_AFTER_MIG_UPDATED, FILE_AFTER_MIG_CREATED);

    // get the hash of two files
    String md5HashBefore = getMD5Hash(FILE_BEFORE_MIG);
    String md5HashAfter = getMD5Hash(FILE_AFTER_MIG);

    if (!md5HashBefore.equals(md5HashAfter)) {
      log.error(
              "md5Hash of data before and after is not equal; so exiting the migration process!! Please check");
      if(!PropertiesLoader.getSkipExitOnStartupFailures().equals(ApplicationConstants.YES)){
        System.exit(1);
      }
    }


    log.info("<<<------Migration Completed---->>>");
  }

  /**
   * Get expiration value of migrated or created account
   * accounts below or equal to 1000 - expiration is Long.MAX_VALUE
   * accounts above 1000 :
   *    1001 to expire on Nov 1st 2019 00:01
   *    1002 to expire on Nov 1st 2019 00:02
   *    ...
   * @param accountNum
   * @return
   */
  public static long getExpirationTimeForMigratedAccount(long accountNum) {
    if (accountNum <= CREATED_ACC_END_NUM) {
      return Long.MAX_VALUE;
    } else {
      Instant instant = Instant.parse("2019-11-01T00:00:00Z");
      return instant.plus(java.time.Duration.ofMinutes(accountNum - 1000)).getEpochSecond();
    }
  }

  public static boolean isSystemFileNum(long num) {
    return num == ApplicationConstants.FEE_FILE_ACCOUNT_NUM ||
            num == ApplicationConstants.EXCHANGE_RATE_FILE_ACCOUNT_NUM ||
            num == ApplicationConstants.ADDRESS_FILE_ACCOUNT_NUM ||
            num == ApplicationConstants.NODE_DETAILS_FILE;
  }

  private static HederaAccount getMapValueForCreatedAccount(Key key) throws DecoderException {
    LocalDate date = LocalDate.parse("2018-09-01");

    JKey jKey = JKey.mapKey(key);
    JAccountID proxyId = convert(AccountID.getDefaultInstance());
    HederaAccount hAccount = new HederaAccountCustomizer()
              .fundsSentRecordThreshold(INITIAL_GENESIS_COINS)
              .fundsReceivedRecordThreshold(INITIAL_GENESIS_COINS)
              .isReceiverSigRequired(false)
              .proxy(proxyId)
              .isDeleted(false)
              .expiry(Long.MAX_VALUE)
              .memo("")
              .isSmartContract(false)
              .key(jKey)
              .autoRenewPeriod(date.toEpochDay())
              .customizing(new HederaAccount());

    return hAccount;
  }

  /**
   * method to get single account data in csv format
   *
   * @param mapVal
   * @param accountID
   * @return
   */
  private static String getAccountData(HederaAccount mapVal, long accountID) {
    StringBuilder accountData = new StringBuilder();
    accountData.append(accountID).append(",").append(mapVal.getBalance()).append(",")
            .append(mapVal.getReceiverThreshold()).append(",").append(mapVal.getSenderThreshold())
            .append(",").append(mapVal.isReceiverSigRequired()).append(",").append(mapVal.getAccountKeys())
            .append(System.getProperty("line.separator"));
    return accountData.toString();
  }

  /**
   * method to get updated single account data in csv format
   * To be written to FILE_AFTER_MIG_UPDATED
   * @param mapVal
   * @param accountID
   * @return
   */
  private static String getAccountData_Updated(HederaAccount mapVal, long accountID) {
    StringBuilder accountData = new StringBuilder();
    accountData.append(accountID).append(",").append(mapVal.getExpirationTime())
            .append(System.getProperty("line.separator"));
    return accountData.toString();
  }

  /**
   * method to get created single account data in csv format
   * To be written to FILE_AFTER_MIG_CREATED
   * @param mapVal
   * @param accountID
   * @return
   */
  private static String getAccountData_Created(HederaAccount mapVal, long accountID) throws IOException {
    StringBuilder accountData = new StringBuilder();
    try {
      accountData.append(accountID).append(",").append(mapVal.getBalance())
              .append(",").append(mapVal.getReceiverThreshold())
              .append(",").append(mapVal.getSenderThreshold())
              .append(",").append(mapVal.isReceiverSigRequired())
              .append(",").append(TextFormat.shortDebugString(JKey.mapJKey(mapVal.getAccountKeys())))
              .append(",").append(mapVal.getExpirationTime())
              .append(System.getProperty("line.separator"));
    } catch (Exception ex) {
      throw new IOException("getAccountData_created() :: an Exception happens when calling JKey.mapJKey(): " + ex.getMessage());
    }
    return accountData.toString();
  }

  /**
   * return the maximum account num in accountMap
   * @param map
   * @return
   */
  public static long getMaxAccountNum(FCMap<MapKey, HederaAccount> map) {
    long maxAccountNum = 0;
    for (MapKey mapKey : map.keySet()) {
      if (mapKey.getAccountNum() > maxAccountNum) {
        maxAccountNum = mapKey.getAccountNum();
      }
    }
    return maxAccountNum;
  }

  /**
   * method to write Account data in file before and after migration
   * @param map
   * @param maxAccountNum
   * @param filePath contains accountID and fields in mapValue which should not change after migration
   * @param updatedFilePath contains accountID and fields in mapValue which woudl be changed after migration
   * @param createdFilePath contains information of accounts which are created during migration
   * @throws IOException
   */
  private static void writeLedgerData(
          FCMap<MapKey, HederaAccount> map,
          long maxAccountNum,
          String filePath,
          String updatedFilePath,
          String createdFilePath
  ) throws IOException {
    File file = new File(filePath);
    Files.createDirectories(Paths.get(file.getParent()));
    FileWriter fr = null;
    BufferedWriter br = null;

    FileWriter fr_updated = null;
    BufferedWriter br_updated = null;

    FileWriter fr_created = null;
    BufferedWriter br_created = null;
	int nullVal = 0;

    try {
      fr = new FileWriter(file);
      br = new BufferedWriter(fr);
      br.write(HEADING);

      if (updatedFilePath != null) {
        fr_updated = new FileWriter(updatedFilePath);
        br_updated = new BufferedWriter(fr_updated);
        br_updated.write(HEADING_UPDATED);
      }

      if (createdFilePath != null) {
        fr_created = new FileWriter(createdFilePath);
        br_created = new BufferedWriter(fr_created);
        br_created.write(HEADING_CREATED);
      }

      for (int i = 1; i <= INITIAL_ACCOUNTS; i++) {
        AccountID acctID = AccountID.newBuilder().setAccountNum((i)).setRealmNum(DEFAULT_REALM)
            .setShardNum(DEFAULT_SHARD).build();
        MapKey mapKey = MapKey.getMapKey(acctID);
        HederaAccount mapValue = map.get(mapKey);
        br.write(getAccountData(mapValue, acctID.getAccountNum()));
        //write accountID and updated ExpirationTime to updatedFilePath
        if (updatedFilePath != null) {
          br_updated.write(getAccountData_Updated(mapValue, acctID.getAccountNum()));
        }
      }

      //For createdAccounts
      if (createdFilePath != null) {
        for (int i = CREATED_ACC_START_NUM; i <= CREATED_ACC_END_NUM; i++) {
          AccountID acctID = AccountID.newBuilder().setAccountNum((i)).setRealmNum(DEFAULT_REALM)
                  .setShardNum(DEFAULT_SHARD).build();
          MapKey mapKey = MapKey.getMapKey(acctID);
          HederaAccount mapValue = map.get(mapKey);
          //write information of accounts which are created during migration to createdFilePath
          br_created.write(getAccountData_Created(mapValue, acctID.getAccountNum()));
        }
      }
      
      final Set<MapKey> accountKeys = map.keySet().stream().filter(
          v -> v.getAccountNum() >= HEDERA_START_ACCOUNT_NUM && v.getAccountNum() <= PropertiesLoader.getConfigAccountNum()).collect(
          Collectors.toSet());
      
      for (MapKey mapKey : accountKeys) {
              HederaAccount mapValue = map.get(mapKey);
              if (mapValue != null) {
                  if (mapValue.getAccountKeys().hasContractID()) {
                      continue;
                  }
                  br.write(getAccountData(mapValue, mapKey.getAccountNum()));
                  //write accountID and updated ExpirationTime to updatedFilePath
                  if (updatedFilePath != null) {
                      br_updated.write(getAccountData_Updated(mapValue, mapKey.getAccountNum()));
                  }
              } else {
                  log.info("MapValue is null!!! MapKey: " + mapKey.toString());
              }
          }      
      log.info("There are " + nullVal + " MapKey not contained in accountMap");
      System.out.println("file: " + file.getAbsolutePath());
    } catch (IOException e) {
      throw new IOException("Ver2ToVer3AcctMigration :: writeLedgerData : IOException happens while writing LedgerData to " + filePath);
    } finally {
      br.close();
      fr.close();
      if (updatedFilePath != null) {
        br_updated.close();
        fr_updated.close();
      }
      if (createdFilePath != null) {
        br_created.close();
        fr_created.close();
      }
    }
  }

  /**
   * method to calculate the md5 hash of file data before and after migration
   * 
   * @param filePath
   * @return
   */
  private static String getMD5Hash(String filePath) {
    String md5 = null;
    FileInputStream fileInputStream = null;
    try {
      File file = new File(filePath);
      fileInputStream = new FileInputStream(file);
      md5 = DigestUtils.md5Hex(IOUtils.toByteArray(fileInputStream));
      fileInputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return md5;
  }
}
