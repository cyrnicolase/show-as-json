# Show as JSON Plugin for DataGrip

[![Version](https://img.shields.io/badge/version-1.0.11-blue.svg)](https://github.com/cyrnicolase/show-as-json-plugin)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![DataGrip](https://img.shields.io/badge/DataGrip-231%2B-orange.svg)](https://www.jetbrains.com/datagrip/)

一个 DataGrip 插件，用于在查询结果表格中将选中的单元格内容以 JSON 格式打开并高亮显示。

## 功能特性

- 🚀 **快速查看**：在查询结果表格中选中单元格后按 `F7`，即可在独立的 JSON 编辑器中查看
- 🎨 **语法高亮**：自动应用 JSON 语法高亮，提升可读性
- 📝 **自动格式化**：自动将压缩的 JSON 字符串格式化为可读的多行格式
- 🔄 **单例模式**：全局只有一个编辑器实例，切换单元格时自动更新内容
- 📏 **字体同步**：JSON 编辑器字体大小与查询结果表格保持一致
- ⌨️ **快捷键支持**：支持 `F7` 快捷键快速触发和关闭面板
- 🎯 **只读模式**：编辑器为只读模式，防止误操作
- 🔁 **Toggle 功能**：未选择单元格时按 `F7` 可关闭已打开的 JSON 面板

## 系统要求

- **DataGrip**: 231 或更高版本（支持到 254.*）
- **Java**: 17 或更高版本

## 安装方法

### 方法一：从 ZIP 文件安装（推荐）

1. 下载最新版本的插件 ZIP 文件（从 [Releases](https://github.com/cyrnicolase/show-as-json-plugin/releases) 页面）
2. 打开 DataGrip
3. 进入 `Settings/Preferences` → `Plugins`
4. 点击齿轮图标 ⚙️ → `Install Plugin from Disk...`
5. 选择下载的 ZIP 文件
6. 重启 DataGrip

### 方法二：从源码构建

```bash
# 克隆仓库
git clone https://github.com/cyrnicolase/show-as-json-plugin.git
cd show-as-json-plugin

# 构建插件
make build

# 或者使用 Gradle
./gradlew buildPlugin

# 构建完成后，插件文件位于：
# build/distributions/show-as-json-plugin-1.0.10.zip
```

## 使用方法

1. 在 DataGrip 中执行 SQL 查询
2. 在查询结果表格中选中一个包含 JSON 数据的单元格
3. 按 `F7` 快捷键
4. JSON 编辑器对话框将自动打开，显示格式化后的 JSON 内容

### 快捷键

- **F7**: 
  - 当选择输出单元格时：打开/更新 JSON 编辑器对话框
  - 当未选择输出单元格时：如果面板已打开，则关闭面板；如果面板未打开，则无操作

### 对话框操作

- **F7**: 未选择单元格时，可关闭已打开的 JSON 面板
- **ESC**: 关闭对话框
- **滚动**: 支持正常的滚动速度，方便查看长 JSON 内容

## 开发指南

### 项目结构

```
show-as-json-plugin/
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/github/cyrnicolase/showasjson/
│       │       ├── ShowAsJsonAction.kt      # Action 入口
│       │       └── util/
│       │           ├── CellValueExtractor.kt   # 单元格值提取
│       │           ├── DialogUtils.kt          # 对话框工具类
│       │           ├── EditorUtils.kt          # 编辑器工具类
│       │           ├── JsonEditorDialog.kt      # JSON 编辑器对话框
│       │           └── JsonFormatter.kt        # JSON 格式化
│       └── resources/
│           └── META-INF/
│               └── plugin.xml              # 插件配置
├── build.gradle.kts                        # Gradle 构建配置
├── Makefile                                # Make 命令
└── README.md                               # 本文件
```

### 构建要求

- **JDK**: 17 或更高版本
- **Gradle**: 通过 Gradle Wrapper 自动管理
- **Kotlin**: 1.9.0

### 构建命令

使用 Makefile（推荐）：

```bash
make build      # 构建插件
make clean      # 清理构建文件
make dist       # 构建并显示插件信息
make run        # 运行插件进行测试
make install    # 安装插件到 DataGrip（需要设置 DATAGRIP_PLUGINS_DIR）
```

使用 Gradle：

```bash
./gradlew buildPlugin    # 构建插件
./gradlew clean          # 清理构建文件
./gradlew runIde         # 运行插件进行测试
```

### 配置 DataGrip 路径

在 `build.gradle.kts` 中配置本地 DataGrip 安装路径（可选）：

```kotlin
intellij {
    // macOS 示例
    localPath.set("/Applications/DataGrip.app")
    
    // Windows 示例
    // localPath.set("C:\\Program Files\\JetBrains\\DataGrip 2023.3")
    
    // Linux 示例
    // localPath.set("/opt/datagrip")
}
```

如果不设置 `localPath`，构建系统会自动下载 IntelliJ IDEA Ultimate（包含数据库插件）。

### 开发环境设置

1. 克隆项目
2. 使用 IntelliJ IDEA 打开项目
3. 等待 Gradle 同步完成
4. 运行 `./gradlew runIde` 启动沙盒 DataGrip 实例进行测试

## 技术实现

### 核心技术

- **IntelliJ Platform SDK**: 用于插件开发
- **Kotlin**: 主要编程语言
- **Gson**: JSON 格式化库
- **PSI (Program Structure Interface)**: 用于语法高亮

### 关键特性实现

- **单元格值提取**: 使用反射机制访问 DataGrip 内部 API，支持多种数据源
- **JSON 格式化**: 使用 Gson 库进行 JSON 解析和美化
- **语法高亮**: 通过 PSI 文件创建 JSON 语言上下文，自动应用语法高亮
- **单例模式**: 使用 `@Volatile` 和 `synchronized` 确保线程安全

## 常见问题

### Q: 按 F7 没有反应？

A: 请确保：
1. 在查询结果表格中选中了一个单元格
2. 单元格包含数据（不为空）
3. 插件已正确安装并启用

### Q: JSON 格式不正确怎么办？

A: 如果 JSON 格式错误，插件会显示原始内容。请检查：
1. JSON 字符串是否完整
2. 是否包含转义字符
3. 是否包含特殊字符

### Q: 对话框无法关闭？

A: 可以：
1. 点击对话框的关闭按钮
2. 按 `ESC` 键
3. 未选择单元格时按 `F7` 键（如果面板已打开）
4. 关闭 DataGrip 窗口

### Q: 支持哪些 DataGrip 版本？

A: 支持 DataGrip 231 到 254.* 版本。

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证。详情请参阅 [LICENSE](LICENSE) 文件。

## 作者

**cyrnicolase**

- GitHub: [@cyrnicolase](https://github.com/cyrnicolase)

## 更新日志

### 1.0.11

- ✅ 新增 F7 快捷键 toggle 功能：未选择单元格时按 F7 可关闭已打开的 show as json 面板
- ✅ 优化代码结构，移除未使用的代码
- ✅ 改进代码格式和可读性

### 1.0.10

- ✅ 修复项目关闭检查缺失的问题
- ✅ 添加异常处理和边界检查
- ✅ 优化代码结构，提升可维护性
- ✅ 移除代码折叠功能（未实现）
- ✅ 移除颜色自定义功能
- ✅ 添加 JSON 格式化空字符串处理
- ✅ 添加表格索引边界检查
- ✅ 改进资源释放逻辑

### 1.0.6

- 初始版本
- 支持在值编辑器中以 JSON 格式显示单元格内容
- 支持快捷键触发（F7）

## 致谢

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Gson](https://github.com/google/gson)
- [DataGrip](https://www.jetbrains.com/datagrip/)

## 相关链接

- [项目主页](https://github.com/cyrnicolase/show-as-json-plugin)
- [问题反馈](https://github.com/cyrnicolase/show-as-json-plugin/issues)
- [DataGrip 官网](https://www.jetbrains.com/datagrip/)

---

如果这个插件对你有帮助，请给个 ⭐ Star！

