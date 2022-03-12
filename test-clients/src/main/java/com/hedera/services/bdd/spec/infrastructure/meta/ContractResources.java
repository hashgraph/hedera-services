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
	public static final String DELEGATING_CONTRACT_BYTECODE_PATH = bytecodePath("CreateTrivial");
	public static final String BALANCE_LOOKUP_BYTECODE_PATH = bytecodePath("BalanceLookup");
	public static final String CIRCULAR_TRANSFERS_BYTECODE_PATH = bytecodePath("CircularTransfers");
	public static final String VALID_BYTECODE_PATH = HapiSpecSetup.getDefaultInstance().defaultContractPath();
	public static final String VERBOSE_DEPOSIT_BYTECODE_PATH = bytecodePath("VerboseDeposit");
	public static final String BIG_ARRAY_BYTECODE_PATH = bytecodePath("BigArray");
	public static final String EMIT_EVENT_BYTECODE_PATH = bytecodePath("EmitEvent");
	public static final String FUSE_BYTECODE_PATH = bytecodePath("Fuse");
	public static final String LAST_TRACKING_SENDER_BYTECODE_PATH = bytecodePath("LastTrackingSender");
	public static final String MULTIPURPOSE_BYTECODE_PATH = bytecodePath("Multipurpose");
	public static final String CHILD_STORAGE_BYTECODE_PATH = bytecodePath("ChildStorage");
	public static final String DOUBLE_SEND_BYTECODE_PATH = bytecodePath("DoubleSend");
	public static final String BENCHMARK_CONTRACT = bytecodePath("Benchmark");
	public static final String IMAP_USER_BYTECODE_PATH = bytecodePath("User");
	public static final String FIBONACCI_PLUS_PATH = bytecodePath("FibonacciPlus");
	public static final String TRACEABILITY_RECURSIVE_CALLS = bytecodePath("Traceability");
	public static final String TRACEABILITY_RECURSIVE_CALLS_CALLCODE = bytecodePath("TraceabilityCallcode");
	public static final String CREATE_DONOR_PATH = bytecodePath("CreateDonor");

	public static final String RELINQUISH_FUNDS_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"beneficiary\",\"type\":\"address\"}],\"name\":\"relinquishFundsTo\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String CREATE_DONOR_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"salt\"," +
			"\"type\":\"bytes32\"}],\"name\":\"buildDonor\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String CREATE_AND_RECREATE_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"salt\"," +
			"\"type\":\"bytes32\"}],\"name\":\"createAndRecreateTest\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String WHAT_IS_FOO_ABI = "{\"inputs\":[],\"name\":\"whatTheFoo\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String PC2_USER_MINT_NFT_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"token_type\",\"type\":\"address\"},{\"internalType\":\"bytes[]\",\"name\":\"metadata\"," +
			"\"type\":\"bytes[]\"}],\"name\":\"mintNft\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String PC2_USER_HELPER_MINT_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"token_type\",\"type\":\"address\"},{\"internalType\":\"bytes[]\",\"name\":\"metadata\"," +
			"\"type\":\"bytes[]\"}],\"name\":\"mintNftViaDelegate\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String TEST_CONTRACT_VACATE_ADDRESS_ABI = "{\"inputs\":[],\"name\":\"vacateAddress\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TEST_CONTRACT_GET_BALANCE_ABI = "{\"inputs\":[],\"name\":\"getBalance\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String SALTING_CREATOR_CREATE_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\",\"n    " +
			"ame\":\"salt\",\"type\":\"bytes32\"}],\"name\":\"createSaltedTestContract\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String RETURN_THIS_ABI = "{\"inputs\":[],\"name\":\"returnThis\"," +
			"\"outputs\":[{\"internalType\":\"uint160\",\"name\":\"\",\"type\":\"uint160\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String CREATE_PLACEHOLDER_ABI = "{\"inputs\":[],\"name\":\"createPlaceholder\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String IMAP_USER_INSERT = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"k\"," +
			"\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"v\",\"type\":\"uint256\"}]," +
			"\"name\":\"insert\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"size\"," +
			"\"type\":\"uint256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String TRACEABILITY_CONSTRUCTOR = "{\"inputs\":[{\"internalType\":\"uint256\"," +
			"\"name\":\"_slot0\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"_slot1\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"_slot2\",\"type\":\"uint256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
	public static final String TRACEABILITY_EET_1 = "{\"inputs\":[{\"internalType\":\"address\"" +
			",\"name\":\"_contractBAddress\",\"type\":\"address\"},{\"internalType\":\"address\"" +
			",\"name\":\"_contractCAddress\",\"type\":\"address\"}],\"name\":\"eetScenario1\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_2 = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"sibling1\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"sibling2\",\"" +
			"type\":\"address\"}],\"name\":\"eetScenario2\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_3 = "{\"inputs\":[{\"internalType\":\"address\"" +
			",\"name\":\"_contractBAddress\",\"type\":\"address\"},{\"internalType\":\"address\"" +
			",\"name\":\"_contractCAddress\",\"type\":\"address\"}],\"name\":\"eetScenario3\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_4 = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"sibling1\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"sibling2\",\"" +
			"type\":\"address\"}],\"name\":\"eetScenario4\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_5 = "{\"inputs\":[{\"internalType\":\"address\"" +
			",\"name\":\"_contractBAddress\",\"type\":\"address\"},{\"internalType\":\"address\"" +
			",\"name\":\"_contractCAddress\",\"type\":\"address\"}],\"name\":\"eetScenario5\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_6 = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"sibling1\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"sibling2\",\"" +
			"type\":\"address\"}],\"name\":\"eetScenario6\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_7 = "{\"inputs\":[{\"internalType\":\"address\"" +
			",\"name\":\"_contractBAddress\",\"type\":\"address\"},{\"internalType\":\"address\"" +
			",\"name\":\"_contractCAddress\",\"type\":\"address\"}],\"name\":\"eetScenario7\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_8 = "{\"inputs\":[{\"internalType\":\"address\"" +
			",\"name\":\"_contractBAddress\",\"type\":\"address\"},{\"internalType\":\"address\"" +
			",\"name\":\"_contractCAddress\",\"type\":\"address\"}],\"name\":\"eetScenario8\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_9 = "{\"inputs\":[{\"internalType\":\"address\"" +
			",\"name\":\"_contractBAddress\",\"type\":\"address\"},{\"internalType\":\"address\"" +
			",\"name\":\"_contractCAddress\",\"type\":\"address\"}],\"name\":\"eetScenario9\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_10 = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"sibling1\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"sibling2\",\"" +
			"type\":\"address\"}],\"name\":\"eetScenario10\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String TRACEABILITY_EET_11 = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"sibling1\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"sibling2\",\"" +
			"type\":\"address\"}],\"name\":\"eetScenario11\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";

	public static final String TWO_SSTORES = "{ \"inputs\": [ { \"internalType\": \"bytes32\", \"name\": " +
			"\"_singleProp\", \"type\": \"bytes32\" } ], \"name\": \"twoSSTOREs\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String FIBONACCI_PLUS_CONSTRUCTOR_ABI = "{\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"numSlots\",\"type\":\"uint32\"}],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"constructor\"}";
	public static final String ADD_NTH_FIB_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32[]\"" +
			",\"name\":\"at\",\"type\":\"uint32[]\"},{\"internalType\":\"uint32\",\"name\":\"n\",\"type\"" +
			":\"uint32\"}],\"name\":\"addNthFib\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String CURRENT_FIB_SLOTS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"currentSlots\"," +
			"\"outputs\":[{\"internalType\":\"uint256[]\",\"name\":\"\",\"type\":\"uint256[]\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";

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
	public static final String VERBOSE_DEPOSIT_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"amount\",\"type\":\"uint32\"}," +
			"{\"internalType\":\"uint32\",\"name\":\"timesForEmphasis\",\"type\":\"uint32\"}," +
			"{\"internalType\":\"string\",\"name\":\"memo\",\"type\":\"string\"}],\"name\":\"deposit\"," +
			"\"outputs\":[]," +
			"\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";

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
