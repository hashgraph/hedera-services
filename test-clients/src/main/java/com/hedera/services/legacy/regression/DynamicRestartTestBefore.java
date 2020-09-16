package com.hedera.services.legacy.regression;

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

import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.legacy.core.TestHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Adds freeze related functions for regression tests.
 * Perform Before Restart Action for creating State of Crypto , File and Smartcontracts refer DynamicRestartTest and DynamicRestartAfter
 * @author Tirupathi Mandala Created on 2019-4-17
 */
public class DynamicRestartTestBefore extends DynamicRestartTest {

  private static final Logger log = LogManager.getLogger(DynamicRestartTestBefore.class);

  public DynamicRestartTestBefore() {

  }



  public static void main(String[] args) throws Throwable {
    if (args.length > 0) {
      host = args[0];
    }
    DynamicRestartTestBefore test = new DynamicRestartTestBefore();
    test.setUp();
    //Before Freeze saving Crypto Account objects
    for(int i=1;i<objectCount;i++) {
      AccountID accountID = createAccount(genesisAccountID, nodeID, 1000000 + ( i * 1000), TestHelper.getCryptoMaxFee(), true);
      Response getInfoResponse = getAccountInfo(accountID);
      log.info(getInfoResponse.getCryptoGetInfo().getAccountInfo());
      if (accountID != null && getInfoResponse.getCryptoGetInfo().getAccountInfo() != null) {
        accountInfoMap.put(accountID, getInfoResponse.getCryptoGetInfo().getAccountInfo());
      }
    }
    writeToFile(CRYPTO_ACCOUNT_MAP_FILE, accountInfoMap);

    //Before Freeze saving File objects
    for(int i=1;i<objectCount;i++) {
      FileGetInfoResponse.FileInfo fileInfo = createFileAndGetInfo(genesisAccountID, nodeID, 4*i, i +  fileDuration);
      log.info("FileInfo: " + fileInfo);
      if(fileInfo!=null && fileInfo.getFileID()!=null) {
        fileInfoMap.put(fileInfo.getFileID(), fileInfo);
      }
    }
    writeToFile(FILE_MAP_FILE, fileInfoMap);

    for(int i=1;i<objectCount;i++) {
      Response createContractResponse = createContract(i + contractDuration, 0L);
      ContractGetInfoResponse.ContractInfo contractInfo = createContractResponse.getContractGetInfo().getContractInfo();
      log.info("Contract Info: "+contractInfo);
      contractInfoMap.put(contractInfo.getContractID(), contractInfo);
    }
    writeToFile(SMART_CONTRACT_MAP_FILE, contractInfoMap);
    log.info("Before Dynamic Restart Test done....");
// Freeze Services (For Now Freeze is controlled shell script )
//    ResponseCodeEnum freezeResponse = freeze(5,1,1,1);
//    Assert.assertEquals(freezeResponse, ResponseCodeEnum.SUCCESS);
//    log.info("FREEZE Started.....");
  }

}
