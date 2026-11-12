package top.niunaijun.blackboxa.view.apps

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.utils.DevicePresets
import top.niunaijun.blackbox.utils.DeviceProfile
import top.niunaijun.blackboxa.R

/**
 * Dialog de identidade por instância (userId): todos os campos são editáveis à mão,
 * há "Gerar novos IDs" (usa o modelo escolhido) e configuração opcional de proxy.
 * Nada é gravado até confirmar; ao salvar, os apps da instância são parados
 * para o próximo start já usar os novos valores.
 */
object DeviceIdentityDialog {

    private class Field(
        val label: String,
        val get: (DeviceProfile) -> String,
        val set: (DeviceProfile, String) -> Unit,
        val validate: ((String) -> Boolean)? = null
    ) {
        lateinit var edit: EditText
    }

    private val hex16 = Regex("^[0-9a-fA-F]{16}$")
    private val mac = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")
    private val digits15 = Regex("^[0-9]{15}$")
    private val digits = Regex("^[0-9]{14,16}$")

    private fun luhnOk(s: String): Boolean {
        var sum = 0
        for (i in s.indices) {
            var d = s[s.length - 1 - i] - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    private val fields = listOf(
        Field("IMEI 1", { it.imei }, { p, v -> p.imei = v }, { digits15.matches(it) }),
        Field("IMEI 2", { it.imei2 }, { p, v -> p.imei2 = v }, { digits15.matches(it) }),
        Field("MEID", { it.meid }, { p, v -> p.meid = v }, { it.length in 14..14 }),
        Field("IMSI", { it.imsi }, { p, v -> p.imsi = v }, { digits.matches(it) }),
        Field("Android ID", { it.androidId }, { p, v -> p.androidId = v.lowercase() }, { hex16.matches(it) }),
        Field("Wi-Fi MAC", { it.wifiMac }, { p, v -> p.wifiMac = v.lowercase() }, { mac.matches(it) }),
        Field("BSSID", { it.bssid }, { p, v -> p.bssid = v.lowercase() }, { mac.matches(it) }),
        Field("SSID", { it.ssid }, { p, v -> p.ssid = v }, { it.isNotEmpty() && it.length <= 32 }),
        Field("Serial", { it.serial }, { p, v -> p.serial = v }, { it.isNotEmpty() }),
        Field("Brand", { it.brand }, { p, v -> p.brand = v }),
        Field("Manufacturer", { it.manufacturer }, { p, v -> p.manufacturer = v }),
        Field("Model", { it.model }, { p, v -> p.model = v }),
        Field("Device", { it.device }, { p, v -> p.device = v }),
        Field("Product", { it.product }, { p, v -> p.product = v }),
        Field("Board", { it.board }, { p, v -> p.board = v }),
        Field("Hardware", { it.hardware }, { p, v -> p.hardware = v }),
        Field("CPU (cpuinfo Hardware)", { it.chip }, { p, v -> p.chip = v }),
        Field("Fingerprint", { it.fingerprint }, { p, v -> p.fingerprint = v })
    )

    fun show(context: Context, userId: Int) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_device_identity, null)
        val ll = view.findViewById<LinearLayout>(R.id.ll_fields)
        val btnRegen = view.findViewById<Button>(R.id.btn_regen)
        val rbHttp = view.findViewById<RadioButton>(R.id.rb_http)
        val rbSocks = view.findViewById<RadioButton>(R.id.rb_socks)
        val etHost = view.findViewById<EditText>(R.id.et_proxy_host)
        val etPort = view.findViewById<EditText>(R.id.et_proxy_port)
        val spPreset = view.findViewById<Spinner>(R.id.sp_preset)

        var working = DeviceProfile.get(userId).copy()

        // Um EditText (com rótulo) por campo
        for (f in fields) {
            val label = TextView(context).apply {
                text = f.label
                textSize = 11f
                setPadding(0, 12, 0, 0)
            }
            val et = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                isSingleLine = f.label != "Fingerprint"
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            f.edit = et
            ll.addView(label, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            ll.addView(et, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun fill() {
            for (f in fields) f.edit.setText(f.get(working))
        }
        fill()

        val options = listOf(context.getString(R.string.identity_random)) + DevicePresets.names().toList()
        spPreset.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
        spPreset.setSelection(options.indexOf(working.presetName).coerceAtLeast(0))

        rbSocks.isChecked = working.proxyType.equals("socks", true)
        rbHttp.isChecked = !rbSocks.isChecked
        etHost.setText(working.proxyHost)
        if (working.proxyPort > 0) etPort.setText(working.proxyPort.toString())

        btnRegen.setOnClickListener {
            val sel = spPreset.selectedItemPosition
            working = DeviceProfile.newRandom(working, if (sel <= 0) null else options[sel])
            fill() // descarta edições manuais em andamento
        }

        MaterialDialog(context).show {
            title(text = context.getString(R.string.identity_title, userId))
            customView(view = view, scrollable = false)
            positiveButton(R.string.identity_save) {
                // 1) valida e aplica os campos digitados
                val edited = working.copy()
                for (f in fields) {
                    val v = f.edit.text.toString().trim()
                    if (f.validate != null && !f.validate.invoke(v)) {
                        Toast.makeText(context, context.getString(R.string.identity_invalid_field, f.label), Toast.LENGTH_LONG).show()
                        return@positiveButton
                    }
                    f.set(edited, v)
                }
                if (!luhnOk(edited.imei) || !luhnOk(edited.imei2)) {
                    Toast.makeText(context, R.string.identity_luhn_warn, Toast.LENGTH_LONG).show()
                }
                // Mantém o nome do preset apenas se ainda coincide com o modelo; senão marca como manual
                edited.presetName = DevicePresets.ALL.firstOrNull {
                    it.name == working.presetName && it.model == edited.model
                }?.name ?: ""

                // 2) proxy
                val host = etHost.text.toString().trim()
                val portStr = etPort.text.toString().trim()
                if (host.isEmpty() && portStr.isEmpty()) {
                    edited.proxyHost = ""
                    edited.proxyPort = 0
                } else {
                    val port = portStr.toIntOrNull() ?: 0
                    if (host.isEmpty() || port !in 1..65535) {
                        Toast.makeText(context, R.string.identity_invalid_proxy, Toast.LENGTH_LONG).show()
                        return@positiveButton
                    }
                    edited.proxyHost = host
                    edited.proxyPort = port
                }
                edited.proxyType = if (rbSocks.isChecked) "socks" else "http"

                // 3) grava e para os apps da instância (processos antigos têm o perfil em cache)
                DeviceProfile.update(userId, edited)
                Thread {
                    try {
                        BlackBoxCore.get().getInstalledApplications(0, userId)?.forEach {
                            BlackBoxCore.get().stopPackage(it.packageName, userId)
                        }
                    } catch (_: Throwable) {
                    }
                }.start()
                Toast.makeText(context, R.string.identity_saved, Toast.LENGTH_LONG).show()
            }
            negativeButton(R.string.cancel)
        }
    }
}
