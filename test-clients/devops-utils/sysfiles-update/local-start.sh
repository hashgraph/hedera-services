#! /bin/sh
docker run -it \
  -v $(pwd):/workspace \
  --net=host \
  svctools
