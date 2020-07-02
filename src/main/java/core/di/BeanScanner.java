package core.di;

import core.annotation.Repository;
import core.annotation.Service;
import core.annotation.web.Controller;
import core.annotation.web.RequestMapping;
import core.di.factory.BeanFactory;
import core.mvc.tobe.HandlerExecution;
import core.mvc.tobe.HandlerKey;
import core.mvc.tobe.support.*;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class BeanScanner {
    private static final List<Class<? extends Annotation>> scanAnnotations = Arrays.asList(Controller.class, Service.class, Repository.class);
    private static final Logger logger = LoggerFactory.getLogger(core.di.BeanScanner.class);

    private static final List<ArgumentResolver> argumentResolvers = asList(
            new HttpRequestArgumentResolver(),
            new HttpResponseArgumentResolver(),
            new RequestParamArgumentResolver(),
            new PathVariableArgumentResolver(),
            new ModelArgumentResolver()
    );

    private static final ParameterNameDiscoverer nameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

    public Map<HandlerKey, HandlerExecution> scan(Object... basePackage) {
        final Reflections reflections = new Reflections(basePackage, new TypeAnnotationsScanner(), new SubTypesScanner(), new MethodAnnotationsScanner());

        final Set<Class<?>> preInstanticateBeans = scanAnnotations.stream()
                .map(reflections::getTypesAnnotatedWith)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        final BeanFactory beanFactory = new BeanFactory(preInstanticateBeans);
        beanFactory.initialize();

        final Map<HandlerKey, HandlerExecution> handlers = new HashMap<>();

        final Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(Controller.class);
        for (Class<?> controller : controllers) {
            final Object target = beanFactory.getBean(controller);
            addHandlerExecution(handlers, target, controller.getMethods());
        }
        return handlers;
    }

    private void addHandlerExecution(Map<HandlerKey, HandlerExecution> handlers, final Object target, Method[] methods) {
        Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(RequestMapping.class))
                .forEach(method -> {
                    RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                    HandlerKey handlerKey = new HandlerKey(requestMapping.value(), requestMapping.method());
                    HandlerExecution handlerExecution = new HandlerExecution(nameDiscoverer, argumentResolvers, target, method);
                    handlers.put(handlerKey, handlerExecution);
                    logger.info("Add - method: {}, path: {}, HandlerExecution: {}", requestMapping.method(), requestMapping.value(), method.getName());
                });
    }

}
