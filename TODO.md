# TODO

## 进行中 (In Progress)
| ID | 任务 | 负责人 | 优先级 | 开始日期 | 预计完成 | 临时方案 |
|----|------|--------|--------|----------|----------|----------|

## 待办 (Backlog)
| ID | 任务 | 负责人 | 优先级 | 备注 |
|----|------|--------|--------|------|
| T-028 | Deploy 配置 Metadata（IDE 智能提示） | - | P3 | spring-configuration-metadata.json，hints，自动补全（T-027 遗留 Phase5） |
| T-026 | Redis ACK 服务 Phase 5 文档补充 | - | P3 | README.md、CHANGELOG.md、扩展指南、性能基准（T-019 遗留） |
| T-023 | 重建测试体系：ack/renew 单元测试；deploy 单元+集成(应用层)+e2e(Facade) | - | P1 | 设计新的测试包结构：deploy:test/unit, test/integration, test/e2e；ack/renew:test/unit |

---

## 使用说明

### 任务生命周期
1. **新建任务**：添加到"待办"区块，分配 ID（T-xxx 格式）
2. **开始任务**：移到"进行中"，在 `docs/temp/` 创建方案文档，并在表格中链接
3. **完成任务**：
   - 从 TODO.md 删除该行
   - 提取方案核心内容合入 `docs/design/` 或 `docs/architecture-overview.md`
   - 在 `developlog.md` 顶部添加完成记录（含任务ID、成果、影响文档）
   - 删除或归档临时方案文档

### 优先级定义
- **P1**：高优先级，影��核心功能或阻塞其他任务
- **P2**：中优先级，重要但不紧急
- **P3**：低优先级，优化改进类

### 任务 ID 规则
- 格式：T-{递增数字}，如 T-101, T-102
- 按创建顺序递增，不重复使用
