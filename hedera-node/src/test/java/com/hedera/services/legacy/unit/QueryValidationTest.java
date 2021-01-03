package com.hedera.services.legacy.unit;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.StandardExemptions;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.context.ContextPlatformStatus;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.records.RecordCache;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.mocks.TestProperties;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.hederahashgraph.fee.FeeObject;
import com.swirlds.common.PlatformStatus;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.fcmap.FCMap;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import static org.mockito.BDDMockito.*;

@TestInstance(Lifecycle.PER_CLASS)
class QueryValidationTest {
	@BeforeAll
    public static void setupAll() {
      SettingsCommon.transactionMaxBytes = 1_234_567;
    }

  long payerAccountInitialBalance = 100000;
  private FCMap<MerkleEntityId, MerkleAccount> map = new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
  private AccountID nodeAccount =
      AccountID.newBuilder().setAccountNum(3).setRealmNum(0).setShardNum(0).build();
  private AccountID payerAccount =
      AccountID.newBuilder().setAccountNum(300).setRealmNum(0).setShardNum(0).build();
  private AccountID negetiveAccountNo =
      AccountID.newBuilder().setAccountNum(-1111).setRealmNum(0).setShardNum(0).build();
  private AccountID lowBalanceAccount = AccountID.newBuilder().setAccountNum(3001).setRealmNum(0)
      .setShardNum(0).build();
  private KeyPair payerKeyGenerated = new KeyPairGenerator().generateKeyPair();
  private TransactionHandler transactionHandler;

  private Transaction createQueryHeaderTransfer(AccountID payer) throws Exception {
    Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now());
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payer.getAccountNum(),
        payer.getRealmNum(), payer.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 100, timestamp, transactionDuration,
        false, "test", payer.getAccountNum(), -100l, nodeAccount.getAccountNum(), 100l);
    List<PrivateKey> privateKeyList = new ArrayList<>();
    List<PublicKey> pubKeyList = new ArrayList<>();
    PrivateKey genPrivKey = payerKeyGenerated.getPrivate();
    PublicKey genPubKey = payerKeyGenerated.getPublic();
    privateKeyList.add(genPrivKey);
    privateKeyList.add(genPrivKey);
    pubKeyList.add(genPubKey);
    pubKeyList.add(genPubKey);
    transferTx = TransactionSigner.signTransactionWithSignatureMap(transferTx, privateKeyList, pubKeyList);
    return transferTx;
  }

  @BeforeAll
  void initializeState() throws Exception {
    PropertyLoaderTest.populatePropertiesWithConfigFilesPath(
        "./configuration/dev/application.properties",
        "./configuration/dev/api-permission.properties");
    PrecheckVerifier precheckVerifier = mock(PrecheckVerifier.class);
    given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
    var policies = new SystemOpPolicies(new MockEntityNumbers());
    var platformStatus = new ContextPlatformStatus();
    platformStatus.set(PlatformStatus.ACTIVE);
    transactionHandler = new TransactionHandler(
            mock(RecordCache.class),
            precheckVerifier,
            () -> map,
            nodeAccount,
            new MockAccountNumbers(),
            policies,
            new StandardExemptions(new MockAccountNumbers(), policies),
            platformStatus);
    transactionHandler.setBasicPrecheck(
            new BasicPrecheck(TestContextValidator.TEST_VALIDATOR, new MockGlobalDynamicProps()));
    byte[] pubKey = ((EdDSAPublicKey) payerKeyGenerated.getPublic()).getAbyte();
    onboardAccount(payerAccount, pubKey, payerAccountInitialBalance);
    onboardAccount(lowBalanceAccount, pubKey, 100L);

    GlobalFlag.getInstance().setExchangeRateSet(getDefaultExchangeRateSet());
  }

  private static ExchangeRateSet getDefaultExchangeRateSet() {
    long expiryTime = Long.MAX_VALUE;
    return RequestBuilder.getExchangeRateSetBuilder(1, 1, expiryTime, 1, 1, expiryTime);
  }

  private void onboardAccount(AccountID account, byte[] publicKey, long initialBalance)
      throws Exception {
    NodeAccountsCreation.insertAccount(initialBalance, MiscUtils.commonsBytesToHex(publicKey), account, map
    );
  }

  @Test
  void testValidateGetInfoQuery_validateQuery() throws Exception {
    Transaction transferTransaction = createQueryHeaderTransfer(payerAccount);
    Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(payerAccount,
        transferTransaction, ResponseType.ANSWER_ONLY);

    ResponseCodeEnum result = transactionHandler.validateQuery(cryptoGetInfoQuery, true);
    assert (result == ResponseCodeEnum.OK);
  }

  @Test
  void testValidateGetInfoQuery_validateQuery_negetive() throws Exception {
    Transaction transferTransaction = createQueryHeaderTransfer(negetiveAccountNo);
    Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(negetiveAccountNo,
        transferTransaction, ResponseType.ANSWER_ONLY);

    ResponseCodeEnum result = transactionHandler.validateQuery(cryptoGetInfoQuery, true);
    assertEquals(result, ResponseCodeEnum.NOT_SUPPORTED);
  }
}
