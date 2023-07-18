package com.example.realtimeml.pojo;

import lombok.*;

import java.sql.Timestamp;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class EShopRecModelDataSpark {
    // sequence of clicks during one session
    @EqualsAndHashCode.Include
    private int order;
    // session ID
    @EqualsAndHashCode.Include
    private int session;
    // timestamp
    private Timestamp eventTimeTs;
    // combined metadata
    private String metaData;
}
