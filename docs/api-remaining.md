# 剩余能力后端接口文档（B1 批次）

本批次实现范围：标签目录/管理、题目推荐、远程评测账号 CRUD、公告/新闻分类。
所有接口统一返回 `Result<T>`：`{ "code": 200, "msg": "success", "data": ... }`，业务错误通过 `code` 表达（400/401/403/404 等）。
除标注“匿名”外均需登录（网关统一 `Authorization: Bearer <token>`）。
角色通过服务端 Sa-Token 缓存校验，网关对 `/api/admin/**` 额外要求 ADMIN/ROOT。

## 1. 标签目录（problem 服务）

### 1.1 获取标签目录

- `GET /api/tags`
- 角色：登录用户（学生/教师/管理员均可）
- 请求参数：无
- 响应 `data`：`TagVo[]`

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    { "id": 3, "name": "动态规划", "color": "#409EFF", "category": "算法" }
  ]
}
```

- 错误：未登录 401（网关拒绝）。

## 2. 标签管理（problem 服务，ADMIN/ROOT + 题目权限）

网关路由：`/api/admin/tags/**` → `hnieoj-problem`；服务端控制器显式校验角色与 `problem:*` 权限。

公共校验：`name` 非空、trim 后长度 ≤ 50；`color` 长度 ≤ 20；`category` 长度 ≤ 50；名称重复返回业务错误。

### 2.1 创建标签

- `POST /api/admin/tags`
- 角色：ADMIN/ROOT，且具备 `problem:create`
- 请求体：

```json
{ "name": "字符串", "color": "#409EFF", "category": "算法" }
```

- 响应：`data` 为 `null`，`msg = "创建成功"`
- 错误：401 未登录；403 缺少角色或 `problem:create`；400 参数缺失/超长/名称重复

### 2.2 更新标签

- `PUT /api/admin/tags/{id}`
- 角色：ADMIN/ROOT，且具备 `problem:update`
- 请求体：同创建
- 响应：`data` 为 `null`，`msg = "修改成功"`
- 错误：401/403；404 标签不存在；400 参数非法/名称重复

### 2.3 删除标签

- `DELETE /api/admin/tags/{id}`
- 角色：ADMIN/ROOT，且具备 `problem:delete`
- 响应：`data` 为 `null`，`msg = "删除成功"`
- 错误：401/403；404 标签不存在；400 标签已被题目引用（先解除关联再删除，服务不会静默删除 `problem_tag` 关系）
- 并发：删除在事务内 `SELECT ... FOR UPDATE` 锁定 tag 行，与题目标签维护使用同一行锁，避免悬挂引用。

> 原题目 tags 字符串接口（列表筛选项、题目新增/编辑 tags 字段）行为不变。

## 3. 题目推荐（problem 服务）

### 3.1 获取推荐题目

- `GET /api/problems/{problemCode}/recommendations?limit=5`
- 角色：登录用户
- 参数：`limit` 可选，默认 5，范围 1..10；`<=0` 或 `>10` 返回 400
- 行为：
  1. 先按题目详情可见性校验源题；隐藏/私有源无 `problem:update` 权限返回 403，源不存在返回 404（业务码 2001）。
  2. 候选仅 `auth=1`（公开题），排除源题。
  3. 排序：公共标签重合数降序 → 难度距离升序（难度为空的候选排后）→ `id` 升序；源题无难度时退化为标签重合降序 + `id` 升序。
  4. 由 SQL `LIMIT` 截断，不下发私有题名/测试数据。
- 响应 `data`：`ProblemListVo[]`

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 12,
      "problemCode": "P1001",
      "title": "A+B Problem",
      "difficulty": 1,
      "tags": ["基础"],
      "submissionCount": 100,
      "acceptedCount": 60,
      "scorePercentage": 60.00
    }
  ]
}
```

- 错误：401 未登录；400 limit 越界；403 私有源无权限；404 源题不存在；无候选返回 `[]`。

## 4. 远程评测账号管理（submission/judge 服务）

网关路由：`/api/admin/judge/**` → `hnieoj-submission`；控制器显式校验 ADMIN/ROOT。
仅账号 CRUD，不代表已实现 Codeforces/POJ 外站代交。
密码只写不读；`RemoteJudgeAccountVo` 不含密码，请求 DTO 的 `toString` 已排除密码。
公共校验：`oj` trim 后 ≤ 20；`username` trim 后 ≤ 100；`password` ≤ 255；`status` ∈ {0,1}；`maxConcurrency` ∈ [1,100]。

### 4.1 账号列表

- `GET /api/admin/judge/account?oj=&status=`
- 角色：ADMIN/ROOT
- 参数：`oj` 可选（trim 精确匹配）；`status` 可选 0/1
- 响应 `data`：`RemoteJudgeAccountVo[]`（无 password）

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    { "id": 7, "oj": "codeforces", "username": "alice", "status": 1, "maxConcurrency": 2, "gmtCreate": "2026-09-20 10:00:00" }
  ]
}
```

- 错误：401/403；400 status 非 0/1。

### 4.2 创建账号

- `POST /api/admin/judge/account`
- 角色：ADMIN/ROOT
- 请求体：

```json
{ "oj": "codeforces", "username": "alice", "password": "secret", "status": 1, "maxConcurrency": 2 }
```

- `password` 必填；`status`/`maxConcurrency` 缺省时使用数据库默认（1/1）。
- 响应：`data` 为 `null`，`msg = "创建成功"`
- 错误：401/403；400 空/超长/非法 status 或并发上限/(oj,username) 重复
- 并发：依赖 `remote_judge_account(oj,username)` 唯一索引，冲突统一返回业务错误。

### 4.3 更新账号

- `PUT /api/admin/judge/account/{id}`
- 角色：ADMIN/ROOT
- 请求体字段均可选，缺省保留原值；`password` 缺失或空白保留原密码，非空密码原样保存（不 trim）：
  服务端只对显式提供的字段执行局部更新，空白密码根本不写库，避免并发编辑用旧快照回写覆盖密码。

```json
{ "username": "alice2", "password": "", "status": 0, "maxConcurrency": 3 }
```

- 响应：`data` 为 `null`，`msg = "修改成功"`
- 错误：401/403；404 账号不存在；400 非法字段/(oj,username) 重复

### 4.4 删除账号

- `DELETE /api/admin/judge/account/{id}`
- 角色：ADMIN/ROOT
- 响应：`data` 为 `null`，`msg = "删除成功"`
- 错误：401/403；404 账号不存在

## 5. 公告/新闻（announcement 服务）

`category` 取值：`ANNOUNCEMENT`（普通公告，默认）或 `NEWS`（新闻）。
新增缺省 `ANNOUNCEMENT`；修改缺省或空白保留原分类；非法值返回 400。
公开列表/详情始终只返回 `status=ONLINE`；管理端详情仍可读 `OFFLINE`。

### 5.1 前台公告列表

- `GET /api/announcements?page=1&pageSize=10&keyword=&category=`
- 角色：登录用户
- `category` 可选：不传/空白保持旧的全部行为；传 `ANNOUNCEMENT`/`NEWS` 精确过滤；非法值 400
- 响应 `data`：`PageVo<AnnouncementListVo>`，列表项新增 `category`

### 5.2 前台公告详情

- `GET /api/announcements/{id}`
- 角色：登录用户
- 响应 `data`：`AnnouncementDetailVo`，新增 `category`；下线公告按不存在返回 404

### 5.3 管理端公告列表

- `GET /api/admin/announcements?page=1&pageSize=10&keyword=&status=&category=`
- 角色：ADMIN/ROOT
- `status` 可选 0/1；`category` 语义同前台
- 响应：`PageVo<AnnouncementListVo>`

### 5.4 管理端公告详情/创建/更新/删除/改状态

- `GET /api/admin/announcements/{id}`（可读 OFFLINE）
- `POST /api/admin/announcements`（请求体新增可选 `category`，缺省 ANNOUNCEMENT）
- `PUT /api/admin/announcements/{id}`（请求体新增可选 `category`，缺省/空白保留原值）
- `DELETE /api/admin/announcements/{id}`
- `PUT /api/admin/announcements/{id}/status`
- 角色：ADMIN/ROOT
- 非法 `category` 返回 400。

## 6. 网关路由与鉴权

`deploy/nacos/dev/DEFAULT_GROUP/hnieoj-gateway.yaml`：

- `hnieoj-problem` 路由新增 `/api/tags`、`/api/admin/tags/**`。
- 既有 `/api/admin/**` 规则继续要求 ADMIN/ROOT。
- 标签写规则：
  - `POST /api/admin/tags` 需要 `problem:create`
  - `PUT /api/admin/tags/{id}` 需要 `problem:update`
  - `DELETE /api/admin/tags/{id}` 需要 `problem:delete`
- `/api/tags` GET 需登录，登录用户（含学生）可读。
- 匿名访问管理 CRUD 返回 401；学生/低权限管理员返回 403。

## 7. 数据库 schema

本批 schema 已包含在完整初始化脚本 `deploy/mysql/hnieoj_多数据库.sql` 中；安装与重建执行该脚本，执行会重建表。

- `hnieoj_system_db.announcement` 含 `category varchar(20) NOT NULL DEFAULT 'ANNOUNCEMENT'`。
- `hnieoj_judge_db.remote_judge_account` 含唯一索引 `uk_oj_username (oj, username)`。
- `hnieoj_problem_db.problem_tag` 含索引 `idx_tid (tid)`。

---

# B2 批次：站内通知/消息、自助资料/密码、身份资料审核（user 服务）

- 本批接口全部位于 `hnieoj-user`，统一返回 `Result<T>`：`{"code":200,"msg":"success","data":...}`。
- 分页统一 `page>=1`、`1<=pageSize<=100`，返回 `PageVo<T>`：`{"list":[...],"total":N}`。
- 鉴权：网关对 `/api/admin/**` 要求 ADMIN/ROOT；各 MVC 服务由 `common` 的 `SaTokenMvcAutoConfiguration` 统一注册 `SaInterceptor`（`/**`，排除 `/internal/**`），方法上的 `@SaCheckLogin` / `@SaCheckRole` / `@SaCheckPermission` 在服务内生效；本批接口另保留显式登录态/角色校验，形成双层防护。
- 本批新增的 `user_notice` / `user_message` / `user_profile_change` 三表已包含在完整初始化脚本 `deploy/mysql/hnieoj_多数据库.sql` 中。

## B2-1 管理通知（ADMIN/ROOT）

网关路由：`/api/admin/notices`、`/api/admin/notices/**` → `hnieoj-user`。
`targetType` 取值：`USERS`（`targetIds` 为 uid）或 `CLASSES`（`targetIds` 为班级 id）。
`status` 取值：`DRAFT`（草稿，可编辑）或 `PUBLISHED`（已发布，不可再编辑正文/目标）。

### B2-1.1 分页查询通知

- `GET /api/admin/notices?page=1&pageSize=10&keyword=&status=`
- `keyword` 模糊匹配标题；`status` 可选 `DRAFT`/`PUBLISHED`，非法值 400。
- 响应 `data`：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 5,
        "title": "期末考试安排",
        "targetType": "CLASSES",
        "status": "DRAFT",
        "creatorUid": "admin",
        "publishedAt": null,
        "gmtCreate": "2026-09-20 10:00:00",
        "gmtModified": "2026-09-20 10:00:00"
      }
    ],
    "total": 1
  }
}
```

- 错误：401 未登录；403 非 ADMIN/ROOT；400 status/pageSize 非法。

### B2-1.2 通知详情（含已保存目标）

- `GET /api/admin/notices/{id}`
- 响应 `data`（草稿、已发布均可读；`targetIds` 为保存的原样目标设置）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 5,
    "title": "期末考试安排",
    "content": "请查看考场安排",
    "targetType": "CLASSES",
    "targetIds": ["10", "11"],
    "status": "DRAFT",
    "creatorUid": "admin",
    "publishedAt": null,
    "gmtCreate": "2026-09-20 10:00:00",
    "gmtModified": "2026-09-20 10:00:00"
  }
}
```

- 错误：401/403；404 通知不存在。

### B2-1.3 创建通知草稿

- `POST /api/admin/notices`
- 请求体：

```json
{ "title": "期末考试安排", "content": "请查看考场安排", "targetType": "CLASSES", "targetIds": ["10", "11"] }
```

- 校验：`title` 非空且 ≤255；`content` 非空且 ≤20000；`targetType` 必须为 `USERS`/`CLASSES`；`targetIds` 非空、去重后 ≤1000、元素非空；`USERS` 目标必须为真实 uid，`CLASSES` 目标必须为数字班级 id 且真实存在。
- 行为：仅保存为 `DRAFT`，不投递。
- 响应：`{"code":200,"msg":"创建成功","data":5}`（`data` 为通知 id）。
- 错误：401/403；400 targetType/targetIds/title/content 非法；1001 用户不存在；1010 班级不存在。

### B2-1.4 编辑通知草稿

- `PUT /api/admin/notices/{id}`
- 请求体同创建。
- 行为：仅 `DRAFT` 可编辑；已发布返回 400，禁止改正文/目标。
- 响应：`{"code":200,"msg":"修改成功","data":null}`
- 错误：401/403；404 通知不存在；400 已发布或目标非法。

### B2-1.5 删除通知管理记录

- `DELETE /api/admin/notices/{id}`
- 行为：只删除 `user_notice` 记录；**不级联删除**已投递到用户收件箱的 `user_message`。
- 响应：`{"code":200,"msg":"删除成功","data":null}`
- 错误：401/403；404 通知不存在。

### B2-1.6 发布通知

- `POST /api/admin/notices/{id}/publish`
- 行为：
  1. 事务内 `SELECT ... FOR UPDATE` 锁定通知行。
  2. 按发布当时解析收件人快照：`USERS` 取仍存在的 uid；`CLASSES` 取该班级当前所有用户。
  3. 批量写入 `user_message`（标题/正文为发布时快照），通知原子置为 `PUBLISHED`。
  4. 重复发布幂等：已是 `PUBLISHED` 直接返回成功；数据库唯一键 `(notice_id, recipient_uid)` 兜底。
  5. 解析结果无有效收件人时返回 400，绝不伪成功。
- 响应：`{"code":200,"msg":"发布成功","data":null}`
- 错误：401/403；404 通知不存在；400 无有效收件人。
- 说明：正文以纯文本/安全 Markdown 在前端渲染，后端不注入 HTML。

## B2-2 本人站内消息（登录用户）

- uid 一律取自服务端登录态，接口不接受 ownerUid 等可操纵参数。
- 匿名访问由网关/服务端返回 401；非本人消息一律按 404 处理。
- 消息 VO 不包含收件人集合或通知目标。

### B2-2.1 收件箱分页

- `GET /api/user/messages?page=1&pageSize=10&unread=true`
- `unread=true` 仅返回未读；默认返回全部未删除消息。
- 响应 `data`：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 100,
        "noticeId": 5,
        "title": "期末考试安排",
        "content": "请查看考场安排",
        "readAt": null,
        "createdAt": "2026-09-20 10:00:00"
      }
    ],
    "total": 1
  }
}
```

### B2-2.2 未读数

- `GET /api/user/messages/unread-count`
- 响应：`{"code":200,"msg":"success","data":3}`（排除已删除、已读）。

### B2-2.3 标记已读

- `PUT /api/user/messages/{id}/read`
- 本人消息重复标记幂等；他人消息 id 返回 404。
- 响应：`{"code":200,"msg":"已读","data":null}`

### B2-2.4 全部标记已读

- `PUT /api/user/messages/read-all`
- 仅影响本人未删除未读消息。
- 响应：`{"code":200,"msg":"已读","data":null}`

### B2-2.5 删除消息

- `DELETE /api/user/messages/{id}`
- 软删除（写 `deleted_at`）；本人重复删除幂等；他人消息 id 返回 404。
- 响应：`{"code":200,"msg":"删除成功","data":null}`

## B2-3 本人自助修改资料

- `PUT /api/user/profile`
- 角色：登录用户。
- 字段白名单：`username`、`avatar`、`qq`、`github`、`blog`。`uid`/`email`/`phone`/`role`/`password`/`collegeId`/`classId`/`grade`/`realname` 等不在白名单，客户端提交也不会被采用。
- 语义：
  - 字段为 `null`（未传）→ 保留原值，绝不擦除。
  - 空字符串 → 清除该可选项（`username` 除外）。
  - `username` 传入时必须非空，长度按现有用户名校验（默认 2~20）。
  - `avatar` 仅允许 `http`/`https` 绝对地址或以单个 `/` 开头的站内路径；`javascript:`、`data:` 等被拒绝；长度上限 500（DB 列长），超长返回 400 而非 500。
  - `qq` 非空时须匹配 `\d{5,11}`。
  - `github`/`blog` 非空时仅允许 `http`/`https` 链接；长度上限各 255（DB 列长），超长返回 400 而非 500。
- 更新只写请求中显式提供的白名单列（`sql SET` 仅包含这些列），未传字段保持原值；空串通过 `SET column = NULL` 显式清除，不会顺带回写 `password`/身份字段等其他列。
- 请求体示例：

```json
{ "username": "new-name", "avatar": "", "qq": "123456", "github": "https://github.com/me", "blog": "https://blog.example.com" }
```

- 响应：`{"code":200,"msg":"修改成功","data":null}`
- 修改后可 `GET /api/user/profile` 读回真实值。
- 错误：401 未登录；400 `username` 为空、URL 格式非法（含非 http(s) scheme）、`avatar` 长度 >500 或 `github`/`blog` 长度 >255；1006 `username` 长度不在允许范围；1008 QQ 格式错误。

### B2-3.1 个人资料补充字段

`GET /api/user/profile` 在保留原有字段的基础上新增 `collegeId`、`classId`（原 `college` 名称、`class` 名称字段不变）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "uid": "2024001",
    "username": "new-name",
    "email": "a@example.com",
    "phone": null,
    "avatar": "/oj/images/1/avatar.png",
    "qq": "123456",
    "grade": "2024",
    "realname": "张三",
    "cfUsername": null,
    "github": "https://github.com/me",
    "blog": "https://blog.example.com",
    "roles": ["student"],
    "college": "计算机学院",
    "collegeId": 1,
    "class": "软件2401",
    "classId": 10
  }
}
```

## B2-4 本人自助修改密码

- `PUT /api/user/password`
- 角色：登录用户。匿名 401。
- 请求体（密码字段不会出现在 DTO 的 `toString`/日志中）：

```json
{ "oldPassword": "old-pass", "newPassword": "new-pass-123" }
```

- 行为：校验 BCrypt 旧密码与现有新密码长度；事务内 `SELECT ... FOR UPDATE` 锁用户行防并发；保存 BCrypt 新密码；**事务提交后**失效该用户全部会话。旧密码错误时不改库、不踢下线。
- 响应：`{"code":200,"msg":"密码修改成功","data":null}`
- 错误：401 未登录；400 旧/新密码为空或新密码长度非法；1003 旧密码错误。

## B2-5 资料变更申请（身份字段 + 联系/社交字段）

资料变更只有这一套流程，按**申请 id** 审批；`original`/`proposed` 以 JSON 全量快照保存。
可申请字段共 12 个（清单与 `user_info` 列宽见 `ProfileChangeField`）：

| 分组 | 字段 |
| --- | --- |
| 身份字段 | `realname`、`collegeId`、`grade`、`classId` |
| 联系/社交字段 | `username`、`email`、`phone`、`avatar`、`qq`、`cfUsername`、`github`、`blog` |

- 字段全部可选，**只提交需要变更的字段**：未传（`null`）或与当前值相同的字段不进入申请，
  `proposed` 在这些字段上等于 `original`。
- 只有 `original` 与 `proposed` 不同的字段才算「本申请要改的字段」；审批时逐字段做原值一致性校验，
  并只写回这些字段。用户在别处改了无关字段不会让本申请失效。
- 身份字段只要有一项要改，就整体校验四项（实名/学院/年级/班级）齐全且相互匹配。
- 同一用户同时只允许一条 `PENDING` 申请。
- 本人直接修改白名单字段仍走 `PUT /api/user/profile`（见 B2-3），不经过本流程。

已退役、**不再存在**的接口（调用返回 404 `接口不存在`）：
`POST/GET /api/user/profile/change-requests`、`GET /api/users/changes`、
`PUT /api/users/{uid}/changes/approve`、`PUT /api/users/{uid}/changes/reject`、
`PUT /api/users/changes/batch-approve`；对应的 `user_profile_change_apply` 表也已不在初始化脚本中。

### B2-5.1 提交申请（本人）

- `POST /api/user/profile-change-requests`
- 请求体（只列需要变更的字段；身份字段与当前资料快照合并后整体校验四项）：

```json
{ "realname": "张三", "collegeId": 1, "grade": "2024", "classId": 11, "reason": "班级调整" }
```

```json
{ "email": "new@example.com", "qq": "123456", "reason": "换邮箱" }
```

- 校验：`reason` 必填 ≤1000；`realname` ≤50、`grade` ≤20、`username` 按用户名长度规则（默认 2~20）、
  `email` ≤255 且未被他人占用、`phone` ≤20、`avatar` ≤500、`qq` ≤20、`cfUsername` ≤100、
  `github` ≤255、`blog` ≤255；身份字段成组校验学院/班级真实存在且相互匹配、年级匹配；uid 固定为登录态。
- 行为：事务内锁 `user_info` 用户行；已有 `PENDING` 申请返回 400 冲突；没有任何字段实际变化返回 400；
  保存全量 `original`/`proposed` JSON 与 `PENDING` 状态。
- 响应：`{"code":200,"msg":"提交成功","data":null}`
- 错误：401；400 无字段变更/已存在待审申请/字段非法；1002 邮箱已被占用；1006 用户名长度非法；
  1009 学院不存在；1010 班级不存在；1011 年级不存在。

### B2-5.2 本人申请列表

- `GET /api/user/profile-change-requests?page=1&pageSize=10`
- 仅返回本人申请；响应 `data` 为 `PageVo<ProfileChangeVo>`：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "uid": "2024001",
        "original": {
          "realname": "张三", "collegeId": 1, "grade": "2024", "classId": 10,
          "username": "zhangsan", "email": "a@example.com", "phone": null, "avatar": null,
          "qq": null, "cfUsername": null, "github": null, "blog": null
        },
        "proposed": {
          "realname": "张三", "collegeId": 1, "grade": "2024", "classId": 11,
          "username": "zhangsan", "email": "a@example.com", "phone": null, "avatar": null,
          "qq": null, "cfUsername": null, "github": null, "blog": null
        },
        "reason": "班级调整",
        "status": "PENDING",
        "reviewerUid": null,
        "reviewReason": null,
        "reviewAt": null,
        "gmtCreate": "2026-09-20 10:00:00",
        "gmtModified": "2026-09-20 10:00:00"
      }
    ],
    "total": 1
  }
}
```

- 快照中未申请变更的字段保留原始值；`original` 与 `proposed` 相同即表示该字段未申请变更。

### B2-5.3 管理端申请列表（ADMIN/ROOT）

- `GET /api/admin/profile-change-requests?page=1&pageSize=10&status=PENDING&keyword=`
- `status` 可选 `PENDING`/`APPROVED`/`REJECTED`，非法值 400；`keyword` 模糊匹配 uid 或 reason。
- 响应同 B2-5.2 的 `PageVo<ProfileChangeVo>`。
- 错误：401/403。

### B2-5.4 审核通过（ADMIN/ROOT）

- `POST /api/admin/profile-change-requests/{id}/approve`
- 请求体可选：`{ "reason": "同意" }`
- 行为：事务内先锁申请行、再锁用户行（统一锁序）；已通过则幂等返回；已驳回则 400 反向审核拒绝；
  逐字段校验用户当前值仍与申请原值一致，任一不一致返回 400（不用旧申请覆盖新值）；
  通过后原子更新 `user_info` 中本申请涉及的字段与申请状态/审核人/审核时间；
  含身份字段时在提交后清理该用户鉴权缓存（不改变角色/权限行），纯联系/社交字段变更不触发缓存清理。
- 响应：`{"code":200,"msg":"审核通过","data":null}`
- 错误：401/403；404 申请不存在；400 原值已变更/已驳回/申请不含字段变更。

### B2-5.5 审核驳回（ADMIN/ROOT）

- `POST /api/admin/profile-change-requests/{id}/reject`
- 请求体：`{ "reason": "材料不足" }`，`reason` 必填。
- 行为：已驳回幂等返回；已通过则 400 反向审核拒绝；仅更新申请状态/审核人/审核时间/驳回原因，不改动用户资料。
- 响应：`{"code":200,"msg":"已驳回","data":null}`
- 错误：401/403；404 申请不存在；400 驳回原因缺失/已通过。

### B2-5.6 批量审核通过（ADMIN/ROOT）

- `POST /api/admin/profile-change-requests/batch-approve`
- 请求体：

```json
{ "ids": [1, 2, 3] }
```

- 行为：`ids` 去重后**逐条独立审批，每条一个独立事务**。单条失败只回滚该条并记入失败原因，
  已成功的条目保持已提交，整体返回 200。
- 响应：`data` 为成功数与失败明细：

```json
{ "successCount": 2, "failedCount": 1, "failures": [ { "id": "3", "reason": "用户资料已发生变化，申请已失效" } ] }
```

- 错误：401/403；400 `ids` 为空。


## B2-6 网关路由

`deploy/nacos/dev/DEFAULT_GROUP/hnieoj-gateway.yaml` 中 `hnieoj-user` 路由新增：

- `/api/admin/notices`、`/api/admin/notices/**`
- `/api/admin/profile-change-requests`、`/api/admin/profile-change-requests/**`

`/api/user/**` 沿现有路由保持不变。两条新管理路由继续受既有 `/api/admin/**` ADMIN/ROOT 规则约束；服务端控制器再显式校验一次。

## B2-7 数据库 schema

- 本批 schema 已包含在完整初始化脚本 `deploy/mysql/hnieoj_多数据库.sql` 中；安装与重建执行该脚本，执行会重建表。
- `hnieoj_user_db.user_notice`、`hnieoj_user_db.user_message`、`hnieoj_user_db.user_profile_change` 三张表随完整脚本统一创建。
- `user_message` 唯一键 `uk_notice_recipient (notice_id, recipient_uid)` 保证发布幂等；`idx_recipient_created`/`idx_recipient_read` 服务收件箱与未读数查询。
- “同一用户仅一条待审申请”由业务层用户行锁保证，不依赖唯一 pending 索引。
