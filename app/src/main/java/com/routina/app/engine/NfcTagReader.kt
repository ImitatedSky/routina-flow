package com.routina.app.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.IOException
import java.nio.charset.Charset

/**
 * NFC 標籤的共用讀取與寫入邏輯。
 *
 * 三條路徑都走這裡：編輯畫面的「掃描標籤」對話框（reader mode，App 在前景）、
 * 同一畫面的「寫入標籤」對話框，以及背景掃描（系統 dispatch 到 [NfcDispatchActivity]）。
 * 比對依據一律是標籤的硬體 UID；寫入只是把 [tagUri] 這個專屬 URI 放進標籤，
 * 讓系統掃到時能精準比對到 Routina（不出現 App 選擇器），比對規則本身不變。
 */
object NfcTagReader {

    /** 寫入標籤的專屬 URI scheme／host（與 manifest 的 NDEF intent-filter 一致） */
    const val URI_SCHEME = "routina"
    const val URI_HOST = "tag"

    /**
     * reader mode 的共同旗標。
     *
     * 四種基本技術全開才能涵蓋市面上的一般標籤；關掉系統掃描音效是因為對話框自己會回饋。
     */
    private const val BASE_READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V or
        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

    /**
     * 掃描登錄用：跳過 NDEF 檢查，讓「只要 UID」的讀取快一點
     * （也避免碰到損壞的 NDEF 內容而失敗）。
     */
    private const val SCAN_FLAGS = BASE_READER_FLAGS or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

    /**
     * 寫入用：**不能**跳過 NDEF 檢查——系統得先做完 NDEF 檢查，
     * [Ndef.get] / [NdefFormatable.get] 才拿得到對應的 tech 物件。
     */
    private const val WRITE_FLAGS = BASE_READER_FLAGS

    /** 裝置是否有 NFC 硬體。沒有時調色盤把 NFC 積木標示為不可用（沿用無 GMS 的做法）。 */
    fun isAvailable(context: Context): Boolean = adapter(context) != null

    /** NFC 是否已開啟。有硬體但關著時掃描不會發生，首頁顯示引導卡。 */
    fun isEnabled(context: Context): Boolean = adapter(context)?.isEnabled == true

    /**
     * 開啟 reader mode，掃到標籤時以 UID 回呼（保證在主執行緒）。
     * 只在掃描對話框開著的期間啟用，關閉時務必呼叫 [disableReaderMode]。
     *
     * @return 是否成功啟用
     */
    fun enableReaderMode(activity: Activity, onTag: (String) -> Unit): Boolean =
        enable(activity, SCAN_FLAGS) { tag ->
            uidOf(tag)?.let { uid -> onMain { onTag(uid) } }
        }

    /**
     * 開啟寫入模式：偵測到標籤就地寫入專屬 URI，結果以 [WriteOutcome] 回呼
     * （保證在主執行緒）。與 [enableReaderMode] 一樣綁在對話框的生命週期上，
     * 同一個 Activity 同時只能有一種 reader mode，因此兩個對話框必須互相讓位。
     *
     * @return 是否成功啟用
     */
    fun enableWriteMode(activity: Activity, onResult: (WriteOutcome) -> Unit): Boolean =
        enable(activity, WRITE_FLAGS) { tag ->
            // 回呼來自系統的 binder 執行緒——標籤 I/O（connect / write）不能在主執行緒做，
            // 所以就在這裡寫完，只把結果 post 回主執行緒
            val outcome = writeTagUri(tag)
            onMain { onResult(outcome) }
        }

    /**
     * 開啟「完整讀取」模式（NFC 標籤庫登錄用）：讀 UID 加整份 NDEF 內容，結果以
     * [ReadResult] 回呼（保證在主執行緒）。與登錄觸發只要 UID 不同，這裡**不能**跳過
     * NDEF 檢查（用 [WRITE_FLAGS]），否則 [Ndef.get] 拿不到 NDEF。
     *
     * @return 是否成功啟用
     */
    fun enableReadFullMode(activity: Activity, onRead: (ReadResult) -> Unit): Boolean =
        enable(activity, WRITE_FLAGS) { tag ->
            // 標籤 I/O 不能在主執行緒，就在 binder 執行緒讀完，只把結果 post 回主執行緒
            val result = readTag(tag)
            onMain { onRead(result) }
        }

    /**
     * 開啟寫入模式：把記錄的 NDEF 內容（[ndefBase64]）原樣寫進另一張可寫標籤（＝複製資料）。
     * 結果以 [WriteOutcome] 回呼（保證在主執行緒）。與 [enableReaderMode] 一樣綁在對話框的
     * 生命週期上，同一個 Activity 同時只能有一種 reader mode。
     *
     * @return 是否成功啟用
     */
    fun enableWriteNdefMode(
        activity: Activity,
        ndefBase64: String,
        onResult: (WriteOutcome) -> Unit
    ): Boolean {
        val bytes = runCatching { Base64.decode(ndefBase64, Base64.NO_WRAP) }.getOrNull()
        return enable(activity, WRITE_FLAGS) { tag ->
            val outcome = writeTagMessage(tag, bytes)
            onMain { onResult(outcome) }
        }
    }

    /**
     * 開啟「卡片探測」模式（實驗性）：讀一張卡的技術特徵並判讀能否複製，結果以 [CardInfo]
     * 回呼（保證在主執行緒）。用 [BASE_READER_FLAGS]（**不**跳過 NDEF 檢查，才認得出 NDEF 標籤）。
     *
     * 只做「讀取／辨識」，不能讓手機模擬（變成）這張卡——那需要安全元件(SE)，只有 OEM 錢包
     * App 拿得到。
     *
     * @return 是否成功啟用
     */
    fun enableInspectMode(activity: Activity, onResult: (CardInfo) -> Unit): Boolean =
        enable(activity, BASE_READER_FLAGS) { tag ->
            // 回呼來自 binder 執行緒——MIFARE 驗證是 I/O，就在這裡做完，只把結果 post 回主執行緒
            val info = readInfo(tag)
            onMain { onResult(info) }
        }

    fun disableReaderMode(activity: Activity) {
        runCatching { adapter(activity)?.disableReaderMode(activity) }
    }

    /**
     * 系統 dispatch 的 intent 內夾帶的標籤 UID；讀不到時為 null。
     *
     * **以硬體 [NfcAdapter.EXTRA_TAG] 為準**：必須 intent 真的帶著一個 Tag 才採信，
     * 否則直接回傳 null。這樣可以擋掉本機 App 偽造一個只有 `routina://tag/{uid}` data URI、
     * 卻沒有真實 NFC 硬體佐證的假 intent 來觸發使用者的例行程序。
     *
     * 正常情況兩種標籤都帶 EXTRA_TAG：一般標籤走 TECH_DISCOVERED、寫入過專屬 URI 的標籤
     * 走 NDEF_DISCOVERED（系統仍會夾帶 Tag），因此以 Tag 為主不影響既有的寫入標籤流程。
     * data URI 只在「確有 Tag、但硬體 UID 罕見地讀不到」時，作為有佐證的 fallback。
     */
    fun uidFrom(intent: Intent?): String? {
        // 沒有真實 Tag（可能是偽造的純 data-URI intent）→ 不採信，不觸發任何程序
        val tag = tagOf(intent) ?: return null
        return uidOf(tag) ?: uidFromUri(intent?.data)
    }

    /** `routina://tag/{uid}` 的 uid；不是本 App 的 URI 時為 null */
    fun uidFromUri(uri: Uri?): String? {
        if (uri == null) return null
        if (!URI_SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        if (!URI_HOST.equals(uri.host, ignoreCase = true)) return null
        return uri.lastPathSegment?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 寫入標籤的專屬 URI */
    fun tagUri(uid: String): String = "$URI_SCHEME://$URI_HOST/$uid"

    /** 標籤 UID 的 hex 字串（大寫、無分隔）；讀不到時為 null */
    fun uidOf(tag: Tag?): String? {
        val id = tag?.id ?: return null
        if (id.isEmpty()) return null
        return id.joinToString("") { "%02X".format(it) }
    }

    /** UID 比對：忽略大小寫與前後空白；空字串（尚未登錄）永不相符 */
    fun matches(registered: String, scanned: String): Boolean {
        val expected = registered.trim()
        return expected.isNotEmpty() && expected.equals(scanned.trim(), ignoreCase = true)
    }

    /**
     * 一次寫入的結果。
     *
     * [uid] 只要讀到標籤就有值——**即使寫入失敗，UID 登錄仍應完成**
     * （標籤還是可以用一般的掃描方式觸發，只是會經過系統的 App 選擇器）。
     * [error] 為 null 代表寫入成功，否則是可以直接顯示給使用者的原因。
     */
    data class WriteOutcome(val uid: String?, val error: String?) {
        val success: Boolean get() = uid != null && error == null
    }

    /**
     * 一次完整讀取的結果（NFC 標籤庫登錄用）。
     *
     * [uid] 讀不到時為 null；[ndefBase64] 與 [summary] 在標籤沒有 NDEF 內容時皆為空字串。
     */
    data class ReadResult(val uid: String?, val ndefBase64: String, val summary: String)

    /**
     * 一次卡片探測的結果：把一張卡的技術特徵與「能不能複製」的誠實判讀整理在一起。
     *
     * null 代表該欄位對這張卡不適用或讀不到（例如非 MIFARE Classic 卡沒有 [sectorCount]）。
     * [verdict] 是給人看的判讀文字（見 [buildVerdict]）。
     */
    data class CardInfo(
        val uid: String?,
        val techList: List<String>,
        val atqa: String?,
        val sak: String?,
        val mifareType: String?,
        val sectorCount: Int?,
        val readableSectors: Int?,
        val hasIsoDep: Boolean,
        val hasNdef: Boolean,
        val verdict: String
    )

    /**
     * 讀 UID 加整份 NDEF 內容。所有標籤 I/O 都包在 runCatching 內：
     * 讀到一半移開標籤只會得到一個空內容的結果，不會讓 App 崩潰。
     */
    private fun readTag(tag: Tag): ReadResult {
        val uid = uidOf(tag)
        val msg = runCatching {
            val ndef = Ndef.get(tag) ?: return@runCatching null
            try {
                ndef.connect()
                ndef.ndefMessage
            } finally {
                runCatching { ndef.close() }
            }
        }.getOrNull()
        return if (msg != null) {
            ReadResult(
                uid = uid,
                ndefBase64 = Base64.encodeToString(msg.toByteArray(), Base64.NO_WRAP),
                summary = summarizeNdef(msg)
            )
        } else {
            ReadResult(uid = uid, ndefBase64 = "", summary = "")
        }
    }

    /**
     * 讀出卡片的技術特徵：UID、tech 清單、ATQA/SAK、MIFARE 類型與可用預設金鑰讀到的磁區數，
     * 最後給一段誠實的判讀。所有卡片 I/O 都包在 runCatching 內：讀到一半移開卡片不會讓 App 崩潰。
     */
    private fun readInfo(tag: Tag): CardInfo {
        val uid = uidOf(tag)
        val techList = tag.techList.map { it.substringAfterLast('.') } // 例：NfcA、MifareClassic、IsoDep、Ndef
        val hasIsoDep = "IsoDep" in techList
        val hasNdef = "Ndef" in techList

        // ATQA／SAK 不用 connect 就拿得到（NfcA 的 low-level 屬性），不佔用連線
        var atqa: String? = null
        var sak: String? = null
        if ("NfcA" in techList) {
            runCatching {
                NfcA.get(tag)?.let {
                    atqa = bytesToHex(it.atqa)
                    sak = "%02X".format(it.sak)
                }
            }
        }

        var mifareType: String? = null
        var sectorCount: Int? = null
        var readableSectors: Int? = null
        if ("MifareClassic" in techList) {
            val mc = runCatching { MifareClassic.get(tag) }.getOrNull()
            if (mc != null) {
                mifareType = when (mc.type) {
                    MifareClassic.TYPE_CLASSIC -> "MIFARE Classic"
                    MifareClassic.TYPE_PLUS -> "MIFARE Plus"
                    MifareClassic.TYPE_PRO -> "MIFARE Pro"
                    else -> "未知"
                }
                sectorCount = mc.sectorCount
                // connect + 逐磁區試預設金鑰都包在 runCatching：移開卡片只會拿到 null，不崩潰
                readableSectors = runCatching {
                    mc.connect()
                    try {
                        countReadableSectors(mc)
                    } finally {
                        runCatching { mc.close() }
                    }
                }.getOrNull()
            }
        }

        val verdict = buildVerdict(
            mifarePresent = mifareType != null,
            sectorCount = sectorCount,
            readableSectors = readableSectors,
            hasIsoDep = hasIsoDep,
            hasNdef = hasNdef
        )

        return CardInfo(
            uid = uid,
            techList = techList,
            atqa = atqa,
            sak = sak,
            mifareType = mifareType,
            sectorCount = sectorCount,
            readableSectors = readableSectors,
            hasIsoDep = hasIsoDep,
            hasNdef = hasNdef,
            verdict = verdict
        )
    }

    /** 逐磁區用預設金鑰清單試 KeyA／KeyB，回傳任一把金鑰能驗證成功的磁區數 */
    private fun countReadableSectors(mc: MifareClassic): Int {
        var readable = 0
        for (s in 0 until mc.sectorCount) {
            val ok = DEFAULT_KEYS.any { key ->
                runCatching { mc.authenticateSectorWithKeyA(s, key) }.getOrDefault(false) ||
                    runCatching { mc.authenticateSectorWithKeyB(s, key) }.getOrDefault(false)
            }
            if (ok) readable++
        }
        return readable
    }

    /**
     * 依技術特徵給一段誠實的「能不能複製」判讀。
     * 永遠附上安全元件(SE)的但書：手機通常無法把門禁卡變成手機刷。
     */
    private fun buildVerdict(
        mifarePresent: Boolean,
        sectorCount: Int?,
        readableSectors: Int?,
        hasIsoDep: Boolean,
        hasNdef: Boolean
    ): String {
        val body = when {
            mifarePresent -> {
                val sectors = sectorCount ?: 0
                val readable = readableSectors ?: 0
                when {
                    sectors > 0 && readable == sectors ->
                        "MIFARE Classic:所有磁區都能用預設金鑰讀取＝弱加密。可用 magic UID 白卡 + " +
                            "MIFARE Classic Tool 複製到實體卡,或部分國行手機的內建門卡功能。" +
                            "但第三方 App 無法讓手機直接變成這張卡。"

                    readable in 1 until sectors ->
                        "MIFARE Classic:部分磁區可讀、部分是自訂金鑰。" +
                            "要完整複製需要那些金鑰(手機一般拿不到)。"

                    else ->
                        "MIFARE Classic:所有磁區都是自訂金鑰,讀不到,無法複製(除非有金鑰)。"
                }
            }

            hasIsoDep ->
                "疑似 DESFire / 其他加密卡(ISO-DEP):強加密,手機與一般工具都無法複製。"

            hasNdef ->
                "NDEF 資料標籤:內容可用『NFC 標籤庫 → 寫到新標籤』複製到另一張標籤" +
                    "(但門禁多半不是這種)。"

            else ->
                "只認 UID 的簡單卡或未知類型:UID 無法寫到一般卡,需 magic UID 白卡才能複製 UID;" +
                    "手機也無法模擬任意 UID。"
        }
        return body + "\n\n註:除了小米/華為等國行手機的內建門卡功能,手機通常無法把門禁卡變成手機刷" +
            "——這是安全元件(SE)的硬體限制,不是 App 做不做得到的問題。"
    }

    /** 位元組陣列轉大寫 hex 字串（無分隔）；空陣列回 null */
    private fun bytesToHex(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return bytes.joinToString("") { "%02X".format(it) }
    }

    /** 把偶數長度的 hex 字串轉成位元組陣列（給預設金鑰清單用） */
    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    /** MIFARE Classic 常見的出廠／通用預設金鑰；能用這些讀到＝弱加密 */
    private val DEFAULT_KEYS: List<ByteArray> = listOf(
        MifareClassic.KEY_DEFAULT,
        MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
        MifareClassic.KEY_NFC_FORUM,
        hexToBytes("000000000000"),
        hexToBytes("A0B0C0D0E0F0"),
        hexToBytes("B0B1B2B3B4B5"),
        hexToBytes("4D3A99C351DD"),
        hexToBytes("1A982C7E459A"),
        hexToBytes("D3F7D3F7D3F7"),
        hexToBytes("AABBCCDDEEFF")
    )

    /** 把 NDEF 內容整理成給人看的摘要（網址／文字／MIME 類型…） */
    private fun summarizeNdef(msg: NdefMessage): String {
        val parts = msg.records.mapNotNull { record ->
            val uri = runCatching { record.toUri() }.getOrNull()
            when {
                uri != null -> uri.toString()
                record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                    record.type.contentEquals(NdefRecord.RTD_TEXT) -> decodeText(record.payload)

                record.tnf == NdefRecord.TNF_MIME_MEDIA ->
                    runCatching { record.toMimeType() }.getOrNull()

                else -> null
            }?.takeIf { it.isNotBlank() }
        }
        val joined = parts.joinToString(" · ").take(160)
        return joined.ifBlank { "（無法辨識的內容或空白標籤）" }
    }

    /** 解碼 RTD_TEXT 的 payload：首位是狀態位元組，低 6 位為語言碼長度，最高位決定編碼 */
    private fun decodeText(payload: ByteArray): String? = runCatching {
        if (payload.isEmpty()) return@runCatching null
        val status = payload[0].toInt()
        val langLength = status and 0x3F
        val charset: Charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        String(payload, 1 + langLength, payload.size - 1 - langLength, charset)
    }.getOrNull()

    /**
     * 把 `routina://tag/{uid}` 的 NDEF URI 寫進標籤。
     *
     * 已格式化的標籤走 [Ndef]（檢查可寫與容量），未格式化但支援的標籤走
     * [NdefFormatable]（格式化的同時寫入），兩者皆不支援就誠實說不支援。
     * 所有標籤 I/O 都包在 try/catch/finally 內：寫到一半移開標籤只會得到
     * 一句「再試一次」的提示，不會讓 App 崩潰。
     */
    private fun writeTagUri(tag: Tag): WriteOutcome {
        val uid = uidOf(tag) ?: return WriteOutcome(null, ERROR_UNREADABLE)
        val message = runCatching {
            NdefMessage(NdefRecord.createUri(tagUri(uid)))
        }.getOrNull() ?: return WriteOutcome(uid, ERROR_GENERIC)

        val ndef = runCatching { Ndef.get(tag) }.getOrNull()
        if (ndef != null) return WriteOutcome(uid, writeNdef(ndef, message))

        val formatable = runCatching { NdefFormatable.get(tag) }.getOrNull()
        if (formatable != null) return WriteOutcome(uid, formatNdef(formatable, message))

        return WriteOutcome(uid, ERROR_UNSUPPORTED)
    }

    /**
     * 把記錄的 NDEF 內容（已解碼的位元組）原樣寫進另一張標籤（＝複製資料）。
     * 結構同 [writeTagUri]，差別只在訊息來自記錄而非新建的專屬 URI。
     */
    private fun writeTagMessage(tag: Tag, bytes: ByteArray?): WriteOutcome {
        val uid = uidOf(tag)
        if (bytes == null || bytes.isEmpty()) return WriteOutcome(uid, ERROR_NO_CONTENT)
        val message = runCatching { NdefMessage(bytes) }.getOrNull()
            ?: return WriteOutcome(uid, ERROR_GENERIC)

        val ndef = runCatching { Ndef.get(tag) }.getOrNull()
        if (ndef != null) return WriteOutcome(uid, writeNdef(ndef, message))

        val formatable = runCatching { NdefFormatable.get(tag) }.getOrNull()
        if (formatable != null) return WriteOutcome(uid, formatNdef(formatable, message))

        return WriteOutcome(uid, ERROR_UNSUPPORTED)
    }

    /** @return null 代表寫入成功，否則為失敗原因 */
    private fun writeNdef(ndef: Ndef, message: NdefMessage): String? = try {
        ndef.connect()
        val size = message.toByteArray().size
        when {
            !ndef.isWritable -> ERROR_READ_ONLY
            ndef.maxSize < size -> "標籤容量不足（需要 $size 位元組，可用 ${ndef.maxSize}）"
            else -> {
                ndef.writeNdefMessage(message)
                null
            }
        }
    } catch (t: IOException) {
        // TagLostException 也是 IOException：寫到一半把標籤移開就是這一條
        ERROR_MOVED
    } catch (t: Throwable) {
        "寫入失敗：${t.message ?: t.javaClass.simpleName}"
    } finally {
        runCatching { ndef.close() }
    }

    /** 未格式化的標籤：格式化的同時寫入。@return null 代表成功 */
    private fun formatNdef(formatable: NdefFormatable, message: NdefMessage): String? = try {
        formatable.connect()
        formatable.format(message)
        null
    } catch (t: IOException) {
        ERROR_MOVED
    } catch (t: Throwable) {
        "格式化失敗：${t.message ?: t.javaClass.simpleName}"
    } finally {
        runCatching { formatable.close() }
    }

    private fun enable(
        activity: Activity,
        flags: Int,
        onTag: (Tag) -> Unit
    ): Boolean {
        val adapter = adapter(activity) ?: return false
        return runCatching {
            adapter.enableReaderMode(activity, { tag -> onTag(tag) }, flags, null)
        }.isSuccess
    }

    /** 系統的回呼來自 binder 執行緒，UI 狀態必須回到主執行緒才動 */
    private fun onMain(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    private fun adapter(context: Context): NfcAdapter? =
        runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()

    private fun tagOf(intent: Intent?): Tag? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag
        }
    }.getOrNull()

    private const val ERROR_UNREADABLE = "讀不到標籤的識別碼，請再試一次"
    private const val ERROR_READ_ONLY = "標籤唯讀，無法寫入"
    private const val ERROR_UNSUPPORTED = "此標籤不支援寫入"
    private const val ERROR_MOVED = "請保持標籤貼緊再試一次"
    private const val ERROR_GENERIC = "無法建立寫入內容"
    private const val ERROR_NO_CONTENT = "這張記錄沒有可寫入的 NDEF 內容"
}
