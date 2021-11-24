package me.scarsz.jdaappender;

import lombok.Getter;

public enum LogLevel {

    DEBUG("#"),
    INFO(" "),
    WARN("!"),
    ERROR("-");

    @Getter private final String levelSymbol;

    LogLevel(String levelSymbol) {
        this.levelSymbol = levelSymbol;
    }

}
