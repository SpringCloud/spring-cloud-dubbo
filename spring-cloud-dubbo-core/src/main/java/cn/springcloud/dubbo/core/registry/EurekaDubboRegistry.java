package cn.springcloud.dubbo.core.registry;

import cn.springcloud.dubbo.core.SpringCloudDubboAutoConfiguration;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.netflix.discovery.EurekaClient;

import java.util.concurrent.ConcurrentLinkedQueue;

public class EurekaDubboRegistry extends FailbackRegistry {
    private EurekaClient eurekaClient = null;

    // FIXME dubbo早于eurekaClient初始化 暂时采用异步队列处理
    int waitCount = 0;
    private ConcurrentLinkedQueue<Event> eventQueue = new ConcurrentLinkedQueue<>();
    private Thread eventQueueThread = new Thread() {
        @Override
        public void run() {
            while (waitCount < 10) {
                if (eurekaClient == null) {
                    try {
                        eurekaClient = SpringCloudDubboAutoConfiguration.applicationContext.getBean(EurekaClient.class);
                    } catch (Exception e) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                    continue;
                }

                Event event = eventQueue.poll();
                if (event == null) {
                    waitCount += 1;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }else{
                    waitCount = 0;
                    System.out.println("处理队列 "+event);
                }
            }
            System.out.println("eventQueueThread完成=============================================");
        }
    };

    public EurekaDubboRegistry(URL url) {
        super(url);
        eventQueueThread.start();
    }

    @Override
    protected void doRegister(URL url) {
        doEvent(new Event(Event.REGISTER, url, null));
//        InstanceInfo instanceInfo = eurekaClient.getApplicationInfoManager().getInfo();
//        Map<String, String> metadata = instanceInfo.getMetadata();
//        metadata.put("dubbo", url.toString());
//
//        eurekaClient.getApplicationInfoManager().registerAppMetadata(metadata);
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
        return false;
    }

    private void doEvent(Event event) {
        if (eurekaClient == null) {
            eventQueue.add(event);
        }else{
            System.out.println("自己处理 "+event);
        }
    }

    public class Event {
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
