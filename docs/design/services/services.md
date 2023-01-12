# Services

`hedera-app-spi` defines the SPI (service provider interface) for service modules. This section gives a brief outline
of what each of the different APIs are intended for.

## Service

The `Service` interface is implemented by each service module, for each conceptual "service" it provides. Typically, a
service module has a single implementation of `Service`, but the `hedera-token-service` is more complicated and may have
several services. For example, it may have a `TokenService` and a `CryptoService`. For simplicity in mapping concepts to
code, we have a `Service` subtype for each different *service* in our protobuf schema definitions.

The actual implementation class for a service is in the corresponding implementation. For example, the
`hedera-token-service-api` module defines an `CryptoService` interface that extends from `Service`, while the
`hedera-token-service` implementation module defines an implementation class such as `CryptoServiceImpl`.

The `hedera-app` module has access to all modules, including implementation modules, and uses this ability to create,
at construction time, instances of each service implementation. We could have instead used a reflection-based approach
with `ServiceLoader` to try to load services dynamically, but we found the resulting code to be difficult to understand.
By design, we strive for simple code that we can easily debug with stack traces that are short and obviously meaningful.
The downside to this design is that it requires changes to code to add or remove new service module implementations.
We accept this downside for the time being. A future revision may institute a **simple** DI solution that does not
depend on reflection, outside from what the `ServiceLoader` does.

Each `Service` implementation takes in its constructor a `StateRegistry` which is used for setting up the service state
in the merkle tree. The `Service` implementation also acts as a factory for `TransactionHandler`s,
`PreTransactionHandler`s, and `QueryHandler`s, and the main entrypoint into all API provided by the service module.
