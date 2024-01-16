package org.quark.dr.canapp;

import java.util.HashMap;

public class Projects {
    /**
     * {
     *  "projects": {
     *   "All": {
     *    "code": "ALL",
     *    "addressing": {
     *     "00": [
     *      "CAN",
     *      "Primary CAN network"
     *     ]
     *    },
     *    "snat": {
     *     "E7": "7EC"
     *    },
     *    "dnat": {
     *     "E7": "7E4"
     *    }
     *   }
     *  }
     * }
     */
    public HashMap<String, project> projects = new HashMap<>();

    public static class project {
        public String code;
        public HashMap<String, HashMap<String, HashMap<String, String>>> addressing = new HashMap<>();
        public HashMap<String, String>  snat = new HashMap<>();
        public HashMap<String, String>  dnat = new HashMap<>();
    }
}
