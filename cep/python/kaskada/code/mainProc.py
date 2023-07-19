import argparse
import logging
import os.path
import sys

from _utils import *

from kaskada.api.session import LocalBuilder
from kaskada.api.remote_session import RemoteBuilder
import kaskada.table as kt
import kaskada.query as kq

logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.DEBUG)

##
# What kind of client applications to launch
# - 'inputProducer':
#       launch a Pulsar client producer that reads data from a data source (either a C* table or a CSV file)
#       and publishes corresponding messages into a Pulsar topic which is as the Model processing input in
#       the next step
# - 'mlProcessor':
#       launch a Kaskada client application that does some magic of processing the input data and then writes
#       the data back into a result Pulsar topic
# - 'modelConsumer':
#       launch a Pulsar client consumer that reads data from the Pulsar topic that contains the real time
#       processing result data
#
valid_client_types = ['inputProducer', 'mlProcessor', 'modelConsumer']

##
# When the launched client type is 'inputProducer' (a Pulsar producer), this variable specifies the valid
# data sources from which the producer application reads data from:
# - 'cass_table': read from a C* table (default)
# - 'csv_file'  : read from a local CSV file
#
valid_producer_data_srcs = ['cass_table', 'csv_file']

##
# When the launched client type is 'mlProcessor' (a Kaskada real-time processor), this variable specifies the
# valid sources from which the Kaskada engine gets the input data:
# - 'pulsar_topic': read input from a Pulsar topic (default)
# - 'csv_file'    : read from a local CSV file
#
valid_kaskada_source_types = ['pulsar_topic', 'csv_file']

##
# When the launched client type is 'mlProcessor' (a Kaskada real-time processor) and it needs to read the input
# from a CSV file, this variable specifies the location of the CSV file:
# - 'gs' : the CSV file is in a GCS bucket
# - 's3' : the CSV file is in a S3 bucket
# - 'file: the CSV file is a local file
#
# NOTE: when deploying Kaskada in a K8s cluster. Only 'gs' or 's3' type is supported.
#       local 'file' type is used when Kaskada is deployed locally
#
valid_kaskada_file_source_types = ['gs', 's3', 'file']

##
# Parsing the command line input parameters
arg_parser = argparse.ArgumentParser()
arg_parser.add_argument(
    "-cf", "--cfile", help="main configuration file", default="../conf/main-cfg.properties")
required_named = arg_parser.add_argument_group('required named arguments')
required_named.add_argument(
    "-ct", "--clientType", help="client type; must be one of (" + ', '.join(valid_client_types) + ")")
# Only relevant when '--clientType' is 'inputProducer'
arg_parser.add_argument(
    "-ps", "--producerSource", default='cass_table',
    help="Pulsar producer data source types; must be one of (" + ', '.join(valid_producer_data_srcs) + ")")
arg_parser.add_argument(
    "-ldn", "--loadRecNum", default=100,
    help="The number of the records to be loaded from the source into a Pulsar topic "
         "(-1 means to load all available records). The source can be either a C* table or a csv file.")
arg_parser.add_argument(
    "-csf", "--csvSrcFile", help="csv source file for the producer (only relevant when '-ct' is 'csv_file'!")
#############
# Only relevant when '--clientType' is 'mlProcessor'
# - Note: when '-kas-src' option is 'csv_file', it must point to a cloud storage location (e.g. S3 or GCS)
#         so that a remote Kaskada service can locate the file successfully
arg_parser.add_argument(
    "-ki", "--kaskadaInput", default='pulsar_topic',
    help="Kaskada data source types; must be one of (" + ', '.join(valid_kaskada_source_types) + ")")
arg_parser.add_argument(
    "-kr", "--kaskadaResult", default='pulsar_topic',
    help="Kaskada data destination types; must be one of (" + ', '.join(valid_kaskada_source_types) + ")")

args = arg_parser.parse_args()


def create_kaskada_pulsar_source(clnt_conn_configs,
                                 tenant,
                                 keyspace,
                                 core_topic):
    broker_svc_url = clnt_conn_configs.get('brokerServiceUrl').data.strip()
    web_svc_url = clnt_conn_configs.get('webServiceUrl').data.strip()
    auth_plugin = clnt_conn_configs.get('authPlugin').data.strip()
    auth_params = clnt_conn_configs.get('authParams').data.strip()

    # Known issue: missing Pulsar Admin service URL, which leads to the error
    #              of failing to get Pulsar topic schema metadata
    # - https://github.com/kaskada-ai/kaskada/pull/408/
    return kt.PulsarTableSource(broker_svc_url,
                                web_svc_url,
                                auth_plugin,
                                auth_params,
                                tenant,
                                keyspace,
                                core_topic)


def create_kaskada_table(tbl_name, time_column, entity_column, tbl_recreate, tbl_source):

    tbl_exists = False

    try:
        kt.get_table(kaskada_input_tbl_name).table
        if (tbl_recreate.lower() == 'true'):
            kt.delete_table(tbl_name, force=True)
        else:
            tbl_exists = True
    except Exception:
        # an error trying to get the table. creating table.
        # Create the table named transactions with the time and name column
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

        action = args.clientType
        if not (action in valid_client_types):
            print("The specified action type (" + action +
                  ") is not valid. Must be one of the following types: " +
                  ', '.join(valid_client_types))
            sys.exit()

        cass_keyspace_name = main_cfg_props.get('ad.keyspace').data
        cass_raw_tbl_name = main_cfg_props.get('ad.table.raw').data
        pulsar_tenant = main_cfg_props.get('as.tenant').data.strip()
        pulsar_namespace = main_cfg_props.get('as.namespace').data.strip()
        pulsar_topic_raw = main_cfg_props.get('as.topic.input').data.strip()
        pulsar_topic_model = main_cfg_props.get('as.topic.output').data.strip()

        pulsar_clnt_conn_file = main_cfg_props.get('as.client.conf').data.strip()
        if not os.path.isfile(pulsar_clnt_conn_file):
            print("The specified Pulsar client connection file (" + pulsar_clnt_conn_file + ") is not valid")
            sys.exit()

        pulsar_clnt_conn_props = load_prop_into_dict(pulsar_clnt_conn_file)

        if action in ['inputProducer', 'modelConsumer']:
            cassandra_session = connect_to_astra_db_cluster(
                main_cfg_props.get('ad.client.id').data.strip(),
                main_cfg_props.get('ad.client.secret').data.strip(),
                main_cfg_props.get('ad.secure.bundle').data.strip()
            )

        # Pulsar producer that reads from the raw data C* table
        #   and produce messages into a raw data Pulsar topic
        if action == 'inputProducer':
            producer_src_type = args.producerSource
            if not (producer_src_type in valid_producer_data_srcs):
                print("The specified producer data source type (" + producer_src_type +
                      ") is not valid. Must be one of the following types: " +
                      ', '.join(valid_producer_data_srcs))
                sys.exit()

            max_load_record_num: int = int(args.oadRecNum)
            if not ((max_load_record_num > 0) or (max_load_record_num == -1)):
                print("The specified 'loadRecNum' parameter has a invalid value ({})!", max_load_record_num)
                print("   the value must be a positive integer or -1 (which means all records)")
                sys.exit()

            producer_record_loaded = 0
            pulsar_client = connect_to_as_tenant(pulsar_clnt_conn_props)
            raw_topic_schema = AvroSchema(EShopInputTopic)
            # print("Schema info is: " + raw_topic_schema.schema_info().schema())
            producer = pulsar_client.create_producer('persistent://{}/{}/{}'.format(pulsar_tenant,
                                                                                    pulsar_namespace,
                                                                                    pulsar_topic_raw),
                                                     schema=raw_topic_schema)

            # Pulsar producer reads data from a C* table
            if (producer_src_type == 'cass_table'):
                # cassandra_session.row_factory = pandas_factory
                # cassandra_session.default_fetch_size = None
                cql_query_stmt = "SELECT {} FROM {}.{};".format(','.join(raw_cass_tbl_col_name_list),
                                                                cass_keyspace_name,
                                                                cass_raw_tbl_name)
                rows = cassandra_session.execute(cql_query_stmt)
                # df = rows._current_rows.sort_values(by=['sokey'], ascending=True)

                # If there are returned results from reading the C* table,
                #   start a Pulsar producer and publish
                if rows:
                    for row in rows:
                        if (max_load_record_num == -1) or (producer_record_loaded < max_load_record_num):
                            msgpayload = EShopInputTopic(
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
                                time=dt2epoch(row.time),
                                so_key=row.so_key
                            )
                            producer.send(msgpayload)
                        else:
                            break
            # Pulsar producer reads the raw source data from a csv file
            else:
                pcsv_file_src = args.csvSrcFile
                if not os.path.isfile(pcsv_file_src):
                    print("The specified data source file (" + pcsv_file_src + ") is not valid")
                    sys.exit()

                with open(pcsv_file_src) as file:
                    record_line = next(file, '').strip()
                    while record_line:
                        if (max_load_record_num == -1) or (producer_record_loaded < max_load_record_num):
                            rawfield_values = record_line.split(",")
                            msgpayload = EShopInputTopic(
                                year=int(rawfield_values[0]),
                                month=int(rawfield_values[1]),
                                day=int(rawfield_values[2]),
                                order_seq=int(rawfield_values[3]),
                                country=int(rawfield_values[4]),
                                session=int(rawfield_values[5]),
                                category=int(rawfield_values[6]),
                                model=rawfield_values[7],
                                color=int(rawfield_values[8]),
                                location=int(rawfield_values[9]),
                                photography=int(rawfield_values[10]),
                                price=int(rawfield_values[11]),
                                price_ind=int(rawfield_values[12]),
                                page=int(rawfield_values[13]),
                                time=int(rawfield_values[14]),
                                so_key=rawfield_values[15]
                            )
                            producer.send(msgpayload)

                            producer_record_loaded = producer_record_loaded + 1
                            record_line = next(file, '').strip()
                        else:
                            break

        # Use Kaskada for AI/ML related (real-time) processing
        elif action == 'mlProcessor':
            kinput_type = args.kaskadaInput
            if not (kinput_type in valid_kaskada_source_types):
                print("The specified Kaskada data source type (" + kinput_type +
                      ") is not valid. Must be one of the following types: " +
                      ', '.join(valid_kaskada_source_types))
                sys.exit()

            kresult_type = args.kaskadaResult
            if not (kresult_type in valid_kaskada_source_types):
                print("The specified Kaskada data destination type (" + kresult_type +
                      ") is not valid. Must be one of the following types: " +
                      ', '.join(valid_kaskada_source_types))
                sys.exit()

            kaskada_deployment = main_cfg_props.get('kaskada.deployment').data.strip()
            if not (kaskada_deployment == 'local' or kaskada_deployment == 'k8s'):
                print("Kaskada deployment mode must be either 'local' or 'k8s'!")
                sys.exit()

            kcluster_endpoint = main_cfg_props.get('kaskada.endpoint').data.strip()
            if kcluster_endpoint is None:
                print("Must provide a Kaskada service endpoint!")
                sys.exit()

            kaskada_session = RemoteBuilder(endpoint=kcluster_endpoint,
                                            is_secure=False).build()

            kaskada_input_tbl_name = 'kasTblInput'
            kaskada_input_tbl_source = None

            kaskada_result_tbl_name = 'kasTblResult'
            kaskada_result_tbl_source = None

            recreate_table = main_cfg_props.get('kaskada.recreate_table').data.strip()

            ######################
            # Create a Kaskada table (source) using a Pulsar topic as the source
            # NOTE 1: this doesn't work at the moment due to the missing 'adminSvcUrl' issue (causing failed to get
            #         the proper Pulsar topic schema metadata)
            #         (https://github.com/kaskada-ai/kaskada/pull/408)
            #
            if kinput_type == 'pulsar_topic':
                # Create a Pulsar source table
                kaskada_input_tbl_source = create_kaskada_pulsar_source(pulsar_clnt_conn_props,
                                                                        pulsar_tenant,
                                                                        pulsar_namespace,
                                                                        pulsar_topic_raw)

                create_kaskada_table(kaskada_input_tbl_name, 'event_time', 'so_key',
                                     recreate_table,
                                     kaskada_input_tbl_source)

            ###
            # NOTE: right now Kaskada k8s deployment has issues (https://github.com/kaskada-ai/kaskada/issues/427)
            #       that prevents successful data loading from a cloud storage based file
            #
            #       Right now, the ONLY feasible way is to read a local file with a local Kaskada deployment
            #
            # Create a Kaskada table (source) using a csv file as the source
            else:
                cfg_ki_src_file = main_cfg_props.get('kaskada.input_file.location').data
                ki_csv_file_type = cfg_ki_src_file.split(":")[0]
                ki_csv_file_name = cfg_ki_src_file[7:]

                if not (ki_csv_file_type in valid_kaskada_file_source_types):
                    print("The specified Kaskada data destination type (" + ki_csv_file_type +
                          ") is not valid. Must be one of the following types: " +
                          ', '.join(valid_kaskada_file_source_types))
                    sys.exit()

                # local source file
                if ki_csv_file_type == "file":
                    pass
                # source file stored in GCS
                elif ki_csv_file_type == "gs":
                    gcp_svc_acct_key_file = main_cfg_props.get('kaskada.gcs.svc_key').data.strip()
                    if not os.path.isfile(gcp_svc_acct_key_file):
                        print("The specified google service account key file is not valid")
                        sys.exit()
                    os.environ['GOOGLE_SERVICE_ACCOUNT_PATH'] = os.path.abspath(gcp_svc_acct_key_file)
                # source file stored in S3
                else:
                    print("TBD ...")

                query = '{ ' + \
                        "time: {}.event_time, " \
                        "entity: {}.so_key, " \
                        "min_amount: {}.session | min()" \
                            .format(kaskada_input_tbl_name, kaskada_input_tbl_name, kaskada_input_tbl_name) + ' }'

                create_kaskada_table(kaskada_input_tbl_name, 'event_time', 'so_key', recreate_table, None)

                try:
                    if ki_csv_file_type == 'file':
                        kt.load(kaskada_input_tbl_name, file=ki_csv_file_name)
                    else:
                        kt.load(kaskada_input_tbl_name, file=ki_csv_file_name)
                except Exception as e:
                    print('Failed loaded data into table "{}" from source "{}")'
                          .format(kaskada_input_tbl_name, cfg_ki_src_file))
                    sys.exit()

            # response_parquet = kq.create_query(expression=query, result_behavior='final_results')

            print("Done ...")

            # ######################
            # # do some "real-time" processing and save (materialize) the results
            # #   into the specified target, either a CSV file or a Pulsar topic
            #
            # # Create a Kaskada table (destination) using a Pulsar topic as the source
            # if kaskada_dest_type == 'pulsar_topic':
            #     # Create a Pulsar source table
            #     kaskada_dest_tbl_source = create_kaskada_pulsar_table(pulsar_clnt_conn_props,
            #                                                          pulsar_tenant,
            #                                                          pulsar_namespace,
            #                                                          pulsar_topic_model)
            # # Create a Kaskada table (destination) using a csv file as the source
            # else:
            #     raw_data_dest_file = main_cfg_props.get('kaskada.dest_file.location').data.strip()
            #
            #     # the source file must be stored in a S3 or GCS bucket
            #     if not (raw_data_dest_file.startswith("gs") or raw_data_dest_file.startswith("s3")):
            #         print("The specified Kaskada data destination file (" + raw_data_dest_file + ") is not valid. "
            #               "It must be stored in either an S3 or a GCS bucket!")
            #         sys.exit()
            #
            #     # source file stored in GCS
            #     if raw_data_dest_file.startswith("gs"):
            #         gcp_svc_acct_key_file = main_cfg_props.get('kaskada.gcs.svc_key').data.strip()
            #         if not os.path.isfile(gcp_svc_acct_key_file):
            #             print("The specified google service account key file is not valid")
            #             sys.exit()
            #         os.environ['GOOGLE_SERVICE_ACCOUNT_PATH'] = gcp_svc_acct_key_file
            #     # source file stored in S3
            #     else:
            #         print("TBD ...")
            #
            # # create a Kaskada table (dest) if it doesn't exist
            # dest_table = create_kaskada_table(kaskada_dest_tbl_name, 'time', 'so_key', kaskada_dest_tbl_source)

        # Pulsar producer that reads from the raw data C* table
        #   and produce messages into a raw data Pulsar topic
        elif action == 'modelConsumer':
            print("TBD ...")
