package com.hedera.services.legacy.file;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hedera.services.legacy.core.CommonUtils;
import org.junit.Ignore;

@Ignore
public class LoadTestIT extends FileServiceIT {

  public static void main(String[] args) throws Exception {
//		accountCreatLoadTest(100);
//		accountCreatTransferLoadTest(100);
    transferLoadTest(10);
  }

  /**
   * Creates a large number of accounts
   *
   * @param maxRound number of rounds to create accounts, each round creates two accounts
   */
  public static void accountCreatLoadTest(int maxRound) throws Exception {
    LoadTestIT tester = new LoadTestIT();
    tester.setUp();

    // load test account creation
    for (int i = 0; i < maxRound; i++) {
      tester.test01InitAccounts();
      System.out.println(
          "\n@@@@ account creation round #" + i + ": accounts = " + payerSeq + ", " + recvSeq);
    }
  }

  public static void accountCreatTransferLoadTest(int round) throws Exception {
    LoadTestIT tester = new LoadTestIT();
    tester.setUp();

    // load test account creation
    for (int i = 0; i < round; i++) {
      tester.test01InitAccounts();

      String msg = getSenderRecverBalances(senderId, recvId, cstub);
      tester.test01Transfer();
      CommonUtils.nap(WAIT_IN_SEC);
      String msgPost = getSenderRecverBalances(senderId, recvId, cstub);
      System.out.println("\n@@@@ account creation and transfer round #" + i + "\n"
          + "Pre-transfer balance: " + msg + "\nPost-transfer balance: " + msgPost);
    }
  }

  private static String getSenderRecverBalances(AccountID senderId, AccountID recvId,
      CryptoServiceBlockingStub cstub) throws Exception {
    long fromBal = getAccountBalance(cstub, senderId);
    return "sender: " + senderId.getAccountNum() + ", " + fromBal;
  }

  /**
   * Make a large number of transfers between same two accounts.
   *
   * @param maxRound number of transfers to make
   */
  public static void transferLoadTest(int maxRound) throws Exception {
    LoadTestIT tester = new LoadTestIT();
    tester.setUp();
    tester.test01InitAccounts();

    // load test account creation
    for (int i = 0; i < maxRound; i++) {
      String msg = getSenderRecverBalances(senderId, recvId, cstub);
      tester.test01Transfer();
      CommonUtils.nap(WAIT_IN_SEC);
      String msgPost = getSenderRecverBalances(senderId, recvId, cstub);
      System.out.println("\n@@@@ transfer round #" + i + "\n"
          + "Pre-transfer balance: " + msg + "\nPost-transfer balance: " + msgPost);
    }
  }
}
