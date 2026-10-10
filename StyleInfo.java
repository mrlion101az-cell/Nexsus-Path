package com.nexuscraft.nexuspath;

import java.util.List;

/** A named bundle of systems: "Builder", "Survivor" and so on. */
record StyleInfo(String id, String title, String icon, String blurb, List<String> systems) {
}
