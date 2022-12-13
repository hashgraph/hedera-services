/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.sigs;

import static com.hedera.node.app.service.mono.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;
import static com.hedera.node.app.service.mono.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.node.app.service.mono.keys.HederaKeyActivation.payerSigIsActive;
import static com.hedera.node.app.service.mono.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.node.app.service.mono.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsFor;
import static com.hedera.node.app.service.mono.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.test.factories.scenarios.BadPayerScenarios.INVALID_PAYER_ID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.CRYPTO_CREATE_COMPLEX_PAYER_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.CRYPTO_CREATE_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.NEW_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_ADD_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.sigs.SigWrappers.asKind;
import static com.hedera.test.factories.sigs.SigWrappers.asValid;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.swirlds.common.crypto.VerificationStatus.INVALID;
import static com.swirlds.common.crypto.VerificationStatus.VALID;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;

import com.hedera.node.app.service.mono.config.EntityNumbers;
import com.hedera.node.app.service.mono.config.FileNumbers;
import com.hedera.node.app.service.mono.config.MockEntityNumbers;
import com.hedera.node.app.service.mono.config.MockFileNumbers;
import com.hedera.node.app.service.mono.files.HederaFs;
import com.hedera.node.app.service.mono.keys.HederaKeyActivation;
import com.hedera.node.app.service.mono.keys.KeyActivationCharacteristics;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.factories.ReusableBodySigningFactory;
import com.hedera.node.app.service.mono.sigs.metadata.SigMetadataLookup;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.HfsSigMetaLookup;
import com.hedera.node.app.service.mono.sigs.order.MapWarmer;
import com.hedera.node.app.service.mono.sigs.order.PolicyBasedSigWaivers;
import com.hedera.node.app.service.mono.sigs.order.SigRequirements;
import com.hedera.node.app.service.mono.sigs.order.SignatureWaivers;
import com.hedera.node.app.service.mono.sigs.order.SigningOrderResult;
import com.hedera.node.app.service.mono.sigs.order.SigningOrderResultFactory;
import com.hedera.node.app.service.mono.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.verification.SyncVerifier;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.txns.auth.SystemOpPolicies;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.factories.txns.CryptoCreateFactory;
import com.hedera.test.utils.CryptoConfigUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.merkle.map.MerkleMap;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SigOpsRegressionTest {
    private HederaFs hfs;
    private AliasManager aliasManager;
    private FileNumbers fileNumbers = new MockFileNumbers();
    private List<TransactionSignature> expectedSigs;
    private ResponseCodeEnum expectedErrorStatus;
    private PlatformTxnAccessor platformTxn;
    private SigRequirements signingOrder;
    private MerkleMap<EntityNum, MerkleAccount> accounts;

    private EntityNumbers mockEntityNumbers = new MockEntityNumbers();
    private SystemOpPolicies mockSystemOpPolicies = new SystemOpPolicies(mockEntityNumbers);
    private SignatureWaivers mockSignatureWaivers =
            new PolicyBasedSigWaivers(mockEntityNumbers, mockSystemOpPolicies);
    private MapWarmer mapWarmer = Mockito.mock(MapWarmer.class);

    static boolean otherPartySigsAreActive(
            PlatformTxnAccessor accessor,
            SigRequirements keyOrder,
            SigningOrderResultFactory<ResponseCodeEnum> summaryFactory) {
        return otherPartySigsAreActive(
                accessor, keyOrder, summaryFactory, DEFAULT_ACTIVATION_CHARACTERISTICS);
    }

    static boolean otherPartySigsAreActive(
            PlatformTxnAccessor accessor,
            SigRequirements keyOrder,
            SigningOrderResultFactory<ResponseCodeEnum> summaryFactory,
            KeyActivationCharacteristics characteristics) {
        TransactionBody txn = accessor.getTxn();
        Function<byte[], TransactionSignature> sigsFn =
                HederaKeyActivation.pkToSigMapFrom(accessor.getCryptoSigs());

        final var othersResult = keyOrder.keysForOtherParties(txn, summaryFactory);
        for (JKey otherKey : othersResult.getOrderedKeys()) {
            if (!HederaKeyActivation.isActive(
                    otherKey, sigsFn, HederaKeyActivation.ONLY_IF_SIG_IS_VALID, characteristics)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void setsExpectedPlatformSigsForCryptoCreate() throws Throwable {
        // given:
        setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);

        // when:
        invokeExpansionScenario();

        // then:
        assertEquals(OK, platformTxn.getExpandedSigStatus());
        assertEquals(expectedSigs, platformTxn.getCryptoSigs());
        final var sigMeta = platformTxn.getSigMeta();
        assertTrue(sigMeta.couldRationalizePayer());
        assertTrue(sigMeta.couldRationalizeOthers());
        assertEquals(DEFAULT_PAYER_KT.asKey(), asKeyUnchecked(sigMeta.payerKey()));
        assertEquals(1, sigMeta.othersReqSigs().size());
        assertEquals(
                List.of(CryptoCreateFactory.DEFAULT_ACCOUNT_KT.asKey()),
                List.of(asKeyUnchecked(sigMeta.othersReqSigs().get(0))));
    }

    @Test
    void setsExpectedErrorForBadPayer() throws Throwable {
        // given:
        setupFor(INVALID_PAYER_ID_SCENARIO);

        // when:
        invokeExpansionScenario();

        // then:
        statusMatches(expectedErrorStatus);
        assertEquals(expectedSigs, platformTxn.getCryptoSigs());
        assertFalse(platformTxn.getSigMeta().couldRationalizePayer());
    }

    @Test
    void setsExpectedErrorAndSigsForMissingTargetAccount() throws Throwable {
        setupFor(CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO);

        invokeExpansionScenario();

        statusMatches(expectedErrorStatus);
        assertEquals(expectedSigs, platformTxn.getCryptoSigs());
        assertTrue(platformTxn.getSigMeta().couldRationalizePayer());
        assertFalse(platformTxn.getSigMeta().couldRationalizeOthers());
    }

    @Test
    void rationalizesExpectedPlatformSigsForCryptoCreate() throws Throwable {
        // given:
        setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);
        // and:
        List<TransactionSignature> expectedSigs = expectedCryptoCreateScenarioSigs();

        // when:
        final var ans = invokeRationalizationScenario();

        // then:
        assertTrue(ans.usedSyncVerification());
        assertEquals(OK, ans.finalStatus());
        assertEquals(expectedSigs, platformTxn.getSigMeta().verifiedSigs());
        // and:
        allVerificationStatusesAre(vs -> !VerificationStatus.UNKNOWN.equals(vs));
    }

    @Test
    void rubberstampsCorrectPlatformSigsForCryptoCreate() throws Throwable {
        // given:
        setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);
        // and:
        List<TransactionSignature> expectedSigs = expectedCryptoCreateScenarioSigs();
        platformTxn.addAllCryptoSigs(asValid(expectedSigs));

        // when:
        final var ans = invokeRationalizationScenario();

        // then:
        assertFalse(ans.usedSyncVerification());
        assertEquals(OK, ans.finalStatus());
        assertEquals(expectedSigs, platformTxn.getCryptoSigs());
        // and:
        allVerificationStatusesAre(vs -> VerificationStatus.VALID.equals(vs));
    }

    @Test
    void validatesComplexPayerSigActivation() throws Throwable {
        // given:
        setupFor(CRYPTO_CREATE_COMPLEX_PAYER_RECEIVER_SIG_SCENARIO);
        // and:
        List<TransactionSignature> unknownSigs =
                PlatformSigOps.createCryptoSigsFrom(
                                List.of(
                                        COMPLEX_KEY_ACCOUNT_KT.asJKey(),
                                        CryptoCreateFactory.DEFAULT_ACCOUNT_KT.asJKey()),
                                platformTxn.getPkToSigsFn(),
                                new ReusableBodySigningFactory(platformTxn))
                        .getPlatformSigs();
        List<TransactionSignature> knownSigs =
                asKind(
                        List.of(
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(0), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(1), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(2), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(3), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(4), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(6), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(7), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID)));

        // expect:
        assertTrue(invokePayerSigActivationScenario(knownSigs));
    }

    @Test
    void deniesInactiveComplexPayerSig() throws Throwable {
        // given:
        setupFor(CRYPTO_CREATE_COMPLEX_PAYER_RECEIVER_SIG_SCENARIO);
        // and:
        List<TransactionSignature> unknownSigs =
                PlatformSigOps.createCryptoSigsFrom(
                                List.of(
                                        COMPLEX_KEY_ACCOUNT_KT.asJKey(),
                                        CryptoCreateFactory.DEFAULT_ACCOUNT_KT.asJKey()),
                                platformTxn.getPkToSigsFn(),
                                new ReusableBodySigningFactory(platformTxn))
                        .getPlatformSigs();
        List<TransactionSignature> knownSigs =
                asKind(
                        List.of(
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(0), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(1), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(2), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(3), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(4), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(6), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(7), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID)));

        // expect:
        assertFalse(invokePayerSigActivationScenario(knownSigs));
    }

    @Test
    void validatesComplexOtherPartySigActivation() throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_SCENARIO);
        // and:
        List<TransactionSignature> unknownSigs =
                PlatformSigOps.createCryptoSigsFrom(
                                List.of(DEFAULT_PAYER_KT.asJKey(), COMPLEX_KEY_ACCOUNT_KT.asJKey()),
                                platformTxn.getPkToSigsFn(),
                                new ReusableBodySigningFactory(platformTxn))
                        .getPlatformSigs();
        List<TransactionSignature> knownSigs =
                asKind(
                        List.of(
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(0), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(1), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(2), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(3), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(4), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(6), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(7), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(9), VALID)));

        // expect:
        assertTrue(invokeOtherPartySigActivationScenario(knownSigs));
    }

    @Test
    void deniesInactiveComplexOtherPartySig() throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_SCENARIO);
        // and:
        List<TransactionSignature> unknownSigs =
                PlatformSigOps.createCryptoSigsFrom(
                                List.of(DEFAULT_PAYER_KT.asJKey(), COMPLEX_KEY_ACCOUNT_KT.asJKey()),
                                platformTxn.getPkToSigsFn(),
                                new ReusableBodySigningFactory(platformTxn))
                        .getPlatformSigs();
        List<TransactionSignature> knownSigs =
                asKind(
                        List.of(
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(0), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(1), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(2), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(3), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(4), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(6), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(7), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(9), VALID)));

        // expect:
        assertFalse(invokeOtherPartySigActivationScenario(knownSigs));
    }

    @Test
    void deniesSecondInactiveComplexOtherPartySig() throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_ADD_NEW_KEY_SCENARIO);
        // and:
        List<TransactionSignature> unknownSigs =
                PlatformSigOps.createCryptoSigsFrom(
                                List.of(
                                        DEFAULT_PAYER_KT.asJKey(),
                                        COMPLEX_KEY_ACCOUNT_KT.asJKey(),
                                        NEW_ACCOUNT_KT.asJKey()),
                                platformTxn.getPkToSigsFn(),
                                new ReusableBodySigningFactory(platformTxn))
                        .getPlatformSigs();
        List<TransactionSignature> knownSigs =
                asKind(
                        List.of(
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(0), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(1), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(2), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(3), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(4), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(6), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(7), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(9), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(10), VALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(11), INVALID),
                                new AbstractMap.SimpleEntry<>(unknownSigs.get(12), INVALID)));

        // expect:
        assertFalse(invokeOtherPartySigActivationScenario(knownSigs));
    }

    private List<TransactionSignature> expectedCryptoCreateScenarioSigs() throws Throwable {
        return PlatformSigOps.createCryptoSigsFrom(
                        List.of(
                                DEFAULT_PAYER_KT.asJKey(),
                                CryptoCreateFactory.DEFAULT_ACCOUNT_KT.asJKey()),
                        platformTxn.getPkToSigsFn(),
                        new ReusableBodySigningFactory(platformTxn))
                .getPlatformSigs();
    }

    private boolean allVerificationStatusesAre(Predicate<VerificationStatus> statusPred) {
        return platformTxn.getSigMeta().verifiedSigs().stream()
                .map(TransactionSignature::getSignatureStatus)
                .allMatch(statusPred);
    }

    private void statusMatches(ResponseCodeEnum expectedStatus) {
        assertEquals(expectedStatus, platformTxn.getExpandedSigStatus());
    }

    private boolean invokePayerSigActivationScenario(List<TransactionSignature> knownSigs) {
        SigRequirements keysOrder =
                new SigRequirements(
                        defaultLookupsFor(
                                aliasManager,
                                null,
                                () -> AccountStorageAdapter.fromInMemory(accounts),
                                () -> null,
                                ref -> null,
                                ref -> null),
                        mockSignatureWaivers,
                        mapWarmer);
        final var impliedOrdering =
                keysOrder.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY);
        final var impliedKey = impliedOrdering.getPayerKey();
        platformTxn.setSigMeta(
                RationalizedSigMeta.forPayerOnly(
                        impliedKey, new ArrayList<>(knownSigs), platformTxn));

        return payerSigIsActive(platformTxn, ONLY_IF_SIG_IS_VALID);
    }

    private boolean invokeOtherPartySigActivationScenario(List<TransactionSignature> knownSigs) {
        platformTxn.clearCryptoSigs();
        platformTxn.addAllCryptoSigs(knownSigs);
        final var hfsSigMetaLookup = new HfsSigMetaLookup(hfs, fileNumbers);
        SigRequirements keysOrder =
                new SigRequirements(
                        defaultLookupsFor(
                                aliasManager,
                                hfsSigMetaLookup,
                                () -> AccountStorageAdapter.fromInMemory(accounts),
                                null,
                                ref -> null,
                                ref -> null),
                        mockSignatureWaivers,
                        mapWarmer);

        return otherPartySigsAreActive(platformTxn, keysOrder, CODE_ORDER_RESULT_FACTORY);
    }

    private void invokeExpansionScenario() {
        final var hfsSigMetaLookup = new HfsSigMetaLookup(hfs, fileNumbers);
        SigMetadataLookup sigMetaLookups =
                defaultLookupsFor(
                        aliasManager,
                        hfsSigMetaLookup,
                        () -> AccountStorageAdapter.fromInMemory(accounts),
                        () -> null,
                        ref -> null,
                        ref -> null);
        SigRequirements keyOrder =
                new SigRequirements(sigMetaLookups, mockSignatureWaivers, mapWarmer);

        final var pkToSigFn = new PojoSigMapPubKeyToSigBytes(platformTxn.getSigMap());
        expandIn(platformTxn, keyOrder, pkToSigFn);
    }

    private Rationalization invokeRationalizationScenario() {
        // setup:
        SyncVerifier syncVerifier =
                new CryptoEngine(getStaticThreadManager(), CryptoConfigUtils.MINIMAL_CRYPTO_CONFIG)
                        ::verifySync;
        final var hfsSigMetaLookup = new HfsSigMetaLookup(hfs, fileNumbers);
        SigMetadataLookup sigMetaLookups =
                defaultLookupsFor(
                        aliasManager,
                        hfsSigMetaLookup,
                        () -> AccountStorageAdapter.fromInMemory(accounts),
                        () -> null,
                        ref -> null,
                        ref -> null);
        SigRequirements keyOrder =
                new SigRequirements(sigMetaLookups, mockSignatureWaivers, mapWarmer);

        // given:
        final var rationalization =
                new Rationalization(syncVerifier, keyOrder, new ReusableBodySigningFactory());

        rationalization.performFor(platformTxn);

        return rationalization;
    }

    private void setupFor(TxnHandlingScenario scenario) throws Throwable {
        hfs = scenario.hfs();
        aliasManager = mock(AliasManager.class);
        accounts = scenario.accounts();
        platformTxn = scenario.platformTxn();

        expectedErrorStatus = null;

        final var hfsSigMetaLookup = new HfsSigMetaLookup(hfs, fileNumbers);
        signingOrder =
                new SigRequirements(
                        defaultLookupsFor(
                                aliasManager,
                                hfsSigMetaLookup,
                                () -> AccountStorageAdapter.fromInMemory(accounts),
                                () -> null,
                                ref -> null,
                                ref -> null),
                        mockSignatureWaivers,
                        mapWarmer);
        final var payerKeys =
                signingOrder.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY);
        expectedSigs = new ArrayList<>();
        if (payerKeys.hasErrorReport()) {
            expectedErrorStatus = payerKeys.getErrorReport();
        } else {
            PlatformSigsCreationResult payerResult =
                    PlatformSigOps.createCryptoSigsFrom(
                            payerKeys.getOrderedKeys(),
                            new PojoSigMapPubKeyToSigBytes(platformTxn.getSigMap()),
                            new ReusableBodySigningFactory(platformTxn));
            expectedSigs.addAll(payerResult.getPlatformSigs());
            SigningOrderResult<ResponseCodeEnum> otherKeys =
                    signingOrder.keysForOtherParties(
                            platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY);
            if (otherKeys.hasErrorReport()) {
                expectedErrorStatus = otherKeys.getErrorReport();
            } else {
                PlatformSigsCreationResult otherResult =
                        PlatformSigOps.createCryptoSigsFrom(
                                otherKeys.getOrderedKeys(),
                                new PojoSigMapPubKeyToSigBytes(platformTxn.getSigMap()),
                                new ReusableBodySigningFactory(platformTxn));
                if (!otherResult.hasFailed()) {
                    expectedSigs.addAll(otherResult.getPlatformSigs());
                }
            }
        }
    }
}
