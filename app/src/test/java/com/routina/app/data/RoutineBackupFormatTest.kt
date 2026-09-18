package com.routina.app.data

import com.routina.app.model.GlobalVar
import com.routina.app.model.NfcRecord
import com.routina.app.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 備份格式的相容性。
 *
 * 這裡守的是一件事：**使用者手上那些格式 1 的舊備份，永遠要讀得回來。**
 * 讀不回來就等於資料救不回來，而且往往要到有人真的需要還原時才會發現。
 *
 * 驗的是 [RoutineBackupIo.json] 本人，不是另外複製一份設定——
 * 複製品驗過了不代表 App 實際用的那份也過。
 */
class RoutineBackupFormatTest {

    private val json = RoutineBackupIo.json

    private fun decode(text: String) =
        json.decodeFromString(RoutineBackup.serializer(), text)

    @Test
    fun `格式 1 的舊備份仍然讀得進來，新欄位是空的`() {
        // 這是 v1 實際會寫出來的樣子：只有信封加 routines
        val v1 = """
            {
              "format": 1,
              "appVersion": "1.0.3",
              "exportedAt": 1756000000000,
              "routines": [
                { "id": "abc", "name": "早晨" }
              ]
            }
        """.trimIndent()

        val backup = decode(v1)

        assertEquals(1, backup.format)
        assertEquals(1, backup.routines.size)
        assertEquals("早晨", backup.routines[0].name)
        // 舊檔沒有這些欄位，要安靜地退回空值而不是解析失敗
        assertTrue(backup.nfcTags.isEmpty())
        assertTrue(backup.globals.isEmpty())
        assertNull(backup.settings)
        assertTrue(backup.hasExtras.not())
    }

    @Test
    fun `連信封欄位都缺的極簡備份也讀得進來`() {
        val bare = """{ "routines": [ { "id": "x", "name": "測試" } ] }"""
        val backup = decode(bare)

        assertEquals(1, backup.routines.size)
        assertEquals(RoutineBackup.FORMAT_VERSION, backup.format)
    }

    @Test
    fun `未來版本多出來的欄位會被忽略，不會讓整份檔案讀不進來`() {
        val withUnknown = """
            {
              "format": 2,
              "routines": [],
              "globals": [ { "name": "喝水", "value": "3" } ],
              "somethingAddedLater": { "a": 1 }
            }
        """.trimIndent()

        val backup = decode(withUnknown)

        assertEquals(1, backup.globals.size)
        assertEquals("喝水", backup.globals[0].name)
    }

    @Test
    fun `格式 2 的三類附加資料來回一趟不會走樣`() {
        val original = RoutineBackup(
            appVersion = "1.0.6",
            routines = listOf(Routine(id = "r1", name = "就寢")),
            nfcTags = listOf(
                NfcRecord(id = "n1", name = "床頭", uid = "04AABBCC", summary = "https://example.com")
            ),
            globals = listOf(GlobalVar(name = "喝水", value = "3")),
            settings = BackupSettings(
                themeMode = ThemeMode.DARK,
                runToast = false,
                logLimit = 200
            )
        )

        val restored = decode(json.encodeToString(RoutineBackup.serializer(), original))

        assertEquals(RoutineBackup.FORMAT_VERSION, restored.format)
        assertEquals("就寢", restored.routines.single().name)
        assertEquals("04AABBCC", restored.nfcTags.single().uid)
        assertEquals("床頭", restored.nfcTags.single().name)
        assertEquals("3", restored.globals.single().value)
        assertEquals(ThemeMode.DARK, restored.settings?.themeMode)
        assertEquals(false, restored.settings?.runToast)
        assertEquals(200, restored.settings?.logLimit)
    }

    @Test
    fun `只有標籤沒有程序的備份不算空`() {
        val onlyTags = RoutineBackup(
            nfcTags = listOf(NfcRecord(uid = "04DDEEFF"))
        )
        assertTrue(onlyTags.isEmpty.not())
        assertTrue(onlyTags.hasExtras)
    }

    @Test
    fun `什麼都沒有的備份算空`() {
        assertTrue(RoutineBackup().isEmpty)
    }
}
