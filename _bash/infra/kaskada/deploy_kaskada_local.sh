#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
KASKADA_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../../.." &> /dev/null && pwd )

source "${KASKADA_RTML_HOMEDIR}/_bash/utilities.sh"
# DEBUG=true
echo

##
# Show usage info 
#
usage() {   
   echo
   echo "Usage: deploy_kaskada_local.sh.sh [-h]"
   echo "                                  [-localBinFolder <kaskada_local_binary_folder>]"
   echo "       -h : Show usage info"
   echo "       -localBinFolder : Local folder where the Kaskada binary is located."
   echo
}

if [[ $# -gt 3 ]]; then
   usage
   errorExit 10 "Incorrect input parameter count!"
fi

localBinFolder="./kaskada/bin"
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -localBinFolder) localBinFolder=$2; shift ;;
      *) errExit 20 "Unknown input parameter passed: $1!"
   esac
   shift
done
debugMsg "localBinFolder=${localBinFolder}"

kasEngineBin="${localBinFolder}/kaskada-engine"
kasManagerBin="${localBinFolder}/kaskada-manager"
if ! [[ -f "${kasEngineBin}" && -f "${kasManagerBin}" ]]; then
   kasEngineBin=$(which kaskada-engine)
   kasManagerBin=$(which kaskada-manager)
fi
debugMsg "kasEngineBin=${kasEngineBin}"
debugMsg "kasManagerBin=${kasManagerBin}"

if ! [[ -f "${kasEngineBin}" && -f "${kasManagerBin}" ]]; then
   errExit 30 "Can't find the required Kaskada binaries in the specified local directory: \"${localBinFolder}\"."
fi

echo "Deploy Kaskada engine locally ..."
echo "- Start Kaskada manager ..."
$(${kasManagerBin} 2>&1 > kaskada-manager.log 2>&1 &)

echo "- Start Kaskada engine ..."
$(${kasEngineBin} serve > kaskada-engine.log 2>&1 &)

echo
