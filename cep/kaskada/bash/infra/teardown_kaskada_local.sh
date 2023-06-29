#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
KASKADA_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../.." &> /dev/null && pwd )

source "${KASKADA_RTML_HOMEDIR}/../_bash_/utilities.sh"

echo

##
# Show usage info 
#
usage() {   
   echo
   echo "Usage: teardown_kaskada_local [-h]" 
   echo "       -h  : Show usage info"
   echo
}

if [[ $# -gt 1 ]]; then
   usage
   echo "Incorrect input parametere count!"
   exit 10
fi

echo "Teardown Kaskada manager and engine locally ..."
kaskadaSvcs=("kaskada-engine" "kaskada-manager")
for ksvc in "${kaskadaSvcs[@]}"; do
    pid=$(ps -ef | grep "${ksvc}" | grep -v grep | awk '{print $2}')
   if [[ -n ${pid// } ]]; then
        echo "- Kill ${ksvc} (pid=${pid}) ..."
        kill -TERM ${pid}
    fi
done

echo