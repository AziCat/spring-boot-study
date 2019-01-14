# SpringBoot学习笔记
记录一下SpringBoot源码阅读笔记，通过debug查看应用启动时spring的工作流程，记录一些常用的扩展功能与自带功能。
如Aware、事件发布机制，BeanDefinitionRegistryPostProcessor，ImportBeanDefinitionRegistrar，
BeanFactory，FactoryBean，BeanFactoryPostProcessor，BeanPostProcessor等。

## 版本
```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.0.3.RELEASE</version>
        <relativePath/>
    </parent>
```

## 源码阅读
SpringBoot应用启动入口为`SpringApplication.run(this.class,args)`:
```java
public static ConfigurableApplicationContext run(Class<?> primarySource,
			String... args) {
		return run(new Class<?>[] { primarySource }, args);
}
```
该方法先会调用内部最终的构造方法：
```java
public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
    ...
    //初始化ApplicationContextInitializer
    setInitializers((Collection) getSpringFactoriesInstances(
                ApplicationContextInitializer.class));
    //初始化ApplicationListener
    setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
    ...
}
```
在构造方法内，会初始化一些成员变量的值，和一些内置的`ApplicationContextInitializer`与
`ApplicationListener`，这些变量的优先级是最高的，可以对后续操作造成影响，如果需要应用加载
自己的`ApplicationContextInitializer`与`ApplicationListener`，可以在**资源根目录**创建
**META-INF\spring.factories**文件，spring内置了很多默认的成员变量，以下是默认的spring.factories
的部分内容：
```
...
# Application Context Initializers
org.springframework.context.ApplicationContextInitializer=\
org.springframework.boot.context.ConfigurationWarningsApplicationContextInitializer,\
org.springframework.boot.context.ContextIdApplicationContextInitializer,\
org.springframework.boot.context.config.DelegatingApplicationContextInitializer,\
org.springframework.boot.web.context.ServerPortInfoApplicationContextInitializer

# Application Listeners
org.springframework.context.ApplicationListener=\
org.springframework.boot.ClearCachesApplicationListener,\
org.springframework.boot.builder.ParentContextCloserApplicationListener,\
org.springframework.boot.context.FileEncodingApplicationListener,\
org.springframework.boot.context.config.AnsiOutputApplicationListener,\
org.springframework.boot.context.config.ConfigFileApplicationListener,\
org.springframework.boot.context.config.DelegatingApplicationListener,\
org.springframework.boot.context.logging.ClasspathLoggingApplicationListener,\
org.springframework.boot.context.logging.LoggingApplicationListener,\
org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener
...
```
在调用完构造函数后，调用该实例的`run()`方法：
```java
    public ConfigurableApplicationContext run(String... args) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        ConfigurableApplicationContext context = null;
        Collection<SpringBootExceptionReporter> exceptionReporters = new ArrayList<>();
        configureHeadlessProperty();
        //实例化了SpringApplicationRunListeners对象，其持有EventPublishingRunListener实例
        SpringApplicationRunListeners listeners = getRunListeners(args);
        //发布应用启动的事件ApplicationStartingEvent，并由SimpleApplicationEventMulticaster进行多线路处理
        listeners.starting();
        try {
            //应用参数加载
            ApplicationArguments applicationArguments = new DefaultApplicationArguments(
                    args);
            //准备环境参数
            ConfigurableEnvironment environment = prepareEnvironment(listeners,
                    applicationArguments);
            configureIgnoreBeanInfo(environment);
            //居然是在这里打印应用的logo
            Banner printedBanner = printBanner(environment);
            //创建容器上下文对象，web应用为AnnotationConfigServletWebServerApplicationContext
            //经过多层继承，在父类的构造方法初始化了许多成员变量
            /*
            先执行顶层父类DefaultResourceLoader构造方法，初始化this.classLoader = ClassUtils.getDefaultClassLoader()
            执行父类AbstractApplicationContext构造方法，初始化this.resourcePatternResolver = getResourcePatternResolver()
            执行父类GenericApplicationContext构造方法，初始化this.beanFactory = new DefaultListableBeanFactory()
                beanFactory初始化时，经过多层继承初始化部分成员变量
            执行父类GenericWebApplicationContext构造方法
            执行父类ServletWebServerApplicationContext构造方法
            执行本身构造方法，初始化this.reader与this.scanner
                reader包含了各类注解解析器
            */
            context = createApplicationContext();
            exceptionReporters = getSpringFactoriesInstances(
                    SpringBootExceptionReporter.class,
                    new Class[]{ConfigurableApplicationContext.class}, context);
            //预处理上下文
            prepareContext(context, environment, listeners, applicationArguments,
                    printedBanner);
            //关键方法在此，内容非常多
            refreshContext(context);
            //这里居然没做什么
            afterRefresh(context, applicationArguments);
            stopWatch.stop();
            if (this.logStartupInfo) {
                new StartupInfoLogger(this.mainApplicationClass)
                        .logStarted(getApplicationLog(), stopWatch);
            }
            //发布ApplicationStartedEvent事件
            listeners.started(context);
            //调用ApplicationRunner与CommandLineRunner的实现类
            callRunners(context, applicationArguments);
        } catch (Throwable ex) {
            handleRunFailure(context, ex, exceptionReporters, listeners);
            throw new IllegalStateException(ex);
        }

        try {
            //发布ApplicationReadyEvent事件
            listeners.running(context);
        } catch (Throwable ex) {
            handleRunFailure(context, ex, exceptionReporters, null);
            throw new IllegalStateException(ex);
        }
        //返回spring上下文对象
        return context;
    }
```
---
## 事件发布
在上述`run()`方法中，使用了spring内置的事件发布机制：
```java
        //实例化了SpringApplicationRunListeners对象，其持有EventPublishingRunListener实例
        SpringApplicationRunListeners listeners = getRunListeners(args);
        //发布应用启动的事件ApplicationStartingEvent，并由SimpleApplicationEventMulticaster进行多线路处理
        listeners.starting();
```
在`getRunListeners(args)`方法获取spring内置的所有`SpringApplicationRunListener`，默认返回**EventPublishingRunListener**实例，
一般`spring.factories`中配置的**EventPublishingRunListener**已能满足我们的需求，如果需要扩充请自行添加:
```
# Run Listeners
org.springframework.boot.SpringApplicationRunListener=\
org.springframework.boot.context.event.EventPublishingRunListener
```
### 事件发布与事件的处理有以下方式：
* 定义一个事件类型MyEvent如上述的ApplicationStartingEvent：
```java
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
```
* 事件的发布需要通过接口**ApplicationEventPublisher**的实现类，spring的上下文对象**ApplicationContext**可以满足此要求：
```java
applicationContext.publishEvent(new MyEvent(this.class,"消息正文"));
```
* 现在我们要处理**MyEvent**事件，可以通过实现接口**ApplicationListener**或者使用注解`@EventListener`来实现：
```java
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
```
```java
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
```
---
## ConfigurableEnvironment加载
在发布完`ApplicationStartingEvent`事件后，应用开始加载配置项：
```java
//准备环境参数
ConfigurableEnvironment environment = prepareEnvironment(listeners,applicationArguments);
```
在**prepareEnvironment**方法中，初始化`ConfigurableEnvironment`实例，并加载默认配置。
```java
//web应用返回StandardServletEnvironment，构造函数调用时，已加载系统参数
ConfigurableEnvironment environment = getOrCreateEnvironment();
configureEnvironment(environment, applicationArguments.getSourceArgs());
```
主要的代码实现在**configureEnvironment**中：
```java
    protected void configureEnvironment(ConfigurableEnvironment environment,
                                        String[] args) {
        configurePropertySources(environment, args);
        configureProfiles(environment, args);
    }
```
**configurePropertySources**方法初步加载`defaultProperties`，然后判断是否有命令行参数传入，如果
有，把命令行参数添加到环境变量中，且优先级最高:
```java

    protected void configurePropertySources(ConfigurableEnvironment environment,
                                            String[] args) {
        MutablePropertySources sources = environment.getPropertySources();
        if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
            //如果有默认配置，添加到最后
            sources.addLast(
                    new MapPropertySource("defaultProperties", this.defaultProperties));
        }
        if (this.addCommandLineProperties && args.length > 0) {
            String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
            if (sources.contains(name)) {
                PropertySource<?> source = sources.get(name);
                CompositePropertySource composite = new CompositePropertySource(name);
                composite.addPropertySource(new SimpleCommandLinePropertySource(
                        "springApplicationCommandLineArgs", args));
                composite.addPropertySource(source);
                sources.replace(name, composite);
            } else {
                //-Dxxxxx=yyyy这种参数的优先级最高
                sources.addFirst(new SimpleCommandLinePropertySource(args));
            }
        }
    }
```
在**configureProfiles**中获取`spring.profiles.active`并设置到环境变量中。完成这些操作后，发布
`ApplicationEnvironmentPreparedEvent`事件，一些配置中心的实现方式，都可以基于此事件的接收来做自己
的配置嵌入。其中内置的监听者中，比较重要的是**ConfigFileApplicationListener**，它监听到`ApplicationEnvironmentPreparedEvent`事件，
会获取所有**EnvironmentPostProcessor**接口实例，调用`postProcessEnvironment()`方法。
```java
//准备好环境变量，发布ApplicationEnvironmentPreparedEvent事件
/*
 ConfigFileApplicationListener
    ConfigFileApplicationListener实现了接口SmartApplicationListener,EnvironmentPostProcessor，并在实现方法中获取所有实现EnvironmentPostProcessor接口
    的实例(包括自己)，并进行调用postProcessEnvironment()
    必须要注册在META-INF/spring.factories中
 */
listeners.environmentPrepared(environment);
```