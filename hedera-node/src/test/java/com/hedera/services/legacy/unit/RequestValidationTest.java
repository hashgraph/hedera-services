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

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.StandardExemptions;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.context.ContextPlatformStatus;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.mocks.TestExchangeRates;
import com.hedera.test.mocks.TestFeesFactory;
import com.hedera.test.mocks.TestProperties;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.context.domain.security.PermissionedAccountsRange;
import com.hedera.services.legacy.proto.utils.CommonUtils;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.swirlds.common.PlatformStatus;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static com.hedera.test.mocks.TestUsagePricesProvider.TEST_USAGE_PRICES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * @author Akshay
 * @Date : 8/13/2018
 */
public class RequestValidationTest {

  /**
   * testing nodeAccount Validation function for positive and negative scenario
   */
  @Test
  public void testNodeAccountValidation() throws Exception {
    long nodeAccShard = 0;
    long nodeAccRealm = 2;
    long nodeAccnNum = 1007;
    AccountID nodeAcc = AccountID.newBuilder().setShardNum(nodeAccShard).setRealmNum(nodeAccRealm)
        .setAccountNum(nodeAccnNum).build();

    var policies = new SystemOpPolicies(new MockEntityNumbers());
    var platformStatus = new ContextPlatformStatus();
    platformStatus.set(PlatformStatus.ACTIVE);
    TransactionHandler trHandler =
        new TransactionHandler(
                null,
                null,
                null,
                nodeAcc,
                null,
                TestFeesFactory.FEES_FACTORY.get(),
                () -> StateView.EMPTY_VIEW,
                new BasicPrecheck(TestContextValidator.TEST_VALIDATOR, new MockGlobalDynamicProps()),
                null,
                null,
                new MockAccountNumbers(),
                policies,
                new StandardExemptions(new MockAccountNumbers(), policies),
                platformStatus);
    Timestamp timestamp =
        RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(10));

    Duration transactionDuration = RequestBuilder.getDuration(30);

    KeyPair pair = new KeyPairGenerator().generateKeyPair();

    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();

    String pubKeyStr = MiscUtils.commonsBytesToHex(pubKey);

    Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    List<Key> keyList = new ArrayList<Key>();
    keyList.add(key);

    long transactionFee = 100l;
    boolean generateRecord = true;
    String memo = "NodeAccount test";
    long initialBalance = 10000l;
    long sendRecordThreshold = 100l;
    long receiveRecordThreshold = 100l;
    boolean receiverSigRequired = true;
    Duration autoRenewPeriod = RequestBuilder.getDuration(500);

    Transaction matchingNodeAccTransaction =
        RequestBuilder.getCreateAccountBuilder(nodeAccnNum, nodeAccRealm, nodeAccShard, nodeAccnNum,
            nodeAccRealm, nodeAccShard, transactionFee, timestamp, transactionDuration,
            generateRecord, memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
    Transaction nonMatchingTransaction =
        RequestBuilder.getCreateAccountBuilder(nodeAccnNum, nodeAccRealm, nodeAccShard, nodeAccnNum,
            nodeAccRealm + 1, nodeAccShard + 1, transactionFee, timestamp, transactionDuration,
            generateRecord, memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
    TransactionBody matchingBody = CommonUtils.extractTransactionBody(matchingNodeAccTransaction);
    TransactionBody nonMatchingBody = CommonUtils.extractTransactionBody(nonMatchingTransaction);
    ResponseCodeEnum matchingPreCheckReturn = trHandler.validateNodeAccount(matchingBody);
    Assertions.assertEquals(matchingPreCheckReturn, OK);
    ResponseCodeEnum nonMatchingPreCheckReturn = trHandler.validateNodeAccount(nonMatchingBody);
    Assertions.assertNotEquals(nonMatchingPreCheckReturn, OK);
  }

  @Test
  public void apiPermissionValidation_Number_Test() {
    AccountID payerAccountId = AccountID.newBuilder().setAccountNum(55).build();
    String accountIdRange = "55";
    PermissionedAccountsRange accountRange = PermissionedAccountsRange.from(accountIdRange);
    Assertions.assertEquals(
            OK,
            accountRange.contains(payerAccountId.getAccountNum()) ? OK : NOT_SUPPORTED);
  }

  @Test
  public void apiPermissionValidation_Range_Positive_Test() {
    AccountID payerAccountId = AccountID.newBuilder().setAccountNum(55).build();
    String accountIdRange = "55-*";
    PermissionedAccountsRange accountRange = PermissionedAccountsRange.from(accountIdRange);
    Assertions.assertEquals(
            OK,
            accountRange.contains(payerAccountId.getAccountNum()) ? OK : NOT_SUPPORTED);
  }

  @Test
  public void apiPermissionValidation_Range_Negative_Test() {
    AccountID payerAccountId = AccountID.newBuilder().setAccountNum(54).build();
    String accountIdRange = "55-*";
    PermissionedAccountsRange accountRange = PermissionedAccountsRange.from(accountIdRange);
    Assertions.assertEquals(
            NOT_SUPPORTED,
            accountRange.contains(payerAccountId.getAccountNum()) ? OK : NOT_SUPPORTED);
  }
}
