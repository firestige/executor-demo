# T-030 Phase 2: 核心实现层工作计划

> **开始时间**: 2025-11-27  
> **阶段**: Phase 2 - 核心实现层  
> **状态**: 🚧 进行中  
> **预计完成**: 2 天

---

## 📋 任务清单

### 2.1 提取器重命名（0.5天）✅ 优先
- [ ] 重命名 `JsonFieldExtractor` → `JsonFieldVersionTagExtractor`
- [ ] 重命名 `FunctionFootprintExtractor` → `FunctionVersionTagExtractor`
- [ ] 重命名 `RegexFootprintExtractor` → `RegexVersionTagExtractor`
- [ ] 新增 `JsonPathVersionTagExtractor`（支持深层路径）
- [ ] 保留旧类作为桥接（@Deprecated）

### 2.2 WriteStageBuilderImpl 重构（0.5天）
- [ ] 支持双模式切换（单字段/多字段）
- [ ] 新增多字段相关字段
- [ ] 实现 `hashKey(String)` 返回 HashFieldsBuilder
- [ ] 实现新的 versionTag API
- [ ] 兼容旧的 footprint API

### 2.3 HashFieldsBuilderImpl 实现（0.5天）
- [ ] 创建内部类 HashFieldsBuilderImpl
- [ ] 实现 `field()` / `fields()` 方法
- [ ] 实现 `versionTagFromField()` 方法
- [ ] 实现 `versionTagFromFields()` 方法

### 2.4 AckTask 扩展（0.25天）
- [ ] 新增多字段模式标识
- [ ] 新增 fields Map 字段
- [ ] 新增 versionTagSourceField 字段
- [ ] 新增 fieldLevelExtractor 字段
- [ ] 更新构造函数和 getter

### 2.5 AckExecutor 重构（0.5天）
- [ ] 实现多字段写入逻辑（HMSET）
- [ ] 实现字段级 versionTag 提取
- [ ] 实现多字段组合提取
- [ ] 更新日志输出（footprint → versionTag）
- [ ] 保持向后兼容

---

## 🎯 成功标准

- [ ] 单字段模式正常工作（向后兼容）
- [ ] 多字段模式可以写入 3+ fields
- [ ] 可以从指定 field 提取 versionTag
- [ ] 可以从多个 fields 计算组合签名
- [ ] 编译零错误
- [ ] 单元测试通过（如果有）

---

## 📝 实施顺序

1. **提取器重命名**（优先）- 基础设施
2. **AckTask 扩展** - 数据模型
3. **HashFieldsBuilderImpl** - 构建器实现
4. **WriteStageBuilderImpl** - 主构建器
5. **AckExecutor** - 执行器逻辑

---

**开始时间**: 2025-11-27 11:30

