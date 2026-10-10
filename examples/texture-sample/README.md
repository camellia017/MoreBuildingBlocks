# 纹理示例投放处

把台阶 / 楼梯的纹理示例放进这个目录，我会直接读（不需要改代码，也不用跑 datagen）。

**详细规则、坐标约定、可整段照抄的示例见 [`docs/texture-sample.md`](../../docs/texture-sample.md)。**

## 放什么

| 文件 | 用途 |
| --- | --- |
| `<方块>_<状态>.json` | 模型 JSON（推荐做法）：照抄 `docs/texture-sample.md` §2 的骨架，只改你认为不对的数字 |
| `textures/<名字>.png` + 同名 `.md` | 手切的贴图，说明里写清「贴到哪个方块的哪个面、取原图哪一块」 |
| `<任意>.png` / `<任意>.md` | 截图或文字描述现状哪里不对（可选，但很有帮助） |

## 每个示例请带上这四项

1. **方块名**：例如 `morebuildingblocks:white_glazed_terracotta_slab`
2. **状态**：例如「底面半砖，`glazed_facing=north`」「楼梯 facing=east，half=bottom，shape=straight」
3. **期望**：一句话就够（例如「图案的尖角应朝向观察者」）
4. **示例本体**：模型 JSON，或 PNG + 映射说明

## 命名参考

- 普通：`white_terracotta_slab`、`white_terracotta_slab_top`、`white_terracotta_stairs`、`white_terracotta_stairs_inner`、`white_terracotta_stairs_outer`
- 带图案：在上面加 `_facing1` / `_facing2` / `_facing3`（对应 `glazed_facing=west` / `north` / `east`；`south` 无后缀）
- 竖半砖：`vertical_slab/<方块>/{pos_x,neg_x,pos_z,neg_z}[_facingN]`
