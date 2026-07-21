# CheeseIM Helm 基线

该 Chart 部署七个独立服务，不部署 Mongo、Redis、Kafka、Nacos，也不创建 Secret。
中间件必须由生产级托管服务或独立集群提供。

## 发布前

1. 为每个服务创建独立 Secret，名称与 `values.yaml` 的 `secretName` 一致；
2. Secret 只放该服务所需凭据，禁止把 authcenter JWT/identity assertion secret 注入其它 Pod；
3. 覆盖 Nacos namespace、Kafka、Redis 拓扑；
4. 使用不可变 image digest；`dev` tag 只用于渲染示例；
5. 按压测结果调整 replicas/resources，默认值不是百万 DAU 容量证明。
6. 在应用发布前运行 `distro/create-im-topics.sh` 或等价 IaC；Pod 默认无 Kafka topic DDL 权限，
   但启动会校验所有主 topic/DLT 的分区、副本、minISR 和 retention。

最低 Secret 键：

| 服务 | 必需/按功能启用的键 |
| --- | --- |
| api-server | `REDIS_PASSWORD` |
| authcenter | `MONGODB_URI`、`REDIS_PASSWORD`、`CHEESEIM_AUTH_JWT_SECRET`、登录 assertion secret |
| business | `MONGODB_URI`、`REDIS_PASSWORD` |
| postoffice | `MONGODB_URI`、`REDIS_PASSWORD` |
| postbox | `MONGODB_URI`、`REDIS_PASSWORD` |
| postmaster | `MONGODB_URI`、`REDIS_PASSWORD` |
| postman | `MONGODB_URI`、`REDIS_PASSWORD`、启用厂商所需 push secret |

当前 common-core 仍让部分服务装配并非其领域所有的 Mongo client，因此表中暂时保留 `MONGODB_URI`；
后续 adapter 模块拆分完成后应按实际数据所有权进一步收紧。

## 渲染与安装

```bash
helm lint distro/helm/cheeseim
helm template cheeseim distro/helm/cheeseim \
  --namespace cheeseim \
  --values values-production.yaml > rendered.yaml
kubectl apply --dry-run=server -f rendered.yaml
helm upgrade --install cheeseim distro/helm/cheeseim \
  --namespace cheeseim --create-namespace \
  --values values-production.yaml
```

Chart 使用 `maxUnavailable=0/maxSurge=1`、PDB、双 topology spread、read-only root filesystem、
非 root UID 10001、禁用 ServiceAccount token、liveness/readiness/startup probes 和 preStop 延迟。
postoffice 使用 StatefulSet 和 headless Service，以稳定 ordinal Pod 名作为 gateway nodeId；
其它无状态模块使用 Deployment。不要把 postoffice 改回随机 Pod 名的 Deployment，除非先提供旧 node queue
接管/重路由机制。

business/postmaster 因包含进程内 write-behind，默认使用 120 秒 termination grace 与 15 秒
preStop；writer 最多等待当前批次 30 秒后 drain 队列。生产需用 oldest-age 与退出耗时演练校准，
不能把默认值视为零丢失保证。

若集群已安装 Prometheus Operator CRD，可显式启用监控对象：

```yaml
monitoring:
  serviceMonitor:
    enabled: true
    additionalLabels:
      release: kube-prometheus-stack
  prometheusRule:
    enabled: true
    additionalLabels:
      release: kube-prometheus-stack
    writerOldestAgeWarningMilliseconds: 5000
    writerOldestAgeCriticalMilliseconds: 30000
```

默认关闭，避免未安装 `ServiceMonitor/PrometheusRule` CRD 的集群安装失败。ServiceMonitor 只选择带
`cheeseim.io/metrics=true` 的七个普通 Service，不会误抓 postoffice headless Service；
规则按 release namespace 限定查询，并校验 critical 必须大于 warning。Prometheus 所在 namespace
还必须匹配 `networkPolicy.monitoringNamespaceSelector`。

NetworkPolicy 默认启用：

- 同 namespace Pod 只访问 Dubbo/内部 business HTTP；
- ingress namespace 只访问 api-server HTTP 与 postoffice TCP/WS；
- monitoring namespace 只访问 management；
- 不限制 egress，避免在未列全 Nacos/Kafka/Mongo/Redis/DNS 地址前误断主链路。

部署前必须按集群真实 namespace label 修改两个 selector。CNI 对 node→Pod 探针流量的处理也必须在预发布验证。
