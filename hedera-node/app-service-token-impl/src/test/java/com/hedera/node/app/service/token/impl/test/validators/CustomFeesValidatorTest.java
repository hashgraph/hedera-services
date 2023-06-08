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

package com.hedera.node.app.service.token.impl.test.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.CustomFeesValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFeesValidatorTest extends CryptoTokenHandlerTestBase {
    private final CustomFeesValidator subject = new CustomFeesValidator();

    @BeforeEach
    public void commonSetUp() {
        super.setUp();
        refreshWritableStores();
    }

    @Test
    @DisplayName("throws if fee collector is not set")
    void validateNullFeeCollector() {
        final var nullCollectorFee = setFeeCollector(customFees, null);
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        nullCollectorFee))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_CUSTOM_FEE_COLLECTOR));
    }

    @Test
    @DisplayName("throws if fee collector doesn't exist")
    void validateMissingFeeCollector() {
        final var missingFeeCollectorFee = setFeeCollector(
                customFees, AccountID.newBuilder().accountNum(100).build());
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        missingFeeCollectorFee))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_CUSTOM_FEE_COLLECTOR));
    }

    @Test
    @DisplayName("throws if fee collector is default instance")
    void validateDefaultInstance() {
        final var missingFeeCollectorFee =
                setFeeCollector(customFees, AccountID.newBuilder().build());
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        missingFeeCollectorFee))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_CUSTOM_FEE_COLLECTOR));
    }

    @Test
    @DisplayName("fixed fee and fractional fee for fungible tokens are allowed")
    void validateFixedFeeAndFractionalFees() {
        assertThatNoException()
                .isThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken, readableAccountStore, readableTokenRelStore, writableTokenStore, customFees));
    }

    @Test
    @DisplayName("royalty fee can be set only for non fungible unique tokens")
    void royaltyFeeForFungibleTokenFails() {
        final List<CustomFee> feeWithRoyalty = new ArrayList<>();
        feeWithRoyalty.add(withRoyaltyFee(royaltyFee));
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken, readableAccountStore, readableTokenRelStore, writableTokenStore, feeWithRoyalty))
                .isInstanceOf(HandleException.class)
                .has(responseCode(CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE));
    }

    @Test
    @DisplayName("royalty fee can be set for non fungible unique tokens")
    void royaltyFeeForNonFungibleTokenSucceeds() {
        refreshWritableStores();
        final List<CustomFee> feeWithRoyalty = new ArrayList<>();
        feeWithRoyalty.add(withRoyaltyFee(royaltyFee));
        assertThatNoException()
                .isThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        nonFungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        feeWithRoyalty));
    }

    @Test
    @DisplayName("royalty fee for NFTs will fail if the denominating token is missing")
    void royaltyFeeFailsWithMissingToken() {
        writableTokenState = emptyWritableTokenState();
        given(writableStates.<EntityNum, Token>get(TOKENS)).willReturn(writableTokenState);
        writableTokenStore = new WritableTokenStore(writableStates);

        final List<CustomFee> feeWithRoyalty = new ArrayList<>();
        feeWithRoyalty.add(withRoyaltyFee(royaltyFee));
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        nonFungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        feeWithRoyalty))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_ID_IN_CUSTOM_FEES));
    }

    @Test
    @DisplayName("royalty fee for NFTs will fail if the denominating token is missing")
    void royaltyFeeFailsFungibleDenom() {
        refreshWritableStores();
        final List<CustomFee> feeWithRoyalty = new ArrayList<>();
        final var nftDenom = royaltyFee
                .copyBuilder()
                .fallbackFee(fixedFee.copyBuilder()
                        .denominatingTokenId(TokenID.newBuilder()
                                .tokenNum(nonFungibleTokenNum.longValue())
                                .build()))
                .build();
        feeWithRoyalty.add(withRoyaltyFee(nftDenom));
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        nonFungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        feeWithRoyalty))
                .isInstanceOf(HandleException.class)
                .has(responseCode(CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON));
    }

    @Test
    void missingTokenAssociationForRoyaltyFeeFails() {
        refreshWritableStores();
        readableTokenRelState = emptyReadableTokenRelsStateBuilder().build();
        given(readableStates.<EntityNumPair, TokenRelation>get(TOKEN_RELS)).willReturn(readableTokenRelState);
        readableTokenRelStore = new ReadableTokenRelationStoreImpl(readableStates);

        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        nonFungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        List.of(withRoyaltyFee(royaltyFee))))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR));
    }

    @Test
    @DisplayName("fractional fee can be set only for fungible unique tokens")
    void fractionalFeeForNonFungibleTokenFails() {
        final List<CustomFee> feeWithFractional = new ArrayList<>();
        feeWithFractional.add(withFractionalFee(fractionalFee));
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        nonFungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        feeWithFractional))
                .isInstanceOf(HandleException.class)
                .has(responseCode(CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON));
    }

    @Test
    @DisplayName("fixed fee can be set for non fungible unique tokens")
    void fixedFeeIsAllowedForNonFungibleToken() {
        refreshWritableStores();
        final List<CustomFee> feeWithFixed = new ArrayList<>();
        feeWithFixed.add(withFixedFee(fixedFee));
        assertThatNoException()
                .isThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        nonFungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        feeWithFixed));
    }

    @Test
    @DisplayName("fails if there is no token relation between token and fee collector in fixed fee")
    void failsIfTokenRelationIsMissingInFixedFee() {
        readableTokenRelState = emptyReadableTokenRelsStateBuilder().build();
        given(readableStates.<EntityNumPair, TokenRelation>get(TOKEN_RELS)).willReturn(readableTokenRelState);
        readableTokenRelStore = new ReadableTokenRelationStoreImpl(readableStates);

        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        List.of(withFixedFee(fixedFee))))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR));
    }

    @Test
    @DisplayName("fails if there is no token relation between token and fee collector in fractional fee")
    void failsIfTokenRelationIsMissingForFractionalFee() {
        readableTokenRelState = emptyReadableTokenRelsStateBuilder().build();
        given(readableStates.<EntityNumPair, TokenRelation>get(TOKEN_RELS)).willReturn(readableTokenRelState);
        readableTokenRelStore = new ReadableTokenRelationStoreImpl(readableStates);

        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        List.of(withFractionalFee(fractionalFee))))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR));
    }

    @Test
    @DisplayName("token denomination should be fungible common for fixed fee")
    void validateTokenDenominationForFixedFee() {
        refreshWritableStores();
        final var newFee = fixedFee.copyBuilder()
                .denominatingTokenId(TokenID.newBuilder()
                        .tokenNum(nonFungibleTokenNum.longValue())
                        .build())
                .build();
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        List.of(withFixedFee(newFee))))
                .isInstanceOf(HandleException.class)
                .has(responseCode(CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON));
    }

    @Test
    @DisplayName("Custom fee validation for TokenCreate is not implemented")
    void validateCustomFeeForCreation() {
        assertThatThrownBy(() -> subject.validateCreation(
                        fungibleToken, readableAccountStore, readableTokenRelStore, writableTokenStore, customFees))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullParamsThrow() {
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        null, readableAccountStore, readableTokenRelStore, writableTokenStore, customFees))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken, null, readableTokenRelStore, writableTokenStore, customFees))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken, readableAccountStore, null, writableTokenStore, customFees))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken, readableAccountStore, readableTokenRelStore, null, customFees))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken, readableAccountStore, readableTokenRelStore, writableTokenStore, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void failsIfEmptyCustomFees() {
        assertThatThrownBy(() -> subject.validateForFeeScheduleUpdate(
                        fungibleToken,
                        readableAccountStore,
                        readableTokenRelStore,
                        writableTokenStore,
                        List.of(CustomFee.newBuilder()
                                .feeCollectorAccountId(AccountID.newBuilder().accountNum(accountNum.longValue()))
                                .build())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected value for custom fee type: UNSET");
    }

    private List<CustomFee> setFeeCollector(List<CustomFee> original, AccountID feeCollector) {
        List<CustomFee> copy = new ArrayList<>();
        for (CustomFee fee : original) {
            copy.add(fee.copyBuilder().feeCollectorAccountId(feeCollector).build());
        }
        return copy;
    }
}
