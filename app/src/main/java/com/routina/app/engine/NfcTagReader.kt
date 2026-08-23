package com.routina.app.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.IOException

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
}
