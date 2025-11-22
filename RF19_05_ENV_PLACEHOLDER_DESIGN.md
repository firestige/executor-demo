# RF-19-05 环境变量占位符机制设计

**状态**: 待实施  
**优先级**: P0  
**目标**: 用统一语法在 `deploy-stages.yml` 中动态引用环境变量，减少多环境差异配置（IP/端口/命名空间等），支持默认值回退。

---
## 1. 语法规范
| 形式 | 说明 | 示例 | 结果 (假设环境变量) |
|------|------|------|------------------|
| `{$VAR_NAME}` | 必须存在，无默认值 | `{$BG_GW_IP}` | 若未设置 → 启动失败 |
| `{$VAR_NAME:default}` | 有默认值回退 | `{$BG_GW_IP:192.168.1.10:8080}` | 未设置 → 使用 `192.168.1.10:8080` |
| 多占位符组合 | 字符串中出现多次 | `http://{$HOST:localhost}:{$PORT:8080}` | 逐个替换 |

限制：
- `VAR_NAME` 采用 `[A-Z0-9_]+`（与环境变量命名一致）
- 默认值不再嵌套解析（第一版不支持 `{$A:{$B:xxx}}`）
- 不支持转义，若出现特殊语法直接报格式错误

---
## 2. 应用范围
对 YAML 解析完得到的配置对象做深度遍历：
- 所有 `String` 字段
- `List<String>` 元素
- `Map<String,String>` 的 value
- 忽略：数字、布尔、复杂对象非字符串属性

解析位置：`DeploymentConfigLoader.loadConfig()` 之后，`validateConfig()` 之前。

---
## 3. 解析流程
正则：`\{\$([A-Z0-9_]+)(?::([^}]*))?}`
算法：
1. 对每个字符串执行 `Matcher.find()`，循环替换
2. `varName = group(1)`，`defaultVal = group(2)`（可能为 null）
3. `envVal = System.getenv(varName)`；为空时：
   - 若 `defaultVal != null` → 使用默认值
   - 否则记录错误 → 最终抛出异常
4. 支持缓存：`Map<String,String>`；key 为完整占位符文本（如 `{$BG_GW_IP:192...}`）
5. 解析后写回原对象

错误收集：
- 缺失且无默认值的变量列表
- 非法语法（找到了 `{$VAR` 未闭合）

输出：
- 成功：debug 打印解析映射（隐藏 `_SECRET` 值）
- 失败：抛出 `IllegalStateException("Missing env vars: BG_GW_IP, ...")`

---
## 4. 安全策略
- 命名以 `_SECRET` 结尾的变量（如 `REDIS_PASSWORD_SECRET`）解析后日志中显示 `***` 代替真实值
- 可选：引入系统属性优先级顺序：`System.getProperty() > System.getenv() > default`

---
## 5. 扩展预留
后续可支持：
- 管道格式化：`{$VAR|upper:default}`
- 多级回退：`ENV > System Property > Config Server > default`
- 嵌套解析：递归深度限制 3 层

---
## 6. 测试场景
| 场景 | 输入 | 环境变量 | 期望 |
|------|------|----------|------|
| 基本替换 | `{$X:1}` | 无 | `1` |
| 存在覆盖 | `{$X:1}` | `X=2` | `2` |
| 必填缺失 | `{$X}` | 无 | 抛异常 |
| 多占位符 | `A{$X:1}B{$Y:2}` | `X=9` | `A9B2` |
| secret 隐藏 | `{$PWD_SECRET:abc}` | `PWD_SECRET=xyz` | 日志输出 `***` |
| 非法格式 | `{$X:12` | - | 报错 |

---
## 7. 实施步骤
1. 创建 `EnvironmentPlaceholderResolver`（独立组件）
2. 在 `DeploymentConfigLoader` 中注入并调用 `resolve(config)`
3. 增加启动日志输出解析结果统计
4. 单元测试 & 集成测试
5. 文档：新增 `ENV_PLACEHOLDER_GUIDE.md`

---
## 8. 与现有功能的关系
- 与现有 `TemplateResolver` 区分：`TemplateResolver` 处理业务变量（如 `{tenantId}`），此解析器仅处理 `{$ENV...}` 前缀
- 保证执行顺序：先环境变量替换，再业务模板替换（防止 default 中包含业务模板时仍能生效）

---
## 9. 风险与缓解
| 风险 | 描述 | 缓解 |
|------|------|------|
| 环境变量缺失导致启动失败 | 运维未配置 | 支持 `--allow-missing-env=false` 开关；默认严格 |
| 默认值中含有非法字符 | 冒号等引发解析歧义 | 文档限定 default 不再包含 `}` |
| 大量字符串重复解析 | 性能下降 | 加缓存 + 单次遍历 |

---
## 10. 验收标准
- 启动日志展示：解析总数 / 使用默认值数 / 缺失变量数
- 全部测试通过（覆盖率 > 80% ���对解析器）
- 未引入行为回归（现有配置在无占位符时保持原样）

---
**准备就绪，等待实施确认。**

