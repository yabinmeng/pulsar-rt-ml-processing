# requirement: pip install jproperties
from jproperties import Properties

# requirement: pip install pulsar-client
# requirement: pip install pulsar-client[avro]
import pulsar
from pulsar.schema import *

# requirement: pip install cassandra-driver
from cassandra.cluster import Cluster
from cassandra.auth import PlainTextAuthProvider

from datetime import datetime

raw_cass_tbl_col_name_list = ["year",
                              "month",
                              "day",
                              "order_seq",
                              "country",
                              "session",
                              "category",
                              "model",
                              "color",
                              "location",
                              "photography",
                              "price",
                              "price_ind",
                              "page",
                              "event_time",
                              "so_key"]


class EShopInputTopic(Record):
    year = Integer()
    month = Integer()
    day = Integer()
    order_seq = Integer()
    country = Integer()
    session = Integer()
    category = Integer()
    model = String()
    color = Integer()
    location = Integer()
    photography = Integer()
    price = Integer()
    price_ind = Integer()
    page = Integer()
    time = Long()
    so_key = Integer()


def dt2epoch(dt):
    epoch = datetime.utcfromtimestamp(0)
    return int((dt - epoch).total_seconds() * 1000)


def str2bool(v):
    return v.lower() in ("yes", "true", "t", "1")


# load configuration settings from a properties file
def load_prop_into_dict(propFileName):
    configs = Properties()
    with open(propFileName, 'rb') as config_file:
        configs.load(config_file)
    return configs


# connect to an Astra Streaming cluster
# - return value: Session
def connect_to_as_tenant(clnt_conn_configs):
    broker_svc_url = clnt_conn_configs.get('brokerServiceUrl').data.strip()
    auth_params = clnt_conn_configs.get('authParams').data.strip()
    jwt_token_value = auth_params.split(":")[1]

    return pulsar.Client(broker_svc_url,
                         authentication=pulsar.AuthenticationToken(jwt_token_value))


# connect to an Astra Streaming cluster
# - return value: Session
def connect_to_astra_db_cluster(clientId, clientSecret, secBundleFile):
    cloud_config = {
        'secure_connect_bundle': secBundleFile
    }
    auth_provider = PlainTextAuthProvider(clientId, clientSecret)
    cluster = Cluster(cloud=cloud_config, auth_provider=auth_provider)
    return cluster.connect()
