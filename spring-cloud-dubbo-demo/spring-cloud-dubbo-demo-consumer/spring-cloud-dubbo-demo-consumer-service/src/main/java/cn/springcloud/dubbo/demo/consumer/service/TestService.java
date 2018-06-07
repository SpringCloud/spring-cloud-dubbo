package cn.springcloud.dubbo.demo.consumer.service;

import cn.springcloud.dubbo.demo.provider.service.ProviderService;
import com.alibaba.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestService {

    @Autowired
//    @Reference
    private ProviderService providerService;

    @GetMapping("/test")
    public String test() {
        return "Test " + providerService.hello();
    }
}
