package top.niunaijun.blackbox.utils;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import top.niunaijun.blackbox.BlackBoxCore;

/**
 * Perfil de identidade por instância (userId virtual).
 * Cada userId ganha seu próprio IMEI, Android ID, MAC, etc., gerados uma vez
 * e persistidos em disco (arquivo legível pelos processos virtuais, que rodam no mesmo UID do host).
 */
public class DeviceProfile {
    private static final String TAG = "DeviceProfile";
    private static final ConcurrentHashMap<Integer, DeviceProfile> CACHE = new ConcurrentHashMap<>();
    private static final SecureRandom RND = new SecureRandom();

    public String imei;
    public String imei2;
    public String meid;
    public String imsi;
    public String androidId;
    public String wifiMac;
    public String bssid;
    public String ssid;
    public String serial;

    // Build.* coerente com o preset escolhido
    public String presetName = "";
    public String brand = "";
    public String manufacturer = "";
    public String model = "";
    public String device = "";
    public String product = "";
    public String board = "";
    public String hardware = "";
    public String chip = "";
    public String fingerprint = "";

    // Proxy de rede por instância (vazio = sem proxy). type: "http" ou "socks"
    public String proxyType = "http";
    public String proxyHost = "";
    public int proxyPort = 0;

    public boolean hasProxy() {
        return proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0;
    }

    // ---------- API ----------

    public static DeviceProfile get(int userId) {
        DeviceProfile p = CACHE.get(userId);
        if (p != null) return p;
        synchronized (DeviceProfile.class) {
            p = CACHE.get(userId);
            if (p != null) return p;
            p = load(userId);
            if (p == null) {
                p = generate();
                save(userId, p);
            }
            CACHE.put(userId, p);
            return p;
        }
    }

    /** Salva alterações (ex.: configurar proxy) e atualiza o cache deste processo. */
    public static void update(int userId, DeviceProfile p) {
        synchronized (DeviceProfile.class) {
            save(userId, p);
            CACHE.put(userId, p);
        }
    }

    /** Gera novos identificadores para a instância, mantendo o proxy configurado. */
    public static DeviceProfile regenerate(int userId) {
        DeviceProfile old = get(userId);
        DeviceProfile n = generate();
        n.proxyType = old.proxyType;
        n.proxyHost = old.proxyHost;
        n.proxyPort = old.proxyPort;
        update(userId, n);
        return n;
    }

    /** Perfil novo (todos os IDs aleatórios) sem salvar; útil para a UI aplicar só ao confirmar. */
    public static DeviceProfile newRandom(DeviceProfile keepProxyFrom) {
        return newRandom(keepProxyFrom, null);
    }

    /** presetName == null/vazio => preset aleatório. */
    public static DeviceProfile newRandom(DeviceProfile keepProxyFrom, String presetName) {
        DeviceProfile n = generate(presetName);
        if (keepProxyFrom != null) {
            n.proxyType = keepProxyFrom.proxyType;
            n.proxyHost = keepProxyFrom.proxyHost;
            n.proxyPort = keepProxyFrom.proxyPort;
        }
        return n;
    }

    /** Cópia independente (a UI edita a cópia e só persiste ao salvar). */
    public DeviceProfile copy() {
        DeviceProfile c = new DeviceProfile();
        c.imei = imei; c.imei2 = imei2; c.meid = meid; c.imsi = imsi;
        c.androidId = androidId; c.wifiMac = wifiMac; c.bssid = bssid;
        c.ssid = ssid; c.serial = serial;
        c.proxyType = proxyType; c.proxyHost = proxyHost; c.proxyPort = proxyPort;
        c.presetName = presetName; c.brand = brand; c.manufacturer = manufacturer; c.model = model;
        c.device = device; c.product = product; c.board = board; c.hardware = hardware;
        c.fingerprint = fingerprint; c.chip = chip;
        return c;
    }

    // ---------- geração ----------

    private static DeviceProfile generate() {
        return generate(null);
    }

    private static DeviceProfile generate(String presetName) {
        DevicePresets.Preset pre = presetName == null ? null : DevicePresets.byName(presetName);
        if (pre == null) pre = DevicePresets.ALL[RND.nextInt(DevicePresets.ALL.length)];

        DeviceProfile p = new DeviceProfile();
        p.presetName = pre.name;
        p.brand = pre.brand;
        p.manufacturer = pre.manufacturer;
        p.model = pre.model;
        p.device = pre.device;
        p.product = pre.product;
        p.board = pre.board;
        p.hardware = pre.hardware;
        p.chip = pre.chip;
        // Mesma versão/build real do sistema que roda (não inventa release incoerente)
        p.fingerprint = pre.brand + "/" + pre.product + "/" + pre.device + ":"
                + android.os.Build.VERSION.RELEASE + "/" + android.os.Build.ID + "/"
                + android.os.Build.VERSION.INCREMENTAL + ":user/release-keys";

        p.imei = randomImei(pre.tacs[RND.nextInt(pre.tacs.length)]);
        p.imei2 = randomImei(pre.tacs[RND.nextInt(pre.tacs.length)]);
        p.meid = randomHex(14).toUpperCase(Locale.US);
        p.imsi = "724" + String.format(Locale.US, "%02d", RND.nextInt(40)) + randomDigits(10);
        p.androidId = randomHex(16);
        p.wifiMac = randomMac(pre.ouis[RND.nextInt(pre.ouis.length)]);
        p.bssid = randomMac();
        p.ssid = "BlackBox_" + randomHex(4);
        p.serial = randomHex(12).toUpperCase(Locale.US);
        return p;
    }

    private static String randomImei(String tac) {
        StringBuilder sb = new StringBuilder(tac + randomDigits(6)); // TAC(8) + serial(6) + Luhn(1)
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int d = sb.charAt(13 - i) - '0';
            if (i % 2 == 0) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
        }
        sb.append((10 - (sum % 10)) % 10); // dígito Luhn
        return sb.toString();
    }

    private static String randomDigits(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(RND.nextInt(10));
        return sb.toString();
    }

    private static String randomHex(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(Integer.toHexString(RND.nextInt(16)));
        return sb.toString();
    }

    private static String randomMac(String oui) {
        byte[] b = new byte[3];
        RND.nextBytes(b);
        return String.format(Locale.US, "%s:%02x:%02x:%02x", oui, b[0], b[1], b[2]);
    }

    private static String randomMac() {
        byte[] b = new byte[6];
        RND.nextBytes(b);
        b[0] = (byte) ((b[0] & 0xFE) | 0x02); // unicast + localmente administrado
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format(Locale.US, "%02x", b[i]));
        }
        return sb.toString();
    }

    // ---------- persistência ----------

    private static File file(int userId) {
        Context c = BlackBoxCore.getContext();
        File dir = new File(c.getFilesDir(), "device_profiles");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "user_" + userId + ".json");
    }

    private static DeviceProfile load(int userId) {
        try {
            File f = file(userId);
            if (!f.exists()) return null;
            JSONObject o = new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"));
            DeviceProfile p = new DeviceProfile();
            p.imei = o.getString("imei");
            p.imei2 = o.getString("imei2");
            p.meid = o.getString("meid");
            p.imsi = o.getString("imsi");
            p.androidId = o.getString("androidId");
            p.wifiMac = o.getString("wifiMac");
            p.bssid = o.getString("bssid");
            p.ssid = o.getString("ssid");
            p.serial = o.getString("serial");
            p.proxyType = o.optString("proxyType", "http");
            p.proxyHost = o.optString("proxyHost", "");
            p.proxyPort = o.optInt("proxyPort", 0);
            p.presetName = o.optString("presetName", "");
            p.brand = o.optString("brand", "");
            p.manufacturer = o.optString("manufacturer", "");
            p.model = o.optString("model", "");
            p.device = o.optString("device", "");
            p.product = o.optString("product", "");
            p.board = o.optString("board", "");
            p.hardware = o.optString("hardware", "");
            p.chip = o.optString("chip", "");
            p.fingerprint = o.optString("fingerprint", "");
            if (p.model.isEmpty()) {
                // perfil da versão anterior (sem Build.*): regenera coerente, preservando o proxy
                DeviceProfile n = generate();
                n.proxyType = p.proxyType; n.proxyHost = p.proxyHost; n.proxyPort = p.proxyPort;
                save(userId, n);
                return n;
            }
            return p;
        } catch (Throwable t) {
            Slog.w(TAG, "load failed for user " + userId, t);
            return null;
        }
    }

    private static void save(int userId, DeviceProfile p) {
        try {
            JSONObject o = new JSONObject();
            o.put("imei", p.imei);
            o.put("imei2", p.imei2);
            o.put("meid", p.meid);
            o.put("imsi", p.imsi);
            o.put("androidId", p.androidId);
            o.put("wifiMac", p.wifiMac);
            o.put("bssid", p.bssid);
            o.put("ssid", p.ssid);
            o.put("serial", p.serial);
            o.put("proxyType", p.proxyType);
            o.put("proxyHost", p.proxyHost);
            o.put("proxyPort", p.proxyPort);
            o.put("presetName", p.presetName);
            o.put("brand", p.brand);
            o.put("manufacturer", p.manufacturer);
            o.put("model", p.model);
            o.put("device", p.device);
            o.put("product", p.product);
            o.put("board", p.board);
            o.put("hardware", p.hardware);
            o.put("chip", p.chip);
            o.put("fingerprint", p.fingerprint);
            java.nio.file.Files.write(file(userId).toPath(), o.toString().getBytes("UTF-8"));
        } catch (Throwable t) {
            Slog.w(TAG, "save failed for user " + userId, t);
        }
    }
}
