# 第三方来源（只作参照，不参与构建）

## `skyhigh173-json.js`

- **来源**：TurboWarp 官方扩展库 · `Skyhigh173/json`
  - 仓库：`https://github.com/TurboWarp/extensions/blob/master/extensions/Skyhigh173/json.js`
  - 运行时地址：`https://extensions.turbowarp.org/Skyhigh173/json.js`
  - 扩展 id：`skyhigh173JSON`，版本 v2.6
- **作者**：Skyhigh173 & Mio ｜ **许可**：MIT（许可证与作者信息保留在文件头）
- **取回日期**：2026-11-19

### 它在这里做什么

29 块 JSON 积木是**逐条对齐**这份实现写的。它们现在只有一份实现：
`turbowarp/src/json-lite.js`（游戏内跑构建时复制的 ES5 副本 `json-core.es5.js`），
Java 侧副本已删除（见 `ARCHITECTURE.md` §23）。
这个文件是核对原语义的唯一依据——不是运行时依赖，也不会被打进 jar。

对齐时踩到的语义坑（**改动 JSON 积木前先看这里**）：

| 位置 | 原实现的行为 | 我们的处理 |
|---|---|---|
| 数组下标 | 1 基、负数从末尾数、`0` 与越界给空串 | 照搬 |
| `[json] 中 [item] 的值` 对数组 | 原本按 **0 基**取值 | **改成 1 基**（同一批积木里两套基准更危险），已写进 README 与 §23 |
| 长度积木解析失败 | 返回一个**空格**（明显笔误） | 返回空串 |
| `删除所有` / `项号` | JS 严格相等：`1 ≠ "1"`，对象按引用永不相等 | 用类型严格比较复刻（`strictEquals`） |
| `排序` | 走 `Scratch.Cast.compare`（数值优先，否则不分大小写字典序；空白串强制走文本） | 在 `json-lite.js` 的 `castCompare` 复刻 |
| `抽字段`（原文叫 filter） | 从每个对象取出同一个键 | 照搬（译名改成「抽字段」以免误解） |
| 解析错误文案 | 浏览器 `SyntaxError` 原文 | 中文说明 + 位置；跨引擎无法逐字一致，这是唯一无法对齐的点 |

### 不打算跟进的部分

原扩展共 41 块，本项目实现 32 块中的「核心 + 建议」部分，后合并为 29 块。
未实现的 9 块见 `docs/BACKLOG.md` 的 JSON-04。
