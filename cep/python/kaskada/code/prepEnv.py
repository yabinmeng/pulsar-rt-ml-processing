import argparse
import logging
import os.path
import sys

from pathlib import Path
from _utils import *

# requirement: pip install requests
import requests

logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.DEBUG)

arg_parser = argparse.ArgumentParser()
arg_parser.add_argument(
    "-cf", "--cfile", help="main configuration file", default="../conf/main-cfg.properties")
arg_parser.add_argument(
    "-cdt", "--createDbTbl", help="create Astra DB tables (input and output)", default="true")
arg_parser.add_argument(
    "-cpt", "--createPulsarTopic", help="create Astra Streaming topics (input and output)", default="true")
arg_parser.add_argument(
    "-ldr", "--loadRawSrc", help="load raw data records into the Astra DB input table", default="false")
# the following 2 parameters are only relevant when '-ldr' parameter is True
arg_parser.add_argument(
    "-rsf", "--rawSrcFile", help="source file that has the raw data")
arg_parser.add_argument(
    "-ldn",
    "--loadRecNum",
    help="number of the records to be loaded from the source file (-1 means to load all)",
    default=100)

args = arg_parser.parse_args()


def process_pulsar_response(status_code, 
                            pulsar_entity_type,
                            pulsar_entity_name):
    exit_system = False
    if 200 <= status_code < 300:
        print("The specified Pulsar entity \"[{}] {}\" is successfully created!"
              .format(pulsar_entity_type, pulsar_entity_name))
    elif status_code == 409:
        print("The specified Pulsar enity \"[{}] {}\" already exists!"
              .format(pulsar_entity_type, pulsar_entity_name))
    else:
        exit_system = True
        print("Failed to create the specified Pulsar entity \"[{}] {}\" with status code \"{}\"!"
              .format(pulsar_entity_type, pulsar_entity_name, status_code))
    
    return exit_system


# create a Pulsar namespace (using Rest API)
# - TBD: can we create the admin object as 'PulsarAdminClient' in Java?
def create_pulsar_namespace(clnt_conn_configs, tnt, ns):
    web_svc_url = clnt_conn_configs.get('webServiceUrl').data.strip()
    auth_params = clnt_conn_configs.get('authParams').data.strip()
    jwt_token_value = auth_params.split(":")[1].strip()

    end_point = web_svc_url + '/admin/v2/namespaces/' + tnt + '/' + ns
    headers = {"Authorization": "Bearer " + jwt_token_value}

    result = requests.put(end_point, headers=headers)
    return result


# create a Pulsar topic (using Rest API)
# - default to a partitioned topic with 3 partitions
def create_pulsar_topic(clnt_conn_configs, tnt, ns, topic):
    web_svc_url = clnt_conn_configs.get('webServiceUrl').data.strip()
    auth_params = clnt_conn_configs.get('authParams').data.strip()
    jwt_token_value = auth_params.split(":")[1].strip()

    end_point = web_svc_url + '/admin/v2/persistent/' + tnt + '/' + ns + '/' + topic + '/partitions'
    headers = {"Authorization": "Bearer " + jwt_token_value,
               "Content-Type": "text/plain"}
    payload = "3"

    result = requests.put(end_point, headers=headers, data=payload)
    return result


# create a Pulsar topic (using Rest API)
# - default to a partitioned topic with 5 partitions
def create_pulsar_topic_schema(clnt_conn_configs, 
                               topic_schema_def_file,
                               tnt,
                               ns,
                               topic):
    topic_schema_json_str = Path(topic_schema_def_file).read_text()

    web_svc_url = clnt_conn_configs.get('webServiceUrl').data.strip()
    auth_params = clnt_conn_configs.get('authParams').data.strip()
    jwt_token_value = auth_params.split(":")[1].strip()

    end_point = web_svc_url + '/admin/v2/schemas/' + tnt + '/' + ns + '/' + topic + '/schema'
    headers = {"Authorization": "Bearer " + jwt_token_value,
               "Content-Type": "application/json"}

    result = requests.post(end_point, headers=headers, data=topic_schema_json_str)
    return result


def load_raw_cass_table(session,
                        keyspace,
                        table,
                        tbl_cols,
                        src_file_name,
                        record_num):
    col_num = tbl_cols.count(',') + 1
    question_mark_str = ("?," * col_num).rstrip(",")
    insert_stmt = session.prepare("INSERT INTO " + keyspace + "." + table + "(" + tbl_cols + ") "
                                  "VALUES (" + question_mark_str + ");")
    record_loaded = 0
    with open(src_file_name) as file:
        record_line = next(file, '').strip()
        while record_line:
            if (record_num == -1) or (record_loaded < record_num):
                rawfield_values = record_line.split(",")

                tgtfield_values = []
                for idx in range(0, len(rawfield_values)):
                    # 'model' columns are of "text" type
                    if idx == 7:
                        tgtfield_values.append(rawfield_values[idx])
                    else:
                        tgtfield_values.append(int(rawfield_values[idx]))

                query = insert_stmt.bind(tgtfield_values)
                session.execute(query)

                record_loaded = record_loaded + 1
                record_line = next(file, '').strip()
            else:
                break


if __name__ == '__main__':
    main_cfg_file = args.cfile
    if not os.path.isfile(main_cfg_file):
        print("The specified main configuration file (" + main_cfg_file + ") is not valid")
        sys.exit()
    else:
        main_cfg_props = load_prop_into_dict(main_cfg_file)

        ###########################
        #
        # Create Astra DB Tables and load source data into the Input Table if requested
        #
        ###########################

        b_create_astra_db_tbl = str2bool(args.createDbTbl)
        b_load_raw_src_data = str2bool(args.loadRawSrc)

        keyspace_name = main_cfg_props.get('ad.keyspace').data
        input_tbl_name = main_cfg_props.get('ad.table.input').data
        output_tbl_name = main_cfg_props.get('ad.table.output').data

        if b_create_astra_db_tbl or b_load_raw_src_data:
            cassandra_session = connect_to_astra_db_cluster(
                main_cfg_props.get('ad.client.id').data.strip(),
                main_cfg_props.get('ad.client.secret').data.strip(),
                main_cfg_props.get('ad.secure.bundle').data.strip()
            )

        ##
        # Create C* tables in Astra DB
        # - note: Astra DB keyspace must be created in advance via the UI
        if b_create_astra_db_tbl:
            # Expecting a CQL statement definition file as below in the specified sub-folder:
            #   ./conf/cassdb-cql/<table_name>.stmt

            for tbl_name in [input_tbl_name, output_tbl_name]:
                if tbl_name == input_tbl_name:
                    tbl_stmt_def_file = '../conf/cql-cassandra/input_tbl.stmt'
                else:
                    tbl_stmt_def_file = '../conf/cql-cassandra/output_tbl.stmt'
                if not os.path.isfile(tbl_stmt_def_file):
                    print("Can't find the corresponding CQL statement file \"{}\""
                          .format(tbl_stmt_def_file))
                    sys.exit()

                input_tbl_crt_cql_stmt = Path(tbl_stmt_def_file)\
                    .read_text() \
                    .replace('<KS_NAME>', keyspace_name) \
                    .replace('<TBL_NAME>', tbl_name)\
                    .replace('\n', '')
                cassandra_session.execute(input_tbl_crt_cql_stmt)

        # Load raw data into the corresponding AstraDB table
        if b_load_raw_src_data:
            raw_data_src_file = args.rawSrcFile
            if not os.path.isfile(raw_data_src_file):
                print("The specified Cassandra raw data source file (" + raw_data_src_file + ") is not valid")
                sys.exit()

            load_record_num: int = int(args.loadRecNum)
            if not ((load_record_num > 0) or (load_record_num == -1)):
                print("The specified 'loadRecNum' parameter has a invalid value (" + load_record_num + ")!")
                print("   the value must be a positive integer or -1 (which means all records)")
                sys.exit()

            load_raw_cass_table(cassandra_session,
                                keyspace_name,
                                input_tbl_name,
                                ','.join(raw_cass_tbl_col_name_list),
                                raw_data_src_file,
                                load_record_num)


        ###########################
        #
        # Create Astra Streaming Topics, Schema (if requested), and the C* Sink Connector
        #
        ###########################

        ##
        # Create the Pulsar topics and the corresponding JSON schema (if requested)
        b_create_pulsar_topic = str2bool(args.createPulsarTopic)        
        if b_create_pulsar_topic:
            pulsar_clnt_conn_file = main_cfg_props.get('as.client.conf').data.strip()
            if not os.path.isfile(pulsar_clnt_conn_file):
                print("The specified Pulsar client connection file (" + pulsar_clnt_conn_file + ") is not valid")
                sys.exit()

            pulsar_clnt_conn_props = load_prop_into_dict(pulsar_clnt_conn_file)

            pulsar_tenant = main_cfg_props.get('as.tenant').data.strip()
            pulsar_namespace = main_cfg_props.get('as.namespace').data.strip()
            pulsar_topic_input = main_cfg_props.get('as.topic.input').data.strip()
            pulsar_topic_output = main_cfg_props.get('as.topic.output').data.strip()
            b_create_topic_schema = str2bool(main_cfg_props.get('as.topic.schema').data.strip())

            # Create the specified Pulsar namespace
            res = create_pulsar_namespace(pulsar_clnt_conn_props, pulsar_tenant, pulsar_namespace)
            if process_pulsar_response(res.status_code,
                                       "namespace",
                                       pulsar_tenant + "/" + pulsar_namespace):
                sys.exit()

            ##
            # Create the specified Pulsar topics (Input Topic and Output Topic)
            for tp_name in [pulsar_topic_input, pulsar_topic_output]:
                res = create_pulsar_topic(pulsar_clnt_conn_props,
                                          pulsar_tenant,
                                          pulsar_namespace,
                                          tp_name)
                if process_pulsar_response(res.status_code,
                                           "topic",
                                           pulsar_tenant + "/" + pulsar_namespace + "/" + tp_name):
                    sys.exit()

                ##
                # If requested, create the JSON schema for the Pulsar Input Topic
                if b_create_topic_schema:
                    if tp_name == pulsar_topic_input:
                        topic_schema_def_file = '../conf/pulsar-schema/input_topic.json'
                    else:
                        topic_schema_def_file = '../conf/pulsar-schema/output_topic.json'    
                    if not os.path.isfile(topic_schema_def_file):
                        print("Can't find the corresponding topic schema definition file \"{}\""
                              .format(topic_schema_def_file))
                        sys.exit()
                    else:
                        res = create_pulsar_topic_schema(pulsar_clnt_conn_props,
                                                         topic_schema_def_file,
                                                         pulsar_tenant,
                                                         pulsar_namespace,
                                                         tp_name)
                        if process_pulsar_response(res.status_code,
                                                   "schema",
                                                   pulsar_tenant + "/" + pulsar_namespace + "/" + tp_name):
                            sys.exit()
