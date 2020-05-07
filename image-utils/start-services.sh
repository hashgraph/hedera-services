#! /bin/sh
cp config-mount/*.properties data/config 
cp config-mount/* . 
./wait-for-it 172.20.4.2:5432 -t 60
java -cp 'data/lib/*' -Dflag=1 -Dfile.encoding='utf-8' \
  com.swirlds.platform.Browser
