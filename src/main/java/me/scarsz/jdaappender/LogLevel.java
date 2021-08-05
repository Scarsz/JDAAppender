package me.scarsz.jdaappender;

import lombok.Getter;

public enum LogLevel {

    INFO(" "),
    WARN("!"),
    ERROR("-");

    @Getter private final String levelSymbol;

    LogLevel(String levelSymbol) {
        this.levelSymbol = levelSymbol;
    }

}
