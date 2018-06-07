package cn.springcloud.dubbo.demo.consumer.service;

import cn.springcloud.dubbo.demo.provider.service.FooService;
import cn.springcloud.dubbo.demo.provider.service.ProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestService {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private FooService fooService;

    @GetMapping("/test")
    public String test() {
        return "Test " + providerService.hello();
    }

    @GetMapping("/testFoo")
    public String testFoo() {
        return "Foo " + fooService.foo();
    }
}
