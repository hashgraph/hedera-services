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
	public static final String DELEGATE_CONTRACT = bytecodePath("DelegateContract");
	public static final String ASSOCIATOR_PATH = bytecodePath("Associator");
	public static final String INSTANT_STORAGE_HOG_PATH = bytecodePath("InstantStorageHog");
	public static final String SELF_ASSOC_PATH = bytecodePath("SelfAssociating");

	public static final String INSTANT_HOG_CONS_ABI = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"n\"," +
			"\"type\":\"uint256\"}],\"stateMutability\":\"payable\",\"type\":\"constructor\"}";
	public static final String SELF_ASSOC_CONS_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"tokenAddr\",\"type\":\"address\"}],\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
	public static final String ASSOCIATOR_ASSOCIATE_ABI = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"" +
			"account\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"token\",\"type\":\"address\"}]" +
			",\"name\":\"associate\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

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

	public static final String ADD_TO_WHITELIST_ABI = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"_toBePermitted" +
			"\",\"type\":\"address\"}],\"name\":\"addToWhitelist\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type" +
			"\":\"function\"}";

	public static final String RAW_IS_WHITELISTED_ABI = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type" +
			"\":\"address\"}],\"name\":\"whitelist\",\"outputs\":[{\"internalType\":\"bool\",\"name\":\"\",\"type\":\"" +
			"bool\"}],\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String IS_WHITELISTED_ABI = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"whitelister\"" +
			",\"type\":\"address\"}],\"name\":\"isWhitelisted\",\"outputs\":[{\"internalType\":\"bool\",\"name\":\"\",\"" +
			"type\":\"bool\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";


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
