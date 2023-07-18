package com.example.realtimeml;

import com.example.realtimeml.exception.InvalidParamException;
import com.example.realtimeml.exception.UnexpectedRuntimException;
import com.example.realtimeml.util.CsvFileLineScanner;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class EShopRawDataEnricher extends EShopRecCmdApp {

    private final static String APP_NAME = "EShopRawDataEnricher";
    static { System.setProperty("log_file_base_name", getLogFileName(API_TYPE, APP_NAME)); }

    private final static Logger logger = LoggerFactory.getLogger(EShopRawDataEnricher.class);

    private File eshopRawDataCsvFile;
    private File enrichedTgtDirFile;
    private boolean crtSessionOrderKey;
    private boolean noHeader;

    public EShopRawDataEnricher(String appName, String[] inputParams) {
        super(appName, inputParams);
        addRequiredCommandLineOption("fp","filePath", true,
                "EShop raw data CSV file full path.");
        addOptionalCommandLineOption("td","targetDir", true,
                "Target directory for the enriched eShop data CSV file.");
        addOptionalCommandLineOption("sok","sessionOrderKey", true,
                "Whether to generate a \"session-order\" key column.");
        addOptionalCommandLineOption("nh","noHeader", true,
                "Whether to generate header in the output file");

        logger.info("Starting application: \"" + appName + "\" ...");
    }

    public static void main(String[] args) throws IOException, ParseException {
        EShopRecCmdApp workshopApp = new EShopRawDataEnricher(APP_NAME, args);
        int exitCode = workshopApp.run();
        System.exit(exitCode);
    }

    @Override
    public void processExtendedInputParams() throws InvalidParamException {
        // (Required) Full path of the eShop raw data csv file
        String rawDataCsvFileFullPath = processStringInputParam("fp");
        if ( StringUtils.isBlank(rawDataCsvFileFullPath) ) {
            throw new InvalidParamException("The \"-fp\" parameter can't be empty!");
        }
        eshopRawDataCsvFile = processFileInputParam("fp");
        if ((eshopRawDataCsvFile==null) || !eshopRawDataCsvFile.isFile()) {
            throw new InvalidParamException("The provided \"-fp\" parameter value must be a valid file!");
        }

        // (Optional) The directory to host the target enriched file
        String rawFileName = StringUtils.substringAfterLast(rawDataCsvFileFullPath, "/");
        String rawFileNameNoType = StringUtils.substringBeforeLast(rawFileName, ".");
        String rawFileType = StringUtils.substringAfterLast(rawFileName, ".");

        // The default target directory is the same as the source file
        String dftTargetDir = StringUtils.substringBeforeLast(rawDataCsvFileFullPath, "/");
        String enrichedTgtDirName = processStringInputParam("td");
        if (StringUtils.isBlank(enrichedTgtDirName)) {
            enrichedTgtDirName = dftTargetDir;
        }

        if (noHeader)
            enrichedTgtDirFile = new File(
                    enrichedTgtDirName + "/" + rawFileNameNoType + "_enriched_no_header." + rawFileType );
        else
            enrichedTgtDirFile = new File(
                    enrichedTgtDirName + "/" + rawFileNameNoType + "_enriched_header." + rawFileType );

        // (Optional) Whether to create a "session-order" column as the key column. Default: false
        crtSessionOrderKey = processBooleanInputParam("sok", false);

        // (Optional) Whether to generate header in the target file. Default: true
        noHeader = processBooleanInputParam("nh", false);
    }

    @Override
    public void runApp() {
        try {
            // Delete existing target file
            FileUtils.deleteQuietly(enrichedTgtDirFile);

            boolean isTitleLine = true;
            String lineForOutput = "";

            CsvFileLineScanner csvFileLineScanner = new CsvFileLineScanner(eshopRawDataCsvFile);

            boolean lineDateStrChg = false;
            String lastLineDateStr = "";
            int secondToAdd = 0;
            String[] lineFields;
            Date lineDate = null;
            Date lineDateWithSeconds;

            int recordPerGrp = 0;
            int totalRecords = 0;

            while (csvFileLineScanner.hasNextLine()) {
                String csvLine = csvFileLineScanner.getNextLine();
                lineFields = StringUtils.split(csvLine, ",");
                String lineDateStr = lineFields[0] + "-" + lineFields[1] + "-" + lineFields[2];

                if (!isTitleLine) {
                    DateFormat df =  new SimpleDateFormat("yyyy-MM-dd");
                    df.setTimeZone(TimeZone.getTimeZone("UTC"));
                    lineDate = df.parse(lineDateStr);

                    lineDateStrChg = !StringUtils.equals(lastLineDateStr, lineDateStr);
                    if (lineDateStrChg) {
                        secondToAdd = 0;

                        if (StringUtils.isNotBlank(lastLineDateStr)) {
                            logger.info("Total {} records enriched for group with date {}",
                                    recordPerGrp, lineDate);
                        }
                        recordPerGrp = 0;
                    }

                    lineDateWithSeconds = DateUtils.addSeconds(lineDate, secondToAdd);
                    lineForOutput = csvLine + "," + lineDateWithSeconds.getTime();

                    if (crtSessionOrderKey) {
                        int sessionOrderKey = Integer.parseInt(lineFields[5]) * 1000 +
                                Integer.parseInt(lineFields[3]);
                        lineForOutput = lineForOutput + "," + sessionOrderKey;
                    }

                    secondToAdd++;
                    lastLineDateStr = lineDateStr;

                    recordPerGrp++;
                    totalRecords++;

                    FileUtils.writeStringToFile(
                            enrichedTgtDirFile,
                            lineForOutput + "\n",
                            Charset.defaultCharset(),
                            true);
                }
                // No header in the target file
                else {
                    isTitleLine = false;

                    if (!noHeader) {
                        if (crtSessionOrderKey) {
                            lineForOutput = csvLine + ",event_time,so_key";
                        }
                        else {
                            lineForOutput = csvLine + ",event_time";
                        }
                        FileUtils.writeStringToFile(
                                enrichedTgtDirFile,
                                lineForOutput + "\n",
                                Charset.defaultCharset(),
                                true);
                    }
                }
            }

            if (lineDate != null) {
                logger.info("{} records enriched for group with date {}", recordPerGrp, lineDate);
            }

            logger.info("Total {} records processed", totalRecords);
        }
        catch (IOException | ParseException e) {
            throw new UnexpectedRuntimException("Unexpected error when enriching the EShop data csv file!");
        }
    }

    @Override
    public void termApp() {
        logger.info("Terminating application: \"" + appName + "\" ...");
    }
}
