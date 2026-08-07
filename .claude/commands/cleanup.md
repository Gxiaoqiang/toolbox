---
description: 代码库卫⽣清理，每个问题⽣成独⽴修复 PR
allowed-tools: Bash(mvn *), Bash(git *), Bash(gh *)
---
# 任务：代码库卫⽣清理
请执⾏以下检查，对每个发现的问题⽣成独⽴的修复 PR：
## 检查清单
1. **超⻓⽂件**：找出 src/main/java/ 下超过 300 ⾏的 .java ⽂件，拆分为更⼩的类
2. **缺失测试**：找出 src/main/java/ 下没有对应 *Test.java 的类，补充基础测试
3. **未使⽤的 import**：清理所有未使⽤的 import 语句
4. **TODO/FIXME**：列出所有 TODO 和 FIXME，超过 30 天未处理则⽣成清理 PR
5. **重复代码**：找出⾼度相似的代码段（>10⾏），提取为共享⼯具类（infrastructure/）
6. **过时⽂档**：检查 docs/design/ 中状态为 Draft 但已超过 30 天的⽂档
7. **Checkstyle/SpotBugs 历史告警**：清理 mvn verify 中累积的⾮阻塞告警
## 约束
- 每个修复作为独⽴ PR，不要混在⼀起
- 每个 PR 修改后必须确保 `mvn -B clean verify` 通过
- PR 标题格式：`chore(cleanup): [具体描述]`
- 不允许使⽤ Java 9+ 语法（record/var/text blocks），保持 JDK 1.8 兼容
- 不允许升级 Spring Boot 主版本（保持 2.7.x）
- 如果不确定某个修改是否安全，跳过并在 PR 中标注原因