
## proxy_protocol
* if you use haproxy or nginx as L4-layer-proxy of camellia-redis-proxy
* you can use proxy_protocol for camellia-redis-proxy to get the real ip address of the client
* if proxy-protocol-enable=true, you can not access redis-proxy direct by redis resp2 protocol anymore

```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  console-port: 16379
  password: pass123
  proxy-protocol-enable: true
  transpond:
    type: local
    local:
      type: simple
      resource: redis://@127.0.0.1:6379
```