package com.routina.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

/**
 * Routina 家族的連結層：找出同家族的其他 App、讀它們開放的能力、組出呼叫的 Intent。
 *
 * **這是家族契約在 Flow 這一側的實作。** 契約的權威定義在 Hub 的
 * `core/contract/Family.kt`（routina-hub repo），本檔實作的是 [CONTRACT_VERSION] 那一版。
 *
 * 為什麼是一份各自的實作、而不是共用 library：Flow 刻意留在自己的 repo、用自己的版本節奏，
 * 而契約本身只是 manifest 鍵名與 Intent extras —— 字串層級的約定，不需要程式碼依賴。
 * 代價是兩邊都要維護一份；契約版本號就是為此存在：改動格式時 [CONTRACT_VERSION] +1，
 * 兩側都得跟上。
 *
 * 所有對外查詢都包在 runCatching 裡：讀的是別的 App 的資料，對方可能正在被更新、
 * 被停用、或 manifest 寫錯，任何一個成員壞掉都不該讓整份清單讀不出來。
 */
object FamilyLink {

    const val CONTRACT_VERSION = 1

    private const val META_MEMBER = "com.routina.family.member"
    private const val META_ID = "com.routina.family.id"
    private const val META_NAME = "com.routina.family.name"
    private const val META_SUMMARY = "com.routina.family.summary"
    private const val META_CAPABILITIES = "com.routina.family.capabilities"

    const val ACTION_RUN_CAPABILITY = "com.routina.family.action.RUN_CAPABILITY"
    const val EXTRA_CAPABILITY_ID = "com.routina.family.extra.CAPABILITY_ID"
    const val EXTRA_PARAM_PREFIX = "com.routina.family.param."

    /** 家族裡的一個成員 App */
    data class Member(
        val packageName: String,
        val familyId: String,
        val name: String,
        val summary: String,
        val capabilities: List<Capability>
    )

    /** 成員開放的一個能力 */
    data class Capability(
        val id: String,
        val label: String,
        val summary: String,
        val params: List<Param>
    )

    /** 能力的一個輸入參數。值傳過去一律是字串（可能來自變數代換） */
    data class Param(
        val name: String,
        val label: String,
        val isNumber: Boolean,
        val required: Boolean
    )

    /**
     * 掃出裝置上的其他家族成員，依名稱排序。
     *
     * 靠「有啟動圖示的 App」＋家族標記來找，不需要 QUERY_ALL_PACKAGES；
     * 套件可見性由 manifest 既有的 `<queries>` MAIN/LAUNCHER 宣告涵蓋。
     * 一律排除 Flow 自己。
     */
    fun members(context: Context): List<Member> {
        val pm = context.packageManager
        val self = context.packageName

        val packages = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
        }.getOrDefault(emptyList())

        return packages
            .filter { it != self }
            .mapNotNull { read(pm, it) }
            .sortedBy { it.name }
    }

    /** 只回有開放能力的成員：挑「要呼叫誰」時，沒有能力的成員選了也沒用 */
    fun callableMembers(context: Context): List<Member> =
        members(context).filter { it.capabilities.isNotEmpty() }

    /**
     * 組一個呼叫 [packageName] 的 [capabilityId] 的 Intent。
     *
     * setPackage：只會解析到那一個 App，不跳選擇器、也不會被別人攔截。
     * NEW_TASK：呼叫方是背景服務（ExecutionService），沒有 Activity 堆疊可用。
     */
    fun buildIntent(
        packageName: String,
        capabilityId: String,
        params: Map<String, String>
    ): Intent = Intent(ACTION_RUN_CAPABILITY).apply {
        setPackage(packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(EXTRA_CAPABILITY_ID, capabilityId)
        params.forEach { (name, value) -> putExtra(EXTRA_PARAM_PREFIX + name, value) }
    }

    /** 被呼叫時用：這次要執行哪個能力 */
    fun capabilityId(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_CAPABILITY_ID)?.takeIf { it.isNotBlank() }

    /** 被呼叫時用：取出某個參數的值 */
    fun param(intent: Intent?, name: String): String? =
        intent?.getStringExtra(EXTRA_PARAM_PREFIX + name)?.takeIf { it.isNotBlank() }

    private fun read(pm: PackageManager, packageName: String): Member? = runCatching {
        @Suppress("DEPRECATION")
        val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val meta = appInfo.metaData ?: return null
        if (!meta.getBoolean(META_MEMBER, false)) return null

        val label = runCatching {
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)

        val capabilitiesResId = meta.getInt(META_CAPABILITIES, 0)

        Member(
            packageName = packageName,
            familyId = meta.getString(META_ID)?.takeIf { it.isNotBlank() } ?: packageName,
            name = meta.getString(META_NAME)?.takeIf { it.isNotBlank() } ?: label,
            summary = meta.getString(META_SUMMARY).orEmpty(),
            capabilities = if (capabilitiesResId == 0) {
                emptyList()
            } else {
                readCapabilities(pm, appInfo, capabilitiesResId)
            }
        )
    }.getOrNull()

    /**
     * 讀對方 APK 裡的能力清單 XML。屬性不帶 android: 命名空間
     * （與 FileProvider 的 file_paths.xml 同樣作法）。
     */
    private fun readCapabilities(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        resId: Int
    ): List<Capability> = runCatching {
        val res = pm.getResourcesForApplication(appInfo)
        val parser = res.getXml(resId)
        try {
            parse(parser, res)
        } finally {
            parser.close()
        }
    }.getOrDefault(emptyList())

    private fun parse(parser: XmlResourceParser, res: Resources): List<Capability> {
        val capabilities = mutableListOf<Capability>()

        // 邊掃邊累積：遇到 capability 開一筆、遇到 param 往當前這筆加、收尾時收一筆。
        // 缺 id 的整筆丟掉（沒有 id 就無法被呼叫）。
        var id: String? = null
        var label: String? = null
        var summary = ""
        var params = mutableListOf<Param>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "capability" -> {
                        id = attr(parser, res, "id")
                        label = attr(parser, res, "label")
                        summary = attr(parser, res, "summary").orEmpty()
                        params = mutableListOf()
                    }

                    "param" -> {
                        val name = attr(parser, res, "name")
                        if (!name.isNullOrBlank()) {
                            params.add(
                                Param(
                                    name = name,
                                    label = attr(parser, res, "label")
                                        ?.takeIf { it.isNotBlank() } ?: name,
                                    isNumber = attr(parser, res, "type")
                                        ?.lowercase() == "number",
                                    required = parser.getAttributeBooleanValue(
                                        null,
                                        "required",
                                        false
                                    )
                                )
                            )
                        }
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == "capability") {
                    val capabilityId = id
                    if (!capabilityId.isNullOrBlank()) {
                        capabilities.add(
                            Capability(
                                id = capabilityId,
                                label = label?.takeIf { it.isNotBlank() } ?: capabilityId,
                                summary = summary,
                                params = params.toList()
                            )
                        )
                    }
                    id = null
                }
            }
            event = parser.next()
        }
        return capabilities
    }

    /** 取屬性值，同時支援字面文字與字串資源參照（AAPT 會把後者編成 id） */
    private fun attr(parser: XmlResourceParser, res: Resources, name: String): String? {
        val resId = parser.getAttributeResourceValue(null, name, 0)
        if (resId != 0) {
            return runCatching { res.getString(resId) }.getOrNull()
        }
        return parser.getAttributeValue(null, name)
    }
}
