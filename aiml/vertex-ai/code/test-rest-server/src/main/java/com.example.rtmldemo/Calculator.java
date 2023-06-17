package com.example.rtmldemo;

import lombok.*;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

@Builder
@Data
@AllArgsConstructor
@ToString
public class Calculator {
    private final long id;
    private final double[] input_values;

    @Getter(lazy = true)
    private final double average = calculateAvg();

    private double calculateAvg() {
        Double[] doubleObjArray = ArrayUtils.toObject(input_values);
        return Arrays.stream(doubleObjArray)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);
    }
}
