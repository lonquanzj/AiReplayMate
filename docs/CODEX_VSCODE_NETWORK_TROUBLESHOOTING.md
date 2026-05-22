# Codex VS Code 重连卡顿排障记录

本文档记录本次 VS Code 中 Codex 反复重连、响应很慢的定位过程和最终配置。后续如果再次出现类似问题，可以按本文档快速复查。

## 现象

VS Code 中 Codex 反复出现类似提示：

```text
Reconnecting... 2/5
timeout waiting for child process to exit
Reconnecting... 3/5
```

后续更明确的表现是：消息要等多次重连后才进来，常见为第 5 次重连后才恢复。

## 关键日志位置

VS Code 会话日志目录：

```powershell
Get-ChildItem "$env:APPDATA\Code\logs" -Directory |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 3 FullName,LastWriteTime
```

Codex 扩展日志一般在最新会话目录下：

```text
C:\Users\lonqu\AppData\Roaming\Code\logs\<最新会话目录>\window1\exthost\openai.chatgpt\Codex.log
```

排查时可以搜索这些关键字：

```powershell
rg "timeout waiting|Reconnecting|startup websocket|child process|analytics|error sending request|websocket|orphan|Persistent process|stream disconnected|falling back to HTTP" "$env:APPDATA\Code\logs\<最新会话目录>"
```

本次定位到的关键日志：

```text
codex_core::session::turn: stream disconnected - retrying sampling request (1/5 ...)
codex_core::session::turn: stream disconnected - retrying sampling request (5/5 ...)
codex_core::client: falling back to HTTP
git ls-remote curated plugins repo failed ... Failed to connect to github.com port 443 after 21092 ms
```

早期还发现过 VS Code 终端持久化导致的孤儿进程恢复：

```text
Persistent process "...": Process had no disconnect runners but was an orphan
Persistent process reconnection
startup websocket prewarm setup failed: timeout waiting for child process to exit
```

## 代理发现

查看 Windows 当前用户代理：

```powershell
Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' |
  Select-Object ProxyEnable,ProxyServer,AutoConfigURL
```

当前机器期望结果：

```text
ProxyEnable = 1
ProxyServer = 127.0.0.1:7897
```

查看本地代理端口是否在监听：

```powershell
Get-NetTCPConnection -State Listen |
  Where-Object { $_.LocalAddress -in @('127.0.0.1','0.0.0.0','::') -and $_.LocalPort -eq 7897 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

查看代理相关进程：

```powershell
Get-Process |
  Where-Object { $_.ProcessName -match 'clash|mihomo|verge|v2ray|sing|tailscale|wireguard' } |
  Select-Object Id,ProcessName,Path
```

本机实际代理来自 Clash Verge / mihomo：

```text
127.0.0.1:7897
```

## 网络验证

直连检查，可能失败或长时间卡住：

```powershell
Test-NetConnection chatgpt.com -Port 443
Test-NetConnection api.openai.com -Port 443
git ls-remote https://github.com/openai/plugins.git HEAD
```

走代理检查，应该能成功：

```powershell
curl.exe -I --proxy http://127.0.0.1:7897 https://chatgpt.com --connect-timeout 10
curl.exe -I --proxy http://127.0.0.1:7897 https://api.openai.com --connect-timeout 10
git -c http.proxy=http://127.0.0.1:7897 ls-remote https://github.com/openai/plugins.git HEAD
```

Git 代理验证成功时应类似：

```text
004da7246d90c695a0ea21e9df6760ecefdedb36    HEAD
```

## 当前配置

推荐优先只配置 VS Code 和 Git 的代理，不配置 Windows 用户环境变量。这样影响范围更小，不会把所有新启动的命令行工具都强制导向 Clash Verge。

如果不想为 VS Code / Git 单独设置代理，可以在 Clash Verge 中开启虚拟网卡模式（TUN / Service Mode，具体名称以当前 Clash Verge 版本为准）。开启后，应用即使没有显式设置 `HTTP_PROXY` / `HTTPS_PROXY`，也可以通过系统网络层走代理。

### VS Code 用户设置

文件位置：

```text
C:\Users\lonqu\AppData\Roaming\Code\User\settings.json
```

相关配置：

```json
{
  "terminal.integrated.enablePersistentSessions": false,
  "terminal.integrated.persistentSessionReviveProcess": "never",
  "http.proxy": "http://127.0.0.1:7897",
  "http.proxySupport": "override",
  "http.proxyStrictSSL": false
}
```

作用说明：

- `terminal.integrated.*`：禁止 VS Code 恢复旧终端，避免 orphan 子进程导致 Codex 启动时等待退出超时。
- `http.proxy`：让 VS Code 和扩展宿主明确走 Clash Verge / mihomo 代理。

### Git 全局代理

文件位置：

```text
C:\Users\lonqu\.gitconfig
```

等价配置：

```ini
[http]
    proxy = http://127.0.0.1:7897
[https]
    proxy = http://127.0.0.1:7897
```

设置命令：

```powershell
git config --global http.proxy http://127.0.0.1:7897
git config --global https.proxy http://127.0.0.1:7897
```

查看命令：

```powershell
git config --global --get-regexp "http.*proxy|https.*proxy"
```

期望输出：

```text
https.proxy http://127.0.0.1:7897
http.proxy http://127.0.0.1:7897
```

### 用户环境变量不推荐配置

本次已删除用户环境变量代理配置。一般不需要配置下面这些用户环境变量：

```text
HTTP_PROXY
HTTPS_PROXY
ALL_PROXY
```

原因：

- 它们会影响所有新启动的命令行进程，范围太大。
- 有些工具对代理变量支持不一致，可能引入新的网络问题。
- VS Code 和 Git 已经可以单独配置代理。
- 如果希望全局透明代理，优先使用 Clash Verge 虚拟网卡模式。

如需确认当前用户环境变量没有配置代理：

```powershell
[Environment]::GetEnvironmentVariable('HTTP_PROXY','User')
[Environment]::GetEnvironmentVariable('HTTPS_PROXY','User')
[Environment]::GetEnvironmentVariable('ALL_PROXY','User')
```

正常情况下应为空。

如果历史上配置过，可以删除：

```powershell
reg delete HKCU\Environment /v HTTP_PROXY /f
reg delete HKCU\Environment /v HTTPS_PROXY /f
reg delete HKCU\Environment /v ALL_PROXY /f
```

不推荐再执行：

```powershell
setx HTTP_PROXY http://127.0.0.1:7897
setx HTTPS_PROXY http://127.0.0.1:7897
setx ALL_PROXY http://127.0.0.1:7897
```

## 重启流程

修改代理配置后，完整关闭 VS Code 和 Codex：

```powershell
Stop-Process -Name Code,codex -Force
```

然后确认 Clash Verge / mihomo 仍在运行，再重新打开 VS Code。

## 其他本地干扰项

本次日志中 CodeGeeX 扩展反复报错：

```text
[AMiner.codegeex] provider FAILED
Illegal value for `line`
```

如果代理配置后 VS Code 仍然明显卡顿，可以先禁用 CodeGeeX 扩展，再单独测试 Codex。

VS Code Java/Gradle 扩展会启动 Gradle Server：

```text
vscjava.vscode-gradle-3.17.3\lib\gradle-server.bat
com.github.badsyntax.gradle.GradleServer
```

Android/Gradle 项目中这是正常现象，但它会增加启动期 CPU 负载。

## 回滚方式

移除 Git 代理：

```powershell
git config --global --unset http.proxy
git config --global --unset https.proxy
```

确认或移除历史用户环境变量代理：

```powershell
reg delete HKCU\Environment /v HTTP_PROXY /f
reg delete HKCU\Environment /v HTTPS_PROXY /f
reg delete HKCU\Environment /v ALL_PROXY /f
```

从下面文件中删除 VS Code 代理配置：

```text
C:\Users\lonqu\AppData\Roaming\Code\User\settings.json
```

删除这些项：

```json
"http.proxy": "http://127.0.0.1:7897",
"http.proxySupport": "override",
"http.proxyStrictSSL": false
```

如果希望恢复 VS Code 终端会话持久化，也删除：

```json
"terminal.integrated.enablePersistentSessions": false,
"terminal.integrated.persistentSessionReviveProcess": "never"
```
