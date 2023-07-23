import argparse
import logging
import os.path
import sys

from _utils import *

from kaskada.api.remote_session import RemoteBuilder
import kaskada.materialization as km
import kaskada.table as kt
import kaskada.query as kq

logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.DEBUG)

##
# What kind of client applications to launch
# - 'pulsarProducer':
#       launch a Pulsar client producer that reads data from a data source (either a C* table or a CSV file)
#       and publishes corresponding messages into a Pulsar topic which is as the Model processing input in
#       the next step
# - 'kaskadaProcessor':
#       launch a Kaskada client application that does some magic of processing the input data and then writes
#       the data back into a result Pulsar topic
#
valid_workload_types = ['producer', 'processor', 'consumer']

##
# Parsing the command line input parameters
arg_parser = argparse.ArgumentParser()
arg_parser.add_argument("-cf", "--cfile", 
                        help="Main configuration file", 
                        default="../conf/main-cfg.properties")
arg_parser.add_argument("-wt", "--workloadType", 
                        help="Valid workload type. Must be one of \"{}\""
                        .format(', '.join(valid_workload_types)), 
                        default="producer")
# Only relevant when '--workloadType' is 'pulsarProducer'
arg_parser.add_argument("-ldn", "--loadRecNum", 
                        default=-1,
                        help="""The number of the records to be loaded from the source into a Pulsar topic.
                             Only relevant when workload type is 'producer'""")

args = arg_parser.parse_args()


def create_kaskada_pulsar_source(clnt_conn_configs,
                                 tenant,
                                 namespace,
                                 core_topic):
    broker_svc_url = clnt_conn_configs.get('brokerServiceUrl').data.strip()
    web_svc_url = clnt_conn_configs.get('webServiceUrl').data.strip()
    auth_plugin = clnt_conn_configs.get('authPlugin').data.strip()
    auth_params = clnt_conn_configs.get('authParams').data.strip()

    return kt.PulsarTableSource(broker_svc_url,
                                web_svc_url,
                                auth_plugin,
                                auth_params,
                                tenant,
                                namespace,
                                core_topic)


def create_kaskada_pulsar_materialization_dest(clnt_conn_configs,
                                               mat_name,
                                               mat_query,
                                               mat_recreate):
    mat_exists = False

    try:
        var = materialization.get_materialization(mat_name)
        if mat_recreate.lower() == 'true':
            materialization.delete_materialization(mat_name)
        else:
            mat_exists = True
    except Exception:
        # an error trying to get the table. creating table.
        # Create the table named transworkload_types with the time and name column
        print(
            '[WARN] Unable to get materialization "{}". Creating one with the following info -- query:"{}"'
            .format(mat_name, mat_query))

    if not mat_exists:
        broker_svc_url = clnt_conn_configs.get('brokerServiceUrl').data.strip()
        web_svc_url = clnt_conn_configs.get('webServiceUrl').data.strip()
        auth_plugin = clnt_conn_configs.get('authPlugin').data.strip()
        auth_params = clnt_conn_configs.get('authParams').data.strip()

        destination = km.PulsarDestination(
            pulsar_tenant,
            pulsar_namespace,
            pulsar_topic_output,
            broker_svc_url,
            web_svc_url,
            auth_plugin,
            auth_params)

        km.create_materialization(
            name="mat-" + pulsar_topic_output,
            destination=destination,
            expression=mat_query,
            views=[]
        )


def create_kaskada_table(tbl_name, time_column, entity_column, tbl_recreate, tbl_source):
    tbl_exists = False

    try:
        var = kt.get_table(kaskada_input_tbl_name).table
        if tbl_recreate.lower() == 'true':
            kt.delete_table(tbl_name, force=True)
        else:
            tbl_exists = True
    except Exception:
        # an error trying to get the table. creating table.
        # Create the table named transworkload_types with the time and name column
        print(
            '[WARN] Unable to get table "{}". Creating with the following info -- time_col:"{}", entity_col:"{}", '
            'source:"{}"...'
            .format(tbl_name, time_column, entity_column, tbl_source))

    if not tbl_exists:
        kt.create_table(table_name=tbl_name,
                        time_column_name=time_column,
                        entity_key_column_name=entity_column,
                        source=tbl_source)


# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    main_cfg_file = args.cfile
    if not os.path.isfile(main_cfg_file):
        print("The specified main configuration file is not valid")
        sys.exit()
    else:
        main_cfg_props = load_prop_into_dict(main_cfg_file)

        workload_type = args.workloadType
        if not (workload_type in valid_workload_types):
            print("""The specified workload_type \"{}\" is not valid.
                  Must be one of the following types: \"{}\""""
                  .format(workload_type, ', '.join(valid_workload_types)))
            sys.exit()

        cass_keyspace_name = main_cfg_props.get('ad.keyspace').data
        cass_input_tbl_name = main_cfg_props.get('ad.table.input').data
        cass_output_tbl_name = main_cfg_props.get('ad.table.output').data
        pulsar_tenant = main_cfg_props.get('as.tenant').data.strip()
        pulsar_namespace = main_cfg_props.get('as.namespace').data.strip()
        pulsar_topic_input = main_cfg_props.get('as.topic.input').data.strip()
        pulsar_topic_output = main_cfg_props.get('as.topic.output').data.strip()

        pulsar_clnt_conn_file = main_cfg_props.get('as.client.conf').data.strip()
        if not os.path.isfile(pulsar_clnt_conn_file):
            print("The specified Pulsar client connection file \"{}\" is not valid"
                  .format(pulsar_clnt_conn_file))
            sys.exit()

        pulsar_clnt_conn_props = load_prop_into_dict(pulsar_clnt_conn_file)

        if workload_type in ['producer', 'consumer']:
            cassandra_session = connect_to_astra_db_cluster(
                main_cfg_props.get('ad.client.id').data.strip(),
                main_cfg_props.get('ad.client.secret').data.strip(),
                main_cfg_props.get('ad.secure.bundle').data.strip()
            )

        # Pulsar producer that reads from the C* Input Table and produce the data 
        #   as messages into the Pulsar Input Topic
        if workload_type == 'producer':
            max_load_record_num: int = int(args.loadRecNum)
            if not ((max_load_record_num > 0) or (max_load_record_num == -1)):
                print("""The specified '-ldn' input parameter has a invalid value \"{}\". 
                      The valid value must be a positive integer or -1 (which means all records)"""
                      .format(max_load_record_num))
                sys.exit()

            producer_record_loaded = 0
            pulsar_client = connect_to_as_tenant(pulsar_clnt_conn_props)
            input_topic_schema = AvroSchema(EShopInput)
            # print("Schema info is: " + input_topic_schema.schema_info().schema())
            producer = pulsar_client.create_producer('persistent://{}/{}/{}'.format(pulsar_tenant,
                                                                                    pulsar_namespace,
                                                                                    pulsar_topic_input),
                                                     schema=input_topic_schema)

            # Pulsar producer reads data from a C* table
            cql_query_stmt = "SELECT {} FROM {}.{};".format(','.join(raw_cass_tbl_col_name_list),
                                                            cass_keyspace_name,
                                                            cass_input_tbl_name)
            rows = cassandra_session.execute(cql_query_stmt)

            # If there are returned results from reading the C* table,
            #   start a Pulsar producer and publish
            if rows:
                for row in rows:
                    if (max_load_record_num == -1) or (producer_record_loaded < max_load_record_num):
                        msgpayload = EShopInput(
                            year=row.year,
                            month=row.month,
                            day=row.day,
                            order_seq=row.order_seq,
                            country=row.country,
                            session=row.session,
                            category=row.category,
                            model=row.model,
                            color=row.color,
                            location=row.location,
                            photography=row.photography,
                            price=row.price,
                            price_ind=row.price_ind,
                            page=row.page,
                            event_time=dt2epoch(row.event_time),
                            so_key=row.so_key
                        )
                        producer.send(msgpayload)
                    else:
                        break

        # Kaskada real-time data processing
        elif workload_type == 'processor':
            kaskada_deployment = main_cfg_props.get('kaskada.deployment').data.strip()
            if not (kaskada_deployment == 'local' or kaskada_deployment == 'k8s'):
                print("Kaskada deployment mode must be either 'local' or 'k8s'!")
                sys.exit()

            kcluster_endpoint = main_cfg_props.get('kaskada.endpoint').data.strip()
            if kcluster_endpoint is None:
                print("Must provide a valid Kaskada service endpoint!")
                sys.exit()

            kaskada_session = RemoteBuilder(endpoint=kcluster_endpoint,
                                            is_secure=False).build()

            # whether to recreate the Kaskada table
            recreate_table = main_cfg_props.get('kaskada.table.recreate').data.strip()

            # Create a Kaskada table that is backed by the Pulsar Input Topic
            kaskada_input_tbl_name = 'kasTblInput'
            kaskada_input_tbl_source = create_kaskada_pulsar_source(pulsar_clnt_conn_props,
                                                                    pulsar_tenant,
                                                                    pulsar_namespace,
                                                                    pulsar_topic_input)
            create_kaskada_table(kaskada_input_tbl_name, 'event_time', 'so_key',
                                 recreate_table,
                                 kaskada_input_tbl_source)

            # kaskada_output_tbl_name = 'kasTblOutput'
            # kaskada_output_tbl_source = create_kaskada_pulsar_source(pulsar_clnt_conn_props,
            #                                                          pulsar_tenant,
            #                                                          pulsar_namespace,
            #                                                          pulsar_topic_output)
            # create_kaskada_table(kaskada_output_tbl_name, 'process_time', 'so_key',
            #                      recreate_table,
            #                      kaskada_output_tbl_source)

            ##
            # Create a Kaskada materialization that is backed by the Pulsar Output Topic

            materialization_name = "mat-" + pulsar_topic_output
                materialization_query = """
                {{
                    year: {input_tbl}.year,
                    month: {input_tbl}.month,
                    day: {input_tbl}.day,
                    order_seq: {input_tbl}.order_seq,
                    session: {input_tbl}.session,
                    category: {input_tbl}.category,
                    model: {input_tbl}.model,
                    price: {input_tbl}.price,
                    price_ind: {input_tbl}.price_ind,
                    event_time: {input_tbl}.event_time,
                    so_key: {input_tbl}.so_key,
                }}
                """.format(input_tbl=kaskada_input_tbl_name)

            # whether to recreate the Kaskada table
            recreate_materialization = main_cfg_props.get('kaskada.materialization.recreate').data.strip()

            create_kaskada_pulsar_materialization_dest(pulsar_clnt_conn_props,
                                                       materialization_name,
                                                       materialization_query,
                                                       recreate_materialization)

            print("Done ...")

        # Pulsar producer that reads from the C* Input Table and produce the data
        #   as messages into the Pulsar Input Topic
        elif workload_type == 'consumer':
            print("Consumer ...")

        # Invalid 'workload_type'
        else:
            print("Invalid \"-wt\" parameter value; must be must be one of the following values: \"{}\""
                  .format(', '.join(valid_workload_types)))
            sys.exit()
