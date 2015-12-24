#!/bin/bash -ex
ruby ./load_into_postgres.rb | tugboat ssh vocabincontext -c "sudo -u postgres /usr/bin/psql"
