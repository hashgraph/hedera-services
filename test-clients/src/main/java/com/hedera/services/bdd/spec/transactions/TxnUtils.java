package com.hedera.services.bdd.spec.transactions;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.usage.SigUsage;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractInfo;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileInfo;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.HapiPropertySource.asFile;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSchedule;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTopic;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.FEE_MATRICES_CONST;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static com.hederahashgraph.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class TxnUtils {
	private static final Logger log = LogManager.getLogger(TxnUtils.class);

	public static final ResponseCodeEnum[] NOISY_RETRY_PRECHECKS = {
			BUSY, PLATFORM_TRANSACTION_NOT_CREATED, DUPLICATE_TRANSACTION
	};
	public final static ResponseCodeEnum[] NOISY_ALLOWED_STATUSES = {
			OK, SUCCESS, DUPLICATE_TRANSACTION
	};

	public static final int BYTES_4K = 4 * (1 << 10);

	private static Pattern ID_LITERAL_PATTERN = Pattern.compile("\\d+[.]\\d+[.]\\d+");
	private static Pattern PORT_LITERAL_PATTERN = Pattern.compile("\\d+");

	public static Key EMPTY_THRESHOLD_KEY = Key.newBuilder().setThresholdKey(ThresholdKey.getDefaultInstance()).build();
	public static Key EMPTY_KEY_LIST = Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();

	public static Key netOf(
			HapiApiSpec spec,
			Optional<String> keyName,
			Optional<? extends SigControl> keyShape,
			Optional<Supplier<KeyGenerator>> keyGenSupplier
	) {
		return netOf(spec, keyName, keyShape, Optional.empty(), keyGenSupplier);
	}

	public static List<Function<HapiApiSpec, Key>> defaultUpdateSigners(
			String owningEntity,
			Optional<String> newKeyName,
			Function<HapiApiSpec, String> effectivePayer
	) {
		List<Function<HapiApiSpec, Key>> signers = new ArrayList<>();
		signers.add(spec -> spec.registry().getKey(effectivePayer.apply(spec)));
		signers.add(spec -> spec.registry().getKey(owningEntity));
		if (newKeyName.isPresent()) {
			signers.add(spec -> spec.registry().getKey(newKeyName.get()));
		}
		return signers;
	}

	public static Key netOf(
			HapiApiSpec spec,
			Optional<String> keyName,
			Optional<? extends SigControl> keyShape,
			Optional<KeyFactory.KeyType> keyType,
			Optional<Supplier<KeyGenerator>> keyGenSupplier
	) {
		if (!keyName.isPresent()) {
			KeyGenerator generator = keyGenSupplier.get().get();
			if (keyShape.isPresent()) {
				return spec.keys().generateSubjectTo(keyShape.get(), generator);
			} else {
				return spec.keys().generate(keyType.orElse(spec.setup().defaultKeyType()), generator);
			}
		} else {
			return spec.registry().getKey(keyName.get());
		}
	}

	public static Duration asDuration(long secs) {
		return Duration.newBuilder().setSeconds(secs).build();
	}

	public static Timestamp asTimestamp(long secs) {
		return Timestamp.newBuilder().setSeconds(secs).build();
	}

	public static boolean isIdLiteral(String s) {
		return ID_LITERAL_PATTERN.matcher(s).matches();
	}

	public static boolean isPortLiteral(String s) {
		return PORT_LITERAL_PATTERN.matcher(s).matches();
	}

	public static AccountID asId(String s, HapiApiSpec lookupSpec) {
		return isIdLiteral(s) ? asAccount(s) : lookupSpec.registry().getAccountID(s);
	}

	public static TokenID asTokenId(String s, HapiApiSpec lookupSpec) {
		return isIdLiteral(s) ? asToken(s) : lookupSpec.registry().getTokenID(s);
	}

	public static ScheduleID asScheduleId(String s, HapiApiSpec lookupSpec) {
		return isIdLiteral(s) ? asSchedule(s) : lookupSpec.registry().getScheduleId(s);
	}

	public static TopicID asTopicId(String s, HapiApiSpec lookupSpec) {
		return isIdLiteral(s) ? asTopic(s) : lookupSpec.registry().getTopicID(s);
	}

	public static FileID asFileId(String s, HapiApiSpec lookupSpec) {
		return isIdLiteral(s) ? asFile(s) : lookupSpec.registry().getFileId(s);
	}

	public static ContractID asContractId(String s, HapiApiSpec lookupSpec) {
		return isIdLiteral(s) ? asContract(s) : lookupSpec.registry().getContractId(s);
	}

	public static String txnToString(Transaction txn) {
		try {
			return com.hedera.services.legacy.proto.utils.CommonUtils.toReadableString(txn);
		} catch (InvalidProtocolBufferException e) {
			log.error("Got Grpc protocol buffer error: ", e);
		}
		return null;
	}

	public static String getTxnIDandType(Transaction txn) {
		try {
			return com.hedera.services.legacy.proto.utils.CommonUtils.toReadableTransactionID(txn);
		} catch (InvalidProtocolBufferException e) {
			log.error("Got Grpc protocol buffer error: ", e);
		}
		return null;
	}

	public static boolean inConsensusOrder(Timestamp t1, Timestamp t2) {
		if (t1.getSeconds() < t2.getSeconds()) {
			return true;
		} else if (t1.getSeconds() == t2.getSeconds()) {
			return (t1.getNanos() < t2.getNanos());
		} else {
			return false;
		}
	}

	public static ContractID asContractId(byte[] bytes) {
		long realm = ByteUtil.byteArrayToLong(Arrays.copyOfRange(bytes, 4, 12));
		long accountNum = ByteUtil.byteArrayToLong(Arrays.copyOfRange(bytes, 12, 20));

		return ContractID.newBuilder()
				.setContractNum(accountNum)
				.setRealmNum(realm)
				.setShardNum(0L).build();
	}

	public static AccountID equivAccount(ContractID contract) {
		return AccountID.newBuilder()
				.setShardNum(contract.getShardNum())
				.setRealmNum(contract.getRealmNum())
				.setAccountNum(contract.getContractNum()).build();
	}

	public static SigUsage suFrom(SigValueObj svo) {
		return new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
	}

	public static Timestamp defaultTimestamp() {
		return getUniqueTimestampPlusSecs(0L);
	}

	public static Timestamp defaultTimestampPlusSecs(long offsetSecs) {
		Instant instant = Instant.now(Clock.systemUTC());
		return Timestamp.newBuilder()
				.setSeconds(instant.getEpochSecond() + offsetSecs)
				.setNanos(instant.getNano() - nanosBehind.addAndGet(1)).build();
	}

	private static int NANOS_IN_A_SECOND = 1_000_000_000;
	private static AtomicInteger NEXT_NANO = new AtomicInteger(0);
	private static int NANO_OFFSET = (int) System.currentTimeMillis() % 1_000;

	public static synchronized Timestamp getUniqueTimestampPlusSecs(long offsetSecs) {
		Instant instant = Instant.now(Clock.systemUTC());

		int candidateNano = NEXT_NANO.getAndIncrement() + NANO_OFFSET;
		if( candidateNano >= NANOS_IN_A_SECOND ) {
			candidateNano = 0;
			NEXT_NANO.set(1);
		}

		Timestamp uniqueTS = Timestamp.newBuilder()
				.setSeconds(instant.getEpochSecond() + offsetSecs)
				.setNanos(candidateNano).build();

		return uniqueTS;
	}

	public static TransactionID asTransactionID(HapiApiSpec spec, Optional<String> payer) {
		var payerID = spec.registry().getAccountID(payer.orElse(spec.setup().defaultPayerName()));
		var validStart = getUniqueTimestampPlusSecs(spec.setup().txnStartOffsetSecs());
		return TransactionID.newBuilder()
				.setTransactionValidStart(validStart)
				.setAccountID(payerID).build();
	}

	private static AtomicInteger nanosBehind = new AtomicInteger(0);

	public static String solidityIdFrom(ContractID contract) {
		return ByteUtil.toHexString(ByteUtil.merge(
				ByteUtil.intToBytes((int) contract.getShardNum()),
				ByteUtil.longToBytes(contract.getRealmNum()),
				ByteUtil.longToBytes(contract.getContractNum())));
	}

	public static TransactionID extractTxnId(Transaction txn) throws Throwable {
		return extractTransactionBody(txn).getTransactionID();
	}

	public static TransferList asTransferList(List<AccountAmount>... specifics) {
		TransferList.Builder builder = TransferList.newBuilder();
		Arrays.stream(specifics).forEach(builder::addAllAccountAmounts);
		return builder.build();
	}

	public static List<AccountAmount> tinyBarsFromTo(long amount, AccountID from, AccountID to) {
		return Arrays.asList(
				AccountAmount.newBuilder().setAccountID(from).setAmount(-1L * amount).build(),
				AccountAmount.newBuilder().setAccountID(to).setAmount(amount).build());
	}

	public static String printable(TransferList transfers) {
		return transfers
				.getAccountAmountsList()
				.stream()
				.map(adjust -> String.format(
						"%d %s %d",
						adjust.getAmount(),
						adjust.getAmount() < 0L ? "from" : "to",
						adjust.getAccountID().getAccountNum()))
				.collect(joining(", "));
	}

	public static Timestamp currExpiry(String file, HapiApiSpec spec, String payer) throws Throwable {
		HapiGetFileInfo subOp = getFileInfo(file).payingWith(payer).noLogging();
		Optional<Throwable> error = subOp.execFor(spec);
		if (error.isPresent()) {
			log.error("Unable to look up current expiration timestamp of file 0.0."
					+ spec.registry().getFileId(file).getFileNum());
			throw error.get();
		}
		return subOp.getResponse().getFileGetInfo().getFileInfo().getExpirationTime();
	}

	public static Timestamp currExpiry(String file, HapiApiSpec spec) throws Throwable {
		return currExpiry(file, spec, spec.setup().defaultPayerName());
	}

	public static Timestamp currContractExpiry(String contract, HapiApiSpec spec) throws Throwable {
		HapiGetContractInfo subOp = getContractInfo(contract).noLogging();
		Optional<Throwable> error = subOp.execFor(spec);
		if (error.isPresent()) {
			log.error("Unable to look up current expiration timestamp of contract 0.0."
					+ spec.registry().getContractId(contract).getContractNum());
			throw error.get();
		}
		return subOp.getResponse().getContractGetInfo().getContractInfo().getExpirationTime();
	}

	public static TopicID asTopicId(AccountID id) {
		return TopicID.newBuilder()
				.setShardNum(id.getShardNum())
				.setRealmNum(id.getRealmNum())
				.setTopicNum(id.getAccountNum())
				.build();
	}

	public static byte[] randomUtf8Bytes(int n) {
		byte[] data = new byte[n];
		int i = 0;
		while (i < n) {
			byte[] rnd = UUID.randomUUID().toString().getBytes();
			System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
			i += rnd.length;
		}
		return data;
	}

	public static String randomUppercase(int l) {
		var sb = new StringBuilder();
		for (int i = 0, n = CANDIDATES.length; i < l; i++) {
			sb.append(CANDIDATES[r.nextInt(n)]);
		}
		return sb.toString();
	}
	private static final SplittableRandom r = new SplittableRandom();
	private static final char[] CANDIDATES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

	public static String readableTokenTransfers(List<TokenTransferList> tokenTransfers) {
		return tokenTransfers.stream()
				.map(scopedXfers -> String.format("%s(%s)",
						asTokenString(scopedXfers.getToken()),
						readableTransferList(scopedXfers.getTransfersList())))
				.collect(joining(", "));
	}

	public static String readableTransferList(TransferList accountAmounts) {
		return readableTransferList(accountAmounts.getAccountAmountsList());
	}

	public static String readableTransferList(List<AccountAmount> adjustments) {
		return adjustments
				.stream()
				.map(aa -> String.format(
						"%s %s %s%s",
						HapiPropertySource.asAccountString(aa.getAccountID()),
						aa.getAmount() < 0 ? "->" : "<-",
						aa.getAmount() < 0 ? "-" : "+",
						BigInteger.valueOf(aa.getAmount()).abs().toString()))
				.collect(toList())
				.toString();
	}

	public static OptionalLong getDeduction(TransferList accountAmounts, AccountID payer) {
		var deduction = accountAmounts.getAccountAmountsList()
				.stream()
				.filter(aa -> aa.getAccountID().equals(payer) && aa.getAmount() < 0)
				.findAny();
		return deduction.isPresent() ? OptionalLong.of(deduction.get().getAmount()) : OptionalLong.empty();
	}

	public static FeeData defaultPartitioning(FeeComponents components, int numPayerKeys) {
		var partitions = FeeData.newBuilder();

		long networkRbh = nonDegenerateDiv(BASIC_RECEIPT_SIZE * RECEIPT_STORAGE_TIME_SEC, HRS_DIVISOR);
		var network = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(components.getBpt())
				.setVpt(components.getVpt())
				.setRbh(networkRbh);

		var node = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(components.getBpt())
				.setVpt(numPayerKeys)
				.setBpr(components.getBpr())
				.setSbpr(components.getSbpr());

		var service = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setRbh(components.getRbh())
				.setSbh(components.getSbh())
				.setTv(components.getTv());

		partitions.setNetworkdata(network).setNodedata(node).setServicedata(service);

		return partitions.build();
	}

	public static long nonDegenerateDiv(long dividend, int divisor) {
		return (dividend == 0) ? 0 : Math.max(1, dividend / divisor);
	}

	// Following methods are for negative test cases purpose, use with caution

	public static Transaction replaceTxnMemo(Transaction txn, String newMemo) {
		try {
			TransactionBody.Builder txnBody = TransactionBody.newBuilder().mergeFrom(txn.getBodyBytes());
			txnBody.setMemo(newMemo);
			return txn.toBuilder().setBodyBytes(txnBody.build().toByteString()).build();
		} catch (Exception e) {
			log.warn("Transaction's body can't be parsed: {}", txnToString(txn), e);
		}
		return null;
	}

	public static Transaction replaceTxnPayerAccount(Transaction txn, AccountID accountID) {
		Transaction newTxn = Transaction.getDefaultInstance();
		try {
			TransactionBody.Builder txnBody = TransactionBody.newBuilder().mergeFrom(txn.getBodyBytes());
			txnBody.setTransactionID(TransactionID.newBuilder()
					.setAccountID(accountID)
					.setTransactionValidStart(txnBody.getTransactionID().getTransactionValidStart()).build());
			return txn.toBuilder().setBodyBytes(txnBody.build().toByteString()).build();
		} catch (Exception e) {
			log.warn("Transaction's body can't be parsed: {}", txnToString(txn), e);
		}
		return null;
	}

	public static Transaction replaceTxnStartTime(Transaction txn, long newStartTimeSecs, int newStartTimeNanos) {
		try {
			TransactionBody.Builder txnBody = TransactionBody.newBuilder().mergeFrom(txn.getBodyBytes());

			txnBody.setTransactionID(TransactionID.newBuilder()
					.setAccountID(txnBody.getTransactionID().getAccountID())
					.setTransactionValidStart(Timestamp.newBuilder().setSeconds(newStartTimeSecs).setNanos(newStartTimeNanos).build())
					.build());
			return txn.toBuilder().setBodyBytes(txnBody.build().toByteString()).build();
		} catch (Exception e) {
			log.warn("Transaction's body can't be parsed: {}", txnToString(txn), e);
		}
		return null;
	}

	public static Transaction replaceTxnDuration(Transaction txn, long newDuration) {
		try {
			TransactionBody.Builder txnBody = TransactionBody.newBuilder().mergeFrom(txn.getBodyBytes());
			txnBody.setTransactionValidDuration(Duration.newBuilder().setSeconds(newDuration).build());
			return txn.toBuilder().setBodyBytes(txnBody.build().toByteString()).build();
		} catch (Exception e) {
			log.warn("Transaction's body can't be parsed: {}", txnToString(txn), e);
		}
		return null;
	}

	public static Transaction replaceTxnNodeAccount(Transaction txn, AccountID newNodeAccount) {
		log.info(String.format("Old Txn attr: %s", TxnUtils.txnToString(txn) ));
		Transaction newTxn = Transaction.getDefaultInstance();
		try {

			TransactionBody.Builder txnBody = TransactionBody.newBuilder().mergeFrom(txn.getBodyBytes());
			txnBody.setNodeAccountID(newNodeAccount);
			return txn.toBuilder().setBodyBytes(txnBody.build().toByteString()).build();
		} catch (Exception e) {
			log.warn("Transaction's body can't be parsed: {}", txnToString(txn), e);
		}
		return null;
	}
}
