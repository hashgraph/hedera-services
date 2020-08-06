#! /bin/sh
find . -name '*.proto' | while read FILE; do
  cat $FILE | head -3 > tmp.proto
  LINES=$(wc -l $FILE | awk '{print $1}')
  TCOUNT=$((LINES-3))
  echo '' >> tmp.proto
  cat copyright.txt >> tmp.proto
  tail -n ${TCOUNT} $FILE >> tmp.proto
  mv tmp.proto $FILE
done
