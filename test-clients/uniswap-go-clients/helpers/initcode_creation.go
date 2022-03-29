package misc

import (
	"github.com/hashgraph/hedera-sdk-go/v2"
	"io/ioutil"
)

func UploadInitcode(client *hedera.Client, binLoc string) hedera.FileID {
	bytes, err := ioutil.ReadFile(binLoc)
	if err != nil {
		panic(err)
	}
	creationResponse, err := hedera.NewFileCreateTransaction().
		SetKeys(client.GetOperatorPublicKey()).
		SetContents([]byte {}).
		Execute(client)
	if err != nil {
		panic(err)
	}
	receipt, err := creationResponse.GetReceipt(client)
	if err != nil {
		panic(err)
	}

	fileId := *receipt.FileID
	appendResponse, err := hedera.NewFileAppendTransaction().
		SetMaxChunkSize(4096).
		SetFileID(fileId).
		SetContents(bytes).
		SetMaxTransactionFee(hedera.NewHbar(5)).
		Execute(client)
	if err != nil {
		panic(err)
	}

	_, err = appendResponse.GetReceipt(client)
	if err != nil {
		panic(err)
	}

	return *receipt.FileID
}
