package cn.sinobest.jzpt.test;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 测试事件监听-监听MyEvent事件
 *
 * @author yanjunhao
 * @date 2018年12月20日
 */
@Component
public class MyEventListenerTwo {
    @EventListener(classes = {MyEvent.class})
    public void listener(Object eventObj) {
        MyEvent event = (MyEvent) eventObj;
        System.out.println("MyEventListenerTwo我听到啦" + event.getMessage());
    }
}
