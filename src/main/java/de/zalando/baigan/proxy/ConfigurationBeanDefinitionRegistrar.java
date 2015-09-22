package de.zalando.baigan.proxy;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.reflections.Reflections;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

import com.google.common.collect.Lists;

import de.zalando.baigan.annotation.BaiganConfig;
import de.zalando.baigan.annotation.ConfigurationServiceScan;

/**
 * ImportBeanDefinitionRegistrar implementation that finds the
 * {@link ConfigurationServiceScan} annotations, delegates the scanning of
 * packages and proxy bean creation further down to the corresponding
 * implementations.
 *
 * @see {@link ConfigurationServiceBeanFactory}
 *
 * @author mchand
 *
 */
public class ConfigurationBeanDefinitionRegistrar
        implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {
        final AnnotationAttributes annotationAttributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(
                        ConfigurationServiceScan.class.getName()));

        if (annotationAttributes == null || annotationAttributes.isEmpty()) {
            throw new IllegalArgumentException(
                    "ConfigurationServiceScan requires at least 1 scan package.");
        }

        final List<String> basePackages = Lists.newArrayList();
        basePackages.addAll(
                Arrays.asList(annotationAttributes.getStringArray("value")));
        basePackages.addAll(Arrays
                .asList(annotationAttributes.getStringArray("basePackages")));

        final Set<String> saneSet = basePackages.stream()
                .filter(str -> !StringUtils.isEmpty(str))
                .collect(Collectors.toSet());

        createAndRegisterBeanDefinitions(saneSet, registry);

    }

    private void createAndRegisterBeanDefinitions(final Set<String> packages,
            final BeanDefinitionRegistry registry) {
        for (final String singlePackage : packages) {

            // final ConfigurationServiceBeanFactory factory = new
            // ConfigurationServiceBeanFactory();
            final Set<Class<?>> configServiceInterfaces = new Reflections(
                    singlePackage).getTypesAnnotatedWith(BaiganConfig.class);

            for (final Class<?> interfaceToImplement : configServiceInterfaces) {
                final BeanDefinitionBuilder builder = BeanDefinitionBuilder
                        .genericBeanDefinition(
                                ConfigurationServiceBeanFactory.class);
                builder.addPropertyValue("candidateInterface",
                        interfaceToImplement);
                // builder.addPropertyValue("targetClass",
                // interfaceToImplement.getClass());
                // builder.addPropertyValue("configurationService", null);

                final String factoryBeanName = interfaceToImplement.getName()
                        + "AppConfigServiceFactoryBean";
                registry.registerBeanDefinition(factoryBeanName,
                        builder.getBeanDefinition());
            }

        }
    }

}
