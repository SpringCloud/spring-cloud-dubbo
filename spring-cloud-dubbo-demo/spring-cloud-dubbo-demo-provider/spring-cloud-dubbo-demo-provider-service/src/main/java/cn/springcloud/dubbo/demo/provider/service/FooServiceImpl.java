package cn.springcloud.dubbo.demo.provider.service;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class FooServiceImpl implements FooService {
    @Override
    public String foo() {
        return "Foo " + System.currentTimeMillis();
    }
}
