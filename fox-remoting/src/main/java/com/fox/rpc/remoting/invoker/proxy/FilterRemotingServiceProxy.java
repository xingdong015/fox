package com.fox.rpc.remoting.invoker.proxy;

import com.fox.rpc.remoting.exception.RpcException;
import com.fox.rpc.remoting.invoker.filter.InvokerFilterWrapper;
import com.fox.rpc.remoting.invoker.InvokerBootStrap;
import com.fox.rpc.remoting.invoker.config.InvokerConfig;

import java.lang.reflect.Proxy;

/**
 * Created by shenwenbo on 2017/4/1.
 */
public class FilterRemotingServiceProxy extends AbstractRemotingServiceProxy {



    @Override
    public <T> T getProxy(InvokerConfig config) {
        Object service = null;
        service=services.get(config);
        if (service==null) {
            try {
                InvokerBootStrap.startup();
                FilterServiceInvocationProxy serviceInvocationProxy=new FilterServiceInvocationProxy(config, InvokerFilterWrapper.selectInvocationHandler());
                service=(T) Proxy.newProxyInstance(config.getClass().getClassLoader(),
                        new Class<?>[]{ config.getInterfaceClass() },serviceInvocationProxy);
            } catch (Throwable e) {
                throw new RpcException("error while trying to invoke service",e);
            }
            services.put(config,service);
        }
        return (T) service;

    }

}
