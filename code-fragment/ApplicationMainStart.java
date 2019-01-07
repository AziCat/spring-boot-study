import cn.sinobest.jzpt.framework.registrar.MultipleDataSourceRegistrar;
import cn.sinobest.jzpt.framework.util.ApplicationContextUtil;
import cn.sinobest.jzpt.kafka.KafkaConsumerConfig;
import cn.sinobest.jzpt.test.MyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

/**
 * 应用启动类
 *
 * @author yanjunhao
 * @date 2018年7月4日
 */
@SpringBootApplication
@EnableScheduling
@Import(value = {MultipleDataSourceRegistrar.class})
@ComponentScan(basePackages = {"cn.sinobest"})
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware", dateTimeProviderRef = "springSecurityAuditorAware")
@PropertySource(value = {"classpath:${spring.profiles.active}/database.properties",
        "classpath:${spring.profiles.active}/application.properties",
        "classpath:${spring.profiles.active}/redis.properties"},
        encoding = "utf-8")
public class ApplicationMainStart implements CommandLineRunner {
    @Autowired
    private KafkaListenerEndpointRegistry registry;

    private final static Logger logger = LoggerFactory.getLogger(ApplicationMainStart.class);


    /**
     * 应用启动方法
     */
    public static void main(String[] args) throws Exception {
        ApplicationContext applicationContext = SpringApplication.run(ApplicationMainStart.class, args);
        //设置上下文对象
        ApplicationContextUtil.setApplicationContext(applicationContext);
        System.out.println("start success!");
        applicationContext.publishEvent(new MyEvent(ApplicationMainStart.class,"消息正文"));
    }

    @Override
    public void run(String... args) throws Exception {
        //应用启动后，遍历已注册的kafka消费者
        registry.getListenerContainerIds().forEach(kafkaListenerId -> {
            MessageListenerContainer messageListenerContainer = registry.getListenerContainer(kafkaListenerId);
            //如果其消费的topic是NULL_TOPIC，则保持不启用状态，否则把消费者启动
            String[] topics = messageListenerContainer.getContainerProperties().getTopics();
            if (!Arrays.asList(topics).contains(KafkaConsumerConfig.TopicConfig.NULL_TOPIC)) {
                messageListenerContainer.start();
                logger.info("kafkaListener [{}] is start working", kafkaListenerId);
            } else {
                logger.info("kafkaListener [{}] contains NULL_TOPIC", kafkaListenerId);
            }
        });
    }
}
