# 本地 GitLab CI/CD 搭建指南

## 一、启动 GitLab

```powershell
cd deploy\gitlab
docker compose up -d
```

首次启动需要 3-5 分钟初始化。等待完成：
```powershell
docker logs gitlab -f --tail 5
# 看到 "gitlab Reconfigured!" 表示就绪
```

## 二、访问 GitLab

- **地址：** http://localhost:8929
- **用户名：** root
- **初始密码：** 
```powershell
docker exec gitlab grep 'Password:' /etc/gitlab/initial_root_password
```
（密码 24 小时后过期，首次登录后修改）

## 三、创建项目

1. 登录后点击 "New project" → "Create blank project"
2. 项目名：`orange-logistics-platform`
3. Visibility: Private
4. 不勾选 "Initialize repository with a README"

## 四、推送代码

```powershell
cd C:\Users\21779\Desktop\orange-logistics-platform

# 添加 GitLab remote
git remote add gitlab http://localhost:8929/root/orange-logistics-platform.git

# 推送所有分支
git push gitlab --all
```

## 五、注册 Runner

```powershell
# 1. 在 GitLab Web UI 获取 Runner token:
#    Settings → CI/CD → Runners → New project runner
#    复制 registration token

# 2. 注册 Runner
docker exec -it gitlab-runner gitlab-runner register \
  --non-interactive \
  --url "http://gitlab:80" \
  --token "<YOUR_RUNNER_TOKEN>" \
  --executor "docker" \
  --docker-image "docker:24" \
  --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" \
  --docker-network-mode "host" \
  --description "local-docker-runner"
```

## 六、配置 CI/CD 变量

GitLab → Settings → CI/CD → Variables：

| Key | Value | Protected | Masked |
|-----|-------|-----------|--------|
| `HARBOR_REGISTRY` | `localhost:5050` | ✗ | ✗ |
| `HARBOR_USERNAME` | `root` | ✗ | ✗ |
| `HARBOR_PASSWORD` | (你的 GitLab 密码) | ✓ | ✓ |

## 七、触发流水线

推送代码到 develop 分支即可自动触发：
```powershell
git push gitlab develop
```

## 八、镜像仓库

GitLab 自带 Container Registry（端口 5050）：
```powershell
# 登录
docker login localhost:5050 -u root -p <password>

# 镜像地址格式
localhost:5050/root/orange-logistics-platform/gateway:latest
```

## 九、资源占用

- GitLab: ~4-6 GB 内存
- GitLab Runner: ~200 MB
- 磁盘: 初始 ~3 GB，随使用增长

## 十、停止/启动

```powershell
cd deploy\gitlab
docker compose stop    # 停止
docker compose start   # 启动
docker compose down    # 完全删除（数据保留在 volume）
docker compose down -v # 完全删除（含数据）
```
