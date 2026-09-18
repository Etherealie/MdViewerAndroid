# MD 阅读器（Android）

自己用的 Markdown 阅读 App，Kotlin + Jetpack Compose 编写，**不依赖任何第三方库**
（Markdown 解析器、公式转换器、滚动条都是自己写的，只用 Compose 官方组件）。


## 更新记录

**v1.4**
- 新增：**自己的 App 图标**（自适应图标：蓝底白色 M↓；系统裁圆/裁圆角都好看）
- 新增：**Android 12+ 的启动画面**用品牌色，不再是白屏突然跳到深色界面
- 新增：[手机上提示「应用未备案」怎么办](#手机上提示应用未备案怎么办)（这是厂商安装器的联网校验，跟 APK 本身无关）

**v1.3**（试用后提的问题 + 想把编辑做得顺手一点）
- 修复：**右边滚动条「颤抖」** —— 旧版用「块序号 ÷ 块总数」算进度，屏幕上可见块数每帧会跳一下；
  现在按**实测像素高度**累加来算滑块位置和长度，拖动也改成像素级跳动，稳了
- 修复：**编辑没法预览、敲 Markdown 符号麻烦** —— 见下面「编辑模式」一节
- 新增：**编辑工具栏** —— 加粗/斜体/删除线/标题/行内码/代码块/链接/列表/待办/引用 一键插入，
  选中文字再点就是包起来，再点一下去掉
- 新增：**编辑时语法高亮** —— 源码模式下 `#` 标题、`**粗体**`、`` `代码` ``、围栏代码块
  都上色，且 **一个字符都不改**（用 `OffsetMapping.Identity`，光标不会跑偏）
- 新增：**编辑/预览 一键切换** —— 表格、公式这种不确定效果的，切过去看一眼再回来接着写，草稿不丢
- 新增：**编辑未保存提醒** —— 改了没存就按返回/取消，弹「保存 / 放弃修改 / 继续编辑」
- 新增：**文档内查找** —— 右上角放大镜，实时高亮命中处，上一个/下一个跳转，带 `3/17` 计数
- 新增：**任务列表可勾选** —— 阅读页直接点复选框，**写回源文件**；
  写之前会校验收解析行号 + 文字内容，定位不准就报错让你去编辑模式改，绝不乱改行
- 新增：**分享原文 / 复制全文** —— 右上角 ⋮ 菜单，分享走系统面板（微信、邮件、笔记 App 都能接）
- 新增：**字数统计** —— 标题下方显示「xxxx 字」，中文按字、英文按词（和 Typora 一致）
- 新增：**字体选择** —— 系统默认 / 衷线 / 等宽，设置面板里随时切
- 新增：**代码块语法高亮** —— 支持 C/C++/Java/Kotlin/JS/TS/Go/Rust/Python/Shell 等，
  关键字、字符串、注释、数字分开着色（自己写的词法器，不引入任何高亮库）
- 新增：**4 个测试类 + 解析器行号用例**，总用例数 52 → 101

**v1.2**（试用后提的三点）
- 修复：**按返回键直接退 App** —— 现在依次是「退出编辑 → 关面板 → 回文件列表」，文件列表按两下才退出
- 新增：**9 套配色主题**（经典蓝 / 森林绿 / 青碧 / 静谧紫 / 绯樱 / 日落橙 / 石墨灰 / 米黄护眼 / 跟随壁纸）
- 扩大：字号 10~40sp、边距 0~96dp、行距 100%~300%，另加字号预设按钮
- 新增：**段落间距**、**阅读时屏幕常亮**、**阅读进度百分比**

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
        │       ├── data/SettingsStore.kt      字号 / 边距 / 行距 / 段落间距 / 主题 / 开关，写进 SharedPreferences
        │       ├── markdown/MdModel.kt        数据结构：MdBlock / InlineText / TocEntry
        │       ├── markdown/InlineParser.kt   行内解析：**粗体** `代码` [链接](url) 公式 $...$
        │       ├── markdown/MarkdownParser.kt 块级解析：标题/列表/表格/代码块/引用/front matter
        │       ├── markdown/MathRenderer.kt   LaTeX -> Unicode + 上下标样式（\alpha -> α 等）
        │       ├── markdown/SyntaxHighlighter.kt  代码块词法器：关键字/字符串/注释/数字（纯 Kotlin）
        │       ├── markdown/MarkdownSourceHighlighter.kt  编辑时的源码着色区间（不改字符，只给区间）
        │       ├── markdown/MarkdownEdit.kt   工具栏那几件事：包选中、行首标记开关、插链接/代码块（纯 Kotlin）
        │       ├── markdown/TextStats.kt      字数统计：中文按字、英文按词（纯 Kotlin）
        │       └── ui/
        │           ├── AppTheme.kt            9 套配色主题（只定义主色，其余颜色自动推导）
        │           ├── Theme.kt               Material3 主题入口
        │           ├── ReaderStyle.kt         阅读排版参数的 CompositionLocal（含字体）
        │           ├── MarkdownView.kt        把解析结果画到屏幕上（InlineText → AnnotatedString）
        │           ├── ScrollIndicator.kt     自己实现的右侧细滚动条（按实测高度算位置，可拖可点）
        │           ├── Editor.kt              编辑工具栏 + 源码着色的 VisualTransformation
        │           ├── SettingsSheet.kt       阅读设置面板（字号/字体/排版/配色/显示）
        │           └── Screens.kt             选文件夹页 / 文件列表页 / 阅读页 + 大纲 + 查找
        └── test/java/com/mdviewer/
            ├── markdown/MarkdownParserTest.kt  25 个用例（含任务行号）
            ├── markdown/MathRendererTest.kt   21 个用例
            ├── markdown/SyntaxHighlighterTest.kt  13 个用例
            ├── markdown/MarkdownSourceHighlighterTest.kt  10 个用例
            ├── markdown/MarkdownEditTest.kt   12 个用例
            ├── markdown/TextStatsTest.kt      9 个用例
            ├── data/DocNamesTest.kt           7 个用例
            └── ui/AppThemeTest.kt             4 个用例
```

**分层思路**（这是本项目最值得看的部分）：
`markdown/` 和 `data/DocNames.kt` **完全不依赖 Android**，只吃 `String`、吐数据类。
所以它们能用普通的 JVM 单元测试跑（`gradlew test`，共 101 个用例），改渲染层时不用碰。
`ui/` 只负责把数据类画出来，`data/DocStore.kt` 只负责拿文件。

---

## 三、界面与交互

| 页面 | 能做什么 |
|---|---|
| 选文件夹 | SAF 选择器，授权持久化，重启后仍然记得 |
| 文件列表 | 递归列出所有 `.md/.markdown/.txt/.rst`；顶部按文件名过滤；分组显示「最近打开」；右下角 **＋ 按钮新建 Markdown 文件**（自动补 `.md`、过滤非法字符，建完直接进编辑） |
| 阅读页 | 标题/正文/代码块/表格/任务列表/引用/**数学公式**渲染；代码块可点「复制」；**任务框可直接勾选并写回文件**；**右侧细滚动条**（可拖动、可点击跳转）；顶部：**查找**、**大纲**、**⋮ 菜单**（编辑 / 阅读设置 / 分享原文 / 复制全文） |

返回键：阅读页里依次是「未保存确认 → 退出编辑 → 关查找/面板 → 回文件列表」；文件列表是主页，按两下才退出。

### 编辑模式（⋮ 菜单 → 编辑）

顶部工具栏从左到右：

| 按钮 | 作用 |
|---|---|
| **B** / *I* / ~~S~~ | 把选中的文字变成 `**粗体**` / `*斜体*` / `~~删除线~~`；没选就插一对符号，光标停中间 |
| H1 / H2 / H3 | 给光标所在行加 `#`，已经有标题标记了再点一下就去掉（开关式） |
| 行内码 | 包一对反引号 |
| 代码块 | 插一段围栏代码块，选中的文字直接放进去 |
| 链接 | 插 `[文字](url)`，并把 `url` 选中，粘贴就能替换 |
| 列表 / 待办 / 引用 | 行首加 `- ` / `- [ ] ` / `> `，多行选中时逐行加 |

编辑区本身也带 **Markdown 语法高亮**（标题、语法符号、行内代码、代码块都上色），
实现上是把着色的结果当作 `VisualTransformation` 返回、并用 `OffsetMapping.Identity` 映射光标，
所以**文本一个字符都没变**，光标、选区、撤销都不受影响。

右上角「预览」可以随时切到渲染视图看效果（表格/公式这种不确定的尤其有用），再点「编辑」切回来，**草稿不会丢**。
标题下方实时显示字数和未保存标记（`● 未保存 · 1234 字`）。

### 阅读设置（齿轮按钮）

| 项目 | 范围 | 说明 |
|---|---|---|
| 正文字号 | 10 ~ 40 sp | 另有 小/标准/大/特大/超大 五个预设；标题、代码、表格字号都按比例跟着缩放 |
| 字体 | 系统默认 / 衷线 / 等宽 | 三类系统字体，不需内置字体文件；代码块始终是等宽 |
| 段落间距 | 0 ~ 40 dp | 段与段之间的留白 |
| 左右页边距 | 0 ~ 96 dp | 正文距屏幕边缘的距离 |
| 行距 | 100% ~ 300% | |
| 配色 | 9 套 | 见下 |
| 显示滚动条 | 开 / 关 | 关掉后右侧不再显示细滚动条 |
| 阅读时屏幕常亮 | 开 / 关 | 看文档时不让屏幕自动熄灭 |
| 显示阅读进度 | 开 / 关 | 标题下方显示「1234 字 · xx% 已读」（字数始终显示，百分比可关） |

设置面板里有实时预览，调完直接关掉即可（拖动时后面的正文已经在变）。
所有设置写进 `SharedPreferences`，下次打开还是你调好的样子。

### 配色主题

| 主题 | 深色模式 | 说明 |
|---|---|---|
| 经典蓝 / 森林绿 / 青碧 / 静谧紫 / 绯樱 / 日落橙 / 石墨灰 | ✅ 各自单独配色 | 只换主色，背景保持白/黑 |
| **米黄护眼** | ✅ 暖色深色 | 背景、代码块、表格、边框整套换成米黄色，长时间读文档不刺眼 |
| **跟随壁纸** | ✅ | Android 12+ 用系统取色（Material You）；低版本自动退回经典蓝 |

实现上每套主题只定义**一个主色**，其余颜色（容器色、边框、代码背景等）由 `AppTheme.kt`
用 `lerp` 与背景混合推导，所以以后加一套主题只需加一行。
点色点立刻全局变色，包括设置面板自己。

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

**5. 滚动条「颤抖」的根因**
旧版位置 = `第一个可见块下标 ÷ 块总数`，而 `visibleItemsInfo` 的个数每帧会抖（±1），
除出来的百分比就跟着跳。现在 `ScrollIndicator` 用 `snapshotFlow` 收集**每个块实测的像素高度**，
位置改成「前面已测高度之和 + 当前块内偏移」，总高用「已测之和 + 未测块 × 实测平均高」估算。
滑块长度和位置因此是单调的，拖动也从「跳到第 N 块」改成 `scrollToItem(index, offsetInItem)` 的像素级跳转。

**6. 编辑时高亮不能改文本**
`VisualTransformation` 会接管光标坐标映射，只要返回的字符串和原文不等长就会错位。
所以 `MarkdownSourceHighlighter` 只输出「区间 + 类别」，一个字符也不增删，
配 `OffsetMapping.Identity` 直接恒等映射，光标/选区/输入法候选都不会出问题。

**7. 勾选任务列表要能安全写回**
点击复选框是直接改磁盘文件的，所以写之前做了两道校验：解析器给出的**行号**必须落在文件里，
且该行去掉 markdown 符号后要跟界面上的文字一致；不匹配就全文搜「唯一匹配」，
还找不到就返回 false，弹提示让你去编辑模式改。宁可不动，不会改错行。

**8. 两个 `setXxx` 命名撞车**
`var showScrollbar` 配 `fun setShowScrollbar()` 会在 JVM 上签名冲突（属性 getter/setter 同名），
所以改成 `setScrollbarVisible` / `updateKeepScreenOn` 这种不同名的写法。

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

### 手机上提示「应用未备案」怎么办

2023 年起工信部要求 App 备案，2024 年之后小米/华为/OPPO/vivo/荣耀的**安装器会连云端查备案库**，
查不到就直接拦（提示「该应用未备案/无法继续安装」）。
这个校验**跟 APK 本身无关** —— 改包名、重新签名、加隐私政策都没用，只能在「怎么装」上绕：

| 办法 | 做法 | 可靠性 |
|---|---|---|
| **adb 安装**（推荐） | 数据线连电脑：`adb install -r mdviewer-debug.apk`。adb 走 shell 权限直接调 `pm install`，不经过厂商的点安装界面 | 最高 |
| **Shizuku + 安装狮** | 手机上装 Shizuku（用无线调试激活，不用电脑）再装「安装狮」，以系统权限安装 | 高 |
| **关掉厂商校验** | 华为/荣耀：设置 → 系统和更新 → 纯净模式 → 退出<br>小米/HyperOS：设置 → 应用设置 → 应用管理 → 右上角 ⋮ → 设置 → 关「应用安装校验」<br>OPPO/一加/真我：设置 → 应用管理 → 特殊应用权限 → 安装未知应用<br>vivo/iQOO：设置 → 安全与隐私 → 更多安全设置 → 关「未知来源应用安装校验」 | 中（各家路径不一样） |
| **断网安装** | 开飞行模式再点安装 —— 部分机型不联网就不查备案 | 碰运气 |
| 真去备案 | 需主体（个人就行）+ 域名/服务器/软著等材料，通过云服务商或应用商店提交到工信部系统，1~20 个工作日 | 自用不值得 |

装完如果安全中心再报「未知来源应用」，忽略就行 —— debug 签名的 APK 都会这样。

> 为什么不做正式签名？自用的 debug 包不需要管理密钥，改完直接出包；
> 真要发布给别人，再去生成 keystore 并把 versionCode / 签名固定下来。

---

## 六、已验证 / 未验证

**已在本机真实跑通**：

| 项目 | 结果 |
|---|---|
| Gradle 编译 | `BUILD SUCCESSFUL`，零警告、零错误 |
| 单元测试 | `gradlew test` 通过，**101 个用例 0 失败**（解析 25 + 公式 21 + 代码高亮 13 + 源码高亮 10 + 编辑工具 12 + 字数 9 + 文件名 7 + 主题 4） |
| 解析器（独立 kotlinc 验证） | 67 项断言全过；解析真实中文文档 6853 字符 → 75 个块、21 个标题 |
| APK 校验 | 9.24 MB；包名 `com.mdviewer`；版本 1.4（versionCode 5）；minSdk 26 / targetSdk 35；`aapt2 dump badging` 正常；自适应图标 `res/mipmap-anydpi-v26/ic_launcher.xml` 已入包 |
| APK 签名 | `apksigner verify` 通过，证书为 `CN=Android Debug`（debug 签名） |

**未验证**：真机／模拟器上的实际运行与观感（本机没连设备，也没装模拟器镜像）。
界面相关的改动（滚动条、公式上下标、设置面板、编辑工具栏、源码高亮）都是本机编译通过、
并有纯逻辑部分的单元测试，但**没在真机上看过效果**。

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
