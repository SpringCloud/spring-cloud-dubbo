package cn.springcloud.dubbo.demo.provider.service;

import com.alibaba.dubbo.config.annotation.Service;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@Service(group="testGroup")
@FeignClient("provider")
public interface ProviderService {
    @GetMapping("/hello")
    String hello();
}
