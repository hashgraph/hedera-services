package com.hedera.services.fees;

import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandardCustomExemptionsTest {
    private StandardCustomPayerExemptions subject = new StandardCustomPayerExemptions();

    @Test
    void treasuriesAreExemptFromAllFees() {
        assertTrue(subject.isPayerExempt(
                WELL_KNOWN_META, FEE_WITH_EXEMPTIONS, TREASURY));
        assertTrue(subject.isPayerExempt(
                WELL_KNOWN_META, FEE_WITHOUT_EXEMPTIONS, TREASURY));
    }

    @Test
    void collectorsAreExemptFromOwnFees() {
        assertTrue(subject.isPayerExempt(
                WELL_KNOWN_META, FEE_WITH_EXEMPTIONS, COLLECTOR_OF_EXEMPT_FEE));
        assertTrue(subject.isPayerExempt(
                WELL_KNOWN_META, FEE_WITHOUT_EXEMPTIONS, COLLECTOR_OF_NON_EXEMPT_FEE));
    }


    @Test
    void collectorsAreNotExemptFromOtherFeesWithoutCollectorsExemption() {
        assertFalse(subject.isPayerExempt(
                WELL_KNOWN_META, FEE_WITHOUT_EXEMPTIONS, COLLECTOR_OF_EXEMPT_FEE));
    }

    @Test
    void nonCollectorsAreNotExemptFromFeesEvenWithCollectorExemption() {
        assertFalse(subject.isPayerExempt(
                WELL_KNOWN_META, FEE_WITH_EXEMPTIONS, CIVILIAN));
    }

    @Test
    void collectorsAreExemptFromOtherFeesWithCollectorExemption() {
        assertTrue(subject.isPayerExempt(
                WELL_KNOWN_META, FEE_WITH_EXEMPTIONS, COLLECTOR_OF_NON_EXEMPT_FEE));
    }


    private static final Id IRRELEVANT_TOKEN = new Id(0, 0, 12345);
    private static final Id TREASURY = new Id(0, 0, 1001);
    private static final Id CIVILIAN = new Id(0, 0, 1002);
    private static final Id COLLECTOR_OF_EXEMPT_FEE = new Id(0, 0, 1003);
    private static final Id COLLECTOR_OF_NON_EXEMPT_FEE = new Id(0, 0, 1004);


     private static final FcCustomFee FEE_WITHOUT_EXEMPTIONS =
             FcCustomFee.fractionalFee(
                     1, 5,
                     1, 3,
                     false, COLLECTOR_OF_NON_EXEMPT_FEE.asEntityId(), false);

    private static final FcCustomFee FEE_WITH_EXEMPTIONS =
            FcCustomFee.fractionalFee(
                    1, 5,
                    1, 3,
                    false, COLLECTOR_OF_EXEMPT_FEE.asEntityId(), true);

    private static final CustomFeeMeta WELL_KNOWN_META = new CustomFeeMeta(
            IRRELEVANT_TOKEN,
            TREASURY,
            List.of(FEE_WITH_EXEMPTIONS, FEE_WITHOUT_EXEMPTIONS));
}