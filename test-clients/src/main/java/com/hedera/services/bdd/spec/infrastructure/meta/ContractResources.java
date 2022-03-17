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
	public static final String ABANDONING_PARENT_BYTECODE_PATH = bytecodePath("AbandoningParent");
	public static final String PAY_TEST_SELF_DESTRUCT_BYTECODE_PATH = bytecodePath("PayTestSelfDestruct");
	public static final String PARENT_CHILD_TRANSFER_BYTECODE_PATH = bytecodePath("ParentChildTransfer");
	public static final String OC_TOKEN_BYTECODE_PATH = bytecodePath("octoken");
	public static final String DOUBLE_SEND_BYTECODE_PATH = bytecodePath("DoubleSend");
	public static final String FIBONACCI_PLUS_PATH = bytecodePath("FibonacciPlus");
	public static final String TOP_LEVEL_TRANSFERRING_CONTRACT = bytecodePath("TopLevelTransferringContract");
	public static final String SUB_LEVEL_TRANSFERRING_CONTRACT = bytecodePath("SubLevelTransferringContract");
	public static final String LARGE_CONTRACT_CRYPTO_KITTIES = bytecodePath("CryptoKitties");
	public static final String SELF_DESTRUCT_CALLABLE = bytecodePath("SelfDestructCallable");
	public static final String TRACEABILITY_RECURSIVE_CALLS = bytecodePath("Traceability");
	public static final String TRACEABILITY_RECURSIVE_CALLS_CALLCODE = bytecodePath("TraceabilityCallcode");
	public static final String REVERTING_SEND_TRY = bytecodePath("RevertingSendTry");
	public static final String ZENOS_BANK_CONTRACT = bytecodePath("ZenosBank");
	public static final String ORDINARY_CALLS_CONTRACT = bytecodePath("HTSCalls");
	public static final String VERSATILE_TRANSFERS_CONTRACT = bytecodePath("VersatileTransfers");
	public static final String HBAR_FEE_COLLECTOR = bytecodePath("HbarFeeCollector");
	public static final String NESTED_HTS_TRANSFERRER = bytecodePath("NestedHTSTransferrer");
	public static final String DISTRIBUTOR_CONTRACT = bytecodePath("FeeDistributor");
	public static final String MUSICAL_CHAIRS_CONTRACT = bytecodePath("MusicalChairs");
	public static final String ASSOCIATE_DISSOCIATE_CONTRACT = bytecodePath("AssociateDissociateContract");
	public static final String SAFE_OPERATIONS_CONTRACT = bytecodePath("SafeOperationsContract");
	public static final String NESTED_ASSOCIATE_DISSOCIATE_CONTRACT = bytecodePath("NestedAssociateDissociateContract");
	public static final String MINT_CONTRACT = bytecodePath("MintContract");
	public static final String TRANSFER_AMOUNT_AND_TOKEN_CONTRACT = bytecodePath("TransferAmountAndToken");
	public static final String NESTED_MINT_CONTRACT = bytecodePath("NestedMintContract");
	public static final String CRYPTO_TRANSFER_CONTRACT = bytecodePath("CryptoTransferContract");
	public static final String BURN_TOKEN = bytecodePath("BurnToken");
	public static final String MINT_TOKEN_CONTRACT = bytecodePath("MintTokenContract");
	public static final String MINT_NFT_CONTRACT = bytecodePath("MintNFTContract");
	public static final String NESTED_BURN = bytecodePath("NestedBurn");
	public static final String TRANSFER_AND_BURN = bytecodePath("TransferAndBurn");
	public static final String GRACEFULLY_FAILING_CONTRACT_BIN = bytecodePath("GracefullyFailingContract");
	public static final String ASSOCIATE_TRY_CATCH = bytecodePath("AssociateTryCatch");
	public static final String CALLED_CONTRACT = bytecodePath("CalledContract");
	public static final String DELEGATE_CONTRACT = bytecodePath("DelegateContract");
	public static final String SERVICE_CONTRACT = bytecodePath("ServiceContract");
	public static final String STATIC_CONTRACT = bytecodePath("StaticContract");
	public static final String MIXED_MINT_TOKEN_CONTRACT = bytecodePath("MixedMintTokenContract");
	public static final String MIXED_FRAMES_SCENARIOS = bytecodePath("MixedFramesScenarios");
	public static final String NESTED_CREATIONS_PATH = bytecodePath("NestedCreations");
	public static final String ERC_20_CONTRACT = bytecodePath("ERC20Contract");
	public static final String NESTED_ERC_20_CONTRACT = bytecodePath("NestedERC20Contract");
	public static final String ERC_721_CONTRACT = bytecodePath("ERC721Contract");
	public static final String CREATE2_FACTORY_PATH = bytecodePath("Create2Factory");
	public static final String SALTING_CREATOR_FACTORY_PATH = bytecodePath("SaltingCreatorFactory");
	public static final String ADDRESS_VAL_RETURNER_PATH = bytecodePath("AddressValueRet");
	public static final String PRECOMPILE_CREATE2_USER_PATH = bytecodePath("Create2PrecompileUser");
	public static final String REVERTING_CREATE_FACTORY_PATH = bytecodePath("RevertingCreateFactory");
	public static final String REVERTING_CREATE2_FACTORY_PATH = bytecodePath("RevertingCreate2Factory");
	public static final String NESTED_TRANSFERRING_CONTRACT_PATH = bytecodePath("NestedTransferringContract");
	public static final String NESTED_TRANSFER_CONTRACT_1_PATH = bytecodePath("NestedTransferContract1");
	public static final String NESTED_TRANSFER_CONTRACT_2_PATH = bytecodePath("NestedTransferContract2");
	public static final String OUTER_CREATOR_PATH = bytecodePath("OuterCreator");
	public static final String VARIOUS_CREATE2_CALLS_PATH = bytecodePath("VariousCreate2Calls");
	public static final String EMIT_BLOCKTIME_PATH = bytecodePath("EmitBlockTimestamp");
	public static final String TOY_MAKER_PATH = bytecodePath("ToyMaker");

	public static final String TOYMAKER_MAKE_ABI = "{\"inputs\":[],\"name\":\"make\"," +
			"\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String INDIRECT_CREATE_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"makerAddress\",\"type\":\"address\"}],\"name\":\"makeOpaquely\"," +
			"\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String EMIT_TIME_ABI = "{\"inputs\":[],\"name\":\"logNow\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String VARIOUS_CALLS_NORMAL_ABI = "{\"inputs\":[],\"name\":\"makeNormalCall\"," +
			"\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}],\"" +
			"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String VARIOUS_CALLS_STATIC_ABI = "{\"inputs\":[],\"name\":\"makeStaticCall\"," +
			"\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}],\"stateM" +
			"utability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String VARIOUS_CALLS_DELEGATE_ABI = "{\"inputs\":[],\"name\":\"makeDelegateCall\"," +
			"\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String VARIOUS_CALLS_CODE_ABI = "{\"inputs\":[],\"name\":\"makeCallCode\"," +
			"\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String CREATE_DONOR_PATH = bytecodePath("CreateDonor");

	public static final String BUILD_THEN_REVERT_THEN_BUILD_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\"," +
			"\"name\":\"salt\",\"type\":\"bytes32\"}],\"name\":\"buildThenRevertThenBuild\",\"outputs\":[]," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String RELINQUISH_FUNDS_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"beneficiary\",\"type\":\"address\"}],\"name\":\"relinquishFundsTo\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String CREATE_DONOR_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"salt\"," +
			"\"type\":\"bytes32\"}],\"name\":\"buildDonor\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String CREATE_AND_RECREATE_ABI = "{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"salt\"," +
			"\"type\":\"bytes32\"}],\"name\":\"createAndRecreateTest\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String WRONG_REPEATED_CREATE2_ABI = "{\"inputs\":[{\"internalType\":\"bytes\"," +
			"\"name\":\"bytecode\",\"type\":\"bytes\"},{\"internalType\":\"uint256\",\"name\":\"_salt\"," +
			"\"type\":\"uint256\"}],\"name\":\"wronglyDeployTwice\",\"outputs\":[],\"stateMutability\":\"payable\"," +
			"\"type\":\"function\"}";
	public static final String WHAT_IS_FOO_ABI = "{\"inputs\":[],\"name\":\"whatTheFoo\"," +
			"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String START_CHAIN_ABI = "{\"inputs\":[{\"internalType\":\"bytes\",\"name\":\"logMessage\"," +
			"\"type\":\"bytes\"}],\"name\":\"startChain\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
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
	public static final String WORKING_HOURS_CONS = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"_tokenAddress\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"_treasury\"," +
			"\"type\":\"address\"}],\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
	public static final String WORKING_HOURS_TAKE_TICKET = "{\"inputs\":[],\"name\":\"takeTicket\"," +
			"\"outputs\":[{\"internalType\":\"int64\",\"name\":\"serialNumber\",\"type\":\"int64\"}]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String WORKING_HOURS_WORK_TICKET = "{\"inputs\":[{\"internalType\":\"int64\"," +
			"\"name\":\"ticketNum\",\"type\":\"int64\"}],\"name\":\"workTicket\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String IMAP_USER_INSERT = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"k\"," +
			"\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"v\",\"type\":\"uint256\"}]," +
			"\"name\":\"insert\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"size\"," +
			"\"type\":\"uint256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String IMAP_USER_REMOVE = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"k\",\"type" +
			"\":\"uint256\"}],\"name\":\"remove\",\"outputs\":[{\"internalType\":\"bool\",\"name\":\"success\"," +
			"\"type\":\"bool\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String MIXED_FRAMES_SCENARIOS_CONS_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"_mixedMintTokenContractAddress\"," +
			"\"type\": \"address\"}],\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";

	public static final String ERC_20_NAME_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}],\"name\": \"name\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_20_SYMBOL_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": " +
			"\"token\",\"type\": \"address\"}],\"name\": \"symbol\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_20_DECIMALS_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": " +
			"\"token\",\"type\": \"address\"}],\"name\": \"decimals\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_20_TOTAL_SUPPLY_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": " +
			"\"token\",\"type\": \"address\"}],\"name\": \"totalSupply\",\"outputs\": [],\"stateMutability\": " +
			"\"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_20_BALANCE_OF_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"account\",\"type\": \"address\"}],\"name\": \"balanceOf\",\"outputs\": [],\"stateMutability\": " +
			"\"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_20_TRANSFER_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"recipient\",\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"amount\"," +
			"\"type\": \"uint256\"}],\"name\": \"transfer\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String ERC_20_DELEGATE_TRANSFER_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"recipient\",\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"amount\"," +
			"\"type\": \"uint256\"}],\"name\": \"delegateTransfer\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";


	public static final String ERC_20_TRANSFER_FROM_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"sender\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"recipient\"," +
			"\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"amount\",\"type\": \"uint256\"}],\"name\": \"transferFrom\",\"outputs\": []," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_20_ALLOWANCE_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"owner\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"spender\",\"type\": \"address\"}],\"name\": \"allowance\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_20_APPROVE_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"spender\",\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"amount\",\"type\": \"uint256\"}],\"name\": \"approve\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String ERC_721_NAME_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}]," +
			"\"name\": \"name\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_SYMBOL_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}]," +
			"\"name\": \"symbol\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_TOKEN_URI_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"tokenId\",\"type\": \"uint256\"}],\"name\": \"tokenURI\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_TOTAL_SUPPLY_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}]," +
			"\"name\": \"totalSupply\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_TOKEN_BY_INDEX_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"index\",\"type\": \"uint256\"}],\"name\": \"tokenByIndex\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_TOKEN_OF_OWNER_BY_INDEX_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"owner\",\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"index\",\"type\": \"uint256\"}]," +
			"\"name\": \"tokenOfOwnerByIndex\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_BALANCE_OF_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"owner\",\"type\": \"address\"}],\"name\": \"balanceOf\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_OWNER_OF_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"tokenId\",\"type\": \"uint256\"}],\"name\": \"ownerOf\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_SAFE_TRANSFER_FROM_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"from\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"to\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"tokenId\",\"type\": \"uint256\"}],\"name\": \"safeTransferFrom\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_SAFE_TRANSFER_FROM_WITH_DATA_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"from\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"to\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"tokenId\",\"type\": \"uint256\"},{\"internalType\": \"bytes\",\"name\": \"data\",\"type\": \"bytes\"}]," +
			"\"name\": \"safeTransferFromWithData\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_TRANSFER_FROM_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"},{\"internalType\": \"address\"," +
			"\"name\": \"from\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"to\",\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"tokenId\"," +
			"\"type\": \"uint256\"}],\"name\": \"transferFrom\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_APPROVE_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"},{\"internalType\": \"address\"," +
			"\"name\": \"to\",\"type\": \"address\"},{\"internalType\": \"uint256\",\"name\": \"tokenId\",\"type\": \"uint256\"}],\"name\": \"approve\",\"outputs\": []," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_SET_APPROVAL_FOR_ALL_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"operator\",\"type\": \"address\"},{\"internalType\": \"bool\",\"name\": \"approved\",\"type\": \"bool\"}]," +
			"\"name\": \"setApprovalForAll\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_GET_APPROVED_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint256\",\"name\": \"tokenId\",\"type\": \"uint256\"}],\"name\": \"getApproved\",\"outputs\": []," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String ERC_721_IS_APPROVED_FOR_ALL_CALL = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"token\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"owner\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"operator\",\"type\": \"address\"}]," +
			"\"name\": \"isApprovedForAll\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"";

	public static final String BURN_CALL_AFTER_NESTED_MINT_CALL_WITH_PRECOMPILE_CALL = "{\"inputs\": [{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"}],\"name\": \"burnCallAfterNestedMintCallWithPrecompileCall\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String BURN_DELEGATE_CALL_AFTER_NESTED_MINT_CALL_WITH_PRECOMPILE_CALL = "{\"inputs\": " +
			"[{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"}],\"name\": \"burnDelegateCallAfterNestedMintCallWithPrecompileCall\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String BURN_DELEGATE_CALL_AFTER_NESTED_MINT_DELEGATE_CALL_WITH_PRECOMPILE_CALL = "{\"inputs" +
			"\": [{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"}],\"name\": \"burnDelegateCallAfterNestedMintDelegateCallWithPrecompileCall\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String BURN_CALL_AFTER_NESTED_MINT_DELEGATE_CALL_WITH_PRECOMPILE_CALL = "{\"inputs\": " +
			"[{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"}],\"name\": \"burnCallAfterNestedMintDelegateCallWithPrecompileCall\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String BURN_CALL_AFTER_NESTED_MINT_CALL_WITH_PRECOMPILE_DELEGATE_CALL = "{\"inputs\": " +
			"[{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"}],\"name\": \"burnCallAfterNestedMintCallWithPrecompileDelegateCall\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String BURN_DELEGATE_CALL_AFTER_NESTED_MINT_CALL_WITH_PRECOMPILE_DELEGATE_CALL = "{\"inputs" +
			"\": [{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"}],\"name\": \"burnDelegateCallAfterNestedMintCallWithPrecompileDelegateCall\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String BURN_DELEGATE_CALL_AFTER_NESTED_MINT_DELEGATE_CALL_WITH_PRECOMPILE_DELEGATE_CALL =
			"{\"inputs\": [{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"}],\"name\": \"burnDelegateCallAfterNestedMintDelegateCallWithPrecompileDelegateCall\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String BURN_CALL_AFTER_NESTED_MINT_DELEGATE_CALL_WITH_PRECOMPILE_DELEGATE_CALL = "{\"inputs\": " +
			"[{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"}],\"name\": \"burnCallAfterNestedMintDelegateCallWithPrecompileDelegateCall\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String HW_MINT_CONS_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"_tokenAddress\",\"type\":\"address\"}],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"constructor\"}";
	public static final String HW_BRRR_CALL_ABI = "{\"inputs\":[{\"internalType\":\"uint64\",\"name\":\"amount\"," +
			"\"type\":\"uint64\"}],\"name\":\"brrr\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String HW_MINT_CALL_ABI = "{\"inputs\":[],\"name\":\"mint\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String NESTED_MINT_CONS_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"_mintContractAddress\"," +
			"\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"_tokenAddress\",\"type\": \"address\"}]," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";
	public static final String NESTED_TRANSFER_NFT_AFTER_MINT_CALL_ABI = "{\"inputs\": [{\"internalType\": \"address\"," +
			"\"name\": \"sender\",\"type\": \"address\"},{\"internalType\": \"address\"," +
			"\"name\": \"recipient\",\"type\": \"address\"},{\"internalType\": \"bytes[]\"," +
			"\"name\": \"metadata\",\"type\": \"bytes[]\"},{\"internalType\": \"int64\"," +
			"\"name\": \"serialNumber\",\"type\": \"int64\"}],\"name\": \"sendNFTAfterMint\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String REVERT_MINT_AFTER_FAILED_ASSOCIATE = "{\"inputs\": [{\"internalType" +
			"\": \"address\",\"name\": \"accountToAssociate\",\"type\": \"address\"},{\"internalType\": \"bytes[]\"," +
			"\"name\": \"metadata\",\"type\": \"bytes[]\"}],\"name\": \"revertMintAfterFailedAssociate\",\"outputs\":[]," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String MINT_CONS_ABI = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"_tokenAddress\",\"type\":\"address\"}],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"constructor\"}";
	public static final String MINT_FUNGIBLE_CALL_ABI = "{\"inputs\":[{\"internalType\":\"uint64\"," +
			"\"name\":\"amount\"," +
			"\"type\":\"uint64\"}],\"name\":\"mintFungibleToken\",\"outputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String MINT_NON_FUNGIBLE_CALL_ABI = "{\"inputs\":[{\"internalType\":\"bytes[]\"," +
			"\"name\":\"metadata\"," +
			"\"type\":\"bytes[]\"}],\"name\":\"mintNonFungibleToken\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String REVERT_AFTER_FAILED_MINT = "{\"inputs\": [{\"internalType\": \"address\"," +
			"\"name\": \"sender\",\"type\": \"address\"},{\"internalType\": \"address\",\"name\": \"recipient\",\"type\": \"address\"}," +
			"{\"internalType\": \"int64\",\"name\": \"amount\",\"type\": \"int64\"}],\"name\": \"revertMintAfterFailedMint\"," +
			"\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String MINT_FUNGIBLE_WITH_EVENT_CALL_ABI = "{\"inputs\":[{\"internalType\":\"uint64\"," +
			"\"name\":\"amount\"," +
			"\"type\":\"uint64\"}],\"name\":\"mintFungibleTokenWithEvent\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String MINT_NON_FUNGIBLE_WITH_EVENT_CALL_ABI = "{\"inputs\": [{\"internalType\": \"bytes[]\"," +
			"\"name\": \"metadata\",\"type\": \"bytes[]\"}],\"name\": \"mintNonFungibleTokenWithEvent\",\"outputs\": " +
			"[]," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";
	public static final String CRYPTO_TRANSFER_CONS_ABI = "{\"inputs\":[],\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"constructor\"}";
	public static final String CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST = "{\"inputs\": [{\"components\": [{\"internalType\": \"address\"," +
			"\"name\": \"token\",\"type\": \"address\"},{\"components\": [{\"internalType\": \"address\",\"name\": \"accountID\"," +
			"\"type\": \"address\"},{\"internalType\": \"int64\",\"name\": \"amount\",\"type\": \"int64\"}],\"internalType\": \"struct IHederaTokenService.AccountAmount[]\"," +
			"\"name\": \"transfers\",\"type\": \"tuple[]\"},{\"components\": [{\"internalType\": \"address\",\"name\": \"senderAccountID\",\"type\": \"address\"}," +
			"{" + "\"internalType\": \"address\",\"name\": \"receiverAccountID\",\"type\": \"address\"},{\"internalType\": \"int64\",\"name\": \"serialNumber\"," +
			"\"type\": \"int64\"}],\"internalType\": \"struct IHederaTokenService.NftTransfer[]\",\"name\": \"nftTransfers\",\"type\": \"tuple[]\"}]," +
			"\"internalType\": \"struct IHederaTokenService.TokenTransferList[]\",\"name\": \"tokenTransfers\"," + "\"type\": \"tuple[]\"" + "}]," +
			"\"name\": \"transferMultipleTokens\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String BURN_TOKEN_CONSTRUCTOR_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"_tokenAddress\",\"type\": \"address\"}]," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";

	public static final String BURN_TOKEN_ABI = "{\"inputs\": [{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"int64[]\",\"name\": \"serialNumbers\",\"type\": \"int64[]\"}]," +
			"\"name\": \"burnToken\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

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
