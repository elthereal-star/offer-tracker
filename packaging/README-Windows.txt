Offer Tracker Windows 便携版
===========================

1. 双击 OfferTracker.exe，程序会自动选择空闲端口并打开默认浏览器。
2. 程序运行后会显示在 Windows 系统托盘中：
   - 双击托盘图标：重新打开页面
   - 右键托盘图标：打开或退出程序
3. 请保持整个 OfferTracker 文件夹结构完整，不要只移动 EXE 文件。
4. 数据默认保存在：%LOCALAPPDATA%\OfferTracker\data
5. 日志默认保存在：%LOCALAPPDATA%\OfferTracker\logs
6. 浏览器页面关闭不会删除数据，也不会自动退出程序；请从托盘菜单退出。
7. 如果托盘图标不可用，可运行 Stop-OfferTracker.cmd。

迁移现有数据：
先在原程序右上角导出完整 JSON 备份，再在便携版中选择 JSON 恢复。
