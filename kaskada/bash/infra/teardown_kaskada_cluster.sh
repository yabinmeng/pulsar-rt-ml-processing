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
   echo "Usage: teardown_kaskada_cluster.sh [-h]" 
   echo "                                   [-portForwardOnly]"
   echo "       -h  : Show usage info"
   echo "       -portForwardOnly : (Optional) Only stop port forwarding when specified."
   echo
}

if [[ $# -gt 1 ]]; then
   usage
   echo "Incorrect input parametere count!"
   exit 10
fi

portForwardOnly=0
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -portForwardOnly) portForwardOnly=1; ;;
      *) echo "Unknown input parameter passed: $1"; exit 20 ;;
   esac
   shift
done
debugMsg "portForwardOnly=${portForwardOnly}"

k8sNamespace=kaskada
helmName=kaskada

kaskadaSvcName=$(kubectl -n ${k8sNamespace} get svc -o name)
debugMsg "kaskadaSvcName=${kaskadaSvcName}"

if [[ -n "${kaskadaSvcName// }" ]]; then
    echo
    echo "--------------------------------------------------------------"
    echo ">> Terminate the forwarded Kaskada ports: ${KASKADA_PORTS[@]} ... "
    source ${KASKADA_RTML_HOMEDIR}/bash/infra/forward_kaskada_port.sh \
        -namespace "${k8sNamespace}" \
        -svcName "${kaskadaSvcName}" \
        -act "stop"
    cd ${curDir}

    if [[ ${portForwardOnly} -eq 0 ]]; then
        echo
        echo "--------------------------------------------------------------"
        echo ">> Uninstall the Kaskada cluster from the K8s namespace \"${k8sNamespace}\" ... "
        helmRepoExistence="$(chkHelmRepoExistence ${k8sNamespace} ${helmName})"
        if [[ ${helmRepoExistence} -eq 1 ]]; then
            helm -n ${k8sNamespace} uninstall "${helmName}" --wait
        fi
    fi
else
    echo
    echo "--------------------------------------------------------------"
    echo "[WARN] Doesn't detect Kaskada service. Likely there is no Kaskada cluster deployed in the K8s namespace \"${k8sNamespace}\"!"
fi

echo
