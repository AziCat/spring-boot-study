package cn.sinobest.jzpt.test;

import org.springframework.context.ApplicationEvent;

/**
 * 测试事件发布监听
 * @author yanjunhao
 * @date 2018年12月20日
 */
public class MyEvent extends ApplicationEvent {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public MyEvent(Object source, String message) {
        super(source);
        this.message = message;
    }

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public MyEvent(Object source) {
        super(source);
    }
}
