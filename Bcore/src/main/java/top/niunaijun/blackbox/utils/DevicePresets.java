package top.niunaijun.blackbox.utils;

/**
 * Modelos de dispositivo coerentes: Build.* + prefixo de IMEI (TAC) + prefixo de MAC (OUI) combinam entre si.
 *
 * ATENÇÃO: os campos de Build (model/device/product/board/hardware) são dos aparelhos reais citados,
 * mas o campo "chip" (linha Hardware do /proc/cpuinfo) também é exemplo; confirme no seu aparelho (cat /proc/cpuinfo),
 * mas os TACs e OUIs abaixo são EXEMPLOS e NÃO foram validados contra GSMA/IEEE.
 * Substitua por valores que você confirmar antes de depender deles.
 */
public final class DevicePresets {
    private DevicePresets() {}

    public static final class Preset {
        public final String name, brand, manufacturer, model, device, product, board, hardware, chip;
        public final String[] tacs;   // prefixos de 8 dígitos do IMEI
        public final String[] ouis;   // prefixos de MAC "aa:bb:cc"

        Preset(String name, String brand, String manufacturer, String model, String device,
               String product, String board, String hardware, String chip, String[] tacs, String[] ouis) {
            this.name = name; this.brand = brand; this.manufacturer = manufacturer; this.model = model;
            this.device = device; this.product = product; this.board = board; this.hardware = hardware;
            this.chip = chip;
            this.tacs = tacs; this.ouis = ouis;
        }
    }

    public static final Preset[] ALL = new Preset[]{
            new Preset("Samsung Galaxy S23", "samsung", "samsung", "SM-S911B", "dm1q", "dm1qxxx",
                    "kalama", "qcom", "Qualcomm Technologies, Inc SM8550", new String[]{"35412117", "35412118"}, new String[]{"8c:f5:a3", "f8:04:2e"}),
            new Preset("Samsung Galaxy A51", "samsung", "samsung", "SM-A515F", "a51", "a51nsxx",
                    "exynos9611", "exynos9611", "Samsung Exynos 9611", new String[]{"35199910", "35199911"}, new String[]{"8c:f5:a3", "f8:04:2e"}),
            new Preset("Samsung Galaxy A54", "samsung", "samsung", "SM-A546B", "a54x", "a54xnsxx",
                    "s5e8835", "s5e8835", "Samsung Exynos 1380", new String[]{"35684922", "35684923"}, new String[]{"8c:f5:a3", "f8:04:2e"}),
            new Preset("Xiaomi 13", "Xiaomi", "Xiaomi", "2211133G", "fuxi", "fuxi_global",
                    "kalama", "qcom", "Qualcomm Technologies, Inc SM8550", new String[]{"86412405", "86412406"}, new String[]{"64:09:80", "28:6c:07"}),
            new Preset("Redmi Note 12", "Redmi", "Xiaomi", "23021RAAEG", "topaz", "topaz_global",
                    "topaz", "qcom", "Qualcomm Technologies, Inc BENGAL", new String[]{"86955206", "86955207"}, new String[]{"64:09:80", "28:6c:07"}),
            new Preset("Google Pixel 7", "google", "Google", "Pixel 7", "panther", "panther",
                    "panther", "tensor", "GS201", new String[]{"35273811", "35273812"}, new String[]{"f4:f5:e8", "3c:5a:b4"}),
    };

    public static String[] names() {
        String[] n = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) n[i] = ALL[i].name;
        return n;
    }

    public static Preset byName(String name) {
        for (Preset p : ALL) if (p.name.equals(name)) return p;
        return null;
    }
}
