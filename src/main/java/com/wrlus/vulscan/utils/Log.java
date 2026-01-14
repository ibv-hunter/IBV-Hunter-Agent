package com.wrlus.vulscan.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Log {
    private static final Logger logger = LoggerFactory.getLogger(Log.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static void e(String message) {
        logger.error("{}\t{}", getCurrentTime(), message);
    }
    
    public static void w(String message) {
        logger.warn("{}\t{}", getCurrentTime(), message);
    }
    
    public static void i(String message) {
        logger.info("{}\t{}", getCurrentTime(), message);
    }

    private static String getCurrentTime() {
        return LocalDateTime.now().format(formatter);
    }
}
