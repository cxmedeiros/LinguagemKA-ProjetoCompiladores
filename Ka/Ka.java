package com.craftinginterpreters.ka;

class Ka {
    static void error(int line, String message) {
        System.err.println("[line " + line + "] Error: " + message);
        System.exit(1);
    }
}
