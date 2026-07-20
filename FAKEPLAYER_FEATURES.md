# Lophine 假人功能文档

> 基于 Lophine 26.1.2（Folia fork）的假人 GUI 管理系统，提供 PlayerDoll 风格的完整假人操控体验。

## 仓库地址

https://github.com/qzgeek/Lophine/tree/26.1.2-doll-v2

## 快速开始

```
/bot                  # 打开假人管理 GUI
/bot <名字> spawn      # 创建一个假人
/bot help             # 查看所有命令
```

蹲下 + 空手右键假人 → 直接打开控制面板。

## 核心命令

| 命令 | 说明 |
|------|------|
| `/bot` | 打开假人管理主界面（列表） |
| `/bot gui` | 同上 |
| `/bot help` | 查看中文帮助 |
| `/bot <名字> spawn` | 召唤假人 |
| `/bot <名字> kill` | 杀死假人 |
| `/bot <名字> menu` | 打开该假人的控制面板 |
| `/bot <名字> echest` | 打开假人末影箱 |
| `/bot <名字> tp` | 传送假人到身边 |

### 动作控制

| 命令 | 说明 |
|------|------|
| `/bot <名> sneak / unsneak` | 切换潜行 |
| `/bot <名> sprint / unsprint` | 切换疾跑 |
| `/bot <名> attack [continuous]` | 攻击（连续） |
| `/bot <名> use [continuous]` | 使用物品 |
| `/bot <名> break [continuous]` | 挖掘 |
| `/bot <名> jump` | 跳跃 |
| `/bot <名> drop` | 丢弃主手物品 |
| `/bot <名> swapHands` | 交换主副手 |
| `/bot <名> mount / dismount` | 骑乘/下马 |
| `/bot <名> look <方向>` | 看向方向(north/south/east/west/up/down) |
| `/bot <名> move <方向>` | 移动(forward/backward/left/right) |
| `/bot <名> stop` | 停止所有动作 |
| `/bot <名> actionstop <动作>` | 停止指定动作 |

### 经验系统

| 命令 | 说明 |
|------|------|
| `/bot <名> xp take <点数>` | 从假人取经验点数 |
| `/bot <名> xp level <等级>` | 从假人取经验等级 |
| `/bot <名> xp give <点数>` | 给假人经验点数 |
| `/bot <名> xp level give <等级>` | 给假人经验等级（从自己扣除） |

**经验运算规则**：
- 假人内部只存储 `totalExperience`（经验点数），等级和进度实时推算
- 等级换算使用 Minecraft Wiki 纯整数公式，零浮点误差
- 存取用同一 XP 值，严格守恒
- 公式：0-16级: `L²+6L`，17-31级: `(5L²-81L+720)/2`，32+级: `(9L²-325L+4440)/2`

### 权限与协作者

| 命令 | 说明 |
|------|------|
| `/bot <名> col add <玩家>` | 添加协作者 |
| `/bot <名> col remove <玩家>` | 移除协作者 |
| `/bot <名> col list` | 查看协作者列表 |

权限管理通过 GUI「权限管理」页面操作，支持：
- **公开权限(GSet)**：15 项精细权限控制（背包/末影箱/攻击/使用/挖掘/潜行/疾跑/跳跃/丢弃/换手/骑乘/下马/移动/视角/传送/设置/经验/下线/删除/召唤）
- **个人权限(PSet)**：每位协作者独立的权限覆写
- 支持从在线玩家列表直接添加协作者

### 设置项

每个假人支持独立配置：
- 跳过睡眠 (skip_sleep)
- 始终发送数据 (always_send_data)
- 生成幻翼 (spawn_phantom)
- 死亡不掉落 (keep_inventory)
- 模拟距离 (simulation_distance)
- Tick 类型 (tick_type: ENTITY_LIST/NETWORK)
- 定位栏 (enable_locator_bar)
- 死亡重生 (respawn_on_death)

## GUI 系统

### 假人列表（27 格）
- 显示玩家头像、假人数量/上限
- 在线假人头像 + 离线假人骷髅头
- 支持分页浏览
- "+ 创建假人" 按钮 → 聊天栏输入名字即创建

### 假人主菜单（27 格）
- 假人信息：名称、生命、饱食度、位置、主人
- 功能入口：背包/末影箱/动作/设置/权限/经验/传送/删除

### 动作面板（54 格）
- 状态切换：潜行、疾跑
- 单次动作：攻击/使用/挖掘/跳跃/丢弃/换手
- 连续动作：连续攻击/使用/挖掘（可开关，用 actionstop 停止）
- 特殊动作：骑乘/下马
- 视角控制：6 方向
- 移动控制：4 方向
- 停止全部

### 经验页面（27 格，4 页分页）
- 第 1 页：按等级取经验（1/5/10 级 + 自定义）
- 第 2 页：按点数取经验（100/500/1000 点 + 自定义）
- 第 3 页：按等级给经验（1/5/10 级 + 自定义）
- 第 4 页：按点数给经验（100/500/1000 点 + 自定义）

### 设置页面（27 格，分页）
- 死亡重生开关
- 各项假人配置循环切换

### 权限管理（27 格，分页）
- 公开权限管理入口
- 协作者头像列表（潜行点击移除）
- 点击协作者进入个人精细权限页面
- "添加在线玩家" 按钮打开在线玩家选择器

## 技术架构

### 文件结构

```
lophine-server/src/main/java/
├── fun/bm/lophine/
│   ├── command/player/PlayerCommand.java   # /bot 命令注册和执行
│   ├── config/modules/function/
│   │   └── FakeplayerConfig.java           # 假人全局配置
│   └── carpet/config/modules/
│       └── FakePlayerCompatConfig.java      # Carpet 兼容配置
└── org/leavesmc/leaves/bot/
    ├── BotList.java                        # 假人列表管理（钩子）
    ├── BotOwnerRegistry.java               # 所有权+权限持久化
    ├── ServerBot.java                      # 假人实体（interact 拦截）
    └── gui/
        ├── BotGui.java                     # GUI 完整系统
        └── BotGuiHolder.java              # GUI 容器
```

### 关键修改点

1. **ServerBot.interact()**：蹲下+空手右键时返回 PASS，防止打开原版背包
2. **BotGui.onInteractBot()**：OFF_HAND 事件处理，打开管理面板
3. **BotList**：构造函数初始化 BotOwnerRegistry + 创建时记录所有权 + 放置时恢复
4. **BotOwnerRegistry**：NBT 格式持久化所有权、协作者、GSet/PSet 权限标志
5. **GUI 点击处理器**：所有按钮操作转发为 `/bot` 命令，GUI 作为薄壳

### XP 计算原理

所有 XP 操作均转换为基础点数运算：

```
取 N 级经验：xpCost = totalXpForLevel(botLevel) - totalXpForLevel(botLevel - N)
给 N 级经验：xpCost = totalXpForLevel(playerLevel) - totalXpForLevel(playerLevel - N)
```

- 假人扣除/增加 `xpCost` 后调用 `recalcBotLevel()` 重新推算等级
- 玩家端使用 `giveExp(xpCost)` 精确增减点数
- 公式来源：Minecraft Wiki，纯整数运算，零浮点误差

### 配置项参考

配置从 `lophine_global_config.toml` 的 `[function.fakeplayer]` 段和 `lophine_carpet_config.toml` 的 `[carpet.fakeplayer]` 段加载。

推荐配置：

```toml
[function.fakeplayer]
prefix = "BOT_"
limit = 100
per-player-limit = 5
enable-gui = true
shortcut-enabled = true
resident-fakeplayer = true
manual-save-and-load = true
open-fakeplayer-inventory = true
can-modify-config = true
respawn-on-death = true
```

```toml
[carpet.fakeplayer]
fakePlayerTicksLikeRealPlayer = false   # 必须为 false，否则假人无法交互
commandBot = true
commandPlayer = true
fakePlayerResident = true
openFakePlayerInventory = true
```
