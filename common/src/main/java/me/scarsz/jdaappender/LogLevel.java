package me.scarsz.jdaappender;

import lombok.Getter;

import java.util.Arrays;

public enum LogLevel {

    DEBUG("#"),
    INFO(" "),
    WARN("!"),
    ERROR("-");

    public static final int MAX_NAME_LENGTH = Arrays.stream(values()).map(Enum::name).mapToInt(String::length).max().orElse(5);

    @Getter private final String levelSymbol;

    LogLevel(String levelSymbol) {
        this.levelSymbol = levelSymbol;
    }

}
