package com.nexuscraft.nexuspath;

/** Turns &-colour codes into section signs. */
final class ColorText {
    private ColorText() {
    }

    static String translate(String s) {
        return s == null ? "" : s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }
}
