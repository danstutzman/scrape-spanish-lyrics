#!/bin/bash -ex
if [ "$1" == "" ]; then
  echo 2>&1 "Specify .txt file to load as first argument"
  exit 1
fi
PSQL=/Applications/Postgres.app/Contents/MacOS/bin/psql
javac LoadIntoPostgres.java && \
  java LoadIntoPostgres $1 lemmatized ~/dev/lemmatize-spanish | $PSQL -U postgres
