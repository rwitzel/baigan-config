package org.zalando.baigan.proxy.handler;

import com.google.common.primitives.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.zalando.baigan.context.ContextProviderRetriever;
import org.zalando.baigan.model.Configuration;
import org.zalando.baigan.provider.ContextProvider;
import org.zalando.baigan.proxy.ProxyUtils;
import org.zalando.baigan.service.ConditionsProcessor;
import org.zalando.baigan.service.ConfigurationRepository;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This class provides a concrete implementation for the Method invocation
 * handler.
 *
 * @author mchand
 *
 */
@Service
public class ContextAwareConfigurationMethodInvocationHandler
        extends ConfigurationMethodInvocationHandler {

    private final Logger LOG = LoggerFactory
            .getLogger(ConfigurationMethodInvocationHandler.class);

    private ConfigurationRepository configurationRepository;

    private ConditionsProcessor conditionsProcessor;

    private ContextProviderRetriever contextProviderRetriever;

    @Autowired
    public ContextAwareConfigurationMethodInvocationHandler(
            final ConfigurationRepository configurationRepository,
            final ConditionsProcessor conditionsProcessor,
            final ContextProviderRetriever contextProviderRetriever) {
        this.configurationRepository = configurationRepository;
        this.conditionsProcessor = conditionsProcessor;
        this.contextProviderRetriever = contextProviderRetriever;
    }

    @Override
    protected Object handleInvocation(Object proxy, Method method,
            Object[] args) throws Throwable {
        final String methodName = method.getName();

        final String nameSpace = method.getDeclaringClass().getSimpleName();

        final String key = ProxyUtils.dottify(nameSpace) + "."
                + ProxyUtils.dottify(methodName);
        final Object result = getConfig(key);
        if (result == null) {
            LOG.warn("Configuration not found for key: {}", key);
            return null;
        }

        final Class<?> declaredReturnType = method.getReturnType();

        try {

            Constructor<?> constructor;
            if (declaredReturnType.isInstance(result)) {
                return result;
            } else if (declaredReturnType.isPrimitive()) {
                final Class<?> resultClass = result.getClass();
                constructor = Primitives.wrap(declaredReturnType)
                        .getDeclaredConstructor(resultClass);
            } else if (declaredReturnType.isEnum()) {
                for (Object t : Arrays
                        .asList(declaredReturnType.getEnumConstants())) {
                    if (result.toString().equalsIgnoreCase(t.toString())) {
                        return t;
                    }
                }
                return result;
            } else {
                constructor = declaredReturnType
                        .getDeclaredConstructor(result.getClass());
            }
            return constructor.newInstance(result);
        } catch (Exception exception) {
            LOG.warn(
                    "Wrong or Incompatible configuration. Cannot find a constructor to create object of type "
                            + declaredReturnType
                            + " for value of the configuration key " + key,
                    exception);
        }
        return null;
    }

    private Object getConfig(final String key) {

        final Optional<Configuration> optional = configurationRepository.get(key);
        if (!optional.isPresent()) {
            return null;
        }

        final Map<String, String> context = new HashMap<>();

        for (final String param : contextProviderRetriever.getContextParameterKeys()) {
            Collection<ContextProvider> providers = contextProviderRetriever.getProvidersFor(param);
            if (CollectionUtils.isEmpty(providers)) {
                continue;
            }
            final ContextProvider provider = providers.iterator().next();
            context.put(param, provider.getContextParam(param));
        }

        return conditionsProcessor.process(optional.get(), context);

    }

}
