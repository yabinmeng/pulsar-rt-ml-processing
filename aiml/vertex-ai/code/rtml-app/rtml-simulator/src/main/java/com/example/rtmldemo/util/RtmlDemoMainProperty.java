package com.example.rtmldemo.util;

import com.example.rtmldemo.exception.InvalidParamException;
import com.example.rtmldemo.exception.RtmlDemoRuntimeException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RtmlDemoMainProperty {
    private final Map<String, Object> cfgConfMap = new HashMap<>();

    public RtmlDemoMainProperty(File cfgPropFile) throws RtmlDemoRuntimeException {
        String canonicalFilePath = "";

        try {
            canonicalFilePath = cfgPropFile.getCanonicalPath();

            Parameters params = new Parameters();

            FileBasedConfigurationBuilder<FileBasedConfiguration> builder =
                    new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
                            .configure(params.properties()
                                    .setFileName(canonicalFilePath));

            Configuration config = builder.getConfiguration();

            for (Iterator<String> it = config.getKeys(); it.hasNext(); ) {
                String confKey = it.next();
                String confVal = config.getProperty(confKey).toString();

                if (!StringUtils.isBlank(confVal)) {
                    if (StringUtils.equalsIgnoreCase(confKey, "clientConf")) {
                        File clientConnFile;
                        try {
                            clientConnFile = new File(confVal);
                            clientConnFile.getCanonicalPath();
                        } catch (IOException ex) {
                            throw new InvalidParamException(
                                    "Invalid file path for 'clientConf' config parameter : " + confVal);
                        }
                        ClientConnConf clientConnConf = new ClientConnConf(clientConnFile);
                        cfgConfMap.put(confKey, clientConnConf);
                    }
                    else if (StringUtils.equalsAnyIgnoreCase(confKey, "useDsrsService", "useAstraStreaming")) {
                        cfgConfMap.put(confKey, BooleanUtils.toBoolean(confVal));
                    }
                    else if (StringUtils.equalsAnyIgnoreCase(confKey, "inputTopic", "outputTopic")) {
                        if (StringUtils.isBlank(confVal)) {
                            throw new InvalidParamException(
                                    "An non-empty value is expected for '" + confKey + "' config parameter!");
                        }
                        cfgConfMap.put(confKey, confVal);
                    }
                }
            }
        } catch (IOException ioe) {
            throw new RtmlDemoRuntimeException("Can't read the specified properties file!");
        } catch (ConfigurationException cex) {
            throw new RtmlDemoRuntimeException(
                    "Error loading configuration items from the specified properties file: " + canonicalFilePath);
        }
    }

    public String toString() {
        return new ToStringBuilder(this).
                append("clientConfMap", cfgConfMap.toString()).
                toString();
    }

    public ClientConnConf getClientConnConf() {
        return (ClientConnConf) cfgConfMap.get("clientConf");
    }

    public String getInputTopic() {
        return (String) cfgConfMap.get("inputTopic");
    }

    public String getOutputTopic() {
        return (String) cfgConfMap.get("outputTopic");
    }

    public boolean getUseDsrsService() {
        if (cfgConfMap.containsKey("useDsrsService"))
            return (Boolean) cfgConfMap.get("useDsrsService");
        else
            return false;
    }

    public boolean getUseAstraStreaming() {
        if (cfgConfMap.containsKey("useAstraStreaming"))
            return (Boolean) cfgConfMap.get("useAstraStreaming");
        else
            return false;
    }
}
