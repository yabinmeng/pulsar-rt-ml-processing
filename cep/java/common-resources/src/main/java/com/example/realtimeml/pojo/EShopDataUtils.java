package com.example.realtimeml.pojo;

import java.util.regex.Pattern;

public class EShopDataUtils {
    public static EShopInputData csvToPojo(String csvLine) {
        String csvLineNoQuote = csvLine.replaceAll("\"", "");
        Pattern pattern = Pattern.compile(",");
        String[] fields = pattern.split(csvLineNoQuote);

        return EShopInputData.builder()
                .year(Integer.parseInt(fields[0]))
                .month(Integer.parseInt(fields[1]))
                .day(Integer.parseInt((fields[2])))
                .order(Integer.parseInt((fields[3])))
                .country(Integer.parseInt((fields[4])))
                .session(Integer.parseInt((fields[5])))
                .category(Integer.parseInt((fields[6])))
                .model((fields[7]))
                .color(Integer.parseInt((fields[8])))
                .location(Integer.parseInt((fields[9])))
                .modelPhoto(Integer.parseInt((fields[10])))
                .price(Integer.parseInt((fields[11])))
                .priceInd(Integer.parseInt((fields[12])))
                .page(Integer.parseInt((fields[13])))
                .eventTime(Long.parseLong(fields[14]))
                .build();
    }
}
