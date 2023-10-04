package token;

import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.workflows.handle.HandlersInjectionModule;
import com.swirlds.common.metrics.Metrics;
import common.BaseScaffoldingComponent;
import common.BaseScaffoldingModule;
import dagger.BindsInstance;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Used by Dagger2 to instantiate an object graph with just the roots needed for testing
 * {@link com.hedera.node.app.service.token.TokenService} handlers.
 */
@Singleton
@Component(modules = {HandlersInjectionModule.class, BaseScaffoldingModule.class})
public interface TokenScaffoldingComponent extends BaseScaffoldingComponent {
    @Component.Factory
    interface Factory {
        TokenScaffoldingComponent create(@BindsInstance Metrics metrics);
    }

    CryptoTransferHandler cryptoTransferHandler();

    TokenMintHandler tokenMintHandler();
}
