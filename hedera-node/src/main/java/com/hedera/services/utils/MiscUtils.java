package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.AddressBook;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkletree.MerklePair;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.grpc.controllers.ConsensusController.CREATE_TOPIC_METRIC;
import static com.hedera.services.grpc.controllers.ConsensusController.DELETE_TOPIC_METRIC;
import static com.hedera.services.grpc.controllers.ConsensusController.GET_TOPIC_INFO_METRIC;
import static com.hedera.services.grpc.controllers.ConsensusController.SUBMIT_MESSAGE_METRIC;
import static com.hedera.services.grpc.controllers.ConsensusController.UPDATE_TOPIC_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.CALL_CONTRACT_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.CREATE_CONTRACT_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.DELETE_CONTRACT_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.GET_CONTRACT_BYTECODE_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.GET_CONTRACT_INFO_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.GET_CONTRACT_RECORDS_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.GET_SOLIDITY_ADDRESS_INFO_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.LOCALCALL_CONTRACT_METRIC;
import static com.hedera.services.grpc.controllers.ContractController.UPDATE_CONTRACT_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.ADD_LIVE_HASH_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.CRYPTO_CREATE_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.CRYPTO_DELETE_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.CRYPTO_TRANSFER_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.CRYPTO_UPDATE_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.DELETE_LIVE_HASH_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.GET_ACCOUNT_BALANCE_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.GET_ACCOUNT_INFO_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.GET_ACCOUNT_RECORDS_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.GET_LIVE_HASH_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.GET_RECEIPT_METRIC;
import static com.hedera.services.grpc.controllers.CryptoController.GET_RECORD_METRIC;
import static com.hedera.services.grpc.controllers.FileController.CREATE_FILE_METRIC;
import static com.hedera.services.grpc.controllers.FileController.DELETE_FILE_METRIC;
import static com.hedera.services.grpc.controllers.FileController.FILE_APPEND_METRIC;
import static com.hedera.services.grpc.controllers.FileController.GET_FILE_CONTENT_METRIC;
import static com.hedera.services.grpc.controllers.FileController.GET_FILE_INFO_METRIC;
import static com.hedera.services.grpc.controllers.FileController.UPDATE_FILE_METRIC;
import static com.hedera.services.grpc.controllers.FreezeController.FREEZE_METRIC;
import static com.hedera.services.grpc.controllers.NetworkController.GET_VERSION_INFO_METRIC;
import static com.hedera.services.grpc.controllers.NetworkController.UNCHECKED_SUBMIT_METRIC;
import static com.hedera.services.legacy.core.jproto.JKey.mapJKey;
import static com.hedera.services.stats.ServicesStatsConfig.SYSTEM_DELETE_METRIC;
import static com.hedera.services.stats.ServicesStatsConfig.SYSTEM_UNDELETE_METRIC;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetByKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetAccountNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UNRECOGNIZED;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONSENSUSGETTOPICINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONTRACTCALLLOCAL;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONTRACTGETBYTECODE;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONTRACTGETINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONTRACTGETRECORDS;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CRYPTOGETACCOUNTBALANCE;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CRYPTOGETACCOUNTRECORDS;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CRYPTOGETINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CRYPTOGETLIVEHASH;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.FILEGETCONTENTS;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.FILEGETINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.GETBYKEY;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.GETBYSOLIDITYID;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.NETWORKGETVERSIONINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.SCHEDULEGETINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TOKENGETACCOUNTNFTINFOS;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TOKENGETINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TOKENGETNFTINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TOKENGETNFTINFOS;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TRANSACTIONGETRECEIPT;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TRANSACTIONGETRECORD;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public class MiscUtils {
	MiscUtils() {
		throw new IllegalStateException("Utility Class");
	}

	private static final String UNEXPECTED_RUNTIME_ERROR =
			"All exceptions should show up in the static initializer, yet `%s` occurred";

	public static final EnumSet<HederaFunctionality> QUERY_FUNCTIONS = EnumSet.of(
			ConsensusGetTopicInfo,
			GetBySolidityID,
			ContractCallLocal,
			ContractGetInfo,
			ContractGetBytecode,
			ContractGetRecords,
			CryptoGetAccountBalance,
			CryptoGetAccountRecords,
			CryptoGetInfo,
			CryptoGetLiveHash,
			FileGetContents,
			FileGetInfo,
			TransactionGetReceipt,
			TransactionGetRecord,
			GetVersionInfo,
			TokenGetInfo,
			ScheduleGetInfo,
			TokenGetNftInfo,
			TokenGetNftInfos,
			TokenGetAccountNftInfos
	);

	private static final Set<HederaFunctionality> SCHEDULE_FUNCTIONS = new HashSet<HederaFunctionality>();
	private static final Set<String> SCHEDULE_FUNCTION_STRINGS = new HashSet<>();
	private static final EnumMap<HederaFunctionality, String> TRANSACTION_FUNCTION_TO_STRINGS
			= new EnumMap<>(HederaFunctionality.class);
	private static final EnumSet<HederaFunctionality> NON_SCHEDULE_FUNCTIONS = EnumSet.of(
			CryptoAddLiveHash,
			CryptoDeleteLiveHash,
			TokenFeeScheduleUpdate,
			ScheduleCreate,
			ScheduleSign,
			UncheckedSubmit
	);

	static {
		final var sameNameTransactionFunctions = Set.of(
				ContractCall,
				CryptoDelete,
				CryptoTransfer,
				FileAppend,
				FileCreate,
				FileDelete,
				FileUpdate,
				SystemDelete,
				SystemUndelete,
				Freeze,
				ConsensusCreateTopic,
				ConsensusUpdateTopic,
				ConsensusDeleteTopic,
				ConsensusSubmitMessage,
				TokenUpdate,
				TokenMint,
				TokenBurn,
				ScheduleDelete
		);
		for (var f : sameNameTransactionFunctions) {
			addFunction(f, f.name());
		}
		addFunction(ContractCreate, "ContractCreateInstance");
		addFunction(ContractUpdate, "ContractUpdateInstance");
		addFunction(ContractDelete, "ContractDeleteInstance");
		addFunction(CryptoCreate, "CryptoCreateAccount");
		addFunction(CryptoUpdate, "CryptoUpdateAccount");
		addFunction(TokenCreate, "TokenCreation");
		addFunction(TokenFreezeAccount, "TokenFreeze");
		addFunction(TokenUnfreezeAccount, "TokenUnfreeze");
		addFunction(TokenGrantKycToAccount, "TokenGrantKyc");
		addFunction(TokenRevokeKycFromAccount, "TokenRevokeKyc");
		addFunction(TokenDelete, "TokenDeletion");
		addFunction(TokenAccountWipe, "TokenWipe");
		addFunction(TokenAssociateToAccount, "TokenAssociate");
		addFunction(TokenDissociateFromAccount, "TokenDissociate");
	}

	private static final void addFunction(final HederaFunctionality func, final String name) {
		SCHEDULE_FUNCTIONS.add(func);
		SCHEDULE_FUNCTION_STRINGS.add(name);
		TRANSACTION_FUNCTION_TO_STRINGS.put(func, name);
	}

	private static final HashMap<String, Method> SCHEDULE_HAS_METHODS = new HashMap<>();
	private static final HashMap<String, Method> SCHEDULE_GETTERS = new HashMap<>();
	private static final HashMap<String, Method> TRANSACTION_SETTERS = new HashMap<>();
	private static final HashMap<String, Method> TRANSACTION_HAS_METHODS = new HashMap<>();

	static {
		initializeScheduleAndTransactionMethods(SCHEDULE_FUNCTION_STRINGS);
	}

	static final void initializeScheduleAndTransactionMethods(final Set<String> types) {
		try {
			for (var type : types) {
				SCHEDULE_HAS_METHODS.put(type, SchedulableTransactionBody.class.getMethod("has" + type));
				SCHEDULE_GETTERS.put(type, SchedulableTransactionBody.class.getMethod("get" + type));
				for (var m : TransactionBody.Builder.class.getMethods()) {
					if (m.getName().equals("set" + type)
							&& !m.getParameterTypes()[0].getSimpleName().contains("Builder")) {
						TRANSACTION_SETTERS.put(type, m);
					}
				}
				TRANSACTION_HAS_METHODS.put(type, TransactionBody.class.getMethod("has" + type));
			}
			for (var f : NON_SCHEDULE_FUNCTIONS) {
				final var type = f.name();
				TRANSACTION_HAS_METHODS.put(type, TransactionBody.class.getMethod("has" + type));
			}
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException(e);
		}
	}

	static final String TOKEN_MINT_METRIC = "mintToken";
	static final String TOKEN_BURN_METRIC = "burnToken";
	static final String TOKEN_CREATE_METRIC = "createToken";
	static final String TOKEN_DELETE_METRIC = "deleteToken";
	static final String TOKEN_UPDATE_METRIC = "updateToken";
	static final String TOKEN_FREEZE_METRIC = "freezeTokenAccount";
	static final String TOKEN_UNFREEZE_METRIC = "unfreezeTokenAccount";
	static final String TOKEN_GRANT_KYC_METRIC = "grantKycToTokenAccount";
	static final String TOKEN_REVOKE_KYC_METRIC = "revokeKycFromTokenAccount";
	static final String TOKEN_WIPE_ACCOUNT_METRIC = "wipeTokenAccount";
	static final String TOKEN_ASSOCIATE_METRIC = "associateTokens";
	static final String TOKEN_DISSOCIATE_METRIC = "dissociateTokens";
	static final String TOKEN_GET_INFO_METRIC = "getTokenInfo";
	static final String TOKEN_GET_NFT_INFO_METRIC = "getTokenNftInfo";
	static final String TOKEN_GET_ACCOUNT_NFT_INFOS_METRIC = "getAccountNftInfos";
	static final String TOKEN_FEE_SCHEDULE_UPDATE_METRIC = "tokenFeeScheduleUpdate";
	static final String TOKEN_GET_NFT_INFOS_METRIC = "getTokenNftInfos";

	static final String SCHEDULE_CREATE_METRIC = "createSchedule";
	static final String SCHEDULE_DELETE_METRIC = "deleteSchedule";
	static final String SCHEDULE_SIGN_METRIC = "signSchedule";
	static final String SCHEDULE_GET_INFO_METRIC = "getScheduleInfo";

	private static final EnumMap<Query.QueryCase, HederaFunctionality> queryFunctions =
			new EnumMap<>(Query.QueryCase.class);

	static {
		queryFunctions.put(NETWORKGETVERSIONINFO, GetVersionInfo);
		queryFunctions.put(GETBYKEY, GetByKey);
		queryFunctions.put(CONSENSUSGETTOPICINFO, ConsensusGetTopicInfo);
		queryFunctions.put(GETBYSOLIDITYID, GetBySolidityID);
		queryFunctions.put(CONTRACTCALLLOCAL, ContractCallLocal);
		queryFunctions.put(CONTRACTGETINFO, ContractGetInfo);
		queryFunctions.put(CONTRACTGETBYTECODE, ContractGetBytecode);
		queryFunctions.put(CONTRACTGETRECORDS, ContractGetRecords);
		queryFunctions.put(CRYPTOGETACCOUNTBALANCE, CryptoGetAccountBalance);
		queryFunctions.put(CRYPTOGETACCOUNTRECORDS, CryptoGetAccountRecords);
		queryFunctions.put(CRYPTOGETINFO, CryptoGetInfo);
		queryFunctions.put(CRYPTOGETLIVEHASH, CryptoGetLiveHash);
		queryFunctions.put(FILEGETCONTENTS, FileGetContents);
		queryFunctions.put(FILEGETINFO, FileGetInfo);
		queryFunctions.put(TRANSACTIONGETRECEIPT, TransactionGetReceipt);
		queryFunctions.put(TRANSACTIONGETRECORD, TransactionGetRecord);
		queryFunctions.put(TOKENGETINFO, TokenGetInfo);
		queryFunctions.put(TOKENGETNFTINFO, TokenGetNftInfo);
		queryFunctions.put(TOKENGETNFTINFOS, TokenGetNftInfos);
		queryFunctions.put(TOKENGETACCOUNTNFTINFOS, TokenGetAccountNftInfos);
		queryFunctions.put(SCHEDULEGETINFO, ScheduleGetInfo);
	}

	private static final EnumMap<HederaFunctionality, String> BASE_STAT_NAMES =
			new EnumMap<>(HederaFunctionality.class);

	static {
		/* Transactions */
		BASE_STAT_NAMES.put(CryptoCreate, CRYPTO_CREATE_METRIC);
		BASE_STAT_NAMES.put(CryptoTransfer, CRYPTO_TRANSFER_METRIC);
		BASE_STAT_NAMES.put(CryptoUpdate, CRYPTO_UPDATE_METRIC);
		BASE_STAT_NAMES.put(CryptoDelete, CRYPTO_DELETE_METRIC);
		BASE_STAT_NAMES.put(CryptoAddLiveHash, ADD_LIVE_HASH_METRIC);
		BASE_STAT_NAMES.put(CryptoDeleteLiveHash, DELETE_LIVE_HASH_METRIC);
		BASE_STAT_NAMES.put(FileCreate, CREATE_FILE_METRIC);
		BASE_STAT_NAMES.put(FileUpdate, UPDATE_FILE_METRIC);
		BASE_STAT_NAMES.put(FileDelete, DELETE_FILE_METRIC);
		BASE_STAT_NAMES.put(FileAppend, FILE_APPEND_METRIC);
		BASE_STAT_NAMES.put(ContractCreate, CREATE_CONTRACT_METRIC);
		BASE_STAT_NAMES.put(ContractUpdate, UPDATE_CONTRACT_METRIC);
		BASE_STAT_NAMES.put(ContractCall, CALL_CONTRACT_METRIC);
		BASE_STAT_NAMES.put(ContractDelete, DELETE_CONTRACT_METRIC);
		BASE_STAT_NAMES.put(ConsensusCreateTopic, CREATE_TOPIC_METRIC);
		BASE_STAT_NAMES.put(ConsensusUpdateTopic, UPDATE_TOPIC_METRIC);
		BASE_STAT_NAMES.put(ConsensusDeleteTopic, DELETE_TOPIC_METRIC);
		BASE_STAT_NAMES.put(ConsensusSubmitMessage, SUBMIT_MESSAGE_METRIC);
		BASE_STAT_NAMES.put(TokenCreate, TOKEN_CREATE_METRIC);
		BASE_STAT_NAMES.put(TokenFreezeAccount, TOKEN_FREEZE_METRIC);
		BASE_STAT_NAMES.put(TokenUnfreezeAccount, TOKEN_UNFREEZE_METRIC);
		BASE_STAT_NAMES.put(TokenGrantKycToAccount, TOKEN_GRANT_KYC_METRIC);
		BASE_STAT_NAMES.put(TokenRevokeKycFromAccount, TOKEN_REVOKE_KYC_METRIC);
		BASE_STAT_NAMES.put(TokenDelete, TOKEN_DELETE_METRIC);
		BASE_STAT_NAMES.put(TokenMint, TOKEN_MINT_METRIC);
		BASE_STAT_NAMES.put(TokenBurn, TOKEN_BURN_METRIC);
		BASE_STAT_NAMES.put(TokenAccountWipe, TOKEN_WIPE_ACCOUNT_METRIC);
		BASE_STAT_NAMES.put(TokenUpdate, TOKEN_UPDATE_METRIC);
		BASE_STAT_NAMES.put(TokenAssociateToAccount, TOKEN_ASSOCIATE_METRIC);
		BASE_STAT_NAMES.put(TokenDissociateFromAccount, TOKEN_DISSOCIATE_METRIC);
		BASE_STAT_NAMES.put(ScheduleCreate, SCHEDULE_CREATE_METRIC);
		BASE_STAT_NAMES.put(ScheduleSign, SCHEDULE_SIGN_METRIC);
		BASE_STAT_NAMES.put(ScheduleDelete, SCHEDULE_DELETE_METRIC);
		BASE_STAT_NAMES.put(UncheckedSubmit, UNCHECKED_SUBMIT_METRIC);
		BASE_STAT_NAMES.put(Freeze, FREEZE_METRIC);
		BASE_STAT_NAMES.put(SystemDelete, SYSTEM_DELETE_METRIC);
		BASE_STAT_NAMES.put(SystemUndelete, SYSTEM_UNDELETE_METRIC);
		/* Queries */
		BASE_STAT_NAMES.put(ConsensusGetTopicInfo, GET_TOPIC_INFO_METRIC);
		BASE_STAT_NAMES.put(GetBySolidityID, GET_SOLIDITY_ADDRESS_INFO_METRIC);
		BASE_STAT_NAMES.put(ContractCallLocal, LOCALCALL_CONTRACT_METRIC);
		BASE_STAT_NAMES.put(ContractGetInfo, GET_CONTRACT_INFO_METRIC);
		BASE_STAT_NAMES.put(ContractGetBytecode, GET_CONTRACT_BYTECODE_METRIC);
		BASE_STAT_NAMES.put(ContractGetRecords, GET_CONTRACT_RECORDS_METRIC);
		BASE_STAT_NAMES.put(CryptoGetAccountBalance, GET_ACCOUNT_BALANCE_METRIC);
		BASE_STAT_NAMES.put(CryptoGetAccountRecords, GET_ACCOUNT_RECORDS_METRIC);
		BASE_STAT_NAMES.put(CryptoGetInfo, GET_ACCOUNT_INFO_METRIC);
		BASE_STAT_NAMES.put(CryptoGetLiveHash, GET_LIVE_HASH_METRIC);
		BASE_STAT_NAMES.put(FileGetContents, GET_FILE_CONTENT_METRIC);
		BASE_STAT_NAMES.put(FileGetInfo, GET_FILE_INFO_METRIC);
		BASE_STAT_NAMES.put(TransactionGetReceipt, GET_RECEIPT_METRIC);
		BASE_STAT_NAMES.put(TransactionGetRecord, GET_RECORD_METRIC);
		BASE_STAT_NAMES.put(GetVersionInfo, GET_VERSION_INFO_METRIC);
		BASE_STAT_NAMES.put(TokenGetInfo, TOKEN_GET_INFO_METRIC);
		BASE_STAT_NAMES.put(TokenGetNftInfo, TOKEN_GET_NFT_INFO_METRIC);
		BASE_STAT_NAMES.put(TokenGetNftInfos, TOKEN_GET_NFT_INFOS_METRIC);
		BASE_STAT_NAMES.put(ScheduleGetInfo, SCHEDULE_GET_INFO_METRIC);
		BASE_STAT_NAMES.put(TokenGetAccountNftInfos, TOKEN_GET_ACCOUNT_NFT_INFOS_METRIC);
		BASE_STAT_NAMES.put(TokenFeeScheduleUpdate, TOKEN_FEE_SCHEDULE_UPDATE_METRIC);
	}

	public static String baseStatNameOf(final HederaFunctionality function) {
		return BASE_STAT_NAMES.getOrDefault(function, function.toString());
	}

	public static List<AccountAmount> canonicalDiffRepr(final List<AccountAmount> a, final List<AccountAmount> b) {
		return canonicalRepr(Stream.concat(a.stream(), b.stream().map(MiscUtils::negationOf)).collect(toList()));
	}

	private static AccountAmount negationOf(final AccountAmount adjustment) {
		return adjustment.toBuilder().setAmount(-1 * adjustment.getAmount()).build();
	}

	public static List<AccountAmount> canonicalRepr(final List<AccountAmount> transfers) {
		return transfers.stream()
				.collect(toMap(AccountAmount::getAccountID, AccountAmount::getAmount, Math::addExact))
				.entrySet().stream()
				.filter(e -> e.getValue() != 0)
				.sorted(comparing(Map.Entry::getKey, HederaLedger.ACCOUNT_ID_COMPARATOR))
				.map(e -> AccountAmount.newBuilder().setAccountID(e.getKey()).setAmount(e.getValue()).build())
				.collect(toList());
	}

	public static String readableTransferList(final TransferList accountAmounts) {
		return accountAmounts.getAccountAmountsList()
				.stream()
				.map(aa -> String.format(
						"%s %s %s%s",
						EntityIdUtils.readableId(aa.getAccountID()),
						aa.getAmount() < 0 ? "->" : "<-",
						aa.getAmount() < 0 ? "-" : "+",
						BigInteger.valueOf(aa.getAmount()).abs().toString()))
				.collect(toList())
				.toString();
	}

	public static String readableNftTransferList(final TokenTransferList tokenTransferList) {
		return tokenTransferList.getNftTransfersList()
				.stream()
				.map(nftTransfer -> String.format(
						"%s %s %s",
						Long.valueOf(nftTransfer.getSerialNumber()).toString(),
						EntityIdUtils.readableId(nftTransfer.getSenderAccountID()),
						EntityIdUtils.readableId(nftTransfer.getReceiverAccountID())))
				.collect(toList())
				.toString();
	}

	public static JKey lookupInCustomStore(
			final LegacyEd25519KeyReader b64Reader,
			final String storeLoc,
			final String kpId
	) {
		try {
			return new JEd25519Key(CommonUtils.unhex(b64Reader.hexedABytesFrom(storeLoc, kpId)));
		} catch (IllegalArgumentException e) {
			final var msg = String.format("Arguments 'storeLoc=%s' and 'kpId=%s' did not denote a valid key!",
					storeLoc, kpId);
			throw new IllegalArgumentException(msg, e);
		}
	}

	public static String readableProperty(final Object o) {
		if (o instanceof FCQueue) {
			return ExpirableTxnRecord.allToGrpc(new ArrayList<>((FCQueue<ExpirableTxnRecord>) o)).toString();
		} else {
			return o.toString();
		}
	}

	public static JKey asFcKeyUnchecked(final Key key) {
		try {
			return JKey.mapKey(key);
		} catch (DecoderException impermissible) {
			throw new IllegalArgumentException("Key " + key + " should have been decode-able!", impermissible);
		}
	}

	public static Optional<JKey> asUsableFcKey(final Key key) {
		try {
			final var fcKey = JKey.mapKey(key);
			if (!fcKey.isValid()) {
				return Optional.empty();
			}
			return Optional.of(fcKey);
		} catch (DecoderException ignore) {
			return Optional.empty();
		}
	}

	public static Key asKeyUnchecked(final JKey fcKey) {
		try {
			return mapJKey(fcKey);
		} catch (Exception impossible) {
			return Key.getDefaultInstance();
		}
	}

	public static Timestamp asTimestamp(final Instant when) {
		return Timestamp.newBuilder()
				.setSeconds(when.getEpochSecond())
				.setNanos(when.getNano())
				.build();
	}

	public static Instant timestampToInstant(final Timestamp timestamp) {
		return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
	}

	public static Optional<QueryHeader> activeHeaderFrom(final Query query) {
		final var methodToSearch = "GET" + query.getQueryCase().name();
		try {
			final var getQueryOp = Stream.of(query.getClass().getDeclaredMethods())
					.filter(m -> methodToSearch.equals(m.getName().toUpperCase())).collect(toList()).get(0);
			final var queryOp = getQueryOp.invoke(query);
			final var getHeader = queryOp.getClass().getDeclaredMethod("getHeader");
			return Optional.of((QueryHeader) getHeader.invoke(queryOp));
		} catch (Exception ignoreToReturnOptionalEmpty) {
		}
		return Optional.empty();
	}

	static String getTxnStat(final TransactionBody txn) {
		try {
			return BASE_STAT_NAMES.get(functionOf(txn));
		} catch (UnknownHederaFunctionality unknownHederaFunctionality) {
			return "NotImplemented";
		}
	}

	public static HederaFunctionality functionOf(final TransactionBody txn) throws UnknownHederaFunctionality {
		return functionOf(txn, NON_SCHEDULE_FUNCTIONS).getLeft();
	}

	static Pair<HederaFunctionality, String> functionOf(
			final TransactionBody txn,
			final Set<HederaFunctionality> sameNameNonScheduleFunctions
	) throws UnknownHederaFunctionality {
		try {
			for (var f : SCHEDULE_FUNCTIONS) {
				if ((boolean) TRANSACTION_HAS_METHODS.get(TRANSACTION_FUNCTION_TO_STRINGS.get(f)).invoke(txn)) {
					return Pair.of(f, "");
				}
			}
			for (var f : sameNameNonScheduleFunctions) {
				if ((boolean) TRANSACTION_HAS_METHODS.get(f.name()).invoke(txn)) {
					return Pair.of(f, "");
				}
			}
		} catch (Exception e) {
			return Pair.of(UNRECOGNIZED, String.format(UNEXPECTED_RUNTIME_ERROR, e));
		}
		throw new UnknownHederaFunctionality();
	}

	public static Optional<HederaFunctionality> functionalityOfQuery(final Query query) {
		return Optional.ofNullable(queryFunctions.get(query.getQueryCase()));
	}

	public static String describe(final JKey k) {
		if (k == null) {
			return "<N/A>";
		} else {
			Key readable = null;
			try {
				readable = mapJKey(k);
			} catch (Exception ignore) {
			}
			return String.valueOf(readable);
		}
	}

	public static Set<AccountID> getNodeAccounts(final AddressBook addressBook) {
		return IntStream.range(0, addressBook.getSize())
				.mapToObj(addressBook::getAddress)
				.map(address -> parseAccount(address.getMemo()))
				.collect(toSet());
	}

	public static TransactionBody asOrdinary(final SchedulableTransactionBody scheduledTxn) {
		return asOrdinary(scheduledTxn, SCHEDULE_FUNCTION_STRINGS).getLeft();
	}

	static Pair<TransactionBody, String> asOrdinary(
			final SchedulableTransactionBody scheduledTxn,
			final Set<String> types
	) {
		final var ordinary = TransactionBody.newBuilder();
		ordinary.setTransactionFee(scheduledTxn.getTransactionFee())
				.setMemo(scheduledTxn.getMemo());
		for (var type : types) {
			try {
				if ((boolean) SCHEDULE_HAS_METHODS.get(type).invoke(scheduledTxn)) {
					final var op = SCHEDULE_GETTERS.get(type).invoke(scheduledTxn);
					TRANSACTION_SETTERS.get(type).invoke(ordinary, op);
					break;
				}
			} catch (Exception e) {
				return Pair.of(ordinary.build(), String.format(UNEXPECTED_RUNTIME_ERROR, e));
			}
		}
		return Pair.of(ordinary.build(), "");
	}

	/**
	 * A permutation (invertible function) on 64 bits. The constants were found
	 * by automated search, to optimize avalanche. Avalanche means that for a
	 * random number x, flipping bit i of x has about a 50 percent chance of
	 * flipping bit j of perm64(x). For each possible pair (i,j), this function
	 * achieves a probability between 49.8 and 50.2 percent.
	 *
	 * @param x
	 * 		the value to permute
	 * @return the avalanche-optimized permutation
	 */
	public static long perm64(long x) {
		// Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
		x += x << 30;
		x ^= x >>> 27;
		x += x << 16;
		x ^= x >>> 20;
		x += x << 5;
		x ^= x >>> 18;
		x += x << 10;
		x ^= x >>> 24;
		x += x << 30;
		return x;
	}

	public static <K extends MerkleNode, V extends MerkleNode> void forEach(
			final FCMap<K, V> map,
			final BiConsumer<? super K, ? super V> action
	) {
		map.forEachNode((@Nonnull final MerkleNode node) -> {
			if (node.getClassId() == MerklePair.CLASS_ID) {
				final MerklePair<K, V> pair = node.cast();
				action.accept(pair.getKey(), pair.getValue());
			}
		});
	}
}
