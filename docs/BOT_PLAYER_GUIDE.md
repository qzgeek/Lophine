# Lophine 假人玩家手册

这份手册面向普通玩家，介绍服务器内置的 Lophine 假人系统如何使用。

假人是由服务端生成的“类玩家实体”。它可以站在世界里、携带背包、执行动作、保存经验，并通过命令或 GUI 管理。

## 1. 快速开始

### 打开假人主界面

```mcfunction
/bot
```

也可以使用：

```mcfunction
/bot gui
```

主界面会显示：

- 你的在线假人
- 你的离线假人记录
- 创建假人的按钮
- 当前在线/总数限制

### 创建假人

方式一：GUI 创建

1. 输入 `/bot`
2. 点击 `+ 创建假人`
3. 在聊天栏输入假人名字

方式二：命令创建

```mcfunction
/bot spawn <名字>
```

例子：

```mcfunction
/bot spawn test
```

名字要求：

- 4 到 16 个字符
- 只能包含字母、数字、下划线
- 不需要手动加 `BOT_` 前缀，服务端会自动处理

### 打开某个假人的控制面板

```mcfunction
/bot menu <名字>
```

例子：

```mcfunction
/bot menu test
```

也可以直接在 `/bot` 主界面点击假人头像。

## 2. GUI 用法

### 2.1 假人列表

命令：

```mcfunction
/bot
```

页面内容：

- 在线假人：绿色头像
- 离线假人：红色骷髅头
- 点击在线假人：进入控制面板
- 点击离线假人：召唤该假人
- `+ 创建假人`：进入聊天输入创建流程

### 2.2 控制面板

控制面板共 2 页。

第 1 页：

- 背包
- 末影箱
- 动作
- 设置
- 权限管理
- 经验
- 传送到我

第 2 页：

- 删除该假人

### 2.3 背包与末影箱

在控制面板点击：

- `背包`：打开假人背包
- `末影箱`：打开假人末影箱

注意：

- 你需要拥有管理权限
- 距离假人太远时不能打开

### 2.4 动作 GUI

在控制面板点击 `动作`。

动作 GUI 是 54 格界面，先选择：

- `启动动作`：让假人开始执行一个动作
- `停止动作`：查看并停止当前正在运行的动作

启动动作时，按动作树逐层选择。常用动作包括：

- 攻击
- 挖掘
- 使用物品
- 移动
- 跳跃
- 潜行
- 丢弃
- 换手
- 骑乘
- 钓鱼

界面里的 `命令预览` 会显示即将执行的命令。

停止动作时，点击列表中的动作即可停止。

### 2.5 设置面板

在控制面板点击 `设置`。

常见设置：

- 死亡重生
- 跳过睡眠
- 始终发送数据
- 生成幻翼
- 死亡不掉落
- 模拟距离
- Tick 类型
- 定位栏

设置 GUI 只是命令薄壳，点击按钮后会转成 `/bot config ...` 命令。

### 2.6 权限管理

在控制面板点击 `权限管理`。

权限系统分为两层：

- 公开权限：给所有玩家的默认权限
- 个人权限：给某个协作者的单独权限

权限判断顺序：

1. 假人主人 / OP
2. 个人权限
3. 公开权限
4. 默认拒绝

可管理的权限包括：

- 背包
- 末影箱
- 攻击
- 使用
- 挖掘
- 潜行
- 疾跑
- 跳跃
- 丢弃
- 换手
- 骑乘
- 下马
- 移动
- 视角
- 传送
- 设置
- 经验
- 下线保存
- 删除
- 召唤

### 2.7 经验面板

在控制面板点击 `经验`。

经验面板有 4 页：

1. 按等级从假人取经验
2. 按点数从假人取经验
3. 按等级给假人经验
4. 按点数给假人经验

每页都有固定数值按钮和自定义按钮。

点击自定义后，在聊天栏输入数字即可。

注意：

- 假人内部保存的是经验点数
- 等级会按原版 Minecraft 经验公式实时换算

## 3. 常用命令

命令统一格式是：

```mcfunction
/bot <功能> <假人名> [参数]
```

不是旧式的 `/bot <假人名> <功能>`。

### 3.1 基本命令

| 命令 | 说明 |
|---|---|
| `/bot` | 打开假人列表 |
| `/bot gui` | 打开假人列表 |
| `/bot help [页码]` | 查看帮助 |
| `/bot spawn <名字>` | 召唤假人 |
| `/bot spawn <名字> time <秒>` | 延迟召唤假人 |
| `/bot menu <名字>` | 打开控制面板 |
| `/bot tp <名字>` | 把假人传送到你身边 |
| `/bot echest <名字>` | 打开假人末影箱 |
| `/bot inventory <名字>` | 打开假人背包 |
| `/bot save <名字>` | 保存假人数据 |
| `/bot load <名字>` | 从保存数据加载假人，通常仅管理员使用 |

### 3.2 下线与删除

| 命令 | 说明 |
|---|---|
| `/bot kill <名字>` | 下线/终止假人，保留所有权和数据记录 |
| `/bot remove <名字>` | 彻底删除假人，数据不可恢复 |

注意：

- `kill` 不是永久删除
- `remove` 才是彻底删除
- GUI 第 2 页的“删除该假人”对应 `remove`

### 3.3 动作命令

| 命令 | 说明 |
|---|---|
| `/bot sneak <名字>` | 开始潜行 |
| `/bot unsneak <名字>` | 停止潜行 |
| `/bot sprint <名字>` | 开始疾跑 |
| `/bot unsprint <名字>` | 停止疾跑 |
| `/bot attack <名字>` | 攻击一次 |
| `/bot attack <名字> continuous` | 连续攻击 |
| `/bot use <名字>` | 使用一次物品 |
| `/bot use <名字> continuous` | 连续使用物品 |
| `/bot break <名字>` | 挖掘一次 |
| `/bot break <名字> continuous` | 连续挖掘 |
| `/bot jump <名字>` | 跳跃 |
| `/bot drop <名字>` | 丢弃物品 |
| `/bot swapHands <名字>` | 主副手交换 |
| `/bot mount <名字>` | 骑乘附近实体 |
| `/bot dismount <名字>` | 下马 |
| `/bot stop <名字>` | 停止所有动作 |
| `/bot actionstop <名字> <动作名>` | 停止指定动作 |

### 3.4 移动与视角

| 命令 | 说明 |
|---|---|
| `/bot move <名字> forward` | 前进 |
| `/bot move <名字> backward` | 后退 |
| `/bot move <名字> left` | 左移 |
| `/bot move <名字> right` | 右移 |
| `/bot look <名字> north` | 看向北 |
| `/bot look <名字> south` | 看向南 |
| `/bot look <名字> east` | 看向东 |
| `/bot look <名字> west` | 看向西 |
| `/bot look <名字> up` | 向上看 |
| `/bot look <名字> down` | 向下看 |
| `/bot look <名字> at <x> <y> <z>` | 看向指定坐标 |
| `/bot turn <名字> left` | 左转 |
| `/bot turn <名字> right` | 右转 |
| `/bot turn <名字> back` | 后转 |

### 3.5 快捷栏与丢弃

| 命令 | 说明 |
|---|---|
| `/bot hotbar <名字> <1-9>` | 切换快捷栏 |
| `/bot drop <名字> all` | 丢弃全部物品 |
| `/bot drop <名字> mainhand` | 丢弃主手物品 |
| `/bot drop <名字> offhand` | 丢弃副手物品 |
| `/bot drop <名字> <槽位>` | 丢弃指定槽位物品 |

### 3.6 经验命令

| 命令 | 说明 |
|---|---|
| `/bot xp <名字> take <点数>` | 从假人取经验点数 |
| `/bot xp <名字> level <等级>` | 从假人取经验等级 |
| `/bot xp <名字> give <点数>` | 给假人经验点数 |
| `/bot xp <名字> level give <等级>` | 给假人经验等级 |
| `/bot xp <名字> clear` | 清空假人经验 |

例子：

```mcfunction
/bot xp test take 100
/bot xp test level 5
/bot xp test give 100
/bot xp test level give 3
```

### 3.7 配置命令

```mcfunction
/bot config <名字>
/bot config <名字> <设置项> <值>
```

常见设置项：

- `skip_sleep`
- `always_send_data`
- `spawn_phantom`
- `keep_inventory`
- `simulation_distance`
- `tick_type`
- `enable_locator_bar`

例子：

```mcfunction
/bot config test keep_inventory true
/bot config test simulation_distance 8
/bot config test tick_type entity_list
```

### 3.8 协作者命令

| 命令 | 说明 |
|---|---|
| `/bot col <名字> add <玩家>` | 添加协作者 |
| `/bot col <名字> remove <玩家>` | 移除协作者 |
| `/bot col <名字> list` | 查看协作者 |
| `/bot col <名字> add all` | 开启公开协作 |
| `/bot col <名字> remove all` | 关闭公开协作 |

## 4. 快捷交互

如果服务器开启了快捷交互：

- 蹲下
- 主手空手
- 右键假人

会打开假人 GUI。

如果服务器配置为直接打开动作 GUI，则该操作会优先打开动作 GUI；否则打开自定义控制面板。

## 5. 常见问题

### Q: 为什么假人不动、不攻击？

先确认服务器配置：

```toml
fakePlayerTicksLikeRealPlayer = false
```

如果这个值是 `true`，假人可能会表现为能存在、能挨打，但动作队列不执行。

### Q: 为什么我点 GUI 没反应？

常见原因：

- 你不是假人主人
- 你没有协作者权限
- 假人已离线
- 你离假人太远
- 命令被服务器权限插件拦截

### Q: `kill` 和 `remove` 有什么区别？

- `kill`：下线/终止假人，保留数据
- `remove`：彻底删除，数据不可恢复

不确定时优先用 `kill`。

### Q: 为什么命令里有时看到 `BOT_`？

服务端内部会使用完整名，例如 `BOT_test`。
玩家通常只需要输入原名，例如 `test`。

### Q: 假人经验的等级为什么会变化？

假人只保存总经验点数，等级和进度由原版 Minecraft 经验公式换算出来。

## 6. 推荐使用流程

日常使用：

```mcfunction
/bot
```

从 GUI 完成大部分操作。

需要精确控制时使用命令：

```mcfunction
/bot spawn test
/bot menu test
/bot move test forward
/bot stop test
```

要长期保存假人数据时：

```mcfunction
/bot save test
```

要下线但保留数据时：

```mcfunction
/bot kill test
```

确认不再需要时再彻底删除：

```mcfunction
/bot remove test
```
