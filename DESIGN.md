---
name: "归拢 · 手绘资产手帐"
description: "同一本手帐，两种用笔。"
colors:
  pencil-paper: "#FCFAF4"
  pencil-surface: "#FFFEF9"
  pencil-ink: "#30332D"
  pencil-muted: "#666B5F"
  pencil-line: "#C5CAB8"
  pencil-accent: "#316A94"
  pencil-pale: "#E4EDD9"
  pencil-branch-1: "#397EB5"
  pencil-branch-2: "#809A53"
  pencil-branch-3: "#C08B42"
  pencil-branch-4: "#9873B1"
  pencil-branch-5: "#428F89"
  pencil-branch-6: "#BD7269"
  blue-paper: "#F4F7FA"
  blue-surface: "#FCFDFF"
  blue-ink: "#203C52"
  blue-muted: "#526777"
  blue-line: "#B8C9D5"
  blue-accent: "#275D86"
  blue-pale: "#DFEAF3"
  blue-branch-1: "#3679AA"
  blue-branch-2: "#588DA1"
  blue-branch-3: "#6B78A4"
  blue-branch-4: "#428584"
  blue-branch-5: "#5B8BBA"
  blue-branch-6: "#7C91A3"
  dark-paper: "#202523"
  dark-surface: "#282F2C"
  dark-ink: "#F4F0E5"
  dark-muted: "#BABFB5"
  dark-line: "#515D53"
  dark-accent: "#A4CBE6"
  dark-pale: "#354944"
  dark-branch-1: "#96BCDD"
  dark-branch-2: "#B6CCA2"
  dark-branch-3: "#E3B782"
  dark-branch-4: "#CAB2DC"
  dark-branch-5: "#8CC9C2"
  dark-branch-6: "#E6AEAA"
  positive-alert-light: "#A23D36"
  positive-alert-dark: "#F3B3A7"
  negative-light: "#376A4D"
  negative-dark: "#A3D1B3"
typography:
  display:
    fontFamily: "Patrick Hand, cursive"
    fontSize: "46sp"
    fontWeight: 400
  headline:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "26sp"
    fontWeight: 400
  page-title:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "20sp"
    fontWeight: 400
  section-title:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "17sp"
    fontWeight: 400
  row-title:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "15sp"
    fontWeight: 400
  body:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "14sp"
    fontWeight: 400
  input:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "16sp"
    fontWeight: 400
  button:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "14sp"
    fontWeight: 400
  action:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "13sp"
    fontWeight: 400
  label:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "12sp"
    fontWeight: 400
  caption:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "11sp"
    fontWeight: 400
  timestamp:
    fontFamily: "LXGW WenKai, cursive"
    fontSize: "10sp"
    fontWeight: 400
  metric:
    fontFamily: "Patrick Hand, cursive"
    fontSize: "20sp"
    fontWeight: 400
  balance-row:
    fontFamily: "Patrick Hand, cursive"
    fontSize: "18sp"
    fontWeight: 400
  holding-value:
    fontFamily: "Patrick Hand, cursive"
    fontSize: "19sp"
    fontWeight: 400
  chart-axis:
    fontFamily: "Patrick Hand, cursive"
    fontSize: "10sp"
    fontWeight: 400
  chart-value:
    fontFamily: "Patrick Hand, cursive"
    fontSize: "16sp"
    fontWeight: 400
  sankey-source-value:
    fontFamily: "Patrick Hand, cursive"
    fontSize: "24sp"
    fontWeight: 400
  sankey-branch-value:
    fontFamily: "Patrick Hand, cursive"
    fontSize: "17sp"
    fontWeight: 400
spacing:
  compact: "4dp"
  small: "8dp"
  inline: "12dp"
  row: "16dp"
  panel: "18dp"
  page: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.pencil-pale}"
    textColor: "{colors.pencil-ink}"
    typography: "{typography.button}"
    padding: "10dp 12dp"
    height: "52dp"
  button-secondary:
    backgroundColor: "{colors.pencil-surface}"
    textColor: "{colors.pencil-ink}"
    typography: "{typography.button}"
    padding: "10dp 12dp"
    height: "52dp"
  input:
    backgroundColor: "{colors.pencil-paper}"
    textColor: "{colors.pencil-ink}"
    typography: "{typography.input}"
    padding: "12dp 14dp"
  navigation-active:
    backgroundColor: "{colors.pencil-pale}"
    textColor: "{colors.pencil-accent}"
    height: "32dp"
    width: "56dp"
  range-active:
    backgroundColor: "{colors.pencil-pale}"
    textColor: "{colors.pencil-accent}"
    typography: "{typography.action}"
    height: "48dp"
  market-summary:
    backgroundColor: "{colors.pencil-surface}"
    textColor: "{colors.pencil-ink}"
    padding: "18dp 10dp 18dp 18dp"
  style-choice:
    backgroundColor: "{colors.pencil-surface}"
    textColor: "{colors.pencil-ink}"
    padding: "16dp"
  sankey-source:
    backgroundColor: "{colors.pencil-surface}"
    textColor: "{colors.pencil-ink}"
    typography: "{typography.sankey-source-value}"
  sankey-branch-selected:
    backgroundColor: "{colors.pencil-pale}"
    textColor: "{colors.pencil-ink}"
    typography: "{typography.sankey-branch-value}"
  sheet:
    backgroundColor: "{colors.pencil-surface}"
    textColor: "{colors.pencil-ink}"
---
# Design System: 归拢

## Overview

**Creative North Star: "手绘资产手帐"**

以自然手写文字、纸面和有轻微起伏的边框组织可核对的私人资产。彩铅手帐使用暖纸和彩色铅笔，蓝墨手帐使用冷纸与蓝色墨水；两者共享布局、字体与交互。用户提供的手绘参考是当前视觉方向，旧版 Paper / Graphite / Cobalt 已退出活动规范。

手绘感来自程序化路径和稳定纹理，不来自随机移动的数据边界。信息仍按名称、金额、状态分层；深色模式保持同一结构，目前两种风格共用一套深色配色。网页组件仅是原生界面的设计对照，不能替代 Android 运行验证。

**Key Characteristics:**
- 文楷承载中文，Patrick Hand 承载金额和图表数字。
- 纸纹、自然边框与柔和铅笔填充贯穿主页面、图表、表单和对话框。
- 色带比例保持真实，名称与精确金额始终可核对。
- 界面风格全局保存；金额隐私、系统字号和原生返回保持可用。

提取来源：[SketchStyle.java](app/src/main/java/com/qianwang/privatewealth/SketchStyle.java)、[MainActivity.java](app/src/main/java/com/qianwang/privatewealth/MainActivity.java)、[WealthCharts.java](app/src/main/java/com/qianwang/privatewealth/WealthCharts.java)、[SankeyView.java](app/src/main/java/com/qianwang/privatewealth/SankeyView.java)。产品范围见 [PRODUCT.md](PRODUCT.md)，本轮方向见 [surface.md](docs/surface.md)，交接与证据边界见 [0.5 说明](docs/sketch-v5/documentation.md)。

## Colors

Frontmatter 是颜色的规范来源；`pencil-*` 对应彩铅手帐，`blue-*` 对应蓝墨手帐，`dark-*` 是两种风格目前共用的深色集合。默认选择彩铅手帐，切换不改变数据或布局。

### Primary

`accent` 是文字操作、当前导航与趋势线的蓝色；主按钮使用 `pale` 填充和 `ink` 文字，不再使用旧版钴蓝实心底。`pale` 同时承载已选范围、风格和桑基图分支。

### Secondary

`branch-1` 至 `branch-4` 依次用于银行存款、证券持仓、证券现金、其他资金；按账户查看时按显示顺序使用六色数组。彩铅配色含蓝、橄榄绿、赭黄、紫、青绿、陶红，蓝墨配色集中在蓝与青灰。按账户的颜色随当前金额排序分配，不是永久账户身份。

`positive-alert` 表示红涨及需要关注的删除／缺失行情提示；`negative` 表示绿跌，零变化回到 `muted`。正负号和状态文字不可省略。

**The 色彩有口径 Rule.** 操作、分类与涨跌分别有明确名称和数值，不单独依赖颜色表达意义。

### Neutral

`paper` 承载页面与淡纸纹，`surface` 承载框内表面；`ink` 用于主要文字与桑基图轮廓，`muted` 用于说明及普通控件边框，`line` 用于纸纹、分隔线与图表参考线。纸纹低对比，不能遮挡数字。

## Typography

中文与一般界面文字使用离线附带的 **LXGW WenKai Regular**，金额与图表数字使用 **Patrick Hand**。两种风格共享字体，字体来源及 SIL OFL 许可见 [FONTS.md](docs/FONTS.md)。Manrope 仍保留为历史资产，不再是活动界面字体。

字重统一采用字体常规字重，层级来自字号、颜色与间距。`text()` 关闭额外字体内边距并增加 3dp 行距；金额启用 `tnum`。字号使用 sp，随系统缩放。

- `display` 用于总资产，小数为主体字号的 0.56 倍；主数字在可用宽度内缩放，CNY 独立排列。
- `page-title`、`section-title`、`row-title` 对应页面、分区和明细名称。
- `body`、`label`、`caption`、`timestamp` 承载内容与逐级弱化的辅助信息。
- `metric`、`balance-row`、`holding-value` 用于并列指标、账户余额与持仓市值。
- 桑基图源节点名称为 16sp，金额从 `sankey-source-value` 按宽度缩放；分支名称为 12sp，金额从 `sankey-branch-value` 缩放。图下选中明细金额在 16–24sp 内缩放，精确合计为 17sp。

**The 数字可核对 Rule.** 图内可用万／亿简写，图下保留两位小数的精确合计与选中金额。

## Layout

主内容左右留 24dp，底部固定四栏导航为总览、统计、持仓、账户，每项高 64dp。名称沿左轴，金额沿右轴；账户和持仓仍使用连续列表。可操作文字与图标至少 48dp，按钮高 52dp，输入框至少 50dp。

趋势图在总览高 150dp、统计页高 190dp；分布容器保留左侧环图与右侧图例。趋势按实际日期布局，断档仍以虚线连接，不制造历史。编辑面板由标题、滚动表单、固定保存区组成，随键盘可用空间缩小。

桑基图使用两列：左侧总资产共同源，右侧类别或账户。图高为 250dp 加每个额外分支的间隔；间隔取 58dp 与四行 12sp 字高的较大值。隐藏／零资产使用 180dp 占位。标签区随系统字号和可用宽度变化；长名称在节点内省略，选中明细保留完整名称。暂无独立平板或横屏分栏规则。

## Elevation & Depth

纸面、淡填色和描边形成层次。主按钮取消抬升阴影，编辑面板保留 0.24 的背景遮罩，触摸反馈使用原生涟漪或平台 selectable 背景。没有新增装饰性页面动画；同一个图形重绘时纹理与线条保持稳定。

**The 稳定笔迹 Rule.** 手绘路径是确定的，重绘和选中不能让金额边界跳动。

## Shapes

所有 `shape()` 容器通过 `SketchStyle.frame()` 绘制略微起伏的闭合框。旧调用中的 radius 参数不再决定圆角，不能继续把 10／12／14／24dp 当作活动半径 token。转角控制距离上限为 8dp，波动上限为 1dp，并随小尺寸收缩；它不是规则圆角矩形，因此 frontmatter 不声明虚假的 rounded scale。

普通框线为 1.05dp，桑基图节点框为 1.3dp；色带边线为 1.15dp，环图段边线为 1.1dp。铅笔纹理使用 0.55dp 曲线，间距 2.3dp，低透明度叠在浅填色上。纸纹是淡点状颗粒。资产比例始终由准确的色带厚度与环图角度承载，纹理仅附着在这些区域内。

## Components

### Buttons

主按钮以 `pale` 填充、`ink` 文字和 `muted` 手绘边框呈现；次按钮使用 `surface`。二者沿用 52dp 高度与 10dp／12dp 内边距。文字动作使用 `accent`，保留 48dp 触点与原生反馈。

### Inputs / Fields

EditText 和 Spinner 使用 `paper` 底、自然边框和文楷文字；标签位于字段上方，错误由原生提示呈现。数字键盘、表单滚动、取消与保存行为保持既有逻辑。

### Navigation / Range Controls

四栏导航的选中背景只包住 56×32dp 图标区，并绘制自然边框；整项仍可点按。日期范围、桑基图维度切换和风格选择使用 `pale` 选中态，同时保留文字与 selected 状态。

### Cards / Style Choice

市场摘要与分布容器使用 `surface` 和自然边框，内部保留金额轴线。风格选择卡有名称、短说明、四枚配色样片与「使用中」，在「设置与数据 → 界面风格」切换后立即重绘并保存选择。

### Trends / Distribution

趋势沿用精确日期与金额坐标、点按／拖动选日和断档虚线。环图四类使用铅笔填充和描边，选中仅增加短标记，不放大弧段。金额隐藏时不画趋势、坐标、比例或有效分布弧段；零资产为空环。

### Current Allocation Sankey

源节点包含「总资产」、简写总额、手绘下划线和 100%；每条色带连续连接源节点与一个分支框。类别模式按固定四类展示非零项；账户模式按金额排序，最多六个节点，超过六个时保留前五个与一个其余账户合并节点。两种维度复用同一总额，切换时清除选择。

源边界按累计金额分割，最后一条落在源节点底边，分支厚度不设置视觉最小值。标签框可以为可读性增高，框高不代表比例。色带通常以 55/255 透明度填充、220/255 描边；选择后其他分支降为 12/255 与 40/255，厚度不变。选中框使用 `pale` 底和 `accent` 边。

点源节点或「全部」清除选择；点标签或色带显示精确金额、占比与账户明细。微小色带可通过标签点选。图下始终有「分支合计」精确总额；单账户可管理，合并节点和类别可逐一进入账户。零余额账户不绘制，隐藏金额时移除图形和比例并使用隐藏描述。此图表示当前资产构成，不是历史现金流。

## Do's and Don'ts

### Do:
- Do 使用统一的文楷文字和 Patrick Hand 金额，保留两位小数与分组逗号。
- Do 通过边框、间距和数值层级建立秩序，保持手绘笔迹稳定。
- Do 同时保留分类名称、金额与占比，并区分当前构成与历史变化。
- Do 保留 48dp 操作触点、中文内容描述和隐藏金额状态。
- Do 在原生 Android 检查配色、字号、键盘与长金额，再记录验证结果。

### Don't:
- Don't 沿用旧版 Manrope 或钴蓝实心主按钮作为当前默认。
- Don't 通过增宽小额色带、改变弧段面积或随机抖动数据端点美化比例。
- Don't 把手绘标签框高度当作金额比例，或把当前构成称为历史现金流。
- Don't 将示例、网页预览或仅有源码支持的行为写成真机验证结论。
