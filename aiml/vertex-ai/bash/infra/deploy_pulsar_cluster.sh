#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
VERTEX_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../.." &> /dev/null && pwd )

source "${VERTEX_RTML_HOMEDIR}/../../_bash_/utilities.sh"

usage() {
   echo
   echo "Usage: deploy_pulsar_cluster.sh [-h]"
   echo "                                [-namespace <namespace>]"
   echo "                                [-upgrade]"
   echo ""
   echo "       -h         : Show usage info"
   echo "       -namespace : (Optional) The K8s namespace to deploy the Pulsar cluster. Default: \"pulsar\""
   echo "       -upgrade   : (Optional) Whether to upgrade the existing Pulsar cluster"
   echo
}

if [[ $# -gt 4 ]]; then
   usage
   errExit 20
fi

upgradeExistingCluster=0
k8sNamespace=pulsar
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -namespace) k8sNamespace="$2"; shift ;;
      -upgrade) upgradeExistingCluster=1; ;;
      *) echo "[ERROR] Unknown parameter passed: $1"; exit 30 ;;
   esac
   shift
done

# Name must be lowercase
pulsarClstrName="rtmldemo"

helmChartHomeDir="${VERTEX_RTML_HOMEDIR}/deployment/pulsar-helm"
helmChartFile="values.yaml"


echo "============================================================== "
echo "= "
echo "= Helm chart file \"${helmChartFile}\" will be used to deploy the Pulsar cluster \"${pulsarClstrName}\" under K8s namespace \"${k8sNamespace}\" ...  "
echo "= "

echo
echo "--------------------------------------------------------------"
echo "Creating K8s namespace ${nsName} if doesn't exist ..."
existingPulsarNs=$(kubectl get namespace | grep ${k8sNamespace})
if [[ -z "${existingPulsarNs}" ]]; then
  kubectl create namespace ${k8sNamespace}
fi


echo
echo "--------------------------------------------------------------"
echo ">> Install \"cert_manager\" as a prerequisite ... "
cmGhRelUrlBase="https://github.com/cert-manager/cert-manager/releases"
cmVersion=$(chkGitHubLatestRelVer "${cmGhRelUrlBase}/latest")
debugMsg "certManagerVersion=${cmVersion}"

kubectl apply -f "https://github.com/cert-manager/cert-manager/releases/download/v${cmVersion}/cert-manager.yaml"

pulsarClusterCreationErrCode=$?
if [[ "${pulsarClusterCreationErrCode}" -ne 0 ]]; then
    echo "[ERROR] Failed to install prerequisite cert manager !"
    errExit 80;
fi

echo
echo "--------------------------------------------------------------"
echo ">> Add Pulsar helm to the local repository ... "
helm repo add datastax-pulsar https://datastax.github.io/pulsar-helm-chart
helm repo update datastax-pulsar

echo
echo "--------------------------------------------------------------"
echo ">> Check if the Pulsar cluster named \"${pulsarClstrName}\" already exists ... "
clusterExistence=$(helm -n ${k8sNamespace} status ${pulsarClstrName} 2>/dev/null | grep STATUS | awk -F': ' '{print $2}')
echo "   clusterExistence=${clusterExistence}; upgradeExistingCluster=${upgradeExistingCluster}"

# There is no existing Pulsar cluster with the same name
if [[ -z "${clusterExistence}" ]]; then
    echo
    echo "--------------------------------------------------------------"
    echo ">> Install a Pulsar cluster named \"${pulsarClstrName}\" ... "
    helm install -n ${k8sNamespace} "${pulsarClstrName}" -f "${helmChartHomeDir}/${helmChartFile}" datastax-pulsar/pulsar
else
    if [[ ${upgradeExistingCluster} -ne 0 ]]; then
        echo
        echo "--------------------------------------------------------------"
        echo ">> Upgrade the existing Pulsar cluster named \"${pulsarClstrName}\" ... "
        helm upgrade -n ${k8sNamespace} --install "${pulsarClstrName}" -f "${helmChartHomeDir}/${helmChartFile}" datastax-pulsar/pulsar
    fi
fi

pulsarClusterCreationErrCode=$?
if [[ "${pulsarClusterCreationErrCode}" -ne 0 ]]; then
    echo "[ERROR] Failed to create/upgrade the Pulsar clsuter !"
    errExit 200;
fi


echo
echo "--------------------------------------------------------------"
echo ">> Wait for Proxy deployment is ready ... "
## wati for Proxy deployment is ready (this approach doesn't work for K8s services)
kubectl -n ${k8sNamespace} wait --timeout=600s --for condition=Available=True deployment -l=component="proxy"

echo
echo ">> Wait for Proxy service is ready ... "
proxySvcName=$(kubectl -n ${k8sNamespace} get svc -l=component="proxy" -o name)
debugMsg "proxySvcName=${proxySvcName}"
## wait for Proxy service is assigned an external IP
until [ -n "$(kubectl -n ${k8sNamespace} get ${proxySvcName} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')" ]; do
    sleep 2
done

if [[ -n "${proxySvcName// }" ]]; then
    echo
    echo "--------------------------------------------------------------"
    echo ">> Forward Pulsar Proxy ports to localhost: ${PULSAR_PROXY_PORTS[@]} ... "
    source ${VERTEX_RTML_HOMEDIR}/bash/infra/forward_pulsar_proxy_port.sh \
        -namespace "${k8sNamespace}" \
        -svcName "${proxySvcName}" \
        -act "start"
fi

echo
