#!/bin/bash -ex
PSQL=/Applications/Postgres.app/Contents/MacOS/bin/psql
javac LoadIntoPostgres.java && \
  java LoadIntoPostgres a2.txt ~/dev/lemmatize-spanish/songs | $PSQL -U postgres
