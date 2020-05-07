#! /bin/sh
docker run --rm \
  -v $(pwd)/src/main/proto/doc:/out \
  -v $(pwd)/src/main/proto:/protos \
  pseudomuto/protoc-gen-doc
cp src/main/proto/doc/index.html HAPI.html
