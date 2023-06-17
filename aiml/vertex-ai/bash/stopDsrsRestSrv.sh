#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
RTML_DEMO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${RTML_DEMO_HOMEDIR}/bash/_utilities_.sh"

#sudo lsof -i -P | grep LISTEN | grep 8090
curl -X POST localhost:8090/actuator/shutdown