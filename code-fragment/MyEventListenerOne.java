package cn.sinobest.jzpt.test;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;

/**
 * 测试事件监听-监听MyEvent事件
 *
 * @author yanjunhao
 * @date 2018年12月20日
 */
@Component
public class MyEventListenerOne implements ApplicationListener<MyEvent> {
    @Override
    public void onApplicationEvent(@NotNull MyEvent event) {
        System.out.println("MyEventListenerOne我听到啦" + event.getMessage());
    }
}
