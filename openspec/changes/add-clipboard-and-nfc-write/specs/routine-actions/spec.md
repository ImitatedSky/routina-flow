# routine-actions — 剪貼簿

## ADDED Requirements

### Requirement: 剪貼簿動作
系統 SHALL 支援「複製到剪貼簿」動作：把使用者設定的文字寫入系統剪貼簿。手動執行（App 於前景）SHALL 一律生效；由背景觸發執行時 SHALL 照常嘗試寫入且不得崩潰，並於動作結果註明部分裝置可能受系統背景剪貼簿限制。

#### Scenario: 手動執行複製
- **WHEN** 使用者手動執行含「複製到剪貼簿：Hello」的程序
- **THEN** 剪貼簿內容為「Hello」，動作記為成功

#### Scenario: 背景觸發複製
- **WHEN** 程序由定時觸發執行剪貼簿動作
- **THEN** 動作不崩潰、照常嘗試寫入，結果描述含背景限制註記
