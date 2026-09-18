package com.routina.app.data

import android.content.Context
import com.routina.app.model.GlobalVar
import com.routina.app.model.NfcRecord
import com.routina.app.model.Routine
import com.routina.app.model.RunLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 例行程序與執行紀錄的本地儲存。
 *
 * - 以 filesDir 下的 JSON 檔持久化（routines.json / logs.json），不使用資料庫、不連網
 * - 以 StateFlow 對外提供資料，UI 直接 collect
 * - 檔案損毀無法解析時以空清單啟動，不崩潰
 */
class RoutineRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val routinesFile: File get() = File(appContext.filesDir, FILE_ROUTINES)
    private val logsFile: File get() = File(appContext.filesDir, FILE_LOGS)
    private val nfcFile: File get() = File(appContext.filesDir, FILE_NFC)
    private val globalsFile: File get() = File(appContext.filesDir, FILE_GLOBALS)

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    private val _logs = MutableStateFlow<List<RunLog>>(emptyList())
    val logs: StateFlow<List<RunLog>> = _logs.asStateFlow()

    private val _nfcTags = MutableStateFlow<List<NfcRecord>>(emptyList())
    val nfcTags: StateFlow<List<NfcRecord>> = _nfcTags.asStateFlow()

    private val _globals = MutableStateFlow<List<GlobalVar>>(emptyList())
    val globals: StateFlow<List<GlobalVar>> = _globals.asStateFlow()

    init {
        // 資料量小（數十筆），初始化時同步載入，讓 UI 與背景元件第一幀就有正確資料
        _routines.value = readList(routinesFile, ListSerializer(Routine.serializer()))
        _logs.value = readList(logsFile, ListSerializer(RunLog.serializer()))
        _nfcTags.value = readList(nfcFile, ListSerializer(NfcRecord.serializer()))
        _globals.value = readList(globalsFile, ListSerializer(GlobalVar.serializer()))
    }

    // ---------- 讀取 ----------

    fun findById(id: String): Routine? = _routines.value.firstOrNull { it.id == id }

    /** 啟用中且觸發類型需要監測服務的例行程序 */
    fun enabledRoutines(): List<Routine> = _routines.value.filter { it.enabled }

    private fun <T> readList(file: File, serializer: KSerializer<List<T>>): List<T> = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    } catch (t: Throwable) {
        // 檔案損毀 / 格式不符 → 以空清單啟動
        emptyList()
    }

    // ---------- 例行程序 CRUD ----------

    fun upsert(routine: Routine) {
        val current = _routines.value
        val index = current.indexOfFirst { it.id == routine.id }
        _routines.value = if (index >= 0) {
            current.toMutableList().apply { this[index] = routine }
        } else {
            current + routine
        }
        persistRoutines()
    }

    fun delete(id: String) {
        _routines.value = _routines.value.filterNot { it.id == id }
        persistRoutines()
    }

    /** 調整清單先後：把 [from] 位置的元素移到 [to] 位置後持久化（順序＝list 順序） */
    fun reorder(from: Int, to: Int) {
        val current = _routines.value
        if (from !in current.indices || to !in current.indices || from == to) return
        _routines.value = current.toMutableList().apply { add(to, removeAt(from)) }
        persistRoutines()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val target = findById(id) ?: return
        upsert(target.copy(enabled = enabled))
    }

    /**
     * 觸發實際執行一次後把該 routine 的 [Routine.runCount] +1 並落地。
     * [blocking] 為 true（背景執行後行程可能立即結束）時同步寫檔，確保次數不會漏記而導致超額觸發。
     */
    fun incrementRunCount(id: String, blocking: Boolean) {
        val current = _routines.value
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return
        val updated = current[idx].copy(runCount = current[idx].runCount + 1)
        _routines.value = current.toMutableList().apply { this[idx] = updated }
        if (blocking) {
            runBlocking { writeMutex.withLock { writeAtomically(routinesFile, encodeRoutines(_routines.value)) } }
        } else {
            persistRoutines()
        }
    }

    // ---------- 執行紀錄 ----------

    fun addLog(log: RunLog) {
        _logs.value = (listOf(log) + _logs.value).take(AppSettings.logLimit)
        persistLogs()
    }

    fun clearLogs() {
        _logs.value = emptyList()
        persistLogs()
    }

    /** 設定把保留筆數調小後呼叫：立刻裁掉多出來的舊紀錄，不必等下一次執行 */
    fun trimLogs() {
        val limit = AppSettings.logLimit
        if (_logs.value.size <= limit) return
        _logs.value = _logs.value.take(limit)
        persistLogs()
    }

    // ---------- NFC 標籤庫 ----------

    fun upsertNfc(record: NfcRecord) {
        val current = _nfcTags.value
        val index = current.indexOfFirst { it.id == record.id }
        _nfcTags.value = if (index >= 0) {
            current.toMutableList().apply { this[index] = record }
        } else {
            current + record
        }
        persistNfc()
    }

    fun deleteNfc(id: String) {
        _nfcTags.value = _nfcTags.value.filterNot { it.id == id }
        persistNfc()
    }

    // ---------- 全域變數（跨程序、可持久化）----------

    /** 目前全域變數的名稱→值對照，供執行開始時載入 [com.routina.app.engine.RunContext] */
    fun globalsSnapshot(): Map<String, String> =
        _globals.value.associate { it.name to it.value }

    /**
     * 把一次執行寫入的全域變數（[changed]：名稱→值）合併落地。
     *
     * 只更新有異動的鍵、保留其餘，避免兩個程序同時執行時互相覆蓋。空名稱略過。
     * [blocking] 為 true（背景執行後行程可能立即結束）時同步寫檔確保落地，否則交背景寫入。
     */
    fun applyGlobals(changed: Map<String, String>, blocking: Boolean) {
        val clean = changed.mapKeys { it.key.trim() }.filterKeys { it.isNotEmpty() }
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val merged = _globals.value.associateBy { it.name }.toMutableMap()
        clean.forEach { (name, value) -> merged[name] = GlobalVar(name, value, now) }
        _globals.value = merged.values.sortedBy { it.name }
        if (blocking) {
            runBlocking { writeMutex.withLock { writeAtomically(globalsFile, encodeGlobals(_globals.value)) } }
        } else {
            persistGlobals()
        }
    }

    /** 管理畫面手動新增／更新一個全域變數（非同步落地） */
    fun setGlobal(name: String, value: String) = applyGlobals(mapOf(name to value), blocking = false)

    fun deleteGlobal(name: String) {
        _globals.value = _globals.value.filterNot { it.name == name }
        persistGlobals()
    }

    // ---------- 寫入 ----------

    /**
     * 落地目前狀態。
     *
     * 快照必須在取得鎖之後才讀取：若在鎖外先取快照，兩次連續異動可能以相反順序寫入，
     * 讓較舊的內容覆蓋較新的內容。在鎖內讀取可保證最後寫入的一定是最新狀態。
     */
    private fun persistRoutines() {
        scope.launch {
            writeMutex.withLock { writeAtomically(routinesFile, encodeRoutines(_routines.value)) }
        }
    }

    private fun persistLogs() {
        scope.launch {
            writeMutex.withLock { writeAtomically(logsFile, encodeLogs(_logs.value)) }
        }
    }

    private fun persistNfc() {
        scope.launch {
            writeMutex.withLock { writeAtomically(nfcFile, encodeNfc(_nfcTags.value)) }
        }
    }

    private fun persistGlobals() {
        scope.launch {
            writeMutex.withLock { writeAtomically(globalsFile, encodeGlobals(_globals.value)) }
        }
    }

    private fun encodeRoutines(value: List<Routine>): String =
        json.encodeToString(ListSerializer(Routine.serializer()), value)

    private fun encodeLogs(value: List<RunLog>): String =
        json.encodeToString(ListSerializer(RunLog.serializer()), value)

    private fun encodeNfc(value: List<NfcRecord>): String =
        json.encodeToString(ListSerializer(NfcRecord.serializer()), value)

    private fun encodeGlobals(value: List<GlobalVar>): String =
        json.encodeToString(ListSerializer(GlobalVar.serializer()), value)

    /**
     * 背景元件（Receiver / Service）寫入紀錄後行程可能立刻結束，
     * 需要在返回前確保資料已落地。
     */
    fun addLogBlocking(log: RunLog) {
        _logs.value = (listOf(log) + _logs.value).take(AppSettings.logLimit)
        runBlocking {
            writeMutex.withLock { writeAtomically(logsFile, encodeLogs(_logs.value)) }
        }
    }

    /** 呼叫端必須已持有 writeMutex */
    private fun writeAtomically(target: File, content: String) {
        try {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        } catch (t: Throwable) {
            // 寫入失敗不影響 App 運作（記憶體內資料仍然正確）
        }
    }

    companion object {
        private const val FILE_ROUTINES = "routines.json"
        private const val FILE_LOGS = "logs.json"
        private const val FILE_NFC = "nfc_tags.json"
        private const val FILE_GLOBALS = "global_vars.json"

        @Volatile
        private var instance: RoutineRepository? = null

        fun get(context: Context): RoutineRepository =
            instance ?: synchronized(this) {
                instance ?: RoutineRepository(context).also { instance = it }
            }
    }
}
