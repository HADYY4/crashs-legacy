# Crashs Legacy ImGui Preview

## 1. 桌面预览器

不使用 Minecraft，不需要打 mod jar。

```powershell
./gradlew.bat :preview:run
```

预览器渲染的是**游戏内同一个界面类** `CrashsImGuiSurface`（通过 `preview/build.gradle.kts` 里的
`sourceSets` 直接把主模块中那批不依赖 Minecraft 的 UI 源码编译进来）。也就是说：

```text
顶栏 / 左侧导航胶囊 / 模块卡片 / 折叠动画 / 悬停扫过 / 圆角裁剪 / 字号与配图
→ 与游戏内完全一致

模块设置里的控件
→ 预览器用演示控件代替（真实控件需要配置读写）
```

窗口打开后：左侧"模块"折叠导航（全部 / 无名）+ 底部 HUD 入口，右侧两列瀑布流卡片，点卡片展开，
右上角可打开 UI 调试 / Style Editor。

预览器不读也不写 `config/` 下的文件（`configDir` 设为 `null`），所以不会污染你的真实配置。
它也不替代游戏内输入/配置验证。

## 2. 游戏内 Style Editor

启动游戏后打开：

```text
F7 → ImGui
右上角 UI 调试
```

会出现：

```text
ImGui Demo
ImGui Style Editor
```

可以直接调整颜色、圆角、间距、FramePadding、ItemSpacing 等 ImGui 参数。

当前修改只存在于运行时；确认数值后手动写回：

```text
CrashsImGuiTheme.kt
```

## 3. 布局 JSON

运行时会读取：

```text
config/crashs-legacy-imgui-layout.json
```

示例：

```json
{
  "windowWidth": 1040,
  "windowHeight": 760,
  "railWidth": 190,
  "navWidth": 160,
  "navHeight": 54,
  "cardGap": 16,
  "cardRadius": 16,
  "cardHeaderHeight": 82,
  "animationSpeed": 14,
  "uiFontSize": 24,
  "rasterizerDensity": 1.5
}
```

修改后重新打开 ImGui 界面即可生效，不需要重新编译；**`uiFontSize` 例外**——字体图集每次会话只烘焙一次，
改字号需要重启客户端，改其它参数重开界面即可。

`uiFontSize` 会被限制在 12–40，`rasterizerDensity` 限制在 1.0–2.0。字形图集面积按
“字号 × 倍率”的平方增长：24px + 1.5 ≈ 32 MB，20px 以上 + 2.0 会跳到 64 MB（更容易出现纹理上传失败）。
只改字号时，胶囊、图标、卡片头、开关、顶栏都会按比例跟着缩放。

`railWidth` 会被限制在 150–280，`navWidth` 会被限制在 104–`railWidth-24`：胶囊里的
“图标 + 文字”整组居中，所以标签最多四个字时也不会贴边；要放更长的标签就同时加大
`railWidth` 和 `navWidth`。

## 4. 配置页

左侧导航底部的 **配置** 胶囊把内容区整体切换成配置页（不是弹窗；再点一次回到模块页，
页面选择会被记住）：

```text
外观
  主题模式   深色 | 浅色
  主题色     6 个预设
  [恢复默认主题]
```

- 配置页打开时顶栏搜索框置灰；
- 卡片区的滚动、裁剪与模块页共用同一个 viewport。

播报查询的入口现在在「播报管理 → 全局 → 文件格式」下方（原来是一个独立模块卡片）；
### 库存管理（Item Tracker）

服务器仓库插件在自动收纳物品时会发送 `[PlayerInv] 5x §5晶簇鲷 ==> 大型仓库 [1]`。开启该模块后：

- 消息被解析成「数量 / 物品（保留服务器颜色）/ 仓库（保留完整颜色）/ 页码」，逐条显示在独立 HUD 上；
- HUD 行格式：灰色 `[` + 绿色 `+` + 灰色 `]`、绿色数量（不带 `x`）、物品名（服务器颜色）、
  灰色 `→`、仓库原文（每个颜色段都保留，含 `[页码]`）；仓库可关；
- **HUD 方向**：**上**（默认，底边锚定，新条目在底部）或 **下**（顶边锚定，新条目在顶部）；
- 全局窗口（默认 6 秒）：任意新内容刷新窗口，到期后所有行直接消失；
- 窗口内同变体按净值合并，净值 0 时该行移除；
- 「拦截聊天」是**单独开关**（默认关）：打开后这类消息不再出现在聊天框；
- 播报管理仍会照常处理同一条消息（两边都处理，互不影响）；
- 「管理背包」开关：监听 `InventoryS2CPacket` / `ScreenHandlerSlotUpdateS2CPacket`（syncId=0），
  缓存按物品聚合的数量并做差；增加显示 `[+]`、减少显示 `[-]`（红色）；
  任何容器界面打开期间暂停，关闭后静默重建基线；背包界面本身正常处理；
- 位置/缩放走 HUD 编辑器（`ItemTrackerHudComponent`，底边锚定时按底边计算 Y），行数、窗口秒数、背景/描边/阴影均可配。

金币统计的「重置数据」在模块描述下方一行。金币只从**击杀奖励行**记账（复用击杀正则）：
服务器金币提示格式太多，单一正则无法覆盖，因此不做纯金币消息的匹配。

## 5. HUD 编辑器

游戏内左侧导航底部的 **HUD** 胶囊（或 `/crashs hud`）会打开可视化编辑器，支持所有 HUD 组件：

```text
击杀反馈 HUD、金币统计 HUD  → 点框选中，面板跟随最后一次点击的组件
拖拽框体                   → 改该组件的百分比位置（左右锚点自动镜像）
数值输入 / 滑杆             → 缩放（0.5–2.5）；X / Y 可直接键入（0–100，允许出屏）
锚点单选                   → 左右切换时保持当前视觉位置
吸附                       → 屏幕边缘/中线 + 其他组件（左/右/中心/双向贴边），统一 8px，
                             最近者胜（等距时优先屏幕），都不命中则 1% 网格，并显示对齐辅助线
面板                       → 可拖动，位置会记住；「重置面板位置」可归位
Enter / 完成 / Esc          → 保存并返回 ClickGUI
重置该组件                  → 恢复该组件的默认位置/缩放/锚点
```

- 编辑器不暂停游戏，两个 HUD 都由**真实渲染路径**绘制，框体只是叠在上面；
- 编辑器面板与配置弹窗同一层底色（popover），锚点用与卡片一致的 pill 开关，次要文字统一走
  `ON_SURFACE_VARIANT`；辅助线/框体颜色来自主题 token（`guideScreen` / `guideComponent` / `boxIdle` / `dragHint`）；
- 最小拖拽区 20×10：HUD 本身再小也能抓住，正常尺寸不受影响；
- 位置换算、吸附、组件读写都是纯逻辑（`HudPlacement` / `HudDragState` / `HudComponent`），
  有单测覆盖左右锚点往返、1% 网格、边缘吸附、钳制与两个组件的默认值。

## 6. 性能基准（不需要进游戏、不需要显卡）

测的是**真实界面**（同一个 `CrashsImGuiSurface`、同一套主题与模块），不是仿制品：

```powershell
$env:GRADLE_USER_HOME='C:\Gradle\repository'
./gradlew.bat --no-daemon --offline :preview:perf
```

输出示例（默认折叠状态）：

```text
Crashs Legacy UI benchmark
  font:        ..\src\main\resources\assets\crashs-legacy\fonts\misans.ttf
  scheme:      DARK  accent=#ffff615a
  cards:       default folds
  frames:      250 warm-up + 1000 measured
  atlas:       2048x4096  32.0 MB  baked in 85 ms
  CPU/frame:   mean 92.8 us | median 85.2 us | p95 166.6 us | max 466.7 us
  draw:        2066 verts, 7407 indices, 2 cmd lists
  budget:      0.56% of a 16.67 ms frame (mean)
```

可选参数（Gradle 属性 → 程序内系统属性）：

| 参数 | 作用 |
|---|---|
| `-PperfAll=true` | 展开全部卡片（最坏情况，帧内控件最多） |
| `-PperfFrames=2000` | 取样帧数（默认 1000，另有 250 帧预热） |
| `-PperfMaxUs=150` | 均值超过该微秒数时任务失败，用于回归门禁 |
| `-PperfJson=true` | 只输出一行 JSON，便于脚本记录 |

说明：

- 只测 **CPU 侧**（建帧 + 生成 ImGui draw data），不含 GPU 上传与绘制；
- `max` 偶尔偏高是 JIT/GC 抖动，判断趋势看 `mean`/`p95`；
- 首帧会把字体图集烘进显存，`atlas` 一行给的是尺寸、占用与烘焙耗时（一次性成本）。

## 7. 自检（不需要进游戏）

```powershell
$env:GRADLE_USER_HOME='C:\Gradle\repository'
./gradlew.bat --no-daemon --offline test
```

单元测试分两类：

- **纯逻辑**：`KillRewardParser`（默认正则、括号奖励、半角逗号、非法正则回退）、布局参数钳制、
  `CrashsUiMetrics` 的几何公式。
- **headless ImGui**（不用启动客户端，也不用显卡）：用同一份 imgui 1.92.7.1 + 同一份 `misans.ttf`
  把图集烘出来，然后断言
  1. `src/main/kotlin` 里**所有字符串字面量中的中文字符**都在字体范围里（缺字会显示成 `?`，
     报错信息会列出具体字、码点和文件）；
  2. 之前踩过的 9 个字（馈/聊/汰/凋/缀/锚/屏/沌/辐）确实被覆盖，且常用字范围本身不含它们；
  3. 24px 下“四字标签 + 图标”的胶囊内容宽度不超过胶囊可用宽度（留白 ≥ 2×8px）；
  4. 两列卡片在 1040 宽窗口下不会越过滚动条。

改 UI 前先跑一遍这个，能拦住“字体、截断、遮挡”这三类只有进游戏才看得见的问题。

## 8. 推荐工作流

```text
改 UI 代码
→ ./gradlew test（几何 / 字形 / 主题 / 交互断言）

改动涉及每帧绘制量
→ :preview:perf（对比 mean / p95 基线）

视觉/布局
→ :preview:run 或 Style Editor

真实配置/输入
→ 游戏内 F7

布局数值
→ 修改 layout JSON 后重开界面
```

## 9. 调试命令

| 命令 | 作用 |
|---|---|
| `/crashs debug ui` | 打开/关闭界面输入诊断（`[UiDebug]` 前缀） |
| `/crashs debug damage` | 记录服务器伤害文本的位移来源，再执行一次输出统计 |
| `/crashs debug chat` | **新增**：把收到的聊天 / ActionBar 消息按样式分段打印到日志 |

`/crashs debug chat` 的输出（`latest.log`，前缀 `[ChatDebug]`）：

```text
[ChatDebug] chat dup=false len=23 parts=3 text="Player1 获得了 10 金币"
[ChatDebug]   [0] "Player1" color=#ff5555 bold
[ChatDebug]   [1] " 获得了 " 
[ChatDebug]   [2] "10 金币" color=#55ff55 click=RunCommand[command=/back]
```

- 在配置门禁（`enabled` / `includeOverlay`）之前打印，所以关掉功能也能看到全部消息；
- 每段显示 text / color / bold / italic / underlined / strikethrough / obfuscated / click / hover；
- 引号、换行、控制字符会被转义，一条消息保持一行；超长文本截断并加 `…`；
- 单条最多打印 16 段，其余以 `... N more parts` 汇总；
- `dup=true` 表示这是同一个包的第二次投递（被去重拦下）——用来验证去重到底有没有真实触发。

## 10. 聊天处理管线

所有聊天 / ActionBar 消息都从同一个入口进来，业务模块不再各自监听网络包：

```text
ClientPlayNetworkHandlerMixin
  → ChatIntake.onChat(text, overlay, packetKey)
       ├ 去重（packetKey + 内容指纹，窗口 2s；重复投递复用第一次结论）
       ├ 构造 ChatEvent(plain 只算一次)
       ├ ChatPipeline 依次执行
       │    ├ BroadcastChatHandler   自己的 broadcast.enabled 门禁，解析/记录/展示
       │    └ KillFeedChatHandler    config.enabled + includeOverlay，解析奖励、音效、金币
       └ 聚合消费：任一处理器返回 true → cancel 该包（聊天不显示）
```

- **聚合语义**：即使播报已经把这条聊天隐藏，击杀反馈和金币统计**仍然执行**——"看不看得见"和
  "算不算数据"解耦；
- 各处理器自己判断模块开关：播报只看 `broadcast.enabled`，金币统计只看 `coinsInfoEnabled`
  （与击杀反馈总开关完全独立，可只开金币而不开击杀反馈）；
- 金币记账只有一处：`CoinsChatHandler` 用击杀正则在击杀行上记账，击杀处理器不再顺带记账；
  因此金币关掉时既不记账也不启动计时（计时从第一笔记账开始，按墙钟走）；
- 去重表在断线/切服时清空（`ChatIntake.reset()`）；
- `/crashs debug chat` 的 `dup=true` 可以确认去重是否真的触发。

## 11. 注意

预览器是 Windows x64 桌面程序，使用本地 imgui-java / LWJGL native。

预览器和游戏共用 M3 风格，但预览器不执行 Crashs Legacy 的真实配置逻辑。
