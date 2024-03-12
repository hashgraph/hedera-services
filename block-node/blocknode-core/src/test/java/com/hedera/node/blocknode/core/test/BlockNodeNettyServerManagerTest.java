package com.hedera.node.blocknode.core.test;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.config.data.BlockNodeGrpcConfig;
import com.hedera.node.blocknode.core.BlockNodeNettyServerManager;
import com.hedera.node.blocknode.core.BlockNodeServicesRegistryImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//@ExtendWith(MockitoExtension.class)
final class BlockNodeNettyServerManagerTest {

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private  BlockNodeServicesRegistryImpl blockNodeRegistry;

    private BlockNodeNettyServerManager serverManager;
    //failing on  this.configProvider with mock maker err, changes when i introduce openMocks
    //cannot create the mock
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        //this.configProvider = mock(ConfigProvider.class);
        //this.blockNodeRegistry = mock(BlockNodeServicesRegistryImpl.class);
        this.serverManager = new BlockNodeNettyServerManager(configProvider, blockNodeRegistry);
    }

//    @AfterEach
//    void tearDown() {
//        if (serverManager.isRunning()) {
//            serverManager.stop();
//        }
//    }

    @Test
    void startServer_Success() {
        // Mock necessary dependencies
        //when(configProvider.getConfiguration().getConfigData(any())).thenReturn(mock(BlockNodeGrpcConfig.class));

        // Start the server
        serverManager.start();

        // Assertions
        assertTrue(serverManager.isRunning());
        assertEquals(1234, serverManager.port()); // Replace with the expected port
    }

    @Test
    void startServer_AlreadyRunning() {
        // Mock necessary dependencies
        //when(configProvider.getConfiguration().getConfigData(any())).thenReturn(mock(BlockNodeGrpcConfig.class));

        // Start the server
        serverManager.start();

        // Attempt to start again
        assertThrows(IllegalStateException.class, () -> serverManager.start());
    }

    @Test
    void stopServer_Success() {
        // Mock necessary dependencies
        //when(configProvider.getConfiguration().getConfigData(any())).thenReturn(mock(BlockNodeGrpcConfig.class));

        // Start and stop the server
        serverManager.start();
        serverManager.stop();

        // Assertions
        assertFalse(serverManager.isRunning());
    }

    @Test
    void portWhenServerNotRunning() {
        // Assertion
        assertEquals(-1, serverManager.port());
    }

    // Add more test cases as needed

}
