#!/bin/bash -ex
if [ "$1" == "" ]; then
  echo 2>&1 "Specify .txt files to load as first argument"
  exit 1
fi
PSQL=/Applications/Postgres.app/Contents/MacOS/bin/psql

echo "Starting lemmatizer server..."
FREELINGSHARE=/usr/local/share/freeling \
  ~/dev/lemmatize-spanish/myfreeling/src/main/analyzer -f \
  ~/dev/lemmatize-spanish/myfreeling/data/config/es.cfg --server --port 3001 &
LEMMATIZER_PID="$!"
sleep 6

javac LoadIntoPostgres.java && java LoadIntoPostgres ~/dev/lemmatize-spanish/myfreeling/src/main/analyzer_client $@ | $PSQL -U postgres

kill $LEMMATIZER_PID
