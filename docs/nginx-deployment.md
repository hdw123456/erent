# Nginx 与 HTTPS 部署

## 当前启用范围

- `nginx/default.conf` 是 Compose 当前唯一挂载的 Nginx 配置。
- 宿主机 `8088` 映射到 Nginx 容器 `80`，再把 `/api/` 转发到 `app:8080`。
- `/api/chat/completions/stream` 使用专用 SSE 配置：HTTP/1.1、关闭代理缓冲和缓存，并把上游读取超时延长到 1 小时。
- `nginx/https.conf.example` 只是示例。它不以 `.conf` 结尾，也没有被 Compose 挂载，因此当前不会启用 HTTPS、80 到 443 跳转或占位证书路径。

本地启动和普通接口验证：

```powershell
docker compose up -d --build
curl.exe --noproxy "*" http://localhost:8088/api/health
```

验证 SSE 时使用 `curl -N`（Windows 下是 `curl.exe -N`）。`-N` 会关闭 curl 自身的输出缓冲；只有事件随 Mock 上游的发送间隔逐条出现，才说明代理没有把整段响应攒到最后。

## 同步当前 HTTP 配置到服务器

本地更改提交并推送后，在服务器仓库目录执行：

```bash
git pull --ff-only
docker compose config --quiet
docker compose exec nginx nginx -t
docker compose up -d --no-deps --force-recreate nginx
docker compose ps nginx
docker compose logs --tail=50 nginx
curl -fsS http://127.0.0.1:8088/api/health
```

`nginx -t` 会读取已经更新的 bind mount，并在重建前阻止错误配置上线。当前服务器仍使用旧配置，只有完成上述同步与重建后，本地 SSE 配置才算部署完成。

## 有域名后启用 HTTPS

1. 把域名解析到服务器，并开放安全组和防火墙的 80、443 端口。
2. 申请证书，把证书和私钥放在服务器受保护的目录；不要提交私钥。
3. 复制 `nginx/https.conf.example` 为实际 `.conf`，替换域名和证书路径。
4. 在 Compose 中发布 `443:443`，只读挂载实际 HTTPS 配置和证书目录。
5. 运行 `nginx -t`，通过后再重载或重建 Nginx，并用 `curl -I https://你的域名` 验证。

在这些步骤完成前，不要启用模板中的 HTTP 到 HTTPS 跳转，否则客户端会被重定向到尚不可用的 443 入口。
