package com.alibaba.dubbo.config.spring.beans.factory.annotation;

import com.alibaba.dubbo.config.annotation.Reference;
import feign.Feign;
import feign.Target;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

public class DubboFeignBuilder extends Feign.Builder {
    @Autowired
    private ApplicationContext applicationContext;

    public Reference defaultReference;
    final class DefaultReferenceClass{
        // dubbo早与eureka启动 check设为false 调用时检查
        @Reference(check = false) String field;
    }

    public DubboFeignBuilder() {
        // 产生@Reference 默认配置实例
        this.defaultReference = ReflectionUtils.findField(DubboFeignBuilder.DefaultReferenceClass.class,"field").getAnnotation(Reference.class);
    }


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
}
