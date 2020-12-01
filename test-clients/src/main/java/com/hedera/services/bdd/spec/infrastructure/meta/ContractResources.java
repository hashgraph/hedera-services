package com.hedera.services.bdd.spec.infrastructure.meta;

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

import com.hedera.services.bdd.spec.HapiSpecSetup;

public class ContractResources {
	public static final String SIMPLE_STORAGE_BYTECODE_PATH = bytecodePath("simpleStorage");
	public static final String PAYABLE_CONTRACT_BYTECODE_PATH = bytecodePath("PayReceivable");
	public static final String DELEGATING_CONTRACT_BYTECODE_PATH = bytecodePath("CreateTrivial");
	public static final String BALANCE_LOOKUP_BYTECODE_PATH = bytecodePath("BalanceLookup");
	public static final String CIRCULAR_TRANSFERS_BYTECODE_PATH = bytecodePath("CircularTransfers");
	public static final String INLINE_TEST_BYTECODE_PATH = bytecodePath("InlineTest");
	public static final String INVALID_BYTECODE_PATH = bytecodePath("CorruptOne");
	public static final String VALID_BYTECODE_PATH = HapiSpecSetup.getDefaultInstance().defaultContractPath();
	public static final String VERBOSE_DEPOSIT_BYTECODE_PATH = bytecodePath("VerboseDeposit");
	public static final String GROW_ARRAY_BYTECODE_PATH = bytecodePath("GrowArray");
	public static final String BIG_ARRAY_BYTECODE_PATH = bytecodePath("BigArray");
	public static final String EMIT_EVENT_BYTECODE_PATH = bytecodePath("EmitEvent");
	public static final String BIG_BIG_BYTECODE_PATH = bytecodePath("BigBig");
	public static final String FUSE_BYTECODE_PATH = bytecodePath("Fuse");
	public static final String LAST_TRACKING_SENDER_BYTECODE_PATH = bytecodePath("LastTrackingSender");
	public static final String MULTIPURPOSE_BYTECODE_PATH = bytecodePath("Multipurpose");
	public static final String CHILD_STORAGE_BYTECODE_PATH = bytecodePath("ChildStorage");
	public static final String ABANDONING_PARENT_BYTECODE_PATH = bytecodePath("AbandoningParent");
	public static final String PAY_TEST_SELF_DESTRUCT_BYTECODE_PATH = bytecodePath("PayTestSelfDestruct");
	public static final String PAY_RECEIVABLE_AMOUNT_BYTECODE_PATH = bytecodePath("PayReceivableAmount");
	public static final String OC_TOKEN_BYTECODE_PATH = bytecodePath("octoken");

	public static final String CREATE_CHILD_ABI = "{\"constant\":false," +
			"\"inputs\":[],\"name\":\"create\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String GET_CHILD_RESULT_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getIndirect\"," +
			"\"outputs\":[{\"name\":\"value\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String GET_CHILD_ADDRESS_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getAddress\"," +
			"\"outputs\":[{\"name\":\"retval\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String SEND_FUNDS_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"receiver\",\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"sendFunds\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String DEPOSIT_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\"," +
			"\"outputs\":[]," +
			"\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String GET_CODE_SIZE_ABI = "{\"constant\":true," +
			"\"inputs\":[{\"name\":\"_addr\",\"type\":\"address\"}],\"name\":\"getCodeSize\"," +
			"\"outputs\":[{\"name\":\"_size\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String GET_STORE_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getStore\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"bytes32\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String SET_STORE_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"inVal\",\"type\":\"bytes32\"}],\"name\":\"setStore\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
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
	public static final String BIG_ARRAY_CHANGE_ARRAY_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"changeArray\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String BIG_ARRAY_GROW_TO_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_limit\",\"type\":\"uint256\"}],\"name\":\"growTo\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String BIG_ARRAY_SET_SIZE_IN_KB_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"_howManyKB\",\"type\":\"uint256\"}],\"name\":\"setSizeInKB\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String PICK_A_BIG_RESULT_ABI = "{\"constant\":true," +
			"\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"how\",\"type\":\"uint32\"}],\"name\":\"pick\"," +
			"\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}]," +
			"\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";
	public static final String LIGHT_ABI = "{\"constant\":false," +
			"\"inputs\":[],\"name\":\"light\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
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
	public static final String GET_CHILD_VALUE_ABI = "{\"constant\":true," +
			"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_childId\",\"type\":\"uint256\"}],\"name\":\"getChildValue\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String SET_ZERO_READ_ONE_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"setZeroReadOne\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_getOne\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String SET_BOTH_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"setBoth\"," +
			"\"outputs\":[],\"" +
			"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String GROW_CHILD_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_childId\",\"type\":\"uint256\"}," +
			"{\"internalType\":\"uint256\",\"name\":\"_howManyKB\",\"type\":\"uint256\"}," +
			"{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"growChild\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String GET_BALANCE_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getBalance\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String KILL_ME_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"beneficiary\",\"type\":\"address\"}],\"name\":\"killMe\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TOKEN_ERC20_CONSTRUCTOR_ABI = "{" +
			"\"inputs\":[{\"name\":\"initialSupply\",\"type\":\"uint256\"},{\"name\":\"tokenName\",\"type\":\"string\"},{\"name\":\"tokenSymbol\",\"type\":\"string\"}]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
	public static final String BALANCE_OF_ABI = "{\"constant\":true," +
			"\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"balanceOf\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String TRANSFER_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transfer\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String APPROVE_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"_spender\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"approve\"," +
			"\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TRANSFER_FROM_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"_from\",\"type\":\"address\"}," +
			"{\"name\":\"_to\",\"type\":\"address\"}," +
			"{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\"," +
			"\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String SYMBOL_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"symbol\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"string\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String DECIMALS_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"decimals\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String bytecodePath(String bytecode) {
		return String.format("src/main/resource/contract/bytecodes/%s.bin", bytecode);
	}

	public static final int CREATED_TRIVIAL_CONTRACT_RETURNS = 7;
}
