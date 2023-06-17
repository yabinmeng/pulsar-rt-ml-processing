#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
RTML_DEMO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${RTML_DEMO_HOMEDIR}/bash/_utilities_.sh"

RTML_DEMO_HOMEDIR_DSRS_SRV="${RTML_DEMO_HOMEDIR}/code/test-rest-server"
java -jar ${RTML_DEMO_HOMEDIR_DSRS_SRV}/target/test-rest-server-1.0.0.jar
