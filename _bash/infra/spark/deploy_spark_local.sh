#! /bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SCENARIO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../../.." &> /dev/null && pwd )

source "${SCENARIO_HOMEDIR}/_bash/utilities.sh"
# DEBUG=true
echo

##
# Show usage info 
#
usage() {   
   echo
   echo "Usage: deploy_spark_local.sh.sh [-h]"
   echo "                                  [-localBinFolder <spark_local_binary_folder>]"
   echo "       -h : Show usage info"
   echo "       -localBinFolder : Local folder where the Spark binary is located."
   echo
}

if [[ $# -gt 3 ]]; then
   usage
   errorExit 10 "Incorrect input parameter count!"
fi

localBinFolder="./spark"
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -localBinFolder) localBinFolder=$2; shift ;;
      *) errExit 20 "Unknown input parameter passed: $1!"
   esac
   shift
done
debugMsg "localBinFolder=${localBinFolder}"

echo "Deploy a local Spark cluster ..."
echo "- Start Spark master ..."
${localBinFolder}/sbin/start-master.sh --webui-port 9090

echo "- Start Spark worker(s) ..."
${localBinFolder}/sbin/start-workers.sh --webui-port 9090
