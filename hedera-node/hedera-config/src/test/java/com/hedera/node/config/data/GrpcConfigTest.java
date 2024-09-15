/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

import org.junit.jupiter.api.Test;

class GrpcConfigTest {

    @Test
    void testValidConfiguration() {
        // Test valid configuration
        GrpcConfig config = new GrpcConfig(50211, 50212, 60211, 60212, 4194304, 4194304, 4194304);

        assertThat(config).isNotNull();
        assertThat(config.port()).isEqualTo(50211);
        assertThat(config.tlsPort()).isEqualTo(50212);
        assertThat(config.workflowsPort()).isEqualTo(60211);
        assertThat(config.workflowsTlsPort()).isEqualTo(60212);
        assertThat(config.maxMessageSize()).isEqualTo(4194304);
        assertThat(config.maxResponseSize()).isEqualTo(4194304);
        assertThat(config.noopMarshallerMaxMessageSize()).isEqualTo(4194304);
    }

    @Test
    void testInvalidPortAndTlsPort() {
        Throwable throwable = catchThrowable(() -> {
            new GrpcConfig(50212, 50212, 50212, 60212, 4194304, 4194304, 4194304);
        });
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grpc.port and grpc.tlsPort must be different");
    }

    @Test
    void testInvalidWorkflowsPortAndTlsPort() {
        Throwable throwable = catchThrowable(() -> {
            new GrpcConfig(50211, 50212, 60212, 60212, 4194304, 4194304, 4194304);
        });
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grpc.workflowsPort and grpc.workflowsTlsPort must be different");
    }

    @Test
    void testValidZeroPorts() {
        GrpcConfig config = new GrpcConfig(0, 0, 60211, 60212, 4194304, 4194304, 4194304);

        assertThat(config).isNotNull();
        assertThat(config.port()).isEqualTo(0);
        assertThat(config.tlsPort()).isEqualTo(0);
        assertThat(config.workflowsPort()).isEqualTo(60211);
        assertThat(config.workflowsTlsPort()).isEqualTo(60212);
        assertThat(config.maxMessageSize()).isEqualTo(4194304);
        assertThat(config.maxResponseSize()).isEqualTo(4194304);
        assertThat(config.noopMarshallerMaxMessageSize()).isEqualTo(4194304);
    }

    @Test
    void testInvalidMinValue() {
        Throwable throwable = catchThrowable(() -> {
            new GrpcConfig(-1, 50212, 60211, 60212, 4194304, 5194304, 7194304);
        });

        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grpc.port must be between 0 and 65535");
    }

    @Test
    void testInvalidMaxValue() {
        Throwable throwable = catchThrowable(() -> {
            new GrpcConfig(65536, 50212, 60211, 60212, 4194304, 5194304, 7194304);
        });

        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grpc.port must be between 0 and 65535");
    }

    @Test
    void testMaxMessageSizeMaxValue() {
        Throwable throwable = catchThrowable(() -> {
            new GrpcConfig(50211, 50212, 60211, 60212, 4194305, 4194304, 4194304);
        });

        assertThat(throwable)
                .isNotNull()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grpc.maxMessageSize must be between 0 and 4194304");
    }

    @Test
    void testMaxResponseSizeMaxValue() {
        Throwable throwable = catchThrowable(() -> {
            new GrpcConfig(50211, 50212, 60211, 60212, 4194304, 4194305, 4194304);
        });

        assertThat(throwable)
                .isNotNull()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grpc.maxResponseSize must be between 0 and 4194304");
    }

    @Test
    void testNoopMarshallerMaxMessageSizeMaxValue() {
        Throwable throwable = catchThrowable(() -> {
            new GrpcConfig(50211, 50212, 60211, 60212, 4194304, 4194304, 4194305);
        });

        assertThat(throwable)
                .isNotNull()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grpc.noopMarshallerMaxMessageSize must be between 0 and 4194304");
    }
}
