package com.hedera.services.txns.crypto.validators;

import static com.hedera.services.ethereum.EthTxSigs.recoverAddressFromPubKey;
import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;

import com.google.protobuf.ByteString;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CryptoCreateChecks {
  static final int MAX_CHARGEABLE_AUTO_ASSOCIATIONS = 5000;
  private final GlobalDynamicProperties dynamicProperties;
  private final OptionValidator validator;


  @Inject
  public CryptoCreateChecks(final GlobalDynamicProperties dynamicProperties, final OptionValidator validator) {
    this.dynamicProperties = dynamicProperties;
    this.validator = validator;
  }

  @SuppressWarnings("java:S1874")
  public ResponseCodeEnum cryptoCreateValidation(final CryptoCreateTransactionBody op,
      final AccountStorageAdapter accounts,
      final NodeInfo nodeInfo,
      final AliasManager aliasManager) {

    var memoValidity = validator.memoCheck(op.getMemo());
    if (memoValidity != OK) {
      return memoValidity;
    }

    if (!op.getAlias().isEmpty() && op.getAlias().size() <= EVM_ADDRESS_SIZE) {
      return INVALID_ALIAS_KEY;
    }

    var noAliasValidity = validateNoEVMAddressCase(op, aliasManager);
    if (noAliasValidity != OK) {
      return noAliasValidity;
    }

    var aliasIsEVMAddressValidity = validateEVMAddressCase(op, aliasManager);
    if (aliasIsEVMAddressValidity != OK) {
      return aliasIsEVMAddressValidity;
    }

    if (op.getInitialBalance() < 0L) {
      return INVALID_INITIAL_BALANCE;
    }
    if (!op.hasAutoRenewPeriod()) {
      return INVALID_RENEWAL_PERIOD;
    }
    if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
      return AUTORENEW_DURATION_NOT_IN_RANGE;
    }
    if (op.getSendRecordThreshold() < 0L) {
      return INVALID_SEND_RECORD_THRESHOLD;
    }
    if (op.getReceiveRecordThreshold() < 0L) {
      return INVALID_RECEIVE_RECORD_THRESHOLD;
    }
    if (tooManyAutoAssociations(op.getMaxAutomaticTokenAssociations())) {
      return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
    }
    if (op.hasProxyAccountID()
        && !op.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
      return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
    }
    final var stakedIdCase = op.getStakedIdCase().name();
    final var electsStakingId = hasStakedId(stakedIdCase);
    if (!dynamicProperties.isStakingEnabled() && (electsStakingId || op.getDeclineReward())) {
      return STAKING_NOT_ENABLED;
    }
    if (electsStakingId
        && !validator.isValidStakedId(
        stakedIdCase,
        op.getStakedAccountId(),
        op.getStakedNodeId(),
        accounts,
        nodeInfo)) {
      return INVALID_STAKING_ID;
    }
    return OK;
  }

  private ResponseCodeEnum validateNoEVMAddressCase(final CryptoCreateTransactionBody op, final AliasManager aliasManager) {
    if (op.getEvmAddress().isEmpty() || op.hasKey() || !op.getAlias().isEmpty()) {
      if (!op.hasKey() && op.getAlias().isEmpty()) {
        return KEY_REQUIRED;
      }

      final var keyAndNoAliasValidity = validateKeyAndNoAlias(op, aliasManager);
      if (keyAndNoAliasValidity != OK) {
        return keyAndNoAliasValidity;
      }

      final var keyAndAliasValidity = validateKeyAndAlias(op, aliasManager);
      if (keyAndAliasValidity != OK) {
        return keyAndAliasValidity;
      }

      final var aliasAndNoKeyValidity = validateAliasAndNoKey(op, aliasManager);
      if (aliasAndNoKeyValidity != OK) {
        return aliasAndNoKeyValidity;
      }
    }
    return OK;
  }

  private ResponseCodeEnum validateEVMAddressCase(final CryptoCreateTransactionBody op, final AliasManager aliasManager) {
    var evmAddress = op.getEvmAddress();
    if (!evmAddress.isEmpty() && evmAddress.size() == EVM_ADDRESS_SIZE) {
      if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
        return NOT_SUPPORTED;
      }

      var isAliasUsedCheck = isUsedAsAliasCheck(evmAddress, aliasManager);
      if (isAliasUsedCheck != OK) {
        return isAliasUsedCheck;
      }

      if (op.hasKey() && op.getAlias().isEmpty()) {
        final var ecdsaKeyValidity = validateEcdsaKey(
            op.getKey().getECDSASecp256K1(), evmAddress.toByteArray());
        if (ecdsaKeyValidity != OK) {
          return ecdsaKeyValidity;
        }
      } else if (op.hasKey() && !op.getAlias().isEmpty()) {
        var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
        if (!op.getKey().equals(keyFromAlias)) {
          return INVALID_ALIAS_KEY;
        }

        final var ecdsaKeyValidity = validateEcdsaKey(
            op.getKey().getECDSASecp256K1(), evmAddress.toByteArray());
        if (ecdsaKeyValidity != OK) {
          return ecdsaKeyValidity;
        }
      } else if (!op.hasKey() && !op.getAlias().isEmpty()) {
        var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());

        final var ecdsaKeyValidity = validateEcdsaKey(
            keyFromAlias.getECDSASecp256K1(), evmAddress.toByteArray());
        if (ecdsaKeyValidity != OK) {
          return ecdsaKeyValidity;
        }
      } else if (!dynamicProperties.isLazyCreationEnabled()) {
        return NOT_SUPPORTED;
      }
    }
    return OK;
  }

  private ResponseCodeEnum tryToRecoverEVMAddressAndCheckValidity(final byte[] key, final AliasManager aliasManager) {
    var recoveredEVMAddress = recoverAddressFromPubKey(key);
    if (recoveredEVMAddress != null) {
      return isUsedAsAliasCheck(ByteString.copyFrom(recoveredEVMAddress), aliasManager);
    }
    return OK;
  }

  private ResponseCodeEnum validateKey(final CryptoCreateTransactionBody op) {
    if (!validator.hasGoodEncoding(op.getKey())) {
      return BAD_ENCODING;
    }
    var fcKey = asFcKeyUnchecked(op.getKey());
    if (fcKey.isEmpty()) {
      return KEY_REQUIRED;
    }
    if (!fcKey.isValid()) {
      return INVALID_ADMIN_KEY;
    }
    return OK;
  }

  private ResponseCodeEnum validateKeyAndAlias(final CryptoCreateTransactionBody op, final AliasManager aliasManager) {
    if (op.hasKey() && !op.getAlias().isEmpty()) {
      if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
        return NOT_SUPPORTED;
      }

      final var keyValidity = validateKey(op);
      if (keyValidity != OK) {
        return keyValidity;
      }

      var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
      var key = op.getKey();
      if ((!key.getEd25519().isEmpty() || !key.getECDSASecp256K1().isEmpty())
          && !key.equals(keyFromAlias)) {
        return INVALID_ALIAS_KEY;
      }

      var isAliasUsedCheck = isUsedAsAliasCheck(op.getAlias(), aliasManager);

      if (isAliasUsedCheck != OK) {
        return isAliasUsedCheck;
      }

      if (!keyFromAlias.getECDSASecp256K1().isEmpty()) {

        return tryToRecoverEVMAddressAndCheckValidity(
            keyFromAlias.getECDSASecp256K1().toByteArray(), aliasManager);
      }
    }
    return OK;
  }

  private ResponseCodeEnum validateAliasAndNoKey(final CryptoCreateTransactionBody op, final AliasManager aliasManager) {
    if (!op.hasKey() && !op.getAlias().isEmpty()) {
      if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
        return NOT_SUPPORTED;
      }

      var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
      if (!validator.hasGoodEncoding(keyFromAlias)) {
        return BAD_ENCODING;
      }
      var fcKey = asFcKeyUnchecked(keyFromAlias);
      if (fcKey.isEmpty()) {
        return KEY_REQUIRED;
      }

      var isAliasUsedCheck = isUsedAsAliasCheck(op.getAlias(), aliasManager);

      if (isAliasUsedCheck != OK) {
        return isAliasUsedCheck;
      }

      if (!keyFromAlias.getECDSASecp256K1().isEmpty()) {

        return tryToRecoverEVMAddressAndCheckValidity(
            keyFromAlias.getECDSASecp256K1().toByteArray(), aliasManager);
      }

    }
    return OK;
  }

  private ResponseCodeEnum validateKeyAndNoAlias(final CryptoCreateTransactionBody op, final AliasManager aliasManager) {
    if (op.hasKey() && op.getAlias().isEmpty()) {

      final var keyValidity = validateKey(op);
      if (keyValidity != OK) {
        return keyValidity;
      }

      if (!op.getKey().getECDSASecp256K1().isEmpty()) {
        var isKeyUsedAsAliasValidity = isUsedAsAliasCheck(op.getKey().getECDSASecp256K1(), aliasManager);

        if (isKeyUsedAsAliasValidity != OK) {
          return isKeyUsedAsAliasValidity;
        }

        return tryToRecoverEVMAddressAndCheckValidity(
            op.getKey().getECDSASecp256K1().toByteArray(), aliasManager);
      }
    }
    return OK;
  }

  private ResponseCodeEnum validateEcdsaKey(final ByteString ecdsaKey, final byte[] evmAddress) {
    if (ecdsaKey.isEmpty()) {
      return INVALID_ADMIN_KEY;
    }
    final var recoveredEvmAddress =
        recoverAddressFromPubKey(ecdsaKey.toByteArray());
    if (!Arrays.equals(recoveredEvmAddress, evmAddress)) {
      return INVALID_ALIAS_KEY;
    }
    return OK;
  }

  private boolean tooManyAutoAssociations(final int n) {
    return n > MAX_CHARGEABLE_AUTO_ASSOCIATIONS
        || (dynamicProperties.areTokenAssociationsLimited()
        && n > dynamicProperties.maxTokensPerAccount());
  }

  private ResponseCodeEnum isUsedAsAliasCheck(final ByteString alias, final AliasManager aliasManager) {
    if (!aliasManager.lookupIdBy(alias).equals(MISSING_NUM)) {
      return INVALID_ALIAS_KEY;
    }
    return OK;
  }
}
