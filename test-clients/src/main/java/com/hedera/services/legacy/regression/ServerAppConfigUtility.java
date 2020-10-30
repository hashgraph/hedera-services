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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
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
 * Expose server app config from file 121 to clients. Add fields as needed by clients.
 *
 * @author Peter
 */
public class ServerAppConfigUtility {

  public static final  long APPLICATION_PROPERTIES_FILE_NUM = 121;
  public static final long API_PROPERTIES_FILE_NUM = 122;
  private static final long DUMMY_LONG = 1L;
  private static final int DUMMY_INT = 1;

  private ManagedChannel channel = null;
  private FileServiceBlockingStub fStub = null;
  private CurrentAndNextFeeSchedule currentNextFeeSch = null;
  private ExchangeRateSet exchRateSet = null;
  private AccountID nodeAccount;
  private long node_shard_number;
  private long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private AccountID genesisAccount;
  private PrivateKey genesisPrivateKey = null;
  private KeyPair genKeyPair = null;
  private Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();
  private static int port;

  private Properties serverAppProperties;
  private long maxGasLimit;
  private int maxFileSize;
  private int maxContractStateSize;

  public static void main(String args[]) throws Exception {
    ServerAppConfigUtility f = getInstance("localhost", 3L);
    System.out.println("Max gas limit is " + f.getMaxGasLimit());
    System.out.println("Max file size is " + f.getMaxFileSize());
    System.out.println("Max contract state size is " + f.getMaxContractStateSize());
  }

  public static ServerAppConfigUtility getInstance(String hostName, long nodeAccountNumber)
      throws Exception {
    ServerAppConfigUtility sACUtil = new ServerAppConfigUtility();
    sACUtil.readProperties(hostName, nodeAccountNumber);
    return sACUtil;
  }

  private ServerAppConfigUtility() {
    ;
  }

  private void readProperties(String hostName, long nodeAccountNumber) throws  Exception {
    Properties properties = TestHelper.getApplicationProperties();
    port = Integer.parseInt(properties.getProperty("port"));
    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
    nodeAccount = AccountID.newBuilder().setAccountNum(nodeAccountNumber)
        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();

    loadGenesisAndNodeAcccounts();  // Use the genesisAccount

    channel = ManagedChannelBuilder.forAddress(hostName, port).usePlaintext().build();
    fStub = FileServiceGrpc.newBlockingStub(channel);
    Response response;

    FileID applicationPropertiesFileId = FileID.newBuilder().setFileNum(APPLICATION_PROPERTIES_FILE_NUM)
        .setRealmNum(0L).setShardNum(0L).build();
    response = TestHelper.getFileContent(fStub, applicationPropertiesFileId, genesisAccount,
        genKeyPair, nodeAccount);
    byte[] applicationPropertiesBytes = response.getFileGetContents().getFileContents().getContents()
        .toByteArray();
    ServicesConfigurationList configValues = null;
    configValues = ServicesConfigurationList.parseFrom(applicationPropertiesBytes);

    // Build a Properties object from the configuration prototype
    serverAppProperties = new Properties();
    configValues.getNameValueList().forEach(setting -> {
      serverAppProperties.setProperty(setting.getName(), setting.getValue());
    });

    // Parse and save values that will be used by client code
    maxGasLimit = fetchLong("maxGasLimit", DUMMY_LONG);
    maxFileSize = fetchInt("maxFileSize", DUMMY_INT);
    maxContractStateSize = fetchInt("maxContractStateSize", DUMMY_INT);
  }

  public long getMaxGasLimit() {
    return maxGasLimit;
  }
  public int getMaxFileSize() {
    return maxFileSize;
  }

  public int getMaxContractStateSize() {
    return maxContractStateSize;
  }

  private long fetchLong(String value, long defaultValue) {
    long result = 0;
    try {
      result = Long.parseLong(serverAppProperties.getProperty(value));
    } catch (NumberFormatException e) {
      result = defaultValue;
    }
      return result;
  }
  private int fetchInt(String value, int defaultValue) {
    int result = 0;
    try {
      result = Integer.parseInt(serverAppProperties.getProperty(value));
    } catch (NumberFormatException e) {
      result = defaultValue;
    }
      return result;
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
