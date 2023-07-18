package com.example.realtimeml.pojo;

import lombok.*;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class EShopInputData {
    private int year;
    private int month;
    private int day;
    // sequence of clicks during one session
    @EqualsAndHashCode.Include
    private int order;
    // The country of the origin of the IP address , see "country_code.txt"
    private int country;
    // session ID
    @EqualsAndHashCode.Include
    private int session;
    // main product category, see "category.txt"
    private int category;
    // clothing model (contains information about the code for each product)
    private String model;
    // product color, see "color_code.txt"
    private int color;
    // photo location on the page, see "location_code.txt"
    private int location;
    // model photography, see "model_photo_code.txt"
    private int modelPhoto;
    // price (in US dollar)
    private int price;
    // price indicator - whether the price of a particular product is higher
    //                   than the average price for the entire product category
    // see "price_ind_code.txt"
    private int priceInd;
    // page number within the e-store website (1 to 5)
    private int page;
    // event time (expressed in long value as the Unix time)
    private long eventTime;
}
