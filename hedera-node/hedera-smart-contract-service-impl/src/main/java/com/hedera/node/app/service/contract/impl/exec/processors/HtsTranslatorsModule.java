/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.contract.impl.exec.processors;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance.GetAllowanceTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.customfees.TokenCustomFeesTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.decimals.DecimalsTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.defaultfreezestatus.DefaultFreezeStatusTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.defaultkycstatus.DefaultKycStatusTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.delete.DeleteTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeUnfreezeTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.fungibletokeninfo.FungibleTokenInfoTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall.IsApprovedForAllTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isfrozen.IsFrozenTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.iskyc.IsKycTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.istoken.IsTokenTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name.NameTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.nfttokeninfo.NftTokenInfoTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof.OwnerOfTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.pauses.PausesTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol.SymbolTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenexpiry.TokenExpiryTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokeninfo.TokenInfoTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey.TokenKeyTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokentype.TokenTypeTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenuri.TokenUriTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply.TotalSupplyTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc721TransferFromTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateExpiryTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateKeysTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeTranslator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Provides the {@link HtsCallTranslator} implementations for the HTS system contract.
 */
@Module
public interface HtsTranslatorsModule {
    @Provides
    @Singleton
    static List<HtsCallTranslator> provideCallAttemptTranslators(@NonNull final Set<HtsCallTranslator> translators) {
        return List.copyOf(translators);
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideAssociationsTranslator(@NonNull final AssociationsTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideErc20TransfersTranslator(@NonNull final Erc20TransfersTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideErc721TransferFromTranslator(
            @NonNull final Erc721TransferFromTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideClassicTransfersTranslator(@NonNull final ClassicTransfersTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideMintTranslator(@NonNull final MintTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideBurnTranslator(@NonNull final BurnTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideCreateTranslator(@NonNull final CreateTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideBalanceOfTranslator(@NonNull final BalanceOfTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideIsApprovedForAllTranslator(@NonNull final IsApprovedForAllTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideNameTranslator(@NonNull final NameTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideSymbolTranslator(@NonNull final SymbolTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTotalSupplyTranslator(@NonNull final TotalSupplyTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideOwnerOfTranslator(@NonNull final OwnerOfTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideSetApprovalForAllTranslator(@NonNull final SetApprovalForAllTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideDecimalsTranslator(@NonNull final DecimalsTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTokenUriTranslator(@NonNull final TokenUriTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideGetAllowanceTranslator(@NonNull final GetAllowanceTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideGrantApproval(@NonNull final GrantApprovalTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator providePausesTranslator(@NonNull final PausesTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideGrantRevokeKycTranslator(@NonNull final GrantRevokeKycTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideGetApprovedTranslator(@NonNull final GetApprovedTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideWipeTranslator(@NonNull final WipeTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideIsFrozenTranslator(@NonNull final IsFrozenTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideIsKycTranslator(@NonNull final IsKycTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideIsTokenTranslator(@NonNull final IsTokenTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTokenTypeTranslator(@NonNull final TokenTypeTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideDefaultFreezeStatusTranslator(
            @NonNull final DefaultFreezeStatusTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideDefaultKycStatusTranslator(@NonNull final DefaultKycStatusTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideFreezeUnfreezeTranslator(@NonNull final FreezeUnfreezeTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideDeleteTranslator(@NonNull final DeleteTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTokenExpiryTranslator(@NonNull final TokenExpiryTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTokenKeyTranslator(@NonNull final TokenKeyTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideUpdateTranslator(@NonNull final UpdateTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTokenCustomFeesTranslator(@NonNull final TokenCustomFeesTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTokenInfoTranslator(@NonNull final TokenInfoTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideUpdateExpiryTranslator(@NonNull final UpdateExpiryTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideFungibleTokenInfoTranslator(@NonNull final FungibleTokenInfoTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideNonFungibleTokenInfoTranslator(@NonNull final NftTokenInfoTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideUpdateKeysTranslator(@NonNull final UpdateKeysTranslator translator) {
        return translator;
    }
}
