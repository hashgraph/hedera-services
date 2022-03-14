package com.hedera.services.bdd.spec.infrastructure.meta;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ContractResources {
	public static final String SIMPLE_STORAGE_BYTECODE_PATH = bytecodePath("simpleStorage");
	public static final String PAYABLE_CONTRACT_BYTECODE_PATH = bytecodePath("PayReceivable");
	public static final String BALANCE_LOOKUP_BYTECODE_PATH = bytecodePath("BalanceLookup");
	public static final String CIRCULAR_TRANSFERS_BYTECODE_PATH = bytecodePath("CircularTransfers");
	public static final String VALID_BYTECODE_PATH = HapiSpecSetup.getDefaultInstance().defaultContractPath();
	public static final String BIG_ARRAY_BYTECODE_PATH = bytecodePath("BigArray");
	public static final String EMIT_EVENT_BYTECODE_PATH = bytecodePath("EmitEvent");
	public static final String FUSE_BYTECODE_PATH = bytecodePath("Fuse");
	public static final String LAST_TRACKING_SENDER_BYTECODE_PATH = bytecodePath("LastTrackingSender");
	public static final String MULTIPURPOSE_BYTECODE_PATH = bytecodePath("Multipurpose");
	public static final String CHILD_STORAGE_BYTECODE_PATH = bytecodePath("ChildStorage");
	public static final String DOUBLE_SEND_BYTECODE_PATH = bytecodePath("DoubleSend");
	public static final String FIBONACCI_PLUS_PATH = bytecodePath("FibonacciPlus");

	public static final String CREATE_CHILD_ABI = "{\"constant\":false," +
			"\"inputs\":[],\"name\":\"create\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String GET_CHILD_RESULT_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getIndirect\"," +
			"\"outputs\":[{\"name\":\"value\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String DEPOSIT_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\"," +
			"\"outputs\":[]," +
			"\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String GET_CODE_SIZE_ABI = "{\"constant\":true," +
			"\"inputs\":[{\"name\":\"_addr\",\"type\":\"address\"}],\"name\":\"getCodeSize\"," +
			"\"outputs\":[{\"name\":\"_size\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String SIMPLE_STORAGE_SETTER_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String SIMPLE_STORAGE_GETTER_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"get\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String SET_NODES_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint64[]\",\"name\":\"accounts\",\"type\":\"uint64[]\"}],\"name\":\"setNodes\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String RECEIVE_AND_SEND_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"keepAmountDivisor\",\"type\":\"uint32\"}," +
			"{\"internalType\":\"uint256\",\"name\":\"stopBalance\",\"type\":\"uint256\"}],\"name\":\"receiveAndSend\"," +
			"\"outputs\":[]," +
			"\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String BALANCE_LOOKUP_ABI = "{\"constant\":true," +
			"\"inputs\":[{\"internalType\":\"uint64\",\"name\":\"accountNum\",\"type\":\"uint64\"}],\"name\":\"lookup\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String BIG_ARRAY_CHANGE_ARRAY_ABI = "{ \"constant\": false," +
			" \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_value\", \"type\": \"uint256\" } ], " +
			"\"name\": \"changeArray\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String BIG_ARRAY_SET_SIZE_ABI = "{ \"constant\": false," +
			" \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_size\", \"type\": \"uint256\" } ], \"name\": \"setSize\"," +
			" \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String CONSPICUOUS_DONATION_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"toNum\",\"type\":\"uint32\"}," +
			"{\"internalType\":\"string\",\"name\":\"saying\",\"type\":\"string\"}],\"name\":\"donate\"," +
			"\"outputs\":[]," +
			"\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String TRACKING_SEND_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"toNum\",\"type\":\"uint32\"}," +
			"{\"internalType\":\"uint32\",\"name\":\"amount\",\"type\":\"uint32\"}],\"name\":\"uncheckedTransfer\"," +
			"\"outputs\":[]," +
			"\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String HOW_MUCH_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"howMuch\"," +
			"\"outputs\":[{\"internalType\":\"uint32\",\"name\":\"\",\"type\":\"uint32\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String LUCKY_NO_LOOKUP_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"pick\"," +
			"\"outputs\":[{\"internalType\":\"uint32\",\"name\":\"\",\"type\":\"uint32\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String BELIEVE_IN_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"no\",\"type\":\"uint32\"}],\"name\":\"believeIn\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String GET_MY_VALUE_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getMyValue\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String SET_ZERO_READ_ONE_ABI = "{\"constant\": false," +
			"\"inputs\": [{\"internalType\": \"uint256\",\"name\": \"_value\",\"type\": \"uint256\"}]," +
			"\"name\": \"setZeroReadOne\",\"outputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_getOne\",\"type\": \"uint256\"}]," +
			"\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String GROW_CHILD_ABI = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_childId\",\"type\": \"uint256\"}" +
			",{\"internalType\": \"uint256\",\"name\": \"_howManyKB\",\"type\": \"uint256\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"_value\",\"type\": \"uint256\"}]," +
			"\"name\": \"growChild\",\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\"," +
			"\"type\": \"function\"}";

	public static final String SEND_TO_TWO_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"toFirst\",\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"toSecond\"," +
			"\"type\":\"uint32\"}],\"name\":\"donate\",\"outputs\":[],\"payable\":true," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String SIMPLE_UPDATE_ABI = "{\"inputs\": [" +
			"{\"internalType\": \"uint256\",\"name\": \"n\",\"type\": \"uint256\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"m\",\"type\": \"uint256\"}]," +
			"\"name\": \"set\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String SIMPLE_SELFDESTRUCT_UPDATE_ABI = "{\"inputs\": [" +
			"{\"internalType\": \"address payable\",\"name\": \"beneficiary\"," +
			"\"type\": \"address\"}],\"name\": \"del\",\"outputs\": [],\"stateMutability\": \"nonpayable\"," +
			"\"type\": \"function\"}";
	public static final String CALLING_CONTRACT_SET_VALUE = "{ \"constant\": false, \"inputs\": [ { \"name\": \"_var1\", \"type\": \"uint256\" } ], \"name\": \"setVar1\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String CALLING_CONTRACT_CALL_CONTRACT = "{ \"constant\": false, \"inputs\": [ { \"name\": \"_addr\", \"type\": \"address\" }, { \"name\": \"_var1\", \"type\": \"uint256\" } ], \"name\": \"callContract\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String CALLING_CONTRACT_VIEW_VAR = "{ \"constant\": true, \"inputs\": [], \"name\": \"getVar1\", \"outputs\": [ { \"name\": \"\", \"type\": \"uint256\" } ], \"payable\": false, \"stateMutability\": \"view\", \"type\": \"function\" }";

	/* ABI for Logs.sol */
	public static final String LOGS_LOG0_ABI = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"n\"," +
			"\"type\":\"uint256\"}],\"name\":\"log0\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String LOGS_LOG1_ABI = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"n\"," +
			"\"type\":\"uint256\"}],\"name\":\"log1\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String LOGS_LOG2_ABI = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"n0\"," +
			"\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"n1\",\"type\":\"uint256\"}],\"name\":\"log2\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String LOGS_LOG3_ABI = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"n0\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"n1\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"n2\",\"type\":\"uint256\"}],\"name\":\"log3\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String LOGS_LOG4_ABI = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"n0\"," +
			"\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"n1\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"n2\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"n3\",\"type\":\"uint256\"}],\"name\":\"log4\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	/* ABI for GlobalProperties.sol */
	public static final String GLOBAL_PROPERTIES_CHAIN_ID_ABI = "{\"inputs\":[],\"name\":\"getChainID\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String GLOBAL_PROPERTIES_BASE_FEE_ABI = "{\"inputs\":[],\"name\":\"getBaseFee\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String GLOBAL_PROPERTIES_COINBASE_ABI = "{\"inputs\":[],\"name\":\"getCoinbase\"," +
			"\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String GLOBAL_PROPERTIES_GASLIMIT_ABI = "{\"inputs\":[],\"name\":\"getGasLimit\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String BALANCE_CHECKER_BALANCE_OF = "{ \"constant\": true, " +
			"\"inputs\": [ { \"name\": \"_address\", \"type\": \"address\" } ], \"name\": \"balanceOf\", " +
			"\"outputs\": [ { \"name\": \"\", \"type\": \"uint256\" } ], " +
			"\"payable\": false, \"stateMutability\": \"view\", \"type\": \"function\" }";

	public static final String EXT_CODE_OP_CHECKER_SIZE_OF = "{ \"constant\": true, " +
			"\"inputs\": [ { \"internalType\": \"address\", \"name\": \"_address\", \"type\": \"address\" } ], " +
			"\"name\": \"sizeOf\", \"outputs\": [ { \"internalType\": \"uint256\", \"name\": \"size\", \"type\": " +
			"\"uint256\" } ], \"payable\": false, \"stateMutability\": \"view\", \"type\": \"function\" }";

	public static final String EXT_CODE_OP_CHECKER_HASH_OF = "{ \"constant\": true, " +
			"\"inputs\": [ { \"internalType\": \"address\", \"name\": \"_address\", \"type\": \"address\" } ], " +
			"\"name\": \"hashOf\", \"outputs\": [ { \"internalType\": \"bytes32\", \"name\": \"hash\", \"type\": " +
			"\"bytes32\" } ], \"payable\": false, \"stateMutability\": \"view\", \"type\": \"function\" }";

	public static final String EXT_CODE_OP_CHECKER_CODE_COPY_OF = "{ \"constant\": true, " +
			"\"inputs\": [ { \"internalType\": \"address\", \"name\": \"_address\", \"type\": " +
			"\"address\" } ], \"name\": \"codeCopyOf\", \"outputs\": [ { \"internalType\": \"bytes\", " +
			"\"name\": \"code\", \"type\": \"bytes\" } ], \"payable\": false, \"stateMutability\": \"view\", " +
			"\"type\": \"function\" }";

	public static final String CALL_CODE_OP_CHECKER_ABI = "{ \"constant\": false, " +
			"\"inputs\": [ { \"name\": \"_address\", \"type\": \"address\" } ], \"name\": \"callCode\", " +
			"\"outputs\": [], \"payable\": true, \"stateMutability\": \"payable\", \"type\": \"function\" }";

	public static final String CALL_OP_CHECKER_ABI = "{ \"constant\": false, " +
			"\"inputs\": [ { \"name\": \"_address\", \"type\": \"address\" } ], \"name\": \"call\", " +
			"\"outputs\": [], \"payable\": true, \"stateMutability\": \"payable\", \"type\": \"function\" }";

	public static final String DELEGATE_CALL_OP_CHECKER_ABI = "{ \"constant\": false, " +
			"\"inputs\": [ { \"name\": \"_address\", \"type\": \"address\" } ], \"name\": \"delegateCall\", " +
			"\"outputs\": [], \"payable\": true, \"stateMutability\": \"payable\", \"type\": \"function\" }";

	public static final String TRANSFER_NFT_ORDINARY_CALL = "{\"inputs\":[{\"internalType\":\"address\",\"name" +
			"\":\"token\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"sender\",\"type" +
			"\":\"address\"},{\"internalType\":\"address\",\"name\":\"receiver\",\"type\":\"address\"},{\"" +
			"internalType\":\"int64\",\"name\":\"serialNum\",\"type\":\"int64\"}],\"name\":\"transferNFTCall" +
			"\",\"outputs\":[{\"internalType\":\"int256\",\"name\":\"responseCode\",\"type\":\"int256\"}],\"" +
			"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String MINT_TOKEN_ORDINARY_CALL = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"token\",\"type\":\"address\"},{\"internalType\":\"uint64\",\"name\":\"amount\"," +
			"\"type\":\"uint64\"},{\"internalType\":\"bytes[]\",\"name\":\"metadata\",\"type\":\"bytes[]\"}]," +
			"\"name\":\"mintTokenCall\",\"outputs\":[{\"internalType\":\"int256\",\"name\":\"responseCode\",\"type\":\"int256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String ASSOCIATE_TOKEN_ORDINARY_CALL = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"account\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"token\",\"type\":\"address\"}],\"name\":\"associateTokenCall\",\"outputs\":[{\"internalType\":\"int256\",\"name\":\"responseCode\",\"type\":\"int256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String DISSOCIATE_TOKEN_ORDINARY_CALL = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"account\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"token\",\"type\":\"address\"}],\"name\":\"dissociateTokenCall\",\"outputs\":[{\"internalType\":\"int256\",\"name\":\"responseCode\",\"type\":\"int256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String BURN_TOKEN_ORDINARY_CALL = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"token\",\"type\":\"address\"},{\"internalType\":\"uint64\",\"name\":\"amount\",\"type\":\"uint64\"},{\"internalType\":\"int64[]\",\"name\":\"serialNumbers\",\"type\":\"int64[]\"}],\"name\":\"burnTokenCall\",\"outputs\":[{\"internalType\":\"int256\",\"name\":\"responseCode\",\"type\":\"int256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";


	public static final String STATIC_CALL_OP_CHECKER_ABI = "{ \"constant\": false, " +
			"\"inputs\": [ { \"name\": \"_address\", \"type\": \"address\" } ], \"name\": \"staticcall\", " +
			"\"outputs\": [], \"payable\": true, \"stateMutability\": \"payable\", \"type\": \"function\" }";

	public static final String NESTED_CHILDREN_CALL_CREATE_ABI = "{ \"inputs\": [], \"name\": \"callCreate\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

    public static final String FACTORY_QUICK_SELF_DESTRUCT_CREATE_AND_DELETE_ABI = "{" +
			"\"inputs\":[],\"name\":\"createAndDeleteChild\"," +
			"\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String ERC20_ABI = "{ \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"initialSupply\", \"type\": \"uint256\" } ], \"stateMutability\": \"nonpayable\", \"type\": \"constructor\" }";
	public static final String ERC20_APPROVE_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"spender\", \"type\": \"address\" }, { \"internalType\": \"uint256\", \"name\": \"amount\", \"type\": \"uint256\" } ]," +
			" \"name\": \"approve\", \"outputs\": [ { \"internalType\": \"bool\", \"name\": \"\", \"type\": \"bool\" } ], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String ERC20_TRANSFER_FROM_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"recipient\", \"type\": \"address\" }," +
			" { \"internalType\": \"uint256\", \"name\": \"amount\", \"type\": \"uint256\" } ], \"name\": \"transferFrom\", \"outputs\": [ { \"internalType\": \"bool\", \"name\": \"\", \"type\": \"bool\" } ], \"stateMutability\": \"nonpayable\"," +
			" \"type\": \"function\" }";
	public static final String ERC20_TRANSFER_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"recipient\", \"type\": \"address\" }, { \"internalType\": \"uint256\", \"name\": \"amount\", \"type\": \"uint256\" } ]," +
			" \"name\": \"transfer\", \"outputs\": [ { \"internalType\": \"bool\", \"name\": \"\", \"type\": \"bool\" } ], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	/* ABI for FactoryContract.sol */
	public static final String FACTORY_CONTRACT_SUCCESS = "{ \"constant\": false, \"inputs\": [], \"name\": \"deploymentSuccess\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String FACTORY_CONTRACT_FAILURE_AFTER_DEPLOY = "{ \"constant\": false, \"inputs\": [], \"name\": \"failureAfterDeploy\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String FACTORY_CONTRACT_FAILURE = "{ \"constant\": false, \"inputs\": [], \"name\": \"deploymentFailure\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String FACTORY_CONTRACT_STACKED_DEPLOYMENT_SUCCESS = "{ \"constant\": false, \"inputs\": [], " +
			"\"name\": \"stackedDeploymentSuccess\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String FACTORY_CONTRACT_STACKED_DEPLOYMENT_FAILURE = "{ \"constant\": false, \"inputs\": [], " +
			"\"name\": \"stackedDeploymentFailure\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String TRANSFERRING_CONTRACT_TRANSFERTOADDRESS = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"address payable\",\"name\": \"_address\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"_amount\",\"type\": \"uint256\"}]," +
			"\"name\": \"transferToAddress\",\"outputs\": [],\"payable\": false," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String TRANSFER_TO_ADDRESS_MULTIPLE_TIMES = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"address payable\",\"name\": \"_address\",\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"_amount\"," +
			"\"type\": \"uint256\"}],\"name\": \"transferToAddressMultipleTimes\",\"outputs\": [],\"payable\": true,\"stateMutability\": \"payable\",\"type\": \"function\"}";
	public static final String TRANSFER_TO_DIFFERENT_ADDRESSES = "{\"constant\": false,\"inputs\": [{\"internalType\": \"address payable\",\"name\": \"receiver1\"," +
			"\"type\": \"address\"},{\"internalType\": \"address payable\",\"name\": \"receiver2\",\"type\": \"address\"},{\"internalType\": \"address payable\"," +
			"\"name\": \"receiver3\",\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"_amount\",\"type\": \"uint256\"}],\"name\": \"transferToDifferentAddresses\",\"outputs\": [],\"payable\": true,\"stateMutability\": \"payable\",\"type\": \"function\"}";
	public static final String TRANSFER_NEGATIVE_AMOUNT = "{\"constant\": false,\"inputs\": [{\"internalType\": \"address payable\",\"name\": \"_address\"," +
			"\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"_amount\",\"type\": \"uint256\"}],\"name\": \"transferToAddressNegativeAmount\",\"outputs\": []," +
			"\"payable\": true,\"stateMutability\": \"payable\",\"type\": \"function\"}";
	public static final String TRANSFER_TO_CALLER = "{\"constant\": false,\"inputs\": [{\"internalType\": \"uint256\",\"name\": \"_amount\"," +
			"\"type\": \"uint256\"}],\"name\": \"transferToCaller\",\"outputs\": [],\"payable\": true,\"stateMutability\": \"payable\",\"type\": \"function\"}";

	public static final String NESTED_TRANSFERRING_CONTRACT_CONSTRUCTOR = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"_nestedContract1\"," +
			"\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"_nestedContract2\",\"type\": \"address\"}],\"payable\": true,\"stateMutability\": \"payable\"," +
			"\"type\": \"constructor\"}";
	public static final String TRANSFER_FROM_DIFFERENT_ADDRESSES_TO_ADDRESS = "{\"constant\": false,\"inputs\": [{\"internalType\": \"address payable\",\"name\": \"_address\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"_amount\",\"type\": \"uint256\"}],\"name\": \"transferFromDifferentAddressesToAddress\",\"outputs\": [],\"payable\": true,\"stateMutability\": \"payable\",\"type\": \"function\"}";
	public static final String TRANSFER_FROM_AND_TO_DIFFERENT_ADDRESSES = "{\"constant\": false,\"inputs\": [{\"internalType\": \"address payable\",\"name\": \"receiver1\",\"type\": \"address\"}," +
			"{\"internalType\": \"address payable\",\"name\": \"receiver2\",\"type\": \"address\"},{\"internalType\": \"address payable\",\"name\": \"receiver3\",\"type\": \"address\"},{\"internalType\": \"uint256\"," +
			"\"name\": \"_amount\",\"type\": \"uint256\"}],\"name\": \"transferFromAndToDifferentAddresses\",\"outputs\": [],\"payable\": true,\"stateMutability\": \"payable\",\"type\": \"function\"}";
	public static final String TRANSFER_TO_CONTRACT_FROM_DIFFERENT_ADDRESSES = "{\"constant\": false,\"inputs\": [{\"internalType\": \"uint256\",\"name\": \"_amount\",\"type\": \"uint256\"}]," +
			"\"name\": \"transferToContractFromDifferentAddresses\",\"outputs\": [],\"payable\": true,\"stateMutability\": \"payable\",\"type\": \"function\"}";
	public static final String TRANSFER_TO_CALLER_FROM_DIFFERENT_ADDRESSES = "{\"constant\": false,\"inputs\": [{\"internalType\": \"uint256\",\"name\": \"_amount\",\"type\": \"uint256\"}]," +
			"\"name\": \"transferToCallerFromDifferentAddresses\",\"outputs\": [],\"payable\": true,\"stateMutability\": \"payable\",\"type\": \"function\"},";

	public static final String PARENT_CHILD_TRANSFER_TRANSFER_TO_CHILD_ABI = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_amount\",\"type\": \"uint256\"}]," +
			"\"name\": \"transferToChild\",\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\"," +
			"\"type\": \"function\"}";
	public static final String TEMPORARY_SSTORE_HOLD_TEMPORARY_ABI = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_tempValue\",\"type\": \"uint256\"}],\"name\": \"holdTemporary\"," +
			"\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String TEMPORARY_SSTORE_HOLD_PERMANENTLY_ABI = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_permanentValue\",\"type\": \"uint256\"}],\"name\": \"holdPermanently\"," +
			"\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	
	public static final String ERC_1155_ABI_SAFE_TRANSFER_FROM = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"from\",\"type\":\"address\"}," +
			"{\"internalType\":\"address\",\"name\":\"to\",\"type\":\"address\"},{\"internalType\":\"uint256\",\"name\":\"id\",\"type\":\"uint256\"}," +
			"{\"internalType\":\"uint256\",\"name\":\"amount\",\"type\":\"uint256\"},{\"internalType\":\"bytes\",\"name\":\"data\",\"type\":\"bytes\"}]" +
			",\"name\":\"safeTransferFrom\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String ERC_1155_ABI_APPROVE = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"operator\",\"type\":\"address\"}," +
			"{\"internalType\":\"bool\",\"name\":\"approved\",\"type\":\"bool\"}],\"name\":\"setApprovalForAll\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String ERC_1155_ABI_MINT = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"tokenType\",\"type\":\"uint256\"}" +
			",{\"internalType\":\"uint256\",\"name\":\"amount\",\"type\":\"uint256\"},{\"internalType\":\"address\",\"name\":\"recipient\",\"type\":\"address\"}]" +
			",\"name\":\"mintToken\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String ZENOS_BANK_WITHDRAW_TOKENS = "{\"constant\": false," +
			"\"inputs\": [],\"name\": \"withdrawTokens\"," +
			"\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ZENOS_BANK_DEPOSIT_TOKENS = "{\"constant\": false," +
			"\"inputs\": [{\"name\": \"amount\",\"type\": \"int64\"}],\"name\": \"depositTokens\"," +
			"\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ZENOS_BANK_CONSTRUCTOR = "{" +
			"\"inputs\": [{\"name\": \"_tokenAddress\",\"type\": \"address\"}]," +
			"\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";

	public static final String HBAR_FEE_COLLECTOR_CONSTRUCTOR = "{\"inputs\": [{\"internalType\": \"address\"," +
			"\"name\": \"transferrerContractAddress\",\"type\": \"address\"}],\"stateMutability\": \"nonpayable\"," +
			"\"type\": \"constructor\"}";

	public static final String HBAR_FEE_COLLECTOR_DISTRIBUTE = "{\"inputs\": [{\"internalType\": \"address\"," +
			"\"name\": \"_tokenAddress\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"_sender\"," +
			"\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"_tokenReceiver\",\"type\": " +
			"\"address\"},{\"internalType\": \"address payable\",\"name\": \"_hbarReceiver\",\"type\": \"address\"}," +
			"{\"internalType\": \"int64\",\"name\": \"_tokenAmount\",\"type\": \"int64\"},{\"internalType\": " +
			"\"uint256\",\"name\": \"_hbarAmount\",\"type\": \"uint256\"}],\"name\": " +
			"\"feeDistributionAfterTransfer\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String VERSATILE_TRANSFERS_CONSTRUCTOR = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"feeDistributorContractAddress\",\"type\": \"address\"}],\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";
	public static final String VERSATILE_TRANSFERS_TOKENS = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"},{\"internalType\": \"address[]\",\"name\": \"accounts\",\"type\": \"address[]\"},{\"internalType\": \"int64[]\",\"name\": \"amounts\",\"type\": \"int64[]\"}],\"name\": \"distributeTokens\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String VERSATILE_TRANSFERS_NFTS = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"},{\"internalType\": \"address[]\",\"name\": \"sender\",\"type\": \"address[]\"},{\"internalType\": \"address[]\",\"name\": \"receiver\",\"type\": \"address[]\"},{\"internalType\": \"int64[]\",\"name\": \"serialNumber\",\"type\": \"int64[]\"}],\"name\": \"transferNfts\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String VERSATILE_TRANSFERS_NFT = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"sender\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"receiver\",\"type\": \"address\"},{\"internalType\": \"int64\",\"name\": \"serialNum\",\"type\": \"int64\"}],\"name\": \"transferNft\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String VERSATILE_TRANSFERS_DISTRIBUTE = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"feeTokenAddress\",\"type\": \"address\"},{\"internalType\": \"address[]\",\"name\": \"accounts\",\"type\": \"address[]\"},{\"internalType\": \"int64[]\",\"name\": \"amounts\",\"type\": \"int64[]\"},{\"internalType\": \"address\",\"name\": \"feeCollector\",\"type\": \"address\"}],\"name\": \"feeDistributionAfterTransfer\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String VERSATILE_TRANSFERS_DISTRIBUTE_STATIC_NESTED_CALL = "{\"inputs\": [{\"internalType\": " +
			"\"address\",\"name\": \"tokenAddress\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"feeTokenAddress\",\"type\": \"address\"},{\"internalType\": \"address[]\",\"name\": \"accounts\",\"type\": \"address[]\"},{\"internalType\": \"int64[]\",\"name\": \"amounts\",\"type\": \"int64[]\"},{\"internalType\": \"address\",\"name\": \"feeCollector\",\"type\": \"address\"}],\"name\": \"feeDistributionAfterTransferStaticNestedCall\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String TRANSFER_AMOUNT_AND_TOKEN_CONSTRUCTOR = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"_tokenAddress\",\"type\": \"address\"}],\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";
	public static final String TRANSFER_AMOUNT_AND_TOKEN_TRANSFER_TO_ADDRESS = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"_address\",\"type\": \"address\"}, {\"internalType\": \"address\",\"name\": \"_address2\",\"type\": \"address\"}," +
			"{\"internalType\": \"int64\",\"name\": \"serialNum\",\"type\": \"int64\"}, {\"internalType\": \"int64\",\"name\": \"serialNum2\",\"type\": \"int64\"}]," +
			"\"name\": \"transferToAddress\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String MUSICAL_CHAIRS_CONSTRUCTOR = "{" +
			"\"inputs\": [{\"internalType\": \"address\",\"name\": \"_dj\",\"type\": \"address\"}]," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";
	public static final String MUSICAL_CHAIRS_SIT_DOWN ="{" +
			"\"inputs\": [],\"name\": \"sitDown\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String MUSICAL_CHAIRS_START_MUSIC = "{" +
			"\"inputs\": [],\"name\": \"startMusic\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String MUSICAL_CHAIRS_STOP_MUSIC = "{" +
			"\"inputs\": [],\"name\": \"stopMusic\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String MUSICAL_CHAIRS_WHO_IS_ON_THE_BUBBLE = "{" +
			"\"inputs\": [],\"name\": \"whoIsOnTheBubble\"," +
			"\"outputs\": [{\"internalType\": \"address\",\"name\": \"hotSeatAddress\",\"type\": \"address\"}]," +
			"\"stateMutability\": \"view\",\"type\": \"function\"}";
        public static final String ASSOCIATE_DISSOCIATE_CONSTRUCTOR = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"_tokenAddress\", \"type\": \"address\" } ], \"stateMutability\": \"nonpayable\", " +
			"\"type\": \"constructor\" }";

	public static final String SINGLE_TOKEN_ASSOCIATE = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": " +
			"\"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"tokenAddress\", " +
			"\"type\": \"address\" } ], \"name\": \"tokenAssociate\", \"outputs\": [], \"stateMutability\": " +
			"\"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_MINT_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"token\"," +
			" \"type\": \"address\" }, { \"internalType\": \"uint64\", \"name\": \"amount\", \"type\": \"uint64\" }, " +
			"{ \"internalType\": \"bytes[]\", \"name\": \"metadata\", \"type\": \"bytes[]\" } ], " +
			"\"name\": \"safeTokenMint\", \"outputs\": [ { \"internalType\": \"uint64\", \"name\": \"newTotalSupply\", " +
			"\"type\": \"uint64\" }, { \"internalType\": \"int256[]\", \"name\": \"serialNumbers\", " +
			"\"type\": \"int256[]\" } ], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_BURN_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"token\"," +
			" \"type\": \"address\" }, { \"internalType\": \"uint64\", \"name\": \"amount\", \"type\": \"uint64\" }, " +
			"{ \"internalType\": \"int64[]\", \"name\": \"serialNumbers\", \"type\": \"int64[]\" } ], " +
			"\"name\": \"safeTokenBurn\", \"outputs\": [ { \"internalType\": \"uint64\", \"name\": \"newTotalSupply\", " +
			"\"type\": \"uint64\" } ], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_ASSOCIATE_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": " +
			"\"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"tokenAddress\", " +
			"\"type\": \"address\" } ], \"name\": \"safeTokenAssociate\", \"outputs\": [], \"stateMutability\": " +
			"\"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_DISSOCIATE_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": " +
			"\"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"tokenAddress\", " +
			"\"type\": \"address\" } ], \"name\": \"safeTokenDissociate\", \"outputs\": [], \"stateMutability\": " +
			"\"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_MULTIPLE_ASSOCIATE_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"account\", \"type\": \"address\" }, { \"internalType\": \"address[]\", \"name\": \"tokens\", " +
			"\"type\": \"address[]\" } ], \"name\": \"safeTokensAssociate\", \"outputs\": [], \"stateMutability\": " +
			"\"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_MULTIPLE_DISSOCIATE_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"account\", \"type\": \"address\" }, { \"internalType\": \"address[]\", \"name\": \"tokens\", " +
			"\"type\": \"address[]\" } ], \"name\": \"safeTokensDissociate\", \"outputs\": [], \"stateMutability\": " +
			"\"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_NFT_TRANSFER_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"token\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"sender\", " +
			"\"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"receiver\", \"type\": \"address\" }, " +
			"{ \"internalType\": \"int64\", \"name\": \"serialNum\", \"type\": \"int64\" } ], " +
			"\"name\": \"safeNFTTransfer\", \"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_TOKEN_TRANSFER_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"token\"," +
			" \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"sender\", \"type\": \"address\" }, " +
			"{ \"internalType\": \"address\", \"name\": \"receiver\", \"type\": \"address\" }, " +
			"{ \"internalType\": \"int64\", \"name\": \"amount\", \"type\": \"int64\" } ], " +
			"\"name\": \"safeTokenTransfer\", \"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_TOKENS_TRANSFER_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"token\", \"type\": \"address\" }, { \"internalType\": \"address[]\", \"name\": \"accountIds\", " +
			"\"type\": \"address[]\" }, { \"internalType\": \"int64[]\", \"name\": \"amounts\", \"type\": \"int64[]\" } ], " +
			"\"name\": \"safeTokensTransfer\", \"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String SAFE_NFTS_TRANSFER_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"token\", \"type\": \"address\" }, { \"internalType\": \"address[]\", \"name\": \"sender\", " +
			"\"type\": \"address[]\" }, { \"internalType\": \"address[]\", \"name\": \"receiver\", " +
			"\"type\": \"address[]\" }, { \"internalType\": \"int64[]\", \"name\": \"serialNumber\", " +
			"\"type\": \"int64[]\" } ], \"name\": \"safeNFTsTransfer\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String NON_SUPPORTED_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": " +
			"\"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"tokenAddress\", " +
			"\"type\": \"address\" } ], \"name\": \"nonSupportedFunction\", \"outputs\": [], \"stateMutability\": " +
			"\"nonpayable\", \"type\": \"function\" }";

	public static final String SINGLE_TOKEN_DISSOCIATE = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": " +
			"\"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": \"tokenAddress\"," +
			" \"type\": \"address\" } ], \"name\": \"tokenDissociate\", \"outputs\": [], \"stateMutability\": " +
			"\"nonpayable\", \"type\": \"function\" }";

	public static final String MULTIPLE_TOKENS_ASSOCIATE = "{ \"inputs\": [ { \"internalType\": \"address\"," +
			" \"name\": \"account\", \"type\": \"address\" }, { \"internalType\": \"address[]\", \"name\": " +
			"\"tokens\", \"type\": \"address[]\" } ], \"name\": \"tokensAssociate\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String ASSOCIATE_TRY_CATCH_CONSTRUCTOR = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"_tokenAddress\", \"type\": \"address\" } ], \"stateMutability\": \"nonpayable\", " +
			"\"type\": \"constructor\" }";

	public static final String ASSOCIATE_TOKEN = "{ \"inputs\": [], \"name\": \"associateToken\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String MULTIPLE_TOKENS_DISSOCIATE = "{ \"inputs\": [ { \"internalType\": \"address\"," +
			"\"name\": \"account\", \"type\": \"address\" }, { \"internalType\": \"address[]\", \"name\": " +
			"\"tokens\", \"type\": \"address[]\" } ], \"name\": \"tokensDissociate\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR = "{ \"inputs\": " +
			"[ { \"internalType\": \"address\", \"name\": \"associateDissociateContractAddress\", \"type\":" +
			" \"address\" } ], \"stateMutability\": \"nonpayable\", \"type\": \"constructor\" }";

	public static final String NESTED_TOKEN_ASSOCIATE = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": " +
			"\"tokenAddress\", \"type\": \"address\" } ], \"name\": \"associateDissociateContractCall\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String NESTED_TOKEN_DISSOCIATE = "{ \"inputs\": [ { \"internalType\": " +
			"\"address\", \"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", " +
			"\"name\": \"tokenAddress\", \"type\": \"address\" } ], \"name\": \"dissociateAssociateContractCall\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String STATIC_ASSOCIATE_CALL_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", " +
			"\"name\": \"tokenAddress\", \"type\": \"address\" } ], \"name\": \"associateStaticCall\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String STATIC_DISSOCIATE_CALL_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", " +
			"\"name\": \"tokenAddress\", \"type\": \"address\" } ], \"name\": \"dissociateStaticCall\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String DELEGATE_ASSOCIATE_CALL_ABI = "{ \"inputs\": [ { \"internalType\": \"address\"," +
			" \"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", " +
			"\"name\": \"tokenAddress\", \"type\": \"address\" } ], \"name\": \"associateDelegateCall\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String DELEGATE_DISSOCIATE_CALL_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", " +
			"\"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", " +
			"\"name\": \"tokenAddress\", \"type\": \"address\" } ], \"name\": \"dissociateDelegateCall\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String PERFORM_NON_EXISTING_FUNCTION_CALL_ABI = "{ \"inputs\": [ { \"internalType\": " +
			"\"address\", \"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": " +
			"\"tokenAddress\", \"type\": \"address\" } ], \"name\": \"performNonExistingServiceFunctionCall\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String PERFORM_INVALIDLY_FORMATTED_SINGLE_FUNCTION_CALL_ABI = "{ \"inputs\": [ " +
			"{ \"internalType\": \"address\", \"name\": \"sender\", \"type\": \"address\" } ], " +
			"\"name\": \"performInvalidlyFormattedSingleFunctionCall\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String PERFORM_INVALIDLY_FORMATTED_FUNCTION_CALL_ABI = "{ \"inputs\": [ { \"internalType\": " +
			"\"address\", \"name\": \"account\", \"type\": \"address\" }, { \"internalType\": \"address[]\", \"name\": " +
			"\"tokens\", \"type\": \"address[]\" } ], \"name\": \"performInvalidlyFormattedFunctionCall\", \"outputs\": " +
			"[], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String PERFORM__FUNCTION_CALL_WITH_LESS_THAN_FOUR_BYTES_ABI = "{ \"inputs\": [ { \"internalType\": " +
			"\"address\", \"name\": \"sender\", \"type\": \"address\" }, { \"internalType\": \"address\", \"name\": " +
			"\"token\", \"type\": \"address\" } ], \"name\": \"performLessThanFourBytesFunctionCall\", \"outputs\": " +
			"[], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String ASSOCIATE_TRY_CATCH_ASSOCIATE_TOKEN = "{ \"inputs\": [], \"name\": \"associateToken\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String DELEGATE_CONTRACT_CONSTRUCTOR = "{\"inputs\": [{\"internalType\": \"address\", " +
			"\"name\": \"serviceContractAddress\",\"type\": \"address\"}],\"stateMutability\": \"nonpayable\", " +
			"\"type\": \"constructor\"}";

	public static final String DELEGATE_TRANSFER_CALL_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\"," +
			"\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"sender\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"receiver\",\"type\": \"address\"}," +
			"{\"internalType\": \"int64\",\"name\": \"serialNum\",\"type\": \"int64\"}]," +
			"\"name\": \"transferDelegateCall\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String DELEGATE_BURN_CALL_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"},{\"internalType\": \"int64[]\",\"name\": \"serialNumbers\"," +
			"\"type\": \"int64[]\"}],\"name\": \"burnDelegateCall\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String DELEGATE_MINT_CALL_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}]," +
			"\"name\": \"mintDelegateCall\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String STATIC_CONTRACT_CONSTRUCTOR = "{\"inputs\": [{\"internalType\": \"address\", " +
			"\"name\": \"serviceContractAddress\",\"type\": \"address\"}],\"stateMutability\": \"nonpayable\", " +
			"\"type\": \"constructor\"}";

	public static final String STATIC_TRANSFER_CALL_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\"," +
			"\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"sender\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"receiver\",\"type\": \"address\"}," +
			"{\"internalType\": \"int64\",\"name\": \"serialNum\",\"type\": \"int64\"}]," +
			"\"name\": \"transferStaticCall\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String STATIC_BURN_CALL_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"},{\"internalType\": \"int64[]\",\"name\": \"serialNumbers\"," +
			"\"type\": \"int64[]\"}],\"name\": \"burnStaticCall\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String STATIC_MINT_CALL_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}]," +
			"\"name\": \"mintStaticCall\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";


	public static String bytecodePath(String bytecode) {
		return String.format("src/main/resource/contract/bytecodes/%s.bin", bytecode);
	}

	public static ByteString literalInitcodeFor(final String contract) {
		try {
			return ByteString.copyFrom(Files.readAllBytes(Paths.get(bytecodePath(contract))));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
