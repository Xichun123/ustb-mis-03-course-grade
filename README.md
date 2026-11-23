# 03 组 · 选课及成绩管理系统（USTB 信息系统开发实践 II）

> **公开协作仓库**：`https://github.com/Xichun123/ustb-mis-03-course-grade`  
> **技术栈（全班统一）**：Spring Boot 3 (Java 17+) + Vite + Vue 3 (Element Plus) + MySQL 8.0  
> **数据库基线**：v9（14 表 134 字段、82 条 CHECK，建表与演示数据见 `sql/`）  
> **实施阶段交付 DDL**：**2026-11-24 15:00**（明天 11:30 前合入各模块代码，13:00 前交测试与截图素材）

---

## 一、快速启动（5 分钟跑起来）

### 1. Fork 仓库并切到自己的开发分支（PR 协作模式）
1. 打开 `https://github.com/Xichun123/ustb-mis-03-course-grade`，点击右上角 **Fork** 到自己的 GitHub 账号下；
2. 克隆**你自己账号下的 Fork 仓库**并建分支开发：
```bash
git clone https://github.com/<你的GitHub用户名>/ustb-mis-03-course-grade.git
cd ustb-mis-03-course-grade

# 按分工切出个人分支：
# 张文浩: git checkout -b feat/selection
# 蒋作:   git checkout -b feat/score-admin
# 黎家俊: git checkout -b feat/stats
```

### 2. 配置数据库连接（仅本机生效，严禁把密码提交到公开仓库！）
```bash
cp backend/src/main/resources/application-local.yml.example \
   backend/src/main/resources/application-local.yml
```
打开 `application-local.yml` 填入群里提供的 03 组云数据库地址、账号、密码（该文件已在 `.gitignore` 中）。

### 3. 启动后端（端口 `8080`）
```bash
cd backend
mvn spring-boot:run
```
访问 `http://localhost:8080/api/v1/health`，看到 `"code": "OK"` 且 `"own_v9_tables": 14` 即连通云库。

### 4. 启动前端（端口 `5173`，自动代理 `/api` 到 `8080`）
```bash
cd frontend
npm install
npm run dev
```
浏览器打开 `http://localhost:5173`，右上角可直接切换 13 个演示账号（学生 / 教师 / 学院 / 教务处 / 处长 / 研究生院 / 管理员）。

---

## 二、目录结构与模块认领（每人只改自己目录，零冲突）

```
ustb-mis-03-course-grade/
├── sql/                   v9 权威建表 SQL、只读验收 SQL、联调桩表与初始化测试数据
├── backend/               Spring Boot 3 后端（Java 17）
│   └── src/main/java/com/ustb/coursegrade/
│       ├── common/        【朱鳞太】统一响应、异常处理、演示鉴权 UserContext
│       └── module/
│           ├── prep/      【朱鳞太】业务① 选课准备与发布（1.1–1.5，完整样板）
│           ├── selection/ 【张文浩】业务② 学生选课（2.1–2.7）
│           ├── score/     【蒋作】  业务③ 成绩录入与审核（3.1–3.5）
│           ├── stats/     【黎家俊】业务④ 成绩查询与统计（4.1–4.6）
│           ├── correction/【朱鳞太】业务⑤ 成绩更正申请（5.1–5.4，完整样板）
│           └── admin/     【蒋作】  业务⑥ 系统维护（6.1–6.2）
└── frontend/              Vite + Vue 3 + Element Plus 前端
    └── src/
        ├── api/           各业务接口封装（prep.js / correction.js 为样板）
        ├── layout/        【朱鳞太】统一侧边栏与角色切换顶栏
        └── views/         prep/ selection/ score/ stats/ correction/ admin/
```

---

## 三、开发规范与 AI 辅助写代码约束

1. **直接照样板写**：后端照 `module/prep/`（`PrepController` + `PrepService`），前端照 `views/prep/PrepView.vue` 与 `api/prep.js`。
2. **统一响应与异常**：成功返回 `ApiResponse.ok(data)`；校验/权限/状态错误抛 `BizException.unprocessable(...)` / `forbidden(...)` / `conflict(...)`。
3. **身份获取**：服务端一律调用 `UserContext.requireUser()` / `UserContext.requireRole(...)`，禁止从前端请求体信任学号或工号。
4. **表结构铁律**：所有 SQL 字段名和取值严格以 `sql/` 下的 v9 脚本为准（含 82 条 CHECK，例如 `lesson_class` 合班课堂、`score_audit.lesson_no`、`sc.study_type=3` 辅修不计入主修统计）。
5. **提交 Pull Request**：明天 **11:30 前** `git push -u origin feat/你的分支名` 推到你自己的 Fork，然后在 GitHub 页面点击 **Contribute → Open pull request**，提交 PR 合入 `Xichun123/ustb-mis-03-course-grade` 的 `main` 分支，群里通知组长审合并联调。
