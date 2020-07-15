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
import com.hedera.test.utils.IdUtils;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.exception.InvalidTotalAccountBalanceException;
import com.swirlds.common.Platform;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.Assert;
import org.junit.Test;
import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccountBalanceExportTest {

  private static String nodeAccountIDString = "0.0.3";

  private static Instant consensusTimestamp = Instant.now();

  // three accounts for testing: shardNum,realmNum,accountNum,balance
  private static long[][] accounts =
      {{0, 0, 2, 4999999999999969300L}, {0, 0, 4, 30000}, {0, 0, 3, 200}, {0, 0, 5, 500}};

  private static HashMap<String, Long> nodeAccounts = new HashMap<>();
  static {
    nodeAccounts.put("0.0.3", 0l);
    nodeAccounts.put("0.0.4", 1l);
    nodeAccounts.put("0.0.5", 2l);
  }

  private static AccountBalanceExport accountBalanceExport =
      new AccountBalanceExport(2, nodeAccounts, 1000);

  @Test
  public void timeToExportTest() {
    Instant timestamp_0 = Instant.parse("2019-06-12T14:00:00.0Z");
    Assert.assertFalse(accountBalanceExport.timeToExport(timestamp_0));

    Instant timestamp_1 = Instant.parse("2019-06-12T14:01:00.0Z");
    Assert.assertFalse(accountBalanceExport.timeToExport(timestamp_1));

    Instant timestamp_2 = Instant.parse("2019-06-12T14:02:00.0Z");
    Assert.assertTrue(accountBalanceExport.timeToExport(timestamp_2));

    Instant timestamp_3 = Instant.parse("2019-06-12T14:03:00.0Z");
    Assert.assertFalse(accountBalanceExport.timeToExport(timestamp_3));

    Instant timestamp_4 = Instant.parse("2019-06-12T14:04:00.0Z");
    Assert.assertTrue(accountBalanceExport.timeToExport(timestamp_4));

    Instant timestamp_6 = Instant.parse("2019-06-12T14:06:50.0Z");
    Assert.assertTrue(accountBalanceExport.timeToExport(timestamp_6));
  }

  FCMap<MerkleEntityId, MerkleAccount> getAccountMapForTest() throws Exception {
    FCMap<MerkleEntityId, MerkleAccount> fcMap =
            new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
    for (long[] account : accounts) {
      MerkleEntityId mk = new MerkleEntityId();
      mk.setShard(account[0]);
      mk.setRealm(account[1]);
      mk.setNum(account[2]);

      MerkleAccount mv = new MerkleAccount();
      mv.setBalance(account[3]);
      fcMap.put(mk, mv);
    }
    return fcMap;
  }

  ServicesState getMockState() throws Exception {
    ServicesState state = mock(ServicesState.class);
    when(state.accounts()).thenReturn(getAccountMapForTest());
    when(state.getNodeAccountId()).thenReturn(IdUtils.asAccount(nodeAccountIDString));
    return state;
  }

  String exportBalanceFile() throws Exception {
    ServicesState mockState = getMockState();
    return accountBalanceExport.exportAccountsBalanceCSVFormat(mockState, consensusTimestamp);
  }

  /**
   * Export a accountBalance file, check its content, and delete it
   * 
   * @throws Exception
   */
  @Test
  public void exportAccountsBalanceCSVFormat_Test() throws Exception {
    String exportedFilename = exportBalanceFile();
    Assert.assertTrue(exportedFilename != null);
    File exportedFile = new File(exportedFilename);
    Assert.assertTrue(exportedFile.exists());

    try (BufferedReader br = new BufferedReader(new FileReader(exportedFilename))) {
      // the first line should end with consensusTimestamp
      Assert.assertTrue(br.readLine().endsWith(consensusTimestamp.toString()));
      Assert.assertTrue(br.readLine().equals("shardNum,realmNum,accountNum,balance"));
      Assert.assertTrue(br.readLine().equals("0,0,2,4999999999999969300"));
      Assert.assertTrue(br.readLine().equals("0,0,3,200"));
      Assert.assertTrue(br.readLine().equals("0,0,4,30000"));
      Assert.assertTrue(br.readLine().equals("0,0,5,500"));
      Assert.assertNull(br.readLine());
    }
    // Delete the file
    exportedFile.delete();
  }

  @Test
  public void signAccountBalanceFileTest() throws Exception {
    String exportedFilename = exportBalanceFile();
    byte[] hash = AccountBalanceExport.getFileHash(exportedFilename);
    Platform mockPlatform = mock(Platform.class);
    byte[] mockSig = "TestSig".getBytes();
    when(mockPlatform.sign(hash)).thenReturn(mockSig);
    accountBalanceExport.signAccountBalanceFile(mockPlatform, exportedFilename);
    String sigFilename = exportedFilename + "_sig";
    File sigFile = new File(sigFilename);
    Assert.assertTrue(sigFile.exists());

    try (DataInputStream dis = new DataInputStream(new FileInputStream(sigFile))) {
      byte[] readedHash = new byte[48];

      // TYPE_FILE_HASH
      Assert.assertEquals(4, dis.readByte());
      // check hash
      dis.read(readedHash);
      Assert.assertTrue(Arrays.equals(hash, readedHash));

      // TYPE_SIGNATURE
      Assert.assertEquals(3, dis.readByte());
      // check signature length
      int readedSigLength = dis.readInt();
      Assert.assertEquals(mockSig.length, readedSigLength);
      // check signature content
      byte[] readedSig = new byte[readedSigLength];
      dis.read(readedSig);
      Assert.assertTrue(Arrays.equals(mockSig, readedSig));
    }
    // Delete the files
    new File(exportedFilename).delete();
    sigFile.delete();
  }

  /**
   * Export a accountBalance file, check if the log contains Insufficient Node Balance Error, and
   * then delete it Because we set nodeAccountBalanceValidity to be 1000 for this test, node0 and
   * node2's balance fall bellow the threshold, so the log must be "Insufficient Node Balance Error
   * - Node0 (0.0.3) balance: 200\nInsufficient Node Balance Error - Node2 (0.0.5) balance: 500\n"
   * 
   * @throws Exception
   */
  @Test
  public void checkNodeBalanceLog_Test() throws Exception {
    Logger logger =
        (org.apache.logging.log4j.core.Logger) LogManager.getLogger(AccountBalanceExport.class);
    CharArrayWriter outContent = new CharArrayWriter();
    Appender appender = WriterAppender.newBuilder().setTarget(outContent)
        .setLayout(PatternLayout.createDefaultLayout()).setName("TestAppender").build();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.WARN);

    String exportedFilename = exportBalanceFile();
    Assert.assertTrue(exportedFilename != null);
    File exportedFile = new File(exportedFilename);
    Assert.assertTrue(exportedFile.exists());
    String out = outContent.toString();
    Assert.assertTrue(
            out.contains(
                    "Node 0 (0.0.3) has insufficient balance 200!")
                    && out.contains("Node 2 (0.0.5) has insufficient balance 500!"));


    // Delete the file
    exportedFile.delete();
    // Remove Appender
    logger.removeAppender(appender);
  }

  ServicesState getMockStateWithInvalidTotalBalance() throws Exception {
    ServicesState state = mock(ServicesState.class);
    when(state.accounts()).thenReturn(getAccountMapWithInvalidTotalBalanceForTest());
    when(state.getNodeAccountId()).thenReturn(IdUtils.asAccount(nodeAccountIDString));
    return state;
  }

  FCMap<MerkleEntityId, MerkleAccount> getAccountMapWithInvalidTotalBalanceForTest() throws Exception {
    FCMap<MerkleEntityId, MerkleAccount> fcMap =
            new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
    for (long[] account : accounts) {
      MerkleEntityId mk = new MerkleEntityId();
      mk.setShard(account[0]);
      mk.setRealm(account[1]);
      mk.setNum(account[2]);

      MerkleAccount mv = new MerkleAccount();
      mv.setBalance(account[3] + 1);
      fcMap.put(mk, mv);
    }
    return fcMap;
  }

  /**
   * While trying to export account balances detect tat total account balance is different from
   * expected total money supply and throw exception
   * 
   * @throws Exception
   */
  @Test
  public void exportAccountsBalanceIncorrectTotalBalanceTest() throws Exception {
    ServicesState mockState;
    mockState = getMockStateWithInvalidTotalBalance();

    String exportedFilename = null;
    boolean exceptionCaught = false;
    try {
      exportedFilename =
          accountBalanceExport.exportAccountsBalanceCSVFormat(mockState, consensusTimestamp);
    } catch (InvalidTotalAccountBalanceException e) {
      exceptionCaught = true;

    }

    if (exportedFilename != null) {
      File exportedFile = new File(exportedFilename);
      if (exportedFile.exists()) {
        // Delete the file
        exportedFile.delete();
      }
    }
    Assert.assertTrue(exceptionCaught);
  }
}
