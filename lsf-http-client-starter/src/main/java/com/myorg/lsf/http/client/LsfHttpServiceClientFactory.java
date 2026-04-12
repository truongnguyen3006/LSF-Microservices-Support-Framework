package com.myorg.lsf.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.http.LsfErrorResponse;
import com.myorg.lsf.discovery.LsfServiceLocator;
import com.myorg.lsf.resilience.LsfResilienceExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

public class LsfHttpServiceClientFactory {

    private final RestClient.Builder restClientBuilder;
    private final LsfServiceLocator serviceLocator;
    private final ObjectProvider<LsfResilienceExecutor> resilienceExecutorProvider;
    private final LsfHttpClientProperties properties;
    private final LsfServiceAuthenticationResolver authenticationResolver;
    private final ObjectMapper objectMapper;

    public LsfHttpServiceClientFactory(
            RestClient.Builder restClientBuilder,
            LsfServiceLocator serviceLocator,
            ObjectProvider<LsfResilienceExecutor> resilienceExecutorProvider,
            LsfHttpClientProperties properties,
            LsfServiceAuthenticationResolver authenticationResolver,
            ObjectMapper objectMapper
    ) {
        this.restClientBuilder = restClientBuilder;
        this.serviceLocator = serviceLocator;
        this.resilienceExecutorProvider = resilienceExecutorProvider;
        this.properties = properties;
        this.authenticationResolver = authenticationResolver;
        this.objectMapper = objectMapper;
    }

    public <T> T createClient(Class<T> clientType) {
        LsfHttpClient annotation = clientType.getAnnotation(LsfHttpClient.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Client type must declare @LsfHttpClient: " + clientType.getName());
        }

        RestClient restClient = buildRestClient(annotation);
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
        T target = proxyFactory.createClient(clientType);
        return wrapWithResilience(clientType, target, annotation);
    }

    private RestClient buildRestClient(LsfHttpClient annotation) {
        RestClient.Builder builder = restClientBuilder.clone();
        builder.uriBuilderFactory(new LsfServiceUriBuilderFactory(
                serviceLocator,
                annotation.serviceId(),
                annotation.pathPrefix()
        ));
        builder.requestFactory(requestFactory());
        builder.requestInterceptor(new LsfRequestContextPropagationInterceptor());
        builder.requestInterceptor(new LsfTraceHeaderPropagationInterceptor());
        builder.requestInterceptor(new LsfAuthenticationInterceptor(authenticationResolver, annotation.authMode()));
        return builder.build();
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.getConnectTimeout().toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.getReadTimeout().toMillis()));
        return requestFactory;
    }

    @SuppressWarnings("unchecked")
    private <T> T wrapWithResilience(Class<T> clientType, T target, LsfHttpClient annotation) {
        LsfResilienceExecutor executor = resilienceExecutorProvider.getIfAvailable();
        String resilienceId = StringUtils.hasText(annotation.resilienceId())
                ? annotation.resilienceId()
                : annotation.serviceId();

        InvocationHandler handler = (proxy, method, args) ->
                invoke(target, method, args, annotation.serviceId(), executor, resilienceId);
        return (T) Proxy.newProxyInstance(clientType.getClassLoader(), new Class<?>[]{clientType}, handler);
    }

    private Object invoke(
            Object target,
            Method method,
            Object[] args,
            String serviceId,
            LsfResilienceExecutor executor,
            String resilienceId
    ) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("HTTP client invocation failed for " + method, ex);
            }
        }

        if (executor == null || !StringUtils.hasText(resilienceId)) {
            return invokeTarget(method, target, args, serviceId);
        }

        try {
            return executor.executeCallable(resilienceId, () -> invokeTarget(method, target, args, serviceId));
        } catch (Exception ex) {
            throw classify(serviceId, ex);
        }
    }

    private Object invokeTarget(Method method, Object target, Object[] args, String serviceId) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw classify(serviceId, ex.getTargetException());
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("HTTP client invocation failed for " + method, ex);
        }
    }

    private RuntimeException classify(String serviceId, Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException && !(runtimeException instanceof RestClientException)) {
            return runtimeException;
        }
        if (throwable instanceof LsfRemoteServiceException remoteServiceException) {
            return remoteServiceException;
        }
        if (throwable instanceof RestClientResponseException responseException) {
            return decodeResponseException(serviceId, responseException);
        }
        if (throwable instanceof ResourceAccessException resourceAccessException) {
            return networkException(serviceId, resourceAccessException);
        }
        if (throwable instanceof RestClientException restClientException) {
            return networkException(serviceId, restClientException);
        }
        if (throwable instanceof TimeoutException timeoutException) {
            return new LsfRemoteServiceException(
                    serviceId,
                    504,
                    "TIMEOUT",
                    true,
                    "Timeout while calling serviceId=" + serviceId + ": " + timeoutException.getMessage(),
                    timeoutException,
                    null
            );
        }
        return new IllegalStateException("HTTP client invocation failed for serviceId=" + serviceId, throwable);
    }

    private LsfRemoteServiceException decodeResponseException(String serviceId, RestClientResponseException responseException) {
        LsfErrorResponse errorResponse = decodeErrorBody(responseException);
        boolean retryable = errorResponse != null
                ? errorResponse.retryable()
                : isRetryableStatus(responseException.getStatusCode().value());
        String code = errorResponse != null && StringUtils.hasText(errorResponse.code())
                ? errorResponse.code()
                : "HTTP_" + responseException.getStatusCode().value();
        String message = errorResponse != null && StringUtils.hasText(errorResponse.message())
                ? errorResponse.message()
                : responseException.getStatusText();

        return new LsfRemoteServiceException(
                serviceId,
                responseException.getStatusCode().value(),
                code,
                retryable,
                "Remote call failed for serviceId=" + serviceId
                        + ", status=" + responseException.getStatusCode().value()
                        + ", code=" + code
                        + ", message=" + message,
                responseException,
                errorResponse
        );
    }

    private LsfErrorResponse decodeErrorBody(RestClientResponseException responseException) {
        String body = responseException.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, LsfErrorResponse.class);
        } catch (IOException ignored) {
            return null;
        }
    }

    private LsfRemoteServiceException networkException(String serviceId, RestClientException exception) {
        Throwable root = rootCause(exception);
        String code = "HTTP_IO_FAILURE";
        if (root instanceof SocketTimeoutException) {
            code = "TIMEOUT";
        } else if (root instanceof ConnectException) {
            code = "CONNECT_FAILURE";
        } else if (root instanceof UnknownHostException) {
            code = "UNKNOWN_HOST";
        }
        return new LsfRemoteServiceException(
                serviceId,
                null,
                code,
                true,
                "I/O failure while calling serviceId=" + serviceId + ": " + exception.getMessage(),
                exception,
                null
        );
    }

    private static boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
