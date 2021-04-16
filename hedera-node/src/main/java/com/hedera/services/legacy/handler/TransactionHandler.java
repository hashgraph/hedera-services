package com.hedera.services.legacy.handler;

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

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.legacy.exception.KeySignatureCountMismatchException;
import com.hedera.services.legacy.exception.KeySignatureTypeMismatchException;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.records.RecordCache;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.TransactionThrottling;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.txns.validation.PureValidation;
import com.hedera.services.txns.validation.TransferListChecks;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.common.Platform;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.utils.MiscUtils.activeHeaderFrom;
import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER_STATE_PROOF;
import static com.swirlds.common.PlatformStatus.ACTIVE;

@Deprecated
public class TransactionHandler {
  public static final Predicate<AccountID> IS_THROTTLE_EXEMPT =
          id -> id.getAccountNum() >= 1 && id.getAccountNum() <= 100L;
  public static final int MESSAGE_MAX_DEPTH = 50;

  private EnumSet<ResponseType> UNSUPPORTED_RESPONSE_TYPES = EnumSet.of(ANSWER_STATE_PROOF, COST_ANSWER_STATE_PROOF);

  private static final Logger log = LogManager.getLogger(TransactionHandler.class);
  private RecordCache recordCache;
  private PrecheckVerifier precheckVerifier;
  private Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
  private AccountID nodeAccount;
  private TransactionThrottling txnThrottling;
  private FeeCalculator fees;
  private FeeExemptions exemptions;
  private Supplier<StateView> stateView;
  private BasicPrecheck basicPrecheck;
  private QueryFeeCheck queryFeeCheck;
  private AccountNumbers accountNums;
  private SystemOpPolicies systemOpPolicies;
  private CurrentPlatformStatus platformStatus;
  private HapiOpPermissions hapiOpPermissions;

  public static boolean validateTxDepth(Transaction transaction) {
        return getDepth(transaction) <= MESSAGE_MAX_DEPTH;
    }

  public static boolean validateTxBodyDepth(TransactionBody transactionBody) {
        return getDepth(transactionBody) < MESSAGE_MAX_DEPTH;
    }

  public static int getDepth(final GeneratedMessageV3 message) {
        Map<Descriptors.FieldDescriptor, Object> fields = message.getAllFields();
        int depth = 0;
        for (var field : fields.values()) {
          if (field instanceof GeneratedMessageV3) {
            GeneratedMessageV3 fieldMessage = (GeneratedMessageV3) field;
            depth = Math.max(depth, getDepth(fieldMessage) + 1);
          } else if (field instanceof List) {
            for (Object ele : (List) field) {
              if (ele instanceof GeneratedMessageV3) {
                depth = Math.max(depth, getDepth((GeneratedMessageV3) ele) + 1);
              }
            }
          }
        }
        return depth;
    }

  public static boolean validateTxSize(Transaction transaction) {
        return transaction.toByteArray().length <= Platform.getTransactionMaxBytes();
    }

  public static boolean validateQueryHeader(QueryHeader queryHeader, boolean hasPayment) {
        boolean returnFlag = true;
        if (queryHeader == null || queryHeader.getResponseType() == null) {
            returnFlag = false;
        } else if (hasPayment) {
            returnFlag = queryHeader.hasPayment();
        }
        return returnFlag;
    }

  public void setBasicPrecheck(BasicPrecheck basicPrecheck) {
    this.basicPrecheck = basicPrecheck;
  }

  public void setFees(FeeCalculator fees) {
    this.fees = fees;
  }

  public TransactionHandler(
          RecordCache recordCache,
          Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
          AccountID nodeAccount,
          PrecheckVerifier precheckVerifier,
          FeeCalculator fees,
          Supplier<StateView> stateView,
          BasicPrecheck basicPrecheck,
          QueryFeeCheck queryFeeCheck,
          AccountNumbers accountNums,
          SystemOpPolicies systemOpPolicies,
          FeeExemptions exemptions,
          CurrentPlatformStatus platformStatus,
          FunctionalityThrottling throttling,
          HapiOpPermissions hapiOpPermissions
  ) {
  	this.fees = fees;
  	this.stateView = stateView;
    this.recordCache = recordCache;
    this.accounts = accounts;
    this.nodeAccount = nodeAccount;
    this.precheckVerifier = precheckVerifier;
    this.basicPrecheck = basicPrecheck;
    this.queryFeeCheck = queryFeeCheck;
    this.accountNums = accountNums;
    this.systemOpPolicies = systemOpPolicies;
    this.exemptions = exemptions;
    this.platformStatus = platformStatus;
    this.hapiOpPermissions = hapiOpPermissions;
    txnThrottling = new TransactionThrottling(throttling);
  }

  public TransactionHandler(
          RecordCache recordCache,
          PrecheckVerifier verifier,
          Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
          AccountID nodeAccount,
          AccountNumbers accountNums,
          SystemOpPolicies systemOpPolicies,
          FeeExemptions exemptions,
          CurrentPlatformStatus platformStatus,
          HapiOpPermissions hapiOpPermissions
  ) {
    this(recordCache, verifier, accounts, nodeAccount,
            null,
            null, null, null, null,
            accountNums, systemOpPolicies, exemptions, platformStatus, hapiOpPermissions);
  }

  public TransactionHandler(
          RecordCache recordCache,
          PrecheckVerifier precheckVerifier,
          Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
          AccountID nodeAccount,
          TransactionThrottling txnThrottling,
          FeeCalculator fees,
          Supplier<StateView> stateView,
          BasicPrecheck basicPrecheck,
          QueryFeeCheck queryFeeCheck,
          AccountNumbers accountNums,
          SystemOpPolicies systemOpPolicies,
          FeeExemptions exemptions,
          CurrentPlatformStatus platformStatus,
          HapiOpPermissions hapiOpPermissions
  ) {
    this.fees = fees;
    this.stateView = stateView;
    this.recordCache = recordCache;
    this.precheckVerifier = precheckVerifier;
    this.accounts = accounts;
    this.nodeAccount = nodeAccount;
    this.basicPrecheck = basicPrecheck;
    this.txnThrottling = txnThrottling;
    this.queryFeeCheck = queryFeeCheck;
    this.accountNums = accountNums;
    this.systemOpPolicies = systemOpPolicies;
    this.exemptions = exemptions;
    this.platformStatus = platformStatus;
    this.hapiOpPermissions = hapiOpPermissions;
  }

  public ResponseCodeEnum nodePaymentValidity(Transaction signedTxn, long queryFee) {
    try {
      var txn = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signedTxn);
      var transfers = txn.getCryptoTransfer().getTransfers().getAccountAmountsList();
      return queryFeeCheck.nodePaymentValidity(transfers, queryFee, txn.getNodeAccountID());
    } catch (Exception ignore) {
      return INVALID_TRANSACTION_BODY;
    }
  }

  public boolean isAccountExist(AccountID acctId) {
    MerkleEntityId merkleEntityId = new MerkleEntityId(acctId.getShardNum(), acctId.getRealmNum(), acctId.getAccountNum());
    return accounts.get().get(merkleEntityId) != null;
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

  private ResponseCodeEnum validateApiPermission(TransactionBody txn) {
    try {
      return hapiOpPermissions.permissibilityOf(functionOf(txn), txn.getTransactionID().getAccountID());
    } catch (UnknownHederaFunctionality unknownHederaFunctionality) {
      return NOT_SUPPORTED;
    }
  }

  private TxnValidityAndFeeReq validateTransactionFeeCoverage(
          TransactionBody txn,
          SignedTxnAccessor accessor) {
    ResponseCodeEnum returnCode = OK;
    if (exemptions.hasExemptPayer(accessor)) {
      return new TxnValidityAndFeeReq(returnCode);
    }

    long fee = 0L;
    long feeRequired = 0L;
    if (txn.getTransactionID().hasAccountID()) {
      AccountID payerAccount = txn.getTransactionID().getAccountID();
      if (isAccountExist(payerAccount)) {
        Long payerAccountBalance = Optional.ofNullable(accounts.get().get(fromAccountId(payerAccount)))
                .map(MerkleAccount::getBalance)
                .orElse(0L);
        long suppliedFee = txn.getTransactionFee();

        Timestamp at = txn.getTransactionID().getTransactionValidStart();
        try {
          JKey payerKey = accounts.get().get(fromAccountId(payerAccount)).getKey();
          FeeObject txnFee = fees.estimateFee(accessor, payerKey, stateView.get(), at);
          fee = txnFee.getNetworkFee() + txnFee.getNodeFee() + txnFee.getServiceFee();
        } catch (Exception e) {
          log.warn("Could not calculate fee for transaction", e);
          returnCode = ResponseCodeEnum.FAIL_FEE;
        }
        if (returnCode == OK) {
          if (suppliedFee < fee) {
            returnCode = ResponseCodeEnum.INSUFFICIENT_TX_FEE;
            feeRequired = fee;
          } else if (payerAccountBalance + Math.min(0L, fees.estimatedNonFeePayerAdjustments(accessor, at)) < fee) {
            returnCode = ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
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

  private ResponseCodeEnum validateTransactionContents(Transaction transaction) {
    if (transaction.getSignedTransactionBytes().isEmpty() && transaction.getBodyBytes().isEmpty()) {
      return INVALID_TRANSACTION_BODY;
    }
    if (!transaction.getSignedTransactionBytes().isEmpty() &&
            (transaction.hasSigMap() || !transaction.getBodyBytes().isEmpty())) {
      return INVALID_TRANSACTION;
    }
    return OK;
  }

  public TxnValidityAndFeeReq validateTransactionPreConsensus(Transaction transaction, boolean isQueryPayment) {
    ResponseCodeEnum returnCode = validateTransactionContents(transaction);
    if (OK != returnCode) {
      return new TxnValidityAndFeeReq(returnCode);
    }

    if (platformStatus.get() != ACTIVE) {
      return new TxnValidityAndFeeReq(ResponseCodeEnum.PLATFORM_NOT_ACTIVE);
    }

    if (!validateTxSize(transaction)) {
      if (log.isDebugEnabled()) {
        log.debug("Size of the transaction exceeds transactionMaxBytes: "
            + Platform.getTransactionMaxBytes());
      }
      return new TxnValidityAndFeeReq(ResponseCodeEnum.TRANSACTION_OVERSIZE);
    }

    if (!validateTxDepth(transaction)) {
      log.debug("Request transaction has too many layers.");
      return new TxnValidityAndFeeReq(ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS);
    }

    long feeRequired = 0L;
    SignedTxnAccessor accessor = null;
    TransactionBody txn = TransactionBody.getDefaultInstance();
    try {
      accessor = new SignedTxnAccessor(transaction);
      txn = accessor.getTxn();
    } catch (InvalidProtocolBufferException e1) {
      returnCode = INVALID_TRANSACTION_BODY;
    }

    if (returnCode == OK && !validateTxBodyDepth(txn)) {
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
      returnCode = systemOpPolicies.check(accessor).asStatus();
    }

    if (returnCode == OK) {
      TxnValidityAndFeeReq localResp = validateTransactionFeeCoverage(txn, accessor);
      returnCode = localResp.getValidity();
      feeRequired = localResp.getRequiredFee();
    }

    if(returnCode == OK && isQueryPayment && txn.hasCryptoTransfer()){
      returnCode = queryFeeCheck.validateQueryPaymentTransfers(txn);
    }

    if (!(isQueryPayment && txn.hasCryptoTransfer()) && returnCode == OK) {
      returnCode = validateTransactionThrottling(txn);
    }

    return new TxnValidityAndFeeReq(returnCode, feeRequired);
  }

  public ResponseCodeEnum validateQuery(Query query, boolean hasPayment) {
    if (hasPayment && platformStatus.get() != ACTIVE) {
      return ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
    }

    QueryHeader header = activeHeaderFrom(query).orElse(QueryHeader.getDefaultInstance());
    if (!validateQueryHeader(header, hasPayment)) {
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

    var queryOp = MiscUtils.functionalityOfQuery(query);
    AccountID payer = body.getTransactionID().getAccountID();
    return queryOp.map(op -> hapiOpPermissions.permissibilityOf(op, payer)).orElse(NOT_SUPPORTED);
  }

  public boolean verifySignature(Transaction signedTxn) throws Exception {
    try {
      SignedTxnAccessor accessor = new SignedTxnAccessor(signedTxn);
      return precheckVerifier.hasNecessarySignatures(accessor);
    } catch (InvalidProtocolBufferException ignore) {
      return false;
    }
  }

  public void setHapiOpPermissions(HapiOpPermissions hapiOpPermissions) {
    this.hapiOpPermissions = hapiOpPermissions;
  }
}
