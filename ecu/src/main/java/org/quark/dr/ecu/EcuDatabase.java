package org.quark.dr.ecu;

import android.os.Environment;
import androidx.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class EcuDatabase {
    boolean m_loaded;
    private HashMap<Integer, ArrayList<EcuInfo>> m_ecuInfo;
    private HashMap<Integer, String> m_ecuAddressing;
    private Set<String> m_projectSet;
    private String m_ecuFilePath;
    private ZipFileSystem m_zipFileSystem;

    private HashMap<Integer, String> RXADDRMAP, TXADDRMAP;
    private HashMap<String, String> MODELSMAP;

    private static final String RXAT = "01: 760, 02: 724, 03: 70F, 04: 762, 06: 791, 07: 771, 08: 778, 09: 7EB, 0B: 18DAF10B, 0D: 775, 0E: 76E, 0F: 770, 10: 18DAF110, 11: 7C9, 12: 18DAF112, 13: 732, 14: 18DAF214, 15: 7E8, 16: 18DAF271, 17: 18DAF117, 18: 7E9, 1A: 731, 1B: 7AC, 1C: 76B, 1D: 18DAF11D, 1E: 768, 20: 794, 21: 761, 22: 7C2, 23: 773, 24: 77D, 25: 700, 26: 765, 27: 76D, 28: 7D7, 29: 764, 2A: 76F, 2B: 735, 2C: 772, 2D: 760, 2E: 7BC, 2F: 76C, 30: 7DD, 32: 776, 33: 18DAF200, 35: 776, 39: 73A, 3A: 7D2, 3B: 7C4, 3C: 7DB, 3D: 7A9, 3E: 18DAF23E, 40: 727, 400: 69C, 401: 5C1, 402: 771, 403: 58B, 404: 5BA, 405: 5BB, 406: 4A7, 407: 757, 408: 5C4, 409: 484, 40A: 7EC, 40B: 79D, 40C: 7A7, 40D: 4B3, 40E: 5B8, 40F: 5B7, 41: 730, 410: 704, 411: 7ED, 412: 7EB, 413: 701, 414: 585, 415: 5D0, 416: 5D6, 417: 726, 418: 5AF, 419: 5C6, 42: 76D, 420: 585, 421: 5AF, 422: 5C4, 423: 5D0, 424: 5D6, 425: 701, 426: 701, 427: 587, 428: 726, 429: 18DAF329, 43: 779, 45: 729, 46: 7CF, 47: 7A8, 48: 7D1, 49: 18DAF249, 4A: 7D2, 4C: 18DAF24C, 4D: 7BD, 4F: 18DAF14F, 50: 738, 500: 18DAF500, 501: 18DAF501, 502: 18DAF502, 503: 18DAF503, 504: 18DAF504, 505: 18DAF505, 506: 18DAF506, 507: 18DAF507, 508: 18DAF508, 51: 763, 54: 18DAF254, 55: 18DAF155, 56: 18DAF156, 57: 18DAF257, 58: 767, 59: 734, 5B: 7A5, 5C: 774, 5D: 18DAF25D, 5E: 18DAF25E, 60: 18DAF160, 61: 7BA, 62: 7DD, 63: 73E, 64: 7D5, 65: 72A, 66: 739, 67: 793, 68: 77E, 69: 18DAF269, 6B: 7B5, 6C: 18DAF26C, 6D: 18DAF26D, 6E: 7E9, 6F: 18DAF26F, 72: 18DAF272, 73: 18DAF273, 74: 18DAF270, 75: 18DAF175, 76: 7D3, 77: 7DA, 78: 783, 79: 7EA, 7A: 7E8, 7B: 18DAF272, 7C: 77C, 80: 74A, 81: 761, 82: 7AD, 83: 18DAF283, 84: 18DAF284, 85: 728, 86: 7A2, 87: 7A0, 89: 18DAF289, 8B: 18DAF28B, 8D: 18DAF28D, 8E: 18DAF28E, 91: 7ED, 92: 18DAF192, 93: 7BB, 94: 18DAF294, 95: 7EC, 96: 79A, 97: 7C8, 98: 18DAF198, 99: 18DAF199, 9A: 18DAF19A, A0: 18DAF2A0, A1: 7AB, A2: 18DAF2A2, A3: 729, A4: 779, A5: 725, A6: 726, A7: 733, A8: 7B6, A9: 791, AA: 7A6, AB: 18DAF2AB, AC: 18DAF2AC, C0: 7B9, C2: 18DAF1C2, C8: 18DAF2C8, C9: 18DAF1C9, CE: 18DAF223, CF: 18DAF224, D0: 18DAF1D0, D1: 18DAF1D1, D2: 18DAF1D2, D3: 7EE, D4: 18DAF1D4, D5: 18DAF1E5, D6: 18DAF2D6, D7: 18DAF2D7, D8: 18DAF1D8, D9: 18DAF2D9, DA: 7EC, DB: 7ED, DC: 7B6, DD: 776, DE: 793, DF: 18DAF1DF, E0: 18DAF1E0, E1: 7EE, E2: 70F, E3: 18DAF1E3, E4: 18DAF2E4, E5: 18DAF1E5, E6: 18DAE6F1, E7: 7EC, E8: 5C4, E9: 18DAF1E9, EA: 18DAF1EA, EB: 18DAF1EB, EC: 18DAF1EC, ED: 18DAF1ED, EE: 18DAF2EE, EF: 18DAF1EF, F7: 736, F8: 737, F9: 72B, FA: 77B, FD: 76F, FE: 76C, FF: 7D0";

    private static final String TXAT = "01: 740, 02: 704, 03: 70E, 04: 742, 06: 790, 07: 751, 08: 758, 09: 7E3, 0B: 18DA0BF1, 0D: 755, 0E: 74E, 0F: 750, 10: 18DA10F1, 11: 7C3, 12: 18DA12F1, 13: 712, 14: 18DA14F2, 15: 7E0, 16: 18DA71F2, 17: 18DA17F1, 18: 7E1, 1A: 711, 1B: 7A4, 1C: 74B, 1D: 18DA1DF1, 1E: 748, 20: 798, 21: 73F, 22: 781, 23: 753, 24: 75D, 25: 70C, 26: 745, 27: 74D, 28: 78A, 29: 744, 2A: 74F, 2B: 723, 2C: 752, 2D: 740, 2E: 79C, 2F: 74C, 30: 7DC, 32: 756, 33: 18DA00F2, 35: 756, 39: 71A, 3A: 7D6, 3B: 7C5, 3C: 7D9, 3D: 7A1, 3E: 18DA3EF2, 40: 707, 400: 6BC, 401: 641, 402: 742, 403: 60B, 404: 63A, 405: 63B, 406: 73A, 407: 74F, 408: 644, 409: 622, 40A: 7E4, 40B: 79C, 40C: 79F, 40D: 79A, 40E: 638, 40F: 637, 41: 710, 410: 714, 411: 7E5, 412: 7E3, 413: 711, 414: 605, 415: 650, 416: 656, 417: 746, 418: 62F, 419: 646, 42: 74D, 420: 605, 421: 62F, 422: 644, 423: 650, 424: 656, 425: 711, 426: 711, 427: 607, 428: 746, 429: 18DA29F3, 43: 759, 45: 709, 46: 7CD, 47: 788, 48: 7C6, 49: 18DA49F2, 4A: 7D6, 4C: 18DA4CF2, 4D: 79D, 4F: 18DA4FF1, 50: 718, 500: 18DA00F5, 501: 18DA01F5, 502: 18DA02F5, 503: 18DA03F5, 504: 18DA04F5, 505: 18DA05F5, 506: 18DA06F5, 507: 18DA07F5, 508: 18DA08F5, 51: 743, 54: 18DA54F2, 55: 18DA55F1, 56: 18DA56F1, 57: 18DA57F2, 58: 747, 59: 714, 5B: 785, 5C: 754, 5D: 18DA5DF2, 5E: 18DA5EF2, 60: 18DA60F1, 61: 7B7, 62: 7DC, 63: 73D, 64: 7D4, 65: 70A, 66: 719, 67: 792, 68: 75A, 69: 18DA69F2, 6B: 795, 6C: 18DA6CF2, 6D: 18DA6DF2, 6E: 7E1, 6F: 18DA6FF2, 72: 18DA72F2, 73: 18DA73F2, 74: 18DA70F2, 75: 18DA75F1, 76: 7C7, 77: 7CA, 78: 746, 79: 7E2, 7A: 7E0, 7B: 18DA72F2, 7C: 75C, 80: 749, 81: 73F, 82: 7AA, 83: 18DA83F2, 84: 18DA84F2, 85: 708, 86: 782, 87: 780, 89: 18DA89F2, 8B: 18DA8BF2, 8D: 18DA8DF2, 8E: 18DA8EF2, 91: 7E5, 92: 18DA92F1, 93: 79B, 94: 18DA94F2, 95: 7E4, 96: 797, 97: 7D8, 98: 18DA98F1, 99: 18DA99F1, 9A: 18DA9AF1, A0: 18DAA0F2, A1: 78B, A2: 18DAA2F2, A3: 709, A4: 759, A5: 705, A6: 706, A7: 713, A8: 796, A9: 790, AA: 786, AB: 18DAABF2, AC: 18DAACF2, C0: 799, C2: 18DAC2F1, C8: 18DAC8F2, C9: 18DAC9F1, CE: 18DA23F2, CF: 18DA24F2, D0: 18DAD0F1, D1: 18DAD1F1, D2: 18DAD2F1, D3: 7E6, D4: 18DAD4F1, D5: 27C4A31, D6: 18DAD6F2, D7: 18DAD7F2, D8: 18DAD8F1, D9: 18DAD9F2, DA: 7E4, DB: 7E5, DC: 796, DD: 756, DE: 792, DF: 18DADFF1, E0: 18DAE0F1, E1: 7E6, E2: 70E, E3: 18DAE3F1, E4: 18DAE4F1, E5: 18DAE5F1, E6: 18DAF1E6, E7: 7E4, E8: 644, E9: 18DAE9F1, EA: 18DAEAF1, EB: 18DAEBF1, EC: 18DAECF1, ED: 18DAEDF1, EE: 18DAEEF2, EF: 18DAEFF1, F7: 716, F8: 717, F9: 70B, FA: 75B, FD: 74F, FE: 74C, FF: 7D0";

    private static final String[] PROJECT_MODEL_DICT = {"AS1:Alpine A110", "DZ110:Alpine DZ110", "X1316A:Alpine ECHO", "XCOP_BEFORE_C1A:Contrôle COP Before C1A", "XCOP_C1A:Contrôle COP C1A", "XCOP_C1AHS:Contrôle COP C1A HS", "XPIV_C1A:DDT-Training C1A", "XPIV_C1AHS:DDT-Training C1A HS", "XJC:Dacia Arkana", "XGA:Dacia BM Lada", "X67:Dacia Docker - Kangoo", "X79:Dacia Duster", "X1310:Dacia Duster II", "X79PH2:Dacia Duster Phase 2", "XJD:Dacia Duster Phase 3", "XHA:Dacia Kaptur - Captur (BAR/IN/RU)", "XHAPH2:Dacia Kaptur - Captur (BAR/IN/RU) Ph2", "X92:Dacia Lodgy", "X52:Dacia Logan", "X90:Dacia Logan / Sandero", "XJI:Dacia Logan III", "X1312:Dacia New Kaptur - Captur (BAR/IN/RU)", "NOVA:Dacia Nova", "W176:Daimler (w176)", "W205:Daimler (w205)", "X60B:Daimler Andrew (xMZ, xNE)", "VS10:Daimler Citan", "VS11:Daimler New Citan", "XJN:Lada (XJN)", "XJO:Lada (XJO)", "RF90:Lada Largus", "XGF:Lada Vesta", "XJF:Logan III Badge Renault", "XR210:Mobilize [Twizy] EZ1", "LZ2A:New EV ((C1A HS EVO) SWEET 400 Nissan)", "P13C:New Kaptur - Captur (Nissan)", "J32V:Nissan (J32V)", "P32:Nissan (P32)", "P33A:Nissan (P33A)", "P33B:Nissan (P33B)", "P42Q:Nissan (P42Q)", "P42R:Nissan (P42R)", "XNN:Nissan (PB1D)", "PY1B:Nissan (PY1B)", "ALMERA:Nissan Almera", "MARCH:Nissan March-Micra", "X02E:Nissan Micra", "X60A:Nissan Navarra (xND)", "L21B:Nissan Note", "PRIMERA:Nissan Primera", "X13A:Nissan [Juke]", "PZ1A:Nissan [Leaf]", "PZ1C:Nissan [PZ1C]", "X89:Renault (X89)", "X96:Renault (X96)", "XFJ:Renault (x38_Chine)", "X94:Renault (x94)", "XFG:Renault (xFG)", "X1317:Renault 4Ever (EV)", "U60:Renault Alaskan - (u55, xND)", "XEF:Renault Alpine A110", "XHN:Renault Austral ", "XHNPH2:Renault Austral Sweet400", "X66:Renault Avantime", "XFI:Renault C-Hatch China (C1A)", "X87:Renault Captur", "X87PH2:Renault Captur Phase 2", "XJA:Renault Clio (C1A)", "XJAPH2:Renault Clio (C1A) Phase2", "X65:Renault Clio II", "X85:Renault Clio III", "X98:Renault Clio IV", "X98PH2:Renault Clio IV Phase 2", "XJK:Renault Docker II", "X81:Renault Espace IV", "X81PH2:Renault Espace IV Phase 2", "X81PH3:Renault Espace IV Phase 3", "X81PH4:Renault Espace IV Phase 4", "XFC:Renault Espace V", "XFCPH2:Renault Espace V Ph2", "X38:Renault Fluence", "XJL:Renault Fluence - Korea", "XJLPH2:Renault Fluence - Korea Phase 2", "HFE:Renault Kadjar", "XZH:Renault Kadjar CN", "XZHPH2:Renault Kadjar CN Ph2", "HFEPH2:Renault Kadjar Ph2", "XZI:Renault Kadjar Rus", "X76:Renault Kangoo", "X61:Renault Kangoo II", "X61PH2:Renault Kangoo II Phase 2", "XZG:Renault Koleos II", "XZJ:Renault Koleos II - Chine", "XZJPH2:Renault Koleos II - Chine Ph2", "XZGPH2:Renault Koleos II Ph2", "XZGPH3:Renault Koleos II Ph3", "XBA:Renault Kwid", "XBB:Renault Kwid BR", "XBG:Renault Kwid EV", "XBGPH2:Renault Kwid EV Sweet400", "X56:Renault Laguna", "X74:Renault Laguna II", "X74PH2:Renault Laguna II Phase 2", "X91:Renault Laguna III", "X91PH2:Renault Laguna III Phase 2", "X91PH3:Renault Laguna III Phase 3", "X47:Renault Laguna III Tricorps", "X43:Renault Latitude", "X24:Renault Mascott", "XDC:Renault Master Chine", "X70:Renault Master II", "X70PH3:Renault Master II Phase 3", "X62:Renault Master III", "X62PH2:Renault Master III Phase 2", "XDD:Renault Master IV", "XDE:Renault Master IV Double Cabin", "X64:Renault Megane & Scenic", "XCB:Renault Megane E-Tech Electrique", "XCBPH2:Renault Megane E-Tech Electrique Sweet400", "X84:Renault Megane II", "X84PH2:Renault Megane II Phase 2", "X84BUGABS:Renault Megane II hors ABS", "X84ABSONLY:Renault Megane II only ABS", "X95:Renault Megane III", "X95PH2:Renault Megane III Phase 2", "XFB:Renault Megane IV", "XFF:Renault Megane IV - Sedan", "XFFPH2:Renault Megane IV - Sedan Ph2", "XFBPH2:Renault Megane IV Ph2", "X77:Renault Modus", "X77PH2:Renault Modus Phase2", "XJB:Renault New Captur (C1A)", "XJBPH2:Renault New Captur (C1A)Ph2", "XJE:Renault New Captur Chine)", "XCC:Renault New EV (C1A HS Evo Sweet400)", "XCD:Renault New EV China version (C1A HS Evo Sweet400)", "XFK:Renault New Kangoo", "X1316:Renault R5 Elec", "X54:Renault Safrane", "XFA:Renault Scenic IV", "XFAPH2:Renault Scenic IV phase2", "X35:Renault Symbol / Thalia", "XFD:Renault Talisman", "XFDPH2:Renault Talisman Phase II", "X83:Renault Trafic II", "X83PH2:Renault Trafic II Phase 2", "X83PH3:Renault Trafic II Phase 3", "X82:Renault Trafic III", "X82PH2:Renault Trafic III Phase2", "XBC:Renault Triber-Kiger India", "X06:Renault Twingo", "X44:Renault Twingo II", "X44PH2:Renault Twingo II Phase2", "X07:Renault Twingo III", "X07PH2:Renault Twingo III Ph2", "X09:Renault Twizy", "X73:Renault VelSatis", "X73PH2:Renault VelSatis Phase 2", "X33:Renault Wind", "X10:Renault Zoe", "X10PH2:Renault Zoe (C1A-Neo)", "XJP:Renault [Captur] ", "XHC:Renault [SUV]Chine", "XJH:Renault xJH", "KJA:Rsm KJA", "H45:Rsm Koleos", "EDISON:X07 Daimler"};

    public class EcuIdent{
        public String supplier_code, soft_version, version, diagnostic_version;
    }

    public class EcuInfo {
        public Set<String> projects;
        public String href;
        public String ecuName, protocol;
        public int addressId;
        public EcuIdent ecuIdents[];
        public boolean exact_match;
    }

    public class DatabaseException extends Exception {
        public DatabaseException(String message) {
            super(message);
        }
    }

    public ArrayList<EcuInfo> getEcuInfo(int addr) {
        return m_ecuInfo.get(addr);
    }

    public ArrayList<String> getEcuByFunctions() {
        ArrayList<String> list = new ArrayList<>();
        Iterator<String> valueIterator = m_ecuAddressing.values().iterator();
        while (valueIterator.hasNext()) {
            list.add(valueIterator.next());
        }
        return list;
    }

    public class EcuIdentifierNew {
        public String supplier, version, soft_version, diag_version;
        public int addr;
        public EcuIdentifierNew() {
            reInit(-1);
        }
        public void reInit(int addr){
            this.addr = addr;
            supplier = version = soft_version = diag_version = "";
        }
        public boolean isFullyFilled(){
            return !supplier.isEmpty() && !version.isEmpty() && !soft_version.isEmpty() && !diag_version.isEmpty();
        }
    }

    @Nullable
    public EcuInfo identifyOldEcu(int addressId, String supplier, String soft_version, String version, int diag_version) {
        ArrayList<EcuInfo> ecuInfos = m_ecuInfo.get(addressId);
        if (ecuInfos == null)
            return null;
        EcuIdent closestEcuIdent = null;
        EcuInfo keptEcuInfo = null;
        for (EcuInfo ecuInfo : ecuInfos){
            for(EcuIdent ecuIdent: ecuInfo.ecuIdents) {
                if (ecuIdent.supplier_code.equals(supplier) && ecuIdent.soft_version.equals(soft_version)) {
                    if (ecuIdent.version.equals(version) && diag_version == Integer.parseInt(ecuIdent.diagnostic_version, 10)) {
                        ecuInfo.exact_match = true;
                        return ecuInfo;
                    }
                    if (closestEcuIdent == null){
                        closestEcuIdent = ecuIdent;
                        continue;
                    }
                    int intVersion = Integer.parseInt(version, 16);
                    int currentDiff = Math.abs(
                            Integer.parseInt(ecuIdent.version, 16) - intVersion);
                    int oldDiff = Math.abs(Integer.parseInt(
                            closestEcuIdent.version, 16) - intVersion);
                    if (currentDiff < oldDiff){
                        closestEcuIdent = ecuIdent;
                        keptEcuInfo = ecuInfo;
                    }
                }
            }
        }
        if (keptEcuInfo != null)
            keptEcuInfo.exact_match = false;
        return keptEcuInfo;
    }

    public ArrayList<EcuInfo> identifyNewEcu(EcuIdentifierNew ecuIdentifer){
        ArrayList<EcuInfo> ecuInfos = m_ecuInfo.get(ecuIdentifer.addr);
        ArrayList<EcuInfo> keptEcus= new ArrayList<>();
        for (EcuInfo ecuInfo : ecuInfos) {
            for (EcuIdent ecuIdent : ecuInfo.ecuIdents) {
                if (ecuIdent.supplier_code.equals(ecuIdentifer.supplier) &&
                        ecuIdent.version.equals(ecuIdentifer.version)) {
                    ecuInfo.exact_match = false;
                    keptEcus.add(ecuInfo);
                    if (ecuIdent.soft_version.equals(ecuIdentifer.soft_version)) {
                        ecuInfo.exact_match = true;
                        keptEcus.clear();
                        keptEcus.add(ecuInfo);
                        return keptEcus;
                    }
                }
            }
        }
        return keptEcus;
    }

    public ArrayList<String> getEcuByFunctionsAndType(String type) {
        Set<String> list = new HashSet<>();
        Iterator<ArrayList<EcuInfo>> ecuArrayIterator = m_ecuInfo.values().iterator();

        while (ecuArrayIterator.hasNext()) {
            ArrayList<EcuInfo> ecuArray = ecuArrayIterator.next();
            for (EcuInfo ecuInfo : ecuArray) {
                if ((ecuInfo.projects.contains(type) || type.isEmpty())
                        && m_ecuAddressing.containsKey(ecuInfo.addressId)) {
                    list.add(m_ecuAddressing.get(ecuInfo.addressId));
                }
            }
        }
        ArrayList<String> ret = new ArrayList<>();
        for (String txt : list) {
            ret.add(txt);
        }
        return ret;
    }

    public int getAddressByFunction(String name) {
        Set<Integer> keySet = m_ecuAddressing.keySet();
        for (Integer i : keySet) {
            if (m_ecuAddressing.get(i) == name) {
                return i;
            }
        }
        return -1;
    }

    public EcuDatabase() {
        m_loaded = false;
        m_ecuInfo = new HashMap<>();
        m_ecuAddressing = new HashMap<>();
        MODELSMAP = new HashMap<>();
        RXADDRMAP = new HashMap<>();
        TXADDRMAP = new HashMap<>();
        buildMaps();
        loadAddressing();
        loadModels();
    }

    public String[] getProjects(){
        return m_projectSet.toArray(new String[m_projectSet.size()]);
    }

    public String[] getModels() {
        return MODELSMAP.values().toArray(new String[MODELSMAP.size()]);
    }

    public String getProjectFromModel(String model){
        Iterator it = MODELSMAP.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry keyval = (Map.Entry)it.next();
            if (((String)keyval.getValue()).toUpperCase().equals(model.toUpperCase())){
                return (String)keyval.getKey();
            }
        }
        return "";
    }

    private void loadModels(){
        for (int i = 0; i < PROJECT_MODEL_DICT.length; ++i){
            String[] split = PROJECT_MODEL_DICT[i].split(":");
            if (split.length != 2)
                continue;
            MODELSMAP.put(split[0], split[1]);
        }
        MODELSMAP.put("", "All");
    }

    private void filterProjects(){
        Iterator<String> its = m_projectSet.iterator();
        while(its.hasNext()) {
            Set<String> modelKeySet = MODELSMAP.keySet();
            String project = its.next();
            if (!MODELSMAP.containsKey(project)){
                MODELSMAP.remove(project);
            }
        }
    }

    public void checkMissings(){
        Iterator<String> its = m_projectSet.iterator();
        while(its.hasNext()){
            Set<String> modelKeySet = MODELSMAP.keySet();
            String project = its.next();
            if (!MODELSMAP.containsKey(project)){
                System.out.println("?? Missing " + project);
            }
        }
    }

    private void loadAddressing() {
        String addressingResource = "addressing.json";
        InputStream ecu_stream = getClass().getClassLoader().getResourceAsStream(addressingResource);
        String line;
        BufferedReader br;
        StringBuilder sb = new StringBuilder();

        try {
            br = new BufferedReader(new InputStreamReader(ecu_stream));
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            JSONObject jobj = new JSONObject(sb.toString());
            Iterator<String> keys = jobj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray ecuArray = jobj.getJSONArray(key);
                String name = ecuArray.getString(1);
                m_ecuAddressing.put(Integer.parseInt(key, 16), name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String loadDatabase(String ecuFilename, String appDir) throws DatabaseException {
        if (m_loaded) {
            Log.e("EcuDatabase", "Database already loaded");
            return m_ecuFilePath;
        }
        File checkEcuFile = new File(ecuFilename);
        if (!checkEcuFile.exists())
            ecuFilename = "";

        if (ecuFilename.isEmpty()) {
            ecuFilename = searchEcuFile(new File(Environment.getExternalStorageDirectory().getPath()));
        }
        if (ecuFilename.isEmpty()) {
            ecuFilename = searchEcuFile(new File(Environment.getDataDirectory().getPath()));
        }
        if (ecuFilename.isEmpty()) {
            ecuFilename = searchEcuFile(new File("/storage"));
        }
        if (ecuFilename.isEmpty()) {
            ecuFilename = searchEcuFile(new File("/mnt"));
        }
        if (ecuFilename.isEmpty()) {
            throw new DatabaseException("Ecu file (ecu.zip) not found");
        }

        String bytes;
        m_ecuFilePath = ecuFilename;
        String indexFileName = appDir + "/ecu.idx";

        File indexFile = new File(indexFileName);
        File ecuFile = new File(m_ecuFilePath);
        if (!ecuFile.exists()){
            throw new DatabaseException("Archive (ecu.zip) file not found");
        }
        long indexTimeStamp = indexFile.lastModified();
        long ecuTimeStamp = ecuFile.lastModified();
        m_zipFileSystem = new ZipFileSystem(m_ecuFilePath, appDir);

        /*
         * If index is already made, use it
         * Also check files exists and timestamps to force [re]scan
         */
        if (indexFile.exists() && (indexTimeStamp > ecuTimeStamp) && m_zipFileSystem.importZipEntries()){
            bytes = m_zipFileSystem.getZipFile("db.json");
            if (bytes.isEmpty()){
                throw new DatabaseException("Database (db.json) file not found");
            }
        } else {
            /*
             * Else create it
             */
            m_zipFileSystem.getZipEntries();
            m_zipFileSystem.exportZipEntries();
            bytes = m_zipFileSystem.getZipFile("db.json");
            if (bytes.isEmpty()){
                throw new DatabaseException("Database (db.json) file not found");
            }
        }

        JSONObject jsonRootObject;
        try {
            jsonRootObject = new JSONObject(bytes);
        } catch (Exception e) {
            throw new DatabaseException("JSON conversion issue");
        }

        m_projectSet = new HashSet<>();
        Set<Integer> addressSet = new HashSet<>();
        Iterator<String> keys = jsonRootObject.keys();
        for (; keys.hasNext(); ) {
            String href = keys.next();
            try {
                JSONObject ecuJsonObject = jsonRootObject.getJSONObject(href);
                JSONArray projectJsonObject = ecuJsonObject.getJSONArray("projects");
                Set<String> projectsSet = new HashSet<>();
                for (int i = 0; i < projectJsonObject.length(); ++i) {
                    String project = projectJsonObject.getString(i);
                    String upperCaseProject = project.toUpperCase();
                    projectsSet.add(upperCaseProject);
                    m_projectSet.add(upperCaseProject);
                }
                int addrId = Integer.parseInt(ecuJsonObject.getString("address"), 16);
                addressSet.add(addrId);
                EcuInfo info = new EcuInfo();
                info.ecuName = ecuJsonObject.getString("ecuname");
                info.href = href;
                info.projects = projectsSet;
                info.addressId = addrId;
                info.protocol = ecuJsonObject.getString("protocol");
                JSONArray jsAutoIdents = ecuJsonObject.getJSONArray("autoidents");
                info.ecuIdents = new EcuIdent[jsAutoIdents.length()];
                for (int i = 0; i < jsAutoIdents.length(); ++i) {
                    JSONObject jsAutoIdent = jsAutoIdents.getJSONObject(i);
                    info.ecuIdents[i] = new EcuIdent();
                    info.ecuIdents[i].soft_version = jsAutoIdent.getString("soft_version");
                    info.ecuIdents[i].supplier_code = jsAutoIdent.getString("supplier_code");
                    info.ecuIdents[i].version = jsAutoIdent.getString("version");
                    info.ecuIdents[i].diagnostic_version = jsAutoIdent.getString("diagnostic_version");
                }
                ArrayList<EcuInfo> ecuList;
                if (!m_ecuInfo.containsKey(addrId)) {
                    ecuList = new ArrayList<>();
                    m_ecuInfo.put(addrId, ecuList);
                } else {
                    ecuList = m_ecuInfo.get(addrId);
                }
                ecuList.add(info);
            } catch (Exception e) {
                e.printStackTrace();
                throw new DatabaseException("JSON parsing issue");
            }
        }

        Set<Integer> keySet = new HashSet<>(m_ecuAddressing.keySet());
        for (Integer key : keySet) {
            if (!addressSet.contains(key)) {
                m_ecuAddressing.remove(key);
            }
        }

        m_loaded = true;
        filterProjects();
        return ecuFilename;
    }

    public boolean isLoaded() {
        return m_loaded;
    }

    public String searchEcuFile(File dir) {
        if (!dir.exists()) {
            return "";
        }
        File listFile[] = dir.listFiles();
        if (listFile != null) {
            for (File f : listFile) {
                if (f.isDirectory()) {
                    String res = searchEcuFile(f);
                    if (!res.isEmpty())
                        return res;
                } else {
                    if (f.getName().equalsIgnoreCase("ecu.zip")) {
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        return "";
    }

    public String getZipFile(String filePath){
        return m_zipFileSystem.getZipFile(filePath);
    }

    private void buildMaps(){
        String[] RXS = RXAT.replace(" ", "").split(",");
        String[] TXS = TXAT.replace(" ", "").split(",");

        for (String rxs : RXS){
            String[] idToAddr = rxs.split(":");
            RXADDRMAP.put(Integer.parseInt(idToAddr[0], 16), idToAddr[1]);
        }
        for (String txs : TXS){
            String[] idToAddr = txs.split(":");
            TXADDRMAP.put(Integer.parseInt(idToAddr[0], 16), idToAddr[1]);
        }
    }

    public String getRxAddressById(int id){
        return RXADDRMAP.get(id);
    }

    public String getTxAddressById(int id){
        return TXADDRMAP.get(id);
    }

    public ZipFileSystem getZipFileSystem(){
        return m_zipFileSystem;
    }
}
