package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
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
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.ledger.HederaLedger;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcqueue.FCQueue;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONSENSUSGETTOPICINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONTRACTCALLLOCAL;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONTRACTGETBYTECODE;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONTRACTGETINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CONTRACTGETRECORDS;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CRYPTOGETACCOUNTBALANCE;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CRYPTOGETACCOUNTRECORDS;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CRYPTOGETLIVEHASH;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.CRYPTOGETINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.FILEGETCONTENTS;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.FILEGETINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.GETBYKEY;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.GETBYSOLIDITYID;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.NETWORKGETVERSIONINFO;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TRANSACTIONGETRECEIPT;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TRANSACTIONGETRECORD;
import static com.hedera.services.legacy.core.jproto.JKey.mapJKey;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

public class MiscUtils {
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
	}

	public static List<AccountAmount> canonicalDiffRepr(List<AccountAmount> a, List<AccountAmount> b) {
		return canonicalRepr(Stream.concat(a.stream(), b.stream().map(MiscUtils::negationOf)).collect(toList()));
	}

	private static AccountAmount negationOf(AccountAmount adjustment) {
		return adjustment.toBuilder().setAmount(-1 * adjustment.getAmount()).build();
	}

	public static List<AccountAmount> canonicalRepr(List<AccountAmount> transfers) {
		return transfers.stream()
				.collect(toMap(AccountAmount::getAccountID, AccountAmount::getAmount, Math::addExact))
				.entrySet().stream()
				.filter(e -> e.getValue() != 0)
				.sorted(comparing(Map.Entry::getKey, HederaLedger.ACCOUNT_ID_COMPARATOR))
				.map(e -> AccountAmount.newBuilder().setAccountID(e.getKey()).setAmount(e.getValue()).build())
				.collect(toList());
	}

	public static String readableTransferList(TransferList accountAmounts) {
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

	public static JKey lookupInCustomStore(LegacyEd25519KeyReader b64Reader, String storeLoc, String kpId) {
		try {
			return new JEd25519Key(commonsHexToBytes(b64Reader.hexedABytesFrom(storeLoc, kpId)));
		} catch (DecoderException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static String readableProperty(Object o) {
		if (o instanceof FCQueue) {
			return ExpirableTxnRecord.allToGrpc(new ArrayList<>((FCQueue<ExpirableTxnRecord>) o)).toString();
		} else {
			return o.toString();
		}
	}

	public static JKey asFcKeyUnchecked(Key key) {
		try {
			return JKey.mapKey(key);
		} catch (Exception impossible) {
			throw new IllegalArgumentException("Key " + key + " should have been decodable!", impossible);
		}
	}

	public static Key asKeyUnchecked(JKey fcKey) {
		try {
			return mapJKey(fcKey);
		} catch (Exception impossible) {
			return Key.getDefaultInstance();
		}
	}

	public static ByteString sha384HashOf(PlatformTxnAccessor accessor) {
		return ByteString.copyFrom(uncheckedSha384Hash(accessor.getSignedTxn().toByteArray()));
	}

	public static Timestamp asTimestamp(Instant when) {
		return Timestamp.newBuilder()
				.setSeconds(when.getEpochSecond())
				.setNanos(when.getNano())
				.build();
	}

	public static Optional<QueryHeader> activeHeaderFrom(Query query) {
		switch (query.getQueryCase()) {
			case CONSENSUSGETTOPICINFO:
				return Optional.of(query.getConsensusGetTopicInfo().getHeader());
			case GETBYSOLIDITYID:
				return Optional.of(query.getGetBySolidityID().getHeader());
			case CONTRACTCALLLOCAL:
				return Optional.of(query.getContractCallLocal().getHeader());
			case CONTRACTGETINFO:
				return Optional.of(query.getContractGetInfo().getHeader());
			case CONTRACTGETBYTECODE:
				return Optional.of(query.getContractGetBytecode().getHeader());
			case CONTRACTGETRECORDS:
				return Optional.of(query.getContractGetRecords().getHeader());
			case CRYPTOGETACCOUNTBALANCE:
				return Optional.of(query.getCryptogetAccountBalance().getHeader());
			case CRYPTOGETACCOUNTRECORDS:
				return Optional.of(query.getCryptoGetAccountRecords().getHeader());
			case CRYPTOGETINFO:
				return Optional.of(query.getCryptoGetInfo().getHeader());
			case CRYPTOGETLIVEHASH:
				return Optional.of(query.getCryptoGetLiveHash().getHeader());
			case CRYPTOGETPROXYSTAKERS:
				return Optional.of(query.getCryptoGetProxyStakers().getHeader());
			case FILEGETCONTENTS:
				return Optional.of(query.getFileGetContents().getHeader());
			case FILEGETINFO:
				return Optional.of(query.getFileGetInfo().getHeader());
			case TRANSACTIONGETRECEIPT:
				return Optional.of(query.getTransactionGetReceipt().getHeader());
			case TRANSACTIONGETRECORD:
				return Optional.of(query.getTransactionGetRecord().getHeader());
			case TRANSACTIONGETFASTRECORD:
				return Optional.of(query.getTransactionGetFastRecord().getHeader());
			case NETWORKGETVERSIONINFO:
				return Optional.of(query.getNetworkGetVersionInfo().getHeader());
			default:
				return Optional.empty();
		}
	}

	public static String getTxnStat(TransactionBody txn) {
		if (txn.hasCryptoCreateAccount()) {
			return "createAccount";
		} else if (txn.hasCryptoUpdateAccount()) {
			return "updateAccount";
		} else if (txn.hasCryptoTransfer()) {
			return "cryptoTransfer";
		} else if (txn.hasCryptoDelete()) {
			return "cryptoDelete";
		} else if (txn.hasContractCreateInstance()) {
			return "createContract";
		} else if (txn.hasContractCall()) {
			return "contractCallMethod";
		} else if (txn.hasContractUpdateInstance()) {
			return "updateContract";
		} else if (txn.hasContractDeleteInstance()) {
			return "deleteContract";
		} else if (txn.hasCryptoAddLiveHash()) {
			return "addLiveHash";
		} else if (txn.hasCryptoDeleteLiveHash()) {
			return "deleteLiveHash";
		} else if (txn.hasFileCreate()) {
			return "createFile";
		} else if (txn.hasFileAppend()) {
			return "appendContent";
		} else if (txn.hasFileUpdate()) {
			return "updateFile";
		} else if (txn.hasFileDelete()) {
			return "deleteFile";
		} else if (txn.hasSystemDelete()) {
			return "systemDelete";
		} else if (txn.hasSystemUndelete()) {
			return "systemUndelete";
		} else if (txn.hasFreeze()) {
			return "freeze";
		} else if (txn.hasConsensusCreateTopic()) {
			return "createTopic";
		} else if (txn.hasConsensusUpdateTopic()) {
			return "updateTopic";
		} else if (txn.hasConsensusDeleteTopic()) {
			return "deleteTopic";
		} else if (txn.hasConsensusSubmitMessage()) {
			return "submitMessage";
		} else {
			return "NotImplemented";
		}
	}

	public static HederaFunctionality functionalityOfTxn(TransactionBody txn) throws UnknownHederaFunctionality {
		if (txn.hasSystemDelete()) {
			return SystemDelete;
		} else if (txn.hasSystemUndelete()) {
			return SystemUndelete;
		} else if (txn.hasContractCall()) {
			return ContractCall;
		} else if (txn.hasContractCreateInstance()) {
			return ContractCreate;
		} else if (txn.hasContractUpdateInstance()) {
			return ContractUpdate;
		} else if (txn.hasCryptoAddLiveHash()) {
			return CryptoAddLiveHash;
		} else if (txn.hasCryptoCreateAccount()) {
			return CryptoCreate;
		} else if (txn.hasCryptoDelete()) {
			return CryptoDelete;
		} else if (txn.hasCryptoDeleteLiveHash()) {
			return CryptoDeleteLiveHash;
		} else if (txn.hasCryptoTransfer()) {
			return CryptoTransfer;
		} else if (txn.hasCryptoUpdateAccount()) {
			return CryptoUpdate;
		} else if (txn.hasFileAppend()) {
			return FileAppend;
		} else if (txn.hasFileCreate()) {
			return FileCreate;
		} else if (txn.hasFileDelete()) {
			return FileDelete;
		} else if (txn.hasFileUpdate()) {
			return FileUpdate;
		} else if (txn.hasContractDeleteInstance()) {
			return ContractDelete;
		} else if (txn.hasFreeze()) {
			return Freeze;
		} else if (txn.hasConsensusCreateTopic()) {
			return ConsensusCreateTopic;
		} else if (txn.hasConsensusUpdateTopic()) {
			return ConsensusUpdateTopic;
		} else if (txn.hasConsensusDeleteTopic()) {
			return ConsensusDeleteTopic;
		} else if (txn.hasConsensusSubmitMessage()) {
			return ConsensusSubmitMessage;
		} else {
			throw new UnknownHederaFunctionality();
		}
	}

	public static Optional<HederaFunctionality> functionalityOfQuery(Query query) {
		return Optional.ofNullable(queryFunctions.get(query.getQueryCase()));
	}

	public static String commonsBytesToHex(byte[] data) {
		return Hex.encodeHexString(data);
	}

	public static byte[] commonsHexToBytes(String literal) throws DecoderException {
		return Hex.decodeHex(literal);
	}

	public static byte[] uncheckedSha384Hash(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-384").digest(data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static String describe(JKey k) {
		if (k == null) {
			return "<N/A>";
		} else {
			Key readable = null;
			try {
				readable = mapJKey(k);
			} catch (Exception ignore) { }
			return String.valueOf(readable);
		}
	}
}
