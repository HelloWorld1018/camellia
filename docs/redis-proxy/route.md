## 路由配置
路由配置表示了camellia-redis-proxy在收到客户端的redis命令之后的转发规则

## 大纲
* 最简单的示例
* 支持的后端redis类型
* 动态配置
* json-file配置示例（双写、读写分离、分片等）
* 集成camellia-dashboard
* 不同的双（多）写模式
* 自定义分片函数

### 最简单的示例
在application.yml里配置如下信息：
```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  password: pass123
  transpond:
    type: local
    local:
      type: simple
      resource: redis-cluster://@127.0.0.1:6379,127.0.0.1:6378,127.0.0.1:6377
```
上面的配置表示proxy的端口=6380，proxy的密码=pass123，代理到后端redis-cluster集群，地址串=127.0.0.1:6379,127.0.0.1:6378,127.0.0.1:6377

### 支持的后端redis类型
我们通过url的方式来描述后端redis服务器，支持普通单点redis、redis-sentinel、redis-cluster三种类型，具体的url格式如下：

* 普通单点redis
```
##有密码
redis://passwd@127.0.0.1:6379
##没有密码
redis://@127.0.0.1:6379
```

* redis-sentinel
```
##有密码
redis-sentinel://passwd@127.0.0.1:16379,127.0.0.1:16379/masterName
##没有密码
redis-sentinel://@127.0.0.1:16379,127.0.0.1:16379/masterName
```

* redis-cluster
```
##有密码
redis-cluster://passwd@127.0.0.1:6379,127.0.0.2:6379,127.0.0.3:6379
##没有密码
redis-cluster://@127.0.0.1:6379,127.0.0.2:6379,127.0.0.3:6379
```

* redis-sentinel-slaves
```
##本类型的后端只能配置为读写分离模式下的读地址

##不读master，此时proxy会从slave集合中随机挑选一个slave进行命令的转发
##有密码
redis-sentinel-slaves://passwd@127.0.0.1:16379,127.0.0.1:16379/masterName?withMaster=false
##没有密码
redis-sentinel-slaves://@127.0.0.1:16379,127.0.0.1:16379/masterName?withMaster=false

##读master，此时proxy会从master+slave集合中随机挑选一个节点进行命令的转发（可能是master也可能是slave，所有节点概率相同）
##有密码
redis-sentinel-slaves://passwd@127.0.0.1:16379,127.0.0.1:16379/masterName?withMaster=true
##没有密码
redis-sentinel-slaves://@127.0.0.1:16379,127.0.0.1:16379/masterName?withMaster=true

##redis-sentinel-slaves会自动感知：节点宕机、主从切换和节点扩容
```

### 动态配置
如果你希望你的proxy的路由配置可以动态变更，比如本来路由到redisA，然后动态的切换成redisB，那么你需要一个额外的配置文件，并且在application.yml中引用，如下：
```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  password: pass123
  transpond:
    type: local
    local:
      type: complex
      dynamic: true
      check-interval-millis: 3000
      json-file: resource-table.json
```
上面的配置表示：
* proxy的路由转发规则来自于一个配置文件（因为在文件里可以自定以配置双写、分片以及各种组合等，所以叫复杂的complex），叫resource-table.json  
* dynamic=true表示配置是动态更新的，此时proxy会定时检查resource-table.json文件是否有变更（默认5s间隔，上图配置了3s），如果有变更，则会重新reload    
* proxy默认会优先去classpath下寻找名称叫resource-table.json的配置文件    
* 此外，你也可以直接配置一个绝对路径，proxy会自动识别这种情况，如下：
```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  password: pass123
  transpond:
    type: local
    local:
      type: complex
      dynamic: true
      check-interval-millis: 3000
      json-file: /home/xxx/resource-table.json
```

### json-file配置示例

#### 配置单个地址
使用单独配置文件方式进行配置时，文件一般来说是一个json文件，但是如果你的配置文件里只写一个地址，也是允许的，proxy会识别这种情况，如下：
```
redis://passwd@127.0.0.1:6379
```
配置文件里只有一行数据，就是一个后端redis地址，表示proxy的路由转发规则是最简单的形式，也就是直接转发给该redis实例  
此时的配置效果和在application.yml里直接配置resource地址url效果是一样的，但是区别在于使用独立配置文件时，该地址是支持动态更新的

#### 配置读写分离一
```json
{
  "type": "simple",
  "operation": {
    "read": "redis://passwd123@127.0.0.1:6379",
    "type": "rw_separate",
    "write": "redis-sentinel://passwd2@127.0.0.1:6379,127.0.0.1:6378/master"
  }
}
```
上面的配置表示：  
* 写命令会代理到redis-sentinel://passwd2@127.0.0.1:6379,127.0.0.1:6378/master  
* 读命令会代理到redis://passwd123@127.0.0.1:6379

可以看到json里可以混用redis、redis-sentinel、redis-cluster  

#### 配置读写分离二
```json
{
  "type": "simple",
  "operation": {
    "read": "redis-sentinel-slaves://passwd123@127.0.0.1:26379/master?withMaster=true",
    "type": "rw_separate",
    "write": "redis-sentinel://passwd123@127.0.0.1:26379/master"
  }
}
```
上面的配置表示：  
* 写命令会代理到redis-sentinel://passwd123@127.0.0.1:26379/master  
* 读命令会代理到redis-sentinel-slaves://passwd123@127.0.0.1:26379/master?withMaster=true，也就是redis-sentinel://passwd123@127.0.0.1:26379/master的主节点和所有从节点

#### 配置分片
```json
{
  "type": "shading",
  "operation": {
    "operationMap": {
      "0-2-4": "redis://password1@127.0.0.1:6379",
      "1-3-5": "redis-cluster://@127.0.0.1:6379,127.0.0.1:6380,127.0.0.1:6381"
    },
    "bucketSize": 6
  }
}
```
上面的配置表示key划分为6个分片，其中： 
* 分片[0,2,4]代理到redis://password1@127.0.0.1:6379
* 分片[1,3,5]代理到redis-cluster://@127.0.0.1:6379,127.0.0.1:6380,127.0.0.1:6381

#### 配置双（多）写
```json
{
  "type": "simple",
  "operation": {
    "read": "redis://passwd1@127.0.0.1:6379",
    "type": "rw_separate",
    "write": {
      "resources": [
        "redis://passwd1@127.0.0.1:6379",
        "redis://passwd2@127.0.0.1:6380"
      ],
      "type": "multi"
    }
  }
}
```
上面的配置表示：  
* 所有的写命令（如setex/zadd/hset）代理到redis://passwd1@127.0.0.1:6379和redis://passwd2@127.0.0.1:6380（即双写），特别的，客户端的回包是看的配置的第一个写地址  
* 所有的读命令（如get/zrange/mget）代理到redis://passwd1@127.0.0.1:6379  

#### 配置多读
```json
{
  "type": "simple",
  "operation": {
    "read": {
      "resources": [
        "redis://password1@127.0.0.1:6379",
        "redis://password2@127.0.0.1:6380"
      ],
      "type": "random"
    },
    "type": "rw_separate",
    "write": "redis://passwd1@127.0.0.1:6379"
  }
}
```
上面的配置表示：  
* 所有的写命令（如setex/zadd/hset）代理到redis://passwd1@127.0.0.1:6379  
* 所有的读命令（如get/zrange/mget）随机代理到redis://passwd1@127.0.0.1:6379或者redis://password2@127.0.0.1:6380

#### 混合各种分片、双写逻辑
```json
{
  "type": "shading",
  "operation": {
    "operationMap": {
      "4": {
        "read": "redis://password1@127.0.0.1:6379",
        "type": "rw_separate",
        "write": {
          "resources": [
            "redis://password1@127.0.0.1:6379",
            "redis://password2@127.0.0.1:6380"
          ],
          "type": "multi"
        }
      },
      "5": {
        "read": {
          "resources": [
            "redis://password1@127.0.0.1:6379",
            "redis://password2@127.0.0.1:6380"
          ],
          "type": "random"
        },
        "type": "rw_separate",
        "write": {
          "resources": [
            "redis://password1@127.0.0.1:6379",
            "redis://password2@127.0.0.1:6380"
          ],
          "type": "multi"
        }
      },
      "0-2": "redis://password1@127.0.0.1:6379",
      "1-3": "redis://password2@127.0.0.1:6380"
    },
    "bucketSize": 6
  }
}
```
上面的配置表示key被划分为6个分片，其中分片4配置了读写分离和双写的逻辑，分片5设置了读写分离和双写多读的逻辑

### 集成camellia-dashboard
上述不管是通过yml还是json文件配置的方式，路由信息均是在本地，此外你也可以将路由信息托管到远程的camellia-dashboard（见[camellia-dashboard](/docs/dashboard/dashboard.md)）  
camellia-dashboard是一个web服务，proxy会定期去检查camellia-dashboard里的配置是否有变更，如果有，则会更新proxy的路由  
以下是一个配置示例：
```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  password: pass123
  transpond:
    type: remote
    remote:
      bid: 1
      bgroup: default
      url: http://127.0.0.1:8080
      check-interval-millis: 5000
```
上面的配置表示proxy的路由配置会从camellia-dashboard获取，获取的是bid=1以及bgroup=default的那份配置  
此外，proxy会定时检查camellia-dashboard上的配置是否更新了，若更新了，则会更新本地配置，默认检查的间隔是5s

特别的，当你使用camellia-dashboard来托管你的proxy配置之后，proxy就有了同时服务多个业务的能力  
比如A业务访问proxy，proxy将其代理到redis1；B业务访问同一个proxy，proxy可以将其代理到redis2，此时proxy的配置如下：  
```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  password: pass123
  transpond:
    type: remote
    remote:
      url: http://127.0.0.1:8080
      check-interval-millis: 5000
      dynamic: true
```   
proxy是通过clientName来识别不同的业务，如下：
```
➜ ~ ./redis-cli -h 127.0.0.1 -p 6380 -a pass123
127.0.0.1:6379> client setname camellia_10_default
OK
127.0.0.1:6380> set k1 v1
OK
127.0.0.1:6380> get k1
"v1"
127.0.0.1:6380> mget k1 k2 k3
1) "v1"
2) (nil)
3) (nil)
```
上面示例表示，请求proxy之后，要求proxy按照camellia-dashboard中bid=10，bgroup=default的那份配置进行路由转发  
特别的，如果端侧是Java，且使用了Jedis，则可以这样调用：
```java
public class Test {
    public static void main(String[] args) {
        JedisPool jedisPool = new JedisPool(new JedisPoolConfig(), "127.0.0.1", 6380,
                2000, "pass123", 0, "camellia_10_default");
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.setex("k1", 10, "v1");
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}
```


### 不同的双（多）写模式
proxy支持设置双（多）写的模式，有三个可选项：  
#### first_resource_only
表示如果配置的第一个写地址返回了，则立即返回给客户端，这是默认的模式
#### all_resources_no_check
表示需要配置的所有写地址都返回了，才返回给给客户端，返回的是第一个地址的返回结果，你可以这样配置来生效这种模式：  
```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  password: pass123
  transpond:
    type: local
    local:
      type: complex
      json-file: resource-table.json
    redis-conf:
      multi-write-mode: all_resources_no_check
```
#### all_resources_check_error
表示需要配置的所有写地址都返回了，才返回给客户端，并且会校验是否所有地址都是返回的非error结果，如果是，则返回第一个地址的返回结果；否则返回第一个错误结果，你可以这样配置来生效这种模式：  
```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  password: pass123
  transpond:
    type: local
    local:
      type: complex
      json-file: resource-table.json
    redis-conf:
      multi-write-mode: all_resources_check_error
```  


### 自定义分片函数
你可以自定义分片函数，分片函数会计算出一个key的哈希值，和分片大小（bucketSize）取余后，得到该key所属的分片。  
默认的分片函数是com.netease.nim.camellia.core.client.env.DefaultShadingFunc  
你可以继承com.netease.nim.camellia.core.client.env.AbstractSimpleShadingFunc实现自己想要的分片函数，类似于这样：  
```java
package com.netease.nim.camellia.redis.proxy.samples;

import com.netease.nim.camellia.core.client.env.AbstractSimpleShadingFunc;

public class CustomShadingFunc extends AbstractSimpleShadingFunc {
    
    @Override
    public int shadingCode(byte[] key) {
        if (key == null) return 0;
        if (key.length == 0) return 0;
        int h = 0;
        for (byte d : key) {
            h = 31 * h + d;
        }
        return (h < 0) ? -h : h;
    }
}
```  
然后在application.yml配置即可，类似于这样：
```yaml
server:
  port: 6380
spring:
  application:
    name: camellia-redis-proxy-server

camellia-redis-proxy:
  password: pass123
  transpond:
    type: local
    local:
      type: complex
      json-file: resource-table.json
    redis-conf:
      shading-func: com.netease.nim.camellia.redis.proxy.samples.CustomShadingFunc
```