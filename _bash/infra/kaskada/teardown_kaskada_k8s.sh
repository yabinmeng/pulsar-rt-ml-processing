#! /usr/local/bin/bash

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
    echo "Usage: teardown_kaskada_k8s.sh [-h]"
    echo "                               [-portForwardOnly]"
    echo "       -h  : Show usage info"
    echo "       -portForwardOnly : (Optional) Only stop port forwarding when specified."
    echo
}

if [[ $# -gt 1 ]]; then
    usage
    errorExit 10 "Incorrect input parameter count!"
fi

portForwardOnly=0
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h) usage; exit 0 ;;
        -portForwardOnly) portForwardOnly=1; ;;
        *) errExit 20 "Unknown input parameter passed: $1!"
    esac
    shift
done
debugMsg "portForwardOnly=${portForwardOnly}"

kubeCtlExistence=$(chkSysSvcExistence kubectl)
debugMsg "kubeCtlExistence=${kubeCtlExistence}"
if [[ ${kubeCtlExistence} -eq 0 ]]; then
    errExit 30 "'kubectl' isn't installed on the local machine, which is required to build scenario programs!"
fi

k8sNamespace=kaskada
helmName=kaskada

echo ">> Tear down the Kaskada cluster ..."
kaskadaSvcName=$(kubectl -n ${k8sNamespace} get svc -o name)
debugMsg "kaskadaSvcName=${kaskadaSvcName}"

if [[ -n "${kaskadaSvcName// }" ]]; then
    echo
    echo "   - Terminate the forwarded Kaskada ports: ${KASKADA_PORTS[@]} ... "
    stopK8sPortForward "${KASKADA_PORTS[@]}"
    echo

    if [[ ${portForwardOnly} -eq 0 ]]; then
        echo "   - Uninstall the Kaskada cluster from the K8s namespace \"${k8sNamespace}\" ... "
        helmRepoExistence="$(chkHelmRepoExistence ${k8sNamespace} ${helmName})"
        if [[ ${helmRepoExistence} -eq 1 ]]; then
            helm -n ${k8sNamespace} uninstall "${helmName}" --wait
        fi
        echo
    fi
else
    echo "   [WARN] Doesn't detect the Kaskada service. Likely there is no Kaskada cluster deployed in the K8s namespace \"${k8sNamespace}\"!"
fi

echo
