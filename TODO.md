# TODO

## 进行中 (In Progress)
| ID | 任务 | 负责人 | 优先级 | 开始日期 | 预计完成 | 临时方案 |
|----|------|--------|--------|----------|----------|----------|
| T-028 | 回滚机制完善 | Copilot | P1 | 2025-11-26 | 2025-11-29 | ✅ 设计已核实。[设计](docs/temp/rollback-task-level-design.md) \| [对比](docs/temp/rollback-architecture-comparison.md) \| [总结](docs/temp/T-028-verification-summary.md) |

## 待办 (Backlog)
| ID | 任务 | 负责人 | 优先级 | 备注 |
|----|------|--------|--------|------|
| T-026 | Redis ACK 服务 Phase 5 文档补充 | - | P3 | README.md、CHANGELOG.md、扩展指南、性能基准（T-019 遗留） |
| T-023 | 重建测试体系：ack/renew 单元测试；deploy 单元+集成(应用层)+e2e(Facade) | - | P1 | 设计新的测试包结构：deploy:test/unit, test/integration, test/e2e；ack/renew:test/unit |
| T-029 | 分布式租户串行策略决策（TenantLockManager vs TenantConflictManager） | - | P2 | 参考报告：`docs/report/executor-stages-properties-analysis.md`、`docs/report/task-017-cleanup-completion-report.md`、`docs/report/executor-stages-cleanup-summary.md`；根据需求选择接入分布式锁或保留本地策略，并更新接口一致性（release 持有者校验）。 |

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
