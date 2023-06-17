package com.example.rtmldemo;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DsrsController{
    private final AtomicLong counter = new AtomicLong();
    @GetMapping("/avg/{paramValue}")
    public Calculator greeting(@PathVariable double[] paramValue) {
        return new Calculator(counter.incrementAndGet(), paramValue);
    }
}
