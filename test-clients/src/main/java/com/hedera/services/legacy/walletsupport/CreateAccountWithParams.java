package com.hedera.services.legacy.walletsupport;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * create account with user parameter to get public key and initial balance
 *
 * @author oc
 */
public class CreateAccountWithParams {
    private static final Logger log = LogManager.getLogger(CreateAccountWithParams.class);

    public static String fileName = TestHelper.getStartUpFile();
    private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

    static String publickeyStr = "some_randome_key_not_working_at_all";
    static long initialBalance = 1000_0000L;
    static int nodeAccountNum = 3;
    public CreateAccountWithParams(int port, String host) {
        // connecting to the grpc server on the port
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        CreateAccountWithParams.stub = CryptoServiceGrpc.newBlockingStub(channel);
    }

    public CreateAccountWithParams() {
    }

  /**
   *
   * @param args
   * @throws InterruptedException
   * @throws IOException
   * @throws URISyntaxException
   * @throws InvalidNodeTransactionPrecheckCode
   * @throws DecoderException
   */
    public static void main(String args[]) throws InterruptedException, IOException, URISyntaxException, InvalidNodeTransactionPrecheckCode, DecoderException {
        Properties properties = TestHelper.getApplicationProperties();
        String host = "localhost" ;//properties.getProperty("host");
        int port = 50211;//Integer.parseInt(properties.getProperty("port"));

        if ((args.length) > 0) {
            publickeyStr = args[0];
        }
        if ((args.length) > 1) {
            initialBalance = Long.parseLong(args[1]);
        }

        if ((args.length) > 2) {
            host = args[2];
        }
        if ((args.length) > 3) {
            port = Integer.parseInt(args[3]);
        }
		if ((args.length) > 4) {
			nodeAccountNum = Integer.parseInt(args[4]);
		}

        log.info("Connecting to NODE          " + host + " : " + port + " accountNum " + nodeAccountNum);
        log.info("New Account public key      " + publickeyStr);
        log.info("New Account initial balance " + initialBalance);

        CreateAccountWithParams createAccountInstance = new CreateAccountWithParams(port, host);
		createAccountInstance.demo();
    }

    public void demo() throws InterruptedException, IOException, URISyntaxException, InvalidNodeTransactionPrecheckCode, DecoderException {


        Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

        List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
        // get Private Key
        PrivateKey genesisPrivateKey = genesisAccount.get(0).getKeyPairList().get(0).getPrivateKey();
        AccountID payerAccount = genesisAccount.get(0).getAccountId();

        AccountID defaultNodeAccount = RequestBuilder.getAccountIdBuild((long)nodeAccountNum, 0l, 0l);

        // create 1st account by payer as genesis
        KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
        Transaction transaction = createAccountWallet(payerAccount,
                defaultNodeAccount,
                publickeyStr,
                initialBalance);

        Transaction signTransaction = TransactionSigner.signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

        System.out.println(signTransaction);
        TransactionResponse response = stub.createAccount(signTransaction);
        Assert.assertNotNull(response);
        Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
        log.info("Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode().name());
        TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
        AccountID newlyCreateAccountId1 = TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
        Assert.assertNotNull(newlyCreateAccountId1);
        log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
        log.info("--------------------------------------");


    }

	public Transaction createAccountWallet(AccountID payerAccount, AccountID nodeAccount, String pubKeyStr, long initialBalance) throws DecoderException {
		Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionDuration = RequestBuilder.getDuration(30);

		byte[] bytes = HexUtils.hexToBytes(pubKeyStr);
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(bytes)).build();
		List<Key> keyList = Collections.singletonList(key);


		long transactionFee = TestHelper.getCryptoMaxFee();
		boolean generateRecord = true;
		String memo = "Create Account Test";
		long sendRecordThreshold = 999;
		long receiveRecordThreshold = 999;
		boolean receiverSigRequired = false;
		Duration autoRenewPeriod = RequestBuilder.getDuration(CustomPropertiesSingleton.getInstance().getAccountDuration());

		Key keys = Key.newBuilder().setEd25519(keyList.get(0).getEd25519()).build();
		return RequestBuilder.getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
				payerAccount.getShardNum(), nodeAccount.getAccountNum(),
				nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee, startTime,
				transactionDuration, generateRecord, memo, keys,
				initialBalance, sendRecordThreshold, receiveRecordThreshold, receiverSigRequired,
				autoRenewPeriod);
	}
}
