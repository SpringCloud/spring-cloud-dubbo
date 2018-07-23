# spring-cloud-dubbo
spring/spring cloud的设计理念是integrate everything。充分利用现有开源组件，在他们之上设计一套统一规范/接口使他们能够接入spring cloud体系并且能够无缝切换底层实现，使他们能够集成到一起良好运作。最典型的例子就是DiscoveryClient，只要实现DiscoveryClient相关接口，spring cloud的底层注册中心可以随意更换，dubbo的注册中心也有SPI规范进行替换。

本项目的目标是将dubbo融入到spring cloud生态中，使微服务之间的调用同时具备restful和dubbo调用的能力。做到对业务代码无侵入，无感知：引入jar包则微服务间调用使用dubbo，去掉jar包则使用默认的restful。

目前已发布1.0.0版本到Maven中央仓库
```xml
<dependency>
    <groupId>cn.springcloud.dubbo</groupId>
    <artifactId>spring-cloud-dubbo-starter</artifactId>
    <version>1.0.0</version>
</dependency>

```
> 如果你觉得spring-cloud-dubbo不错，让你很爽，烦请拨冗**“Star”**。

## 设计思路
之前因为工作需要增强过feign，feign的设计思路就是提供一套API，底层契约随意更换，参照feign的SpringMvcContract类。与项目理念非常相似，所以我们也使用feign作为统一接口，spring cloud下feign默认使用restful方式调用，我们只需要扩展feign，提供dubbo方式调用就行了。

### 服务提供方
服务提供方提供service(restful服务)和api(sdk开发工具)。服务消费方使用api来调用服务(方便提供方升级增加服务提供方可控性)。

api中定义FeignClient接口(spring-cloud-dubbo-demo-provider-api)：
```
@FeignClient("provider")
public interface ProviderService {
    @GetMapping("/hello")
    String hello();
}
```
service中实现该接口(spring-cloud-dubbo-demo-provider-service)：
```
@RestController
public class ProviderServiceImpl implements ProviderService {
    @Override
    public String hello() {
        return "Hello " + System.currentTimeMillis();
    }
}
```
上述代码是一个典型的spring cloud restful api。在服务提供方我们要做的就是：引入dubbo，扫描含有@FeignClient注解的类并且提供dubbo访问即可(FeignClientToDubboProviderBeanPostProcessor)。关键代码片段：
```
private void registerServiceBeans(Set<String> packagesToScan, BeanDefinitionRegistry registry) {
        ...
        scanner.addIncludeFilter(new AnnotationTypeFilter(FeignClient.class, true, true));
        for (String packageToScan : packagesToScan) {
            // Registers @Service Bean first
            scanner.scan(packageToScan);
         ...
}

```

### 服务消费方
引入spring-cloud-dubbo-demo-provider-api依赖，并直接使用@Autowire使用相关api：
```
@RestController
public class TestService {
    @Autowired
    private ProviderService providerService;

    @GetMapping("/test")
    public String test() {
        return "Test " + providerService.hello();
    }
}
```
上述代码是一个典型的spring cloud feign使用。我们只需要替换feign的实现：产生ProviderService接口proxy bean时，使用dubbo产生的bean替换默认的feign产生的restful调用的bean即可(DubboFeignBuilder)。关键代码片段：
```
@Override
public <T> T target(Target<T> target) {
    ReferenceBeanBuilder beanBuilder = ReferenceBeanBuilder
            .create(defaultReference, target.getClass().getClassLoader(), applicationContext)
            .interfaceClass(target.type());
    try {
        T object = (T) beanBuilder.build().getObject();
        return object;
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
```
## 如何使用
参考spring-cloud-dubbo-demo

先建立一组标准的spring cloud restful工程，注意feign client接口由服务提供方提供。然后接入spring-cloud-dubbo给项目提供dubbo调用能力。
引入依赖：
```
<dependency>
    <groupId>cn.springcloud.dubbo</groupId>
    <artifactId>spring-cloud-dubbo-starter</artifactId>
</dependency>
```
使用 https://github.com/apache/incubator-dubbo-spring-boot-project 0.2.0版本，spring-boot与dubbo集成和配置均安装此项目说明文档

服务提供方配置：
```
dubbo.application.name=provider
dubbo.registry.address=eureka://127.0.0.1:8761
dubbo.scan.basePackages=cn.springcloud.dubbo.demo.provider.service
```
为了减少依赖，快速体验，并且作为将dubbo完全融入spring cloud后续计划的POC。我们按照dubbo SPI扩展规范 http://dubbo.apache.org/books/dubbo-dev-book/impls/registry.html ，提供了一个实验性质的dubbo eureka注册中心（dubbo eureka配置中心的ip和端口可以随便填，我们并不会用到这里的配置，我们用的是spring cloud的配置，dubbo扩展规范要求 eureka:// 后面必须要跟ip和端口而已）。如果要在生产环境使用，目前还是建议采用dubbo自带的zookeeper注册中心，只需将上面注册中心配置改为：
```
dubbo.registry.address=zookeeper://127.0.0.1:2181
```

服务消费方配置：
```
dubbo.application.name=consumer
dubbo.registry.address=eureka://127.0.0.1:8761
dubbo.scan.basePackages=cn.springcloud.dubbo.demo.consumer.service
```

开启eureka

开启provider

开启consumer

访问 view-source:http://localhost:8761/eureka/apps/CONSUMER metadata确认含有如下dubbo注册信息：
```
<metadata>
    <providers>["dubbo://172.24.223.241:30880/cn.springcloud.dubbo.demo.consumer.service.BarService?anyhost=true&amp;application=consumer&amp;dubbo=2.6.2&amp;generic=false&amp;interface=cn.springcloud.dubbo.demo.consumer.service.BarService&amp;methods=bar&amp;pid=9268&amp;side=provider&amp;timestamp=1528524172162"]</providers>
    <consumers>["consumer://172.24.223.241/cn.springcloud.dubbo.demo.provider.service.FooService?application=consumer&amp;category=consumers&amp;check=false&amp;dubbo=2.6.2&amp;interface=cn.springcloud.dubbo.demo.provider.service.FooService&amp;methods=foo&amp;pid=9268&amp;qos.enable=false&amp;side=consumer&amp;timestamp=1528524172906","consumer://172.24.223.241/cn.springcloud.dubbo.demo.provider.service.ProviderService?application=consumer&amp;category=consumers&amp;check=false&amp;dubbo=2.6.2&amp;interface=cn.springcloud.dubbo.demo.provider.service.ProviderService&amp;methods=hello&amp;pid=9268&amp;qos.enable=false&amp;side=consumer&amp;timestamp=1528524172823"]</consumers>
</metadata>
```
在服务消费方TestService打上断点，访问 http://localhost:28080/test 可以看到ProviderService的实现类proxy为dubbo，采用dubbo进行服务间调用

服务消费方使用restful调用，只需将dubbo相关依赖排除即可：
```
<dependency>
    <groupId>cn.springcloud.dubbo</groupId>
    <artifactId>spring-cloud-dubbo-starter</artifactId>
    <exclusions>
        <exclusion>
            <groupId>com.alibaba.boot</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```
在服务消费方TestService打上断点，访问 http://localhost:28080/test 可以看到ProviderService的实现类proxy为feign，采用restful进行服务间调用

### FAQ
#### 如何使用更加细致的dubbo配置？
我们的工程融合了spring cloud和dubbo，只是将feign底层实现替换为dubbo而已，因此所有dubbo标准用法均支持。服务提供方可以使用标准dubbo @Service注解进行细致配置：
```
@Service(group = "testGroup")
@RestController
public class ProviderServiceImpl implements ProviderService {
}
```

服务消费方可以使用标准dubbo @Reference注解进行细致配置：
```
// @Autowired
@Reference(group = "testGroup")
private ProviderService providerService;
```


