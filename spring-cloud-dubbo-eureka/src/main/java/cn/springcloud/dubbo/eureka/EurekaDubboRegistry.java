package cn.springcloud.dubbo.eureka;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEvent;
import com.netflix.discovery.EurekaEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EurekaDubboRegistry extends FailbackRegistry {
    private static final Logger logger = LoggerFactory.getLogger(EurekaDubboRegistry.class);

    private EurekaClient eurekaClient = null;
    // FIXME dubbo早于eurekaClient初始化 暂时采用异步队列处理
    int waitCount = 0;
    private ConcurrentLinkedQueue<Event> eventQueue = new ConcurrentLinkedQueue<>();
    private Thread eventQueueThread = new Thread("EurekaDubboRegistry.eventQueueThread") {
        @Override
        public void run() {
            while (waitCount < 10 && eventQueue.isEmpty()) {
                if (eurekaClient == null) {
                    try {
                        eurekaClient = EurekaDubboRegistryAutoConfiguration.getApplicationContext().getBean(EurekaClient.class);
                        eurekaClient.getApplications("");
                    } catch (Exception e) {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e1) {
                        }
                    }
                    continue;
                }

                Event event = eventQueue.poll();
                if (event == null) {
                    waitCount += 1;
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                    }
                } else {
                    waitCount = 0;
                    doEvent(event);
                }
            }
            logger.info("==========eventQueueThread done==========");
        }
    };

    public EurekaDubboRegistry(URL url) {
        super(url);
        eventQueueThread.start();
    }

    @Override
    protected void doRegister(URL url) {
        doEvent(new Event(Event.REGISTER, url, null));
    }

    @Override
    protected void doUnregister(URL url) {
        doEvent(new Event(Event.UNREGISTER, url, null));
//        InstanceInfo instanceInfo = eurekaClient.getApplicationInfoManager().getInfo();
//        eurekaClient.setStatus(InstanceInfo.InstanceStatus.DOWN, instanceInfo);
    }

    @Override
    protected void doSubscribe(URL url, NotifyListener listener) {
        doEvent(new Event(Event.SUBSCRIBE, url, listener));
    }

    @Override
    protected void doUnsubscribe(URL url, NotifyListener listener) {
        doEvent(new Event(Event.UNSUBSCRIBE, url, listener));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private void doEvent(Event event) {
        if (eurekaClient == null) {
            eventQueue.add(event);
        } else {
            if (event.type == Event.REGISTER) {
                InstanceInfo instanceInfo = eurekaClient.getApplicationInfoManager().getInfo();
                Map<String, String> metadata = instanceInfo.getMetadata();
                metadata.put("dubbo", event.url.toString());
                eurekaClient.getApplicationInfoManager().registerAppMetadata(metadata);
            } else if (event.type == Event.UNREGISTER) {
                InstanceInfo instanceInfo = eurekaClient.getApplicationInfoManager().getInfo();
                Map<String, String> metadata = instanceInfo.getMetadata();
                metadata.remove("dubbo");
                eurekaClient.getApplicationInfoManager().registerAppMetadata(metadata);
            } else if (event.type == Event.SUBSCRIBE) {

            } else if (event.type == Event.UNSUBSCRIBE) {

            }
        }
    }

    class Event {
        public static final int REGISTER = 0;
        public static final int UNREGISTER = 1;
        public static final int SUBSCRIBE = 2;
        public static final int UNSUBSCRIBE = 3;

        int type;
        URL url;
        NotifyListener listener;

        public Event(int type, URL url, NotifyListener listener) {
            this.type = type;
            this.url = url;
            this.listener = listener;
        }
    }
}
