package org.openhab.habdroid.util.bleBeaconUtil;

import org.openhab.habdroid.model.OpenHABBeacon;

public class BeaconParser {

    private static final String[] EDDYSTONE_URL_PREFIX = {
            "http://www.",
            "https://www.",
            "http://",
            "https://"
    };

    private static final String[] EDDYSTONE_URL_POSTFIX = {
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
                default://TODO Ignore Eddystone TLM and EID model for now
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
        byte txPower = data[12];
        StringBuilder url = new StringBuilder();
        url.append(EDDYSTONE_URL_PREFIX[data[13]]);
        int i = 14;
        while (data[i] != 0x13){
            if (data[i] <= 0x0d) {
                url.append(EDDYSTONE_URL_POSTFIX[data[i]]);
            } else {
                url.append((char)data[i]);
            }
            i++;
        }
        return OpenHABBeacon.builder()
                .txPower(txPower)
                .url(url.toString());
    }

    private static OpenHABBeacon.Builder parseEddystoneUid(byte[] data){
        return null;
    }

    private static OpenHABBeacon.Builder parseIBeacon(byte[] data){
        return null;
    }
}
