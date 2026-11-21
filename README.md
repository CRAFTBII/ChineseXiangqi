# 中国象棋 · ChineseXiangqi

原生 Android 中国象棋（Java，无第三方依赖，纯 Canvas 自绘 + 自研 Alpha-Beta 引擎）。
界面走**实木棋盘 + 天天象棋式布局**：胡桃木外框、浅木纹棋面、车削木质棋子、上下玩家条与五项底部导航。

## 交付物

- `dist/ChineseXiangqi-1.2.0.apk` — 可直接安装的 release 包（debug 密钥签名，minSdk 24 / targetSdk 34）

## 功能

| 模块 | 说明 |
|---|---|
| 棋盘 | 木框倒角 + 内凹阴影、刻线式网格、九宫斜线、炮位/兵位角标、繁体「楚河 / 漢界」 |
| 棋子 | 车削木质圆片：落影 + 侧壁厚度 + 年轮纹理 + 倒角暗环 + 顶部高光 + 阴刻汉字（红「帥仕相馬車炮兵」黑「將士象馬車砲卒」） |
| 布局 | 上（电脑）/ 下（我）玩家条：圆形头像 + 名称 + 损子统计 + 用时；底部五项导航：菜单 / 棋谱 / 上一步 / 下一步 / 分析 |
| 交互 | 选子金环 + 落点提示（空格绿点、可吃子绿环）；上一步绿角框；将军红环 |
| 棋谱 | 中文记法（兵五进一 / 砲2平5），支持点击任意一手跳转复盘，复盘条常驻右上角 |
| 分析 | 随时对当前局面跑一次搜索，给出建议着法 + 评分 + 深度，并在棋盘上打金色角标 |
| 悔棋 | 一次退回自己上一手（连同电脑应手），支持「下一步」重做 |
| 存档 | 单槽自动存档（走法序列），主菜单直接续局，恢复后仍可悔棋 |
| 历史对局 | 终局自动归档（最多 100 局，含胜负/难度/手数/用时），主菜单「历史对局」点开即只读复盘，可逐手翻页 |
| HUD | 形势评估条（tanh 压缩）、搜索统计（深度/评分/节点/耗时）、回合与计时 |
| 规则引擎 | 车/马(蹩腿)/象(塞眼)/士/将(九宫+照面)/炮(隔子吃)/兵(过河横走)，自杀过滤，将死/困毙/60 回合判和 |
| AI | Negamax + Alpha-Beta + 迭代加深 + 置换表(Zobrist) + PVS + 静态搜索 + 空着裁剪 + LMR + 重复局面检测 |

## 难度

| 档位 | 最大深度 | 时间上限 | 实测（模拟器） |
|---|---|---|---|
| 简单 | 3 | 400 ms | 深度 3 |
| 普通 | 6 | 1800 ms | 深度 6 / 约 8.5 万节点 / 约 0.4 s |
| 困难 | 10 | 5000 ms | 深度 10 / 约 200 万节点 / 约 2.5 s |

## 贴图与授权

棋盘面、外框、桌面、棋子面四张木质贴图由 `tools/TexPrep.java` 生成：

- 原图：**ambientCG** 的 Wood096（浅松木）、Wood092（暖蜜色）、Wood051（深胡桃），
  均为 **CC0 / 公共领域**，允许商用与修改。
- 处理：灰度化 → 目标色板渐变映射 → 缩放 → 输出 768² JPEG；
  棋子面为程序化车削年轮（值噪声 + 同心环 + 偏心髓心），不依赖照片采样。
- 产物在 `app/src/main/res/drawable-nodpi/`，合计约 100 KB；原图留在 `assets_src/`（2.6 MB）。

重新生成：

```bash
javac -encoding UTF-8 -d build/tools tools/TexPrep.java
java -cp build/tools TexPrep assets_src/tex app/src/main/res/drawable-nodpi
```

## 代码结构

```
app/src/main/java/com/workbuddy/xiangqi/
├── MainActivity.java          对局界面、五项导航、复盘/分析/悔棋、AI 调度
├── MenuActivity.java          主菜单（续局 / 三档难度 / 历史对局）
├── GameStore.java             单槽存档（走法序列）
├── GameArchive.java           历史对局归档（终局棋谱，供复盘）
├── game/
│   ├── Piece.java             棋子编码 + 中文名/繁体刻字
│   ├── Move.java              走法 int 压缩编码
│   ├── MoveList.java          走法缓冲
│   ├── Board.java             局面、走子/撤销、FEN、Zobrist
│   ├── Zobrist.java           固定种子随机表
│   ├── MoveGen.java           走法生成、被攻击检测、终局判定
│   └── Notation.java          中文棋谱记法（含前/后）
├── ai/
│   ├── Evaluator.java         子力 + PST + 兵型/缺士象/车路
│   └── SearchEngine.java      Alpha-Beta 搜索引擎
└── ui/
    ├── BoardView.java         棋盘自绘（贴图 + 棋子预渲染位图）
    ├── Theme.java             色板、贴图加载、圆角面板工厂
    ├── EvalBar.java           形势评估条
    ├── IconView.java          底部导航线性图标
    └── AvatarView.java        圆形头像
```

## 构建

环境：JDK 17、Android SDK build-tools 34.0.0、platform android-34。

```bash
bash tools/build_apk.sh
```

脚本流程：`aapt2 compile → aapt2 link → javac → d8 → zip → zipalign → apksigner`，全程离线，不依赖 Gradle 与网络。
（本机 Gradle 离线缓存缺 plugin marker，AGP 无法解析，故构建不依赖 Gradle；工程文件仍保留供 Android Studio 打开。）

## 自检

```bash
javac -encoding UTF-8 -d build/test-classes \
  app/src/main/java/com/workbuddy/xiangqi/game/*.java \
  app/src/main/java/com/workbuddy/xiangqi/ai/*.java tools/EngineTest.java
java -cp build/test-classes EngineTest
```

覆盖 perft 走法计数、马腿/象眼/炮架/照面/兵卒规则、将死判定、AI 搜索。
当前结果：**26 项全部通过**，`perft(1)=44`、`perft(2)=1920`、`perft(3)=79666` 与公开基准值一致。

## 已知限制

- 未做长将/长捉的循环判负，仅以 60 回合无吃子判和
- AI 无开局库与残局库，纯搜索
- 复盘为只读，不能在复盘局面上下棋分支
- release 包使用调试密钥签名，正式分发需替换为自有 keystore
