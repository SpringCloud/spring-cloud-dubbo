package cn.springcloud.dubbo.eureka;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class EurekaDubboRegistry extends FailbackRegistry {
    private static final Logger logger = LoggerFactory.getLogger(EurekaDubboRegistry.class);

    private ConcurrentHashMap<URL, NotifyListener> subscribeMap = new ConcurrentHashMap<>();

    private EurekaClient eurekaClient = null;
    private ObjectMapper objectMapper = new ObjectMapper();
    private TypeReference<TreeSet<String>> stringListTypeReference = new TypeReference<TreeSet<String>>() {
    };

    private EurekaEventListener eurekaEventListener = (event) -> {
        if (event instanceof CacheRefreshedEvent) {
            HashSet<URL> registeredProviders = new HashSet<>();

            eurekaClient.getApplications().getRegisteredApplications().stream().forEach(application -> application.getInstances().stream().forEach(instanceInfo -> {
                String metadataCategory = instanceInfo.getMetadata().get(Constants.PROVIDERS_CATEGORY);
                if (!StringUtils.isEmpty(metadataCategory)) {
                    try {
                        TreeSet<String> values = objectMapper.readValue(metadataCategory, stringListTypeReference);
                        values.stream().forEach(s -> registeredProviders.add(URL.valueOf(s)));
                    } catch (IOException e) {
                        // json格式不对直接跳过
                    }
                }
            }));

            for (Map.Entry<URL, NotifyListener> entry : subscribeMap.entrySet()) {
                URL key = entry.getKey();
                if (key.getParameter(Constants.CATEGORY_KEY).contains(Constants.PROVIDERS_CATEGORY)) {
                    List<URL> urlList = registeredProviders.stream()
                            .filter(url -> url.getServiceKey().equals(key.getServiceKey())).collect(Collectors.toList());
                    entry.getValue().notify(urlList);
                }
            }
        }
    };

    // FIXME dubbo早于eurekaClient初始化 暂时采用异步队列处理
    int waitCount = 0;
    private ConcurrentLinkedQueue<URL> registerQueue = new ConcurrentLinkedQueue<>();
    private Thread registerThread = new Thread("EurekaDubboRegistry.registerThread") {
        @Override
        public void run() {
            while (waitCount < 10 || !registerQueue.isEmpty()) {
                if (eurekaClient == null) {
                    try {
                        eurekaClient = EurekaDubboRegistryAutoConfiguration.getApplicationContext().getBean(EurekaClient.class);
                        eurekaClient.registerEventListener(eurekaEventListener);
                    } catch (Exception e) {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e1) {
                        }
                    }
                    continue;
                }

                URL url = registerQueue.poll();
                if (url == null) {
                    waitCount += 1;
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                    }
                } else {
                    waitCount = 0;
                    doRegister(url);
                }
            }
            logger.info("==========registerThread done==========");
        }
    };

    public EurekaDubboRegistry(URL url) {
        super(url);
        registerThread.start();
    }

    @Override
    protected void doRegister(URL url) {
        if (eurekaClient == null) {
            registerQueue.add(url);
        } else {
            String category = url.getParameter(Constants.CATEGORY_KEY);
            if (StringUtils.isEmpty(category)) {
                category = Constants.DEFAULT_CATEGORY;
            }
            addOrDeleteMetadata(url, category, true);
        }
    }

    @Override
    protected void doUnregister(URL url) {
        if (eurekaClient == null) {
            registerQueue.remove(url);
        } else {
            String category = url.getParameter(Constants.CATEGORY_KEY);
            addOrDeleteMetadata(url, category, false);
        }
    }

    @Override
    protected void doSubscribe(URL url, NotifyListener listener) {
        subscribeMap.put(url, listener);
    }

    @Override
    protected void doUnsubscribe(URL url, NotifyListener listener) {
        subscribeMap.remove(url);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private void addOrDeleteMetadata(URL url, String category, boolean isAdd) {
        InstanceInfo instanceInfo = eurekaClient.getApplicationInfoManager().getInfo();
        Map<String, String> metadata = instanceInfo.getMetadata();
        String metadataCategory = metadata.get(category);
        try {
            TreeSet<String> values = StringUtils.isEmpty(metadataCategory) ?
                    new TreeSet<>() : objectMapper.readValue(metadataCategory, stringListTypeReference);
            if (isAdd) {
                values.add(url.toString());
            } else {
                values.remove(url.toString());
            }
            metadata.put(category, objectMapper.writeValueAsString(values));
            eurekaClient.getApplicationInfoManager().registerAppMetadata(metadata);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
