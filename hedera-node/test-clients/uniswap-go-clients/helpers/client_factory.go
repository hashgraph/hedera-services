package misc

import (
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"os"
)

const (
	defaultNetwork = string(hedera.NetworkNamePreviewnet)
	defaultPayerId string = "0.0.6016"
	defaultPayerKey string = "3ca7d9a337174a3e3e7b0d1496e2e8a8626f97a39b944805377c2f651a8f8c92"
)

func ClientFromEnvVars() (*hedera.Client) {
	var client *hedera.Client
	var err error

	targetNetwork := envVarOrDefault("NETWORK", defaultNetwork)
	if targetNetwork == "localhost" {
		var localNodes = map[string]hedera.AccountID{
			"127.0.0.1:50211": {Account: 3},
		}
		client = hedera.ClientForNetwork(localNodes)
	} else {
		client, err = hedera.ClientForName(targetNetwork)
	}
	if err != nil {
		panic(err)
	}

	payerId, err := hedera.AccountIDFromString(envVarOrDefault("PAYER_ID", defaultPayerId))
	if err != nil {
		panic(err)
	}

	payerKey, err := hedera.PrivateKeyFromString(envVarOrDefault("PAYER_KEY", defaultPayerKey))
	if err != nil {
		panic(err)
	}

	client.SetOperator(payerId, payerKey)

	fmt.Printf("🗝  Initialized client targeting %s; default payer is %s\n\n", targetNetwork, payerId.String())

	return client
}

func envVarOrDefault(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}