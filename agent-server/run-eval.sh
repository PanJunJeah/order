#!/usr/bin/env bash
# 离线评测脚本：无需大模型 API Key，跑全部核心用例
set -e
cd "$(dirname "$0")"
echo "== 在线点餐平台 Agent 编排层 · 离线评测 =="
mvn -q -Dtest=AgentEvalTest,AgentGateHttpTest test
echo "评测全部通过 ✅"
