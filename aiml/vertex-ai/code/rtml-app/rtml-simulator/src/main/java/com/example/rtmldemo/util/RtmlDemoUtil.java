package com.example.rtmldemo.util;

import com.example.rtmldemo.exception.InvalidParamException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.io.IOException;

public class RtmlDemoUtil {

    public static File processFileInputParam(CommandLine commandLine, Options cliOptions, String optionName) {
        Option option = cliOptions.getOption(optionName);

        File file = null;

        if (option.isRequired()) {
            String path = commandLine.getOptionValue(option.getOpt());
            try {
                file = new File(path);
                file.getCanonicalPath();
            } catch (IOException ex) {
                throw new InvalidParamException("Invalid file path for param '" + optionName + "': " + path);
            }
        }

        return file;
    }

    public static int processIntegerInputParam(CommandLine commandLine, Options cliOptions, String optionName, int dftValue) {
        Option option = cliOptions.getOption(optionName);

        // Default value if not present on command line
        int intVal = dftValue;
        String value = commandLine.getOptionValue(option.getOpt());

        if (option.isRequired()) {
            if (StringUtils.isBlank(value)) {
                throw new InvalidParamException("Empty value for argument '" + optionName + "'");
            }
        }

        if (StringUtils.isNotBlank(value)) {
            intVal = NumberUtils.toInt(value);
        }

        return intVal;
    }

    public static String processStringInputParam(CommandLine commandLine, Options cliOptions, String optionName) {
        return processStringInputParam(commandLine, cliOptions, optionName, null);
    }
    public static String processStringInputParam(CommandLine commandLine, Options cliOptions, String optionName, String dftValue) {
        Option option = cliOptions.getOption(optionName);

        String strVal = dftValue;
        String value = commandLine.getOptionValue(option);

        if (option.isRequired()) {
            if (StringUtils.isBlank(value)) {
                throw new InvalidParamException("Empty value for argument '" + optionName + "'");
            }
        }

        if (StringUtils.isNotBlank(value)) {
            strVal = value;
        }

        return strVal;
    }
}
