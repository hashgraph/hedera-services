package com.hedera.services.legacy.handler;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.domain.security.PermissionedAccountsRange;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.legacy.service.GlobalFlag;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.records.RecordCache;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.TransactionThrottling;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.txns.validation.PureValidation;
import com.hedera.services.txns.validation.TransferListChecks;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.FeeObject;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.legacy.exception.KeySignatureCountMismatchException;
import com.hedera.services.legacy.exception.KeySignatureTypeMismatchException;
import com.hedera.services.legacy.exception.PlatformTransactionCreationException;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.legacy.logic.ProtectedEntities;
import com.hedera.services.legacy.utils.TransactionValidationUtils;
import com.swirlds.common.Platform;
import com.swirlds.common.PlatformStatus;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.context.domain.security.PermissionFileUtils.permissionFileKeyForQuery;
import static com.hedera.services.context.domain.security.PermissionFileUtils.permissionFileKeyForTxn;
import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.utils.MiscUtils.activeHeaderFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER_STATE_PROOF;

/**
 * @author Akshay
 * @Date : 7/23/2018
 */
@Deprecated
public class TransactionHandler {
  public static final String GET_TOPIC_INFO_QUERY_NAME = "getTopicInfo";
  public static final Predicate<AccountID> IS_THROTTLE_EXEMPT =
          id -> id.getAccountNum() >= 1 && id.getAccountNum() <= 100L;

  private EnumSet<ResponseType> UNSUPPORTED_RESPONSE_TYPES = EnumSet.of(ANSWER_STATE_PROOF, COST_ANSWER_STATE_PROOF);

  private static final Logger log = LogManager.getLogger(TransactionHandler.class);
  private RecordCache recordCache;
  private PrecheckVerifier precheckVerifier;
  private Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
  private FunctionalityThrottling throttling;
  private AccountID nodeAccount;
  private TransactionThrottling txnThrottling;
  private UsagePricesProvider usagePrices;
  private HbarCentExchange exchange;
  private FeeCalculator fees;
  private Supplier<StateView> stateView;
  private BasicPrecheck basicPrecheck;
  private QueryFeeCheck queryFeeCheck;
  private AccountNumbers accountNums;

  public void setBasicPrecheck(BasicPrecheck basicPrecheck) {
    this.basicPrecheck = basicPrecheck;
  }

  public void setThrottling(FunctionalityThrottling throttling) {
    this.throttling = throttling;
    this.txnThrottling = new TransactionThrottling(throttling);
  }

  public TransactionHandler(
          RecordCache recordCache,
          Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
          AccountID nodeAccount,
          PrecheckVerifier precheckVerifier,
          UsagePricesProvider usagePrices,
          HbarCentExchange exchange,
          FeeCalculator fees,
          Supplier<StateView> stateView,
          BasicPrecheck basicPrecheck,
          QueryFeeCheck queryFeeCheck,
          AccountNumbers accountNums
  ) {
  	this.fees = fees;
  	this.stateView = stateView;
    this.exchange = exchange;
    this.usagePrices = usagePrices;
    this.recordCache = recordCache;
    this.accounts = accounts;
    this.nodeAccount = nodeAccount;
    this.precheckVerifier = precheckVerifier;
    this.basicPrecheck = basicPrecheck;
    this.queryFeeCheck = queryFeeCheck;
    this.accountNums = accountNums;
    throttling = function -> false;
    txnThrottling = new TransactionThrottling(throttling);
  }

  public TransactionHandler(
          RecordCache recordCache,
          PrecheckVerifier verifier,
          Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
          AccountID nodeAccount,
          AccountNumbers accountNums
  ) {
    this(recordCache, verifier, accounts, nodeAccount,
            null, null, null, null,
            null, null, null, null, accountNums);
  }

  public TransactionHandler(
          RecordCache recordCache,
          PrecheckVerifier precheckVerifier,
          Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
          AccountID nodeAccount,
          TransactionThrottling txnThrottling,
          UsagePricesProvider usagePrices,
          HbarCentExchange exchange,
          FeeCalculator fees,
          Supplier<StateView> stateView,
          BasicPrecheck basicPrecheck,
          QueryFeeCheck queryFeeCheck,
          FunctionalityThrottling throttling,
          AccountNumbers accountNums
  ) {
    this.fees = fees;
    this.stateView = stateView;
    this.exchange = exchange;
    this.recordCache = recordCache;
    this.precheckVerifier = precheckVerifier;
    this.accounts = accounts;
    this.nodeAccount = nodeAccount;
    this.basicPrecheck = basicPrecheck;
    this.txnThrottling = txnThrottling;
    this.usagePrices = usagePrices;
    this.queryFeeCheck = queryFeeCheck;
    this.throttling = throttling;
    this.accountNums = accountNums;
  }

  public ResponseCodeEnum nodePaymentValidity(Transaction signedTxn, long fee) {
    try {
      var txn = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signedTxn);
      var transfers = txn.getCryptoTransfer().getTransfers().getAccountAmountsList();
      return queryFeeCheck.nodePaymentValidity(transfers, fee, txn.getNodeAccountID());
    } catch (Exception ignore) {
      return INVALID_TRANSACTION_BODY;
    }
  }

  /**
   * Method to validate Fee and Submit Transaction used only for query header payment
   */
  public ResponseCodeEnum validateScheduledFee(
          HederaFunctionality function,
          Transaction transaction,
          long scheduledFee
  ) {
    ResponseCodeEnum validationCode = OK;
    if (scheduledFee > 0) {
      validationCode = validateTransactionPreConsensus(transaction, true).getValidity();
      if (validationCode != OK) {
        log.debug("Pre Consensus validation failed.");
        return validationCode;
      }
      validationCode = nodePaymentValidity(transaction, scheduledFee);
    }
    boolean isThrottleExempt = false;
	try {
	  TransactionBody txn = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(transaction);
	  isThrottleExempt = IS_THROTTLE_EXEMPT.test(txn.getTransactionID().getAccountID());
	} catch (Exception ignore) { }

	if (!isThrottleExempt && throttling.shouldThrottle(function)) {
      validationCode = ResponseCodeEnum.BUSY;
    }
    return validationCode;
  }

  public boolean isAccountExist(AccountID acctId) {
    MerkleEntityId merkleEntityId = new MerkleEntityId(acctId.getShardNum(), acctId.getRealmNum(), acctId.getAccountNum());
    return accounts.get().get(merkleEntityId) != null;
  }

  public void addReceiptEntry(TransactionID txnId) {
    recordCache.addPreConsensus(txnId);
  }

  /**
   * @param trBody body of the transaction
   * @return ResponseCodeEnum.CONTRACT_GAS_NEGATIVE if this is a contract creation or call and the
   * gas is negative, ResponseCodeEnum.CONTRACT_VALUE_NEGATIVE if a contract create / call with
   * negative initial balance / value, else OK.
   */
  private ResponseCodeEnum validateContractPositiveValues(TransactionBody trBody) {
    long gas = 0;
    long value = 0;
    ResponseCodeEnum returnCode = OK;
    if (trBody.hasContractCreateInstance()) {
      gas = trBody.getContractCreateInstance().getGas();
      value = trBody.getContractCreateInstance().getInitialBalance();
    } else if (trBody.hasContractCall()) {
      gas = trBody.getContractCall().getGas();
      value = trBody.getContractCall().getAmount();
    }
    if (gas < 0) {
      returnCode = ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
    } else if (value < 0) {
      returnCode = ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
    }
    return returnCode;
  }

  /**
   * @param trBody body of the transaction
   * @return ResponseCodeEnum.MEMO_TOO_LONG if this is a contract creation or update and the memo is
   * longer than 100 characters, else OK.
   */
  private ResponseCodeEnum validateContractMemoSize(TransactionBody trBody) {
    String memo = null;
    ResponseCodeEnum returnCode = OK;
    if (trBody.hasContractCreateInstance()) {
      memo = trBody.getContractCreateInstance().getMemo();
    } else if (trBody.hasContractUpdateInstance()) {
      memo = trBody.getContractUpdateInstance().getMemo();
    }
    if (memo != null && memo.length() > 100) {
      returnCode = ResponseCodeEnum.MEMO_TOO_LONG;
    }
    return returnCode;
  }

  /**
   * validates node account id against current node account
   *
   * @param trBody body of the transaction
   * @return NodeTransactionPrecheckCode.OK is returned if node account in transaction body matches
   * current node account otherwise INVALID_NODE_ACCOUNT should be returned
   */
  public ResponseCodeEnum validateNodeAccount(TransactionBody trBody) {
    ResponseCodeEnum returnCode = ResponseCodeEnum.INVALID_NODE_ACCOUNT;
    if (trBody.hasNodeAccountID() && trBody.getNodeAccountID().equals(this.nodeAccount)) {
      returnCode = OK;
    }
    return returnCode;
  }

  private ResponseCodeEnum validateTransactionThrottling(TransactionBody txn) {
    AccountID payer = txn.getTransactionID().getAccountID();
    if (IS_THROTTLE_EXEMPT.test(payer)) {
      return OK;
    } else {
      return txnThrottling.shouldThrottle(txn) ? BUSY : OK;
    }
  }

  /**
   * Validates if given transaction is permitted for payer account based on api-permission.property
   * file
   *
   * @return NOT_SUPPORTED if permission is not granted OK otherwise
   */
  private ResponseCodeEnum validateApiPermission(TransactionBody txn) {
    var permissionKey = permissionFileKeyForTxn(txn);
    if (!StringUtils.isEmpty(permissionKey)) {
      var payer = txn.getTransactionID().getAccountID();
      if (accountNums.isSysAdmin(payer.getAccountNum())) {
        return OK;
      } else {
        PermissionedAccountsRange accountRange =  PropertiesLoader.getApiPermission().get(permissionKey);
        if (accountRange != null) {
        	return accountRange.contains(payer.getAccountNum()) ? OK : NOT_SUPPORTED;
        }
      }
    }
    return NOT_SUPPORTED;
  }

  private TxnValidityAndFeeReq validateTransactionFeeCoverage(Transaction transaction, TransactionBody trBody) {
    ResponseCodeEnum returnCode = OK;
    if (ProtectedEntities.isFree(trBody)) {
      return new TxnValidityAndFeeReq(returnCode);
    }

    long fee = 0L;
    long feeRequired = 0L;
    if (trBody.getTransactionID().hasAccountID()) {
      AccountID payerAccount = trBody.getTransactionID().getAccountID();
      if (isAccountExist(payerAccount)) {
        Long payerAccountBalance = Optional.ofNullable(accounts.get().get(fromAccountId(payerAccount)))
                .map(MerkleAccount::getBalance)
                .orElse(null);
        long suppliedFee = trBody.getTransactionFee();
        long payerChangeDuringTxn = !trBody.hasCryptoTransfer() ? 0L :
            trBody.getCryptoTransfer().getTransfers().getAccountAmountsList()
                    .stream()
                    .filter(aa -> aa.getAccountID().equals(payerAccount))
                    .mapToLong(AccountAmount::getAmount)
					.sum();
        if (suppliedFee > (payerAccountBalance + Math.min(0L, payerChangeDuringTxn))) {
          returnCode = ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
        }

        if (returnCode == OK) {
          try {
            SignedTxnAccessor accessor = new SignedTxnAccessor(transaction);
            JKey payerKey = accounts.get().get(fromAccountId(payerAccount)).getKey();
            Timestamp at = trBody.getTransactionID().getTransactionValidStart();
            FeeObject txnFee = fees.estimateFee(accessor, payerKey, stateView.get(), at);
            fee = txnFee.getNetworkFee() + txnFee.getNodeFee() + txnFee.getServiceFee();
          } catch (Exception e) {
            log.error("Could not calculate fee for transaction. " + e);
            returnCode = ResponseCodeEnum.FAIL_FEE;
          }
        }
        if (returnCode == OK) {
          if (suppliedFee < fee) {
            returnCode = ResponseCodeEnum.INSUFFICIENT_TX_FEE;
            feeRequired = fee;
          } else if (payerAccountBalance < fee) {
            returnCode = ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
          } else if (trBody.hasContractCreateInstance()) {
            ContractCreateTransactionBody createBody = trBody.getContractCreateInstance();
            long gasInTransacton = createBody.getGas();
            long gasCost = gasInTransacton * getGasPrice(HederaFunctionality.ContractCreate);
            long balance = createBody.getInitialBalance();
            if (payerAccountBalance < (fee + gasCost + balance)) {
              returnCode = ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
            }
          } else if (trBody.hasContractCall()) {
            ContractCallTransactionBody callBody = trBody.getContractCall();
            long gasCost = callBody.getGas() * getGasPrice(HederaFunctionality.ContractCall);
            long amount = callBody.getAmount();
            if (payerAccountBalance < (fee + gasCost + amount)) {
              returnCode = ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
            }
          }
        }
      } else {
        returnCode = ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
      }
    } else {
      returnCode = ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
    }
    return new TxnValidityAndFeeReq(returnCode, feeRequired);
  }

  public long getGasPrice(HederaFunctionality function) {
    Timestamp at = Timestamp.newBuilder()
            .setSeconds(System.currentTimeMillis() / 1000)
            .build();

    FeeData prices = usagePrices.pricesGiven(function, at);
    long feeInTinyCents = prices.getServicedata().getGas() / 1000;
    long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(at), feeInTinyCents);
    return Math.max(feeInTinyBars, 1L);
  }

  public TxnValidityAndFeeReq validateTransactionPreConsensus(Transaction transaction, boolean isQueryPayment) {
    if (checkPlatformStatus()) {
      return new TxnValidityAndFeeReq(ResponseCodeEnum.PLATFORM_NOT_ACTIVE);
    }

    if (!TransactionValidationUtils.validateTxSize(transaction)) {
      if (log.isDebugEnabled()) {
        log.debug("Size of the transaction exceeds transactionMaxBytes: "
            + Platform.getTransactionMaxBytes());
      }
      return new TxnValidityAndFeeReq(ResponseCodeEnum.TRANSACTION_OVERSIZE);
    }

    if (!TransactionValidationUtils.validateTxDepth(transaction)) {
      log.debug("Request transaction has too many layers.");
      return new TxnValidityAndFeeReq(ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS);
    }

    ResponseCodeEnum returnCode = OK;
    long feeRequired = 0L;
    TransactionBody txn = TransactionBody.getDefaultInstance();
    try {
      txn = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(transaction);
    } catch (Exception e1) {
      returnCode = INVALID_TRANSACTION_BODY;
    }

    if (returnCode == OK && !TransactionValidationUtils.validateTxBodyDepth(txn)) {
      return new TxnValidityAndFeeReq(ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS);
    }

    if (returnCode == OK && !(isQueryPayment && txn.hasCryptoTransfer())) {
      returnCode = validateApiPermission(txn);
    }

    if (returnCode == OK) {
      returnCode = basicPrecheck.validate(txn);
    }

    if (returnCode == OK) {
        var rationalStatus = PureValidation.queryableAccountStatus(txn.getTransactionID().getAccountID(), accounts.get());
        returnCode = (rationalStatus == INVALID_ACCOUNT_ID) ? PAYER_ACCOUNT_NOT_FOUND : OK;
    }

    if (returnCode == OK) {
      returnCode = recordCache.isReceiptPresent(txn.getTransactionID()) ? DUPLICATE_TRANSACTION : OK;
    }

    if (returnCode == OK) {
      returnCode = validateNodeAccount(txn);
    }

    if (returnCode == OK && txn.hasCryptoTransfer()) {
      if (TransferListChecks.hasRepeatedAccount(txn.getCryptoTransfer().getTransfers())) {
        returnCode = ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
      }
    }

    if (returnCode == OK) {
      try {
        if (!verifySignature(transaction)) {
          returnCode = ResponseCodeEnum.INVALID_SIGNATURE;
        }
      } catch (KeySignatureTypeMismatchException e) {
        returnCode = ResponseCodeEnum.INVALID_SIGNATURE_TYPE_MISMATCHING_KEY;
      } catch (KeySignatureCountMismatchException e) {
        returnCode = ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
      } catch (InvalidAccountIDException e) {
        returnCode = ResponseCodeEnum.INVALID_ACCOUNT_ID;
      } catch (KeyPrefixMismatchException e) {
        returnCode = ResponseCodeEnum.KEY_PREFIX_MISMATCH;
      } catch (Exception e) {
        returnCode = ResponseCodeEnum.INVALID_SIGNATURE;
      }
    }

    if (returnCode == OK) {
      returnCode = ProtectedEntities.validateProtectedEntities(txn);
    }

    if (returnCode == OK) {
      TxnValidityAndFeeReq localResp = validateTransactionFeeCoverage(transaction, txn);
      returnCode = localResp.getValidity();
      feeRequired = localResp.getRequiredFee();
    }

    if (!(isQueryPayment && txn.hasCryptoTransfer()) && returnCode == OK) {
      returnCode = validateTransactionThrottling(txn);
    }

    if (returnCode == OK) {
      returnCode = validateContractPositiveValues(txn);
    }

    if (returnCode == OK) {
      returnCode = validateContractMemoSize(txn);
    }

    return new TxnValidityAndFeeReq(returnCode, feeRequired);
  }

  private boolean checkPlatformStatus() {
    PlatformStatus platformStatus = GlobalFlag.getInstance().getPlatformStatus();
    if (platformStatus != null && !platformStatus.equals(PlatformStatus.ACTIVE)) {
      log.warn("Platform Status is :: " + platformStatus);
      return true;
    }
    return false;
  }

  /**
   * Method to check query validations based on QueryCase from request
   *
   * @return validationCode
   */
  public ResponseCodeEnum validateQuery(Query query, boolean hasPayment) {
    if (checkPlatformStatus()) {
      return ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
    }

    QueryHeader header = activeHeaderFrom(query).orElse(QueryHeader.getDefaultInstance());
    if (!TransactionValidationUtils.validateQueryHeader(header, hasPayment)) {
      return ResponseCodeEnum.MISSING_QUERY_HEADER;
    }
    if (UNSUPPORTED_RESPONSE_TYPES.contains(header.getResponseType())) {
      return NOT_SUPPORTED;
    }

    Transaction feePayment = header.getPayment();
    TransactionBody body;
    try {
      body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(feePayment);
    } catch (Exception e) {
      return INVALID_TRANSACTION_BODY;
    }

    ResponseCodeEnum permissionStatus = NOT_SUPPORTED;
    var queryName = permissionFileKeyForQuery(query);
    if (!StringUtils.isEmpty(queryName)) {
      AccountID payer = body.getTransactionID().getAccountID();
      if (accountNums.isSysAdmin(payer.getAccountNum())) {
        permissionStatus = OK;
      } else {
        PermissionedAccountsRange accountRange = PropertiesLoader.getApiPermission().get(queryName);
        if (accountRange != null) {
          long payerNum = body.getTransactionID().getAccountID().getAccountNum();
          permissionStatus = accountRange.contains(payerNum) ? OK : NOT_SUPPORTED;
        }
      }
    }

    return permissionStatus;
  }

  public boolean verifySignature(Transaction signedTxn) throws Exception {
    try {
      SignedTxnAccessor accessor = new SignedTxnAccessor(signedTxn);
      return precheckVerifier.hasNecessarySignatures(accessor);
    } catch (InvalidProtocolBufferException ignore) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  public List<ExpirableTxnRecord> getAllTransactionRecordFCM(MerkleEntityId merkleEntityId) {
    try {
      MerkleAccount account = accounts.get().get(merkleEntityId);
      if (account != null) {
        return account.recordList();
      } else {
        return Collections.emptyList();
      }
    } catch (ConcurrentModificationException cme) {
      log.error("Queried records are being modified by another thread!", cme);
      throw cme;
    }
  }

  /**
   * Submits transaction to platform.
   *
   * @param request       tx to be submitted
   * @param txnId request tx id
   * @throws PlatformTransactionCreationException thrown when transaction not created by platform
   *                                              due to either large backlog or message size
   *                                              exceeded transactionMaxBytes
   */
  public void submitTransaction(Platform platform, Transaction request, TransactionID txnId)
      throws PlatformTransactionCreationException, InvalidProtocolBufferException {
    byte[] transaction = request.toByteArray();
    boolean status = platform.createTransaction(new com.swirlds.common.Transaction(transaction));
    if (status) {
      recordCache.addPreConsensus(txnId);
    } else {
      throw new PlatformTransactionCreationException(
          "platform tx not created: tx serialized size = " + transaction.length + ", txShortInfo = "
              + com.hedera.services.legacy.proto.utils.CommonUtils.toReadableStringShort(request));
    }
  }
}
