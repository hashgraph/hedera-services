package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Utility provides the price of gas in tinybars for contract calls and local calls.
 *
 * @author Peter
 */
public class FeeUtility {

  public static long FEE_FILE_ACCOUNT_NUM = 111;
  public static long EXCHANGE_RATE_FILE_ACCOUNT_NUM = 112;


  private ManagedChannel channel = null;
  private FileServiceBlockingStub fStub = null;
  private CurrentAndNextFeeSchedule currentNextFeeSch = null;
  private ExchangeRateSet exchRateSet = null;
  private AccountID nodeAccount;
  private long node_account_number;
  private long node_shard_number;
  private long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private AccountID genesisAccount;
  private PrivateKey genesisPrivateKey = null;
  private KeyPair genKeyPair = null;
  private Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();
  private static String host;
  private static int port;

  private long callPriceInTinybars = -7;
  private long createPriceInTinybars = -7;

  public static void main(String args[]) throws Exception {
    FeeUtility f = getInstance();
  }

  public static FeeUtility getInstance() throws Exception {
    FeeUtility fUtil = new FeeUtility();
    fUtil.readSchedules();
    return fUtil;
  }

  private FeeUtility() {
    ;
  }

  private void readSchedules() throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    host = properties.getProperty("host");
    port = Integer.parseInt(properties.getProperty("port"));
    node_account_number = Utilities.getDefaultNodeAccount();
    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();

    FileID feeFileId = FileID.newBuilder().setFileNum(FEE_FILE_ACCOUNT_NUM)
        .setRealmNum(0L).setShardNum(0L).build();
    FileID exchRateFileId = FileID.newBuilder().setFileNum(EXCHANGE_RATE_FILE_ACCOUNT_NUM)
        .setRealmNum(0L).setShardNum(0L).build();

    loadGenesisAndNodeAcccounts();  // Use the genesisAccount

    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    fStub = FileServiceGrpc.newBlockingStub(channel);

    // Get the cost of gas in thousands of tinycents per unit
    Response response = TestHelper.getFileContent(fStub, feeFileId, genesisAccount,
        genKeyPair, nodeAccount);
    byte[] feeScheduleBytes = response.getFileGetContents().getFileContents().getContents()
        .toByteArray();
    currentNextFeeSch = CurrentAndNextFeeSchedule.parseFrom(feeScheduleBytes);

    long gasPriceKiloTinyCentsCreate = 0;
    long gasPriceKiloTinyCentsCall = 0;
    for (TransactionFeeSchedule tFS : currentNextFeeSch.getCurrentFeeSchedule()
        .getTransactionFeeScheduleList()) {
      if (tFS.getHederaFunctionality() == HederaFunctionality.ContractCall) {
        gasPriceKiloTinyCentsCall = tFS.getFeeData().getServicedata().getGas();
        System.out.println("Raw Contract Call gas: " + gasPriceKiloTinyCentsCall);
      } else if (tFS.getHederaFunctionality() == HederaFunctionality.ContractCreate) {
        gasPriceKiloTinyCentsCreate = tFS.getFeeData().getServicedata().getGas();
        System.out.println("Raw Contract Create gas: " + gasPriceKiloTinyCentsCreate);
      }
    }

    // Get the exchange rate
    response = TestHelper.getFileContent(fStub, exchRateFileId, genesisAccount,
        genKeyPair, nodeAccount);
    byte[] exchRateScheduleBytes = response.getFileGetContents().getFileContents().getContents()
        .toByteArray();
    exchRateSet = ExchangeRateSet.parseFrom(exchRateScheduleBytes);
    System.out.println("Exchange rate: " + exchRateSet.getCurrentRate());

    callPriceInTinybars = Math.max(1L, FeeBuilder.getTinybarsFromTinyCents(
        exchRateSet.getCurrentRate(), gasPriceKiloTinyCentsCall / 1000));
    createPriceInTinybars = Math.max(1L, FeeBuilder.getTinybarsFromTinyCents(
        exchRateSet.getCurrentRate(), gasPriceKiloTinyCentsCreate / 1000));
    System.out.println("Call price of 1 unit of gas in tinybars: " + callPriceInTinybars);
    System.out.println("Create price of 1 unit of gas in tinybars: " + createPriceInTinybars);
  }

  public long getCallPriceInTinybars() {
    return callPriceInTinybars;
  }

  public long getCreatePriceInTinybars() {
    return createPriceInTinybars;
  }

  private void loadGenesisAndNodeAcccounts() throws Exception {
    Map<String, List<AccountKeyListObj>> hederaAccounts = null;
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

    // Get Genesis Account key Pair
    List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");
    ;

    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
    genesisPrivateKey = genKeyPairObj.getPrivateKey();
    genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    // get the Account Object
    genesisAccount = genesisAccountList.get(0).getAccountId();
    List<PrivateKey> genesisKeyList = new ArrayList<PrivateKey>(1);
    genesisKeyList.add(genesisPrivateKey);
    accountKeys.put(genesisAccount, genesisKeyList);

  }
}
