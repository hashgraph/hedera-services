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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class DeleteAllowanceValidatorTest {
    //    @Test
    //    void failsWhenNotSupported() {
    //        given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
    //        assertEquals(NOT_SUPPORTED, subject.deleteAllowancesValidation(nftAllowances, payer, view));
    //    }
    //
    //    @Test
    //    void validateIfSerialsEmpty() {
    //        final List<Long> serials = List.of();
    //        var validity = subject.validateDeleteSerialNums(serials, nftModel, tokenStore);
    //        assertEquals(EMPTY_ALLOWANCES, validity);
    //    }
    //
    //    @Test
    //    void semanticCheckForEmptyAllowancesInOp() {
    //        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
    //                .setTransactionID(ourTxnId())
    //                .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder())
    //                .build();
    //        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
    //        assertEquals(EMPTY_ALLOWANCES, subject.validateAllowancesCount(op.getNftAllowancesList()));
    //    }
    //
    //    @Test
    //    void rejectsMissingToken() {
    //        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(nftToken)))
    //                .willThrow(new InvalidTransactionException(INVALID_TOKEN_ID, true));
    //        nftAllowances.add(nftAllowance2);
    //        assertEquals(
    //                INVALID_TOKEN_ID, subject.validateNftDeleteAllowances(nftAllowances, payer, accountStore,
    // tokenStore));
    //    }
    //
    //    @Test
    //    void validatesIfOwnerExists() {
    //        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(nftToken))).willReturn(nftModel);
    //        nftAllowances.add(nftAllowance2);
    //        given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willThrow(InvalidTransactionException.class);
    //        assertEquals(
    //                INVALID_ALLOWANCE_OWNER_ID,
    //                subject.validateNftDeleteAllowances(nftAllowances, payer, accountStore, tokenStore));
    //    }
    //
    //    @Test
    //    void considersPayerIfOwnerMissing() {
    //        final var allowance = NftRemoveAllowance.newBuilder().build();
    //        nftAllowances.add(allowance);
    //        assertEquals(
    //                Pair.of(payer, OK),
    //                subject.fetchOwnerAccount(Id.fromGrpcAccount(allowance.getOwner()), payer, accountStore));
    //    }
    //
    //    @Test
    //    void failsIfTokenNotAssociatedToAccount() {
    //        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(nftToken))).willReturn(nftModel);
    //        nftAllowances.add(nftAllowance2);
    //        given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(ownerAccount);
    //        given(tokenStore.hasAssociation(nftModel, ownerAccount)).willReturn(false);
    //        assertEquals(
    //                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
    //                subject.validateNftDeleteAllowances(nftAllowances, payer, accountStore, tokenStore));
    //    }
    //
    //    @Test
    //    void failsIfInvalidTypes() {
    //        nftAllowances.clear();
    //
    //        nftModel.setType(TokenType.FUNGIBLE_COMMON);
    //        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(nftToken))).willReturn(nftModel);
    //        nftModel.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
    //        nftAllowances.add(NftRemoveAllowance.newBuilder().setTokenId(nftToken).build());
    //        assertEquals(
    //                FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES,
    //                subject.validateNftDeleteAllowances(nftAllowances, payer, accountStore, tokenStore));
    //    }
    //
    //    @Test
    //    void returnsValidationOnceFailed() {
    //        nftAllowances.add(nftAllowance1);
    //
    //        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
    //        given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(1);
    //        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
    //
    //        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
    //                .setTransactionID(ourTxnId())
    //                .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder()
    //                        .addAllNftAllowances(nftAllowances)
    //                        .build())
    //                .build();
    //        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
    //
    //        assertEquals(
    //                MAX_ALLOWANCES_EXCEEDED, subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer,
    // view));
    //    }
    //
    //    @Test
    //    void succeedsWithEmptyLists() {
    //        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
    //                .setTransactionID(ourTxnId())
    //                .setCryptoDeleteAllowance(
    //                        CryptoDeleteAllowanceTransactionBody.newBuilder().build())
    //                .build();
    //        assertEquals(
    //                OK,
    //                subject.validateNftDeleteAllowances(
    //                        cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance().getNftAllowancesList(),
    //                        payer,
    //                        accountStore,
    //                        tokenStore));
    //    }
    //
    //    @Test
    //    void happyPath() {
    //        setUpForTest();
    //        getValidTxnCtx();
    //        given(validator.expiryStatusGiven(anyLong(), anyBoolean(), anyBoolean()))
    //                .willReturn(OK);
    //
    //        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
    //        given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
    //        assertEquals(OK, subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, view));
    //    }
    //
    //    @Test
    //    void validateSerialsExistence() {
    //        final var serials = List.of(1L, 10L);
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken),
    // 1L)).willThrow(InvalidTransactionException.class);
    //
    //        var validity = subject.validateSerialNums(serials, nftModel, tokenStore);
    //        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    //    }
    //
    //    @Test
    //    void returnsIfSerialsFail() {
    //        final var serials = List.of(1L, 10L);
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken),
    // 1L)).willThrow(InvalidTransactionException.class);
    //        var validity = subject.validateSerialNums(serials, nftModel, tokenStore);
    //        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    //    }
    //
    //    @Test
    //    void addsSerialsCorrectly() {
    //        nftAllowances.add(nftAllowance1);
    //        nftAllowances.add(nftAllowance2);
    //        assertEquals(5, subject.aggregateNftDeleteAllowances(nftAllowances));
    //    }
    //
    //    @Test
    //    void validatesNegativeSerialsAreNotValid() {
    //        final var serials = List.of(-100L, 10L);
    //
    //        var validity = subject.validateSerialNums(serials, nftModel, tokenStore);
    //        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    //    }
    //
    //    @Test
    //    void validateSerials() {
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 10L)).willReturn(uniqueToken);
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 1L)).willReturn(uniqueToken);
    //
    //        var serials = List.of(1L, 10L, 1L);
    //        var validity = subject.validateSerialNums(serials, nftModel, tokenStore);
    //        assertEquals(OK, validity);
    //
    //        serials = List.of(10L, 4L);
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken),
    // 10L)).willThrow(InvalidTransactionException.class);
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 4L)).willReturn(uniqueToken);
    //        validity = subject.validateSerialNums(serials, nftModel, tokenStore);
    //        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    //
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 20L)).willReturn(uniqueToken);
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 4L)).willReturn(uniqueToken);
    //
    //        serials = List.of(20L, 4L);
    //        validity = subject.validateSerialNums(serials, nftModel, tokenStore);
    //        assertEquals(OK, validity);
    //    }

}
