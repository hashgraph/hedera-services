package com.hedera.services.fees.congestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class DelegatingMultiplierSourceTest {
    @Mock private ThrottleMultiplierSource delegate;

    private DelegatingMultiplierSource subject;

    @BeforeEach
    void setUp() {
        subject = new DelegatingMultiplierSource(delegate);
    }

    @Test
    void delegatesToString() {
        final var useful = "Lorem ipsum dolor sit amet";
        given(delegate.toString()).willReturn(useful);
        assertEquals(useful, subject.toString());
    }
}