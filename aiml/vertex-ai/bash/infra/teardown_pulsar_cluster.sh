#! /bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
RTML_DEMO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../.." &> /dev/null && pwd )

source "${RTML_DEMO_HOMEDIR}/bash/_utilities_.sh"

usage() {
    echo
    echo "Usage: teardown_pulsar_cluster.sh [-h]"
    echo "                                  [-namespace <namespace>]"
    echo ""
    echo "      -h : Show usage info"
    echo "      -namespace : (Optional) The K8s namespace to deploy the Pulsar cluster. Default: \"pulsar\""
    echo
}

if [[ $# -gt 3 ]]; then
   usage
   errExit 20
fi

k8sNamespace=pulsar
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -namespace) k8sNamespace="$2"; shift ;;
      *) echo "[ERROR] Unknown parameter passed: $1"; exit 30 ;;
   esac
   shift
done


# Name must be lowercase
pulsarClstrName="rtmldemo"

helmChartHomeDir="${RTML_DEMO_HOMEDIR}/conf/helm"

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
    source ${RTML_DEMO_HOMEDIR}/bash/infra/forward_pulsar_proxy_port.sh \
        -act "stop" \
        -proxySvc "${proxySvcName}" \
        -tlsEnabled "false"
    cd ${curDir}

    echo
    echo "--------------------------------------------------------------"
    echo ">> Uninstall the Pulsar cluster (\"${pulsarClstrName}\") from the K8s namespace \"${k8sNamespace}\" ... "
    helmRepoExistence="$(chkHelmRepoExistence ${k8sNamespace} ${pulsarClstrName})"
    if [[ ${helmRepoExistence} -eq 1 ]]; then
        helm -n ${k8sNamespace} uninstall "${pulsarClstrName}" --wait
    fi
else
    echo
    echo "--------------------------------------------------------------"
    echo "[WARN] Doesn't detect Pulsar Proxy service. Likely there is no Pulsar cluster \"${pulsarClstrName}\" deployed in the K8s namespace \"${k8sNamespace}\"!"
fi

echo
