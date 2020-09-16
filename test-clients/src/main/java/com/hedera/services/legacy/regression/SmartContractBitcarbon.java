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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.Abi;
import org.ethereum.solidity.Abi.Event;
import org.junit.Assert;
import org.apache.commons.collections4.Predicate;

/**
 * Test a multi-part smart contract
 *
 * @author Peter
 */
public class SmartContractBitcarbon {

	private static final String ARBITRARY_ADDRESS = "1234567890123456789012345678901234567890";
	private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	private final Logger log = LogManager.getLogger(SmartContractBitcarbon.class);


	private static final int MAX_RECEIPT_RETRIES = 60;
	public static final String ADDRESS_BOOK_BIN = "/testfiles/AddressBook.bin";
	public static final String JURISDICTIONS_BIN = "/testfiles/Jurisdictions.bin";
	public static final String MINTERS_BIN = "/testfiles/Minters.bin";

	private static final String SC_JUR_CONSTRUCTOR_ABI = "{\"inputs\":[{\"name\":\"_admin\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}\n";
	private static final String SC_JUR_ADD_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"name\"," +
			"\"type\":\"string\"},{\"name\":\"taxRate\",\"type\":\"uint256\"},{\"name\":\"inventory\"," +
			"\"type\":\"address\"},{\"name\":\"reserve\",\"type\":\"address\"}],\"name\":\"add\",\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_JUR_ISVALID_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"code\"," +
			"\"type\":\"bytes32\"}],\"name\":\"isValid\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_JUR_ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"code\"," +
			"\"type\":\"bytes32\"}],\"name\":\"getInventory\",\"outputs\":[{\"name\":\"\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false," +
			"\"inputs\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"taxRate\",\"type\":\"uint256\"}," +
			"{\"name\":\"inventory\",\"type\":\"address\"},{\"name\":\"reserve\",\"type\":\"address\"}]," +
			"\"name\":\"add\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}," +
			"{\"name\":\"taxRate\",\"type\":\"uint256\"},{\"name\":\"reserve\",\"type\":\"address\"}," +
			"{\"name\":\"inventory\",\"type\":\"address\"}],\"name\":\"setJurisdictionParams\",\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"getReserve\",\"outputs\":[{\"name\":\"\"," +
			"\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}," +
			"{\"constant\":true,\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"isValid\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"owner\",\"outputs\":[{\"name\":\"\"," +
			"\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}," +
			"{\"constant\":false,\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"remove\"," +
			"\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}," +
			"{\"constant\":true,\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"getTaxRate\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"priceCents\",\"type\":\"uint256\"}," +
			"{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"getTaxes\",\"outputs\":[{\"name\":\"\"," +
			"\"type\":\"uint256\"},{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\"," +
			"\"type\":\"bytes32\"}],\"name\":\"jurisdictions\",\"outputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}," +
			"{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"taxRate\",\"type\":\"uint256\"}," +
			"{\"name\":\"inventory\",\"type\":\"address\"},{\"name\":\"reserve\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[]," +
			"\"name\":\"getCodes\",\"outputs\":[{\"name\":\"\",\"type\":\"bytes32[]\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"code\"," +
			"\"type\":\"bytes32\"},{\"name\":\"taxRate\",\"type\":\"uint256\"}],\"name\":\"setTaxRate\",\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"isBitcarbon\",\"outputs\":[{\"name\":\"\"," +
			"\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}," +
			"{\"constant\":false,\"inputs\":[{\"name\":\"newOwner\",\"type\":\"address\"}]," +
			"\"name\":\"transferOwnership\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}]," +
			"\"name\":\"bitcarbonJurisdiction\",\"outputs\":[{\"name\":\"\",\"type\":\"bytes32\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"inventory\",\"type\":\"address\"}],\"name\":\"getPendingTokens\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256[]\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"},{\"inputs\":[{\"name\":\"_admin\",\"type\":\"address\"}],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"},{\"anonymous\":false," +
			"\"inputs\":[{\"indexed\":false,\"name\":\"code\",\"type\":\"bytes32\"},{\"indexed\":false," +
			"\"name\":\"name\",\"type\":\"string\"},{\"indexed\":false,\"name\":\"taxRate\",\"type\":\"uint256\"}," +
			"{\"indexed\":false,\"name\":\"inventory\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"reserve\"," +
			"\"type\":\"address\"},{\"indexed\":false,\"name\":\"timestamp\",\"type\":\"uint256\"}]," +
			"\"name\":\"JurisdictionAdded\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false," +
			"\"name\":\"code\",\"type\":\"bytes32\"},{\"indexed\":false,\"name\":\"timestamp\",\"type\":\"uint256\"}]," +
			"\"name\":\"JurisdictionRemoved\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false," +
			"\"name\":\"oldTaxRate\",\"type\":\"uint256\"},{\"indexed\":false,\"name\":\"newTaxRate\"," +
			"\"type\":\"uint256\"},{\"indexed\":false,\"name\":\"timestamp\",\"type\":\"uint256\"}]," +
			"\"name\":\"TaxRateChanged\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true," +
			"\"name\":\"previousOwner\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"newOwner\"," +
			"\"type\":\"address\"}],\"name\":\"OwnershipTransferred\",\"type\":\"event\"}]";
	private static final String SC_MINT_CONSTRUCTOR_ABI = "{\"inputs\":[{\"name\":\"_jurisdictions\"," +
			"\"type\":\"address\"},{\"name\":\"_admin\",\"type\":\"address\"}],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
	private static final String SC_MINT_ADD_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"minter\"," +
			"\"type\":\"address\"},{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"jurisdiction\"," +
			"\"type\":\"bytes32\"}],\"name\":\"add\",\"outputs\":[],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_MINT_ISVALID_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"minter\"," +
			"\"type\":\"address\"}],\"name\":\"isValid\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_MINT_SEVEN_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"seven\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SC_MINT_OWNER_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"owner\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SC_MINT_CONFIGURE_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_jurisdictions\"," +
			"\"type\":\"address\"}],\"name\":\"configureJurisdictionContract\",\"outputs\":[],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	private static AccountID nodeAccount;
	private static long node_account_number;
	private static long node_shard_number;
	private static long node_realm_number;
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	private AccountID genesisAccount;
	private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
	private static String host;
	private static int port;
	private static long localCallGas;
	private static Event jurisAddEvent = null;
	private static long contractDuration;


	public static void main(String args[]) throws Exception {
		Properties properties = TestHelper.getApplicationProperties();
		contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
		host = properties.getProperty("host");
		port = Integer.parseInt(properties.getProperty("port"));
		node_account_number = Utilities.getDefaultNodeAccount();
		node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
		node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
		nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
				.setRealmNum(node_shard_number).setShardNum(node_realm_number).build();
		localCallGas = Long.parseLong(properties.getProperty("LOCAL_CALL_GAS"));

		int numberOfReps = 1;
		if ((args.length) > 0) {
			numberOfReps = Integer.parseInt(args[0]);
		}
		for (int i = 0; i < numberOfReps; i++) {
			SmartContractBitcarbon scSs = new SmartContractBitcarbon();
			scSs.demo();
		}

	}

	private void loadGenesisAndNodeAcccounts() throws Exception {
		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

		// Get Genesis Account key Pair
		List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");

		// get Private Key
		KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
		PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
		KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

		// get the Account Object
		genesisAccount = genesisAccountList.get(0).getAccountId();
		accountKeyPairs.put(genesisAccount, genesisKeyPair);
	}

	private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
			throws Exception {
		Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
				nodeAccount, payer,
				accountKeyPairs.get(payer), nodeAccount, transferAmt);
		return transferTx;
	}

	private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance)
			throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		Transaction transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
						accountKeyPairs.get(payerAccount));
		TransactionResponse response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		System.out.println(
				"Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId = TestHelper
				.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		accountKeyPairs.put(newlyCreateAccountId, keyPair);
		channel.shutdown();
		return newlyCreateAccountId;
	}

	private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
		Query query = Query.newBuilder()
				.setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
						transactionId, ResponseType.ANSWER_ONLY)).build();
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		Response transactionReceipts = stub.getTransactionReceipts(query);
		int attempts = 1;
		while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
				.getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN)) {
			Thread.sleep(1000);
			transactionReceipts = stub.getTransactionReceipts(query);
			System.out.println("waiting to getTransactionReceipts as not Unknown..." +
					transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
			attempts++;
		}
		channel.shutdown();
		return transactionReceipts.getTransactionGetReceipt();
	}

	private ContractID createContract(AccountID payerAccount, FileID contractFile,
			byte[] constructorData) throws Exception {
		ContractID createdContract = null;
		ByteString dataToPass = ByteString.EMPTY;
		if (constructorData != null) {
			dataToPass = ByteString.copyFrom(constructorData);
		}

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();

		Duration contractAutoRenew = Duration.newBuilder().setSeconds(contractDuration).build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
		Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
		Transaction createContractRequest = TestHelper
				.getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
						nodeAccount.getShardNum(), 100l, timestamp,
						transactionDuration, true, "", 8_500, contractFile, dataToPass, 0,
						contractAutoRenew, accountKeyPairs.get(payerAccount), "", null);

		TransactionResponse response = stub.createContract(createContractRequest);
		System.out.println(
				" createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());

		TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
				createContractBody.getTransactionID());
		if (contractCreateReceipt != null) {
			createdContract = contractCreateReceipt.getReceipt().getContractID();
		}
		TransactionRecord trRecord = getTransactionRecord(payerAccount,
				createContractBody.getTransactionID());
		Assert.assertNotNull(trRecord);
		Assert.assertTrue(trRecord.hasContractCreateResult());
		Assert.assertEquals(trRecord.getContractCreateResult().getContractID(),
				contractCreateReceipt.getReceipt().getContractID());

		channel.shutdown();

		return createdContract;
	}

	/*
	Encoding functions for Jurisdictions: constructor, add, isValid
	 */
	public static byte[] encodeJurisdictionsConstructor(String admin) {
		CallTransaction.Function function = getJurisdictionsConstructorFunction();
		byte[] encodedFunc = function.encode(admin);
		return encodedFunc;
	}

	public static CallTransaction.Function getJurisdictionsConstructorFunction() {
		String funcJson = SC_JUR_CONSTRUCTOR_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeJurisdictionsAdd(String name, int taxRate, String inventoryAddr,
			String reserveAddr) {
		CallTransaction.Function function = getJurisdictionsAddFunction();
		byte[] encodedFunc = function.encode(name, taxRate, inventoryAddr, reserveAddr);
		return encodedFunc;
	}

	public static CallTransaction.Function getJurisdictionsAddFunction() {
		String funcJson = SC_JUR_ADD_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeJurisdictionsIsValid(byte[] code) {
		CallTransaction.Function function = getJurisdictionsIsValidFunction();
		byte[] encodedFunc = function.encode(code);
		return encodedFunc;
	}

	public static CallTransaction.Function getJurisdictionsIsValidFunction() {
		String funcJson = SC_JUR_ISVALID_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public Boolean decodeJurisdictionsIsValidResult(byte[] value) {
		Boolean decodedReturnedValue = null;
		CallTransaction.Function function = getJurisdictionsIsValidFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			decodedReturnedValue = (Boolean) retResults[0];
		}
		return decodedReturnedValue;
	}

	/*
	Encoding functions for Minters: constructor, add, isValid, configure
	seven, owner
	 */
	public static byte[] encodeMintersConstructor(String jurisdictions, String admin) {
		CallTransaction.Function function = getMintersConstructorFunction();
		byte[] encodedFunc = function.encode(jurisdictions, admin);
		return encodedFunc;
	}

	public static CallTransaction.Function getMintersConstructorFunction() {
		String funcJson = SC_MINT_CONSTRUCTOR_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeMintersIsValid(String minter) {
		CallTransaction.Function function = getMintersIsValidFunction();
		byte[] encodedFunc = function.encode(minter);
		return encodedFunc;
	}

	public static CallTransaction.Function getMintersIsValidFunction() {
		String funcJson = SC_MINT_ISVALID_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeMintersSeven() {
		CallTransaction.Function function = getMintersSevenFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getMintersSevenFunction() {
		String funcJson = SC_MINT_SEVEN_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeMintersSevenResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getMintersSevenFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	public static byte[] encodeMintersOwner() {
		CallTransaction.Function function = getMintersOwnerFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getMintersOwnerFunction() {
		String funcJson = SC_MINT_OWNER_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static String decodeMintersOwnerResult(byte[] value) {
		String decodedReturnedValue = "";
		CallTransaction.Function function = getMintersOwnerFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			decodedReturnedValue = HexUtils.bytes2Hex((byte[]) retResults[0]);
		}
		return decodedReturnedValue;
	}

	public static byte[] encodeMintersAdd(String minter, String name, byte[] jurisdiction) {
		CallTransaction.Function function = getMintersAddFunction();
		byte[] encodedFunc = function.encode(minter, name, jurisdiction);
		return encodedFunc;
	}

	public static CallTransaction.Function getMintersAddFunction() {
		String funcJson = SC_MINT_ADD_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeMintersConfigure(String jurisdictions) {
		CallTransaction.Function function = getMintersConfigureFunction();
		byte[] encodedFunc = function.encode(jurisdictions);
		return encodedFunc;
	}

	public static CallTransaction.Function getMintersConfigureFunction() {
		String funcJson = SC_MINT_CONFIGURE_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	private TransactionRecord callContract(AccountID payerAccount, ContractID contractToCall,
			byte[] data, ResponseCodeEnum expectedStatus)
			throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
		Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
		ByteString dataBstr = ByteString.EMPTY;
		if (data != null) {
			dataBstr = ByteString.copyFrom(data);
		}
		Transaction callContractRequest = TestHelper
				.getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), node_account_number, 0l, 0l, 100l, timestamp,
						transactionDuration, 250000, contractToCall, dataBstr, 0,
						accountKeyPairs.get(payerAccount));

		TransactionResponse response = stub.contractCallMethod(callContractRequest);
		System.out.println(
				" createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());
		Thread.sleep(1000);
		TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCallReceipt = getReceipt(
				callContractBody.getTransactionID());
		Assert.assertEquals(expectedStatus, contractCallReceipt.getReceipt().getStatus());

		TransactionRecord txRecord = getTransactionRecord(payerAccount,
				callContractBody.getTransactionID());
		Assert.assertTrue(txRecord.hasContractCallResult());

		String errMsg = txRecord.getContractCallResult().getErrorMessage();
		if (!StringUtils.isEmpty(errMsg)) {
			log.info("@@@ Contract Call resulted in error: " + errMsg);
		}

		channel.shutdown();
		return txRecord;
	}

	private TransactionRecord getTransactionRecord(AccountID payerAccount,
			TransactionID transactionId) throws Exception {
		AccountID createdAccount = null;
		int port = 50211;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		long fee = FeeClient.getCostForGettingTxRecord();
		Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
				ResponseType.COST_ANSWER);
		fee = recordResp.getTransactionGetRecord().getHeader().getCost();
		recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
				ResponseType.ANSWER_ONLY);
		TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
		System.out.println("tx record = " + txRecord);
		channel.shutdown();
		return txRecord;
	}


	private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
			CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType)
			throws Exception {
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
		Query getRecordQuery = RequestBuilder
				.getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
		Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
		return recordResp;
	}

	private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data)
			throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);
		ByteString callData = ByteString.EMPTY;
		int callDataSize = 0;
		if (data != null) {
			callData = ByteString.copyFrom(data);
			callDataSize = callData.size();
		}
		long fee = FeeClient.getCostContractCallLocalFee(callDataSize);
		Response callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
				ResponseType.COST_ANSWER);
		fee = callResp.getContractCallLocal().getHeader().getCost() + localCallGas;
		callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
				ResponseType.ANSWER_ONLY);
		System.out.println("callContractLocal response = " + callResp);
		ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
				.getContractCallResult();

		channel.shutdown();
		return functionResults.toByteArray();
	}

	private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
			SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, ByteString callData, long fee,
			ResponseType resposeType)
			throws Exception {
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
		Query contractCallLocal = RequestBuilder
				.getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
						resposeType);

		Response callResp = stub.contractCallLocalMethod(contractCallLocal);
		return callResp;
	}

	private int getMintersSeven(AccountID payerAccount, ContractID contractId) throws Exception {
		int retVal = 0;
		byte[] dataToGet = encodeMintersSeven();
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeMintersSevenResult(result);
		}
		return retVal;
	}

	private Boolean getJurisdictionsIsValid(AccountID payerAccount, ContractID contractId, byte[] address)
			throws Exception {
		Boolean retVal = null;
		byte[] dataToGet = encodeJurisdictionsIsValid(address);
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeJurisdictionsIsValidResult(result);
		}
		return retVal;
	}

	private String getMintersOwner(AccountID payerAccount, ContractID contractId) throws Exception {
		String retVal = "";
		byte[] dataToGet = encodeMintersOwner();
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeMintersOwnerResult(result);
		}
		return retVal;
	}

	private TransactionRecord callJurisdictionsAdd(AccountID payerAccount, ContractID contractId,
			ResponseCodeEnum expectedStatus, String name, int taxRate, String inventory, String reserve)
			throws Exception {
		byte[] dataToSet = encodeJurisdictionsAdd(name, taxRate, inventory, reserve);
		TransactionRecord txRec = callContract(payerAccount, contractId, dataToSet, expectedStatus);
		return txRec;
	}

	private TransactionRecord callMintersadd(AccountID payerAccount, ContractID contractId,
			ResponseCodeEnum expectedStatus, String minter, String name, byte[] jurisdiction)
			throws Exception {
		byte[] dataToSet = encodeMintersAdd(minter, name, jurisdiction);
		TransactionRecord txRec = callContract(payerAccount, contractId, dataToSet, expectedStatus);
		return txRec;
	}

	private TransactionRecord callMintersConfigure(AccountID payerAccount, ContractID contractId,
			ResponseCodeEnum expectedStatus, String jurisdictions)
			throws Exception {
		byte[] dataToSet = encodeMintersConfigure(jurisdictions);
		TransactionRecord txRec = callContract(payerAccount, contractId, dataToSet, expectedStatus);
		return txRec;
	}

	public void demo() throws Exception {
		loadGenesisAndNodeAcccounts();

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
				nodeAccount);
		channel.shutdown();

		KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
		AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);
		Assert.assertNotNull(crAccount);
		Assert.assertNotEquals(0, crAccount.getAccountNum());
		log.info("Account created successfully: " + crAccount);

    /*
    Load three contracts: AddressBook, Jurisdictions, and Minters
     */

		// Upload AddressBook file
		FileID addressBookFileId = LargeFileUploadIT
				.uploadFile(crAccount, ADDRESS_BOOK_BIN, crAccountKeyPair);
		Assert.assertNotNull(addressBookFileId);
		Assert.assertNotEquals(0, addressBookFileId.getFileNum());
		log.info("AddressBook file uploaded successfully");

		// Create AddressBook contract
		ContractID addressBookContractId = createContract(crAccount, addressBookFileId,
				null);
		Assert.assertNotNull(addressBookContractId);
		Assert.assertNotEquals(0, addressBookContractId.getContractNum());
		log.info("AddressBook Contract created successfully: " + addressBookContractId);

		// Upload Jurisdictions file
		FileID jurisdictionsFileId = LargeFileUploadIT
				.uploadFile(crAccount, JURISDICTIONS_BIN, crAccountKeyPair);
		Assert.assertNotNull(jurisdictionsFileId);
		Assert.assertNotEquals(0, jurisdictionsFileId.getFileNum());
		log.info("Jurisdictions file uploaded successfully");

		// To be "linked" into Minters contract binary
		String addressBookSolidityAddress = CommonUtils.calculateSolidityAddress(
				0, 0, addressBookContractId.getContractNum());


		// Create Jurisdictions contract
		// Address for "admin" caller, not used
		byte[] constructorArgs = encodeJurisdictionsConstructor(ARBITRARY_ADDRESS);
		Thread.sleep(1000);
		ContractID jurisdictionsContractId = createContract(crAccount, jurisdictionsFileId,
				constructorArgs);
		Assert.assertNotNull(jurisdictionsContractId);
		Assert.assertNotEquals(0, jurisdictionsContractId.getContractNum());
		log.info("Jurisdictions Contract created successfully: " + jurisdictionsContractId);

		// To be passed to Minters constructor
		String jurisdictionsSolidityAddress = CommonUtils.calculateSolidityAddress(
				0, 0, jurisdictionsContractId.getContractNum());


		// Upload Minters file, "linking" address of AddressBook into the where needed.
		FileID MintersFileId = LargeFileUploadIT.uploadFileWithSubst(
				crAccount, MINTERS_BIN, crAccountKeyPair,
				"_+AddressBook.sol:AddressBook_+", addressBookSolidityAddress);
		Assert.assertNotNull(MintersFileId);
		Assert.assertNotEquals(0, MintersFileId.getFileNum());
		log.info("Minters file uploaded successfully");

		// Create Minters contract.
		byte[] minterConstructorArgs = encodeMintersConstructor(jurisdictionsSolidityAddress,
				ARBITRARY_ADDRESS);
		Thread.sleep(1000);
		ContractID mintersContractId = createContract(crAccount, MintersFileId, minterConstructorArgs);

		Assert.assertNotNull(mintersContractId);
		Assert.assertNotEquals(0, mintersContractId.getContractNum());
		log.info("Minters Contract created successfully: " + mintersContractId);

		TransactionRecord txRec;
		// Configure the Minter with the address of the Jurisdictions contract
		txRec = callMintersConfigure(crAccount, mintersContractId, ResponseCodeEnum.SUCCESS,
				jurisdictionsSolidityAddress);
		log.info("Configured Jurisdictions address in Minters contract");

		// Add the Jurisdiction "ny" to the list
		txRec = callJurisdictionsAdd(crAccount, jurisdictionsContractId,
				ResponseCodeEnum.SUCCESS, "ny", 825, ARBITRARY_ADDRESS, ARBITRARY_ADDRESS);
		byte[] jurisdictionCode = parseJurisdictionsAddEvent(txRec);
		log.info("Added 'ny' jurisdiction to Jurisdictions list");

		Boolean valid = getJurisdictionsIsValid(crAccount, jurisdictionsContractId, jurisdictionCode);
		System.out.println("isvalid test after creating 'ny' returns " + valid);

		// Try  to get a constant 7 from the Minters contract
		int result = getMintersSeven(crAccount, mintersContractId);
		Assert.assertEquals(7, result);
		log.info("Minters get constant call worked");

		// Get the owner from the Minters contract
		String address = getMintersOwner(crAccount, mintersContractId);
		System.out.println("Minters thinks that its owner address is " + address);

		// Get the owner from the Jurisdictions contract
		address = getMintersOwner(crAccount, jurisdictionsContractId);
		System.out.println("Jurisdictions thinks that its owner address is " + address);

		// Add a Minter to the list of Minters
		txRec = callMintersadd(crAccount, mintersContractId, ResponseCodeEnum.SUCCESS,
				ARBITRARY_ADDRESS, "Peter", jurisdictionCode);

		// Marker message for regression report
		log.info("Regression summary: This run is successful.");
	}

	private byte[] parseJurisdictionsAddEvent(TransactionRecord addRecord) {
		assert (addRecord.hasContractCallResult());
		ContractFunctionResult setResults = addRecord.getContractCallResult();
		List<ContractLoginfo> logs = setResults.getLogInfoList();
		for (ContractLoginfo currLog : logs) {
			ByteString logdata = currLog.getData();
			byte[] dataArr = { };
			if (logdata != null) {
				dataArr = logdata.toByteArray();
			}
			List<ByteString> topicsBstr = currLog.getTopicList();
			int topicSize = 0;
			if (topicsBstr != null) {
				topicSize = topicsBstr.size();
			}
			byte[][] topicsArr = new byte[topicSize][];
			for (int topicIndex = 0; topicIndex < topicsBstr.size(); topicIndex++) {
				topicsArr[topicIndex] = topicsBstr.get(topicIndex).toByteArray();
			}

			Event storedEvnt = getJurisAddEvent();
			List<?> eventData = storedEvnt.decode(dataArr, topicsArr);
			byte[] retValue = (byte[]) eventData.get(0);
			return retValue;
		}
		return null;
	}

	private static Event getJurisAddEvent() {
		if (jurisAddEvent == null) {
			Abi abi = Abi.fromJson(SC_JUR_ABI);
			Predicate<Event> searchEventPredicate = sep -> {
				return sep.name.equals("JurisdictionAdded");
			};
			jurisAddEvent = abi.findEvent(searchEventPredicate);

		}
		return jurisAddEvent;
	}
}
