package org.quark.dr.ecu;

import java.util.HashMap;

public class ProjectData {
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
     *    "snat_ext": {
     *     "E7": "7ECDDDEE"
     *    },
     *    "dnat": {
     *     "E7": "7E4"
     *    "dnat_ext": {
     *     "E7": "7E4EEEFF"
     *    }
     *   }
     *  }
     * }
     */

    public static class Projects {
        public HashMap<String, Project> projects = new HashMap<>();
    }

    public static class Project {
        public String code;
        public HashMap<String, String[]> addressing = new HashMap<>();
        public HashMap<String, String> snat = new HashMap<>();
        public HashMap<String, String> snat_ext = new HashMap<>();
        public HashMap<String, String> dnat = new HashMap<>();
        public HashMap<String, String> dnat_ext = new HashMap<>();
    }
}