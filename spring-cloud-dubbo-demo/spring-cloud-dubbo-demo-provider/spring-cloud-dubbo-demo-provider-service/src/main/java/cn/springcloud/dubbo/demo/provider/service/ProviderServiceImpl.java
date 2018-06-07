package cn.springcloud.dubbo.demo.provider.service;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProviderServiceImpl implements ProviderService {
    public ProviderServiceImpl() {
        System.out.println("ProviderServiceImpl");
    }

    @Override
    public String hello() {
        return "Hello " + System.currentTimeMillis();
    }
}
