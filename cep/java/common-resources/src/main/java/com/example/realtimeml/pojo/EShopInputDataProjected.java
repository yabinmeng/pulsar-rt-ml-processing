package com.example.realtimeml.pojo;

import lombok.*;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class EShopInputDataProjected {
    // session ID
    @EqualsAndHashCode.Include
    private int session;
    // sequence of clicks during one session
    @EqualsAndHashCode.Include
    private int order;
    // main product category, see "category.txt"
    private int category;
    // clothing model (contains information about the code for each product)
    private String model;
    // product color, see "color_code.txt"
    private int color;
    // timestamp (event time)
    private long eventTime;
}
