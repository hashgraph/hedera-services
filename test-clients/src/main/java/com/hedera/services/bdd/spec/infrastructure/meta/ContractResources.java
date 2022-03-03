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

import com.hedera.services.bdd.spec.HapiSpecSetup;

public class ContractResources {
	public static final String SIMPLE_STORAGE_BYTECODE_PATH = bytecodePath("simpleStorage");
	public static final String PAYABLE_CONTRACT_BYTECODE_PATH = bytecodePath("PayReceivable");
	public static final String DELEGATING_CONTRACT_BYTECODE_PATH = bytecodePath("CreateTrivial");
	public static final String BALANCE_LOOKUP_BYTECODE_PATH = bytecodePath("BalanceLookup");
	public static final String CIRCULAR_TRANSFERS_BYTECODE_PATH = bytecodePath("CircularTransfers");
	public static final String INVALID_BYTECODE_PATH = bytecodePath("CorruptOne");
	public static final String VALID_BYTECODE_PATH = HapiSpecSetup.getDefaultInstance().defaultContractPath();
	public static final String VERBOSE_DEPOSIT_BYTECODE_PATH = bytecodePath("VerboseDeposit");
	public static final String GROW_ARRAY_BYTECODE_PATH = bytecodePath("GrowArray");
	public static final String BIG_ARRAY_BYTECODE_PATH = bytecodePath("BigArray");
	public static final String EMIT_EVENT_BYTECODE_PATH = bytecodePath("EmitEvent");
	public static final String FUSE_BYTECODE_PATH = bytecodePath("Fuse");
	public static final String LAST_TRACKING_SENDER_BYTECODE_PATH = bytecodePath("LastTrackingSender");
	public static final String MULTIPURPOSE_BYTECODE_PATH = bytecodePath("Multipurpose");
	public static final String JUST_SEND_BYTECODE_PATH = bytecodePath("JustSend");
	public static final String SEND_INTERNAL_AND_DELEGATE_BYTECODE_PATH = bytecodePath("SendInternalAndDelegate");
	public static final String CHILD_STORAGE_BYTECODE_PATH = bytecodePath("ChildStorage");
	public static final String ABANDONING_PARENT_BYTECODE_PATH = bytecodePath("AbandoningParent");
	public static final String OC_TOKEN_BYTECODE_PATH = bytecodePath("octoken");
	public static final String ADDRESS_BOOK_BYTECODE_PATH = bytecodePath("AddressBook");
	public static final String JURISDICTIONS_BYTECODE_PATH = bytecodePath("Jurisdictions");
	public static final String MINTERS_BYTECODE_PATH = bytecodePath("Minters");
	public static final String PAY_TEST_BYTECODE_PATH = bytecodePath("PayTest");
	public static final String DOUBLE_SEND_BYTECODE_PATH = bytecodePath("DoubleSend");
	public static final String EMPTY_CONSTRUCTOR = bytecodePath("EmptyConstructor");
	public static final String PAYABLE_CONSTRUCTOR = bytecodePath("PayableConstructor");
	public static final String BENCHMARK_CONTRACT = bytecodePath("Benchmark");
	public static final String IMAP_USER_BYTECODE_PATH = bytecodePath("User");
	public static final String CALLING_CONTRACT = bytecodePath("CallingContract");
	public static final String GLOBAL_PROPERTIES = bytecodePath("GlobalProperties");
	public static final String BALANCE_CHECKER_CONTRACT = bytecodePath("BalanceChecker");
	public static final String EXT_CODE_OPERATIONS_CHECKER_CONTRACT = bytecodePath("ExtCodeOperationsChecker");
	public static final String CALL_OPERATIONS_CHECKER = bytecodePath("CallOperationsChecker");
	public static final String FACTORY_CONTRACT = bytecodePath("FactoryContract");
	public static final String NESTED_CHILDREN_CONTRACT = bytecodePath("NestedChildren");
	public static final String FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT = bytecodePath("FactorySelfDestructConstructor");
	public static final String FACTORY_QUICK_SELF_DESTRUCT_CONTRACT = bytecodePath("FactoryQuickSelfDestruct");
	public static final String TEMPORARY_SSTORE_REFUND_CONTRACT = bytecodePath("TemporarySStoreRefund");
	public static final String FIBONACCI_PLUS_PATH = bytecodePath("FibonacciPlus");
	public static final String LARGE_CONTRACT_CRYPTO_KITTIES = bytecodePath("CryptoKitties");
	public static final String SELF_DESTRUCT_CALLABLE = bytecodePath("SelfDestructCallable");
	public static final String REVERTING_SEND_TRY = bytecodePath("RevertingSendTry");
	public static final String MUSICAL_CHAIRS_CONTRACT = bytecodePath("MusicalChairs");
	public static final String SAFE_OPERATIONS_CONTRACT = bytecodePath("SafeOperationsContract");
	public static final String NESTED_CREATIONS_PATH = bytecodePath("NestedCreations");
	public static final String CREATE2_FACTORY_PATH = bytecodePath("Create2Factory");
	public static final String SALTING_CREATOR_FACTORY_PATH = bytecodePath("SaltingCreatorFactory");
	public static final String ADDRESS_VAL_RETURNER_PATH = bytecodePath("AddressValueRet");
	public static final String PRECOMPILE_CREATE2_USER_PATH = bytecodePath("Create2PrecompileUser");
	public static final String REVERTING_CREATE_FACTORY_PATH = bytecodePath("RevertingCreateFactory");
	public static final String REVERTING_CREATE2_FACTORY_PATH = bytecodePath("RevertingCreate2Factory");

	public static final String NORMAL_DEPLOY_ABI = "{\"inputs\":[{\"internalType\":\"bytes\",\"name\":\"bytecode\"," +
			"\"type\":\"bytes\"}],\"name\":\"deploy\",\"outputs\":[],\"stateMutability\":\"payable\"," +
			"\"type\":\"function\"}";
	public static final String CREATE_FACTORY_GET_BYTECODE_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"_owner\",\"type\":\"address\"},{\"internalType\":\"uint256\",\"name\":\"_foo\"," +
			"\"type\":\"uint256\"}],\"name\":\"getBytecode\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\"," +
			"\"type\":\"bytes\"}],\"stateMutability\":\"pure\",\"type\":\"function\"}";
	public static final String PC2_CREATE_USER_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\"," +
			"\"name\":\"salt\",\"type\":\"bytes32\"}],\"name\":\"createUser\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String PC2_ASSOCIATE_BOTH_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"token_type\",\"type\":\"address\"}],\"name\":\"associateBothTo\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String PC2_DISSOCIATE_BOTH_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"token_type\",\"type\":\"address\"}],\"name\":\"dissociateBothFrom\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String PC2_FT_SEND_ABI = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"ft_type\"," +
			"\"type\":\"address\"},{\"internalType\":\"int64\",\"name\":\"amount\",\"type\":\"int64\"}]," +
			"\"name\":\"sendFtToUser\",\"outputs\":[{\"internalType\":\"int256\",\"name\":\"\"," +
			"\"type\":\"int256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String PC2_NFT_SEND_ABI = "{\"inputs\":[{\"internalType\":\"address\",\"name\":\"nft_type\"," +
			"\"type\":\"address\"},{\"internalType\":\"int64\",\"name\":\"sn\",\"type\":\"int64\"}]," +
			"\"name\":\"sendNftToUser\",\"outputs\":[{\"internalType\":\"int256\",\"name\":\"\",\"type\":\"int256\"}]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String PC2_USER_MINT_NFT_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"token_type\",\"type\":\"address\"},{\"internalType\":\"bytes[]\",\"name\":\"metadata\"," +
			"\"type\":\"bytes[]\"}],\"name\":\"mintNft\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String PC2_USER_HELPER_MINT_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"token_type\",\"type\":\"address\"},{\"internalType\":\"bytes[]\",\"name\":\"metadata\"," +
			"\"type\":\"bytes[]\"}],\"name\":\"mintNftViaDelegate\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String CREATE2_FACTORY_DEPLOY_ABI = "{\"inputs\":[{\"internalType\":\"bytes\"," +
			"\"name\":\"bytecode\",\"type\":\"bytes\"},{\"internalType\":\"uint256\",\"name\":\"_salt\",\"type\":" +
			"\"uint256\"}],\"name\":\"deploy\",\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String CREATE2_FACTORY_GET_BYTECODE_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"_owner\",\"type\":\"address\"},{\"internalType\":\"uint256\",\"name\":\"_foo\"," +
			"\"type\":\"uint256\"}],\"name\":\"getBytecode\",\"outputs\":[{\"internalType\":\"bytes\"," +
			"\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"pure\",\"type\":\"function\"}";
	public static final String CREATE2_FACTORY_GET_ADDRESS_ABI = "{\"inputs\":[{\"internalType\":\"bytes\"," +
			"\"name\":\"bytecode\",\"type\":\"bytes\"},{\"internalType\":\"uint256\",\"name\":\"_salt\"," +
			"\"type\":\"uint256\"}],\"name\":\"getAddress\",\"outputs\":[{\"internalType\":\"address\",\"name\":\"\"," +
			"\"type\":\"address\"}],\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String TEST_CONTRACT_VACATE_ADDRESS_ABI = "{\"inputs\":[],\"name\":\"vacateAddress\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TEST_CONTRACT_GET_BALANCE_ABI = "{\"inputs\":[],\"name\":\"getBalance\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String SALTING_CREATOR_FACTORY_BUILD_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\"," +
			"\"name\":\"salt\",\"type\":\"bytes32\"}],\"name\":\"buildCreator\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String SALTING_CREATOR_CREATE_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\",\"n    " +
			"ame\":\"salt\",\"type\":\"bytes32\"}],\"name\":\"createSaltedTestContract\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String SALTING_CREATOR_CALL_CREATOR_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"creator_address\",\"type\":\"address\"},{\"internalType\":\"bytes32\",\"name\":\"salt\"," +
			"\"type\":\"bytes32\"}],\"name\":\"callCreator\",\"outputs\":[],\"stat    eMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String ADDRESS_VAL_CREATE_RETURNER_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\"," +
			"\"name\":\"salt\",\"type\":\"bytes32\"}],\"name\":\"createReturner\",\"outputs\":[],\"stateMutabi" +
			"lity\":\"nonpayable\",\"type\":\"function\"}";
	public static final String ADDRESS_VAL_CALL_RETURNER_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"returner_address\",\"type\":\"address\"}],\"name\":\"callReturner\"," +
			"\"outputs\":[{\"internalType\":\"uint160\",\"name\":\"\",\"type\":\"uint160\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String RETURN_THIS_ABI = "{\"inputs\":[],\"name\":\"returnThis\"," +
			"\"outputs\":[{\"internalType\":\"uint160\",\"name\":\"\",\"type\":\"uint160\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String CREATE_PLACEHOLDER_ABI = "{\"inputs\":[],\"name\":\"createPlaceholder\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String PROPAGATE_NESTED_CREATIONS_ABI = "{\"inputs\":[],\"name\":\"propagate\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String IMAP_USER_INSERT = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"k\"," +
			"\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"v\",\"type\":\"uint256\"}]," +
			"\"name\":\"insert\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"size\"," +
			"\"type\":\"uint256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String SEND_REPEATEDLY_ABI = "{\"inputs\":[{\"internalType\":\"uint64\",\"name\":\"just_send_" +
			"num\",\"type\":\"uint64\"},{\"internalType\":\"uint64\",\"name\":\"account_num\",\"type\":\"uint64\"}," +
			"{\"internalType\":\"uint64\",\"name\":\"value\",\"type\":\"uint64\"}],\"name\":\"sendRepeatedlyTo\"," +
			"\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String SELF_DESTRUCT_CALL_ABI = "{\"inputs\":[],\"name\":\"destroy\",\"outputs\":[]," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String SEND_THEN_REVERT_NESTED_SENDS_ABI = "{\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"amount\",\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"numA\"," +
			"\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"numB\",\"type\":\"uint32\"}]," +
			"\"name\":\"sendTo\",\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String TWO_SSTORES = "{ \"inputs\": [ { \"internalType\": \"bytes32\", \"name\": " +
			"\"_singleProp\", \"type\": \"bytes32\" } ], \"name\": \"twoSSTOREs\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String BENCHMARK_GET_COUNTER = "{ \"inputs\": [], \"name\": \"counter\", \"outputs\": [ " +
			"{ \"internalType\": \"uint256\", \"name\": \"\", \"type\": \"uint256\" } ], " +
			"\"stateMutability\": \"view\", \"type\": \"function\" }";
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
	public static final String GET_CHILD_ADDRESS_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getAddress\"," +
			"\"outputs\":[{\"name\":\"retval\",\"type\":\"address\"}]," +
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

	public static final String GROW_ARRAY_GROW_TO = "{\"constant\": false," +
			"\"inputs\": [{\"internalType\": \"uint256\",\"name\": \"_limit\",\"type\": \"uint256\"}]," +
			"\"name\": \"growTo\",\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\"," +
			"\"type\": \"function\"}";

	public static final String GROW_ARRAY_CHANGE_ARRAY = "{\"constant\": false," +
			"\"inputs\": [{\"internalType\": \"uint256\",\"name\": \"_value\",\"type\": \"uint256\"}]," +
			"\"name\": \"changeArray\",\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\"," +
			"\"type\": \"function\"}";

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

	public static final String SET_ZERO_READ_ONE_ABI = "{\"constant\": false," +
			"\"inputs\": [{\"internalType\": \"uint256\",\"name\": \"_value\",\"type\": \"uint256\"}]," +
			"\"name\": \"setZeroReadOne\",\"outputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_getOne\",\"type\": \"uint256\"}]," +
			"\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String SET_BOTH_ABI = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_value\",\"type\": \"uint256\"}]," +
			"\"name\": \"setBoth\",\"outputs\": [],\"payable\": false," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String GROW_CHILD_ABI = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_childId\",\"type\": \"uint256\"}" +
			",{\"internalType\": \"uint256\",\"name\": \"_howManyKB\",\"type\": \"uint256\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"_value\",\"type\": \"uint256\"}]," +
			"\"name\": \"growChild\",\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\"," +
			"\"type\": \"function\"}";

	public static final String GET_BALANCE_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getBalance\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

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

	public static final String JURISDICTION_CONSTRUCTOR_ABI = "{" +
			"\"inputs\":[{\"name\":\"_admin\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}\n";

	public static final String JURISDICTION_ADD_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"name\",\"type\":\"string\"}," +
			"{\"name\":\"taxRate\",\"type\":\"uint256\"}," +
			"{\"name\":\"inventory\",\"type\":\"address\"}," +
			"{\"name\":\"reserve\",\"type\":\"address\"}],\"name\":\"add\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String JURISDICTION_ISVALID_ABI = "{\"constant\":true," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"isValid\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String JURISDICTION_ABI = "[{\"constant\":true," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"getInventory\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false," +
			"\"inputs\":[{\"name\":\"name\",\"type\":\"string\"}," +
			"{\"name\":\"taxRate\",\"type\":\"uint256\"}," +
			"{\"name\":\"inventory\",\"type\":\"address\"}," +
			"{\"name\":\"reserve\",\"type\":\"address\"}],\"name\":\"add\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}," +
			"{\"name\":\"taxRate\",\"type\":\"uint256\"}," +
			"{\"name\":\"reserve\",\"type\":\"address\"}," +
			"{\"name\":\"inventory\",\"type\":\"address\"}],\"name\":\"setJurisdictionParams\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"getReserve\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"isValid\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[],\"name\":\"owner\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"remove\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"getTaxRate\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"priceCents\",\"type\":\"uint256\"}," +
			"{\"name\":\"code\",\"type\":\"bytes32\"}],\"name\":\"getTaxes\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}," +
			"{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"\",\"type\":\"bytes32\"}],\"name\":\"jurisdictions\"," +
			"\"outputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}," +
			"{\"name\":\"name\",\"type\":\"string\"}," +
			"{\"name\":\"taxRate\",\"type\":\"uint256\"}," +
			"{\"name\":\"inventory\",\"type\":\"address\"}," +
			"{\"name\":\"reserve\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[],\"name\":\"getCodes\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"bytes32[]\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false," +
			"\"inputs\":[{\"name\":\"code\",\"type\":\"bytes32\"}," +
			"{\"name\":\"taxRate\",\"type\":\"uint256\"}],\"name\":\"setTaxRate\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"isBitcarbon\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false," +
			"\"inputs\":[{\"name\":\"newOwner\",\"type\":\"address\"}],\"name\":\"transferOwnership\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"bitcarbonJurisdiction\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"bytes32\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true," +
			"\"inputs\":[{\"name\":\"inventory\",\"type\":\"address\"}],\"name\":\"getPendingTokens\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256[]\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{" +
			"\"inputs\":[{\"name\":\"_admin\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"},{\"anonymous\":false," +
			"\"inputs\":[{\"indexed\":false,\"name\":\"code\",\"type\":\"bytes32\"}," +
			"{\"indexed\":false,\"name\":\"name\",\"type\":\"string\"}," +
			"{\"indexed\":false,\"name\":\"taxRate\",\"type\":\"uint256\"}," +
			"{\"indexed\":false,\"name\":\"inventory\",\"type\":\"address\"}," +
			"{\"indexed\":false,\"name\":\"reserve\",\"type\":\"address\"}," +
			"{\"indexed\":false,\"name\":\"timestamp\",\"type\":\"uint256\"}]," +
			"\"name\":\"JurisdictionAdded\",\"type\":\"event\"},{\"anonymous\":false," +
			"\"inputs\":[{\"indexed\":false,\"name\":\"code\",\"type\":\"bytes32\"}," +
			"{\"indexed\":false,\"name\":\"timestamp\",\"type\":\"uint256\"}]," +
			"\"name\":\"JurisdictionRemoved\",\"type\":\"event\"},{\"anonymous\":false," +
			"\"inputs\":[{\"indexed\":false,\"name\":\"oldTaxRate\",\"type\":\"uint256\"}," +
			"{\"indexed\":false,\"name\":\"newTaxRate\",\"type\":\"uint256\"}," +
			"{\"indexed\":false,\"name\":\"timestamp\",\"type\":\"uint256\"}]," +
			"\"name\":\"TaxRateChanged\",\"type\":\"event\"},{\"anonymous\":false," +
			"\"inputs\":[{\"indexed\":true,\"name\":\"previousOwner\",\"type\":\"address\"}," +
			"{\"indexed\":true,\"name\":\"newOwner\",\"type\":\"address\"}]," +
			"\"name\":\"OwnershipTransferred\",\"type\":\"event\"}]";

	public static final String MINT_CONSTRUCTOR_ABI = "{" +
			"\"inputs\":[{\"name\":\"_jurisdictions\",\"type\":\"address\"}," +
			"{\"name\":\"_admin\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";

	public static final String MINT_ADD_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"minter\",\"type\":\"address\"}," +
			"{\"name\":\"name\",\"type\":\"string\"}," +
			"{\"name\":\"jurisdiction\",\"type\":\"bytes32\"}],\"name\":\"add\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String MINT_SEVEN_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"seven\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String MINT_OWNER_ABI = "{\"constant\":true," +
			"\"inputs\":[],\"name\":\"owner\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"address\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String MINT_CONFIGURE_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"name\":\"_jurisdictions\",\"type\":\"address\"}],\"name\":\"configureJurisdictionContract\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String SEND_TO_TWO_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"toFirst\",\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"toSecond\"," +
			"\"type\":\"uint32\"}],\"name\":\"donate\",\"outputs\":[],\"payable\":true," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String CALLING_CONTRACT_SET_VALUE = "{ \"constant\": false, \"inputs\": [ { \"name\": \"_var1\", \"type\": \"uint256\" } ], \"name\": \"setVar1\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String CALLING_CONTRACT_CALL_CONTRACT = "{ \"constant\": false, \"inputs\": [ { \"name\": \"_addr\", \"type\": \"address\" }, { \"name\": \"_var1\", \"type\": \"uint256\" } ], \"name\": \"callContract\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String CALLING_CONTRACT_VIEW_VAR = "{ \"constant\": true, \"inputs\": [], \"name\": \"getVar1\", \"outputs\": [ { \"name\": \"\", \"type\": \"uint256\" } ], \"payable\": false, \"stateMutability\": \"view\", \"type\": \"function\" }";

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

	public static final String STATIC_CALL_OP_CHECKER_ABI = "{ \"constant\": false, " +
			"\"inputs\": [ { \"name\": \"_address\", \"type\": \"address\" } ], \"name\": \"staticcall\", " +
			"\"outputs\": [], \"payable\": true, \"stateMutability\": \"payable\", \"type\": \"function\" }";

	public static final String NESTED_CHILDREN_CALL_CREATE_ABI = "{ \"inputs\": [], \"name\": \"callCreate\", " +
			"\"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

    public static final String FACTORY_QUICK_SELF_DESTRUCT_CREATE_AND_DELETE_ABI = "{" +
			"\"inputs\":[],\"name\":\"createAndDeleteChild\"," +
			"\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"}";

	/* ABI for FactoryContract.sol */
	public static final String FACTORY_CONTRACT_SUCCESS = "{ \"constant\": false, \"inputs\": [], \"name\": \"deploymentSuccess\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String FACTORY_CONTRACT_FAILURE_AFTER_DEPLOY = "{ \"constant\": false, \"inputs\": [], \"name\": \"failureAfterDeploy\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String FACTORY_CONTRACT_FAILURE = "{ \"constant\": false, \"inputs\": [], \"name\": \"deploymentFailure\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String FACTORY_CONTRACT_STACKED_DEPLOYMENT_SUCCESS = "{ \"constant\": false, \"inputs\": [], " +
			"\"name\": \"stackedDeploymentSuccess\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String FACTORY_CONTRACT_STACKED_DEPLOYMENT_FAILURE = "{ \"constant\": false, \"inputs\": [], " +
			"\"name\": \"stackedDeploymentFailure\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

	public static final String TEMPORARY_SSTORE_HOLD_TEMPORARY_ABI = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_tempValue\",\"type\": \"uint256\"}],\"name\": \"holdTemporary\"," +
			"\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String TEMPORARY_SSTORE_HOLD_PERMANENTLY_ABI = "{\"constant\": false,\"inputs\": " +
			"[{\"internalType\": \"uint256\",\"name\": \"_permanentValue\",\"type\": \"uint256\"}],\"name\": \"holdPermanently\"," +
			"\"outputs\": [],\"payable\": false,\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

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

	public static String bytecodePath(String bytecode) {
		return String.format("src/main/resource/contract/bytecodes/%s.bin", bytecode);
	}
}
