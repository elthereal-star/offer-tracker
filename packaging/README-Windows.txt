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

AI 面试增强版：
 - 在页面右上角“AI 设置”中填写 OpenAI 兼容服务地址、模型和 API Key。
 - AI 配置保存在：%LOCALAPPDATA%\OfferTracker\data\ai-config.json；请勿分享或提交此文件。
 - AI 面试会使用简历文本和回答调用你配置的服务商，费用和数据处理规则由服务商决定。
 - 删除简历不会删除关联的 AI 面试历史；清除 AI 配置也不会删除历史记录。

迁移现有数据：
先在原程序右上角导出完整 JSON 备份，再在便携版中选择 JSON 恢复。
如需同时迁移 AI 配置和本地简历文件，请在退出程序后复制整个
`%LOCALAPPDATA%\OfferTracker\data` 目录，并确保目标机器上的文件权限仅对当前用户开放。
