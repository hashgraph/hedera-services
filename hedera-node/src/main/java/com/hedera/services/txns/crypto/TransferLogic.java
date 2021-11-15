package com.hedera.services.txns.crypto;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.MerkleAccountScopedCheck;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class TransferLogic {
    private final MerkleAccountScopedCheck scopedCheck;
    private final TransferList.Builder netTransfers = TransferList.newBuilder();
    private final GlobalDynamicProperties dynamicProperties;
    private final OptionValidator validator;
    private UniqTokenViewsManager tokenViewsManager = null;

    private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    private final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokenLedger;
    private final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
    private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
    private final SideEffectsTracker sideEffectsTracker;

    // TODO: move to SideEffectsTracker
    private final List<FcTokenAssociation> newTokenAssociations = new ArrayList<>();

    public TransferLogic(MerkleAccountScopedCheck scopedCheck, GlobalDynamicProperties dynamicProperties, OptionValidator validator, TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger, TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokenLedger, TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger, TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger, SideEffectsTracker sideEffectsTracker) {
        this.scopedCheck = scopedCheck;
        this.dynamicProperties = dynamicProperties;
        this.validator = validator;
        this.accountsLedger = accountsLedger;
        this.tokenLedger = tokenLedger;
        this.nftsLedger = nftsLedger;
        this.tokenRelsLedger = tokenRelsLedger;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    public void setTokenViewsManager(UniqTokenViewsManager tokenViewsManager) {
        this.tokenViewsManager = tokenViewsManager;
    }

    public void cryptoTransfer(List<BalanceChange> changes) {
        doZeroSum(changes);
    }

    private void doZeroSum(List<BalanceChange> changes) {
        var validity = OK;
        for (var change : changes) {
            if (!change.isForHbar()) {
                validity = accountsLedger.validate(
                        change.accountId(),
                        scopedCheck.setBalanceChange(change));
            }
            if (validity != OK) {
                return;
            }
        }

        for (var change : changes) {
            if (change.isForHbar()) {
                accountsLedger.set(change.accountId(), BALANCE, change.getNewBalance());
                // TODO: move updateXfers to SideEffectsTracker
            }
        }
    }
}
