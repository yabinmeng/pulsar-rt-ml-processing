#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
VERTEX_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../.." &> /dev/null && pwd )

source "${VERTEX_RTML_HOMEDIR}/../../_bash_/utilities.sh"

usage() {
    echo
    echo "Usage: teardown_pulsar_cluster.sh [-h]"
    echo "                                  [-namespace <namespace>]"
    echo "                                  [-portForwardOnly]"
    echo ""
    echo "      -h : Show usage info"
    echo "      -namespace : (Optional) The K8s namespace to deploy the Pulsar cluster. Default: \"pulsar\""
    echo "      -portForwardOnly : (Optional) Only stop port forwarding when specified."
    echo
}

if [[ $# -gt 4 ]]; then
   usage
   errExit 20
fi

k8sNamespace=pulsar
portForwardOnly=0
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -namespace) k8sNamespace="$2"; shift ;;
      -portForwardOnly) portForwardOnly=1; ;;
      *) echo "[ERROR] Unknown parameter passed: $1"; exit 30 ;;
   esac
   shift
done
debugMsg "k8sNamespace=${k8sNamespace}"
debugMsg "portForwardOnly=${portForwardOnly}"


# Name must be lowercase
pulsarClstrName="rtmldemo"

helmChartHomeDir="${VERTEX_RTML_HOMEDIR}/deployment/pulsar-helm"

echo "============================================================== "
echo "= "
echo "= A Pulsar cluster with name \"${pulsarClstrName}\" will be deleted ...  "
echo "= "
echo

proxySvcName=$(kubectl -n ${k8sNamespace} get svc -l=component="proxy" -o name)
debugMsg "proxySvcName=${proxySvcName}"

if [[ -n "${proxySvcName// }" ]]; then
    echo
    echo "--------------------------------------------------------------"
    echo ">> Terminate the forwarded Pulsar Proxy ports ... "
    source ${VERTEX_RTML_HOMEDIR}/bash/infra/forward_pulsar_proxy_port.sh \
        -namespace "${k8sNamespace}" \
        -svcName "${proxySvcName}" \
        -act "stop"
    cd ${curDir}

    if [[ ${portForwardOnly} -eq 0 ]]; then
        echo
        echo "--------------------------------------------------------------"
        echo ">> Uninstall the Pulsar cluster (\"${pulsarClstrName}\") from the K8s namespace \"${k8sNamespace}\" ... "
        helmRepoExistence="$(chkHelmRepoExistence ${k8sNamespace} ${pulsarClstrName})"
        if [[ ${helmRepoExistence} -eq 1 ]]; then
            helm -n ${k8sNamespace} uninstall "${pulsarClstrName}" --wait
        fi
    fi
else
    echo
    echo "--------------------------------------------------------------"
    echo "[WARN] Doesn't detect Pulsar Proxy service. Likely there is no Pulsar cluster \"${pulsarClstrName}\" deployed in the K8s namespace \"${k8sNamespace}\"!"
fi

echo
