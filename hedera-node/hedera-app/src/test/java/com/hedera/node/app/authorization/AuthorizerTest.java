package com.hedera.node.app.authorization;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.mono.context.domain.security.HapiOpPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_CREATE_TOPIC;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

final class AuthorizerTest {
    private HapiOpPermissions hapiOpPermissions;
    private AccountID accountID;
    private HederaFunctionality hapiFunction;

    @BeforeEach
    void setUp() {
        hapiOpPermissions = mock(HapiOpPermissions.class);
        accountID = AccountID.newBuilder().build();
        hapiFunction = CONSENSUS_CREATE_TOPIC;
    }

    @Test
    @DisplayName("Account ID is null throws")
    void accountIdIsNullThrows() {
        // given:
        final var authorizer = new AuthorizerImpl(hapiOpPermissions);

        // expect:
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> authorizer.isAuthorized(null, hapiFunction))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Hapi function is null throws")
    void hapiFunctionIsNullThrows() {
        // given:
        final var authorizer = new AuthorizerImpl(hapiOpPermissions);

        // expect:
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> authorizer.isAuthorized(accountID, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Account is not permitted")
    void accountIsNotPermitted() {
        // given:
        final var authorizer = new AuthorizerImpl(hapiOpPermissions);
        given(hapiOpPermissions.permissibilityOf2(any(), any())).willReturn(AUTHORIZATION_FAILED);

        // expect:
        final var authorized = authorizer.isAuthorized(accountID, hapiFunction);
        assertThat(authorized).isFalse();
    }

    @Test
    @DisplayName("Account is permitted")
    void accountIsPermitted() {
        // given:
        final var authorizer = new AuthorizerImpl(hapiOpPermissions);
        given(hapiOpPermissions.permissibilityOf2(any(), any())).willReturn(OK);

        // expect:
        final var authorized = authorizer.isAuthorized(accountID, hapiFunction);
        assertThat(authorized).isTrue();
    }
}
