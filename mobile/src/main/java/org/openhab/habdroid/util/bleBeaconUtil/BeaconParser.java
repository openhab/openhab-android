package org.openhab.habdroid.util.bleBeaconUtil;

import org.openhab.habdroid.model.OpenHABBeacon;

/*
  This is the BLE beacon parser to parse BLE advertisement data into specified beacon format.
  The implementations are the shortcut to recognize only Eddystone beacons(both URL and UID) and iBeacons.
  The official specification can go through here:
  Eddystone beacon: https://github.com/google/eddystone/blob/master/protocol-specification.md
  iBeacon: https://developer.apple.com/ibeacon/
*/
public class BeaconParser {
    private static final String[] EDDYSTONE_URL_PREFIX = {
            "http://www.",
            "https://www.",
            "http://",
            "https://"
    };

    private static final String[] EDDYSTONE_URL_SUFFIX = {
            ".com/",
            ".org/",
            ".edu/",
            ".net/",
            ".info/",
            ".biz/",
            ".gov/",
            ".com",
            ".org",
            ".edu",
            ".net",
            ".info",
            ".biz",
            ".gov",
    };

    //Return null if not a beacon.
    public static OpenHABBeacon.Builder parseToBeacon(byte[] data){
        OpenHABBeacon.Type type = whichBeacon(data);
        switch (type){
            case EddystoneUrl:
                return parseEddystoneUrl(data);
            case EddystoneUid:
                return parseEddystoneUid(data);
            case iBeacon:
                return parseIBeacon(data);
            default:
                return null;
        }
    }

    private static OpenHABBeacon.Type whichBeacon(byte[] data){
        if (isEddystoneBeacon(data)){
            switch (data[11]){
                case 0x00:
                    return OpenHABBeacon.Type.EddystoneUid;
                case 0x10:
                    return OpenHABBeacon.Type.EddystoneUrl;
                default://Ignore Eddystone TLM and EID model for now
                    return OpenHABBeacon.Type.NotABeacon;
            }
        }
        if (isIBeacon(data)){
            return OpenHABBeacon.Type.iBeacon;
        }
        //More type of beacons can be inserted here

        return OpenHABBeacon.Type.NotABeacon;
    }

    //According to definition, the byte offset 9,10 are Eddystone beacon's service data type - 0xFEAA in litte endian.
    //To see more specification of Eddystone beacon, go through here: https://github.com/google/eddystone/blob/master/protocol-specification.md
    private static boolean isEddystoneBeacon(byte[] data){
        return data[9] == (byte)0xAA && data[10] == (byte)0xFE;
    }

    //iBeacon specification go through here: https://developer.apple.com/ibeacon/
    private static boolean isIBeacon(byte[] data){
        return data[5] == (byte)0x4C && data[6] == 0x00
                && data[7] == 0x02 && data[8] == 0x15;
    }

    private static OpenHABBeacon.Builder parseEddystoneUrl(byte[] data){
        StringBuilder url = new StringBuilder();
        int urlLength = data[7] - 6;//data[7] is the total length of Service ids, Eddystone type, tx power, URL prefix and url length.
        byte txPower = (byte)(data[12] - 41);
        int offset = 14;

        if (data[13] >= 0 && data[13] < EDDYSTONE_URL_PREFIX.length) {
            url.append(EDDYSTONE_URL_PREFIX[data[13]]);
        }

        for (int i = 0; i < urlLength; i++){
            if (data[offset] <= 0x0D) {
                url.append(EDDYSTONE_URL_SUFFIX[data[offset]]);
            } else {
                url.append((char)data[offset]);
            }
            offset++;
        }
        return OpenHABBeacon.builder(OpenHABBeacon.Type.EddystoneUrl)
                .setTxPower(txPower)
                .setUrl(url.toString());
    }

    private static OpenHABBeacon.Builder parseEddystoneUid(byte[] data){
        byte txPower = (byte)(data[12] - 41);
        StringBuilder nameSpace = new StringBuilder();
        for (int i = 13; i < 23; i++){
            nameSpace.append(Integer.toHexString(data[i] & 0xFF));
        }

        StringBuilder instance = new StringBuilder();
        for (int i = 23; i < 29; i++){
            instance.append(Integer.toHexString(data[i] & 0xFF));
        }

        return OpenHABBeacon.builder(OpenHABBeacon.Type.EddystoneUid)
                .setTxPower(txPower)
                .setNameSpace(nameSpace.toString())
                .setInstance(instance.toString());
    }

    private static OpenHABBeacon.Builder parseIBeacon(byte[] data){
        byte txPower = data[29];
        StringBuilder uuid = new StringBuilder();
        for (int i = 9; i < 25; i++){
            uuid.append(Integer.toHexString(data[i] & 0xFF));
        }

        String major = Integer.toString(((data[25] << 8) & 0xFFFF) + (data[26] & 0xFF));
        String minor = Integer.toString(((data[27] << 8) & 0xFFFF) + (data[28] & 0xFF));
        return OpenHABBeacon.builder(OpenHABBeacon.Type.iBeacon)
                .setTxPower(txPower)
                .setUuid(uuid.toString())
                .setMajor(major)
                .setMinor(minor);
    }
}
