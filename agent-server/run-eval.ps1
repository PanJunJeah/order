# 离线评测脚本：无需大模型 API Key，跑全部核心用例
# 用法: pwsh -File run-eval.ps1   （或在 agent-server 目录直接运行 ./run-eval.ps1）

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "== 在线点餐平台 Agent 编排层 · 离线评测 ==" -ForegroundColor Cyan
Write-Host "运行 mvn test（含 13 条核心用例 + HTTP 闸门验证）...`n"

mvn -q "-Dtest=AgentEvalTest,AgentGateHttpTest" test
if ($LASTEXITCODE -eq 0) {
    Write-Host "`n评测全部通过 ✅" -ForegroundColor Green
} else {
    Write-Host "`n评测存在失败 ❌（退出码 $LASTEXITCODE）" -ForegroundColor Red
}
exit $LASTEXITCODE
