# a2a-t-sample

`a2a-t-sample` 是 A2A-T Java SDK 的示例模块，包含客户端与服务端两个可直接运行的入口。

当前示例基于 `a2a-java v1.0.0.Beta1` 运行真实的 A2A `HTTP+JSON/REST` 链路：
- `a2a-t-client` 仅用于生成结构化 prompt
- `a2a-t-server` 仅用于校验结构化 prompt

## 入口类

- 客户端：`net.openan.a2at.sample.client.ClientSampleMain`
- 服务端：`net.openan.a2at.sample.server.ServerSampleMain`

## 模块内资源

- 客户端环境模板：`sample/client/client.env.example`
- 服务端环境模板：`sample/server/server.env.example`
- 客户端场景输入：`sample/client/scenario.json`

## 客户端启动

1. 修改仓库根目录下的 `client.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 如需修改默认请求内容，可编辑 `sample/client/scenario.json`
3. 启动客户端：

```bash
java @a2a-t-sample/target/client.javaargs.txt
```

如果不传参数，`ClientSampleMain` 会回退到包内的 `sample/client/client.env.example`。

## 服务端启动

1. 修改仓库根目录下的 `server.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 启动服务端：

```bash
java @a2a-t-sample/target/server.javaargs.txt
```

如果不传参数，`ServerSampleMain` 会回退到包内的 `sample/server/server.env.example`。

## Git Bash 本地调试

先编译打包：

```bash
mvn "-Dmaven.repo.local=.mvn/repository" -pl a2a-t-sample -am -DskipTests package
```

启动服务端：

```bash
java @a2a-t-sample/target/server.javaargs.txt
```

另开一个窗口启动客户端：

```bash
java @a2a-t-sample/target/client.javaargs.txt
```
