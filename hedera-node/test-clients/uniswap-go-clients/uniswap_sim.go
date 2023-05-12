package main

import (
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	misc "internal/helpers"
	"os"
)

func main() {
	if len(os.Args) < 2 {
		panic("USAGE: go run uniswap_sim.go {setup|do-swaps|manage-liquidity}")
	}

	var client *hedera.Client
	var err error

	client = misc.ClientFromEnvVars()

	defer func() {
		err = client.Close()
		if err != nil {
			println(err.Error(), " - was unable to close client")
		}
	}()

	if os.Args[1] == "setup" {
		misc.SetupSimFromParams(client)
	} else if os.Args[1] == "do-swaps" {
		misc.DoSwaps(client)
	} else if os.Args[1] == "manage-liquidity" {
		misc.DoMints(client)
	} else {
		panic(fmt.Sprintf("Don't know what to do with '%s'", os.Args[1]))
	}
}
