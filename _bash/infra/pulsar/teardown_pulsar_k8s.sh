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
    echo "Usage: deploy_pulsar_k8s.sh [-h]"
    echo "                            [-portForwardOnly]"
    echo "       -h  : Show usage info"
    echo "       -portForwardOnly : (Optional) Only do port forwarding when specified."
    echo
}

if [[ $# -gt 4 ]]; then
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

k8sNamespace=pulsar
helmName=pulsar

echo ">> Tear down the Pulsar cluster ..."
proxySvcName=$(kubectl -n ${k8sNamespace} get svc -l=component="proxy" -o name)
debugMsg "proxySvcName=${proxySvcName}"
if [[ -n "${proxySvcName// }" ]]; then
    echo
    echo "   - Terminate the forwarded Pulsar ports: ${PULSAR_PORTS[@]} ... "
    stopK8sPortForward "${PULSAR_PORTS[@]}"
    echo

    if [[ ${portForwardOnly} -eq 0 ]]; then
        echo "   - Uninstall the Pulsar cluster from the K8s namespace \"${k8sNamespace}\" ... "
        helm -n ${k8sNamespace} uninstall ${helmName}
        echo
    fi
else
    echo "   [WARN] Doesn't detect the corresponding Pulsar service. Likely there is no Flink cluster deployed in the K8s namespace \"${k8sNamespace}\"!"
fi

echo

