package com.example.realtimeml.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;

public class CsvFileLineScanner {

    private File csvFile;

    private LineIterator lineIterator;

    public CsvFileLineScanner(File file) throws IOException  {
        this.csvFile = file;
        this.lineIterator = FileUtils.lineIterator(csvFile, "UTF-8");
    }

    public boolean hasNextLine() {
        return lineIterator.hasNext();
    }

    public String getNextLine() {
        return lineIterator.nextLine();
    }

    public void close() throws IOException {
        if (lineIterator != null) {
            lineIterator.close();
        }
    }

}
