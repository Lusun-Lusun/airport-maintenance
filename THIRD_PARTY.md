# 第三方依赖与参考

本项目业务代码为此次独立实现，未复制下列 CMMS 项目源码。检索日期：2026-11-02。

| 项目 | 来源与核对结果 | 使用决策 |
| --- | --- | --- |
| Atlas CMMS / Grashjs/cmms | https://github.com/Grashjs/cmms ，项目页与 LICENSE 原文已获取；Spring Boot + React，AGPL-3.0 | 参考资产、工单、预防性维护等功能边界；不复用源码，前端与本项目 Vue 3 要求不同 |
| RuoYi-Vue | https://github.com/yangzongzhuan/RuoYi-Vue ，项目页与 MIT LICENSE 原文已获取 | 参考后台管理模块组织；本次采用独立轻量实现 |
| Libremaint | https://github.com/lowag/libremaint ，项目页显示 GPL-3.0，PHP7/MySQL，并提供 sample_database.sql | 技术栈不符合；不导入其数据和代码 |

GitHub 搜索：https://github.com/search?q=cmms&type=repositories 已获取搜索结果并沿实际链接核验。
GitHub Search API 遇到 403 限流，因此不把 API 失败结果当作项目证据。
Gitee 搜索：https://search.gitee.com/?q=设备维修&type=repository 仅返回前端入口，未获取可核验的仓库结果；不宣称已完成 Gitee 项目对比。

运行依赖由 Maven 管理：Spring Boot 3.3.5 / Spring Framework（Apache-2.0）、MyBatis（Apache-2.0）、Lettuce（Apache-2.0）、H2（MPL-2.0/EPL-1.0）、JUnit 5（EPL-2.0）、JaCoCo（EPL-2.0）。完整传递列表以 pom.xml 和 Maven 解析结果为准。

前端离线打包 Vue 3.5.13，来源 https://unpkg.com/vue@3.5.13/dist/vue.global.prod.js ，MIT，保留文件头版权声明；许可证全文见 licenses/vue-MIT.txt。

可选本机 Redis 运行工具来源：https://github.com/tporadowski/redis/releases/tag/v5.0.14.1 ，独立下载在工作目录，不作为生产版本选择。部署时建议在 Linux/Docker 使用受支持的 Redis，并按其对应版本许可证配置。Spring Data Redis 客户端用于统计缓存，缓存故障回退数据库，TTL 10 秒。
