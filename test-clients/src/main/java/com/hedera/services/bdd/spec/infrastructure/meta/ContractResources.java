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
	public static final String HW_MINT_PATH = bytecodePath("HelloWorldMint");
	public static final String SIMPLE_STORAGE_BYTECODE_PATH = bytecodePath("simpleStorage");
	public static final String PAYABLE_CONTRACT_BYTECODE_PATH = bytecodePath("PayReceivable");
	public static final String DELEGATING_CONTRACT_BYTECODE_PATH = bytecodePath("CreateTrivial");
	public static final String BALANCE_LOOKUP_BYTECODE_PATH = bytecodePath("BalanceLookup");
	public static final String CIRCULAR_TRANSFERS_BYTECODE_PATH = bytecodePath("CircularTransfers");
	public static final String INLINE_TEST_BYTECODE_PATH = bytecodePath("InlineTest");
	public static final String INVALID_BYTECODE_PATH = bytecodePath("CorruptOne");
	public static final String VALID_BYTECODE_PATH = HapiSpecSetup.getDefaultInstance().defaultContractPath();
	public static final String ERC721_BYTECODE_PATH = bytecodePath("ERC721");
	public static final String VERBOSE_DEPOSIT_BYTECODE_PATH = bytecodePath("VerboseDeposit");
	public static final String GROW_ARRAY_BYTECODE_PATH = bytecodePath("GrowArray");
	public static final String BIG_ARRAY_BYTECODE_PATH = bytecodePath("BigArray");
	public static final String EMIT_EVENT_BYTECODE_PATH = bytecodePath("EmitEvent");
	public static final String BIG_BIG_BYTECODE_PATH = bytecodePath("BigBig");
	public static final String FUSE_BYTECODE_PATH = bytecodePath("Fuse");
	public static final String LAST_TRACKING_SENDER_BYTECODE_PATH = bytecodePath("LastTrackingSender");
	public static final String MULTIPURPOSE_BYTECODE_PATH = bytecodePath("Multipurpose");
	public static final String JUST_SEND_BYTECODE_PATH = bytecodePath("JustSend");
	public static final String SEND_INTERNAL_AND_DELEGATE_BYTECODE_PATH = bytecodePath("SendInternalAndDelegate");
	public static final String CHILD_STORAGE_BYTECODE_PATH = bytecodePath("ChildStorage");
	public static final String ABANDONING_PARENT_BYTECODE_PATH = bytecodePath("AbandoningParent");
	public static final String PAY_TEST_SELF_DESTRUCT_BYTECODE_PATH = bytecodePath("PayTestSelfDestruct");
	public static final String PARENT_CHILD_TRANSFER_BYTECODE_PATH = bytecodePath("ParentChildTransfer");
	public static final String OC_TOKEN_BYTECODE_PATH = bytecodePath("octoken");
	public static final String ADDRESS_BOOK_BYTECODE_PATH = bytecodePath("AddressBook");
	public static final String JURISDICTIONS_BYTECODE_PATH = bytecodePath("Jurisdictions");
	public static final String MINTERS_BYTECODE_PATH = bytecodePath("Minters");
	public static final String PAY_TEST_BYTECODE_PATH = bytecodePath("PayTest");
	public static final String DOUBLE_SEND_BYTECODE_PATH = bytecodePath("DoubleSend");
	public static final String EMPTY_CONSTRUCTOR = bytecodePath("EmptyConstructor");
	public static final String PAYABLE_CONSTRUCTOR = bytecodePath("PayableConstructor");
	public static final String ERC20_BYTECODE_PATH = bytecodePath("ERC20");
	public static final String ERC_1155_BYTECODE_PATH = bytecodePath("erc1155");
	public static final String BENCHMARK_CONTRACT = bytecodePath("Benchmark");
	public static final String SIMPLE_UPDATE = bytecodePath("SimpleUpdate");
	public static final String LOGS = bytecodePath("Logs");
	public static final String IMAP_USER_BYTECODE_PATH = bytecodePath("User");
	public static final String WORKING_HOURS_USER_BYTECODE_PATH = bytecodePath("WorkingHours");
	public static final String CALLING_CONTRACT = bytecodePath("CallingContract");
	public static final String GLOBAL_PROPERTIES = bytecodePath("GlobalProperties");
	public static final String BALANCE_CHECKER_CONTRACT = bytecodePath("BalanceChecker");
	public static final String EXT_CODE_OPERATIONS_CHECKER_CONTRACT = bytecodePath("ExtCodeOperationsChecker");
	public static final String CALL_OPERATIONS_CHECKER = bytecodePath("CallOperationsChecker");
	public static final String FACTORY_CONTRACT = bytecodePath("FactoryContract");
	public static final String TRANSFERRING_CONTRACT = bytecodePath("TransferringContract");
	public static final String NESTED_CHILDREN_CONTRACT = bytecodePath("NestedChildren");
	public static final String FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT = bytecodePath("FactorySelfDestructConstructor");
	public static final String FACTORY_QUICK_SELF_DESTRUCT_CONTRACT = bytecodePath("FactoryQuickSelfDestruct");
	public static final String TEMPORARY_SSTORE_REFUND_CONTRACT = bytecodePath("TemporarySStoreRefund");
	public static final String FIBONACCI_PLUS_PATH = bytecodePath("FibonacciPlus");
	public static final String TOP_LEVEL_TRANSFERRING_CONTRACT = bytecodePath("TopLevelTransferringContract");
	public static final String SUB_LEVEL_TRANSFERRING_CONTRACT = bytecodePath("SubLevelTransferringContract");
	public static final String LARGE_CONTRACT_CRYPTO_KITTIES = bytecodePath("CryptoKitties");
	public static final String SELF_DESTRUCT_CALLABLE = bytecodePath("SelfDestructCallable");
	public static final String REVERTING_SEND_TRY = bytecodePath("RevertingSendTry");
	public static final String ZENOS_BANK_CONTRACT = bytecodePath("ZenosBank");
	public static final String ORDINARY_CALLS_CONTRACT = bytecodePath("HTSCalls");
	public static final String VERSATILE_TRANSFERS_CONTRACT = bytecodePath("VersatileTransfers");
	public static final String HBAR_FEE_TRANSFER = bytecodePath("HBARFeeTransfer");
	public static final String TRANSFERER = bytecodePath("Transferer");
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

	public static final String BURN_TOKEN_WITH_EVENT_ABI = "{\"inputs\": [{\"internalType\": \"uint64\",\"name\": " +
			"\"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"int64[]\",\"name\": \"serialNumbers\",\"type\": \"int64[]\"}]," +
			"\"name\": \"burnTokenWithEvent\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": " +
			"\"function\"}";

	public static final String NESTED_BURN_CONSTRUCTOR_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"mintTokenContractAddress\",\"type\": \"address\"}]," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";

	public static final String BURN_AFTER_NESTED_MINT_ABI = "{\"inputs\": [{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"address\",\"name\": \"tokenAddress\",\"type\": \"address\"},{\"internalType\": \"int64[]\",\"name\": \"serialNumbers\",\"type\": \"int64[]\"} ]," +
		"\"name\": \"burnAfterNestedMint\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String TRANSFER_AND_BURN_CONSTRUCTOR_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"_tokenAddress\",\"type\": \"address\"}]," +
			"\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";

	public static final String TRANSFER_BURN_ABI = "{\"inputs\": [{\"internalType\": \"address\",\"name\": \"_address\",\"type\": \"address\"}," +
			"{\"internalType\": \"address\",\"name\": \"_address2\",\"type\": \"address\"}," +
			"{\"internalType\": \"uint64\",\"name\": \"amount\",\"type\": \"uint64\"}," +
			"{\"internalType\": \"int64\",\"name\": \"serialNum\",\"type\": \"int64\"}," +
			"{\"internalType\": \"int64[]\",\"name\": \"serialNumbers\",\"type\": \"int64[]\"}]," +
			"\"name\": \"transferBurn\",\"outputs\": [],\"stateMutability\": \"nonpayable\",\"type\": \"function\"}";

	public static final String SEND_REPEATEDLY_ABI = "{\"inputs\":[{\"internalType\":\"uint64\",\"name\":\"just_send_" +
			"num\",\"type\":\"uint64\"},{\"internalType\":\"uint64\",\"name\":\"account_num\",\"type\":\"uint64\"}," +
			"{\"internalType\":\"uint64\",\"name\":\"value\",\"type\":\"uint64\"}],\"name\":\"sendRepeatedlyTo\"," +
			"\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String SELF_DESTRUCT_CALL_ABI = "{\"inputs\":[],\"name\":\"destroy\",\"outputs\":[]," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String CRYPTO_KITTIES_CREATE_PROMO_KITTY_ABI = "{\"constant\":false,\"inputs\":[{\"name\":" +
			"\"_genes\",\"type\":\"uint256\"},{\"name\":\"_owner\",\"type\":\"address\"}],\"name\":\"createPromoKitty\"," +
			"\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String TOP_LEVEL_TRANSFERRING_CONTRACT_TRANSFER_CALL_PAYABLE_ABI = "{\"inputs\":[],\"name\":" +
			"\"topLevelTransferCall\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String TOP_LEVEL_TRANSFERRING_CONTRACT_NON_PAYABLE_ABI = "{\"inputs\":[],\"name\":" +
			"\"topLevelNonPayableCall\",\"outputs\":[{\"internalType\":\"bool\",\"name\":\"\",\"type\":\"bool\"}]," +
			"\"stateMutability\":\"pure\",\"type\":\"function\"}";
	public static final String SEND_THEN_REVERT_NESTED_SENDS_ABI = "{\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"amount\",\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"numA\"," +
			"\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"numB\",\"type\":\"uint32\"}]," +
			"\"name\":\"sendTo\",\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"}";
	public static final String SUB_LEVEL_PAYABLE_ABI = "{\"inputs\":" +
			"[{\"internalType\":\"address\",\"name\":\"_contract\",\"type\":\"address\"}" +
			",{\"internalType\":\"uint256\",\"name\":\"_amount\",\"type\":\"uint256\"}],\"name\":\"subLevelPayableCall\"," +
			"\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static final String SUB_LEVEL_NON_PAYABLE_ABI = "{\"inputs\":" +
			"[{\"internalType\":\"address\",\"name\":\"_contract\",\"type\":\"address\"}," +
			"{\"internalType\":\"uint256\",\"name\":\"_amount\",\"type\":\"uint256\"}]," +
			"\"name\":\"subLevelNonPayableCall\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

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
	public static final String COMPUTE_NTH_FIB_ABI = "{\"constant\":true,\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"n\",\"type\":\"uint32\"}],\"name\":\"fib\",\"outputs\":[{\"internalType\":\"uint256\"," +
			"\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";
	public static final String ERC721_MINT_ABI = "{ \"inputs\": [ { \"internalType\": \"address\"," +
			" \"name\": \"player\", \"type\": \"address\" }, { \"internalType\": \"uint256\", \"name\": \"tokenid\", \"type\": \"uint256\" } ]," +
			" \"name\": \"mint\", \"outputs\": [ { \"internalType\": \"uint256\", " +
			"\"name\": \"\", \"type\": \"uint256\" } ], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String ERC721_APPROVE_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"to\", \"type\": \"address\" }, " +
			"{ \"internalType\": \"uint256\", \"name\": \"tokenId\", \"type\": \"uint256\" } ], \"name\": \"approve\", \"outputs\": [], " +
			"\"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String ERC721_TRANSFER_FROM_ABI = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"from\", \"type\": \"address\" }, " +
			"{ \"internalType\": \"address\", \"name\": \"to\", \"type\": \"address\" }, " +
			"{ \"internalType\": \"uint256\", \"name\": \"tokenId\", \"type\": \"uint256\" } ], " +
			"\"name\": \"transferFrom\", \"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
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
	public static final String BIG_ARRAY_CHANGE_ARRAY_ABI = "{ \"constant\": false," +
			" \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_value\", \"type\": \"uint256\" } ], " +
			"\"name\": \"changeArray\", \"outputs\": [], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
	public static final String BIG_ARRAY_GROW_TO_ABI = "{\"constant\":false," +
			"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_limit\",\"type\":\"uint256\"}],\"name\":\"growTo\"," +
			"\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
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
	public static final String MINT_ISVALID_ABI = "{\"constant\":true," +
			"\"inputs\":[{\"name\":\"minter\",\"type\":\"address\"}],\"name\":\"isValid\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
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

	public static final String HBAR_FEE_TRANSFER_CONSTRUCTOR = "{\"inputs\": [{\"internalType\": \"address\"," +
			"\"name\": " +
			"\"transfererContractAddress\",\"type\": \"address\"}],\"stateMutability\": \"nonpayable\",\"type\": \"constructor\"}";

	public static final String HBAR_FEE_TRANSFER_DISTRIBUTE = "{\"inputs\":[{\"internalType\":\"address\"," +
			"\"name\":\"tokenAddress\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"sender\"," +
			"\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"receiver\",\"type\":\"address\"}," +
			"{\"internalType\":\"int64\",\"name\":\"amount\",\"type\":\"int64\"},{\"internalType\":\"addresspayable\"" +
			",\"name\":\"feeCollector\",\"type\":\"address\"}],\"name\":\"feeDistributionAfterTransfer\",\"outputs\":[]," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

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
}
