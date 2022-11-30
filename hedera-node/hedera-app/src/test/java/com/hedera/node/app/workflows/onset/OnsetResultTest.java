package com.hedera.node.app.workflows.onset;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnsetResultTest {

    @Test
    void checkConstructorWithIllegalArguments() {
        // given
        final var txBody = TransactionBody.getDefaultInstance();
        final var signatureMap = SignatureMap.getDefaultInstance();
        final var functionality = HederaFunctionality.NONE;

        // then
        assertThatThrownBy(() -> new OnsetResult(null, signatureMap, functionality))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OnsetResult(txBody, null, functionality))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OnsetResult(txBody, signatureMap, null))
                .isInstanceOf(NullPointerException.class);
    }
}
