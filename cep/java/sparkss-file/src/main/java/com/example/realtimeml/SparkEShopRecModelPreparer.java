package com.example.realtimeml;


import com.example.realtimeml.exception.HelpExitException;
import com.example.realtimeml.exception.InvalidParamException;
import com.example.realtimeml.exception.UnexpectedRuntimException;
import com.example.realtimeml.pojo.EShopInputData;
import com.example.realtimeml.pojo.EShopRecModelDataSpark;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.*;
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.*;

public class SparkEShopRecModelPreparer extends SparkDemoCmdApp {

    private final static String APP_NAME = "EShopRecModelPreparer";

    // Must be set before initializing the "logger" object.
    static { System.setProperty("log_file_base_name", getLogFileName(API_TYPE, APP_NAME)); }

    private final static Logger logger = LoggerFactory.getLogger(SparkEShopRecModelPreparer.class);

    private static SparkSession sparkSession;
    private StreamingQuery streamingQuery;
    private final static StructType eshopDataSchema = DataTypes.createStructType(
        new StructField[] {
            DataTypes.createStructField("year",  DataTypes.IntegerType, false),
            DataTypes.createStructField("month",  DataTypes.IntegerType, false),
            DataTypes.createStructField("day",  DataTypes.IntegerType, false),
            DataTypes.createStructField("order",  DataTypes.IntegerType, false),
            DataTypes.createStructField("country",  DataTypes.IntegerType, false),
            DataTypes.createStructField("session",  DataTypes.IntegerType, false),
            DataTypes.createStructField("category",  DataTypes.IntegerType, false),
            DataTypes.createStructField("model",  DataTypes.StringType, false),
            DataTypes.createStructField("color",  DataTypes.IntegerType, false),
            DataTypes.createStructField("location",  DataTypes.IntegerType, false),
            DataTypes.createStructField("modelPhoto",  DataTypes.IntegerType, false),
            DataTypes.createStructField("price",  DataTypes.IntegerType, false),
            DataTypes.createStructField("priceInd",  DataTypes.IntegerType, false),
            DataTypes.createStructField("page",  DataTypes.IntegerType, false),
            DataTypes.createStructField("eventTime",  DataTypes.LongType, false)}
    );

    public static void main(String[] args) {
        SparkEShopRecModelPreparer workshopApp = new SparkEShopRecModelPreparer(args);
        int exitCode = workshopApp.run();
        System.exit(exitCode);
    }

    public SparkEShopRecModelPreparer(String[] inputParams) {
        super(APP_NAME, inputParams);
        // No new CLI parameters.
    }

    public void processExtendedInputParams() throws HelpExitException, InvalidParamException {
        super.processExtendedInputParams();
        // No new CLI parameters.
    }

    public void runApp() {
        if (sparkSession == null) {
            sparkSession = SparkSession.builder()
                    .appName(APP_NAME)
                    .getOrCreate();
            sparkSession.sparkContext().setLogLevel("ERROR");
        }

        Map<String, String> inputOptionsMap = new HashMap<>();
        inputOptionsMap.put("inferSchema", "false");
        inputOptionsMap.put("delimiter", ",");
        inputOptionsMap.put("header", "true");

        Dataset<Row> rawCsvDf;
        if (streamingMode) {
            rawCsvDf = sparkSession.readStream()
                    .options(inputOptionsMap)
                    .schema(eshopDataSchema)
                    .csv(eshopRawDataCsvFileDir);
        }
        else {
            rawCsvDf = sparkSession.read()
                    .options(inputOptionsMap)
                    .schema(eshopDataSchema)
                    .csv(eshopRawDataCsvFileDir);
        }

        Dataset<EShopInputData> eshopRawDf =
                rawCsvDf.as(ExpressionEncoder.javaBean(EShopInputData.class));
        //eshopRawDf.printSchema();

        Dataset<EShopRecModelDataSpark> recModelDf = eshopRawDf.withColumn("eventTimeTs", to_timestamp(col("eventTime")))
                .withColumn("metaData",
                        concat(
                                lit("{ "),
                                concat_ws(
                                        ", ",
                                        concat(lit("\"category\":\""), col("category"), lit("\"")),
                                        concat(lit("\"clothingModel\":\""), col("model"), lit("\"")),
                                        concat(lit("\"color\":\""), col("color"), lit("\""))
                                ),
                                lit(" }")
                        )
                )
                .drop("year")
                .drop("month")
                .drop("day")
                .drop("country")
                .drop("category")
                .drop("country")
                .drop("model")
                .drop("color")
                .drop("location")
                .drop("modelPhoto")
                .drop("price")
                .drop("priceInd")
                .drop("page")
                .drop("eventTime")
                .map((MapFunction<Row, EShopRecModelDataSpark>) row -> new EShopRecModelDataSpark(
                        row.getAs("order"),
                        row.getAs("session"),
                        row.getAs("eventTimeTs"),
                        row.getAs("metaData")),
                    ExpressionEncoder.javaBean(EShopRecModelDataSpark.class)
                );
        //recModelDf.printSchema();

        if (streamingMode) {
            try {
                Map<String, String> outputOptionsMap = new HashMap<>();
                outputOptionsMap.put("truncate", "false");
                outputOptionsMap.put("numRows", String.valueOf(recordNum));

                streamingQuery = recModelDf
                        .withWatermark("eventTimeTs", "1 seconds")
                        .groupBy(
                            col("session"),
                            // A Tumbling window with 5 second size
                            functions.window(col("eventTimeTs"), "10 seconds"))
                        .agg(collect_set("metaData").as("metaDataList"))
                        //.orderBy(col("session"))
                        .writeStream()
                        .options(outputOptionsMap)
                        .outputMode("complete")
                        .format("console")
                        .start();

                try {
                    streamingQuery.awaitTermination();
                } catch (StreamingQueryException e) {
                    e.printStackTrace();
                    throw new UnexpectedRuntimException("Unexpected error when terminating the streaming query!");
                }

            } catch (TimeoutException e) {
                e.printStackTrace();
                throw new UnexpectedRuntimException("Unexpected error detected and failed with executing the streaming query!");
            }
        }
        else {
            // for testing purpose

            // Non-time-based window - for the purpose of
            //   get the last N record within each group
            // --------------------------------------------
            // NOTE: this can't be used in the previous block.
            //       otherwise, get this error: Non-time-based windows are not supported on streaming DataFrames/Datasets;
            WindowSpec windowSpec = Window
                    .partitionBy(col("session"))
                    .orderBy(col("eventTimeTs").desc());

            recModelDf
                    .withColumn("row_number", row_number().over(windowSpec).alias("row_number"))
                    .where(col("row_number").$less$eq(clickNum))
                    .drop("row_number")
                    .groupBy("session")
                    .agg(collect_set("metaData").as("metaDataList"))
                    .show(recordNum,false);
        }
    }
    public void termApp() {
//        if (streamingQuery != null) {
//            assert (streamingMode);
//            try {
//                while (streamingQuery.isActive()) {
//                    StreamingQueryStatus queryStatus = streamingQuery.status();
//                    String statusMsgStr = queryStatus.message();
//
//                    if (!queryStatus.isDataAvailable()
//                            && !queryStatus.isTriggerActive()
//                            && !StringUtils.equalsIgnoreCase(statusMsgStr, "Initializing sources")) {
//                        streamingQuery.stop();
//                    }
//                }
//                streamingQuery.awaitTermination(5000);
//
//            } catch (TimeoutException | StreamingQueryException e) {
//                e.printStackTrace();
//                throw new UnexpectedRuntimException("Unexpected error when terminating the streaming query!");
//            }
//        }

        if (sparkSession != null) {
            sparkSession.close();
        }
    }
}
