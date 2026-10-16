# 足迹涂色 · 设计 Tokens 与 Android Compose 组件规格

**方向一句话**：一张冷雾白的勘测图纸，未探索处是深色迷雾，走过的地方以软边揭开地面并留下一条珊瑚色轨迹；界面本身极轻，把整屏留给地图。

- 色彩以 `oklch()` 定义（`assets/styles.css` 为唯一色彩来源），下表的 sRGB 十六进制由该文件逐值换算而来，供 Compose 直接使用。
- 地图为本地绘制（Canvas 2D）的**示意矢量底图**，非真实地图 SDK；轨迹为原创匿名演示数据。
- 深浅两套 token 只替换颜色，字体、间距、圆角、尺寸、动效完全共用。

---

## 1. 色彩 Tokens

### 1.1 基础面与文字

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--bg` | `#F2F7F7` | `#0C1415` | 页面底色（冷雾白 / 深墨） |
| `--surface` | `#FCFEFE` | `#151E20` | 卡片、浮层表面 |
| `--surface-2` | `#ECF2F2` | `#1C2729` | 次级填充、悬停底 |
| `--surface-3` | `#E2EAEA` | `#253032` | 轨道、禁用底 |
| `--fg` | `#1D2C26` | `#E2EAE8` | 主文字（深墨绿 / 雾白） |
| `--fg-2` | `#364540` | `#BDC6C5` | 次级文字、图标 |
| `--muted` | `#505E5B` | `#9DA7A6` | 说明文字、标签（对底 ≥ 4.5:1） |
| `--faint` | `#7D8988` | `#75807F` | 仅用于装饰/禁用，不承载正文 |
| `--border` | `#D8E0DF` | `#2B3537` | 分隔线、卡片描边 |
| `--border-2` | `#C2CCCB` | `#404A4C` | 强调描边、控件边框 |

### 1.2 品牌与强调

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--accent` | `#2A8C78` | `#56B9A2` | 品牌青绿，仅用于填充（开关、主按钮、定位点） |
| `--accent-ink` | `#006653` | `#7BD1BB` | 作正文颜色的强调（链接、选中态文字） |
| `--accent-soft` | `#D5F1E9` | `#123A31` | 强调底 |
| `--on-accent` | `#F7FEFC` | `#051214` | 强调填充上的文字 |

> 每屏强调色最多出现两处（一个主按钮 + 一处选中态），其余入口一律 ghost / 文字链接。

### 1.3 轨迹与状态

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--track` | `#DB6C44` | `#ED845B` | 走过的轨迹线（默认「珊瑚」，可在设置中更换） |
| `--track-ink` | `#A63A14` | `#FD9A73` | 轨迹色作正文 |
| `--track-soft` | `#FFE5D9` | `#4A291B` | 轨迹色底 |
| `--ok` | `#368F62` | `#67B98B` | 正常、已定位 |
| `--warn` / `--warn-ink` | `#CC9230` / `#8F5D00` | `#E3AD52` / `#EDBB6A` | 等待定位、需注意 |
| `--danger` | `#C64944` | `#ED756C` | 删除、错误 |
| `--info` | `#427AA5` | `#72ABD9` | 中性提示 |

可选轨迹色（设置 › 外观 › 轨迹颜色，浅/深各一套）：

| 名称 | value | 浅色 | 深色 |
|---|---|---|---|
| 珊瑚 | `coral` | `#DB6C44` | `#ED845B` |
| 青绿 | `teal` | `#2A8C78` | `#56B9A2` |
| 琥珀 | `amber` | `#C08328` | `#DCAB4F` |
| 靛蓝 | `indigo` | `#5751A8` | `#8C88D6` |
| 绛红 | `rose` | `#C24A5C` | `#DA7484` |

### 1.4 地图（图纸）token

| Token | 浅色 | 深色 |
|---|---|---|
| `--map-land` | `#ECF5F1` | `#1A2224` |
| `--map-land-2` | `#E3F0EB` | `#1E2729` |
| `--map-park` | `#D3EBD8` | `#243428` |
| `--map-green` | `#D9EEE0` | `#213026` |
| `--map-water` | `#C3E2EA` | `#1C363E` |
| `--map-water-2` | `#B4D9E3` | `#1E414D` |
| `--map-road` | `#FCFEFE` | `#414A4C` |
| `--map-road-casing` | `#D2DADB` | `#111719` |
| `--map-road-2` | `#F0F6F6` | `#31393A` |
| `--map-road-2-casing` | `#DAE1E2` | `#111719` |
| `--map-contour` | `#CFDDD5` | `#293334` |
| `--map-block` | `#E8EFED` | `#212A2B` |
| `--map-block-line` | `#D9E2E0` | `#2B3435` |
| `--map-label` | `#576763` | `#9BA8A8` |
| `--map-label-strong` | `#354541` | `#C4CFCE` |
| `--map-label-halo` | `#F6FBFB` | `#0E1517` |
| `--fog` | `#18282B` | `#02080A` |
| `--track-case` | `#F8FDFC` | `#091011` |
| `--loc` / `--loc-halo` | `#29937E` / 22% | `#60C3AB` / 24% |

---

## 2. 中文字体

| Token | 值 |
|---|---|
| `--font-display` | `"Noto Serif CJK SC", "Source Han Serif SC", "Songti SC", "STSong", "SimSun", serif` |
| `--font-ui` | `"Noto Sans CJK SC", "Source Han Sans SC", "PingFang SC", "HarmonyOS Sans SC", "Microsoft YaHei", system-ui, -apple-system, "Segoe UI", sans-serif` |
| `--font-mono` | `ui-monospace, "SFMono-Regular", Menlo, Consolas, monospace` |

- **衬线（宋）只承担"数据"**：大号统计数字、页面标题、月份、日历日号。它给图纸带来勘测/文献气质。
- **无衬线（黑）承担其余全部界面文字**。
- Compose 侧：正文用 `FontFamily.SansSerif`（Android 中文即思源黑体）；标题用 `FontFamily.Serif`，并在 `res/font/` 内置 **Noto Serif SC** 子集以保证各机型一致，`fallback = FontFamily.Serif`。
- 字重只用三档：**400/450 正文 → 550 标签与导航 → 600 标题与按钮**。不使用 700。

### 字号与行高（CJK 已按汉字撑满 em 的特性调整）

| 角色 | 字号 | 行高 | 字距 | 用法 |
|---|---|---|---|---|
| 数据大写 | 26 px | 1.15 | 0 | 探索页「已涂色 %」、总里程 |
| 页标题 H1 | 25 px | 1.35 | 0 | 足迹 / 我的 |
| 卡片标题 | 20 px | 1.4 | 0.01em | 月份、弹层标题 |
| 数据中号 | 21 px | 1.2 | 0 | 三格统计数值 |
| 数据小号 | 18 px | 1.25 | 0 | 当天详情四宫格 |
| 正文 | 15 px | 1.7 | 0 | 段落、说明 |
| 列表主文 | 14.5 px | 1.5 | 0.005em | 设置项标题 |
| 小字 | 13 px | 1.6 | 0.01em | 次级说明、链接 |
| 标签 / 说明 | 11–12 px | 1.5 | 0.02em | 字段名、提示 |
| 全大写标签 | 11 px | 1.5 | **0.09em** | `.k`、`.sect-h` |

> 汉字标题不使用负字距（会挤字），负字距只用于拉丁文字。全大写拉丁必须 ≥ 0.06em。

---

## 3. 间距 / 圆角 / 阴影 / 控件尺寸

| 类别 | Tokens |
|---|---|
| 间距 | `--sp-1 4` `--sp-2 8` `--sp-3 12` `--sp-4 16` `--sp-5 20` `--sp-6 24` `--sp-8 32` `--sp-10 40` |
| 页面左右边距 | `--gutter 16` |
| 圆角 | `--r-xs 5` `--r-sm 9` `--r-md 13` `--r-lg 18` `--r-xl 24` `--r-2xl 30` `--r-full 999` |
| 阴影 1（轻） | `0 1px 2px rgba(0,30,26,.07), 0 1px 1px rgba(0,30,26,.05)` |
| 阴影 2（浮层） | `0 6px 18px rgba(0,30,26,.10), 0 1px 3px rgba(0,30,26,.07)` |
| 阴影 3（弹层） | `0 16px 40px rgba(0,30,26,.18), 0 2px 6px rgba(0,30,26,.08)` |
| 毛玻璃 | `--glass`：表面色 82% + `blur(14px) saturate(1.2)` |
| 最小可点 | `--tap 48`（全局下限） |
| 浮动控件 | `--ctrl 44` + 外扩 2px → **48dp 可点区** |
| 图标 | `--icon 21`（描边 1.7，`stroke-linecap: round`） |
| 安全区 | `--safe-t / --safe-b = env(safe-area-inset-*)` |

**可点区清单（全部 ≥ 44dp）**

| 控件 | 视觉尺寸 | 实际可点 |
|---|---|---|
| 顶部导航项 | 68×44 | 44 |
| 缩放 / 定位按钮 | 44×44 | 48（`::after inset -2`） |
| 月份切换 | 36×36 | 48（`::after inset -6`） |
| 子页返回 | 40×40 | 48（`::after inset -4`） |
| 设置行 | 高 ≥48 | 整行 |
| 开关 | 46×28 | 66×48（`::before inset -10/-8`） |
| 分段控件 | 高 36 | 48（`::after inset -6 0`） |
| 颜色圆点 | 34×34 | 48（`::after inset -7`） |
| 日历格 | ≥48×42（320dp） | ≥48 |
| 按钮 | 高 48 | 48 |

---

## 4. Android Compose 转换

### 4.1 Tokens 对象

```kotlin
object WpTokens {
    // 用 CompositionLocal 或 enableEdgeToEdge + isSystemInDarkTheme() 切换
    data class Palette(
        val bg: Color, val surface: Color, val surface2: Color, val surface3: Color,
        val fg: Color, val fg2: Color, val muted: Color, val faint: Color,
        val border: Color, val border2: Color,
        val accent: Color, val accentInk: Color, val onAccent: Color,
        val track: Color, val ok: Color, val warn: Color, val danger: Color,
        val fog: Color,
    )

    val Light = Palette(
        bg = Color(0xFFF2F7F7), surface = Color(0xFFFCFEFE),
        surface2 = Color(0xFFECF2F2), surface3 = Color(0xFFE2EAEA),
        fg = Color(0xFF1D2C26), fg2 = Color(0xFF364540),
        muted = Color(0xFF505E5B), faint = Color(0xFF7D8988),
        border = Color(0xFFD8E0DF), border2 = Color(0xFFC2CCCB),
        accent = Color(0xFF2A8C78), accentInk = Color(0xFF006653), onAccent = Color(0xFFF7FEFC),
        track = Color(0xFFDB6C44), ok = Color(0xFF368F62), warn = Color(0xFFCC9230),
        danger = Color(0xFFC64944), fog = Color(0xFF18282B),
    )

    val Dark = Palette(
        bg = Color(0xFF0C1415), surface = Color(0xFF151E20),
        surface2 = Color(0xFF1C2729), surface3 = Color(0xFF253032),
        fg = Color(0xFFE2EAE8), fg2 = Color(0xFFBDC6C5),
        muted = Color(0xFF9DA7A6), faint = Color(0xFF75807F),
        border = Color(0xFF2B3537), border2 = Color(0xFF404A4C),
        accent = Color(0xFF56B9A2), accentInk = Color(0xFF7BD1BB), onAccent = Color(0xFF051214),
        track = Color(0xFFED845B), ok = Color(0xFF67B98B), warn = Color(0xFFE3AD52),
        danger = Color(0xFFED756C), fog = Color(0xFF02080A),
    )

    object Space { val s1=4.dp; val s2=8.dp; val s3=12.dp; val s4=16.dp; val s5=20.dp; val s6=24.dp; val s8=32.dp; val s10=40.dp; val gutter=16.dp }
    object Radius { val xs=5.dp; val sm=9.dp; val md=13.dp; val lg=18.dp; val xl=24.dp; val xxl=30.dp; val full=999.dp }
    object Size { val tap=48.dp; val ctrl=44.dp; val icon=21.dp }

    val Display = FontFamily(Font(R.font.noto_serif_sc_medium, FontWeight.Medium), FontFamily.Serif)
    val Ui = FontFamily.SansSerif
}
```

### 4.2 文字样式

```kotlin
object WpType {
    val StatBig   = TextStyle(fontFamily = WpTokens.Display, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
    val H1        = TextStyle(fontFamily = WpTokens.Display, fontSize = 25.sp, lineHeight = 34.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
    val CardTitle = TextStyle(fontFamily = WpTokens.Display, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.2.sp, fontWeight = FontWeight.SemiBold)
    val StatMid   = TextStyle(fontFamily = WpTokens.Display, fontSize = 21.sp, lineHeight = 25.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
    val StatSm    = TextStyle(fontFamily = WpTokens.Display, fontSize = 18.sp, lineHeight = 23.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
    val Body      = TextStyle(fontFamily = WpTokens.Ui, fontSize = 15.sp, lineHeight = 26.sp, letterSpacing = 0.sp)
    val RowTitle  = TextStyle(fontFamily = WpTokens.Ui, fontSize = 14.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val Small     = TextStyle(fontFamily = WpTokens.Ui, fontSize = 13.sp, lineHeight = 21.sp, letterSpacing = 0.13.sp)
    val Caption   = TextStyle(fontFamily = WpTokens.Ui, fontSize = 11.sp, lineHeight = 17.sp, letterSpacing = 0.22.sp)
    // 全大写/标签专用（0.09em）
    val Label     = Caption.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
    // 所有数值文本使用等宽数字，避免跳动
    // TextStyle(fontFeatureSettings = "tnum") 或 platformStyle = TextUnitStyle(TextUnit.TextUnitType.Em)
}
```

### 4.3 组件规格

| 组件 | 规格 | 交互 |
|---|---|---|
| **TopNav**（顶部三段导航） | 悬浮胶囊：`Row`，高 48dp，`RoundedCornerShape(full)`，底 `glass`、边 `1dp border`、阴影 2；每项 68×44dp，选中底 `fg` / 文字 `bg`，未选文字 `muted` | 距顶部 `safeTop+10dp`，水平居中；点击切换 `screen`；不随页面滚动 |
| **MapControls**（缩放/定位） | 右上竖排 `Column(spacing 8dp)`，按钮 44×44dp，`RoundedCornerShape(13dp)`，底 `glass`，图标 21dp | 距底部 150dp 起；放大/缩小以**当前位置**为锚点；定位按钮仅在点击时以 `animateScrollTo` 回到当前位置（无自动回中） |
| **LegendChip** | 左下，两行，11.5sp `muted`，色块 16×8dp | 纯展示，不可点 |
| **OverviewCard** | 底部悬浮卡：`glass` + `RoundedCornerShape(18dp)` + 阴影 2；上排两个指标（左「已涂色」26sp、右「本月里程」18sp，右对齐）；中间 4dp 进度轨（`surface3` 底 + `track` 填充）；下排 12.5sp 说明 + 文字链接「足迹记录 →」 | 链接进入足迹页；进度以真实涂色比例填充 |
| **StatStrip** | `Row` 三等分，`1dp border` 外框 + 内竖分隔；每格 `12dp` 内距，标签 `Label`，数值 `StatMid` + 单位 11sp | 只读 |
| **MonthNav** | `Row` + `SpaceBetween`；左右 36×36dp `IconButton`（外扩到 48）；中间 `CardTitle` + `em` 13sp `muted` | 跨月切换，越界禁用并降透明度 |
| **Calendar** | `LazyVerticalGrid(7)` 固定 6 行；星期头 11sp `faint`；格子 ≥48dp 高，日号 `Display/500`；有记录时下方 4dp 圆条，宽 = `min(20dp, 6dp + km*4dp)`；今天用 1.5dp `accent` 圆环；选中格底 `fg`、字 `bg` | 有/无记录均可点，进入当天详情 |
| **ListRow** | 高 ≥48dp，`16dp` 水平内距，底部 1dp 分隔；左 30×30dp 图标位（`surface2` 底、`r-xs`）；主文 14.5sp，副文 12sp `muted`；右侧为 `Switch` / chevron / 数值 | 悬停/按压底变 `surface2`；危险行文字 `danger` |
| **Switch** | 46×28dp 胶囊，`RoundedCornerShape(full)`；关：`surface3` + `border2`；开：`accent`；滑块 22dp 圆，`2dp` 内距，`animateDpAsState` 平移 18dp | 整行可点；`toggleable(role=Switch)` |
| **SegmentedControl** | `Row` 内嵌，底 `surface2` + `1dp border` + `r-sm`；项高 36dp、`6dp` 圆角；选中底 `surface` + 阴影 1 | 每项外扩 6dp 保证 48dp 可点 |
| **ColorSwatchRow** | 34dp 圆点，内描边 `border`；选中加 2dp `fg` 描边 | 每点外扩 7dp |
| **DayMapCard** | 高 190dp，`r-md`，`clipToOutline`；左上角标签（glass 胶囊 + 轨迹色小图标），右下角比例尺（`r-xs` + 12dp 折线） | 内为独立地图实例，只画当天轨迹并揭开当天迷雾 |
| **KVGrid** | 2 列，`1dp border` 作网格线；每格 11dp/13dp 内距，标签 `Label`，数值 `StatSm` + 单位 11.5sp | 只读 |
| **BottomSheet** | `ModalBottomSheet`，顶部 `36×4dp` 圆角把手，`r-xl` 顶角，`safeBottom+18dp` 内距 | 遮罩 42% 黑；Esc / 点遮罩关闭；打开后焦点落到首个按钮 |
| **Button** | 高 48dp，`r-md`；`primary` 底 `accent` 字 `onAccent`（按压缩放 .98）；`ghost` 底 `surface2` + `border2`；`quiet` 透明 | 同一屏同功能只出现一个 `primary` |
| **Banner** | 顶部提示条：`surface` + `border2` + 阴影 2；图标 18dp `warn`，文字 12.5sp（可换行），右侧文字按钮 + 关闭 | 出现在导航下方 60dp；与定位胶囊互斥 |
| **LocChip** | 左上胶囊，7dp 状态点（`ok`/`warn`/`danger`）+ 12sp `muted` | 与 Banner 互斥 |
| **Overlay(Loading)** | 满屏 `bg` 底 + 26dp 环形进度（`border2` 底、`accent` 顶）+ 标题 + 说明 | 进入地图时出现，`~950ms` 后自动消失 |
| **Overlay(Error)** | 46dp 图标块（`surface2` + `border`，图标 `warn-ink`）+ 标题 + 说明 + 单个 `primary`「重新加载」 | 重试回到 Loading 再回 Ready |
| **EmptyState** | 居中：42dp 线性图标（`faint`）+ 标题 14.5sp + 说明 12.5sp（≤26ch）+ 可选按钮 | 空月份 / 空日期 |
| **Toast / Snackbar** | 底部 92dp 处胶囊，底 `fg` 字 `bg`，12.5sp，1.9s 自动消失 | 改动设置、回到定位、检查更新时反馈 |

### 4.4 战争迷雾揭图（核心算法规格）

必须**连续**、**软边**，禁止方格 / 热力图 / 大片扁平蓝色。

```
每帧：
  1. 离屏 fogCanvas 尺寸 = 画布像素（devicePixelRatio 缩放）
  2. fog.fillStyle = --fog ; fillRect(全屏)
  3. fog.globalCompositeOperation = DESTINATION_OUT
  4. 半径 R = clamp(34 * scale, 22, 260)（当天页 40）
     径向渐变：0 → alpha 1.0 ; 0.62 → 0.96 ; 1 → 0
  5. 沿每条轨迹段在**屏幕坐标**重采样：步长 = max(7, R*0.42)
     每点画一个半径 R 的渐变圆 → 累积挖空，得到连续软边走廊
  6. 当前位置再挖一个半径 R*1.1 的软圆
  7. 主画布 globalAlpha = 0.94（深色 0.90）叠加 fogCanvas
绘制顺序：land → contour → blocks → roads → parks → water → bridges → riversidePath
         → tracks(白描边 + 轨迹色) → location → fog → labels(屏幕坐标，带描边光晕)
```

Compose 落地：`Canvas` + `drawIntoCanvas` 使用 `android.graphics.Canvas` 的 `PorterDuff.Mode.DST_OUT` 与 `RadialGradient`，或用一个 `ImageBitmap` 作离屏层；`drawImage(alpha = 0.94f)` 合成。轨迹长度按**世界坐标（米）**计算，揭示半径固定为世界米数，缩放时走廊宽度按真实比例变化。

### 4.5 状态与交互参数

| 项 | 规格 |
|---|---|
| 页面切换 | 透明度 180ms + `translateY 8dp → 0` 240ms `FastOutSlowIn` |
| 弹层 | `translateY 104% → 0` 260ms |
| 缩放锚点 | 按钮：**当前位置**的屏幕投影（若在视口外则取视口中心）；滚轮/双指：指针/双指中点 |
| 缩放范围 | `0.22 – 6.0`（连续，无级） |
| 平移约束 | 视口边缘至少保留 `35%` 短边，地图不会被拖出屏幕 |
| 视图持久化 | 切页与重开都保留；**只有定位按钮**改变视角中心 |
| 无自动重置 | 除定位按钮外任何操作都不重新居中，快速连点缩放不会漂移 |
| 深色模式 | 跟随系统 / 浅色 / 深色三种；地图与界面共用同一套 token 切换 |
| 动效克制 | 仅位移/透明度/颜色，无弹跳、无缩放动画；`prefers-reduced-motion` 下全部降为 1ms |

---

## 5. 组件树（对应原型 DOM）

```
App
├─ ExploreScreen   (地图铺满 + TopNav + MapControls + LegendChip + OverviewCard
│                   + Overlay[Loading|Error] + Banner|LocChip)
├─ TracksScreen    (PageHead + MonthNav + StatStrip + Calendar + DayList | EmptyState)
├─ MineScreen      (PageHead + StatStrip + 记录/外观/隐私/关于 四组 ListRow)
├─ DayScreen       (SubHead + DayMapCard + KVGrid + 途经 ListRow 组 | EmptyState)
├─ PrivacyScreen   (SubHead + 权限 SegmentedControl + 数据 ListRow 组)
├─ DiagScreen      (SubHead + 运行状态 pills + 原型状态预览 + 日志)
├─ TopNav          (全局悬浮，仅三个主页面显示)
├─ Scrim + BottomSheet (首次授权引导 / 清除确认)
└─ Toast
```
