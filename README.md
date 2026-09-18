# MD 阅读器（Android）

自己用的 Markdown 阅读 App，Kotlin + Jetpack Compose 编写，**不依赖任何第三方库**
（Markdown 解析器、公式转换器、滚动条都是自己写的，只用 Compose 官方组件）。


## 更新记录

**v1.1**（装到手机试用后提的三点 + 一点）
- 新增：文件列表右下角 **＋ 新建 Markdown 文件**
- 新增：阅读页 **右侧细滚动条**（可拖动、可点击跳转，可在设置里关掉）
- 新增：**阅读设置**面板 —— 字号 12~28sp、左右边距 0~48dp、行距 120%~220%
- 修复：**数学公式无法显示** —— 现在 `\alpha` 显示为 α，`C_{L0}` 是真正的下标，`\frac` / `\sqrt` 也有对应排版

**v1.0** 首次版本：文件树、大纲跳转、编辑保存、深色模式、GBK 兼容

## 二、工程结构

```
MdViewerAndroid/
├── settings.gradle.kts            模块声明（只有 :app）
├── build.gradle.kts               顶层脚本：声明插件版本
├── gradle.properties              编译参数
├── gradle/libs.versions.toml      版本目录：所有依赖版本集中在这里，改版本只看这一个文件
└── app/
    ├── build.gradle.kts           app 模块：compileSdk / minSdk / 依赖清单
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml            只声明一个 Activity，不要任何存储权限
        │   ├── res/values/strings.xml         文案
        │   ├── res/values/themes.xml          只用于启动前的窗口底色，界面全是 Compose
        │   └── java/com/mdviewer/
        │       ├── MainActivity.kt            Activity 入口 + 页面切换（AppRoot）
        │       ├── data/DocStore.kt           用 SAF 列目录、读文件、写文件、新建文件、记最近打开
        │       ├── data/DocNames.kt           新建文件时的文件名规整（纯 Kotlin，可单测）
        │       ├── data/SettingsStore.kt      字号 / 边距 / 行距 / 滚动条开关，写进 SharedPreferences
        │       ├── markdown/MdModel.kt        数据结构：MdBlock / InlineText / TocEntry
        │       ├── markdown/InlineParser.kt   行内解析：**粗体** `代码` [链接](url) 公式 $...$
        │       ├── markdown/MarkdownParser.kt 块级解析：标题/列表/表格/代码块/引用/front matter
        │       ├── markdown/MathRenderer.kt   LaTeX -> Unicode + 上下标样式（\alpha -> α 等）
        │       └── ui/
        │           ├── Theme.kt               Material3 明暗两套配色
        │           ├── ReaderStyle.kt         阅读字号/行距的 CompositionLocal
        │           ├── MarkdownView.kt        把解析结果画到屏幕上（InlineText → AnnotatedString）
        │           ├── ScrollIndicator.kt     自己实现的右侧细滚动条（可拖可点）
        │           ├── SettingsSheet.kt       阅读设置面板
        │           └── Screens.kt             选文件夹页 / 文件列表页 / 阅读页 + 大纲
        └── test/java/com/mdviewer/
            ├── markdown/MarkdownParserTest.kt 20 个用例
            ├── markdown/MathRendererTest.kt   21 个用例
            └── data/DocNamesTest.kt           7 个用例
```

**分层思路**（这是本项目最值得看的部分）：
`markdown/` 和 `data/DocNames.kt` **完全不依赖 Android**，只吃 `String`、吐数据类。
所以它们能用普通的 JVM 单元测试跑（`gradlew test`，共 48 个用例），改渲染层时不用碰。
`ui/` 只负责把数据类画出来，`data/DocStore.kt` 只负责拿文件。

---

## 三、界面与交互

| 页面 | 能做什么 |
|---|---|
| 选文件夹 | SAF 选择器，授权持久化，重启后仍然记得 |
| 文件列表 | 递归列出所有 `.md/.markdown/.txt/.rst`；顶部按文件名过滤；分组显示「最近打开」；右下角 **＋ 按钮新建 Markdown 文件**（自动补 `.md`、过滤非法字符，建完直接进编辑） |
| 阅读页 | 标题/正文/代码块/表格/任务列表/引用/**数学公式**渲染；代码块可点「复制」；**右侧细滚动条**（可拖动、可点击跳转）；右上角依次是**设置**、**大纲**、**编辑** |

返回键：编辑状态下按返回是「取消编辑」，不会退出阅读页。

### 阅读设置（齿轮按钮）

| 项目 | 范围 | 说明 |
|---|---|---|
| 正文字号 | 12 ~ 28 sp | 标题、代码、表格字号都按比例跟着缩放 |
| 左右边距 | 0 ~ 48 dp | 正文距屏幕边缘的距离 |
| 行距 | 120% ~ 220% | |
| 显示滚动条 | 开 / 关 | 关掉后右侧不再显示细滚动条 |

设置面板里有实时预览，调完直接关掉即可（拖动时后面的正文已经在变）。
所有设置写进 `SharedPreferences`，下次打开还是你调好的样子。

### 数学公式

不需要联网、不依赖 KaTeX，`MathRenderer` 把常见 LaTeX 记法转成能读的文本：

| 写法 | 显示效果 |
|---|---|
| `\alpha` `\Delta` `\omega` | α Δ ω |
| `\times` `\leq` `\approx` `\to\infty` | × ≤ ≈ →∞ |
| `C_L` `C_{L0}` | C 和 L0 变成真正的下标 |
| `x^{2}` | 2 变成上标 |
| `\frac{C_L}{2}` | (CL)/2（多字符参数自动加括号） |
| `\sqrt{x+1}` `\sqrt[3]{x}` | √(x+1)、√[3]x |
| `\bar{x}` `\hat{x}` `\vec{x}` | x̄ x̂ x⃗（组合字符叠加） |
| `\text{刹车量}` | 刹车量（原样输出） |
| `\left( \right)` | 丢掉，只留括号 |

支持 `$行内公式$` 和 `$$展示公式$$`（单独成段时自动居中）。
不认识的命令（如 `\arctan`）会退化成命令名本身，不会让内容凭空消失。

[已知取舍] 这不是真正的 LaTeX 排版：矩阵、多行公式、`\sum` 的上下限位置都不会排得和论文一样。
真要 1:1 还原，得往 assets 里塞 KaTeX 并用 WebView 渲染，代价是包体积和性能。

---

## 四、几个实现要点

**1. 为什么用 SAF（存储访问框架）而不是文件路径？**
Android 10 起是分区存储，拿不到任意目录的读写权限。SAF 让用户手动挑一个文件夹并授权，
之后靠持久化 URI 权限访问 —— 好处是 `AndroidManifest.xml` 里**一条存储权限都不用声明**，
也不用给不同 Android 版本写兼容分支。

**2. 顺手支持 GBK**
很多老的中文文档是 GBK 存的。`DocStore.decode()` 先按 UTF-8 解，发现替换字符
（U+FFFD）就退回 GB18030。**保存时统一写成 UTF-8**，和桌面版行为一致。

**3. 大文件的性能**
阅读页用 `LazyColumn`，**一个块一项**，只渲染屏幕内可见的部分。
这同时让「点大纲跳转」变得简单：标题在块列表里的下标就是 `animateScrollToItem` 的参数。

**4. 代码块不折行**
`softWrap = false` + `horizontalScroll`，保持缩进可读，而不是把一行硬折成几段。

---

## 五、在本机直接打包（不装 Android Studio）

本机已装好一套命令行工具链，可以直接出 APK：

| 组件 | 位置 |
|---|---|
| JDK 17 | `C:\Program Files\Java\jdk-17`（系统原有） |
| Android SDK | `D:\Android\Sdk`（platform-tools / platforms;android-35 / build-tools;35.0.0） |
| Gradle 缓存与本体 | `D:\Android\gradle-home`（靠环境变量 `GRADLE_USER_HOME` 指过去，避免占 C 盘） |
| SDK 路径 | 已写在 `local.properties`（该文件不入库） |

> **注意**：本机的 Java 连不上 `services.gradle.org`（连接超时），所以 `gradlew`
> 自动下载 Gradle 会失败。已经把 Gradle 8.9 预置进
> `D:\Android\gradle-home\wrapper\dists\`，现在完全离线可用。
> 如果以后要换 Gradle 版本，记得先把发行包下好放进同一个 dists 目录，
> 或者给 `gradle-wrapper.properties` 换一个能访问的镜像地址。

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="D:\Android\Sdk"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cd d:\codefiles\MdViewerAndroid

.\gradlew.bat assembleDebug      # 出 debug APK（完全离线，约 15 秒）
.\gradlew.bat test               # 跑单元测试
.\gradlew.bat assembleRelease    # 出 release APK（需先配签名，见下）
```

产物：`app\build\outputs\apk\debug\app-debug.apk`（约 9 MB），
已经复制一份到工程根目录的 `mdviewer-debug.apk`。

装机：

```powershell
# 手机用 USB 连上并开启「USB 调试」后
D:\Android\Sdk\platform-tools\adb.exe install -r d:\codefiles\MdViewerAndroid\mdviewer-debug.apk
```

或者直接把 `mdviewer-debug.apk` 拷到手机（微信/网盘/U 盘都行）点击安装，
需要在系统设置里允许「安装未知来源应用」。debug 包已经用 Android Debug 证书签过名，
可以直接装、可以直接用。

---

## 六、已验证 / 未验证

**已在本机真实跑通**：

| 项目 | 结果 |
|---|---|
| Gradle 编译 | `BUILD SUCCESSFUL`，零警告、零错误 |
| 单元测试 | `gradlew test` 通过，**48 个用例 0 失败**（解析器 20 + 公式 21 + 文件名 7） |
| 解析器（独立 kotlinc 验证） | 67 项断言全过；解析真实中文文档 6853 字符 → 75 个块、21 个标题 |
| APK 校验 | 9.23 MB；包名 `com.mdviewer`；minSdk 26 / targetSdk 35；`aapt2 dump badging` 正常 |
| APK 签名 | `apksigner verify` 通过，证书为 `CN=Android Debug`（debug 签名） |

**未验证**：真机／模拟器上的实际运行与观感（本机没连设备，也没装模拟器镜像）。
界面相关的改动（滚动条、公式上下标、设置面板）都是本机编译通过但没在真机上看过效果。

---

## 七、release 签名（要上架或长期用才需要）

```powershell
# 生成自己的签名文件，一辈子只做一次；丢了就无法升级已发布的包
keytool -genkeypair -v -keystore mdviewer.jks -alias mdviewer -keyalg RSA -keysize 2048 -validity 10000
```

然后在 `app/build.gradle.kts` 里加：

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../mdviewer.jks")
            storePassword = "你的密码"
            keyAlias = "mdviewer"
            keyPassword = "你的密码"
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }
}
```

再 `.\gradlew.bat assembleRelease`。**不要**把 `.jks` 和密码提交到版本库。

---

## 八、可以自己接着加的

按难度从低到高：

1. **应用图标**：在 `res/mipmap-*/` 放图标，或在 Studio 里 `File → New → Image Asset`。
2. **全文搜索**：`DocStore` 已经会遍历所有文件，加一个 `searchAll(keyword)` 返回
   `(文件, 行号, 上下文)`，列表页再挂一个搜索结果页 —— 桌面版就是这么做的。
3. **正文内高亮关键词**：跳到命中行之后，把该段用 `SpanStyle(background = ...)` 标出来。
4. **收藏 / 置顶常用文档**：`SharedPreferences` 存一组 docId。
5. **公式排版**：现在 `$...$` 只是原样显示。接一个 KaTeX 的 WebView，
   或引入 MathJax 包装，把 `MdBlock.Paragraph` 里成对 `$` 的内容替换成渲染视图。
6. **导出分享**：`Intent.ACTION_SEND` 把渲染后的 HTML 发出去。

---

## 九、常见问题

| 现象 | 处理 |
|---|---|
| Sync 卡住不动 | 检查网络/代理；Gradle 首次要下几百 MB |
| 报 `Unsupported class file major version` | Android Studio 里 `Settings → Build → Gradle → Gradle JDK` 选 17 |
| 模拟器起不来 | 装 Intel/AMD 的虚拟化驱动；或直接用真机 USB 调试 |
| 选文件夹后是空的 | 那个文件夹里确实没有 `.md`；或换一个文件夹再授权一次 |
| 保存后手机上其他 App 看不到变化 | 部分网盘类提供方的 URI 写入有延迟，稍等或重新进入 |
